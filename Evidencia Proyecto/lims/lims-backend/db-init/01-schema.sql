-- =====================================================================
-- PROYECTO APT — LIMS (Sistema de Gestión de Información de Laboratorio)
-- Esquema de Base de Datos — MySQL 8.x — v5
-- Equipo: Claudio Murua, Cristian Solís, Nicolás Cárdenas
-- Cubre: RF01-RF06 y soporta RNF01, RNF03 del documento de Fase 1
-- =====================================================================
-- Motor: InnoDB -> soporta llaves foráneas y transacciones, necesario
--        para no perder integridad entre muestra -> análisis -> resultado
--        -> aprobación -> reporte.
-- Charset: utf8mb4 -> evita problemas con tildes/ñ (nombres, direcciones).
--
-- v2: incorpora al núcleo un set de mejoras "de bajo costo, alto valor"
-- levantadas al comparar este diseño contra LIMS reales (SENAITE/Bika,
-- SampleManager, Matrix Gemini) y contra los requisitos típicos de
-- ISO/IEC 17025 sobre trazabilidad de muestras y de resultados:
--   1) Historial de estados de la muestra (auditoría acotada, sin el
--      costo de una tabla de auditoría genérica para todas las tablas).
--   2) Datos de cadena de custodia "ligera" en la propia muestra
--      (condición de recepción, ubicación de almacenamiento, vencimiento).
--   3) Distinción de muestras de control de calidad (blanco/duplicado/
--      estándar) vs. muestras de investigadores, sin crear un módulo aparte.
--   4) Trazabilidad de qué instrumento generó cada resultado.
--   5) Justificación obligatoria para resultados fuera de rango y para
--      aprobaciones rechazadas, forzada con CHECK constraints de MySQL 8.
--
-- v3: agrega la tabla "centros" y la referencia desde usuarios/muestras.
-- Decisión de equipo: la instalación sigue siendo de UN solo centro (no
-- se implementa aislamiento multi-tenant todavía: sin filtros por centro
-- en las consultas, sin un rol "superadministrador" que vea varios a la
-- vez), pero se deja el dato modelado desde ya para que, si el producto
-- se ofrece a más de un laboratorio más adelante, sea una extensión del
-- backend (agregar el filtro "WHERE id_centro = ..." en cada consulta)
-- y no una migración de esquema.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS lims_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE lims_db;

-- =====================================================================
-- 1. CENTROS  (laboratorio/sede que usa el sistema)
-- =====================================================================
-- Para el MVP habrá una sola fila en esta tabla (el laboratorio propio
-- del equipo). El resto del sistema ya queda "enganchado" a un centro
-- mediante id_centro en usuarios y muestras, así que escalar a varios
-- laboratorios más adelante no requiere alterar tablas, solo insertar
-- más filas aquí y agregar el filtro por centro en el backend.

