package com.duoc.lims.limsbackend.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.beta.messages.BetaContentBlock;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaTextBlock;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.duoc.lims.limsbackend.dto.InterpretacionDTO;
import com.duoc.lims.limsbackend.dto.InterpretacionRequestDTO;
import com.duoc.lims.limsbackend.model.AnalisisCatalogo;
import com.duoc.lims.limsbackend.model.Muestra;
import com.duoc.lims.limsbackend.model.MuestraAnalisis;
import com.duoc.lims.limsbackend.model.Resultado;
import com.duoc.lims.limsbackend.model.Usuario;
import com.duoc.lims.limsbackend.repository.MuestraAnalisisRepository;
import com.duoc.lims.limsbackend.repository.MuestraRepository;
import com.duoc.lims.limsbackend.repository.ResultadoRepository;
import com.duoc.lims.limsbackend.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * v5 — Interpretación de resultados asistida por IA para el informe al investigador.
 *
 * El backend arma un resumen de la muestra (sitio, parámetros, valores, rangos, valores atípicos) y
 * le pide a Claude (modelo claude-opus-5-5, SDK oficial de Anthropic) un borrador breve y prudente.
 * Si no hay API key configurada o el servicio falla, se genera un borrador con reglas fijas, para que
 * el sistema funcione igual sin conexión. En ambos casos es SOLO un borrador: un Supervisor lo revisa,
 * lo edita y lo guarda; el informe PDF indica el origen y quién lo validó.
 *
 * Configuración: variable de entorno ANTHROPIC_API_KEY (o propiedad lims.ia.api-key). Nunca en git.
 */
@Service
public class InterpretacionService {

    private static final Logger log = LoggerFactory.getLogger(InterpretacionService.class);

    private static final String MODELO = "claude-opus-5-5";
    private static final Set<String> ORIGENES = Set.of("IA revisada", "Reglas revisadas", "Manual");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private static final String INSTRUCCIONES = """
            Eres un asistente de un laboratorio de investigación ambiental (muestras de algas, agua y \
            sedimentos de la Región de Los Lagos, Chile). Redacta en español un borrador de la sección \
            "Interpretación de resultados" de un informe dirigido al investigador que encargó el muestreo.

            Reglas:
            - Usa solo los datos entregados; no inventes valores, normas ni causas.
            - Destaca primero los parámetros fuera del rango de referencia y los valores atípicos respecto \
            del historial del sitio, y luego resume los que están dentro de rango.
            - Usa lenguaje técnico y prudente ("podría", "se sugiere"); no entregues diagnósticos definitivos.
            - Si corresponde, sugiere una acción concreta (repetir el análisis, ampliar el muestreo).
            - Escribe 1 o 2 párrafos de texto plano, de 150 palabras como máximo, sin títulos ni viñetas \
            ni formato markdown.
            """;

    private final MuestraRepository muestraRepository;
    private final MuestraAnalisisRepository muestraAnalisisRepository;
    private final ResultadoRepository resultadoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AnthropicClient cliente;   // null si no hay API key

    public InterpretacionService(MuestraRepository muestraRepository,
                                 MuestraAnalisisRepository muestraAnalisisRepository,
                                 ResultadoRepository resultadoRepository,
                                 UsuarioRepository usuarioRepository,
                                 @Value("${lims.ia.api-key:${ANTHROPIC_API_KEY:}}") String apiKey) {
        this.muestraRepository = muestraRepository;
        this.muestraAnalisisRepository = muestraAnalisisRepository;
        this.resultadoRepository = resultadoRepository;
        this.usuarioRepository = usuarioRepository;
        this.cliente = apiKey == null || apiKey.isBlank() ? null
                : AnthropicOkHttpClient.builder().apiKey(apiKey).timeout(Duration.ofSeconds(90)).build();
        log.info(cliente == null
                ? "IA: sin ANTHROPIC_API_KEY; los borradores de interpretación se generarán por reglas"
                : "IA: borradores de interpretación con " + MODELO);
    }

