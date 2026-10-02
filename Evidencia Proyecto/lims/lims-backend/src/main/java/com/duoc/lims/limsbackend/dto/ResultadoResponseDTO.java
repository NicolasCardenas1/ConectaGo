package com.duoc.lims.limsbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
public class ResultadoResponseDTO {
    private Integer id;
    private Integer idMuestraAnalisis;
    private String nombreAnalisis;
    private String codigoMuestra;
    private BigDecimal valorResultado;
    private String unidadMedida;
    private BigDecimal valorMinNormal;
    private BigDecimal valorMaxNormal;
    private Boolean dentroRango;
    private String instrumentoUtilizado;
    private String usuarioIngreso;
    private LocalDateTime fechaIngreso;
    private String observaciones;
}