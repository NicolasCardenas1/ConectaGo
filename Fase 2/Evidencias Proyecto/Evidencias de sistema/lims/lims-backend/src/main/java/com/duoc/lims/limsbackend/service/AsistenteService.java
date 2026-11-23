package com.duoc.lims.limsbackend.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.beta.messages.BetaContentBlock;
import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaMessageParam;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaTextBlock;
import com.anthropic.models.beta.messages.BetaTool;
import com.anthropic.models.beta.messages.BetaToolResultBlockParam;
import com.anthropic.models.beta.messages.BetaToolUseBlock;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.duoc.lims.limsbackend.dto.AsistenteRequestDTO;
import com.duoc.lims.limsbackend.dto.AsistenteResponseDTO;
import com.duoc.lims.limsbackend.dto.CalibracionResponseDTO;
import com.duoc.lims.limsbackend.dto.TrazabilidadEventoDTO;
import com.duoc.lims.limsbackend.model.Equipo;
import com.duoc.lims.limsbackend.model.Muestra;
import com.duoc.lims.limsbackend.model.ResultadoVersion;
import com.duoc.lims.limsbackend.model.SitioMuestreo;
import com.duoc.lims.limsbackend.repository.EquipoRepository;
import com.duoc.lims.limsbackend.repository.MuestraRepository;
import com.duoc.lims.limsbackend.repository.ResultadoVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * v6 — Chatbot de trazabilidad: responde en lenguaje natural preguntas como
 * "¿dónde está guardada la M-2026-0012?", "¿con qué equipo se tomó?" o
 * "¿qué muestras se midieron con el EQ-005?".
 *
 * Funciona en modo SOLO LECTURA: Claude no ve la base de datos; solo puede llamar a cuatro
 * herramientas de consulta (buscar muestras, trazabilidad de una muestra, ficha de un equipo y
 * listado de equipos) que el backend ejecuta con los mismos datos de la pestaña "Trazabilidad completa".
 * Ninguna herramienta modifica datos.
 *
 * Sin ANTHROPIC_API_KEY (o si el servicio falla) responde en "modo directo": reconoce códigos de
 * muestra (M-2026-0012) o de equipo (EQ-005) en la pregunta y devuelve la consulta correspondiente.
 */
@Service
public class AsistenteService {

    private static final Logger log = LoggerFactory.getLogger(AsistenteService.class);

    private static final String MODELO = "claude-opus-5-5";
    private static final int MAX_VUELTAS = 8;              // máximo de rondas pregunta → herramientas → respuesta
    private static final int MAX_MENSAJES_HISTORIAL = 12;
    private static final int MAX_RESULTADOS = 25;
    private static final String ORIGEN_IA = "IA (Claude)";
    private static final String ORIGEN_DIRECTO = "Consulta directa (sin IA)";
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final Pattern CODIGO_MUESTRA = Pattern.compile("\\b([A-Za-z]{1,4}-\\d{4}-\\d{3,6})\\b");
    private static final Pattern CODIGO_EQUIPO = Pattern.compile("\\b(EQ-\\d{1,5})\\b", Pattern.CASE_INSENSITIVE);

    private static final String INSTRUCCIONES = """
            Eres el asistente de trazabilidad de un LIMS (sistema de gestión de laboratorio) de \
            investigación ambiental: muestras de algas, agua y sedimentos de la Región de Los Lagos, Chile.
            Respondes preguntas del personal del laboratorio sobre la historia de las muestras: dónde y \
            cuándo se tomaron, con qué equipo, quién las recibió y custodió, dónde están guardadas, qué \
            análisis tienen, con qué equipo y calibración se midieron, quién aprobó o rechazó y qué \
            informes se emitieron.

            Reglas:
            - Usa SIEMPRE las herramientas para obtener los datos; nunca respondas de memoria ni inventes \
            códigos, fechas, personas, equipos o valores. Si una herramienta no encuentra algo, dilo.
            - Si el usuario menciona una muestra sin el código exacto (por sitio, tipo o investigador), \
            búscala primero con buscar_muestras.
            - Cita el código de la muestra o del equipo y las fechas de donde sale cada dato.
            - Si un dato no está registrado en el sistema (por ejemplo, una muestra sin equipo de toma), \
            dilo explícitamente: es una brecha de trazabilidad.
            - Señala los hallazgos relevantes para la calidad: calibraciones vencidas, resultados fuera \
            de rango o atípicos, rechazos y correcciones.
            - Eres de solo lectura: no puedes registrar, modificar ni borrar datos. Si te lo piden, \
            explica que eso se hace desde las pantallas del sistema.
            - Si la pregunta no tiene relación con las muestras, equipos o la trazabilidad del \
            laboratorio, indícalo amablemente en una frase.
            - Responde en español, breve y claro, en texto plano: frases cortas o viñetas con "-", sin \
            títulos ni formato markdown (sin **, #, tablas).
            """;

