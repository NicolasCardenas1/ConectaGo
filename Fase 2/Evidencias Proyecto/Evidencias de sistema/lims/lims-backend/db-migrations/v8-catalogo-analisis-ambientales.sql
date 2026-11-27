-- =====================================================================
-- v8 — Catálogo de análisis ambientales
--
-- 1) Desactiva "Hemograma Completo" (análisis clínico, fuera del dominio del laboratorio).
--    NO se borra: si alguna muestra antigua lo tiene, conserva su historia; solo deja de
--    aparecer en la lista "Agregar análisis".
-- 2) Agrega 10 análisis ambientales (nutrientes, calidad de agua, microbiología y
--    sedimento/macroalga). Los rangos normales son REFERENCIALES para aguas costeras y
--    continentales del sur de Chile: deben validarse con el investigador responsable.
-- 3) Agrega 6 equipos de laboratorio para medirlos (EQ-010 a EQ-015), con su calibración.
--
-- Se puede ejecutar más de una vez: no duplica análisis existentes.
-- Ejecutar con (desde lims-backend):
--   docker cp db-migrations\v8-catalogo-analisis-ambientales.sql lims-mysql:/tmp/m.sql
--   docker exec lims-mysql sh -c "mysql --default-character-set=utf8mb4 -ulims_user -plims_pass lims_db < /tmp/m.sql"
-- =====================================================================

UPDATE analisis_catalogo SET activo = FALSE WHERE nombre = 'Hemograma Completo';

INSERT INTO analisis_catalogo (nombre, descripcion, unidad_medida, metodo_referencia, tiempo_estimado_hrs,
                               valor_min_normal, valor_max_normal, activo)
SELECT * FROM (
    SELECT 'Nitrato' AS nombre, 'Nutriente: nitrógeno como nitrato (N-NO3)' AS descripcion, 'mg/L' AS unidad,
           'Espectrofotometría, reducción con cadmio (SM 4500-NO3 E)' AS metodo, 3.00 AS horas, 0.0000 AS minimo, 0.5000 AS maximo, TRUE AS activo
    UNION ALL SELECT 'Fosfato', 'Nutriente: fósforo como ortofosfato (P-PO4)', 'mg/L',
           'Espectrofotometría, ácido ascórbico (SM 4500-P E)', 2.00, 0.0000, 0.1000, TRUE
    UNION ALL SELECT 'Amonio', 'Nutriente: nitrógeno amoniacal (N-NH4)', 'mg/L',
           'Espectrofotometría, fenato (SM 4500-NH3 F)', 2.00, 0.0000, 0.2000, TRUE
    UNION ALL SELECT 'Silicato', 'Nutriente para diatomeas (Si-SiO4)', 'mg/L',
           'Espectrofotometría, molibdosilicato (SM 4500-SiO2 C)', 2.00, 0.0000, 3.0000, TRUE
    UNION ALL SELECT 'Turbidez', 'Transparencia de la columna de agua', 'NTU',
           'Nefelometría (SM 2130 B)', 0.50, 0.0000, 5.0000, TRUE
    UNION ALL SELECT 'Conductividad eléctrica', 'Sales disueltas en agua dulce (lagos y ríos)', 'µS/cm',
           'Conductimetría (SM 2510 B)', 0.50, 20.0000, 1500.0000, TRUE
    UNION ALL SELECT 'Sólidos suspendidos totales', 'Material particulado en suspensión', 'mg/L',
           'Gravimetría, filtración y secado a 103-105 °C (SM 2540 D)', 24.00, 0.0000, 30.0000, TRUE
    UNION ALL SELECT 'Coliformes fecales', 'Indicador de contaminación fecal (NCh 1333, aguas recreativas)', 'NMP/100 mL',
           'Fermentación en tubos múltiples (SM 9221 E)', 48.00, 0.0000, 1000.0000, TRUE
    UNION ALL SELECT 'Materia orgánica en sedimento', 'Contenido orgánico del sedimento', '%',
           'Pérdida por ignición a 550 °C', 24.00, 0.0000, 10.0000, TRUE
    UNION ALL SELECT 'Biomasa de macroalgas', 'Peso húmedo por unidad de área (cuadrante 0,25 m²)', 'g/m²',
           'Gravimetría, peso húmedo escurrido', 2.00, 500.0000, 5000.0000, TRUE
) AS nuevos
WHERE NOT EXISTS (SELECT 1 FROM analisis_catalogo a WHERE a.nombre = nuevos.nombre);

