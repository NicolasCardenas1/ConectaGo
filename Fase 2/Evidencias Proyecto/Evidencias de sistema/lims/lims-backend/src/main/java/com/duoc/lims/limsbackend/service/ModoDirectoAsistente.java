package com.duoc.lims.limsbackend.service;

import com.duoc.lims.limsbackend.dto.AsistenteRequestDTO;
import com.duoc.lims.limsbackend.model.AnalisisCatalogo;
import com.duoc.lims.limsbackend.model.Equipo;
import com.duoc.lims.limsbackend.model.Investigador;
import com.duoc.lims.limsbackend.model.SitioMuestreo;
import com.duoc.lims.limsbackend.model.enums.EstadoMuestra;
import com.duoc.lims.limsbackend.model.enums.TipoMuestra;
import com.duoc.lims.limsbackend.service.ConsultasTrazabilidad.Aspecto;
import com.duoc.lims.limsbackend.service.ConsultasTrazabilidad.Filtro;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Modo directo del asistente (sin IA): interpreta la pregunta con reglas y responde con las consultas
 * de {@link ConsultasTrazabilidad}. No necesita API key ni internet.
 *
 * Reconoce, sin importar tildes ni mayúsculas:
 * - Códigos de muestra (M-2026-0012), de equipo (EQ-005) y de sitio (ST-001). Para una muestra
 *   responde solo lo que se preguntó (dónde está guardada, con qué equipo, quién aprobó...).
 * - Temas: resumen, pendientes de aprobación, fuera de rango, atípicos, rechazos, correcciones,
 *   informes, equipos y calibración, sitios, investigadores, almacenamiento, conservación y
 *   análisis sin resultado.
 * - Filtros que se combinan con cualquier tema: sitio (por nombre o comuna), investigador, tipo de
 *   muestra, estado, parámetro (salinidad, pH...), lugar de almacenamiento y fecha (hoy, esta semana,
 *   este mes, en junio, últimos 15 días...).
 * - Preguntas de seguimiento: "¿y dónde está guardada?" usa la última muestra nombrada en la conversación.
 */
@Component
public class ModoDirectoAsistente {

