package com.duoc.lims.limsclient.model;

import java.math.BigDecimal;

/** v5 — Cuerpo de POST/PUT /api/sitios. */
public record SitioRequest(
        String codigo,
        String nombre,
        BigDecimal latitud,
        BigDecimal longitud,
        String tipoAmbiente,
        String comuna,
        String region,
        String descripcion) {}
