-- =====================================================================
-- MIGRACIÓN v6 — Equipos de terreno e historial de calibraciones
-- =====================================================================
-- Responde a la retroalimentación del docente (presentación Fase 3):
--   * "Base de datos de los equipos"  -> los equipos pasan a cubrir también el
--     muestreo en terreno (uso Terreno / Laboratorio / Ambos), cada muestra
--     registra con qué equipo se TOMÓ, y cada calibración queda en un historial
--     que nunca se sobrescribe (equipo_calibraciones).
--   * "Chatbot de trazabilidad" -> no necesita tablas nuevas: consulta en modo
--     solo lectura lo que ya registra el sistema (muestras, sitios, custodia,
--     resultados, equipos, aprobaciones e informes).
--
-- Para bases de datos que YA existen y ya tienen la v5. Una base nueva NO la
-- necesita: db-init/01-schema.sql ya trae todo esto. Ejecutar UNA sola vez:
--
--   docker cp db-migrations\v6-equipos-terreno-calibraciones.sql lims-mysql:/tmp/m.sql
--   docker exec lims-mysql sh -c "mysql --default-character-set=utf8mb4 -ulims_user -plims_pass lims_db < /tmp/m.sql"
-- =====================================================================

SET NAMES utf8mb4;   -- los textos del script tienen tildes

-- 1) Dónde se usa cada equipo: en terreno (toma de muestras), en el laboratorio (análisis) o en ambos
ALTER TABLE equipos
    ADD COLUMN uso VARCHAR(20) NOT NULL DEFAULT 'Laboratorio' AFTER numero_serie,
    ADD CONSTRAINT chk_equipo_uso CHECK (uso IN ('Terreno', 'Laboratorio', 'Ambos'));

-- 2) Historial de calibraciones: cada calibración es una fila nueva (trazabilidad metrológica).
--    equipos.fecha_ultima_calibracion / fecha_proxima_calibracion quedan como "vigente" (la última fila).
CREATE TABLE IF NOT EXISTS equipo_calibraciones (
    id_calibracion     INT AUTO_INCREMENT PRIMARY KEY,
    id_equipo          INT NOT NULL,
    fecha_calibracion  DATE NOT NULL,
    fecha_vencimiento  DATE NOT NULL,
    realizada_por      VARCHAR(120),                  -- laboratorio o técnico que calibró
    certificado        VARCHAR(80),                   -- N° del certificado de calibración
    observaciones      VARCHAR(255),
    id_usuario         INT NULL,                      -- quién la registró en el sistema
    fecha_registro     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cal_equipo  FOREIGN KEY (id_equipo)  REFERENCES equipos(id_equipo),
    CONSTRAINT fk_cal_usuario FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario),
    CONSTRAINT chk_cal_fechas CHECK (fecha_vencimiento >= fecha_calibracion),
    INDEX idx_cal_equipo (id_equipo)
) ENGINE=InnoDB;

-- 3) Con qué equipo se tomó la muestra en terreno
ALTER TABLE muestras
    ADD COLUMN id_equipo_toma INT NULL AFTER id_sitio,
    ADD CONSTRAINT fk_muestra_equipo_toma FOREIGN KEY (id_equipo_toma) REFERENCES equipos(id_equipo);

-- 4) Datos iniciales: la sonda multiparámetro se usa en terreno y en laboratorio,
--    y se agregan equipos de terreno
UPDATE equipos SET uso = 'Ambos' WHERE codigo = 'EQ-003';

INSERT IGNORE INTO equipos (codigo, nombre, marca, modelo, numero_serie, uso, fecha_ultima_calibracion, fecha_proxima_calibracion) VALUES
    ('EQ-006', 'Botella Niskin 5 L',             'General Oceanics', '1010-5',   'GO-1010-2291', 'Terreno', NULL, NULL),
    ('EQ-007', 'GPS de mano',                    'Garmin',           'GPSMAP 67', 'GM67-4410873', 'Terreno', DATE_SUB(CURDATE(), INTERVAL 4 MONTH), DATE_ADD(CURDATE(), INTERVAL 8 MONTH)),
    ('EQ-008', 'Draga Van Veen (sedimentos)',    'KC Denmark',       '12.110',   'KC-12110-077', 'Terreno', NULL, NULL),
    ('EQ-009', 'Cuadrante de muestreo 0,25 m²',  'Fabricación propia', NULL,     NULL,           'Terreno', NULL, NULL);

-- 5) La calibración vigente de cada equipo pasa a ser la primera fila de su historial
INSERT INTO equipo_calibraciones (id_equipo, fecha_calibracion, fecha_vencimiento, observaciones)
SELECT e.id_equipo, e.fecha_ultima_calibracion, e.fecha_proxima_calibracion, 'Calibración vigente al migrar a v6'
FROM equipos e
WHERE e.fecha_ultima_calibracion IS NOT NULL AND e.fecha_proxima_calibracion IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM equipo_calibraciones c WHERE c.id_equipo = e.id_equipo);

-- Datos de demostración (equipo de toma de las muestras de la demo, calibraciones anteriores):
-- ejecutar después db-migrations/datos-demo-v6.sql
