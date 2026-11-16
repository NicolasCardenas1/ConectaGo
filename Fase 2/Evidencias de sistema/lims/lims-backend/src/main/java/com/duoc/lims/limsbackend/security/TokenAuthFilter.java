package com.duoc.lims.limsbackend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Lee el encabezado {@code Authorization: Bearer <token>} de cada petición y, si el token es válido,
 * registra al usuario como autenticado con su rol (ROLE_ANALISTA, ROLE_SUPERVISOR, ROLE_ADMINISTRADOR).
 * Si no hay token o es inválido no hace nada: SecurityConfig decide si la ruta exige sesión.
 */
public class TokenAuthFilter extends OncePerRequestFilter {

    private static final String PREFIJO = "Bearer ";

    private final SesionService sesionService;

    public TokenAuthFilter(SesionService sesionService) {
        this.sesionService = sesionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String encabezado = request.getHeader("Authorization");
        if (encabezado != null && encabezado.startsWith(PREFIJO)) {
            String token = encabezado.substring(PREFIJO.length()).trim();
            sesionService.obtener(token).ifPresent(sesion -> {
                UsuarioAutenticado usuario = sesion.usuario();
                var autenticacion = new UsernamePasswordAuthenticationToken(
                        usuario, token,
                        List.of(new SimpleGrantedAuthority("ROLE_" + usuario.rolSpring())));
                SecurityContextHolder.getContext().setAuthentication(autenticacion);
            });
        }
        chain.doFilter(request, response);
    }
}