    private final MuestraRepository muestraRepository;
    private final EquipoRepository equipoRepository;
    private final ResultadoVersionRepository versionRepository;
    private final TrazabilidadService trazabilidadService;
    private final EquipoService equipoService;
    private final TransactionTemplate soloLectura;
    private final AnthropicClient cliente;   // null si no hay API key
    private final List<BetaTool> herramientas = definirHerramientas();

    public AsistenteService(MuestraRepository muestraRepository,
                            EquipoRepository equipoRepository,
                            ResultadoVersionRepository versionRepository,
                            TrazabilidadService trazabilidadService,
                            EquipoService equipoService,
                            PlatformTransactionManager transactionManager,
                            @Value("${lims.ia.api-key:${ANTHROPIC_API_KEY:}}") String apiKey) {
        this.muestraRepository = muestraRepository;
        this.equipoRepository = equipoRepository;
        this.versionRepository = versionRepository;
        this.trazabilidadService = trazabilidadService;
        this.equipoService = equipoService;
        this.soloLectura = new TransactionTemplate(transactionManager);
        this.soloLectura.setReadOnly(true);
        this.cliente = apiKey == null || apiKey.isBlank() ? null
                : AnthropicOkHttpClient.builder().apiKey(apiKey).timeout(Duration.ofSeconds(120)).build();
        log.info(cliente == null
                ? "Chatbot: sin ANTHROPIC_API_KEY; responderá en modo consulta directa (códigos de muestra/equipo)"
                : "Chatbot de trazabilidad con " + MODELO);
    }

    public AsistenteResponseDTO consultar(AsistenteRequestDTO dto) {
        String pregunta = dto.getPregunta().trim();
        List<String> consultas = new ArrayList<>();
        if (cliente == null) {
            // Sin clave de IA, la consulta directa es el modo normal: no se muestra aviso al usuario.
            return modoDirecto(pregunta, consultas, null);
        }
        try {
            Optional<String> respuesta = consultarClaude(dto, consultas);
            if (respuesta.isPresent()) {
                return new AsistenteResponseDTO(respuesta.get(), ORIGEN_IA, consultas, null);
            }
            return modoDirecto(pregunta, consultas, "La IA no entregó una respuesta: se usó la consulta directa.");
        } catch (RateLimitException e) {
            log.warn("Chatbot: límite de uso alcanzado", e);
            return modoDirecto(pregunta, consultas, "El servicio de IA está saturado: se usó la consulta directa.");
        } catch (AnthropicServiceException e) {
            log.warn("Chatbot: error del servicio ({})", e.statusCode(), e);
            return modoDirecto(pregunta, consultas, "El servicio de IA respondió con un error: se usó la consulta directa.");
        } catch (RuntimeException e) {
            log.warn("Chatbot: no se pudo contactar al servicio", e);
            return modoDirecto(pregunta, consultas, "No se pudo contactar al servicio de IA: se usó la consulta directa.");
        }
    }

    // =====================================================================
    // Claude con herramientas (tool use)
    // =====================================================================

    private Optional<String> consultarClaude(AsistenteRequestDTO dto, List<String> consultas) {
        List<BetaMessageParam> mensajes = new ArrayList<>(historial(dto));
        mensajes.add(mensaje(BetaMessageParam.Role.USER, dto.getPregunta().trim()));
        String sistema = INSTRUCCIONES + "\nFecha de hoy: " + LocalDate.now().format(FECHA) + ".";

        for (int vuelta = 0; vuelta < MAX_VUELTAS; vuelta++) {
            MessageCreateParams.Builder params = MessageCreateParams.builder()
                    .model(MODELO)
                    .maxTokens(16000L)
                    .system(sistema)
                    // consultas acotadas: esfuerzo bajo (más rápido y económico)
                    .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.LOW).build())
                    // si el modelo declina la solicitud, la API la reintenta con otro modelo adecuado
                    .addBeta("server-side-fallback-2026-07-01")
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                    .messages(mensajes);
            herramientas.forEach(params::addTool);

