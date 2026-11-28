package com.duoc.lims.limsbackend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Pone la base de datos al día al arrancar el backend.
 *
 * Antes cada integrante tenía que ejecutar a mano los scripts de db-migrations, y si se le pasaba uno el
 * backend fallaba con errores como "Unknown column 'codigo_verificacion'". Ahora, al iniciar, se revisa
 * qué versión tiene la base (local en Docker o Aiven) y se aplican solo las migraciones que faltan, en
 * orden. Se puede arrancar cuantas veces se quiera: lo que ya está aplicado no se vuelve a ejecutar.
 *
 * Los scripts son los mismos archivos de lims-backend/db-migrations (el pom.xml los copia al jar).
 * Corre antes que cualquier otra tarea de arranque (por ejemplo, crear el administrador inicial).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ActualizacionBaseDatos implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ActualizacionBaseDatos.class);

    /** Una migración y la consulta que dice si ya está aplicada (devuelve una fila si ya está). */
    private record Migracion(String version, String archivo, String yaAplicada) {}

    private static final List<Migracion> MIGRACIONES = List.of(
            new Migracion("v4", "v4-dominio-ambiental.sql", tabla("investigadores")),
            new Migracion("v5", "v5-trazabilidad-sitios-ia.sql", tabla("resultado_versiones")),
            new Migracion("v6", "v6-equipos-terreno-calibraciones.sql", tabla("equipo_calibraciones")),
            new Migracion("v7", "v7-firma-digital-informes.sql", columna("reportes", "codigo_verificacion")),
            new Migracion("v8", "v8-catalogo-analisis-ambientales.sql",
                    "SELECT 1 FROM analisis_catalogo WHERE nombre = 'Nitrato'"));

    private final DataSource dataSource;

    public ActualizacionBaseDatos(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection con = dataSource.getConnection()) {
            int aplicadas = 0;
            for (Migracion m : MIGRACIONES) {
                if (existe(con, m.yaAplicada())) {
                    continue;
                }
                log.info("Base de datos: aplicando migración {} ({})...", m.version(), m.archivo());
                try {
                    ScriptUtils.executeSqlScript(con, new EncodedResource(
                            new ClassPathResource("db/migraciones/" + m.archivo()), StandardCharsets.UTF_8));
                } catch (Exception e) {
                    throw new IllegalStateException("No se pudo aplicar la migración " + m.version() + " ("
                            + m.archivo() + "): " + e.getMessage() + ". Revisa la base de datos o aplícala a mano"
                            + " como indica el README.", e);
                }
                aplicadas++;
            }
            log.info(aplicadas == 0
                    ? "Base de datos al día (v8)."
                    : "Base de datos actualizada a v8 (" + aplicadas + " migración(es) aplicada(s)).");
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo revisar la versión de la base de datos: " + e.getMessage(), e);
        }
    }

    private static boolean existe(Connection con, String consulta) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(consulta); ResultSet rs = ps.executeQuery()) {
            return rs.next();
        }
    }

    private static String tabla(String nombre) {
        return "SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = '"
                + nombre + "'";
    }

    private static String columna(String tabla, String columna) {
        return "SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = '"
                + tabla + "' AND column_name = '" + columna + "'";
    }
}
