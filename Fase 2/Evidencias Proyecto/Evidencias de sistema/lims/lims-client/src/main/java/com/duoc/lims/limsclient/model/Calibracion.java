package com.duoc.lims.limsclient.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** v6 — Una calibración del historial de un equipo (GET /api/equipos/{id}/calibraciones). */
public record Calibracion(
        Integer id,
        LocalDate fechaCalibracion,
        LocalDate fechaVencimiento,
        String realizadaPor,
        String certificado,
        String observaciones,
        String registradaPor,
        LocalDateTime fechaRegistro,
        boolean vigente) {}
