package com.duoc.lims.limsbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MuestraRequestDTO {

    @NotNull(message = "tipoMuestra es obligatorio")
    private String tipoMuestra;

    @NotBlank(message = "procedencia (cliente) es obligatorio")
    private String procedencia;

    @NotNull(message = "prioridad es obligatorio")
    private String prioridad;

    private String observaciones;

    // Temporal: hasta que exista login (tarea de Seguridad), el cliente
    // debe indicar explícitamente quién registra la muestra.
    @NotNull(message = "idUsuarioRegistro es obligatorio")
    private Integer idUsuarioRegistro;
}