package com.duoc.lims.limsbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class AnalisisCatalogoRequestDTO {

    @NotBlank(message = "nombre es obligatorio")
    private String nombre;

    private String descripcion;

    @NotBlank(message = "unidadMedida es obligatorio")
    private String unidadMedida;

    private String metodoReferencia;

    private BigDecimal tiempoEstimadoHrs;

    @NotNull(message = "valorMinNormal es obligatorio")
    private BigDecimal valorMinNormal;

    @NotNull(message = "valorMaxNormal es obligatorio")
    private BigDecimal valorMaxNormal;
}