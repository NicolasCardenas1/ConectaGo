package com.duoc.lims.limsbackend.service;

import com.duoc.lims.limsbackend.dto.DashboardDTO;
import com.duoc.lims.limsbackend.dto.DashboardDTO.Alerta;
import com.duoc.lims.limsbackend.dto.DashboardDTO.Conteo;
import com.duoc.lims.limsbackend.dto.DashboardDTO.Punto;
import com.duoc.lims.limsbackend.dto.DashboardDTO.Tendencia;
import com.duoc.lims.limsbackend.model.AnalisisCatalogo;
import com.duoc.lims.limsbackend.model.Equipo;
import com.duoc.lims.limsbackend.model.Muestra;
import com.duoc.lims.limsbackend.model.MuestraHistorialEstado;
import com.duoc.lims.limsbackend.model.Resultado;
import com.duoc.lims.limsbackend.model.enums.EstadoMuestra;
import com.duoc.lims.limsbackend.repository.AnalisisCatalogoRepository;
import com.duoc.lims.limsbackend.repository.EquipoRepository;
import com.duoc.lims.limsbackend.repository.MuestraHistorialEstadoRepository;
import com.duoc.lims.limsbackend.repository.MuestraRepository;
import com.duoc.lims.limsbackend.repository.ResultadoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

/** v5 — Panel de inicio: indicadores del laboratorio, alertas y tendencias por sitio. */
@Service
public class DashboardService {

    private static final int MAX_ALERTAS = 12;

    private final MuestraRepository muestraRepository;
    private final ResultadoRepository resultadoRepository;
    private final EquipoRepository equipoRepository;
    private final MuestraHistorialEstadoRepository historialRepository;
    private final AnalisisCatalogoRepository analisisRepository;

    public DashboardService(MuestraRepository muestraRepository, ResultadoRepository resultadoRepository,
                            EquipoRepository equipoRepository, MuestraHistorialEstadoRepository historialRepository,
                            AnalisisCatalogoRepository analisisRepository) {
        this.muestraRepository = muestraRepository;
        this.resultadoRepository = resultadoRepository;
        this.equipoRepository = equipoRepository;
        this.historialRepository = historialRepository;
        this.analisisRepository = analisisRepository;
    }

