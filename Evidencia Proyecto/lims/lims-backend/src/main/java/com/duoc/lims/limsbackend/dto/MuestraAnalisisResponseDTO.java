package com.duoc.lims.limsbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
public class MuestraAnalisisResponseDTO {
    private Integer id;
    private Integer idMuestra;
    private String codigoMuestra;
    private Integer idAnalisis;
    private String nombreAnalisis;
    private String analistaAsignado;
    private String estado;
    private LocalDateTime fechaAsignacion;
    private LocalDateTime fechaCompletado;
}