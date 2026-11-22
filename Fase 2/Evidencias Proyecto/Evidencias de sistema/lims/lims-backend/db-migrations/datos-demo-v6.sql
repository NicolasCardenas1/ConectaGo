-- =====================================================================
-- DATOS DE DEMOSTRACIÓN v6 — equipo de toma y calibraciones anteriores
-- =====================================================================
-- Completa los datos de datos-demo-v5.sql para mostrar lo nuevo de la v6:
--   * cada muestra de la demo (códigos H-... y D-...) queda con el equipo con que se
--     tomó en terreno, según su tipo (así el chatbot puede responder "¿con qué equipo
--     se tomó?");
--   * el fluorómetro EQ-002 y el pHmetro EQ-001 quedan con una calibración anterior en
--     su historial (para mostrar que las calibraciones no se sobrescriben).
--
-- Requiere la v6 (base nueva o migración v6) y datos-demo-v5.sql. Es idempotente.
-- NO ejecutar en la base compartida (Aiven) sin acuerdo del equipo.
--
--   docker cp db-migrations\datos-demo-v6.sql lims-mysql:/tmp/m.sql
--   docker exec lims-mysql sh -c "mysql --default-character-set=utf8mb4 -ulims_user -plims_pass lims_db < /tmp/m.sql"
-- =====================================================================

SET NAMES utf8mb4;

-- Equipo de toma según el tipo de muestra (solo muestras de la demo y sin equipo asignado)
UPDATE muestras SET id_equipo_toma = (SELECT id_equipo FROM equipos WHERE codigo = 'EQ-009')
WHERE id_equipo_toma IS NULL AND tipo_muestra = 'Macroalga'
  AND (codigo_unico LIKE 'H-%' OR codigo_unico LIKE 'D-%');

UPDATE muestras SET id_equipo_toma = (SELECT id_equipo FROM equipos WHERE codigo = 'EQ-006')
WHERE id_equipo_toma IS NULL AND tipo_muestra IN ('Microalga', 'Agua de mar', 'Agua dulce')
  AND (codigo_unico LIKE 'H-%' OR codigo_unico LIKE 'D-%');

UPDATE muestras SET id_equipo_toma = (SELECT id_equipo FROM equipos WHERE codigo = 'EQ-008')
WHERE id_equipo_toma IS NULL AND tipo_muestra = 'Sedimento'
  AND (codigo_unico LIKE 'H-%' OR codigo_unico LIKE 'D-%');

-- Calibraciones anteriores (una por equipo, solo si todavía no tiene historial previo)
INSERT INTO equipo_calibraciones (id_equipo, fecha_calibracion, fecha_vencimiento, realizada_por, certificado, observaciones)
SELECT e.id_equipo, DATE_SUB(e.fecha_ultima_calibracion, INTERVAL 6 MONTH), e.fecha_ultima_calibracion,
       'Metrología Austral Ltda.', 'CAL-2026-0142', 'Calibración anterior (demo)'
FROM equipos e
WHERE e.codigo IN ('EQ-001', 'EQ-002') AND e.fecha_ultima_calibracion IS NOT NULL
  AND (SELECT COUNT(*) FROM equipo_calibraciones c WHERE c.id_equipo = e.id_equipo) < 2;
