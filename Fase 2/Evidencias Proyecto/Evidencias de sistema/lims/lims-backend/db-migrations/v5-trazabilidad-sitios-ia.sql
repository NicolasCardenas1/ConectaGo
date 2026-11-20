-- =====================================================================
-- MIGRACIÓN v5 — Trazabilidad del dato, sitios geolocalizados,
--                equipos con calibración, catálogos desplegables e IA
-- =====================================================================
-- Responde a la retroalimentación del docente (Fase 2):
--   * "No veo trazabilidad de los datos de análisis"  -> resultado_versiones,
--     equipos con calibración, versión evaluada en cada aprobación.
--   * "Base de datos de sitios de muestreo (geolocalización)" -> sitios_muestreo.
--   * "Pueden utilizar datos desplegables" -> sitios, equipos y ubicaciones
--     pasan a ser catálogos (ya no texto libre).
--   * "Pueden aplicar inteligencia artificial" -> puntaje de anomalía del
--     resultado e interpretación del informe asistida por IA (revisada).
--
-- Para bases de datos que YA existen (Aiven o un Docker que no quieran
-- recrear) y que ya tienen la migración v4. Una base nueva NO la necesita:
-- db-init/01-schema.sql ya trae todo esto. Ejecutar UNA sola vez:
--
--   docker exec -i lims-mysql mysql -u lims_user -plims_pass lims_db < db-migrations/v5-trazabilidad-sitios-ia.sql
-- =====================================================================

SET NAMES utf8mb4;   -- los textos del script tienen tildes: evita que el cliente los lea como latin1

-- 1) Sitios de muestreo con coordenadas (geolocalización)
CREATE TABLE IF NOT EXISTS sitios_muestreo (
    id_sitio        INT AUTO_INCREMENT PRIMARY KEY,
    codigo          VARCHAR(20)  NOT NULL UNIQUE,     -- ej. ST-001
    nombre          VARCHAR(150) NOT NULL,
    latitud         DECIMAL(9,6) NOT NULL,            -- grados decimales (sur = negativo)
    longitud        DECIMAL(9,6) NOT NULL,            -- grados decimales (oeste = negativo)
    tipo_ambiente   VARCHAR(40)  NOT NULL,            -- Marino costero, Estuario, Lago, Río, Humedal, Otro
    comuna          VARCHAR(80),
    region          VARCHAR(80),
    descripcion     VARCHAR(255),
    activo          BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_sitio_latitud  CHECK (latitud  BETWEEN -90  AND 90),
    CONSTRAINT chk_sitio_longitud CHECK (longitud BETWEEN -180 AND 180)
) ENGINE=InnoDB;

ALTER TABLE muestras
    ADD COLUMN id_sitio INT NULL AFTER procedencia,
    ADD CONSTRAINT fk_muestra_sitio FOREIGN KEY (id_sitio) REFERENCES sitios_muestreo(id_sitio);

-- 2) Equipos del laboratorio con su calibración (trazabilidad metrológica)
CREATE TABLE IF NOT EXISTS equipos (
    id_equipo                 INT AUTO_INCREMENT PRIMARY KEY,
    codigo                    VARCHAR(30)  NOT NULL UNIQUE,   -- ej. EQ-001 (etiqueta física del equipo)
    nombre                    VARCHAR(100) NOT NULL,
    marca                     VARCHAR(80),
    modelo                    VARCHAR(80),
    numero_serie              VARCHAR(80),
    fecha_ultima_calibracion  DATE NULL,
    fecha_proxima_calibracion DATE NULL,                      -- vencida = no se puede usar
    activo                    BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 3) Ubicaciones físicas de almacenamiento (lista desplegable)
