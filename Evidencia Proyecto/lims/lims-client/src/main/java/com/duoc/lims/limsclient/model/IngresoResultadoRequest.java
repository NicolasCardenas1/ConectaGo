package com.duoc.lims.limsclient.model;

import java.math.BigDecimal;

/** Cuerpo de POST /api/resultados. */
public record IngresoResultadoRequest(
        Integer idMuestraAnalisis,
        BigDecimal valorResultado,
        Integer idEquipo,                 // v5: equipo calibrado (obligatorio)
        String instrumentoUtilizado,
        Integer idUsuarioIngreso,
        String observaciones) {}