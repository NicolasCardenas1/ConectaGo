package com.duoc.lims.limsclient.model;

import java.time.LocalDate;

/** v6 — Cuerpo de POST /api/equipos/{id}/calibraciones. */
public record CalibracionRequest(
        LocalDate fechaCalibracion,
        LocalDate fechaVencimiento,
        String realizadaPor,
        String certificado,
        String observaciones) {}
