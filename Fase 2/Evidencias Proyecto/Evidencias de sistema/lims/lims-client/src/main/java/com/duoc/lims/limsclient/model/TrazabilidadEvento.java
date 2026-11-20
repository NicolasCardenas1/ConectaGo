package com.duoc.lims.limsclient.model;

import java.time.LocalDateTime;

/** v5 — Hito de la línea de tiempo de una muestra (GET /api/muestras/{id}/trazabilidad). */
public record TrazabilidadEvento(
        LocalDateTime fecha,
        String categoria,
        String titulo,
        String detalle,
        String usuario,
        boolean alerta) {}
