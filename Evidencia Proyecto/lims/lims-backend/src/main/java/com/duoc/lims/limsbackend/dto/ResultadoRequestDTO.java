package com.duoc.lims.limsbackend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ResultadoRequestDTO {

    @NotNull(message = "idMuestraAnalisis es obligatorio")
    private Integer idMuestraAnalisis;

    @NotNull(message = "valorResultado es obligatorio")
    private BigDecimal valorResultado;

    private String instrumentoUtilizado;

    @NotNull(message = "idUsuarioIngreso es obligatorio")
    private Integer idUsuarioIngreso;

    private String observaciones;
}