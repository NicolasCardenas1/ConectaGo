package com.duoc.lims.limsbackend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AprobacionRequestDTO {

    @NotNull(message = "idResultado es obligatorio")
    private Integer idResultado;

    // RF06: lo completa el backend con el usuario de la sesión (el valor que envíe el cliente se ignora).
    private Integer idSupervisor;

    // "Aprobado" o "Rechazado"
    @NotNull(message = "estadoAprobacion es obligatorio")
    private String estadoAprobacion;

    // Obligatorio solo si se rechaza (lo valida el service).
    private String comentario;
}