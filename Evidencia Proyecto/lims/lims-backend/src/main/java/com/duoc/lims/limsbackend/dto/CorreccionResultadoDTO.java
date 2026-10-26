package com.duoc.lims.limsbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** v5 — Corrección de un resultado (crea una versión nueva; el valor anterior se conserva). */
@Getter
@Setter
public class CorreccionResultadoDTO {

    @NotNull(message = "valorResultado es obligatorio")
    private BigDecimal valorResultado;

    @NotNull(message = "idEquipo es obligatorio: indica con qué equipo se midió")
    private Integer idEquipo;

    private String observaciones;

    // ISO 17025: toda modificación de un dato registrado debe justificarse.
    @NotBlank(message = "el motivo del cambio es obligatorio")
    private String motivoCambio;

    // Lo completa el backend con el usuario de la sesión.
    private Integer idUsuario;
}
