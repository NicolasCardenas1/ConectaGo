package com.duoc.lims.limsclient.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** v5 — Una versión de un resultado con la evaluación que recibió (GET /api/resultados/{id}/versiones). */
public record ResultadoVersion(
        Integer version,
        BigDecimal valorResultado,
        String unidadMedida,
        Boolean dentroRango,
        String equipo,
        String observaciones,
        boolean anomalo,
        String detalleAnomalia,
        String usuario,
        LocalDateTime fecha,
        String motivoCambio,
        String estadoAprobacion,
        String supervisor,
        String comentarioAprobacion,
        LocalDateTime fechaAprobacion) {}
