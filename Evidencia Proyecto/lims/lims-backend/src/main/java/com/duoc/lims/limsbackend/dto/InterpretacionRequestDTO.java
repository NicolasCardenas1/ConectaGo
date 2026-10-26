package com.duoc.lims.limsbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** v5 — Interpretación revisada que el supervisor guarda para el informe PDF. */
@Getter
@Setter
public class InterpretacionRequestDTO {

    @NotBlank(message = "el texto de la interpretación es obligatorio")
    @Size(max = 4000, message = "la interpretación no puede superar 4000 caracteres")
    private String texto;

    // "IA revisada", "Reglas revisadas" o "Manual"
    @NotBlank(message = "el origen de la interpretación es obligatorio")
    private String origen;
}
