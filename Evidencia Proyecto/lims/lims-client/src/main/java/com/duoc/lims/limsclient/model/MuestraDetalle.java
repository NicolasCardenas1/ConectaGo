package com.duoc.lims.limsclient.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Respuesta de GET /api/muestras/{id}: la muestra con sus análisis, resultados y aprobación. */
public record MuestraDetalle(Muestra muestra, List<AnalisisItem> analisis, List<HistorialItem> historial) {

    public record AnalisisItem(
            Integer idMuestraAnalisis,
            Integer idAnalisis,
            String nombreAnalisis,
            String unidadMedida,
            BigDecimal valorMinNormal,
            BigDecimal valorMaxNormal,
            String analistaAsignado,
            String estado,
            ResultadoItem resultado,      // null si aún no se ingresa
            String estadoAprobacion,      // null si no ha sido evaluado
            String comentarioAprobacion) {}

    /** Un cambio de estado de la muestra (trazabilidad ISO 17025). */
    public record HistorialItem(
            String estadoAnterior,     // null en la recepción
            String estadoNuevo,
            String usuario,
            LocalDateTime fecha,
            String comentario) {}

    public record ResultadoItem(
            Integer id,
            BigDecimal valorResultado,
            String unidadMedida,
            Boolean dentroRango,
            String instrumentoUtilizado,
            String usuarioIngreso,
            LocalDateTime fechaIngreso,
            String observaciones) {}
}