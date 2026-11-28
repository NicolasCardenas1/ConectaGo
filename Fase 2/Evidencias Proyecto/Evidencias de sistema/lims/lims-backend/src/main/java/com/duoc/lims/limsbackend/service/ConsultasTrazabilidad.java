package com.duoc.lims.limsbackend.service;

import com.duoc.lims.limsbackend.dto.CalibracionResponseDTO;
import com.duoc.lims.limsbackend.dto.TrazabilidadEventoDTO;
import com.duoc.lims.limsbackend.model.AnalisisCatalogo;
import com.duoc.lims.limsbackend.model.Aprobacion;
import com.duoc.lims.limsbackend.model.Equipo;
import com.duoc.lims.limsbackend.model.Investigador;
import com.duoc.lims.limsbackend.model.Muestra;
import com.duoc.lims.limsbackend.model.MuestraAnalisis;
import com.duoc.lims.limsbackend.model.Reporte;
import com.duoc.lims.limsbackend.model.Resultado;
import com.duoc.lims.limsbackend.model.ResultadoVersion;
import com.duoc.lims.limsbackend.model.SitioMuestreo;
import com.duoc.lims.limsbackend.model.Usuario;
import com.duoc.lims.limsbackend.model.enums.EstadoAprobacion;
import com.duoc.lims.limsbackend.model.enums.EstadoMuestra;
import com.duoc.lims.limsbackend.model.enums.EstadoMuestraAnalisis;
import com.duoc.lims.limsbackend.model.enums.TipoMuestra;
import com.duoc.lims.limsbackend.repository.AnalisisCatalogoRepository;
import com.duoc.lims.limsbackend.repository.AprobacionRepository;
import com.duoc.lims.limsbackend.repository.EquipoRepository;
import com.duoc.lims.limsbackend.repository.InvestigadorRepository;
import com.duoc.lims.limsbackend.repository.MuestraAnalisisRepository;
import com.duoc.lims.limsbackend.repository.MuestraRepository;
import com.duoc.lims.limsbackend.repository.ReporteRepository;
import com.duoc.lims.limsbackend.repository.ResultadoRepository;
import com.duoc.lims.limsbackend.repository.ResultadoVersionRepository;
import com.duoc.lims.limsbackend.repository.SitioMuestreoRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Consultas de SOLO LECTURA del asistente de trazabilidad. Cada método devuelve texto plano listo
 * para mostrar en el chat (modo directo) o para entregárselo a Claude como resultado de una herramienta.
 * Deben llamarse dentro de una transacción de solo lectura (las relaciones de las entidades son perezosas).
 */
@Component
public class ConsultasTrazabilidad {

    static final int MAX_RESULTADOS = 25;
    private static final int DIAS_AVISO_CONSERVACION = 30;
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    /** Partes de la historia de una muestra que se pueden pedir por separado. */
    public enum Aspecto { TOMA, EQUIPO, UBICACION, CUSTODIA, INVESTIGADOR, RESULTADOS, APROBACIONES, INFORMES }

    /**
     * Filtros reconocidos en la pregunta. Un conjunto vacío o una fecha nula significan "sin filtro".
     * Las fechas son inclusivas. {@code descripcion} es el texto para el encabezado ("en Bahía Ilque, de junio")
     * y {@code descripcionAnalisis} el de los parámetros ("de Salinidad"), aparte para no repetirlo.
     */
    public record Filtro(Set<Integer> sitios, Set<Integer> investigadores, Set<TipoMuestra> tipos,
                         Set<EstadoMuestra> estados, Set<Integer> analisis, List<String> ubicaciones,
                         LocalDate desde, LocalDate hasta, String descripcion, String descripcionAnalisis) {

        public static Filtro ninguno() {
            return new Filtro(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), List.of(), null, null, "", "");
        }

        public boolean sinFiltrosDeMuestra() {
            return sitios.isEmpty() && investigadores.isEmpty() && tipos.isEmpty() && estados.isEmpty()
                    && ubicaciones.isEmpty() && desde == null && hasta == null;
        }

        /** La muestra cumple los filtros; con {@code conFecha} también se exige la fecha (de toma o recepción). */
        boolean acepta(Muestra m, boolean conFecha) {
            if (!sitios.isEmpty() && (m.getSitio() == null || !sitios.contains(m.getSitio().getId()))) return false;
            if (!investigadores.isEmpty()
                    && (m.getInvestigador() == null || !investigadores.contains(m.getInvestigador().getId()))) return false;
            if (!tipos.isEmpty() && !tipos.contains(m.getTipoMuestra())) return false;
            if (!estados.isEmpty() && !estados.contains(m.getEstado())) return false;
            if (!ubicaciones.isEmpty()) {
                String u = ModoDirectoAsistente.normalizar(m.getUbicacionAlmacenamiento());
                if (ubicaciones.stream().noneMatch(x -> ModoDirectoAsistente.contienePalabra(u, x))) return false;
            }
            return !conFecha || enFecha(fechaMuestra(m));
        }

        boolean enFecha(LocalDateTime f) {
            if (desde == null && hasta == null) return true;
            if (f == null) return false;
            LocalDate d = f.toLocalDate();
            return (desde == null || !d.isBefore(desde)) && (hasta == null || !d.isAfter(hasta));
        }

        /** Texto de todos los filtros, para el encabezado de una lista. */
        String sufijo() {
            String a = descripcionAnalisis == null ? "" : descripcionAnalisis;
            String d = descripcion == null ? "" : descripcion;
            String todo = a.isBlank() ? d : d.isBlank() ? a : a + ", " + d;
            return todo.isBlank() ? "" : " " + todo;
        }

