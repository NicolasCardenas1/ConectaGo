package com.duoc.lims.limsbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class UsuarioResponseDTO {
    private Integer id;
    private String nombre;
    private String apellido;
    private String email;
    private String username;
    private String nombreRol;
    private String nombreCentro;
    private boolean activo;
    private boolean requiereResetPassword;
}