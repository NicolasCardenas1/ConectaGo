package com.duoc.lims.limsbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
public class MuestraResponseDTO {
    private Integer id;
    private String codigoUnico;
    private String tipoMuestra;
    private String procedencia;
    private String prioridad;
    private String estado;
    private LocalDateTime fechaRecepcion;
    private String usuarioRegistro;
    private String observaciones;
}