package com.duoc.lims.limsbackend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InvestigadorRequestDTO {

    @NotBlank(message = "nombre es obligatorio")
    private String nombre;

    @NotBlank(message = "apellido es obligatorio")
    private String apellido;

    private String institucion;

    @Email(message = "email debe tener un formato valido")
    private String email;

    private String telefono;

    private String proyecto;
}
