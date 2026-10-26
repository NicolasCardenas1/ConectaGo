package com.duoc.lims.limsclient.model;

import java.math.BigDecimal;

/** v5 — Cuerpo de PUT /api/resultados/{id}: crea una versión nueva del resultado. */
public record CorreccionResultadoRequest(
        BigDecimal valorResultado,
        Integer idEquipo,
        String observaciones,
        String motivoCambio) {}