            BetaMessage respuesta = cliente.beta().messages().create(params.build());
            if (respuesta.stopReason().filter(BetaStopReason.REFUSAL::equals).isPresent()) {
                log.warn("Chatbot: la solicitud fue declinada");
                return Optional.empty();
            }

            List<BetaToolUseBlock> usos = respuesta.content().stream()
                    .map(BetaContentBlock::toolUse)
                    .flatMap(Optional::stream)
                    .toList();
            if (usos.isEmpty()) {
                String texto = respuesta.content().stream()
                        .map(BetaContentBlock::text)
                        .flatMap(Optional::stream)
                        .map(BetaTextBlock::text)
                        .collect(Collectors.joining("\n"))
                        .trim();
                return texto.isEmpty() ? Optional.empty() : Optional.of(texto);
            }

            // El modelo pidió datos: se ejecutan las herramientas y se le devuelven los resultados.
            mensajes.add(respuesta.toParam());
            List<BetaContentBlockParam> resultados = new ArrayList<>();
            for (BetaToolUseBlock uso : usos) {
                Map<String, Object> entrada = entrada(uso);
                consultas.add(describir(uso.name(), entrada));
                String salida;
                boolean error = false;
                try {
                    salida = ejecutarHerramienta(uso.name(), entrada);
                } catch (RuntimeException e) {
                    log.warn("Chatbot: falló la herramienta {}", uso.name(), e);
                    salida = "Error al consultar: " + e.getMessage();
                    error = true;
                }
                resultados.add(BetaContentBlockParam.ofToolResult(BetaToolResultBlockParam.builder()
                        .toolUseId(uso.id())
                        .content(salida)
                        .isError(error)
                        .build()));
            }
            mensajes.add(BetaMessageParam.builder()
                    .role(BetaMessageParam.Role.USER)
                    .contentOfBetaContentBlockParams(resultados)
                    .build());
        }
        log.warn("Chatbot: se alcanzó el máximo de {} rondas de herramientas", MAX_VUELTAS);
        return Optional.empty();
    }

    /** Conversación previa (solo texto). La API exige que empiece por el usuario y alterne los roles. */
    private List<BetaMessageParam> historial(AsistenteRequestDTO dto) {
        List<AsistenteRequestDTO.Mensaje> previos = dto.getHistorial() == null ? List.of() : dto.getHistorial();
        if (previos.size() > MAX_MENSAJES_HISTORIAL) {
            previos = previos.subList(previos.size() - MAX_MENSAJES_HISTORIAL, previos.size());
        }
        List<BetaMessageParam> salida = new ArrayList<>();
        BetaMessageParam.Role ultimo = BetaMessageParam.Role.ASSISTANT;   // así el primero debe ser del usuario
        for (AsistenteRequestDTO.Mensaje m : previos) {
            if (m == null || m.getTexto() == null || m.getTexto().isBlank()) {
                continue;
            }
            BetaMessageParam.Role rol = "asistente".equalsIgnoreCase(m.getRol())
                    ? BetaMessageParam.Role.ASSISTANT : BetaMessageParam.Role.USER;
            if (rol.equals(ultimo)) {
                continue;
            }
            salida.add(mensaje(rol, m.getTexto()));
            ultimo = rol;
        }
        // La pregunta nueva es del usuario: el historial debe terminar en el asistente.
        if (!salida.isEmpty() && BetaMessageParam.Role.USER.equals(ultimo)) {
            salida.remove(salida.size() - 1);
        }
        return salida;
    }

    private BetaMessageParam mensaje(BetaMessageParam.Role rol, String texto) {
        return BetaMessageParam.builder().role(rol).content(texto).build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> entrada(BetaToolUseBlock uso) {
        try {
            Map<String, Object> m = uso.input(Map.class);
            return m == null ? Map.of() : m;
        } catch (RuntimeException e) {
            return Map.of();
        }
    }

    private List<BetaTool> definirHerramientas() {
        return List.of(
                herramienta("buscar_muestras",
                        "Busca muestras por texto (código, sitio, tipo de muestra, investigador, institución, "
                                + "procedencia o equipo de toma) y/o por estado. Devuelve un resumen de cada muestra. "
                                + "Sin filtros devuelve las más recientes.",
                        Map.of("texto", propiedad("string", "Texto a buscar, ej. 'Ilque', 'Macroalga', 'Pérez'"),
                                "estado", propiedad("string", "Estado: Recibida, En analisis, Resultados ingresados, "
                                        + "Aprobada, Rechazada o Reportada")),
                        List.of()),
                herramienta("trazabilidad_muestra",
                        "Historia completa de UNA muestra: sitio con coordenadas, fecha de toma, equipo de toma y su "
                                + "calibración, investigador, ubicación actual de almacenamiento, cadena de custodia, "
                                + "análisis, cada versión de cada resultado con el equipo usado, aprobaciones, "
                                + "interpretación e informes emitidos.",
                        Map.of("codigo", propiedad("string", "Código único de la muestra, ej. M-2026-0012")),
                        List.of("codigo")),
                herramienta("consultar_equipo",
                        "Ficha de UN equipo: uso (terreno/laboratorio), estado e historial de calibraciones, "
                                + "muestras que se tomaron con él y resultados que se midieron con él.",
                        Map.of("codigo", propiedad("string", "Código del equipo, ej. EQ-005")),
                        List.of("codigo")),
                herramienta("listar_equipos",
                        "Lista todos los equipos con su uso, estado (activo/de baja) y estado de calibración "
                                + "(Vigente, Por vencer, Vencida, Sin registro).",
                        Map.of(),
                        List.of()));
    }

    private BetaTool herramienta(String nombre, String descripcion, Map<String, JsonValue> propiedades,
                                 List<String> requeridas) {
        BetaTool.InputSchema.Properties.Builder props = BetaTool.InputSchema.Properties.builder();
        propiedades.forEach(props::putAdditionalProperty);
        BetaTool.InputSchema.Builder esquema = BetaTool.InputSchema.builder().properties(props.build());
        if (!requeridas.isEmpty()) {
            esquema.required(requeridas);
        }
        return BetaTool.builder().name(nombre).description(descripcion).inputSchema(esquema.build()).build();
    }

    private JsonValue propiedad(String tipo, String descripcion) {
        return JsonValue.from(Map.of("type", tipo, "description", descripcion));
    }

    private String ejecutarHerramienta(String nombre, Map<String, Object> entrada) {
        return switch (nombre) {
            case "buscar_muestras" -> leer(() -> buscarMuestras(texto(entrada.get("texto")), texto(entrada.get("estado"))));
            case "trazabilidad_muestra" -> leer(() -> trazabilidadMuestra(texto(entrada.get("codigo"))));
            case "consultar_equipo" -> leer(() -> consultarEquipo(texto(entrada.get("codigo"))));
            case "listar_equipos" -> leer(this::listarEquipos);
            default -> "Herramienta desconocida: " + nombre;
        };
    }

    private String describir(String herramienta, Map<String, Object> entrada) {
        String args = entrada.entrySet().stream()
                .filter(e -> e.getValue() != null && !String.valueOf(e.getValue()).isBlank())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
        return herramienta + (args.isEmpty() ? "" : " (" + args + ")");
    }

    // =====================================================================
    // Modo directo (sin IA): reconoce códigos en la pregunta
    // =====================================================================

    private AsistenteResponseDTO modoDirecto(String pregunta, List<String> consultas, String aviso) {
        List<String> partes = new ArrayList<>();
        Matcher mEquipo = CODIGO_EQUIPO.matcher(pregunta);
        while (mEquipo.find()) {
            String codigo = mEquipo.group(1).toUpperCase(Locale.ROOT);
            consultas.add("consultar_equipo (codigo=" + codigo + ")");
            partes.add(leer(() -> consultarEquipo(codigo)));
        }
        Matcher mMuestra = CODIGO_MUESTRA.matcher(pregunta);
        while (mMuestra.find()) {
            String codigo = mMuestra.group(1).toUpperCase(Locale.ROOT);
            if (codigo.startsWith("EQ-")) {
                continue;
            }
            consultas.add("trazabilidad_muestra (codigo=" + codigo + ")");
            partes.add(leer(() -> trazabilidadMuestra(codigo)));
        }
        String minus = pregunta.toLowerCase(Locale.ROOT);
        if (partes.isEmpty() && minus.contains("equipo")) {
            consultas.add("listar_equipos");
            partes.add(leer(this::listarEquipos));
        }
        if (partes.isEmpty()) {
            partes.add("""
                    En modo consulta directa (sin IA) puedo responder si escribes un código:
                    - Un código de muestra (ej. M-2026-0012) para ver toda su trazabilidad: dónde y con qué equipo \
                    se tomó, custodia, ubicación actual, resultados, aprobaciones e informes.
                    - Un código de equipo (ej. EQ-005) para ver su calibración y en qué muestras se usó.
                    - La palabra "equipos" para ver el estado de calibración de todos los equipos.""");
        }
        return new AsistenteResponseDTO(String.join("\n\n", partes), ORIGEN_DIRECTO, consultas, aviso);
    }

    // =====================================================================
    // Herramientas de consulta (solo lectura)
    // =====================================================================

    private String leer(Supplier<String> consulta) {
        return soloLectura.execute(estado -> consulta.get());
    }

    private String buscarMuestras(String texto, String estado) {
        String t = texto == null ? null : texto.toLowerCase(Locale.ROOT);
        List<Muestra> encontradas = muestraRepository.findAll().stream()
                .filter(m -> estado == null || m.getEstado().getValorDb().equalsIgnoreCase(estado))
                .filter(m -> t == null || textoBuscable(m).contains(t))
                .sorted(Comparator.comparing(Muestra::getFechaRecepcion,
                        Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder())).reversed())
                .toList();
        if (encontradas.isEmpty()) {
            return "No hay muestras que coincidan" + (texto == null ? "" : " con \"" + texto + "\"")
                    + (estado == null ? "" : " en estado " + estado) + ".";
        }
        StringBuilder sb = new StringBuilder(encontradas.size() + " muestra(s) encontrada(s)");
        if (encontradas.size() > MAX_RESULTADOS) {
            sb.append("; se muestran las ").append(MAX_RESULTADOS).append(" más recientes");
        }
        sb.append(":\n");
        encontradas.stream().limit(MAX_RESULTADOS).forEach(m -> sb.append("- ").append(resumen(m)).append("\n"));
        return sb.toString().trim();
    }

    private String textoBuscable(Muestra m) {
        StringBuilder sb = new StringBuilder();
        sb.append(m.getCodigoUnico()).append(' ').append(m.getTipoMuestra().getValorDb()).append(' ')
                .append(nn(m.getProcedencia())).append(' ').append(nn(m.getObservaciones())).append(' ');
        if (m.getSitio() != null) {
            sb.append(m.getSitio().getCodigo()).append(' ').append(m.getSitio().getNombre()).append(' ')
                    .append(nn(m.getSitio().getComuna())).append(' ');
        }
        if (m.getInvestigador() != null) {
            sb.append(m.getInvestigador().getNombreCompleto()).append(' ')
                    .append(nn(m.getInvestigador().getInstitucion())).append(' ');
        }
        if (m.getEquipoToma() != null) {
            sb.append(m.getEquipoToma().getCodigo()).append(' ').append(m.getEquipoToma().getNombre());
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private String resumen(Muestra m) {
        return m.getCodigoUnico() + " | " + m.getTipoMuestra().getValorDb() + " | estado " + m.getEstado().getValorDb()
                + " | sitio " + (m.getSitio() == null ? nn(m.getProcedencia()) : m.getSitio().getCodigo() + " " + m.getSitio().getNombre())
                + " | toma " + fechaHora(m.getFechaToma())
                + " | equipo de toma " + (m.getEquipoToma() == null ? "no registrado" : m.getEquipoToma().getEtiqueta())
                + " | guardada en " + (m.getUbicacionAlmacenamiento() == null ? "no registrado" : m.getUbicacionAlmacenamiento());
    }

    private String trazabilidadMuestra(String codigo) {
        if (codigo == null) {
            return "Falta el código de la muestra.";
        }
        Optional<Muestra> encontrada = muestraRepository.findByCodigoUnicoIgnoreCase(codigo.trim());
        if (encontrada.isEmpty()) {
            return "No existe una muestra con el código " + codigo + ". Usa buscar_muestras para encontrarla.";
        }
        Muestra m = encontrada.get();
        StringBuilder sb = new StringBuilder();
        sb.append("Muestra ").append(m.getCodigoUnico()).append(" (").append(m.getTipoMuestra().getValorDb())
                .append(", tipo de registro ").append(m.getTipoRegistro().getValorDb()).append(")\n");
        sb.append("- Estado actual: ").append(m.getEstado().getValorDb()).append(" · prioridad ")
                .append(m.getPrioridad().getValorDb()).append("\n");
        SitioMuestreo s = m.getSitio();
        sb.append("- Dónde se tomó: ").append(s == null ? nn(m.getProcedencia())
                : s.getCodigo() + " " + s.getNombre() + " (" + s.getTipoAmbiente() + ", " + nn(s.getComuna())
                + "; lat " + coord(s.getLatitud()) + ", lon " + coord(s.getLongitud()) + ")"
                + (m.getProcedencia() != null && !m.getProcedencia().equals(s.getNombre())
                        ? " · punto: " + m.getProcedencia() : "")).append("\n");
        sb.append("- Cuándo se tomó: ").append(fechaHora(m.getFechaToma())).append("\n");
        sb.append("- Equipo de toma: ").append(m.getEquipoToma() == null
                ? "NO REGISTRADO (brecha de trazabilidad)"
                : m.getEquipoToma().getEtiqueta() + " (uso " + m.getEquipoToma().getUso() + ")").append("\n");
        sb.append("- Investigador que la encargó: ").append(m.getInvestigador() == null ? "no registrado"
                : m.getInvestigador().getNombreCompleto() + " (" + nn(m.getInvestigador().getInstitucion()) + ")").append("\n");
        sb.append("- Recibida en el laboratorio: ").append(fechaHora(m.getFechaRecepcion())).append(" por ")
                .append(m.getUsuarioRegistro().getNombre()).append(" ").append(m.getUsuarioRegistro().getApellido())
                .append(" · condición: ").append(nn(m.getCondicionRecepcion())).append("\n");
        sb.append("- Dónde está guardada ahora: ").append(m.getUbicacionAlmacenamiento() == null ? "no registrado"
                : m.getUbicacionAlmacenamiento()).append(" · conservar hasta: ")
                .append(m.getFechaVencimiento() == null ? "no definido" : m.getFechaVencimiento().format(FECHA)).append("\n");
        sb.append("\nLínea de tiempo (orden cronológico):\n");
        for (TrazabilidadEventoDTO e : trazabilidadService.lineaDeTiempo(m.getId())) {
            sb.append("- ").append(fechaHora(e.getFecha())).append(" | ").append(e.getCategoria())
                    .append(e.isAlerta() ? " ⚠" : "").append(" | ").append(e.getTitulo());
            if (e.getDetalle() != null && !e.getDetalle().isBlank()) {
                sb.append(" | ").append(e.getDetalle());
            }
            if (e.getUsuario() != null) {
                sb.append(" | por ").append(e.getUsuario());
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String consultarEquipo(String codigo) {
        if (codigo == null) {
            return "Falta el código del equipo.";
        }
        Optional<Equipo> encontrado = equipoRepository.findByCodigoIgnoreCase(codigo.trim());
        if (encontrado.isEmpty()) {
            return "No existe un equipo con el código " + codigo + ". Usa listar_equipos para ver los códigos.";
        }
        Equipo e = encontrado.get();
        StringBuilder sb = new StringBuilder();
        sb.append("Equipo ").append(e.getEtiqueta()).append("\n");
        sb.append("- Marca/modelo: ").append(nn(e.getMarca())).append(" ").append(nn(e.getModelo()))
                .append(" · N° serie: ").append(nn(e.getNumeroSerie())).append("\n");
        sb.append("- Uso: ").append(e.getUso()).append(" · ").append(e.isActivo() ? "activo" : "DADO DE BAJA").append("\n");
        sb.append("- Calibración actual: ").append(EquipoService.estadoCalibracion(e));
        if (e.getFechaProximaCalibracion() != null) {
            sb.append(" (última ").append(fecha(e.getFechaUltimaCalibracion())).append(", vence ")
                    .append(fecha(e.getFechaProximaCalibracion())).append(")");
        }
        sb.append("\n");

        List<CalibracionResponseDTO> calibraciones = equipoService.listarCalibraciones(e.getId());
        sb.append("\nHistorial de calibraciones (").append(calibraciones.size()).append("):\n");
        if (calibraciones.isEmpty()) {
            sb.append("- Sin calibraciones registradas\n");
        }
        for (CalibracionResponseDTO c : calibraciones) {
            sb.append("- ").append(fecha(c.getFechaCalibracion())).append(" → vence ").append(fecha(c.getFechaVencimiento()))
                    .append(c.isVigente() ? " (vigente)" : "")
                    .append(c.getRealizadaPor() == null ? "" : " · por " + c.getRealizadaPor())
                    .append(c.getCertificado() == null ? "" : " · certificado " + c.getCertificado()).append("\n");
        }

        List<Muestra> tomadas = muestraRepository.findByEquipoToma_IdOrderByFechaTomaDesc(e.getId());
        sb.append("\nMuestras tomadas en terreno con este equipo (").append(tomadas.size()).append("):\n");
        if (tomadas.isEmpty()) {
            sb.append("- Ninguna\n");
        }
        tomadas.stream().limit(MAX_RESULTADOS).forEach(m -> sb.append("- ").append(m.getCodigoUnico())
                .append(" · ").append(m.getTipoMuestra().getValorDb()).append(" · toma ").append(fechaHora(m.getFechaToma()))
                .append(" · ").append(m.getSitio() == null ? nn(m.getProcedencia()) : m.getSitio().getNombre())
                .append(calibracionEn(e, m.getFechaToma())).append("\n"));

        List<ResultadoVersion> mediciones = versionRepository.findByEquipo_IdOrderByFechaDesc(e.getId());
        sb.append("\nResultados medidos con este equipo (").append(mediciones.size()).append("):\n");
        if (mediciones.isEmpty()) {
            sb.append("- Ninguno\n");
        }
        mediciones.stream().limit(MAX_RESULTADOS).forEach(v -> {
            var ma = v.getResultado().getMuestraAnalisis();
            sb.append("- ").append(ma.getMuestra().getCodigoUnico()).append(" · ").append(ma.getAnalisis().getNombre())
                    .append(" = ").append(numero(v.getValorResultado())).append(" ").append(ma.getAnalisis().getUnidadMedida())
                    .append(" (versión ").append(v.getVersion()).append(") · ").append(fechaHora(v.getFecha()))
                    .append(calibracionEn(e, v.getFecha())).append("\n");
        });
        return sb.toString().trim();
    }

    /** Estado de la calibración del equipo en la fecha en que se usó. */
    private String calibracionEn(Equipo e, LocalDateTime cuando) {
        if (cuando == null || (e.getFechaUltimaCalibracion() == null && e.getFechaProximaCalibracion() == null)) {
            return "";
        }
        LocalDate dia = cuando.toLocalDate();
        return equipoService.calibracionVigenteEn(e.getId(), dia)
                .map(c -> c.getFechaVencimiento().isBefore(dia)
                        ? " · ⚠ calibración vencida ese día (venció " + fecha(c.getFechaVencimiento()) + ")"
                        : " · calibración vigente ese día (hasta " + fecha(c.getFechaVencimiento()) + ")")
                .orElse(" · sin calibración registrada a esa fecha");
    }

    private String listarEquipos() {
        List<Equipo> equipos = equipoRepository.findAllByOrderByCodigoAsc();
        if (equipos.isEmpty()) {
            return "No hay equipos registrados.";
        }
        StringBuilder sb = new StringBuilder(equipos.size() + " equipo(s):\n");
        for (Equipo e : equipos) {
            sb.append("- ").append(e.getEtiqueta()).append(" | uso ").append(e.getUso())
                    .append(" | ").append(e.isActivo() ? "activo" : "de baja")
                    .append(" | calibración ").append(EquipoService.estadoCalibracion(e));
            if (e.getFechaProximaCalibracion() != null) {
                sb.append(" (vence ").append(fecha(e.getFechaProximaCalibracion())).append(")");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    // ---------- helpers ----------

    private String texto(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    private String nn(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    private String fechaHora(LocalDateTime f) {
        return f == null ? "no registrada" : f.format(FECHA_HORA);
    }

    private String fecha(LocalDate f) {
        return f == null ? "—" : f.format(FECHA);
    }

    private String numero(BigDecimal n) {
        return n == null ? "" : n.stripTrailingZeros().toPlainString();
    }

    private String coord(BigDecimal n) {
        return n == null ? "?" : n.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }
}
