package com.duoc.lims.limsclient.model;

import java.math.BigDecimal;

/** Cuerpo de POST/PUT /api/analisis (solo Administrador). */
public record AnalisisCatalogoRequest(
        String nombre,
        String descripcion,
        String unidadMedida,
        String metodoReferencia,
        BigDecimal tiempoEstimadoHrs,
        BigDecimal valorMinNormal,
        BigDecimal valorMaxNormal) {}
