package com.duoc.lims.limsbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class InvestigadorResponseDTO {
    private Integer id;
    private String nombre;
    private String apellido;
    private String institucion;
    private String email;
    private String telefono;
    private String proyecto;
    private boolean activo;
}