CREATE TABLE IF NOT EXISTS ubicaciones_almacenamiento (
    id_ubicacion   INT AUTO_INCREMENT PRIMARY KEY,
    nombre         VARCHAR(100) NOT NULL UNIQUE,   -- se copia a muestras.ubicacion_almacenamiento
    tipo           VARCHAR(40)  NOT NULL,          -- Ultracongelador, Congelador, Refrigerador, Estante, Cámara
    temperatura    VARCHAR(30),
    descripcion    VARCHAR(255),
    activo         BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB;

-- 4) Resultados: equipo usado, versión vigente y evaluación de anomalía
ALTER TABLE resultados
    ADD COLUMN id_equipo          INT NULL AFTER instrumento_utilizado,
    ADD COLUMN version            INT NOT NULL DEFAULT 1,
    ADD COLUMN fecha_modificacion DATETIME NULL,
    ADD COLUMN puntaje_anomalia   DECIMAL(8,2) NULL,             -- puntaje z robusto (mediana/MAD)
    ADD COLUMN es_anomalo         BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN detalle_anomalia   VARCHAR(255) NULL,
    ADD CONSTRAINT fk_resultado_equipo FOREIGN KEY (id_equipo) REFERENCES equipos(id_equipo);

-- 5) Historial de versiones de cada resultado (nunca se sobrescribe un dato)
CREATE TABLE IF NOT EXISTS resultado_versiones (
    id_version            INT AUTO_INCREMENT PRIMARY KEY,
    id_resultado          INT NOT NULL,
    version               INT NOT NULL,
    valor_resultado       DECIMAL(12,4) NOT NULL,
    dentro_rango          BOOLEAN NOT NULL,
    id_equipo             INT NULL,
    instrumento_utilizado VARCHAR(100),
    observaciones         TEXT,
    es_anomalo            BOOLEAN NOT NULL DEFAULT FALSE,
    detalle_anomalia      VARCHAR(255),
    id_usuario            INT NOT NULL,                  -- quién ingresó esta versión
    fecha                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    motivo_cambio         VARCHAR(255) NULL,             -- obligatorio desde la versión 2
    CONSTRAINT fk_rv_resultado FOREIGN KEY (id_resultado) REFERENCES resultados(id_resultado),
    CONSTRAINT fk_rv_equipo    FOREIGN KEY (id_equipo) REFERENCES equipos(id_equipo),
    CONSTRAINT fk_rv_usuario   FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario),
    CONSTRAINT uq_rv_version UNIQUE (id_resultado, version),
    CONSTRAINT chk_rv_motivo CHECK (version = 1 OR motivo_cambio IS NOT NULL)
) ENGINE=InnoDB;

-- Los resultados que ya existían quedan como su versión 1.
INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo,
                                 instrumento_utilizado, observaciones, es_anomalo, detalle_anomalia,
                                 id_usuario, fecha, motivo_cambio)
SELECT r.id_resultado, 1, r.valor_resultado, r.dentro_rango, NULL, r.instrumento_utilizado, r.observaciones,
       FALSE, NULL, r.id_usuario_ingreso, r.fecha_ingreso, NULL
FROM resultados r
WHERE NOT EXISTS (SELECT 1 FROM resultado_versiones v WHERE v.id_resultado = r.id_resultado);

-- 6) Cada aprobación/rechazo indica QUÉ versión del resultado evaluó el supervisor
ALTER TABLE aprobaciones
    ADD COLUMN version_resultado INT NOT NULL DEFAULT 1 AFTER id_resultado;

-- 7) Interpretación de resultados para el informe (borrador IA revisado por un supervisor)
ALTER TABLE muestras
    ADD COLUMN interpretacion            TEXT NULL,
    ADD COLUMN interpretacion_origen     VARCHAR(40) NULL,   -- "IA revisada" / "Reglas revisadas" / "Manual"
    ADD COLUMN id_usuario_interpretacion INT NULL,
    ADD COLUMN fecha_interpretacion      DATETIME NULL,
    ADD CONSTRAINT fk_muestra_usuario_interp FOREIGN KEY (id_usuario_interpretacion) REFERENCES usuarios(id_usuario);

