package com.duoc.lims.limsclient.model;

import java.time.LocalDateTime;

/** Respuesta de POST /api/usuarios/login: token de sesión, expiración y datos del usuario. */
public record LoginResponse(String token, LocalDateTime expira, UsuarioResponse usuario) {}
