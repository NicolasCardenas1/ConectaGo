package com.duoc.lims.limsbackend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UsuarioRequestDTO {

    @NotNull(message = "idCentro es obligatorio")
    private Integer idCentro;

    @NotBlank(message = "nombre es obligatorio")
    private String nombre;

    @NotBlank(message = "apellido es obligatorio")
    private String apellido;

    @NotBlank(message = "email es obligatorio")
    @Email(message = "email debe tener un formato valido")
    private String email;

    @NotBlank(message = "username es obligatorio")
    private String username;

    @NotBlank(message = "password es obligatorio")
    private String password;

    @NotNull(message = "idRol es obligatorio")
    private Integer idRol;
}