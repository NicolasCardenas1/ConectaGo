package com.duoc.lims.limsclient.model;

/** Resumen de un informe emitido (GET /api/reportes). */
public record ReporteResumen(
        Integer id,
        String numeroInforme,
        Integer idMuestra,
        String codigoMuestra,
        String tipoMuestra,
        String investigador,
        Integer version,
        String emitidoPor,
        String fechaGeneracion) {
}