-- ---------------------------------------------------------------------
-- 3) Equipos de laboratorio para los análisis nuevos (con su calibración inicial).
--    Nitrato, fosfato, amonio y silicato se miden con el espectrofotómetro EQ-004 (ya existe).
--    EQ-005 (balanza analítica) se deja vencida a propósito: la demo muestra que no se puede usar.
--    EQ-010 queda "por vencer" para que el panel muestre esa alerta.
-- ---------------------------------------------------------------------
INSERT INTO equipos (codigo, nombre, marca, modelo, numero_serie, uso, fecha_ultima_calibracion,
                     fecha_proxima_calibracion, activo)
SELECT * FROM (
    SELECT 'EQ-010' AS codigo, 'Turbidímetro' AS nombre, 'Hach' AS marca, '2100Q' AS modelo, '2100Q-1706582' AS serie,
           'Laboratorio' AS uso, DATE '2025-12-15' AS ultima, DATE '2026-12-15' AS proxima, TRUE AS activo
    UNION ALL SELECT 'EQ-011', 'Conductímetro de sobremesa', 'Hanna Instruments', 'HI5321', 'HI5321-0912',
           'Laboratorio', DATE '2026-08-20', DATE '2027-08-20', TRUE
    UNION ALL SELECT 'EQ-012', 'Estufa de secado 105 °C', 'Memmert', 'UN55', 'UN55-B417.0233',
           'Laboratorio', DATE '2026-06-10', DATE '2027-06-10', TRUE
    UNION ALL SELECT 'EQ-013', 'Balanza de precisión', 'Ohaus', 'Pioneer PX3202', 'PX3202-B9A41',
           'Laboratorio', DATE '2026-10-05', DATE '2027-04-05', TRUE
    UNION ALL SELECT 'EQ-014', 'Mufla 550 °C', 'Nabertherm', 'L 9/11', 'NB-L911-30187',
           'Laboratorio', DATE '2026-07-01', DATE '2027-07-01', TRUE
    UNION ALL SELECT 'EQ-015', 'Incubadora 44,5 °C (coliformes)', 'Memmert', 'IN30', 'IN30-G613.1150',
           'Laboratorio', DATE '2026-09-15', DATE '2027-03-15', TRUE
) AS nuevos
WHERE NOT EXISTS (SELECT 1 FROM equipos e WHERE e.codigo = nuevos.codigo);

-- Primera fila del historial de calibraciones de cada equipo nuevo (igual que hizo la v6 con los existentes)
INSERT INTO equipo_calibraciones (id_equipo, fecha_calibracion, fecha_vencimiento, realizada_por, certificado, observaciones)
SELECT e.id_equipo, e.fecha_ultima_calibracion, e.fecha_proxima_calibracion, c.realizada_por, c.certificado, c.observaciones
FROM equipos e
JOIN (
    SELECT 'EQ-010' AS codigo, 'Metrología Austral Ltda.' AS realizada_por, 'CAL-2025-0981' AS certificado,
           'Verificación con patrones de formacina 0,1 / 20 / 800 NTU' AS observaciones
    UNION ALL SELECT 'EQ-011', 'Metrología Austral Ltda.', 'CAL-2026-0655', 'Patrones de conductividad 84 y 1413 µS/cm'
    UNION ALL SELECT 'EQ-012', 'Metrología Austral Ltda.', 'CAL-2026-0418', 'Mapeo térmico a 105 °C, 9 puntos'
    UNION ALL SELECT 'EQ-013', 'Metrología Austral Ltda.', 'CAL-2026-0790', 'Pesas patrón clase F1, 1 g a 2 kg'
    UNION ALL SELECT 'EQ-014', 'Metrología Austral Ltda.', 'CAL-2026-0502', 'Verificación de temperatura a 550 °C con termopar tipo K'
    UNION ALL SELECT 'EQ-015', 'Metrología Austral Ltda.', 'CAL-2026-0714', 'Mapeo térmico a 44,5 ± 0,2 °C'
) AS c ON c.codigo = e.codigo
WHERE NOT EXISTS (SELECT 1 FROM equipo_calibraciones x WHERE x.id_equipo = e.id_equipo);
