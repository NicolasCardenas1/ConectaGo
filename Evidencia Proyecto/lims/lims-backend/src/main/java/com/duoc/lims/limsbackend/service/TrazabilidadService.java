package com.duoc.lims.limsbackend.service;

import com.duoc.lims.limsbackend.dto.TrazabilidadEventoDTO;
import com.duoc.lims.limsbackend.model.AnalisisCatalogo;
import com.duoc.lims.limsbackend.model.Aprobacion;
import com.duoc.lims.limsbackend.model.CustodiaEvento;
import com.duoc.lims.limsbackend.model.Muestra;
import com.duoc.lims.limsbackend.model.MuestraAnalisis;
import com.duoc.lims.limsbackend.model.MuestraHistorialEstado;
import com.duoc.lims.limsbackend.model.Reporte;
import com.duoc.lims.limsbackend.model.Resultado;
import com.duoc.lims.limsbackend.model.ResultadoVersion;
import com.duoc.lims.limsbackend.model.SitioMuestreo;
import com.duoc.lims.limsbackend.model.Usuario;
import com.duoc.lims.limsbackend.model.enums.EstadoAprobacion;
import com.duoc.lims.limsbackend.repository.AprobacionRepository;
import com.duoc.lims.limsbackend.repository.CustodiaEventoRepository;
import com.duoc.lims.limsbackend.repository.MuestraAnalisisRepository;
import com.duoc.lims.limsbackend.repository.MuestraHistorialEstadoRepository;
import com.duoc.lims.limsbackend.repository.MuestraRepository;
import com.duoc.lims.limsbackend.repository.ReporteRepository;
import com.duoc.lims.limsbackend.repository.ResultadoRepository;
import com.duoc.lims.limsbackend.repository.ResultadoVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * v5 — Trazabilidad completa de una muestra en una sola línea de tiempo:
 * sitio y toma en terreno → recepción y custodia → análisis solicitados → cada versión de cada
 * resultado (con equipo y calibración) → aprobaciones y rechazos → interpretación → informes.
 * Responde a "¿quién hizo qué, cuándo, con qué y por qué?" sobre cada dato (ISO/IEC 17025).
 */
@Service
public class TrazabilidadService {

    private final MuestraRepository muestraRepository;
    private final MuestraHistorialEstadoRepository historialRepository;
    private final CustodiaEventoRepository custodiaRepository;
    private final MuestraAnalisisRepository muestraAnalisisRepository;
    private final ResultadoRepository resultadoRepository;
    private final ResultadoVersionRepository versionRepository;
    private final AprobacionRepository aprobacionRepository;
    private final ReporteRepository reporteRepository;

    public TrazabilidadService(MuestraRepository muestraRepository,
                               MuestraHistorialEstadoRepository historialRepository,
                               CustodiaEventoRepository custodiaRepository,
                               MuestraAnalisisRepository muestraAnalisisRepository,
                               ResultadoRepository resultadoRepository,
                               ResultadoVersionRepository versionRepository,
                               AprobacionRepository aprobacionRepository,
                               ReporteRepository reporteRepository) {
        this.muestraRepository = muestraRepository;
        this.historialRepository = historialRepository;
        this.custodiaRepository = custodiaRepository;
        this.muestraAnalisisRepository = muestraAnalisisRepository;
        this.resultadoRepository = resultadoRepository;
        this.versionRepository = versionRepository;
        this.aprobacionRepository = aprobacionRepository;
        this.reporteRepository = reporteRepository;
    }

