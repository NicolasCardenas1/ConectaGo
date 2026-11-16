package com.duoc.lims.limsbackend.security;

import com.duoc.lims.limsbackend.model.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RF06 — Sesiones de usuario basadas en token.
 *
 * Al iniciar sesión se genera un token aleatorio (256 bits) que el cliente envía en cada petición
 * con el encabezado {@code Authorization: Bearer <token>}. Las sesiones se guardan en memoria:
 * si el backend se reinicia, los usuarios simplemente vuelven a iniciar sesión.
 * Cada sesión expira tras {@code lims.sesion.horas} horas (por defecto 8, un turno de laboratorio).
 */
@Service
public class SesionService {

    /** Datos de una sesión activa. */
    public record Sesion(UsuarioAutenticado usuario, LocalDateTime expira) {
        boolean vigente() {
            return LocalDateTime.now().isBefore(expira);
        }
    }

    private final Map<String, Sesion> sesiones = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final long horasDuracion;

    public SesionService(@Value("${lims.sesion.horas:8}") long horasDuracion) {
        this.horasDuracion = horasDuracion;
    }

    /** Crea una sesión nueva para el usuario y devuelve su token. */
    public String iniciar(Usuario usuario) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        UsuarioAutenticado ua = new UsuarioAutenticado(
                usuario.getId(),
                usuario.getUsername(),
                usuario.getNombre() + " " + usuario.getApellido(),
                usuario.getRol().getNombreRol());
        sesiones.put(token, new Sesion(ua, LocalDateTime.now().plusHours(horasDuracion)));
        return token;
    }

    /** Devuelve la sesión si el token existe y no ha expirado (las expiradas se eliminan). */
    public Optional<Sesion> obtener(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Sesion sesion = sesiones.get(token);
        if (sesion == null) {
            return Optional.empty();
        }
        if (!sesion.vigente()) {
            sesiones.remove(token);
            return Optional.empty();
        }
        return Optional.of(sesion);
    }

    public LocalDateTime expiracion(String token) {
        return obtener(token).map(Sesion::expira).orElse(null);
    }

    /** Cierra una sesión (logout). */
    public void cerrar(String token) {
        if (token != null) {
            sesiones.remove(token);
        }
    }

    /** Cierra todas las sesiones de un usuario (ej. al desactivarlo o resetear su contraseña). */
    public void cerrarSesionesDe(Integer idUsuario) {
        sesiones.entrySet().removeIf(e -> e.getValue().usuario().id().equals(idUsuario));
    }
}
