package com.duoc.lims.limsclient.model;

import java.math.BigDecimal;

/** v5 — Comparación de un valor contra el historial del sitio (GET /api/resultados/anomalia). */
public record Anomalia(
        boolean evaluado,
        boolean anomalo,
        BigDecimal puntaje,
        int tamanoHistorial,
        BigDecimal mediana,
        String referencia,
        String mensaje) {}
