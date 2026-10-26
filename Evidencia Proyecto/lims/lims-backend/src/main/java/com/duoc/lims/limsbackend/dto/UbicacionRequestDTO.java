package com.duoc.lims.limsbackend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UbicacionRequestDTO {

    @NotBlank(message = "el nombre de la ubicación es obligatorio")
    private String nombre;

    @NotBlank(message = "el tipo de ubicación es obligatorio")
    private String tipo;

    private String temperatura;

    private String descripcion;
}
