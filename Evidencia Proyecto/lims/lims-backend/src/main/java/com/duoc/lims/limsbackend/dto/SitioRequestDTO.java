package com.duoc.lims.limsbackend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class SitioRequestDTO {

    // Si viene vacío el backend genera el siguiente (ST-007, ST-008...).
    private String codigo;

    @NotBlank(message = "el nombre del sitio es obligatorio")
    private String nombre;

    @NotNull(message = "la latitud es obligatoria")
    @DecimalMin(value = "-90", message = "la latitud debe estar entre -90 y 90")
    @DecimalMax(value = "90", message = "la latitud debe estar entre -90 y 90")
    private BigDecimal latitud;

    @NotNull(message = "la longitud es obligatoria")
    @DecimalMin(value = "-180", message = "la longitud debe estar entre -180 y 180")
    @DecimalMax(value = "180", message = "la longitud debe estar entre -180 y 180")
    private BigDecimal longitud;

    @NotBlank(message = "el tipo de ambiente es obligatorio")
    private String tipoAmbiente;

    private String comuna;

    private String region;

    private String descripcion;
}