    private static final Pattern CODIGO_MUESTRA = Pattern.compile("\\b([A-Za-z]{1,4}-\\d{4}-\\d{3,6})\\b");
    private static final Pattern CODIGO_EQUIPO = Pattern.compile("\\b(EQ-\\d{1,5})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CODIGO_SITIO = Pattern.compile("\\b(ST-\\d{1,5})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ULTIMOS_DIAS = Pattern.compile("\\bultim[oa]s? (\\d{1,3}) dias\\b");
    private static final Pattern ANIO = Pattern.compile("\\b(20\\d{2})\\b");

    /** Palabras de los nombres de sitio que no sirven para distinguirlos. */
    private static final Set<String> GENERICAS_SITIO = Set.of("bahia", "isla", "sector", "norte", "sur", "este",
            "oeste", "estacion", "lago", "canal", "caleta", "estuario", "seno", "playa", "costa", "puerto", "rio",
            "punta", "centro", "sitio", "zona", "punto", "region", "los", "las", "del", "de", "la", "el");

    /** Palabras que no se usan en la búsqueda libre. */
    private static final Set<String> VACIAS = Set.of("que", "cual", "cuales", "cuantas", "cuantos", "como",
            "donde", "cuando", "quien", "quienes", "muestra", "muestras", "hay", "tiene", "tienen", "esta", "estan",
            "este", "esto", "esa", "ese", "esas", "esos", "para", "por", "con", "sin", "del", "las", "los", "una",
            "uno", "unos", "unas", "son", "fue", "fueron", "ver", "mostrar", "muestrame", "dame", "lista", "listar",
            "todas", "todos", "sobre", "datos", "informacion", "laboratorio", "sistema", "favor", "puedes", "quiero",
            "saber", "necesito", "busca", "buscar", "algun", "alguna", "existe", "existen", "registro", "registrada",
            "registradas", "tomada", "tomadas", "tomaron", "hola", "gracias");

    private final ConsultasTrazabilidad consultas;

    public ModoDirectoAsistente(ConsultasTrazabilidad consultas) {
        this.consultas = consultas;
    }

    /** Respuesta en texto. {@code realizadas} recibe la descripción de cada consulta ejecutada. */
    public String responder(String pregunta, List<AsistenteRequestDTO.Mensaje> historial, List<String> realizadas) {
        String n = normalizar(pregunta);
        List<String> partes = new ArrayList<>();

        // 1) Códigos explícitos
        Set<Aspecto> aspectos = aspectos(n);
        for (String codigo : codigos(CODIGO_EQUIPO, pregunta)) {
            realizadas.add("consultar_equipo (codigo=" + codigo + ")");
            partes.add(consultas.consultarEquipo(codigo));
        }
        for (String codigo : codigos(CODIGO_MUESTRA, pregunta)) {
            if (codigo.startsWith("EQ-") || codigo.startsWith("ST-")) {
                continue;
            }
            realizadas.add("trazabilidad_muestra (codigo=" + codigo + describirAspectos(aspectos) + ")");
            partes.add(consultas.trazabilidadMuestra(codigo, aspectos));
        }
        if (!partes.isEmpty()) {
            return String.join("\n\n", partes);
        }

        Filtro filtro = filtro(n, pregunta);
        boolean pideLista = contiene(n, "muestras", "cuales", "cuantas", "cuantos", "listado", "lista", "todas", "todos");

        // 2) Saludo o ayuda
        if (n.isBlank() || coincide(n, "^(hola|buenas|buenos dias|buenas tardes|ayuda|help)\\b.{0,20}$")
                || contiene(n, "que puedes", "que sabes", "que preguntas", "como funciona", "como te uso", "que haces")) {
            realizadas.add("ayuda");
            return ayuda(null);
        }

        // 3) Seguimiento: "¿y dónde está guardada?" sin código → la última muestra de la conversación
        if (!aspectos.isEmpty() && !pideLista && filtro.sinFiltrosDeMuestra() && filtro.analisis().isEmpty()
                && !contiene(n, "pendiente", "rango", "atipic", "equipos", "informes", "sitios", "investigadores", "vencid")) {
            Optional<String> previa = ultimaMuestraMencionada(historial);
            if (previa.isPresent()) {
                realizadas.add("trazabilidad_muestra (codigo=" + previa.get() + describirAspectos(aspectos) + ")");
                return consultas.trazabilidadMuestra(previa.get(), aspectos);
            }
        }

        // 4) Temas
        if (contiene(n, "equipo", "calibra", "instrumento", "balanza", "sonda", "phmetro", "espectrofotometro")) {
            Set<String> estados = new LinkedHashSet<>();
            if (contiene(n, "vencid", "caducad", "expirad", "no vigente", "descalibrad", "sin calibrar")) estados.add("Vencida");
            if (contiene(n, "por vencer", "proxim", "pronto", "a vencer", "vence")) estados.add("Por vencer");
            if (contiene(n, "vigente", "al dia", "calibrados") && !contiene(n, "no vigente")) estados.add("Vigente");
            if (contiene(n, "sin registro", "nunca", "sin calibracion")) estados.add("Sin registro");
            String uso = contiene(n, "terreno", "campo") ? Equipo.USO_TERRENO
                    : contiene(n, "de laboratorio") ? Equipo.USO_LABORATORIO : null;
            realizadas.add("listar_equipos" + (estados.isEmpty() ? "" : " (calibración=" + String.join("/", estados) + ")"));
            return consultas.listarEquipos(estados, uso);
        }
        if (contiene(n, "resumen", "panorama", "estado general", "como va", "como estamos", "estadistica", "indicador")
                || (coincide(n, "\\bcuant[ao]s muestras (hay|tenemos|existen|se han)\\b") && filtro.sinFiltrosDeMuestra())) {
            if (filtro.sinFiltrosDeMuestra()) {
                realizadas.add("resumen_general");
                return consultas.resumenGeneral();
            }
            realizadas.add("muestras" + filtro.sufijo());
            return consultas.muestrasFiltradas(filtro);
        }
        if (contiene(n, "aprob", "revis", "supervis", "validar") && contiene(n, "pendiente", "falta", "por aprob",
                "por revis", "por valid", "sin aprob", "sin revis", "sin valid", "espera", "debo", "tengo que",
                "hay que", "quedan")) {
            realizadas.add("pendientes_de_aprobacion" + filtro.sufijo());
            return consultas.pendientesDeAprobacion(filtro);
        }
        if (contiene(n, "fuera de rango", "fuera del rango", "fuera rango", "fuera de los rangos", "fuera de norma",
                "sobre el rango", "bajo el rango", "excede", "anormal")) {
            realizadas.add("fuera_de_rango" + filtro.sufijo());
            return consultas.fueraDeRango(filtro);
        }
        if (contiene(n, "atipic", "anomal", "raro", "raros", "sospechos", "inusual", "outlier")) {
            realizadas.add("valores_atipicos" + filtro.sufijo());
            return consultas.atipicos(filtro);
        }
        // "rechazos", "resultados rechazados" → los rechazos; "macroalgas rechazadas" → muestras en ese estado
        if (contiene(n, "rechaz") && !filtro.estados().contains(EstadoMuestra.RECHAZADA)) {
            realizadas.add("rechazos" + filtro.sufijo());
            return consultas.rechazos(filtro);
        }
        if (contiene(n, "correg", "correcci", "modificad", "cambiad", "versiones", "segunda version")) {
            realizadas.add("correcciones" + filtro.sufijo());
            return consultas.correcciones(filtro);
        }
        if (contiene(n, "informe", "reporte", "pdf", "firmad", "firma digital", "emitid")
                && !contiene(n, "sin informe", "sin reporte")) {
            realizadas.add("informes" + filtro.sufijo());
            return consultas.informes(filtro);
        }
        if (contiene(n, "conservaci", "plazo", "descartar", "eliminar la muestra", "botar")
                || coincide(n, "\\bmuestras? (vencidas|por vencer|que vencen)\\b")) {
            realizadas.add("conservacion" + filtro.sufijo());
            return consultas.conservacion(filtro);
        }
        if (contiene(n, "sin resultado", "falta medir", "faltan medir", "por medir", "por analizar", "sin medir",
                "analisis pendiente", "analisis en proceso", "falta analizar", "faltan analizar")) {
            realizadas.add("analisis_sin_resultado" + filtro.sufijo());
            return consultas.analisisSinResultado(filtro);
        }
        if (!filtro.analisis().isEmpty() || contiene(n, "promedio", "maximo", "minimo", "tendencia", "valores de", "evolucion")) {
            if (filtro.analisis().isEmpty()) {
                return "¿De qué parámetro? Puedo mostrar los valores de: " + nombresParametros()
                        + ". Ej.: \"salinidad en Bahía Ilque\".";
            }
            realizadas.add("valores_parametro" + filtro.sufijo());
            return consultas.valoresDeParametro(filtro);
        }
        if (contiene(n, "sitio", "lugares", "puntos de muestreo", "mapa", "coordenada", "donde se muestrea")
                || CODIGO_SITIO.matcher(pregunta).find()) {
            realizadas.add("sitios" + filtro.sufijo());
            if (filtro.sitios().isEmpty()) {
                return consultas.resumenSitios(filtro);
            }
            // un sitio concreto: su ficha y sus muestras
            return consultas.resumenSitios(filtro) + "\n\n" + consultas.muestrasFiltradas(filtro);
        }
        if (contiene(n, "investigador", "investigadora", "cliente", "quien encarga", "quienes encargan")
                && filtro.investigadores().isEmpty()) {
            realizadas.add("investigadores");
            return consultas.resumenInvestigadores(filtro);
        }
        if (contiene(n, "guardad", "almacen", "ubicacion", "ubicaciones", "donde estan", "refrigerador", "congelador",
                "estante", "rack")) {
            realizadas.add("almacenamiento" + filtro.sufijo());
            return consultas.almacenamiento(filtro);
        }

        // 5) Muestras con filtros (sitio, investigador, tipo, estado, fecha...)
        if (!filtro.sinFiltrosDeMuestra()) {
            realizadas.add("muestras" + filtro.sufijo());
            return consultas.muestrasFiltradas(filtro);
        }
        // 6) Búsqueda libre por palabras (institución, procedencia, observaciones...)
        List<String> palabras = palabrasClave(n);
        if (!palabras.isEmpty()) {
            Optional<String> encontrado = consultas.buscarPorPalabras(palabras);
            if (encontrado.isPresent()) {
                realizadas.add("buscar_muestras (texto=" + String.join(" ", palabras) + ")");
                return encontrado.get();
            }
        }
        if (pideLista || contiene(n, "ultimas", "recientes", "muestras")) {
            realizadas.add("muestras recientes");
            return consultas.muestrasFiltradas(filtro);
        }
        realizadas.add("ayuda");
        return ayuda("No encontré datos para esa pregunta.");
    }

    // =====================================================================
    // Reconocimiento
    // =====================================================================

    private Set<Aspecto> aspectos(String n) {
        Set<Aspecto> a = EnumSet.noneOf(Aspecto.class);
        if (contiene(n, "trazabilidad", "historia", "todo sobre", "completa", "completo", "detalle")) {
            return a;   // vacío = historia completa
        }
        if (contiene(n, "guardad", "almacen", "ubicad", "ubicacion", "donde esta", "donde se encuentra", "donde queda",
                "refrigerador", "congelador", "conservar", "vence")) a.add(Aspecto.UBICACION);
        if (contiene(n, "equipo", "calibra", "instrumento", "sonda", "balanza")) a.add(Aspecto.EQUIPO);
        if (contiene(n, "donde se tomo", "donde la tomaron", "donde fue tomada", "sitio", "coordenada", "cuando se tomo",
                "cuando la tomaron", "fecha de toma", "muestreo", "procedencia", "de donde viene", "lugar")) a.add(Aspecto.TOMA);
        if (contiene(n, "resultado", "valor", "medicion", "medida", "analisis", "rango", "atipic")
                || coincide(n, "\\b(dio|salio|salieron|midio)\\b")) a.add(Aspecto.RESULTADOS);
        if (contiene(n, "aprob", "rechaz", "supervis", "valid", "correg", "correcci")) a.add(Aspecto.APROBACIONES);
        if (contiene(n, "informe", "reporte", "pdf", "firma", "emitid")) a.add(Aspecto.INFORMES);
        if (contiene(n, "custodia", "recibio", "recibida", "recepcion", "traslad", "movimiento", "entrego", "quien la tiene")) a.add(Aspecto.CUSTODIA);
        if (contiene(n, "investigador", "encargo", "quien la pidio", "solicito", "cliente", "proyecto", "de quien es")) a.add(Aspecto.INVESTIGADOR);
        return a;
    }

    private Filtro filtro(String n, String original) {
        List<String> descripcion = new ArrayList<>();

        Set<Integer> sitios = new LinkedHashSet<>();
        List<String> nombresSitio = new ArrayList<>();
        Set<String> codigosSitio = new LinkedHashSet<>(codigos(CODIGO_SITIO, original));
        for (SitioMuestreo s : consultas.sitios()) {
            String comuna = normalizar(s.getComuna());
            if (!comuna.isEmpty() && contienePalabra(n, comuna)) {
                // por comuna: se nombra la comuna, no cada sitio
                sitios.add(s.getId());
                String texto = "la comuna de " + capitalizar(s.getComuna());
                if (nombresSitio.stream().noneMatch(x -> normalizar(x).equals(normalizar(texto)))) {
                    nombresSitio.add(texto);
                }
            } else if (codigosSitio.contains(s.getCodigo().toUpperCase(Locale.ROOT))
                    || palabrasDistintivas(s.getNombre()).stream().anyMatch(p -> contienePalabra(n, p))) {
                sitios.add(s.getId());
                nombresSitio.add(s.getNombre());
            }
        }
        if (!nombresSitio.isEmpty()) {
            descripcion.add("en " + unir(nombresSitio));
        }

        Set<Integer> investigadores = new LinkedHashSet<>();
        List<String> nombresInv = new ArrayList<>();
        for (Investigador i : consultas.investigadores()) {
            String nom = normalizar(i.getNombre());
            String ape = normalizar(i.getApellido());
            boolean coincide = (ape.length() >= 3 && contienePalabra(n, ape))
                    || (nom.length() >= 3 && contienePalabra(n, nom) && contiene(n, "investigador", "de " + nom, nom + " "));
            if (coincide) {
                investigadores.add(i.getId());
                nombresInv.add(i.getNombreCompleto());
            }
        }
        if (!nombresInv.isEmpty()) {
            descripcion.add("de " + unir(nombresInv));
        }

        Set<Integer> analisis = new LinkedHashSet<>();
        List<String> nombresAnalisis = new ArrayList<>();
        for (AnalisisCatalogo a : consultas.analisisDelCatalogo()) {
            String clave = clave(a.getNombre());
            if (!clave.isEmpty() && (contienePalabra(n, clave) || contienePalabra(n, normalizar(a.getNombre())))) {
                analisis.add(a.getId());
                nombresAnalisis.add(a.getNombre());
            }
        }
        if (contienePalabra(n, "oxigeno") || contienePalabra(n, "o2")) {
            // ya cubierto por la clave "oxigeno"; "o2" es un sinónimo frecuente
            consultas.analisisDelCatalogo().stream().filter(a -> clave(a.getNombre()).equals("oxigeno"))
                    .forEach(a -> { if (analisis.add(a.getId())) nombresAnalisis.add(a.getNombre()); });
        }
        String descripcionAnalisis = nombresAnalisis.isEmpty() ? "" : "de " + unir(nombresAnalisis);

        Set<TipoMuestra> tipos = EnumSet.noneOf(TipoMuestra.class);
        if (contiene(n, "macroalga")) tipos.add(TipoMuestra.MACROALGA);
        if (contiene(n, "microalga", "fitoplancton")) tipos.add(TipoMuestra.MICROALGA);
        if (coincide(n, "\\balgas?\\b")) { tipos.add(TipoMuestra.MACROALGA); tipos.add(TipoMuestra.MICROALGA); }
        if (contiene(n, "agua de mar", "agua marina", "agua salada")) tipos.add(TipoMuestra.AGUA_DE_MAR);
        if (contiene(n, "agua dulce")) tipos.add(TipoMuestra.AGUA_DULCE);
        if (coincide(n, "\\b(muestras? de agua|aguas)\\b") && !contiene(n, "agua de mar", "agua dulce")) {
            tipos.add(TipoMuestra.AGUA_DE_MAR);
            tipos.add(TipoMuestra.AGUA_DULCE);
        }
        if (contiene(n, "sedimento")) tipos.add(TipoMuestra.SEDIMENTO);
        if (!tipos.isEmpty()) {
            descripcion.add("de tipo " + unir(tipos.stream().map(TipoMuestra::getValorDb).toList()).toLowerCase(Locale.ROOT));
        }

        Set<EstadoMuestra> estados = EnumSet.noneOf(EstadoMuestra.class);
        // "recibidas esta semana" habla de la fecha, no del estado
        if (coincide(n, "\\brecibidas?\\b") && fechas(n) == null) estados.add(EstadoMuestra.RECIBIDA);
        if (coincide(n, "\\b(en analisis|analizandose|en proceso)\\b")) estados.add(EstadoMuestra.EN_ANALISIS);
        if (contiene(n, "resultados ingresados")) estados.add(EstadoMuestra.RESULTADOS_INGRESADOS);
        if (coincide(n, "\\baprobadas?\\b")) estados.add(EstadoMuestra.APROBADA);
        if (coincide(n, "\\brechazadas?\\b")) estados.add(EstadoMuestra.RECHAZADA);
        if (coincide(n, "\\b(reportadas?|informadas?|terminadas?|finalizadas?|cerradas?)\\b")) estados.add(EstadoMuestra.REPORTADA);
        if (coincide(n, "\\bmuestras? (pendientes(?! de (aprob|revis|valid))|en curso|abiertas|sin terminar|sin informe|activas)\\b")) {
            estados.addAll(EnumSet.of(EstadoMuestra.RECIBIDA, EstadoMuestra.EN_ANALISIS,
                    EstadoMuestra.RESULTADOS_INGRESADOS, EstadoMuestra.APROBADA, EstadoMuestra.RECHAZADA));
        }
        if (!estados.isEmpty() && estados.size() < EstadoMuestra.values().length) {
            descripcion.add(estados.size() == 5 ? "aún sin informe"
                    : "en estado " + unir(estados.stream().map(ConsultasTrazabilidad::estadoTexto).toList()));
        }

        List<String> ubicaciones = new ArrayList<>();
        for (String u : consultas.ubicacionesUsadas()) {
            String primera = normalizar(u).split("[^a-z0-9]+")[0];
            if (primera.length() >= 5 && contienePalabra(n, primera) && !ubicaciones.contains(primera)) {
                ubicaciones.add(primera);
            }
        }
        if (!ubicaciones.isEmpty()) {
            descripcion.add("en " + unir(ubicaciones));
        }

        LocalDate[] rango = fechas(n);
        if (rango != null) {
            descripcion.add(describirFechas(rango[0], rango[1], n));
        }

        return new Filtro(sitios, investigadores, tipos, estados, analisis, ubicaciones,
                rango == null ? null : rango[0], rango == null ? null : rango[1], String.join(", ", descripcion),
                descripcionAnalisis);
    }

    /** Rango de fechas mencionado en la pregunta, o null. */
    private LocalDate[] fechas(String n) {
        LocalDate hoy = LocalDate.now();
        Matcher dias = ULTIMOS_DIAS.matcher(n);
        if (dias.find()) {
            return new LocalDate[]{hoy.minusDays(Long.parseLong(dias.group(1))), hoy};
        }
        if (contienePalabra(n, "hoy")) return new LocalDate[]{hoy, hoy};
        if (contienePalabra(n, "ayer")) return new LocalDate[]{hoy.minusDays(1), hoy.minusDays(1)};
        if (contiene(n, "esta semana", "ultima semana", "semana pasada")) return new LocalDate[]{hoy.minusDays(7), hoy};
        if (contiene(n, "este mes")) return new LocalDate[]{hoy.withDayOfMonth(1), hoy};
        if (contiene(n, "ultimo mes", "mes pasado")) return new LocalDate[]{hoy.minusMonths(1), hoy};
        if (contiene(n, "este ano", "este año")) return new LocalDate[]{hoy.withDayOfYear(1), hoy};
        Matcher anio = ANIO.matcher(n.replaceAll("[a-z]{1,4}-20\\d{2}-\\d+", " "));
        Integer anioExplicito = anio.find() ? Integer.parseInt(anio.group(1)) : null;
        for (int mes = 1; mes <= 12; mes++) {
            String nombre = normalizar(java.time.Month.of(mes).getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es")));
            if (contienePalabra(n, nombre)) {
                int a = anioExplicito != null ? anioExplicito : (mes > hoy.getMonthValue() ? hoy.getYear() - 1 : hoy.getYear());
                YearMonth ym = YearMonth.of(a, mes);
                return new LocalDate[]{ym.atDay(1), ym.atEndOfMonth()};
            }
        }
        if (anioExplicito != null) {
            return new LocalDate[]{LocalDate.of(anioExplicito, 1, 1), LocalDate.of(anioExplicito, 12, 31)};
        }
        return null;
    }

    private String describirFechas(LocalDate desde, LocalDate hasta, String n) {
        if (desde.equals(hasta)) {
            return contienePalabra(n, "hoy") ? "de hoy" : contienePalabra(n, "ayer") ? "de ayer" : "del " + desde;
        }
        if (desde.getDayOfMonth() == 1 && hasta.equals(YearMonth.from(desde).atEndOfMonth())) {
            return "de " + desde.getMonth().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es")) + " " + desde.getYear();
        }
        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");
        return "entre el " + desde.format(f) + " y el " + hasta.format(f);
    }

    private Optional<String> ultimaMuestraMencionada(List<AsistenteRequestDTO.Mensaje> historial) {
        if (historial == null) {
            return Optional.empty();
        }
        for (int i = historial.size() - 1; i >= 0; i--) {
            AsistenteRequestDTO.Mensaje m = historial.get(i);
            if (m == null || m.getTexto() == null) {
                continue;
            }
            // en la respuesta del asistente, la muestra consultada es la primera; en la del usuario, la última
            List<String> encontrados = codigos(CODIGO_MUESTRA, m.getTexto()).stream()
                    .filter(c -> !c.startsWith("EQ-") && !c.startsWith("ST-"))
                    .toList();
            if (encontrados.size() == 1 || (!encontrados.isEmpty() && "usuario".equalsIgnoreCase(m.getRol()))) {
                return Optional.of(encontrados.get(encontrados.size() - 1));
            }
            if (!encontrados.isEmpty() && m.getTexto().startsWith("Muestra ")) {
                return Optional.of(encontrados.get(0));
            }
        }
        return Optional.empty();
    }

    private String ayuda(String motivo) {
        String ejemplo = consultas.sitios().stream().findFirst().map(s -> primeraDistintiva(s.getNombre())).orElse("Bahía Ilque");
        return (motivo == null ? "" : motivo + " ") + """
                Sin IA puedo responder preguntas como estas:
                - Una muestra por su código: "trazabilidad de H-2026-0001", "¿dónde está guardada la H-2026-0001?", \
                "¿con qué equipo se midió?", "¿quién la aprobó?". Después puedes seguir preguntando sin repetir el código.
                - Un equipo: "EQ-005", "¿qué equipos tienen la calibración vencida o por vencer?".
                - Pendientes: "¿qué resultados faltan por aprobar?", "¿qué análisis están sin resultado?".
                - Calidad: "resultados fuera de rango", "valores atípicos", "rechazos", "correcciones".
                - Muestras por sitio, tipo, estado, investigador o fecha: "muestras de %s", "macroalgas rechazadas", \
                "muestras de junio".
                - Parámetros: "salinidad en %s", "promedio de pH en junio".
                - Otros: "resumen", "sitios", "investigadores", "informes emitidos", "¿qué hay en el refrigerador?", \
                "muestras con plazo de conservación por vencer".
                Con la API key de Claude configurada, el asistente entiende además preguntas libres.""".formatted(ejemplo, ejemplo);
    }

    private String nombresParametros() {
        return unir(consultas.analisisDelCatalogo().stream().map(AnalisisCatalogo::getNombre).toList());
    }

    private String describirAspectos(Set<Aspecto> aspectos) {
        return aspectos.isEmpty() ? "" : ", aspecto=" + String.join("/", aspectos.stream()
                .map(a -> a.name().toLowerCase(Locale.ROOT)).toList());
    }

    // =====================================================================
    // Texto
    // =====================================================================

    /** Minúsculas, sin tildes y con los espacios normalizados. */
    static String normalizar(String s) {
        if (s == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).replaceAll("[¿?¡!.,;:()\"']", " ").replaceAll("\\s+", " ").trim();
    }

    /** {@code palabra} aparece como palabra completa (o frase) en el texto normalizado. */
    static boolean contienePalabra(String texto, String palabra) {
        if (palabra == null || palabra.isBlank()) {
            return false;
        }
        return Pattern.compile("(^|[^a-z0-9])" + Pattern.quote(palabra) + "($|[^a-z0-9])").matcher(texto).find();
    }

    private static boolean contiene(String texto, String... trozos) {
        for (String t : trozos) {
            if (texto.contains(t)) {
                return true;
            }
        }
        return false;
    }

    private static boolean coincide(String texto, String regex) {
        return Pattern.compile(regex).matcher(texto).find();
    }

    private static List<String> codigos(Pattern patron, String texto) {
        List<String> lista = new ArrayList<>();
        Matcher m = patron.matcher(texto);
        while (m.find()) {
            String c = m.group(1).toUpperCase(Locale.ROOT);
            if (!lista.contains(c)) {
                lista.add(c);
            }
        }
        return lista;
    }

    private static List<String> palabrasDistintivas(String nombreSitio) {
        List<String> l = new ArrayList<>();
        for (String p : normalizar(nombreSitio).split("[^a-z0-9]+")) {
            if (p.length() >= 4 && !GENERICAS_SITIO.contains(p)) {
                l.add(p);
            }
        }
        return l;
    }

    private static String primeraDistintiva(String nombreSitio) {
        List<String> l = palabrasDistintivas(nombreSitio);
        if (l.isEmpty()) {
            return nombreSitio;
        }
        String p = l.get(0);
        return Character.toUpperCase(p.charAt(0)) + p.substring(1);
    }

    /** Clave corta de un parámetro: "Clorofila-a" → "clorofila", "Oxígeno disuelto" → "oxigeno", "pH" → "ph". */
    private static String clave(String nombreAnalisis) {
        String[] partes = normalizar(nombreAnalisis).split("[^a-z0-9]+");
        return partes.length == 0 ? "" : partes[0];
    }

    private static List<String> palabrasClave(String n) {
        List<String> l = new ArrayList<>();
        for (String p : n.split("[^a-z0-9]+")) {
            if (p.length() >= 4 && !VACIAS.contains(p) && !l.contains(p)) {
                l.add(p);
            }
        }
        return l;
    }

    /** "PUERTO MONTT" o "puerto montt" → "Puerto Montt". */
    private static String capitalizar(String s) {
        StringBuilder sb = new StringBuilder();
        for (String p : s.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            if (!p.isEmpty()) {
                sb.append(sb.length() == 0 ? "" : " ").append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
            }
        }
        return sb.toString();
    }

    private static String unir(List<String> l) {
        if (l.size() <= 1) {
            return String.join("", l);
        }
        return String.join(", ", l.subList(0, l.size() - 1)) + " o " + l.get(l.size() - 1);
    }
}
