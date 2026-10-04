package com.duoc.lims.limsclient.model;

import java.math.BigDecimal;

/** Cuerpo de POST /api/resultados. */
public record IngresoResultadoRequest(
        Integer idMuestraAnalisis,
        BigDecimal valorResultado,
        String instrumentoUtilizado,
        Integer idUsuarioIngreso,
        String observaciones) {}