    @Transactional(readOnly = true)
    public List<TrazabilidadEventoDTO> lineaDeTiempo(Integer idMuestra) {
        Muestra m = muestraRepository.findById(idMuestra)
                .orElseThrow(() -> new IllegalArgumentException("No existe la muestra con id " + idMuestra));
        List<TrazabilidadEventoDTO> eventos = new ArrayList<>();

        // 1. Toma en terreno
        if (m.getFechaToma() != null) {
            SitioMuestreo s = m.getSitio();
            StringJoiner d = new StringJoiner(" · ");
            d.add("Sitio: " + (s == null ? texto(m.getProcedencia())
                    : s.getCodigo() + " " + s.getNombre() + " (" + coord(s.getLatitud()) + ", " + coord(s.getLongitud()) + ")"));
            if (m.getProcedencia() != null && s != null && !m.getProcedencia().equals(s.getNombre())) {
                d.add("Punto: " + m.getProcedencia());
            }
            d.add("Tipo: " + m.getTipoMuestra().getValorDb());
            if (m.getInvestigador() != null) {
                d.add("Investigador: " + m.getInvestigador().getNombreCompleto());
            }
            eventos.add(new TrazabilidadEventoDTO(m.getFechaToma(), "MUESTREO", "Toma de la muestra en terreno",
                    d.toString(), m.getInvestigador() == null ? null : m.getInvestigador().getNombreCompleto(), false));
        }

        // 2. Registro en el sistema (los demás cambios de estado se agregan al final; ver orden más abajo)
        List<MuestraHistorialEstado> historial = historialRepository.findByMuestra_IdOrderByIdAsc(idMuestra);
        historial.stream().filter(h -> h.getEstadoAnterior() == null).forEach(h -> eventos.add(
                new TrazabilidadEventoDTO(h.getFechaCambio(), "ESTADO",
                        "Registrada en el sistema como " + h.getEstadoNuevo().getValorDb(), h.getComentario(),
                        nombre(h.getUsuario()), false)));

        // 3. Cadena de custodia
        for (CustodiaEvento c : custodiaRepository.findByMuestra_IdOrderByIdAsc(idMuestra)) {
            StringJoiner d = new StringJoiner(" · ");
            d.add("Entregó: " + c.getEntregadoPor());
            d.add("Recibió y firmó: " + nombre(c.getUsuarioRecibe()));
            if (c.getUbicacion() != null) d.add("Ubicación: " + c.getUbicacion());
            if (c.getCondicion() != null) d.add("Condición: " + c.getCondicion());
            if (c.getObservaciones() != null) d.add(c.getObservaciones());
            eventos.add(new TrazabilidadEventoDTO(c.getFechaHora(), "CUSTODIA",
                    "Custodia: " + c.getTipoEvento().getValorDb(), d.toString(), nombre(c.getUsuarioRecibe()), false));
        }

        // 4. Análisis, versiones de resultados y evaluaciones
        for (MuestraAnalisis ma : muestraAnalisisRepository.findByMuestra_Id(idMuestra)) {
            AnalisisCatalogo cat = ma.getAnalisis();
            if (ma.getFechaAsignacion() != null) {
                eventos.add(new TrazabilidadEventoDTO(ma.getFechaAsignacion(), "ANALISIS",
                        "Análisis solicitado: " + cat.getNombre(),
                        "Método: " + texto(cat.getMetodoReferencia()) + " · Rango normal: "
                                + numero(cat.getValorMinNormal()) + " – " + numero(cat.getValorMaxNormal()) + " "
                                + cat.getUnidadMedida(),
                        nombre(ma.getAnalistaAsignado()), false));
            }
            Optional<Resultado> resultado = resultadoRepository.findByMuestraAnalisis_Id(ma.getId());
            if (resultado.isEmpty()) {
                continue;
            }
            // Cada versión seguida de la evaluación que recibió (v1, rechazo v1, v2, aprobación v2...).
            List<Aprobacion> evaluaciones = aprobacionRepository.findByResultado_IdOrderByIdAsc(resultado.get().getId());
            for (ResultadoVersion v : versionRepository.findByResultado_IdOrderByVersionAsc(resultado.get().getId())) {
                StringJoiner d = new StringJoiner(" · ");
                d.add("Equipo: " + texto(v.getInstrumentoUtilizado()));
                if (v.getEquipo() != null && v.getEquipo().getFechaProximaCalibracion() != null) {
                    d.add("calibrado hasta " + v.getEquipo().getFechaProximaCalibracion());
                }
                d.add(Boolean.TRUE.equals(v.getDentroRango()) ? "En rango" : "FUERA DE RANGO");
                if (v.isAnomalo()) d.add("⚠ " + v.getDetalleAnomalia());
                if (v.getObservaciones() != null) d.add("Obs.: " + v.getObservaciones());
                if (v.getMotivoCambio() != null) d.add("Motivo del cambio: " + v.getMotivoCambio());
                boolean correccion = v.getVersion() > 1;
                eventos.add(new TrazabilidadEventoDTO(v.getFecha(), correccion ? "CORRECCION" : "RESULTADO",
                        (correccion ? "Corrección v" : "Resultado v") + v.getVersion() + " · " + cat.getNombre()
                                + " = " + numero(v.getValorResultado()) + " " + cat.getUnidadMedida(),
                        d.toString(), nombre(v.getUsuario()),
                        !Boolean.TRUE.equals(v.getDentroRango()) || v.isAnomalo()));
                for (Aprobacion a : evaluaciones) {
                    if (!v.getVersion().equals(a.getVersionResultado())) {
                        continue;
                    }
                    boolean aprobado = a.getEstadoAprobacion() == EstadoAprobacion.APROBADO;
                    eventos.add(new TrazabilidadEventoDTO(a.getFechaAprobacion(), aprobado ? "APROBACION" : "RECHAZO",
                            (aprobado ? "Aprobado " : "Rechazado ") + cat.getNombre() + " (v" + a.getVersionResultado() + ")",
                            a.getComentario(), nombre(a.getSupervisor()), !aprobado));
                }
            }
        }

        // Cambios de estado: después de los hechos que los provocan (resultados, aprobaciones)
        historial.stream().filter(h -> h.getEstadoAnterior() != null).forEach(h -> eventos.add(
                new TrazabilidadEventoDTO(h.getFechaCambio(), "ESTADO",
                        "Estado: " + h.getEstadoAnterior().getValorDb() + " → " + h.getEstadoNuevo().getValorDb(),
                        h.getComentario(), nombre(h.getUsuario()),
                        "Rechazada".equals(h.getEstadoNuevo().getValorDb()))));

        // 5. Interpretación del informe
        if (m.getInterpretacion() != null && m.getFechaInterpretacion() != null) {
            eventos.add(new TrazabilidadEventoDTO(m.getFechaInterpretacion(), "INTERPRETACION",
                    "Interpretación de resultados registrada (" + texto(m.getInterpretacionOrigen()) + ")",
                    resumen(m.getInterpretacion()), nombre(m.getUsuarioInterpretacion()), false));
        }

        // 6. Informes emitidos
        for (Reporte r : reporteRepository.findByMuestra_IdOrderByVersionAsc(idMuestra)) {
            eventos.add(new TrazabilidadEventoDTO(r.getFechaGeneracion(), "INFORME",
                    "Informe emitido N° " + m.getCodigoUnico() + "-v" + r.getVersion(),
                    "Dirigido a " + (m.getInvestigador() == null ? "—" : m.getInvestigador().getNombreCompleto()),
                    nombre(r.getUsuarioGenerador()), false));
        }

        // Orden cronológico al segundo (la precisión de DATETIME). Los empates conservan el orden
        // lógico en que se agregaron arriba (sort de Java es estable).
        eventos.sort(Comparator.comparing((TrazabilidadEventoDTO e) ->
                        e.getFecha() == null ? null : e.getFecha().withNano(0),
                Comparator.nullsFirst(Comparator.<LocalDateTime>naturalOrder())));
        return eventos;
    }

    private String nombre(Usuario u) {
        return u == null ? null : u.getNombre() + " " + u.getApellido();
    }

    private String numero(BigDecimal n) {
        return n == null ? "" : n.stripTrailingZeros().toPlainString();
    }

    private String coord(BigDecimal n) {
        return n == null ? "?" : n.setScale(4, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String texto(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    private String resumen(String s) {
        return s.length() <= 160 ? s : s.substring(0, 157) + "...";
    }
}
