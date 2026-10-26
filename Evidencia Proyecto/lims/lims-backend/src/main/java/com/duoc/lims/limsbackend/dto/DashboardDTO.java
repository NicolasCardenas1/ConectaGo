package com.duoc.lims.limsbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** v5 — Indicadores y alertas del panel de inicio. */
@Getter
@Setter
@AllArgsConstructor
public class DashboardDTO {
    private long totalMuestras;
    private Map<String, Long> muestrasPorEstado;
    private long pendientesAprobacion;
    private long fueraDeRango30Dias;
    private long anomalias30Dias;
    private Double horasPromedioHastaAprobacion;   // recepción → aprobada (null si aún no hay)
    private long equiposCalibracionVencida;
    private long equiposCalibracionPorVencer;
    private long muestrasPorVencerConservacion;
    private List<Conteo> muestrasPorSitio;
    private List<Alerta> alertas;

    @Getter
    @Setter
    @AllArgsConstructor
    public static class Conteo {
        private String etiqueta;
        private long cantidad;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    public static class Alerta {
        private String tipo;      // ANOMALIA, FUERA_RANGO, RECHAZO, CALIBRACION, CONSERVACION
        private String nivel;     // alta / media
        private String mensaje;
        private Integer idMuestra;
        private LocalDateTime fecha;
    }

    /** Serie de un parámetro en el tiempo (gráfico de tendencia). */
    @Getter
    @Setter
    @AllArgsConstructor
    public static class Tendencia {
        private String analisis;
        private String unidad;
        private BigDecimal valorMinNormal;
        private BigDecimal valorMaxNormal;
        private List<Punto> puntos;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    public static class Punto {
        private LocalDateTime fecha;
        private BigDecimal valor;
        private Integer idMuestra;
        private String codigoMuestra;
        private String sitio;
        private boolean dentroRango;
        private boolean anomalo;
    }
}