CREATE TABLE centros (
    id_centro    INT AUTO_INCREMENT PRIMARY KEY,
    nombre_centro VARCHAR(150) NOT NULL,
    tipo_centro  ENUM('Clinico','Acuicola','Ambiental','Universitario','Otro') NOT NULL,
    direccion    VARCHAR(255),
    telefono     VARCHAR(30),
    email        VARCHAR(150),
    activo       BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB;

-- =====================================================================
-- 2. ROLES Y USUARIOS  (RF06 - autenticación con roles / RNF03 - hash)
-- =====================================================================
-- Se usa tabla "roles" en vez de ENUM directo en usuarios: si más adelante
-- agregan un rol nuevo (ej. "Auditor de calidad") no hay que migrar la
-- tabla usuarios, solo insertar una fila.

CREATE TABLE roles (
    id_rol      INT AUTO_INCREMENT PRIMARY KEY,
    nombre_rol  VARCHAR(50) NOT NULL UNIQUE,   -- Administrador, Supervisor, Analista
    descripcion VARCHAR(255)
) ENGINE=InnoDB;

CREATE TABLE usuarios (
    id_usuario         INT AUTO_INCREMENT PRIMARY KEY,
    id_centro          INT NOT NULL,                  -- a qué laboratorio pertenece este usuario
    nombre             VARCHAR(100) NOT NULL,
    apellido           VARCHAR(100) NOT NULL,
    email              VARCHAR(150) NOT NULL UNIQUE,
    username           VARCHAR(50)  NOT NULL UNIQUE,
    password_hash      VARCHAR(255) NOT NULL,       -- BCrypt (RNF03), NUNCA texto plano
    id_rol             INT NOT NULL,
    activo             BOOLEAN NOT NULL DEFAULT TRUE, -- baja lógica, no se borra (trazabilidad)
    requiere_reset_password BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_creacion     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_ultimo_login DATETIME NULL,
    CONSTRAINT fk_usuario_rol    FOREIGN KEY (id_rol) REFERENCES roles(id_rol),
    CONSTRAINT fk_usuario_centro FOREIGN KEY (id_centro) REFERENCES centros(id_centro)
) ENGINE=InnoDB;

-- =====================================================================
-- 3. CATÁLOGO DE ANÁLISIS Y RANGOS DE REFERENCIA  (RF02 / RF03)
-- =====================================================================

CREATE TABLE analisis_catalogo (
    id_analisis         INT AUTO_INCREMENT PRIMARY KEY,
    nombre              VARCHAR(100) NOT NULL,
    descripcion         VARCHAR(255),
    unidad_medida       VARCHAR(20) NOT NULL,       -- mg/L, %, UFC/mL, etc.
    metodo_referencia   VARCHAR(150),                -- norma o método (útil para ISO 17025)
    tiempo_estimado_hrs DECIMAL(6,2),
    valor_min_normal    DECIMAL(12,4) NOT NULL,      -- rango válido -> usado por RF03
    valor_max_normal    DECIMAL(12,4) NOT NULL,
    activo              BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB;

-- Nota de diseño: el rango se dejó como 2 columnas en el propio catálogo
-- para que el MVP de 12 semanas sea simple de implementar y de validar
-- desde el backend (RF03: "if valor between min and max"). Si más adelante
-- el mismo análisis necesita rangos distintos según el tipo de muestra
-- (ej. pH en agua vs. pH en suelo), ver la mejora "rangos_referencia +
-- tipos_muestra" al final del archivo.
--
-- Nota multi-centro: esta tabla se deja GLOBAL (sin id_centro) a propósito.
-- Si el día de mañana cada laboratorio necesita su propio catálogo de
-- análisis, se agrega id_centro aquí igual que se hizo en usuarios/muestras;
-- mientras tanto, un catálogo compartido es más simple de mantener.

-- =====================================================================
-- 3b. INVESTIGADORES  (v4 - dominio: investigación ambiental)
-- =====================================================================
-- El LIMS atiende a investigadores que toman muestras en terreno (ej. algas)
-- y las conservan por largos periodos. El investigador NO es usuario del
-- sistema: es quien encarga el muestreo y recibe el informe de resultados.

CREATE TABLE investigadores (
    id_investigador INT AUTO_INCREMENT PRIMARY KEY,
    nombre          VARCHAR(100) NOT NULL,
    apellido        VARCHAR(100) NOT NULL,
    institucion     VARCHAR(150),                 -- universidad, centro de investigación, etc.
    email           VARCHAR(150),
    telefono        VARCHAR(30),
    proyecto        VARCHAR(200),                 -- proyecto o línea de investigación
    activo          BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- =====================================================================
-- 3c. CATÁLOGOS DEL LABORATORIO  (v5 - geolocalización y desplegables)
-- =====================================================================
-- Sitios de muestreo con coordenadas: permiten ubicar cada muestra en un
-- mapa y comparar resultados del mismo sitio en el tiempo (tendencias y
-- detección de anomalías).

CREATE TABLE sitios_muestreo (
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

-- Equipos con su calibración: cada resultado indica con qué equipo se midió
-- y el backend impide usar un equipo con la calibración vencida (ISO 17025).
CREATE TABLE equipos (
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

-- Ubicaciones físicas de almacenamiento (lista desplegable en vez de texto libre).
CREATE TABLE ubicaciones_almacenamiento (
    id_ubicacion   INT AUTO_INCREMENT PRIMARY KEY,
    nombre         VARCHAR(100) NOT NULL UNIQUE,   -- se copia a muestras.ubicacion_almacenamiento
    tipo           VARCHAR(40)  NOT NULL,          -- Ultracongelador, Congelador, Refrigerador, Estante, Cámara
    temperatura    VARCHAR(30),
    descripcion    VARCHAR(255),
    activo         BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB;

-- =====================================================================
-- 4. MUESTRAS  (RF01 - registro con código único y estado de workflow)
-- =====================================================================

CREATE TABLE muestras (
    id_muestra              INT AUTO_INCREMENT PRIMARY KEY,
    id_centro               INT NOT NULL,                  -- a qué laboratorio pertenece la muestra
    codigo_unico            VARCHAR(30) NOT NULL,          -- ej. LAB-2026-000123 (único DENTRO del centro)
    tipo_muestra            ENUM('Macroalga','Microalga','Agua de mar','Agua dulce','Sedimento','Otro')
                             NOT NULL DEFAULT 'Otro',
    procedencia             VARCHAR(150),                  -- nombre del sitio (copia legible; antes de v5, texto libre)
    id_sitio                INT NULL,                      -- v5: sitio de muestreo georreferenciado
    id_investigador         INT NULL,                      -- investigador que encarga el muestreo (destinatario del informe)
    id_usuario_registro     INT NOT NULL,                  -- quién la ingresó al sistema
    fecha_toma              DATETIME NULL,                 -- cuándo se tomó la muestra
    fecha_recepcion         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    estado                  ENUM('Recibida','En analisis','Resultados ingresados',
                                   'Aprobada','Rechazada','Reportada')
                             NOT NULL DEFAULT 'Recibida',
    tipo_registro           ENUM('Muestra','Blanco','Duplicado','Estandar')
                             NOT NULL DEFAULT 'Muestra',
        -- Mejora: en todo LIMS real, junto a las muestras de investigación circulan
        -- muestras de control de calidad (blancos, duplicados, estándares)
        -- que validan al analista/instrumento. Marcarlas aquí evita crear un
        -- módulo aparte y permite excluirlas de los informes al investigador.
    condicion_recepcion     VARCHAR(150),   -- estado físico/temperatura al recibir (cadena de custodia)
    ubicacion_almacenamiento VARCHAR(100),  -- dónde queda guardada físicamente
    fecha_vencimiento       DATE NULL,      -- hasta cuándo debe conservarse la muestra
    prioridad               ENUM('Baja','Media','Alta') NOT NULL DEFAULT 'Media',
    observaciones           TEXT,
    -- v5: interpretación de resultados para el informe (borrador IA revisado por un supervisor)
    interpretacion            TEXT NULL,
    interpretacion_origen     VARCHAR(40) NULL,   -- "IA revisada" / "Reglas revisadas" / "Manual"
    id_usuario_interpretacion INT NULL,
    fecha_interpretacion      DATETIME NULL,
    CONSTRAINT fk_muestra_usuario FOREIGN KEY (id_usuario_registro) REFERENCES usuarios(id_usuario),
    CONSTRAINT fk_muestra_sitio   FOREIGN KEY (id_sitio) REFERENCES sitios_muestreo(id_sitio),
    CONSTRAINT fk_muestra_usuario_interp FOREIGN KEY (id_usuario_interpretacion) REFERENCES usuarios(id_usuario),
    CONSTRAINT fk_muestra_centro  FOREIGN KEY (id_centro) REFERENCES centros(id_centro),
    CONSTRAINT fk_muestra_investigador FOREIGN KEY (id_investigador) REFERENCES investigadores(id_investigador),
    -- Único DENTRO de cada centro, no global: dos laboratorios distintos
    -- pueden generar el mismo código sin chocar entre sí.
    CONSTRAINT uq_muestra_codigo UNIQUE (id_centro, codigo_unico),
    INDEX idx_muestra_estado (estado),           -- acelera el listado filtrado por estado
    INDEX idx_muestra_fecha_recepcion (fecha_recepcion)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Historial de estados de la muestra (mejora: audit trail acotado).
-- En vez de una tabla de auditoría genérica para todas las tablas (más
-- cara de implementar en 12 semanas), se registra cada transición de
-- estado de la entidad más crítica del sistema: la muestra. Esto ya
-- responde a la exigencia típica de ISO 17025 de dejar un "rastro"
-- documentado de quién hizo qué y cuándo sobre la muestra.
-- ---------------------------------------------------------------------

CREATE TABLE muestra_historial_estado (
    id_historial    INT AUTO_INCREMENT PRIMARY KEY,
    id_muestra      INT NOT NULL,
    estado_anterior ENUM('Recibida','En analisis','Resultados ingresados',
                          'Aprobada','Rechazada','Reportada') NULL,
    estado_nuevo    ENUM('Recibida','En analisis','Resultados ingresados',
                          'Aprobada','Rechazada','Reportada') NOT NULL,
    id_usuario      INT NOT NULL,          -- quién provocó el cambio
    fecha_cambio    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    comentario      VARCHAR(255),
    CONSTRAINT fk_hist_muestra FOREIGN KEY (id_muestra) REFERENCES muestras(id_muestra),
    CONSTRAINT fk_hist_usuario FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario),
    INDEX idx_hist_muestra (id_muestra)
) ENGINE=InnoDB;
-- Se llena con un INSERT desde el service layer de Spring Boot cada vez
-- que cambia "muestras.estado" (no requiere triggers de MySQL para el MVP).

-- =====================================================================
-- 5. ANÁLISIS SOLICITADOS POR MUESTRA  (tabla puente)
-- =====================================================================
-- Una muestra puede requerir varios análisis a la vez (ej. una muestra de
-- agua puede pedir pH, turbidez y coliformes en la misma recepción).

CREATE TABLE muestra_analisis (
    id_muestra_analisis  INT AUTO_INCREMENT PRIMARY KEY,
    id_muestra           INT NOT NULL,
    id_analisis          INT NOT NULL,
    id_analista_asignado INT NULL,                     -- se asigna al iniciar el análisis
    estado               ENUM('Pendiente','En proceso','Completado')
                          NOT NULL DEFAULT 'Pendiente',
    fecha_asignacion     DATETIME NULL,
    fecha_completado     DATETIME NULL,
    CONSTRAINT fk_ma_muestra   FOREIGN KEY (id_muestra) REFERENCES muestras(id_muestra),
    CONSTRAINT fk_ma_analisis  FOREIGN KEY (id_analisis) REFERENCES analisis_catalogo(id_analisis),
    CONSTRAINT fk_ma_analista  FOREIGN KEY (id_analista_asignado) REFERENCES usuarios(id_usuario),
    CONSTRAINT uq_muestra_analisis UNIQUE (id_muestra, id_analisis)  -- evita duplicar el mismo análisis
) ENGINE=InnoDB;

-- =====================================================================
-- 6. RESULTADOS  (RF02 - ingreso de resultados / RF03 - validación de rango)
-- =====================================================================

CREATE TABLE resultados (
    id_resultado         INT AUTO_INCREMENT PRIMARY KEY,
    id_muestra_analisis  INT NOT NULL UNIQUE,   -- 1 resultado vigente por análisis solicitado
    valor_resultado      DECIMAL(12,4) NOT NULL, -- DECIMAL, no FLOAT: evita errores de redondeo en mediciones
    dentro_rango         BOOLEAN NOT NULL,       -- calculado por el backend contra el rango del análisis
    instrumento_utilizado VARCHAR(100),           -- texto legible del equipo (copia de equipos.codigo + nombre)
    id_equipo            INT NULL,                -- v5: equipo calibrado con que se midió
    id_usuario_ingreso   INT NOT NULL,
    fecha_ingreso        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    observaciones        TEXT,
    version              INT NOT NULL DEFAULT 1,  -- v5: versión vigente (ver resultado_versiones)
    fecha_modificacion   DATETIME NULL,
    puntaje_anomalia     DECIMAL(8,2) NULL,       -- v5: puntaje z robusto (mediana/MAD) vs. historial
    es_anomalo           BOOLEAN NOT NULL DEFAULT FALSE,
    detalle_anomalia     VARCHAR(255) NULL,
    CONSTRAINT fk_resultado_ma      FOREIGN KEY (id_muestra_analisis) REFERENCES muestra_analisis(id_muestra_analisis),
    CONSTRAINT fk_resultado_usuario FOREIGN KEY (id_usuario_ingreso) REFERENCES usuarios(id_usuario),
    CONSTRAINT fk_resultado_equipo  FOREIGN KEY (id_equipo) REFERENCES equipos(id_equipo),
    -- Mejora: ISO 17025 exige documentar por qué un resultado no conforme
    -- fue aceptado/reportado. MySQL 8 soporta CHECK real (se valida en cada
    -- INSERT/UPDATE, no solo se acepta y se ignora como en versiones viejas).
    CONSTRAINT chk_resultado_justificacion
        CHECK (dentro_rango = TRUE OR observaciones IS NOT NULL)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- v5: historial de versiones de cada resultado. Un resultado NUNCA se
-- sobrescribe sin rastro: cada ingreso o corrección (por ejemplo, después
-- de un rechazo) agrega una fila con el valor, el equipo, quién y cuándo,
-- y el motivo del cambio (obligatorio desde la versión 2).
-- ---------------------------------------------------------------------

CREATE TABLE resultado_versiones (
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

-- =====================================================================
-- 7. APROBACIONES  (RF04 - aprobación por un supervisor)
-- =====================================================================
-- Se deja como tabla separada (no un simple campo "aprobado" en
-- resultados) para conservar el historial completo: si un supervisor
-- rechaza y el analista reingresa el resultado, queda registro de ambos
-- eventos en vez de sobrescribir el primero.

CREATE TABLE aprobaciones (
    id_aprobacion     INT AUTO_INCREMENT PRIMARY KEY,
    id_resultado      INT NOT NULL,
    version_resultado INT NOT NULL DEFAULT 1,   -- v5: qué versión del resultado se evaluó
    id_supervisor     INT NOT NULL,
    estado_aprobacion ENUM('Aprobado','Rechazado') NOT NULL,
    comentario        VARCHAR(255),
    fecha_aprobacion  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_aprobacion_resultado   FOREIGN KEY (id_resultado) REFERENCES resultados(id_resultado),
    CONSTRAINT fk_aprobacion_supervisor  FOREIGN KEY (id_supervisor) REFERENCES usuarios(id_usuario),
    -- Mejora: un rechazo sin motivo no es trazable ni defendible ante una
    -- auditoría de calidad. Se exige comentario solo cuando se rechaza.
    CONSTRAINT chk_aprobacion_motivo
        CHECK (estado_aprobacion = 'Aprobado' OR comentario IS NOT NULL)
) ENGINE=InnoDB;

-- =====================================================================
-- 8. REPORTES  (RF05 - generación de PDF descargable)
-- =====================================================================

CREATE TABLE reportes (
    id_reporte           INT AUTO_INCREMENT PRIMARY KEY,
    id_muestra           INT NOT NULL,
    id_usuario_generador INT NOT NULL,
    fecha_generacion     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ruta_archivo         VARCHAR(255) NOT NULL,   -- ubicación/URL del PDF generado
    version              INT NOT NULL DEFAULT 1,   -- permite regenerar sin perder el historial anterior
    CONSTRAINT fk_reporte_muestra  FOREIGN KEY (id_muestra) REFERENCES muestras(id_muestra),
    CONSTRAINT fk_reporte_usuario  FOREIGN KEY (id_usuario_generador) REFERENCES usuarios(id_usuario)
) ENGINE=InnoDB;

-- =====================================================================
-- 9. CADENA DE CUSTODIA  (v4 - firma de entrega/recepción de la muestra)
-- =====================================================================
-- Cada vez que la muestra cambia de manos o de lugar (entrega del
-- investigador, traslado a almacenamiento, retiro para análisis...) se
-- registra un evento. La "firma" es la confirmación explícita del usuario
-- que recibe, con fecha y hora (CHECK confirmado = TRUE).

CREATE TABLE custodia_eventos (
    id_custodia       INT AUTO_INCREMENT PRIMARY KEY,
    id_muestra        INT NOT NULL,
    tipo_evento       VARCHAR(40) NOT NULL,     -- Recepción en laboratorio / Traslado / Retiro para análisis / Devolución a almacenamiento
    entregado_por     VARCHAR(150) NOT NULL,    -- quién entrega físicamente (ej. el investigador)
    id_usuario_recibe INT NOT NULL,             -- quién recibe y firma en el sistema
    ubicacion         VARCHAR(100),             -- dónde queda la muestra después del evento
    condicion         VARCHAR(150),             -- estado / temperatura de la muestra
    confirmado        BOOLEAN NOT NULL,         -- firma: confirmación explícita
    fecha_hora        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    observaciones     VARCHAR(255),
    CONSTRAINT fk_custodia_muestra FOREIGN KEY (id_muestra) REFERENCES muestras(id_muestra),
    CONSTRAINT fk_custodia_usuario FOREIGN KEY (id_usuario_recibe) REFERENCES usuarios(id_usuario),
    CONSTRAINT chk_custodia_firmada CHECK (confirmado = TRUE),
    INDEX idx_custodia_muestra (id_muestra)
) ENGINE=InnoDB;

-- =====================================================================
-- DATOS INICIALES MÍNIMOS (seed)
-- =====================================================================

-- Editar con los datos reales del laboratorio antes de usar en producción.
INSERT INTO centros (nombre_centro, tipo_centro, direccion, telefono, email) VALUES
    ('Laboratorio Central', 'Ambiental', 'Por definir', NULL, NULL);

INSERT INTO roles (nombre_rol, descripcion) VALUES
    ('Administrador', 'Gestiona usuarios, roles y catálogo de análisis'),
    ('Supervisor',     'Aprueba o rechaza resultados ingresados por analistas'),
    ('Analista',       'Registra muestras e ingresa resultados de análisis');

-- v5: catálogos iniciales (coordenadas referenciales, Región de Los Lagos)
INSERT INTO sitios_muestreo (codigo, nombre, latitud, longitud, tipo_ambiente, comuna, region, descripcion) VALUES
    ('ST-001', 'Bahía Ilque, estación 3',            -41.621500, -73.060800, 'Marino costero', 'Puerto Montt', 'Los Lagos', 'Pradera de macroalgas intermareal'),
    ('ST-002', 'Isla Tenglo, sector norte',          -41.490300, -72.973500, 'Marino costero', 'Puerto Montt', 'Los Lagos', 'Frente al canal Tenglo'),
    ('ST-003', 'Seno de Reloncaví, Caleta La Arena', -41.687900, -72.652700, 'Estuario',       'Puerto Montt', 'Los Lagos', 'Zona de mezcla agua dulce / marina'),
    ('ST-004', 'Canal de Chacao, Pargua',            -41.792600, -73.429800, 'Marino costero', 'Calbuco',      'Los Lagos', 'Corrientes intensas, alta renovación'),
    ('ST-005', 'Estuario río Maullín',               -41.611000, -73.598000, 'Estuario',       'Maullín',      'Los Lagos', 'Desembocadura del río Maullín'),
    ('ST-006', 'Lago Llanquihue, Puerto Varas',      -41.317600, -72.983100, 'Lago',           'Puerto Varas', 'Los Lagos', 'Costanera, muestreo de agua dulce');

INSERT INTO equipos (codigo, nombre, marca, modelo, numero_serie, fecha_ultima_calibracion, fecha_proxima_calibracion) VALUES
    ('EQ-001', 'pHmetro de sobremesa',      'Hanna Instruments', 'HI5221',       'HI5221-0457', DATE_SUB(CURDATE(), INTERVAL 2 MONTH),  DATE_ADD(CURDATE(), INTERVAL 4 MONTH)),
    ('EQ-002', 'Fluorómetro (clorofila-a)', 'Turner Designs',    'Trilogy',      'TR-7200-118', DATE_SUB(CURDATE(), INTERVAL 1 MONTH),  DATE_ADD(CURDATE(), INTERVAL 5 MONTH)),
    ('EQ-003', 'Sonda multiparámetro',      'YSI',               'ProDSS',       'PD-19K10442', DATE_SUB(CURDATE(), INTERVAL 5 MONTH),  DATE_ADD(CURDATE(), INTERVAL 20 DAY)),
    ('EQ-004', 'Espectrofotómetro UV-Vis',  'Hach',              'DR6000',       'DR6-1803344', DATE_SUB(CURDATE(), INTERVAL 3 MONTH),  DATE_ADD(CURDATE(), INTERVAL 3 MONTH)),
    ('EQ-005', 'Balanza analítica',         'Sartorius',         'Quintix 125D', 'SQ-3302871',  DATE_SUB(CURDATE(), INTERVAL 13 MONTH), DATE_SUB(CURDATE(), INTERVAL 1 MONTH));

INSERT INTO ubicaciones_almacenamiento (nombre, tipo, temperatura, descripcion) VALUES
    ('Ultracongelador -80 °C · Rack A', 'Ultracongelador', '-80 °C',   'Conservación de largo plazo'),
    ('Congelador -20 °C · Rack B',      'Congelador',      '-20 °C',   'Muestras de algas congeladas'),
    ('Refrigerador 4 °C · Estante 1',   'Refrigerador',    '4 °C',     'Aguas y muestras en proceso'),
    ('Cámara de cultivo 18 °C',         'Cámara',          '18 °C',    'Microalgas vivas'),
    ('Estante temperatura ambiente',    'Estante',         'Ambiente', 'Muestras secas y sedimentos');

-- =====================================================================
-- FIN DEL NÚCLEO MVP (cubre exactamente RF01-RF06)
-- =====================================================================


-- #######################################################################
-- MEJORAS ADICIONALES (opcionales, no rompen el núcleo de arriba)
-- Las de "bajo costo, alto valor" (historial de estados, condición de
-- recepción, control de calidad, CHECK de justificación) ya quedaron
-- incorporadas arriba. Lo que sigue es lo que se dejó fuera del núcleo
-- por ser de mayor esfuerzo relativo a las 12 semanas / 6 sprints; no es
-- obligatorio para cumplir los RF/RNF, pero se documenta como trabajo
-- futuro defendible ante el profesor.
-- #######################################################################

-- ---------------------------------------------------------------------
-- MEJORA A: Clientes y tipos de muestra (si necesitan más que el campo
-- "procedencia" de texto libre, por ejemplo para reportes agrupados por
-- cliente o filtros por matriz en el listado de muestras).
--
-- OJO: "clientes" NO es lo mismo que "centros" del núcleo. "centros" es
-- el laboratorio que USA el sistema (multi-tenant); "clientes" sería
-- quién le ENCARGA la muestra a ese laboratorio (un hospital, una
-- empresa, un particular). Son dos conceptos independientes.
-- ---------------------------------------------------------------------
-- CREATE TABLE clientes (
--     id_cliente   INT AUTO_INCREMENT PRIMARY KEY,
--     nombre_razon VARCHAR(150) NOT NULL,
--     rut          VARCHAR(15) UNIQUE,
--     tipo_cliente ENUM('Clinico','Acuicola','Ambiental','Universitario','Otro') NOT NULL,
--     telefono     VARCHAR(30),
--     email        VARCHAR(150),
--     activo       BOOLEAN NOT NULL DEFAULT TRUE
-- ) ENGINE=InnoDB;
--
-- CREATE TABLE tipos_muestra (
--     id_tipo_muestra INT AUTO_INCREMENT PRIMARY KEY,
--     nombre          VARCHAR(80) NOT NULL UNIQUE,   -- Agua, Sangre, Suelo, Alimento...
--     descripcion     VARCHAR(255)
-- ) ENGINE=InnoDB;
--
-- -- luego: ALTER TABLE muestras ADD COLUMN id_cliente INT, ADD COLUMN id_tipo_muestra INT,
-- -- y agregar sus FOREIGN KEY correspondientes.

-- ---------------------------------------------------------------------
-- MEJORA B: Rangos de referencia por tipo de muestra (matriz).
-- Útil si el mismo análisis (ej. pH) necesita rangos distintos según si
-- la muestra es agua, suelo o sangre. Reemplaza valor_min_normal /
-- valor_max_normal de analisis_catalogo por una tabla aparte.
-- Requiere la tabla tipos_muestra de la Mejora A.
-- ---------------------------------------------------------------------
-- CREATE TABLE rangos_referencia (
--     id_rango        INT AUTO_INCREMENT PRIMARY KEY,
--     id_analisis     INT NOT NULL,
--     id_tipo_muestra INT NOT NULL,
--     valor_min       DECIMAL(12,4) NOT NULL,
--     valor_max       DECIMAL(12,4) NOT NULL,
--     CONSTRAINT fk_rango_analisis     FOREIGN KEY (id_analisis) REFERENCES analisis_catalogo(id_analisis),
--     CONSTRAINT fk_rango_tipo_muestra FOREIGN KEY (id_tipo_muestra) REFERENCES tipos_muestra(id_tipo_muestra),
--     CONSTRAINT uq_rango UNIQUE (id_analisis, id_tipo_muestra)
-- ) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- MEJORA C: Auditoría genérica para TODAS las tablas (no solo el estado
-- de la muestra, que ya cubre muestra_historial_estado del núcleo).
-- Útil si en la sustentación les piden trazabilidad también sobre
-- cambios en usuarios, catálogo de análisis, etc. Se puede llenar desde
-- triggers de MySQL o desde la capa de servicio en Spring Boot.
-- ---------------------------------------------------------------------
-- CREATE TABLE auditoria (
--     id_auditoria         BIGINT AUTO_INCREMENT PRIMARY KEY,
--     tabla_afectada       VARCHAR(50) NOT NULL,
--     id_registro_afectado INT NOT NULL,
--     accion               ENUM('INSERT','UPDATE','DELETE') NOT NULL,
--     id_usuario           INT NULL,
--     fecha_hora           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
--     valores_anteriores   JSON NULL,
--     valores_nuevos       JSON NULL,
--     CONSTRAINT fk_auditoria_usuario FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario),
--     INDEX idx_auditoria_tabla_registro (tabla_afectada, id_registro_afectado)
-- ) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- MEJORA D: Cadena de custodia formal y ficha de equipos con calibración.
-- El núcleo ya deja "instrumento_utilizado" como texto libre en
-- resultados (suficiente para el MVP); esta mejora lo reemplaza por una
-- tabla real de equipos con fecha de calibración y bloqueo de uso si el
-- equipo está vencido -- práctica estándar en LIMS (ver fuentes) pero
-- probablemente fuera de alcance de un MVP de 12 semanas.
-- ---------------------------------------------------------------------
-- CREATE TABLE equipos (
--     id_equipo           INT AUTO_INCREMENT PRIMARY KEY,
--     nombre              VARCHAR(100) NOT NULL,
--     marca               VARCHAR(100),
--     fecha_calibracion   DATE,
--     proxima_calibracion DATE
-- ) ENGINE=InnoDB;
--
-- CREATE TABLE resultado_equipo (
--     id_resultado INT NOT NULL,
--     id_equipo    INT NOT NULL,
--     PRIMARY KEY (id_resultado, id_equipo),
--     CONSTRAINT fk_re_resultado FOREIGN KEY (id_resultado) REFERENCES resultados(id_resultado),
--     CONSTRAINT fk_re_equipo    FOREIGN KEY (id_equipo) REFERENCES equipos(id_equipo)
-- ) ENGINE=InnoDB;
--
-- CREATE TABLE cadena_custodia (
--     id_custodia        INT AUTO_INCREMENT PRIMARY KEY,
--     id_muestra         INT NOT NULL,
--     id_usuario_origen  INT NOT NULL,
--     id_usuario_destino INT NOT NULL,
--     fecha_transferencia DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
--     motivo             VARCHAR(255),
--     CONSTRAINT fk_cc_muestra FOREIGN KEY (id_muestra) REFERENCES muestras(id_muestra),
--     CONSTRAINT fk_cc_origen  FOREIGN KEY (id_usuario_origen) REFERENCES usuarios(id_usuario),
--     CONSTRAINT fk_cc_destino FOREIGN KEY (id_usuario_destino) REFERENCES usuarios(id_usuario)
-- ) ENGINE=InnoDB;