    /** Genera un borrador (no guarda nada). */
    @Transactional(readOnly = true)
    public InterpretacionDTO generarBorrador(Integer idMuestra) {
        Muestra muestra = buscar(idMuestra);
        List<Fila> filas = filas(muestra);
        if (filas.stream().noneMatch(f -> f.resultado != null)) {
            throw new IllegalArgumentException("La muestra aún no tiene resultados para interpretar");
        }

        String porReglas = borradorPorReglas(muestra, filas);
        if (cliente == null) {
            return new InterpretacionDTO(porReglas, "Reglas automáticas",
                    "IA no configurada (falta ANTHROPIC_API_KEY): se generó un borrador por reglas.");
        }
        try {
            Optional<String> texto = consultarClaude(datosParaIA(muestra, filas));
            if (texto.isPresent()) {
                return new InterpretacionDTO(texto.get(), "IA (Claude)", null);
            }
            return new InterpretacionDTO(porReglas, "Reglas automáticas",
                    "La IA no entregó un borrador para esta muestra: se generó uno por reglas.");
        } catch (RateLimitException e) {
            log.warn("IA: límite de uso alcanzado", e);
            return new InterpretacionDTO(porReglas, "Reglas automáticas",
                    "El servicio de IA está saturado en este momento: se generó un borrador por reglas.");
        } catch (AnthropicServiceException e) {
            log.warn("IA: error del servicio ({})", e.statusCode(), e);
            return new InterpretacionDTO(porReglas, "Reglas automáticas",
                    "El servicio de IA respondió con un error: se generó un borrador por reglas.");
        } catch (RuntimeException e) {
            log.warn("IA: no se pudo contactar al servicio", e);
            return new InterpretacionDTO(porReglas, "Reglas automáticas",
                    "No se pudo contactar al servicio de IA: se generó un borrador por reglas.");
        }
    }