-- 8) Datos iniciales de los catálogos (coordenadas referenciales, Región de Los Lagos)
INSERT IGNORE INTO sitios_muestreo (codigo, nombre, latitud, longitud, tipo_ambiente, comuna, region, descripcion) VALUES
    ('ST-001', 'Bahía Ilque, estación 3',          -41.621500, -73.060800, 'Marino costero', 'Puerto Montt', 'Los Lagos', 'Pradera de macroalgas intermareal'),
    ('ST-002', 'Isla Tenglo, sector norte',        -41.490300, -72.973500, 'Marino costero', 'Puerto Montt', 'Los Lagos', 'Frente al canal Tenglo'),
    ('ST-003', 'Seno de Reloncaví, Caleta La Arena', -41.687900, -72.652700, 'Estuario',     'Puerto Montt', 'Los Lagos', 'Zona de mezcla agua dulce / marina'),
    ('ST-004', 'Canal de Chacao, Pargua',          -41.792600, -73.429800, 'Marino costero', 'Calbuco',      'Los Lagos', 'Corrientes intensas, alta renovación'),
    ('ST-005', 'Estuario río Maullín',             -41.611000, -73.598000, 'Estuario',       'Maullín',      'Los Lagos', 'Desembocadura del río Maullín'),
    ('ST-006', 'Lago Llanquihue, Puerto Varas',    -41.317600, -72.983100, 'Lago',           'Puerto Varas', 'Los Lagos', 'Costanera, muestreo de agua dulce');

INSERT IGNORE INTO equipos (codigo, nombre, marca, modelo, numero_serie, fecha_ultima_calibracion, fecha_proxima_calibracion) VALUES
    ('EQ-001', 'pHmetro de sobremesa',            'Hanna Instruments', 'HI5221',   'HI5221-0457', DATE_SUB(CURDATE(), INTERVAL 2 MONTH), DATE_ADD(CURDATE(), INTERVAL 4 MONTH)),
    ('EQ-002', 'Fluorómetro (clorofila-a)',       'Turner Designs',    'Trilogy',  'TR-7200-118', DATE_SUB(CURDATE(), INTERVAL 1 MONTH), DATE_ADD(CURDATE(), INTERVAL 5 MONTH)),
    ('EQ-003', 'Sonda multiparámetro',            'YSI',               'ProDSS',   'PD-19K10442', DATE_SUB(CURDATE(), INTERVAL 5 MONTH), DATE_ADD(CURDATE(), INTERVAL 20 DAY)),
    ('EQ-004', 'Espectrofotómetro UV-Vis',        'Hach',              'DR6000',   'DR6-1803344', DATE_SUB(CURDATE(), INTERVAL 3 MONTH), DATE_ADD(CURDATE(), INTERVAL 3 MONTH)),
    ('EQ-005', 'Balanza analítica',               'Sartorius',         'Quintix 125D', 'SQ-3302871', DATE_SUB(CURDATE(), INTERVAL 13 MONTH), DATE_SUB(CURDATE(), INTERVAL 1 MONTH));

INSERT IGNORE INTO ubicaciones_almacenamiento (nombre, tipo, temperatura, descripcion) VALUES
    ('Ultracongelador -80 °C · Rack A', 'Ultracongelador', '-80 °C', 'Conservación de largo plazo'),
    ('Congelador -20 °C · Rack B',      'Congelador',      '-20 °C', 'Muestras de algas congeladas'),
    ('Refrigerador 4 °C · Estante 1',   'Refrigerador',    '4 °C',   'Aguas y muestras en proceso'),
    ('Cámara de cultivo 18 °C',         'Cámara',          '18 °C',  'Microalgas vivas'),
    ('Estante temperatura ambiente',    'Estante',         'Ambiente', 'Muestras secas y sedimentos');
