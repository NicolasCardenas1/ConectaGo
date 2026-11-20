package com.duoc.lims.limsbackend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** v6 — Registrar una calibración nueva de un equipo (POST /api/equipos/{id}/calibraciones). */
@Getter
@Setter
public class CalibracionRequestDTO {

    @NotNull(message = "la fecha de calibración es obligatoria")
    private LocalDate fechaCalibracion;

    @NotNull(message = "la fecha de vencimiento es obligatoria")
    private LocalDate fechaVencimiento;

    private String realizadaPor;

    private String certificado;

    private String observaciones;
}
