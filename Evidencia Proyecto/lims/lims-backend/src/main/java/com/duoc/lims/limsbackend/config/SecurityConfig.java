package com.duoc.lims.limsbackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    // TEMPORAL: mientras no se implemente la tarea de Seguridad (login +
    // roles con Spring Security), se deja /api/** abierto para poder
    // construir y probar el CRUD de Muestras. Esto se reemplaza más
    // adelante por reglas reales (autenticación + hasRole(...)) y BCrypt.
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/**", "/error").permitAll()
                        .anyRequest().authenticated()
                );
        return http.build();
    }
}