-- =====================================================================
-- MIGRACIÓN v4 — Dominio de investigación ambiental
-- =====================================================================
-- Para bases de datos que YA existen (Aiven, o un Docker que no quieran
-- recrear). Una base nueva NO la necesita: db-init/01-schema.sql ya trae
-- todo esto. Ejecutar UNA sola vez, en orden, por ejemplo:
--
--   docker exec -i lims-mysql mysql -u lims_user -plims_pass lims_db < db-migrations/v4-dominio-ambiental.sql
--
-- o pegándolo en el cliente MySQL conectado a Aiven.
-- =====================================================================

-- 1) Investigadores (quien encarga el muestreo y recibe el informe)
CREATE TABLE IF NOT EXISTS investigadores (
    id_investigador INT AUTO_INCREMENT PRIMARY KEY,
    nombre          VARCHAR(100) NOT NULL,
    apellido        VARCHAR(100) NOT NULL,
    institucion     VARCHAR(150),
    email           VARCHAR(150),
    telefono        VARCHAR(30),
    proyecto        VARCHAR(200),
    activo          BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

ALTER TABLE muestras
    ADD COLUMN id_investigador INT NULL AFTER procedencia,
    ADD CONSTRAINT fk_muestra_investigador FOREIGN KEY (id_investigador) REFERENCES investigadores(id_investigador);

-- 2) Tipos de muestra ambientales (se migran los valores antiguos)
--    a) se amplía el ENUM con los valores viejos + nuevos
ALTER TABLE muestras MODIFY tipo_muestra
    ENUM('Agua potable','Agua residual','Sangre','Suelo','Alimento',
         'Macroalga','Microalga','Agua de mar','Agua dulce','Sedimento','Otro') NOT NULL DEFAULT 'Otro';
--    b) se convierten los valores antiguos
UPDATE muestras SET tipo_muestra = 'Agua dulce' WHERE tipo_muestra IN ('Agua potable', 'Agua residual');
UPDATE muestras SET tipo_muestra = 'Sedimento'  WHERE tipo_muestra = 'Suelo';
UPDATE muestras SET tipo_muestra = 'Otro'       WHERE tipo_muestra IN ('Sangre', 'Alimento');
--    c) se deja solo la lista nueva
ALTER TABLE muestras MODIFY tipo_muestra
    ENUM('Macroalga','Microalga','Agua de mar','Agua dulce','Sedimento','Otro') NOT NULL DEFAULT 'Otro';

-- 3) Cadena de custodia (firma de entrega / recepción)
CREATE TABLE IF NOT EXISTS custodia_eventos (
    id_custodia       INT AUTO_INCREMENT PRIMARY KEY,
    id_muestra        INT NOT NULL,
    tipo_evento       VARCHAR(40) NOT NULL,
    entregado_por     VARCHAR(150) NOT NULL,
    id_usuario_recibe INT NOT NULL,
    ubicacion         VARCHAR(100),
    condicion         VARCHAR(150),
    confirmado        BOOLEAN NOT NULL,
    fecha_hora        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    observaciones     VARCHAR(255),
    CONSTRAINT fk_custodia_muestra FOREIGN KEY (id_muestra) REFERENCES muestras(id_muestra),
    CONSTRAINT fk_custodia_usuario FOREIGN KEY (id_usuario_recibe) REFERENCES usuarios(id_usuario),
    CONSTRAINT chk_custodia_firmada CHECK (confirmado = TRUE),
    INDEX idx_custodia_muestra (id_muestra)
) ENGINE=InnoDB;

-- 4) El laboratorio pasa a ser de tipo ambiental
UPDATE centros SET tipo_centro = 'Ambiental' WHERE id_centro = 1;

-- 5) (Opcional, recomendado) Desactivar los análisis clínicos de prueba del catálogo
--    para que no aparezcan en la demo. Revisen los nombres antes de ejecutar:
-- SELECT id_analisis, nombre FROM analisis_catalogo;
-- UPDATE analisis_catalogo SET activo = FALSE WHERE nombre IN ('Hemograma Completo', 'Glucosa', 'Colesterol');