    @Transactional(readOnly = true)
    public DashboardDTO resumen() {
        List<Muestra> muestras = muestraRepository.findAll();
        List<Resultado> resultados = resultadoRepository.findAll();
        List<Equipo> equipos = equipoRepository.findAll().stream().filter(Equipo::isActivo).toList();
        LocalDateTime hace30 = LocalDateTime.now().minusDays(30);
        LocalDate hoy = LocalDate.now();

        Map<String, Long> porEstado = new LinkedHashMap<>();
        for (EstadoMuestra e : EstadoMuestra.values()) {
            porEstado.put(e.getValorDb(), muestras.stream().filter(m -> m.getEstado() == e).count());
        }

        long fueraRango = resultados.stream()
                .filter(r -> !Boolean.TRUE.equals(r.getDentroRango()) && fecha(r).isAfter(hace30)).count();
        long anomalias = resultados.stream().filter(r -> r.isAnomalo() && fecha(r).isAfter(hace30)).count();
        long pendientes = resultadoRepository.findPendientesDeAprobacion().size();

        long vencidos = equipos.stream().filter(e -> "Vencida".equals(EquipoService.estadoCalibracion(e))).count();
        long porVencer = equipos.stream().filter(e -> "Por vencer".equals(EquipoService.estadoCalibracion(e))).count();

        List<Muestra> conservacion = muestras.stream()
                .filter(m -> m.getFechaVencimiento() != null && m.getEstado() != EstadoMuestra.REPORTADA
                        && !m.getFechaVencimiento().isAfter(hoy.plusDays(30)))
                .toList();

        List<Conteo> porSitio = muestras.stream()
                .collect(Collectors.groupingBy(m -> m.getSitio() == null ? "Sin sitio (anteriores a v5)"
                        : m.getSitio().getCodigo() + " · " + m.getSitio().getNombre(), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new Conteo(e.getKey(), e.getValue()))
                .toList();

        // ---- alertas, más recientes primero ----
        List<Alerta> alertas = new ArrayList<>();
        for (Equipo e : equipos) {
            String estado = EquipoService.estadoCalibracion(e);
            if ("Vencida".equals(estado) || "Por vencer".equals(estado)) {
                alertas.add(new Alerta("CALIBRACION", "Vencida".equals(estado) ? "alta" : "media",
                        "Equipo " + e.getEtiqueta() + ": calibración " + estado.toLowerCase()
                                + " (" + e.getFechaProximaCalibracion() + ")",
                        null, e.getFechaProximaCalibracion().atStartOfDay()));
            }
        }
        for (Resultado r : resultados) {
            Muestra m = r.getMuestraAnalisis().getMuestra();
            String nombre = r.getMuestraAnalisis().getAnalisis().getNombre();
            if (r.isAnomalo() && m.getEstado() != EstadoMuestra.REPORTADA) {
                alertas.add(new Alerta("ANOMALIA", "alta",
                        m.getCodigoUnico() + " · " + nombre + ": " + r.getDetalleAnomalia(), m.getId(), fecha(r)));
            } else if (!Boolean.TRUE.equals(r.getDentroRango()) && m.getEstado() != EstadoMuestra.REPORTADA) {
                alertas.add(new Alerta("FUERA_RANGO", "media",
                        m.getCodigoUnico() + " · " + nombre + " fuera de rango: "
                                + numero(r.getValorResultado()) + " " + r.getMuestraAnalisis().getAnalisis().getUnidadMedida(),
                        m.getId(), fecha(r)));
            }
        }
        for (Muestra m : muestras) {
            if (m.getEstado() == EstadoMuestra.RECHAZADA) {
                alertas.add(new Alerta("RECHAZO", "alta",
                        m.getCodigoUnico() + ": resultado rechazado, pendiente de corrección por el analista",
                        m.getId(), m.getFechaRecepcion()));
            }
        }
        for (Muestra m : conservacion) {
            boolean vencida = m.getFechaVencimiento().isBefore(hoy);
            alertas.add(new Alerta("CONSERVACION", vencida ? "alta" : "media",
                    m.getCodigoUnico() + ": plazo de conservación " + (vencida ? "vencido" : "por vencer")
                            + " (" + m.getFechaVencimiento() + ")",
                    m.getId(), m.getFechaVencimiento().atStartOfDay()));
        }
        alertas.sort(Comparator.comparing((Alerta a) -> "alta".equals(a.getNivel()) ? 0 : 1)
                .thenComparing(Alerta::getFecha, Comparator.nullsLast(Comparator.reverseOrder())));

        return new DashboardDTO(muestras.size(), porEstado, pendientes, fueraRango, anomalias,
                horasPromedioHastaAprobacion(), vencidos, porVencer, conservacion.size(), porSitio,
                alertas.size() > MAX_ALERTAS ? alertas.subList(0, MAX_ALERTAS) : alertas);
    }

    /** Serie de un parámetro en el tiempo, opcionalmente filtrada por sitio. */
    @Transactional(readOnly = true)
    public Tendencia tendencia(Integer idAnalisis, Integer idSitio) {
        AnalisisCatalogo analisis = analisisRepository.findById(idAnalisis)
                .orElseThrow(() -> new IllegalArgumentException("No existe el análisis con id " + idAnalisis));
        List<Punto> puntos = resultadoRepository.findByMuestraAnalisis_Analisis_Id(idAnalisis).stream()
                .filter(r -> idSitio == null || (r.getMuestraAnalisis().getMuestra().getSitio() != null
                        && idSitio.equals(r.getMuestraAnalisis().getMuestra().getSitio().getId())))
                .map(r -> {
                    Muestra m = r.getMuestraAnalisis().getMuestra();
                    LocalDateTime f = m.getFechaToma() != null ? m.getFechaToma() : fecha(r);
                    return new Punto(f, r.getValorResultado(), m.getId(), m.getCodigoUnico(),
                            m.getSitio() == null ? m.getProcedencia() : m.getSitio().getCodigo(),
                            Boolean.TRUE.equals(r.getDentroRango()), r.isAnomalo());
                })
                .sorted(Comparator.comparing(Punto::getFecha))
                .toList();
        return new Tendencia(analisis.getNombre(), analisis.getUnidadMedida(), analisis.getValorMinNormal(),
                analisis.getValorMaxNormal(), puntos);
    }

    /** Tiempo promedio entre la recepción y la primera vez que la muestra quedó Aprobada. */
    private Double horasPromedioHastaAprobacion() {
        Map<Integer, LocalDateTime> primeraAprobacion = new LinkedHashMap<>();
        for (MuestraHistorialEstado h : historialRepository.findAll()) {
            if (h.getEstadoNuevo() == EstadoMuestra.APROBADA) {
                primeraAprobacion.merge(h.getMuestra().getId(), h.getFechaCambio(),
                        (a, b) -> a.isBefore(b) ? a : b);
            }
        }
        OptionalDouble promedio = muestraRepository.findAllById(primeraAprobacion.keySet()).stream()
                .filter(m -> m.getFechaRecepcion() != null)
                .mapToDouble(m -> Duration.between(m.getFechaRecepcion(), primeraAprobacion.get(m.getId())).toMinutes() / 60.0)
                .filter(h -> h >= 0)
                .average();
        return promedio.isPresent() ? Math.round(promedio.getAsDouble() * 10) / 10.0 : null;
    }

    private LocalDateTime fecha(Resultado r) {
        return Objects.requireNonNullElse(r.getFechaModificacion(),
                Objects.requireNonNullElse(r.getFechaIngreso(), LocalDateTime.MIN));
    }

    private String numero(BigDecimal n) {
        return n == null ? "" : n.stripTrailingZeros().toPlainString();
    }
}