    /** Guarda la interpretación revisada por el supervisor (la usará el informe PDF). */
    @Transactional
    public void guardar(Integer idMuestra, InterpretacionRequestDTO dto, Integer idUsuario) {
        if (!ORIGENES.contains(dto.getOrigen())) {
            throw new IllegalArgumentException("Origen no válido: use " + String.join(", ", ORIGENES));
        }
        Muestra muestra = buscar(idMuestra);
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new IllegalArgumentException("No existe el usuario con id " + idUsuario));
        muestra.setInterpretacion(dto.getTexto().trim());
        muestra.setInterpretacionOrigen(dto.getOrigen());
        muestra.setUsuarioInterpretacion(usuario);
        muestra.setFechaInterpretacion(LocalDateTime.now());
        muestraRepository.save(muestra);
    }

    // ---------- Claude ----------

    private Optional<String> consultarClaude(String datos) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(MODELO)
                .maxTokens(16000L)
                .system(INSTRUCCIONES)
                // tarea breve y acotada: esfuerzo bajo (más rápido y económico)
                .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.LOW).build())
                // si el modelo declina la solicitud, la API la reintenta con otro modelo adecuado
                .addBeta("server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .addUserMessage(datos)
                .build();

        BetaMessage respuesta = cliente.beta().messages().create(params);
        if (respuesta.stopReason().filter(BetaStopReason.REFUSAL::equals).isPresent()) {
            log.warn("IA: la solicitud fue declinada");
            return Optional.empty();
        }
        String texto = respuesta.content().stream()
                .map(BetaContentBlock::text)
                .flatMap(Optional::stream)
                .map(BetaTextBlock::text)
                .collect(Collectors.joining("\n"))
                .trim();
        return texto.isEmpty() ? Optional.empty() : Optional.of(texto);
    }

    private String datosParaIA(Muestra m, List<Fila> filas) {
        StringBuilder sb = new StringBuilder();
        sb.append("Muestra: ").append(m.getCodigoUnico()).append(" (").append(m.getTipoMuestra().getValorDb()).append(")\n");
        if (m.getSitio() != null) {
            sb.append("Sitio: ").append(m.getSitio().getNombre()).append(", ").append(m.getSitio().getTipoAmbiente())
                    .append(", comuna de ").append(texto(m.getSitio().getComuna())).append("\n");
        } else {
            sb.append("Sitio: ").append(texto(m.getProcedencia())).append("\n");
        }
        if (m.getFechaToma() != null) {
            sb.append("Fecha de toma: ").append(m.getFechaToma().format(FECHA)).append("\n");
        }
        if (m.getCondicionRecepcion() != null) {
            sb.append("Condición de recepción: ").append(m.getCondicionRecepcion()).append("\n");
        }
        sb.append("\nResultados:\n");
        for (Fila f : filas) {
            AnalisisCatalogo a = f.analisis;
            sb.append("- ").append(a.getNombre()).append(": ");
            if (f.resultado == null) {
                sb.append("sin resultado\n");
                continue;
            }
            Resultado r = f.resultado;
            sb.append(numero(r.getValorResultado())).append(" ").append(a.getUnidadMedida())
                    .append(" (rango de referencia ").append(numero(a.getValorMinNormal())).append("–")
                    .append(numero(a.getValorMaxNormal())).append(" ").append(a.getUnidadMedida()).append("; ")
                    .append(Boolean.TRUE.equals(r.getDentroRango()) ? "dentro de rango" : "FUERA de rango").append(")");
            if (r.isAnomalo() && r.getDetalleAnomalia() != null) {
                sb.append(" — ").append(r.getDetalleAnomalia());
            }
            if (r.getObservaciones() != null) {
                sb.append(" — observación del analista: ").append(r.getObservaciones());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // ---------- respaldo por reglas ----------

    private String borradorPorReglas(Muestra m, List<Fila> filas) {
        List<Fila> con = filas.stream().filter(f -> f.resultado != null).toList();
        List<String> fuera = new ArrayList<>();
        List<String> atipicos = new ArrayList<>();
        List<String> enRango = new ArrayList<>();
        for (Fila f : con) {
            Resultado r = f.resultado;
            AnalisisCatalogo a = f.analisis;
            String valor = a.getNombre() + " (" + numero(r.getValorResultado()) + " " + a.getUnidadMedida() + ")";
            if (!Boolean.TRUE.equals(r.getDentroRango())) {
                fuera.add(valor + ", fuera del rango de referencia " + numero(a.getValorMinNormal()) + "–"
                        + numero(a.getValorMaxNormal()) + " " + a.getUnidadMedida());
            } else {
                enRango.add(a.getNombre());
            }
            if (r.isAnomalo()) {
                atipicos.add(a.getNombre());
            }
        }

        StringBuilder sb = new StringBuilder();
        String sitio = m.getSitio() != null ? m.getSitio().getNombre() : texto(m.getProcedencia());
        sb.append("La muestra ").append(m.getCodigoUnico()).append(" (").append(m.getTipoMuestra().getValorDb().toLowerCase())
                .append(", ").append(sitio);
        if (m.getFechaToma() != null) {
            sb.append(", tomada el ").append(m.getFechaToma().format(FECHA));
        }
        sb.append(") fue analizada en ").append(con.size()).append(con.size() == 1 ? " parámetro. " : " parámetros. ");
        if (fuera.isEmpty()) {
            sb.append("Todos los resultados se encuentran dentro de los rangos de referencia del laboratorio. ");
        } else {
            sb.append("Se observan valores fuera de rango en: ").append(String.join("; ", fuera)).append(". ");
        }
        if (!atipicos.isEmpty()) {
            sb.append("Además, ").append(String.join(", ", atipicos))
                    .append(atipicos.size() == 1 ? " presenta un valor atípico" : " presentan valores atípicos")
                    .append(" respecto del historial del sitio, por lo que se sugiere confirmar con un nuevo análisis o muestreo. ");
        }
        if (!enRango.isEmpty() && !fuera.isEmpty()) {
            sb.append("Los parámetros ").append(String.join(", ", enRango)).append(" se mantienen dentro de lo esperado. ");
        }
        sb.append("Los resultados se refieren únicamente a la muestra recibida.");
        return sb.toString();
    }

    // ---------- helpers ----------

    private record Fila(AnalisisCatalogo analisis, Resultado resultado) {}

    private List<Fila> filas(Muestra m) {
        List<Fila> filas = new ArrayList<>();
        for (MuestraAnalisis ma : muestraAnalisisRepository.findByMuestra_Id(m.getId())) {
            filas.add(new Fila(ma.getAnalisis(), resultadoRepository.findByMuestraAnalisis_Id(ma.getId()).orElse(null)));
        }
        return filas;
    }

    private Muestra buscar(Integer id) {
        return muestraRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe la muestra con id " + id));
    }

    private String numero(BigDecimal n) {
        return n == null ? "" : n.stripTrailingZeros().toPlainString();
    }

    private String texto(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }
}
