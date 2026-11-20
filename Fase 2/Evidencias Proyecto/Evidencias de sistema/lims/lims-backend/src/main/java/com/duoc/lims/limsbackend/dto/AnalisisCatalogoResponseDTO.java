package com.duoc.lims.limsbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
public class AnalisisCatalogoResponseDTO {
    private Integer id;
    private String nombre;
    private String descripcion;
    private String unidadMedida;
    private String metodoReferencia;
    private BigDecimal tiempoEstimadoHrs;
    private BigDecimal valorMinNormal;
    private BigDecimal valorMaxNormal;
    private Boolean activo;
}