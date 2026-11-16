package com.duoc.lims.limsbackend.security;

/**
 * Usuario de la sesión actual. Spring Security lo deja disponible en los controllers con
 * {@code @AuthenticationPrincipal UsuarioAutenticado yo}. Así el backend sabe QUIÉN hace cada
 * acción sin confiar en un id enviado por el cliente.
 */
public record UsuarioAutenticado(Integer id, String username, String nombreCompleto, String rol) {

    /** Nombre del rol en el formato que usa Spring Security: "Supervisor" -> "SUPERVISOR". */
    public String rolSpring() {
        return rol.toUpperCase();
    }
}
