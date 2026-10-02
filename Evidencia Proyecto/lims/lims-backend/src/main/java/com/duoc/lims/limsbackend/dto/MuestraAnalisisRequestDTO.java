package com.duoc.lims.limsbackend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MuestraAnalisisRequestDTO {

    @NotNull(message = "idMuestra es obligatorio")
    private Integer idMuestra;

    @NotNull(message = "idAnalisis es obligatorio")
    private Integer idAnalisis;

    // Opcional: si se asigna un analista al momento de solicitar el análisis.
    private Integer idAnalistaAsignado;
}