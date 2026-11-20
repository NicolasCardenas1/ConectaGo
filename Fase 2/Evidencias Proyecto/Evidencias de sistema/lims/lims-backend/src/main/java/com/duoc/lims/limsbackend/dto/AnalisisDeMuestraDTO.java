package com.duoc.lims.limsbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
public class AnalisisDeMuestraDTO {
    private Integer idMuestraAnalisis;
    private Integer idAnalisis;
    private String nombreAnalisis;
    private String unidadMedida;
    private BigDecimal valorMinNormal;
    private BigDecimal valorMaxNormal;
    private String analistaAsignado;
    private String estado;
    private ResultadoResponseDTO resultado;   // null si aún no se ingresa
    private String estadoAprobacion;          // null si no ha sido evaluado
    private String comentarioAprobacion;
}