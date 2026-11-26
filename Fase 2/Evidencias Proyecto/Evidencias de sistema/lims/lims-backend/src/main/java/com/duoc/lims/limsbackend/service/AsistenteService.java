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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * v6 — Chatbot de trazabilidad: responde en lenguaje natural preguntas como
 * "¿dónde está guardada la M-2026-0012?", "¿con qué equipo se tomó?" o
 * "¿qué muestras se midieron con el EQ-005?".
 *
 * Funciona en modo SOLO LECTURA: Claude no ve la base de datos; solo puede llamar a herramientas de
 * consulta ({@link ConsultasTrazabilidad}) que el backend ejecuta con los mismos datos de la pestaña
 * "Trazabilidad completa". Ninguna herramienta modifica datos.
 *
 * Sin ANTHROPIC_API_KEY (o si el servicio falla) responde en "modo directo" ({@link ModoDirectoAsistente}):
 * reglas que reconocen códigos, temas (pendientes, fuera de rango, equipos vencidos, sitios...) y filtros
 * (sitio, investigador, tipo, estado, parámetro, fecha) en la pregunta.
 */
@Service
public class AsistenteService {

    private static final Logger log = LoggerFactory.getLogger(AsistenteService.class);

    private static final String MODELO = "claude-opus-5-5";
    private static final int MAX_VUELTAS = 8;              // máximo de rondas pregunta → herramientas → respuesta
    private static final int MAX_MENSAJES_HISTORIAL = 12;
    private static final String ORIGEN_IA = "IA (Claude)";
    private static final String ORIGEN_DIRECTO = "Consulta directa (sin IA)";
    private static final java.time.format.DateTimeFormatter FECHA = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");

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

    private final ConsultasTrazabilidad datos;
    private final ModoDirectoAsistente modoDirecto;
    private final TransactionTemplate soloLectura;
    private final AnthropicClient cliente;   // null si no hay API key
    private final List<BetaTool> herramientas = definirHerramientas();

    public AsistenteService(ConsultasTrazabilidad datos,
                            ModoDirectoAsistente modoDirecto,
                            PlatformTransactionManager transactionManager,
                            @Value("${lims.ia.api-key:${ANTHROPIC_API_KEY:}}") String apiKey) {
        this.datos = datos;
        this.modoDirecto = modoDirecto;
        this.soloLectura = new TransactionTemplate(transactionManager);
        this.soloLectura.setReadOnly(true);
        this.cliente = apiKey == null || apiKey.isBlank() ? null
                : AnthropicOkHttpClient.builder().apiKey(apiKey).timeout(Duration.ofSeconds(120)).build();
        log.info(cliente == null
                ? "Chatbot: sin ANTHROPIC_API_KEY; responderá en modo consulta directa (reglas, sin IA)"
                : "Chatbot de trazabilidad con " + MODELO);
    }

    public AsistenteResponseDTO consultar(AsistenteRequestDTO dto) {
        List<String> consultas = new ArrayList<>();
        if (cliente == null) {
            // Sin clave de IA, la consulta directa es el modo normal: no se muestra aviso al usuario.
            return modoDirecto(dto, consultas, null);
        }
        try {
            Optional<String> respuesta = consultarClaude(dto, consultas);
            if (respuesta.isPresent()) {
                return new AsistenteResponseDTO(respuesta.get(), ORIGEN_IA, consultas, null);
            }
            return modoDirecto(dto, consultas, "La IA no entregó una respuesta: se usó la consulta directa.");
        } catch (RateLimitException e) {
            log.warn("Chatbot: límite de uso alcanzado", e);
            return modoDirecto(dto, consultas, "El servicio de IA está saturado: se usó la consulta directa.");
        } catch (AnthropicServiceException e) {
            log.warn("Chatbot: error del servicio ({})", e.statusCode(), e);
            return modoDirecto(dto, consultas, "El servicio de IA respondió con un error: se usó la consulta directa.");
        } catch (RuntimeException e) {
            log.warn("Chatbot: no se pudo contactar al servicio", e);
            return modoDirecto(dto, consultas, "No se pudo contactar al servicio de IA: se usó la consulta directa.");
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
                        List.of()),
                herramienta("resumen_laboratorio",
                        "Resumen general: muestras por estado, resultados pendientes de aprobación, fuera de rango "
                                + "y atípicos, equipos con calibración vencida o por vencer e informes emitidos.",
                        Map.of(), List.of()),
                herramienta("pendientes_de_aprobacion",
                        "Resultados cuya versión vigente espera la evaluación de un supervisor, del más antiguo al más reciente.",
                        Map.of(), List.of()),
                herramienta("resultados_fuera_de_rango",
                        "Resultados vigentes fuera del rango normal del parámetro, con muestra, sitio y estado de aprobación.",
                        Map.of(), List.of()),
                herramienta("valores_atipicos",
                        "Resultados vigentes marcados como valor atípico por la detección de anomalías (mediana y MAD).",
                        Map.of(), List.of()),
                herramienta("rechazos_y_correcciones",
                        "Rechazos del supervisor con su comentario, y correcciones de resultados (versión nueva) con su motivo.",
                        Map.of(), List.of()),
                herramienta("informes_emitidos",
                        "Informes PDF emitidos: muestra, versión, fecha, quién lo emitió y código de verificación de la firma.",
                        Map.of(), List.of()),
                herramienta("sitios_de_muestreo",
                        "Sitios de muestreo con coordenadas, cantidad de muestras, último muestreo y resultados fuera de rango.",
                        Map.of(), List.of()));
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
            case "buscar_muestras" -> leer(() -> datos.buscarMuestras(texto(entrada.get("texto")), texto(entrada.get("estado"))));
            case "trazabilidad_muestra" -> leer(() -> datos.trazabilidadMuestra(texto(entrada.get("codigo"))));
            case "consultar_equipo" -> leer(() -> datos.consultarEquipo(texto(entrada.get("codigo"))));
            case "listar_equipos" -> leer(datos::listarEquipos);
            case "resumen_laboratorio" -> leer(datos::resumenGeneral);
            case "pendientes_de_aprobacion" -> leer(() -> datos.pendientesDeAprobacion(ConsultasTrazabilidad.Filtro.ninguno()));
            case "resultados_fuera_de_rango" -> leer(() -> datos.fueraDeRango(ConsultasTrazabilidad.Filtro.ninguno()));
            case "valores_atipicos" -> leer(() -> datos.atipicos(ConsultasTrazabilidad.Filtro.ninguno()));
            case "rechazos_y_correcciones" -> leer(() -> datos.rechazos(ConsultasTrazabilidad.Filtro.ninguno())
                    + "\n\n" + datos.correcciones(ConsultasTrazabilidad.Filtro.ninguno()));
            case "informes_emitidos" -> leer(() -> datos.informes(ConsultasTrazabilidad.Filtro.ninguno()));
            case "sitios_de_muestreo" -> leer(() -> datos.resumenSitios(ConsultasTrazabilidad.Filtro.ninguno()));
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
    // Modo directo (sin IA): reglas sobre la pregunta (ver ModoDirectoAsistente)
    // =====================================================================

    private AsistenteResponseDTO modoDirecto(AsistenteRequestDTO dto, List<String> consultas, String aviso) {
        String respuesta = soloLectura.execute(estado ->
                modoDirecto.responder(dto.getPregunta().trim(), dto.getHistorial(), consultas));
        return new AsistenteResponseDTO(respuesta, ORIGEN_DIRECTO, consultas, aviso);
    }

    private String leer(Supplier<String> consulta) {
        return soloLectura.execute(estado -> consulta.get());
    }

    private String texto(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }
}
