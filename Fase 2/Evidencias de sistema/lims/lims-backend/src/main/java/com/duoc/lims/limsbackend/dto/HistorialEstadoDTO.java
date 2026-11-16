package com.duoc.lims.limsbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
public class HistorialEstadoDTO {
    private String estadoAnterior;   // null en la recepción
    private String estadoNuevo;
    private String usuario;
    private LocalDateTime fecha;
    private String comentario;
}