        /** Igual que {@link #sufijo()} pero sin los parámetros (cuando el encabezado ya los nombra). */
        String sufijoSinAnalisis() {
            return descripcion == null || descripcion.isBlank() ? "" : " " + descripcion;
        }
    }

    private final MuestraRepository muestraRepository;
    private final EquipoRepository equipoRepository;
    private final ResultadoVersionRepository versionRepository;
    private final ResultadoRepository resultadoRepository;
    private final MuestraAnalisisRepository muestraAnalisisRepository;
    private final AprobacionRepository aprobacionRepository;
    private final ReporteRepository reporteRepository;
    private final SitioMuestreoRepository sitioRepository;
    private final InvestigadorRepository investigadorRepository;
    private final AnalisisCatalogoRepository analisisRepository;
    private final TrazabilidadService trazabilidadService;
    private final EquipoService equipoService;

    public ConsultasTrazabilidad(MuestraRepository muestraRepository, EquipoRepository equipoRepository,
                                 ResultadoVersionRepository versionRepository, ResultadoRepository resultadoRepository,
                                 MuestraAnalisisRepository muestraAnalisisRepository,
                                 AprobacionRepository aprobacionRepository, ReporteRepository reporteRepository,
                                 SitioMuestreoRepository sitioRepository, InvestigadorRepository investigadorRepository,
                                 AnalisisCatalogoRepository analisisRepository, TrazabilidadService trazabilidadService, EquipoService equipoService) {
        this.muestraRepository = muestraRepository;
        this.equipoRepository = equipoRepository;
        this.versionRepository = versionRepository;
        this.resultadoRepository = resultadoRepository;
        this.muestraAnalisisRepository = muestraAnalisisRepository;
        this.aprobacionRepository = aprobacionRepository;
        this.reporteRepository = reporteRepository;
        this.sitioRepository = sitioRepository;
        this.investigadorRepository = investigadorRepository;
        this.analisisRepository = analisisRepository;
        this.trazabilidadService = trazabilidadService;
        this.equipoService = equipoService;
    }

    // =====================================================================
    // Catálogos (los usa el intérprete del modo directo para reconocer nombres)
    // =====================================================================

    public List<SitioMuestreo> sitios() {
        return sitioRepository.findAllByOrderByCodigoAsc();
    }

    public List<Investigador> investigadores() {
        return investigadorRepository.findAllByOrderByApellidoAscNombreAsc();
    }

    public List<String> ubicacionesUsadas() {
        return muestraRepository.findAll().stream()
                .map(Muestra::getUbicacionAlmacenamiento)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    public List<AnalisisCatalogo> analisisDelCatalogo() {
        return analisisRepository.findAll().stream()
                .filter(a -> !Boolean.FALSE.equals(a.getActivo()))
                .toList();
    }

    // =====================================================================
    // Muestras
    // =====================================================================

    public String buscarMuestras(String texto, String estado) {
        String t = texto == null ? null : texto.toLowerCase(Locale.ROOT);
        List<Muestra> encontradas = muestraRepository.findAll().stream()
                .filter(m -> estado == null || m.getEstado().getValorDb().equalsIgnoreCase(estado))
                .filter(m -> t == null || textoBuscable(m).contains(t))
                .sorted(masRecientes())
                .toList();
        if (encontradas.isEmpty()) {
            return "No hay muestras que coincidan" + (texto == null ? "" : " con \"" + texto + "\"")
                    + (estado == null ? "" : " en estado " + estado) + ".";
        }
        return listaMuestras(encontradas, encontradas.size() + " muestra(s) encontrada(s)");
    }

    /** Muestras que cumplen los filtros reconocidos en la pregunta, con un conteo por estado. */
    public String muestrasFiltradas(Filtro f) {
        List<Muestra> encontradas = muestraRepository.findAll().stream()
                .filter(m -> f.acepta(m, true))
                .sorted(masRecientes())
                .toList();
        if (encontradas.isEmpty()) {
            return "No hay muestras" + f.sufijo() + ".";
        }
        String encabezado = encontradas.size() + " muestra(s)" + f.sufijo();
        if (f.estados().size() != 1) {
            encabezado += " (" + conteoPorEstado(encontradas) + ")";
        }
        return listaMuestras(encontradas, encabezado);
    }

    /** Búsqueda libre por varias palabras: gana la muestra que contiene más de ellas. */
    public Optional<String> buscarPorPalabras(List<String> palabras) {
        Map<Muestra, Long> aciertos = new LinkedHashMap<>();
        for (Muestra m : muestraRepository.findAll()) {
            String texto = ModoDirectoAsistente.normalizar(textoBuscable(m));
            long n = palabras.stream().filter(p -> ModoDirectoAsistente.contienePalabra(texto, p)).count();
            if (n > 0) {
                aciertos.put(m, n);
            }
        }
        if (aciertos.isEmpty()) {
            return Optional.empty();
        }
        long max = aciertos.values().stream().max(Long::compare).orElse(0L);
        List<Muestra> mejores = aciertos.entrySet().stream()
                .filter(e -> e.getValue() == max)
                .map(Map.Entry::getKey)
                .sorted(masRecientes())
                .toList();
        return Optional.of(listaMuestras(mejores, mejores.size() + " muestra(s) relacionada(s) con \""
                + String.join(" ", palabras) + "\""));
    }

    public String trazabilidadMuestra(String codigo) {
        return trazabilidadMuestra(codigo, EnumSet.noneOf(Aspecto.class));
    }

    /**
     * Historia de una muestra. Sin aspectos, la historia completa; con aspectos (ej. solo UBICACION),
     * el encabezado y lo que se preguntó.
     */
    public String trazabilidadMuestra(String codigo, Set<Aspecto> aspectos) {
        if (codigo == null) {
            return "Falta el código de la muestra.";
        }
        Optional<Muestra> encontrada = muestraRepository.findByCodigoUnicoIgnoreCase(codigo.trim());
        if (encontrada.isEmpty()) {
            return "No existe una muestra con el código " + codigo.trim().toUpperCase(Locale.ROOT)
                    + ". Puedes buscarla por sitio, tipo o investigador (ej. \"muestras de Bahía Ilque\").";
        }
        Muestra m = encontrada.get();
        boolean todo = aspectos.isEmpty();
        StringBuilder sb = new StringBuilder();
        sb.append("Muestra ").append(m.getCodigoUnico()).append(" (").append(m.getTipoMuestra().getValorDb())
                .append(", tipo de registro ").append(m.getTipoRegistro().getValorDb()).append(")\n");
        sb.append("- Estado actual: ").append(estadoTexto(m.getEstado())).append(" · prioridad ")
                .append(m.getPrioridad().getValorDb()).append("\n");

        if (todo || aspectos.contains(Aspecto.TOMA)) {
            SitioMuestreo s = m.getSitio();
            sb.append("- Dónde se tomó: ").append(s == null ? nn(m.getProcedencia())
                    : s.getCodigo() + " " + s.getNombre() + " (" + s.getTipoAmbiente() + ", " + nn(s.getComuna())
                    + "; lat " + coord(s.getLatitud()) + ", lon " + coord(s.getLongitud()) + ")"
                    + (m.getProcedencia() != null && !m.getProcedencia().equals(s.getNombre())
                            ? " · punto: " + m.getProcedencia() : "")).append("\n");
            sb.append("- Cuándo se tomó: ").append(fechaHora(m.getFechaToma())).append("\n");
        }
        if (todo || aspectos.contains(Aspecto.TOMA) || aspectos.contains(Aspecto.EQUIPO)) {
            Equipo eq = m.getEquipoToma();
            sb.append("- Equipo de toma: ").append(eq == null
                    ? "NO REGISTRADO (brecha de trazabilidad)"
                    : eq.getEtiqueta() + " (uso " + eq.getUso() + ")" + calibracionEn(eq, m.getFechaToma())).append("\n");
        }
        if (todo || aspectos.contains(Aspecto.INVESTIGADOR)) {
            sb.append("- Investigador que la encargó: ").append(m.getInvestigador() == null ? "no registrado"
                    : m.getInvestigador().getNombreCompleto() + " (" + nn(m.getInvestigador().getInstitucion()) + ")"
                    + (m.getInvestigador().getProyecto() == null ? "" : " · proyecto " + m.getInvestigador().getProyecto()))
                    .append("\n");
        }
        if (todo || aspectos.contains(Aspecto.CUSTODIA)) {
            sb.append("- Recibida en el laboratorio: ").append(fechaHora(m.getFechaRecepcion())).append(" por ")
                    .append(nombre(m.getUsuarioRegistro()))
                    .append(" · condición: ").append(nn(m.getCondicionRecepcion())).append("\n");
        }
        if (todo || aspectos.contains(Aspecto.UBICACION) || aspectos.contains(Aspecto.CUSTODIA)) {
            sb.append("- Dónde está guardada ahora: ").append(m.getUbicacionAlmacenamiento() == null ? "no registrado"
                    : m.getUbicacionAlmacenamiento()).append(" · conservar hasta: ")
                    .append(m.getFechaVencimiento() == null ? "no definido" : m.getFechaVencimiento().format(FECHA))
                    .append("\n");
        }
        if (!todo && (aspectos.contains(Aspecto.RESULTADOS) || aspectos.contains(Aspecto.EQUIPO))) {
            sb.append(resultadosDeMuestra(m, aspectos.contains(Aspecto.EQUIPO)));
        }

        Set<String> categorias = categoriasDe(aspectos);
        List<TrazabilidadEventoDTO> eventos = trazabilidadService.lineaDeTiempo(m.getId()).stream()
                .filter(e -> todo || categorias.contains(e.getCategoria()))
                .toList();
        if (todo || !eventos.isEmpty()) {
            sb.append(todo ? "\nLínea de tiempo (orden cronológico):\n" : "\nRegistros relacionados:\n");
            for (TrazabilidadEventoDTO e : eventos) {
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
        } else if (categorias.contains("INFORME")) {
            sb.append("\nTodavía no se ha emitido un informe para esta muestra.\n");
        } else if (categorias.contains("APROBACION")) {
            sb.append("\nNingún resultado de esta muestra ha sido evaluado por un supervisor todavía.\n");
        }
        if (!todo) {
            sb.append("\nPara ver toda su historia escribe: trazabilidad de ").append(m.getCodigoUnico());
        }
        return sb.toString().trim();
    }

    private Set<String> categoriasDe(Set<Aspecto> aspectos) {
        Set<String> c = new java.util.HashSet<>();
        for (Aspecto a : aspectos) {
            switch (a) {
                case TOMA -> c.add("MUESTREO");
                case UBICACION, CUSTODIA -> c.add("CUSTODIA");
                case APROBACIONES -> { c.add("APROBACION"); c.add("RECHAZO"); c.add("CORRECCION"); }
                case INFORMES -> c.add("INFORME");
                default -> { }
            }
        }
        return c;
    }

    /** Resultados vigentes de una muestra; con {@code conEquipo}, el equipo y su calibración ese día. */
    private String resultadosDeMuestra(Muestra m, boolean conEquipo) {
        List<MuestraAnalisis> analisis = muestraAnalisisRepository.findByMuestra_Id(m.getId());
        StringBuilder sb = new StringBuilder("\nAnálisis y resultados (" + analisis.size() + "):\n");
        if (analisis.isEmpty()) {
            sb.append("- No tiene análisis asignados\n");
        }
        for (MuestraAnalisis ma : analisis) {
            Optional<Resultado> r = resultadoRepository.findByMuestraAnalisis_Id(ma.getId());
            sb.append("- ").append(ma.getAnalisis().getNombre()).append(": ");
            if (r.isEmpty()) {
                sb.append("sin resultado (análisis ").append(ma.getEstado().getValorDb().toLowerCase(Locale.ROOT)).append(")\n");
                continue;
            }
            Resultado res = r.get();
            sb.append(valorConRango(res)).append(" · versión ").append(res.getVersion())
                    .append(" · ").append(estadoAprobacion(res));
            if (conEquipo) {
                sb.append(" · medido con ").append(res.getEquipo() == null ? nn(res.getInstrumentoUtilizado())
                        : res.getEquipo().getEtiqueta() + calibracionEn(res.getEquipo(), fechaResultado(res)));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String listaMuestras(List<Muestra> muestras, String encabezado) {
        StringBuilder sb = new StringBuilder(encabezado);
        if (muestras.size() > MAX_RESULTADOS) {
            sb.append("; se muestran las ").append(MAX_RESULTADOS).append(" más recientes");
        }
        sb.append(":\n");
        muestras.stream().limit(MAX_RESULTADOS).forEach(m -> sb.append("- ").append(resumen(m)).append("\n"));
        return sb.toString().trim();
    }

    private String conteoPorEstado(List<Muestra> muestras) {
        Map<EstadoMuestra, Long> conteo = muestras.stream()
                .collect(Collectors.groupingBy(Muestra::getEstado, () -> new java.util.EnumMap<>(EstadoMuestra.class),
                        Collectors.counting()));
        return conteo.entrySet().stream()
                .map(e -> e.getValue() + " " + estadoTexto(e.getKey()).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(", "));
    }

    String textoBuscable(Muestra m) {
        StringBuilder sb = new StringBuilder();
        sb.append(m.getCodigoUnico()).append(' ').append(m.getTipoMuestra().getValorDb()).append(' ')
                .append(nn(m.getProcedencia())).append(' ').append(nn(m.getObservaciones())).append(' ')
                .append(nn(m.getUbicacionAlmacenamiento())).append(' ');
        if (m.getSitio() != null) {
            sb.append(m.getSitio().getCodigo()).append(' ').append(m.getSitio().getNombre()).append(' ')
                    .append(nn(m.getSitio().getComuna())).append(' ');
        }
        if (m.getInvestigador() != null) {
            sb.append(m.getInvestigador().getNombreCompleto()).append(' ')
                    .append(nn(m.getInvestigador().getInstitucion())).append(' ')
                    .append(nn(m.getInvestigador().getProyecto())).append(' ');
        }
        if (m.getEquipoToma() != null) {
            sb.append(m.getEquipoToma().getCodigo()).append(' ').append(m.getEquipoToma().getNombre());
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private String resumen(Muestra m) {
        return m.getCodigoUnico() + " | " + m.getTipoMuestra().getValorDb() + " | estado " + estadoTexto(m.getEstado())
                + " | sitio " + (m.getSitio() == null ? nn(m.getProcedencia()) : m.getSitio().getCodigo() + " " + m.getSitio().getNombre())
                + " | toma " + fechaHora(m.getFechaToma())
                + " | equipo de toma " + (m.getEquipoToma() == null ? "no registrado" : m.getEquipoToma().getEtiqueta())
                + " | guardada en " + (m.getUbicacionAlmacenamiento() == null ? "no registrado" : m.getUbicacionAlmacenamiento());
    }

    // =====================================================================
    // Resultados
    // =====================================================================

    /** Resultados cuya versión vigente espera la evaluación del supervisor. */
    public String pendientesDeAprobacion(Filtro f) {
        List<Resultado> pendientes = resultadoRepository.findPendientesDeAprobacion().stream()
                .filter(r -> aceptaResultado(f, r))
                .toList();
        if (pendientes.isEmpty()) {
            return "No hay resultados pendientes de aprobación" + f.sufijo() + ".";
        }
        long muestras = pendientes.stream().map(r -> r.getMuestraAnalisis().getMuestra().getId()).distinct().count();
        return listaResultados(pendientes, pendientes.size() + " resultado(s) pendiente(s) de aprobación en "
                + muestras + " muestra(s)" + f.sufijo() + ", del más antiguo al más reciente", false);
    }

    /** Resultados vigentes fuera del rango normal del parámetro. */
    public String fueraDeRango(Filtro f) {
        List<Resultado> lista = resultadoRepository.findAll().stream()
                .filter(r -> Boolean.FALSE.equals(r.getDentroRango()))
                .filter(r -> aceptaResultado(f, r))
                .sorted(Comparator.comparing(this::fechaResultado).reversed())
                .toList();
        if (lista.isEmpty()) {
            return "No hay resultados fuera de rango" + f.sufijo() + ".";
        }
        return listaResultados(lista, lista.size() + " resultado(s) fuera del rango normal" + f.sufijo(), true);
    }

    /** Resultados vigentes marcados como valor atípico por la detección de anomalías. */
    public String atipicos(Filtro f) {
        List<Resultado> lista = resultadoRepository.findAll().stream()
                .filter(Resultado::isAnomalo)
                .filter(r -> aceptaResultado(f, r))
                .sorted(Comparator.comparing(this::fechaResultado).reversed())
                .toList();
        if (lista.isEmpty()) {
            return "No hay valores atípicos vigentes" + f.sufijo()
                    + ". (Un valor atípico corregido queda en el historial de versiones de la muestra.)";
        }
        StringBuilder sb = new StringBuilder(listaResultados(lista, lista.size() + " valor(es) atípico(s)" + f.sufijo(), true));
        sb.append("\n\nUn valor es atípico cuando se aleja mucho de lo habitual para ese parámetro en ese sitio "
                + "(puntaje z modificado con mediana y MAD). No bloquea el ingreso: avisa al supervisor.");
        return sb.toString();
    }

    /** Valores de un parámetro (ej. salinidad), con mínimo, máximo y promedio. */
    public String valoresDeParametro(Filtro f) {
        List<Resultado> lista = resultadoRepository.findAll().stream()
                .filter(r -> aceptaResultado(f, r))
                .sorted(Comparator.comparing((Resultado r) -> Objects.requireNonNullElse(
                        fechaMuestra(r.getMuestraAnalisis().getMuestra()), LocalDateTime.MIN)).reversed())
                .toList();
        if (lista.isEmpty()) {
            return "No hay resultados" + f.sufijo() + ".";
        }
        StringBuilder sb = new StringBuilder();
        Map<AnalisisCatalogo, List<Resultado>> porParametro = lista.stream()
                .collect(Collectors.groupingBy(r -> r.getMuestraAnalisis().getAnalisis(), LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<AnalisisCatalogo, List<Resultado>> e : porParametro.entrySet()) {
            AnalisisCatalogo a = e.getKey();
            List<Resultado> rs = e.getValue();
            List<BigDecimal> valores = rs.stream().map(Resultado::getValorResultado).filter(Objects::nonNull).toList();
            sb.append(a.getNombre()).append(f.sufijoSinAnalisis()).append(": ").append(rs.size()).append(" resultado(s)");
            if (!valores.isEmpty()) {
                BigDecimal suma = valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                sb.append(" · mínimo ").append(numero(valores.stream().min(BigDecimal::compareTo).orElseThrow()))
                        .append(" · máximo ").append(numero(valores.stream().max(BigDecimal::compareTo).orElseThrow()))
                        .append(" · promedio ").append(numero(suma.divide(BigDecimal.valueOf(valores.size()), 2, RoundingMode.HALF_UP)))
                        .append(" ").append(a.getUnidadMedida());
            }
            sb.append(rango(a).isEmpty() ? "" : " · rango normal " + rango(a));
            long fuera = rs.stream().filter(r -> Boolean.FALSE.equals(r.getDentroRango())).count();
            long atip = rs.stream().filter(Resultado::isAnomalo).count();
            sb.append(" · ").append(fuera).append(" fuera de rango · ").append(atip).append(" atípico(s)\n");
            if (rs.size() > MAX_RESULTADOS) {
                sb.append("Los ").append(MAX_RESULTADOS).append(" más recientes:\n");
            }
            rs.stream().limit(MAX_RESULTADOS).forEach(r -> {
                Muestra m = r.getMuestraAnalisis().getMuestra();
                sb.append("- ").append(fecha(fechaMuestra(m))).append(" · ").append(m.getCodigoUnico())
                        .append(" · ").append(sitio(m)).append(" · ").append(valorConRango(r)).append("\n");
            });
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    /** Rechazos del supervisor, con su comentario y si el resultado se corrigió después. */
    public String rechazos(Filtro f) {
        List<Aprobacion> lista = aprobacionRepository.findAll().stream()
                .filter(a -> a.getEstadoAprobacion() == EstadoAprobacion.RECHAZADO)
                .filter(a -> aceptaResultadoSinFecha(f, a.getResultado()) && f.enFecha(a.getFechaAprobacion()))
                .sorted(Comparator.comparing(Aprobacion::getFechaAprobacion,
                        Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder())).reversed())
                .toList();
        if (lista.isEmpty()) {
            return "No hay resultados rechazados" + f.sufijo() + ".";
        }
        StringBuilder sb = new StringBuilder(lista.size() + " rechazo(s)" + f.sufijo() + ", más recientes primero:\n");
        lista.stream().limit(MAX_RESULTADOS).forEach(a -> {
            Resultado r = a.getResultado();
            Muestra m = r.getMuestraAnalisis().getMuestra();
            sb.append("- ").append(fechaHora(a.getFechaAprobacion())).append(" · ").append(m.getCodigoUnico())
                    .append(" · ").append(r.getMuestraAnalisis().getAnalisis().getNombre())
                    .append(" (versión ").append(a.getVersionResultado()).append(") rechazado por ")
                    .append(nombre(a.getSupervisor()))
                    .append(a.getComentario() == null || a.getComentario().isBlank() ? "" : ": \"" + a.getComentario() + "\"")
                    .append(r.getVersion() > a.getVersionResultado()
                            ? " · después se corrigió (versión vigente " + r.getVersion() + ", " + estadoAprobacion(r) + ")"
                            : " · aún sin corregir")
                    .append("\n");
        });
        return sb.toString().trim();
    }

    /** Correcciones de resultados (versiones 2 en adelante), con su motivo. */
    public String correcciones(Filtro f) {
        List<ResultadoVersion> lista = versionRepository.findAll().stream()
                .filter(v -> v.getVersion() != null && v.getVersion() > 1)
                .filter(v -> aceptaResultadoSinFecha(f, v.getResultado()) && f.enFecha(v.getFecha()))
                .sorted(Comparator.comparing(ResultadoVersion::getFecha,
                        Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder())).reversed())
                .toList();
        if (lista.isEmpty()) {
            return "No hay resultados corregidos" + f.sufijo() + ".";
        }
        StringBuilder sb = new StringBuilder(lista.size() + " corrección(es)" + f.sufijo() + ", más recientes primero:\n");
        lista.stream().limit(MAX_RESULTADOS).forEach(v -> {
            Resultado r = v.getResultado();
            var ma = r.getMuestraAnalisis();
            Optional<ResultadoVersion> anterior = versionRepository.findByResultado_IdOrderByVersionAsc(r.getId()).stream()
                    .filter(x -> x.getVersion() == v.getVersion() - 1)
                    .findFirst();
            sb.append("- ").append(fechaHora(v.getFecha())).append(" · ").append(ma.getMuestra().getCodigoUnico())
                    .append(" · ").append(ma.getAnalisis().getNombre()).append(": ")
                    .append(anterior.map(x -> numero(x.getValorResultado()) + " → ").orElse(""))
                    .append(numero(v.getValorResultado())).append(" ").append(ma.getAnalisis().getUnidadMedida())
                    .append(" (versión ").append(v.getVersion()).append(") por ").append(nombre(v.getUsuario()))
                    .append(" · motivo: ").append(nn(v.getMotivoCambio())).append("\n");
        });
        sb.append("\nLas versiones anteriores no se borran: se ven en Ver versiones o en la trazabilidad de la muestra.");
        return sb.toString().trim();
    }

    /** Análisis asignados que todavía no tienen resultado. */
    public String analisisSinResultado(Filtro f) {
        List<MuestraAnalisis> lista = muestraAnalisisRepository.findAll().stream()
                .filter(ma -> ma.getEstado() != EstadoMuestraAnalisis.COMPLETADO)
                .filter(ma -> !resultadoRepository.existsByMuestraAnalisis_Id(ma.getId()))
                .filter(ma -> f.acepta(ma.getMuestra(), true)
                        && (f.analisis().isEmpty() || f.analisis().contains(ma.getAnalisis().getId())))
                .sorted(Comparator.comparing(MuestraAnalisis::getFechaAsignacion,
                        Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder())))
                .toList();
        if (lista.isEmpty()) {
            return "No hay análisis pendientes de medir" + f.sufijo() + ".";
        }
        StringBuilder sb = new StringBuilder(lista.size() + " análisis sin resultado" + f.sufijo() + ", del más antiguo al más reciente:\n");
        lista.stream().limit(MAX_RESULTADOS).forEach(ma -> sb.append("- ").append(ma.getMuestra().getCodigoUnico())
                .append(" · ").append(ma.getAnalisis().getNombre()).append(" · ").append(ma.getEstado().getValorDb())
                .append(" · asignado ").append(fechaHora(ma.getFechaAsignacion()))
                .append(ma.getAnalistaAsignado() == null ? " · sin analista asignado" : " a " + nombre(ma.getAnalistaAsignado()))
                .append("\n"));
        return sb.toString().trim();
    }

    private String listaResultados(List<Resultado> lista, String encabezado, boolean conAprobacion) {
        StringBuilder sb = new StringBuilder(encabezado);
        if (lista.size() > MAX_RESULTADOS) {
            sb.append("; se muestran ").append(MAX_RESULTADOS);
        }
        sb.append(":\n");
        lista.stream().limit(MAX_RESULTADOS).forEach(r -> {
            var ma = r.getMuestraAnalisis();
            Muestra m = ma.getMuestra();
            sb.append("- ").append(m.getCodigoUnico()).append(" · ").append(ma.getAnalisis().getNombre())
                    .append(" = ").append(valorConRango(r))
                    .append(r.isAnomalo() && r.getDetalleAnomalia() != null ? " (" + r.getDetalleAnomalia() + ")" : "")
                    .append(" · ").append(sitio(m))
                    .append(" · ingresado ").append(fechaHora(fechaResultado(r))).append(" por ").append(nombre(r.getUsuarioIngreso()))
                    .append(r.getVersion() > 1 ? " · versión " + r.getVersion() : "")
                    .append(conAprobacion ? " · " + estadoAprobacion(r) : "")
                    .append("\n");
        });
        return sb.toString().trim();
    }

    private boolean aceptaResultado(Filtro f, Resultado r) {
        return aceptaResultadoSinFecha(f, r) && f.enFecha(fechaMuestra(r.getMuestraAnalisis().getMuestra()));
    }

    private boolean aceptaResultadoSinFecha(Filtro f, Resultado r) {
        return f.acepta(r.getMuestraAnalisis().getMuestra(), false)
                && (f.analisis().isEmpty() || f.analisis().contains(r.getMuestraAnalisis().getAnalisis().getId()));
    }

    private String valorConRango(Resultado r) {
        AnalisisCatalogo a = r.getMuestraAnalisis().getAnalisis();
        String marca = Boolean.FALSE.equals(r.getDentroRango()) ? " ⚠ fuera de rango" : "";
        if (r.isAnomalo()) {
            marca += " ⚠ atípico";
        }
        String rango = rango(a);
        return numero(r.getValorResultado()) + " " + a.getUnidadMedida()
                + (rango.isEmpty() ? "" : " (normal " + rango + ")") + marca;
    }

    private String rango(AnalisisCatalogo a) {
        if (a.getValorMinNormal() == null && a.getValorMaxNormal() == null) {
            return "";
        }
        return (a.getValorMinNormal() == null ? "…" : numero(a.getValorMinNormal())) + "–"
                + (a.getValorMaxNormal() == null ? "…" : numero(a.getValorMaxNormal()));
    }

    /** "aprobado", "rechazado" o "pendiente de aprobación", según la evaluación de la versión vigente. */
    private String estadoAprobacion(Resultado r) {
        return aprobacionRepository.findTopByResultado_IdOrderByIdDesc(r.getId())
                .filter(a -> Objects.equals(a.getVersionResultado(), r.getVersion()))
                .map(a -> (a.getEstadoAprobacion() == EstadoAprobacion.APROBADO ? "aprobado" : "rechazado")
                        + " por " + nombre(a.getSupervisor()))
                .orElse("pendiente de aprobación");
    }

    // =====================================================================
    // Informes
    // =====================================================================

    public String informes(Filtro f) {
        List<Reporte> lista = reporteRepository.findAllByOrderByFechaGeneracionDesc().stream()
                .filter(r -> f.acepta(r.getMuestra(), false) && f.enFecha(r.getFechaGeneracion()))
                .toList();
        if (lista.isEmpty()) {
            return "No se han emitido informes" + f.sufijo() + ".";
        }
        long firmados = lista.stream().filter(r -> r.getCodigoVerificacion() != null).count();
        StringBuilder sb = new StringBuilder(lista.size() + " informe(s) emitido(s)" + f.sufijo()
                + " (" + firmados + " con firma digital), más recientes primero:\n");
        lista.stream().limit(MAX_RESULTADOS).forEach(r -> sb.append("- ").append(r.getMuestra().getCodigoUnico())
                .append(" v").append(r.getVersion()).append(" · ").append(fechaHora(r.getFechaGeneracion()))
                .append(" · emitido por ").append(nombre(r.getUsuarioGenerador()))
                .append(r.getCodigoVerificacion() == null ? " · sin firma digital (anterior a la v7)"
                        : " · firmado, código de verificación " + r.getCodigoVerificacion())
                .append("\n"));
        sb.append("\nPara comprobar si un PDF es auténtico: Reportes → Verificar informe.");
        return sb.toString().trim();
    }

    // =====================================================================
    // Sitios, investigadores, almacenamiento y conservación
    // =====================================================================

    public String resumenSitios(Filtro f) {
        List<SitioMuestreo> sitios = sitioRepository.findAllByOrderByCodigoAsc().stream()
                .filter(s -> f.sitios().isEmpty() || f.sitios().contains(s.getId()))
                .toList();
        if (sitios.isEmpty()) {
            return "No hay sitios de muestreo registrados.";
        }
        List<Muestra> muestras = muestraRepository.findAll();
        List<Resultado> resultados = resultadoRepository.findAll();
        StringBuilder sb = new StringBuilder(sitios.size() + " sitio(s) de muestreo:\n");
        for (SitioMuestreo s : sitios) {
            List<Muestra> delSitio = muestras.stream()
                    .filter(m -> m.getSitio() != null && m.getSitio().getId().equals(s.getId()))
                    .toList();
            Optional<LocalDateTime> ultima = delSitio.stream().map(ConsultasTrazabilidad::fechaMuestra)
                    .filter(Objects::nonNull).max(Comparator.naturalOrder());
            long fuera = resultados.stream()
                    .filter(r -> Boolean.FALSE.equals(r.getDentroRango()))
                    .filter(r -> delSitio.contains(r.getMuestraAnalisis().getMuestra()))
                    .count();
            sb.append("- ").append(s.getCodigo()).append(" ").append(s.getNombre())
                    .append(" (").append(nn(s.getTipoAmbiente())).append(", ").append(nn(s.getComuna()))
                    .append("; lat ").append(coord(s.getLatitud())).append(", lon ").append(coord(s.getLongitud())).append(")")
                    .append(s.isActivo() ? "" : " · INACTIVO")
                    .append(" · ").append(delSitio.size()).append(" muestra(s)")
                    .append(ultima.map(u -> " · último muestreo " + fecha(u)).orElse(""))
                    .append(fuera > 0 ? " · ⚠ " + fuera + " resultado(s) fuera de rango" : "")
                    .append("\n");
        }
        return sb.toString().trim();
    }

    public String resumenInvestigadores(Filtro f) {
        List<Investigador> lista = investigadorRepository.findAllByOrderByApellidoAscNombreAsc().stream()
                .filter(i -> f.investigadores().isEmpty() || f.investigadores().contains(i.getId()))
                .toList();
        if (lista.isEmpty()) {
            return "No hay investigadores registrados.";
        }
        List<Muestra> muestras = muestraRepository.findAll();
        StringBuilder sb = new StringBuilder(lista.size() + " investigador(es):\n");
        for (Investigador i : lista) {
            List<Muestra> suyas = muestras.stream()
                    .filter(m -> m.getInvestigador() != null && m.getInvestigador().getId().equals(i.getId()))
                    .toList();
            long enCurso = suyas.stream().filter(m -> m.getEstado() != EstadoMuestra.REPORTADA).count();
            sb.append("- ").append(i.getNombreCompleto()).append(" · ").append(nn(i.getInstitucion()))
                    .append(i.getProyecto() == null ? "" : " · proyecto " + i.getProyecto())
                    .append(i.isActivo() ? "" : " · inactivo")
                    .append(" · ").append(suyas.size()).append(" muestra(s), ").append(enCurso).append(" aún sin informe\n");
        }
        return sb.toString().trim();
    }

    /** Cuántas muestras hay en cada lugar de almacenamiento (o el detalle, si se filtró por uno). */
    public String almacenamiento(Filtro f) {
        List<Muestra> muestras = muestraRepository.findAll().stream()
                .filter(m -> f.acepta(m, true))
                .toList();
        if (!f.ubicaciones().isEmpty()) {
            return muestras.isEmpty() ? "No hay muestras guardadas" + f.sufijo() + "."
                    : listaMuestras(muestras.stream().sorted(masRecientes()).toList(),
                            muestras.size() + " muestra(s) guardada(s)" + f.sufijo());
        }
        Map<String, List<Muestra>> porLugar = muestras.stream()
                .collect(Collectors.groupingBy(m -> m.getUbicacionAlmacenamiento() == null ? "Sin ubicación registrada"
                        : m.getUbicacionAlmacenamiento(), java.util.TreeMap::new, Collectors.toList()));
        StringBuilder sb = new StringBuilder("Muestras por lugar de almacenamiento" + f.sufijo() + ":\n");
        porLugar.forEach((lugar, lista) -> sb.append("- ").append(lugar).append(": ").append(lista.size())
                .append(" muestra(s)")
                .append(lista.size() <= 5 ? " (" + lista.stream().map(Muestra::getCodigoUnico).collect(Collectors.joining(", ")) + ")" : "")
                .append("\n"));
        return sb.toString().trim();
    }

    /** Muestras cuyo plazo de conservación venció o vence dentro de 30 días. */
    public String conservacion(Filtro f) {
        LocalDate hoy = LocalDate.now();
        List<Muestra> lista = muestraRepository.findAll().stream()
                .filter(m -> m.getFechaVencimiento() != null && !m.getFechaVencimiento().isAfter(hoy.plusDays(DIAS_AVISO_CONSERVACION)))
                .filter(m -> f.acepta(m, true))
                .sorted(Comparator.comparing(Muestra::getFechaVencimiento))
                .toList();
        if (lista.isEmpty()) {
            return "Ninguna muestra" + f.sufijo() + " tiene el plazo de conservación vencido ni por vencer en los próximos "
                    + DIAS_AVISO_CONSERVACION + " días.";
        }
        StringBuilder sb = new StringBuilder(lista.size() + " muestra(s)" + f.sufijo()
                + " con el plazo de conservación vencido o por vencer (30 días):\n");
        lista.stream().limit(MAX_RESULTADOS).forEach(m -> sb.append("- ").append(m.getCodigoUnico())
                .append(" · ").append(m.getFechaVencimiento().isBefore(hoy) ? "⚠ venció el " : "vence el ")
                .append(fecha(m.getFechaVencimiento())).append(" · ").append(estadoTexto(m.getEstado()))
                .append(" · guardada en ").append(nn(m.getUbicacionAlmacenamiento())).append("\n"));
        return sb.toString().trim();
    }

    // =====================================================================
    // Resumen general
    // =====================================================================

    public String resumenGeneral() {
        List<Muestra> muestras = muestraRepository.findAll();
        List<Resultado> resultados = resultadoRepository.findAll();
        Predicate<Resultado> enCurso = r -> r.getMuestraAnalisis().getMuestra().getEstado() != EstadoMuestra.REPORTADA;
        long pendientes = resultadoRepository.findPendientesDeAprobacion().size();
        long fuera = resultados.stream().filter(r -> Boolean.FALSE.equals(r.getDentroRango())).filter(enCurso).count();
        long atip = resultados.stream().filter(Resultado::isAnomalo).filter(enCurso).count();
        List<Equipo> equipos = equipoRepository.findAll().stream().filter(Equipo::isActivo).toList();
        long vencidos = equipos.stream().filter(e -> "Vencida".equals(EquipoService.estadoCalibracion(e))).count();
        long porVencer = equipos.stream().filter(e -> "Por vencer".equals(EquipoService.estadoCalibracion(e))).count();
        long informes = reporteRepository.count();

        StringBuilder sb = new StringBuilder("Resumen del laboratorio al ").append(LocalDate.now().format(FECHA)).append(":\n");
        sb.append("- ").append(muestras.size()).append(" muestra(s): ").append(conteoPorEstado(muestras)).append("\n");
        sb.append("- ").append(pendientes).append(" resultado(s) pendiente(s) de aprobación\n");
        sb.append("- En muestras todavía sin informe: ").append(fuera).append(" resultado(s) fuera de rango y ")
                .append(atip).append(" valor(es) atípico(s)\n");
        sb.append("- Equipos activos: ").append(equipos.size()).append(" · ").append(vencidos)
                .append(" con calibración vencida · ").append(porVencer).append(" por vencer\n");
        sb.append("- ").append(informes).append(" informe(s) emitido(s) · ").append(sitioRepository.count())
                .append(" sitio(s) de muestreo · ").append(investigadorRepository.count()).append(" investigador(es)");
        return sb.toString();
    }

    // =====================================================================
    // Equipos
    // =====================================================================

    public String consultarEquipo(String codigo) {
        if (codigo == null) {
            return "Falta el código del equipo.";
        }
        Optional<Equipo> encontrado = equipoRepository.findByCodigoIgnoreCase(codigo.trim());
        if (encontrado.isEmpty()) {
            return "No existe un equipo con el código " + codigo.trim().toUpperCase(Locale.ROOT)
                    + ". Escribe \"equipos\" para ver los códigos.";
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
                    .append(c.isVigente() ? " (la actual)" : "")
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

    public String listarEquipos() {
        return listarEquipos(null, null);
    }

    /**
     * Equipos, opcionalmente solo los de ciertos estados de calibración ("Vencida", "Por vencer",
     * "Vigente", "Sin registro") y/o de un uso ("Terreno" o "Laboratorio"; "Ambos" cuenta para los dos).
     */
    public String listarEquipos(Set<String> estadosCalibracion, String uso) {
        List<Equipo> equipos = equipoRepository.findAllByOrderByCodigoAsc().stream()
                .filter(e -> estadosCalibracion == null || estadosCalibracion.isEmpty()
                        || (e.isActivo() && estadosCalibracion.contains(EquipoService.estadoCalibracion(e))))
                .filter(e -> uso == null || (Equipo.USO_TERRENO.equals(uso) ? e.isUsoTerreno() : e.isUsoLaboratorio()))
                .toList();
        String que = (estadosCalibracion == null || estadosCalibracion.isEmpty() ? "" : " con calibración "
                + String.join(" o ", estadosCalibracion).toLowerCase(Locale.ROOT))
                + (uso == null ? "" : " de uso en " + uso.toLowerCase(Locale.ROOT));
        if (equipos.isEmpty()) {
            return "No hay equipos" + que + "."
                    + (estadosCalibracion != null && estadosCalibracion.contains("Vencida") ? " Todos los equipos activos están al día." : "");
        }
        StringBuilder sb = new StringBuilder(equipos.size() + " equipo(s)" + que + ":\n");
        for (Equipo e : equipos) {
            sb.append("- ").append(e.getEtiqueta()).append(" | uso ").append(e.getUso())
                    .append(" | ").append(e.isActivo() ? "activo" : "de baja")
                    .append(" | calibración ").append(EquipoService.estadoCalibracion(e));
            if (e.getFechaProximaCalibracion() != null) {
                sb.append(" (vence ").append(fecha(e.getFechaProximaCalibracion())).append(")");
            }
            sb.append("\n");
        }
        if (estadosCalibracion != null && estadosCalibracion.contains("Vencida")) {
            sb.append("\nUn equipo con la calibración vencida no se puede usar para ingresar resultados.");
        }
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

    // ---------- helpers ----------

    static LocalDateTime fechaMuestra(Muestra m) {
        return m.getFechaToma() != null ? m.getFechaToma() : m.getFechaRecepcion();
    }

    private LocalDateTime fechaResultado(Resultado r) {
        return Objects.requireNonNullElse(r.getFechaModificacion(),
                Objects.requireNonNullElse(r.getFechaIngreso(), LocalDateTime.MIN));
    }

    private static Comparator<Muestra> masRecientes() {
        return Comparator.comparing(ConsultasTrazabilidad::fechaMuestra,
                Comparator.nullsFirst(Comparator.<LocalDateTime>naturalOrder())).reversed();
    }

    private String sitio(Muestra m) {
        return m.getSitio() == null ? nn(m.getProcedencia()) : m.getSitio().getCodigo() + " " + m.getSitio().getNombre();
    }

    private String nombre(Usuario u) {
        return u == null ? "—" : u.getNombre() + " " + u.getApellido();
    }

    private String nn(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    private String fechaHora(LocalDateTime f) {
        return f == null || f.equals(LocalDateTime.MIN) ? "no registrada" : f.format(FECHA_HORA);
    }

    private String fecha(LocalDateTime f) {
        return f == null ? "—" : f.format(FECHA);
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

    /** Nombre del estado para mostrar (el valor guardado en la BD no lleva tilde: "En analisis"). */
    static String estadoTexto(EstadoMuestra e) {
        return e.getValorDb().replace("analisis", "análisis");
    }
}
