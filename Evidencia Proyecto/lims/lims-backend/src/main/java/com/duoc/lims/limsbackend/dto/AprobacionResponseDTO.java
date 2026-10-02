package com.duoc.lims.limsbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
public class AprobacionResponseDTO {
    private Integer id;
    private Integer idResultado;
    private String codigoMuestra;
    private String nombreAnalisis;
    private String supervisor;
    private String estadoAprobacion;
    private String comentario;
    private LocalDateTime fechaAprobacion;
    private String estadoMuestra;
}