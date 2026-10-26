package com.duoc.lims.limsbackend.config;

import com.duoc.lims.limsbackend.security.SesionService;
import com.duoc.lims.limsbackend.security.TokenAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * RF06 — Autenticación con roles diferenciados.
 *
 * Toda la API exige una sesión válida (token), salvo login, registro público y "olvidé mi contraseña".
 * Además, algunas acciones están restringidas por rol:
 *
 * | Acción                                         | Analista | Supervisor | Administrador |
 * |------------------------------------------------|:--------:|:----------:|:-------------:|
 * | Ver muestras, registrar, asignar, ingresar     |    sí    |     sí     |      sí       |
 * | Aprobar / rechazar resultados                  |    no    |     sí     |      sí       |
 * | Emitir informe PDF                             |    no    |     sí     |      sí       |
 * | Interpretación del informe (borrador IA)       |    no    |     sí     |      sí       |
 * | Crear / editar análisis del catálogo           |    no    |     no     |      sí       |
 * | Crear / editar equipos y ubicaciones (v5)      |    no    |     no     |      sí       |
 * | Administrar usuarios y contraseñas             |    no    |     no     |      sí       |
 */
@Configuration
public class SecurityConfig {

    private static final String SUPERVISOR = "SUPERVISOR";
    private static final String ADMINISTRADOR = "ADMINISTRADOR";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, SesionService sesionService) throws Exception {
        http
                .csrf(csrf -> csrf.disable())   // API stateless con token: no usa cookies de sesión
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .exceptionHandling(e -> e
                        // 401: no hay sesión o expiró
                        .authenticationEntryPoint((req, res, ex) -> {
                            res.setStatus(401);
                            res.setContentType("text/plain;charset=UTF-8");
                            res.getWriter().write("Sesión no válida o expirada. Inicia sesión nuevamente.");
                        })
                        // 403: hay sesión, pero el rol no tiene permiso
                        .accessDeniedHandler((req, res, ex) -> {
                            res.setStatus(403);
                            res.setContentType("text/plain;charset=UTF-8");
                            res.getWriter().write("No tienes permisos para realizar esta acción.");
                        }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()

                        // Públicas
                        .requestMatchers(HttpMethod.POST,
                                "/api/usuarios/login",
                                "/api/usuarios/registro",
                                "/api/usuarios/solicitar-reset").permitAll()

                        // Cualquier usuario con sesión
                        .requestMatchers(HttpMethod.POST, "/api/usuarios/logout").authenticated()

                        // Solo Supervisor o Administrador (RF04 y RF05)
                        .requestMatchers(HttpMethod.POST, "/api/aprobaciones").hasAnyRole(SUPERVISOR, ADMINISTRADOR)
                        .requestMatchers(HttpMethod.POST, "/api/muestras/*/reporte").hasAnyRole(SUPERVISOR, ADMINISTRADOR)
                        // Historial de informes y re-descarga (RF05): Supervisor o Administrador
                        .requestMatchers(HttpMethod.GET, "/api/reportes", "/api/reportes/**").hasAnyRole(SUPERVISOR, ADMINISTRADOR)
                        // v5: interpretación de resultados para el informe (borrador IA y versión revisada)
                        .requestMatchers("/api/muestras/*/interpretacion", "/api/muestras/*/interpretacion/**")
                                .hasAnyRole(SUPERVISOR, ADMINISTRADOR)

                        // Solo Administrador: catálogo de análisis (escritura) y usuarios
                        .requestMatchers(HttpMethod.POST, "/api/analisis").hasRole(ADMINISTRADOR)
                        .requestMatchers(HttpMethod.PUT, "/api/analisis/**").hasRole(ADMINISTRADOR)
                        .requestMatchers(HttpMethod.PATCH, "/api/analisis/**").hasRole(ADMINISTRADOR)
                        // v5: equipos (calibración) y ubicaciones de almacenamiento: lectura para todos, escritura Administrador
                        .requestMatchers(HttpMethod.POST, "/api/equipos", "/api/ubicaciones").hasRole(ADMINISTRADOR)
                        .requestMatchers(HttpMethod.PUT, "/api/equipos/**", "/api/ubicaciones/**").hasRole(ADMINISTRADOR)
                        .requestMatchers(HttpMethod.PATCH, "/api/equipos/**", "/api/ubicaciones/**").hasRole(ADMINISTRADOR)
                        .requestMatchers("/api/usuarios/**").hasRole(ADMINISTRADOR)
                        .requestMatchers("/api/usuarios").hasRole(ADMINISTRADOR)

                        // El resto de la API: cualquier usuario con sesión
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll()
                )
                .addFilterBefore(new TokenAuthFilter(sesionService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
