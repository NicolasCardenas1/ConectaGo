package com.duoc.lims.limsclient.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** v5 — Indicadores y alertas del panel de inicio (GET /api/dashboard). */
public record Dashboard(
        long totalMuestras,
        Map<String, Long> muestrasPorEstado,
        long pendientesAprobacion,
        long fueraDeRango30Dias,
        long anomalias30Dias,
        Double horasPromedioHastaAprobacion,
        long equiposCalibracionVencida,
        long equiposCalibracionPorVencer,
        long muestrasPorVencerConservacion,
        List<Conteo> muestrasPorSitio,
        List<Alerta> alertas) {

    public record Conteo(String etiqueta, long cantidad) {}

    public record Alerta(String tipo, String nivel, String mensaje, Integer idMuestra, LocalDateTime fecha) {}

    /** Serie de un parámetro en el tiempo (GET /api/dashboard/tendencia). */
    public record Tendencia(String analisis, String unidad, BigDecimal valorMinNormal, BigDecimal valorMaxNormal,
                            List<Punto> puntos) {}

    public record Punto(LocalDateTime fecha, BigDecimal valor, Integer idMuestra, String codigoMuestra,
                        String sitio, boolean dentroRango, boolean anomalo) {}
}
