package com.duoc.lims.limsbackend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AprobacionRequestDTO {

    @NotNull(message = "idResultado es obligatorio")
    private Integer idResultado;

    @NotNull(message = "idSupervisor es obligatorio")
    private Integer idSupervisor;

    // "Aprobado" o "Rechazado"
    @NotNull(message = "estadoAprobacion es obligatorio")
    private String estadoAprobacion;

    // Obligatorio solo si se rechaza (lo valida el service).
    private String comentario;
}