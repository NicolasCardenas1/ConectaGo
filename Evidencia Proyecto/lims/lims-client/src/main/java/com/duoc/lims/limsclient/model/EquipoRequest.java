package com.duoc.lims.limsclient.model;

import java.time.LocalDate;

/** v5 — Cuerpo de POST/PUT /api/equipos. */
public record EquipoRequest(
        String codigo,
        String nombre,
        String marca,
        String modelo,
        String numeroSerie,
        LocalDate fechaUltimaCalibracion,
        LocalDate fechaProximaCalibracion) {}
