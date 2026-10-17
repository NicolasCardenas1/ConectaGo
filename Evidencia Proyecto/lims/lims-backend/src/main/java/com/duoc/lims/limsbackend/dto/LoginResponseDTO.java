package com.duoc.lims.limsbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Respuesta del login: el token de sesión, cuándo expira y los datos del usuario. */
@Getter
@Setter
@AllArgsConstructor
public class LoginResponseDTO {
    private String token;
    private LocalDateTime expira;
    private UsuarioResponseDTO usuario;
}
