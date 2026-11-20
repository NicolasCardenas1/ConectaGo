package com.duoc.lims.limsbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Firma de un evento de custodia. Quien recibe es el usuario de la sesión. */
@Getter
@Setter
public class CustodiaRequestDTO {

    @NotBlank(message = "tipoEvento es obligatorio")
    private String tipoEvento;

    @NotBlank(message = "entregadoPor es obligatorio")
    private String entregadoPor;

    private String ubicacion;

    private String condicion;

    // La "firma": el receptor debe confirmar explícitamente.
    @NotNull(message = "confirmado es obligatorio")
    private Boolean confirmado;

    private String observaciones;
}
