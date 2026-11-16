package com.duoc.lims.limsbackend.config;

import com.duoc.lims.limsbackend.model.Centro;
import com.duoc.lims.limsbackend.model.Rol;
import com.duoc.lims.limsbackend.model.Usuario;
import com.duoc.lims.limsbackend.repository.CentroRepository;
import com.duoc.lims.limsbackend.repository.RolRepository;
import com.duoc.lims.limsbackend.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * RF06 — Con la seguridad activa, solo un Administrador puede crear usuarios con rol.
 * Para no quedar "encerrados" en una base de datos nueva, si NO existe ningún usuario se crea
 * un Administrador inicial:  usuario "admin"  /  contraseña "admin123".
 * Cámbienle la contraseña desde la pantalla de Usuarios apenas entren.
 */
@Configuration
public class DatosInicialesConfig {

    private static final Logger log = LoggerFactory.getLogger(DatosInicialesConfig.class);

    @Bean
    CommandLineRunner crearAdministradorInicial(UsuarioRepository usuarios, RolRepository roles,
                                                CentroRepository centros) {
        return args -> {
            if (usuarios.count() > 0) {
                return;
            }
            Rol admin = roles.findByNombreRol("Administrador").orElse(null);
            Centro centro = centros.findById(1).orElse(null);
            if (admin == null || centro == null) {
                log.warn("No se pudo crear el administrador inicial: faltan el rol 'Administrador' o el centro 1");
                return;
            }
            Usuario u = new Usuario();
            u.setCentro(centro);
            u.setNombre("Administrador");
            u.setApellido("LIMS");
            u.setEmail("admin@lims.cl");
            u.setUsername("admin");
            u.setPasswordHash(new BCryptPasswordEncoder().encode("admin123"));
            u.setRol(admin);
            u.setActivo(true);
            usuarios.save(u);
            log.warn("Se creó el usuario administrador inicial 'admin' / 'admin123'. Cambien la contraseña.");
        };
    }
}
