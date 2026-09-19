-- =====================================================================
-- PROYECTO APT — LIMS (Sistema de Gestión de Información de Laboratorio)
-- Esquema de Base de Datos — MySQL 8.x — v3
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
--      estándar) vs. muestras de cliente, sin crear un módulo aparte.
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
-- 4. MUESTRAS  (RF01 - registro con código único y estado de workflow)
-- =====================================================================

CREATE TABLE muestras (
    id_muestra              INT AUTO_INCREMENT PRIMARY KEY,
    id_centro               INT NOT NULL,                  -- a qué laboratorio pertenece la muestra
    codigo_unico            VARCHAR(30) NOT NULL,          -- ej. LAB-2026-000123 (único DENTRO del centro)
    tipo_muestra            ENUM('Agua potable','Agua residual','Sangre','Suelo','Alimento','Otro')
                             NOT NULL DEFAULT 'Otro',
    procedencia             VARCHAR(150),                  -- quién/qué origina la muestra
    id_usuario_registro     INT NOT NULL,                  -- quién la ingresó al sistema
    fecha_toma              DATETIME NULL,                 -- cuándo se tomó la muestra
    fecha_recepcion         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    estado                  ENUM('Recibida','En analisis','Resultados ingresados',
                                   'Aprobada','Rechazada','Reportada')
                             NOT NULL DEFAULT 'Recibida',
    tipo_registro           ENUM('Muestra','Blanco','Duplicado','Estandar')
                             NOT NULL DEFAULT 'Muestra',
        -- Mejora: en todo LIMS real, junto a las muestras de cliente circulan
        -- muestras de control de calidad (blancos, duplicados, estándares)
        -- que validan al analista/instrumento. Marcarlas aquí evita crear un
        -- módulo aparte y permite excluirlas de los reportes al cliente.
    condicion_recepcion     VARCHAR(150),   -- estado físico/temperatura al recibir (cadena de custodia)
    ubicacion_almacenamiento VARCHAR(100),  -- dónde queda guardada físicamente
    fecha_vencimiento       DATE NULL,      -- vida útil de la muestra (crítico en clínico/acuícola)
    prioridad               ENUM('Baja','Media','Alta') NOT NULL DEFAULT 'Media',
    observaciones           TEXT,
    CONSTRAINT fk_muestra_usuario FOREIGN KEY (id_usuario_registro) REFERENCES usuarios(id_usuario),
    CONSTRAINT fk_muestra_centro  FOREIGN KEY (id_centro) REFERENCES centros(id_centro),
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
    instrumento_utilizado VARCHAR(100),           -- mejora: trazabilidad de qué equipo generó el resultado
    id_usuario_ingreso   INT NOT NULL,
    fecha_ingreso        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    observaciones        TEXT,
    CONSTRAINT fk_resultado_ma      FOREIGN KEY (id_muestra_analisis) REFERENCES muestra_analisis(id_muestra_analisis),
    CONSTRAINT fk_resultado_usuario FOREIGN KEY (id_usuario_ingreso) REFERENCES usuarios(id_usuario),
    -- Mejora: ISO 17025 exige documentar por qué un resultado no conforme
    -- fue aceptado/reportado. MySQL 8 soporta CHECK real (se valida en cada
    -- INSERT/UPDATE, no solo se acepta y se ignora como en versiones viejas).
    CONSTRAINT chk_resultado_justificacion
        CHECK (dentro_rango = TRUE OR observaciones IS NOT NULL)
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
-- DATOS INICIALES MÍNIMOS (seed)
-- =====================================================================

-- Editar con los datos reales del laboratorio antes de usar en producción.
INSERT INTO centros (nombre_centro, tipo_centro, direccion, telefono, email) VALUES
    ('Laboratorio Central', 'Otro', 'Por definir', NULL, NULL);

INSERT INTO roles (nombre_rol, descripcion) VALUES
    ('Administrador', 'Gestiona usuarios, roles y catálogo de análisis'),
    ('Supervisor',     'Aprueba o rechaza resultados ingresados por analistas'),
    ('Analista',       'Registra muestras e ingresa resultados de análisis');

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
