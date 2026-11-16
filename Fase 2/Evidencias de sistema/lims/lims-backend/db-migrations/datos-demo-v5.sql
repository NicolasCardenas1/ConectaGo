-- =====================================================================
-- DATOS DE DEMOSTRACIÓN v5 — historial ambiental (solo para demo / pruebas)
-- =====================================================================
-- Carga 8 meses de muestras ya informadas en 4 sitios de muestreo, con
-- clorofila-a, temperatura, salinidad, pH y oxígeno disuelto. Sirve para
-- mostrar el mapa, los gráficos de tendencia del panel de inicio y la
-- detección de valores atípicos (necesita historial por sitio).
-- También deja 2 muestras recibidas (D-2026-0001/0002) listas para ingresar
-- resultados en vivo durante la presentación.
--
-- Requiere la migración v5. Es idempotente: si ya se cargó, no hace nada.
-- NO ejecutar en la base compartida (Aiven) sin acuerdo del equipo.
--
--   docker exec -i lims-mysql mysql -u lims_user -plims_pass lims_db < db-migrations/datos-demo-v5.sql
--
-- Usuarios de demostración (cambiar o desactivar fuera de la demo):
--   admin / admin123 (Administrador, solo si no existe) · supervisor.demo / super123 · analista.demo / analista123
-- =====================================================================

SET NAMES utf8mb4;          -- textos con tildes
SET time_zone = '-03:00';   -- NOW() en hora de Chile (el contenedor MySQL usa UTC; el backend, la hora local)

DROP PROCEDURE IF EXISTS cargar_demo_v5;
DELIMITER //
CREATE PROCEDURE cargar_demo_v5()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM muestras WHERE codigo_unico = 'H-2026-0001') THEN

  -- Usuarios de demostración
  INSERT IGNORE INTO usuarios (id_centro, nombre, apellido, email, username, password_hash, id_rol, activo)
  SELECT 1, 'Administrador', 'LIMS', 'admin@lims.cl', 'admin',
         '$2a$10$YJWdj3NEuU7p0m9K2tiGOuz9NinSi0TMCswlRulKcEXPkhNJmFdPm', id_rol, TRUE FROM roles WHERE nombre_rol = 'Administrador';
  INSERT IGNORE INTO usuarios (id_centro, nombre, apellido, email, username, password_hash, id_rol, activo)
  SELECT 1, 'Sofía', 'Mansilla', 'supervisor.demo@lims.cl', 'supervisor.demo',
         '$2a$10$J0OU9kw38z9IZDrBotpRtOyqErCt9uSWFBZRyt9YoCr6KJHiyTXmq', id_rol, TRUE FROM roles WHERE nombre_rol = 'Supervisor';
  INSERT IGNORE INTO usuarios (id_centro, nombre, apellido, email, username, password_hash, id_rol, activo)
  SELECT 1, 'Tomás', 'Barrientos', 'analista.demo@lims.cl', 'analista.demo',
         '$2a$10$bMGUgIDN/7sWr8ODCVX1i.8n3O0N.YVTSJArcNaNPlFU6ocK8c8dW', id_rol, TRUE FROM roles WHERE nombre_rol = 'Analista';
  SET @sup = (SELECT id_usuario FROM usuarios WHERE username = 'supervisor.demo');
  SET @an  = (SELECT id_usuario FROM usuarios WHERE username = 'analista.demo');

  -- Investigadora
  INSERT INTO investigadores (nombre, apellido, institucion, email, telefono, proyecto)
  SELECT 'Paula', 'Rivas', 'Universidad de Los Lagos', 'p.rivas@ulagos.cl', '+56 65 232 2000',
         'Monitoreo de praderas de macroalgas y calidad de agua en el seno de Reloncaví'
  WHERE NOT EXISTS (SELECT 1 FROM investigadores WHERE email = 'p.rivas@ulagos.cl');
  SET @inv = (SELECT id_investigador FROM investigadores WHERE email = 'p.rivas@ulagos.cl' LIMIT 1);

  -- Parámetros ambientales del catálogo
  INSERT INTO analisis_catalogo (nombre, descripcion, unidad_medida, metodo_referencia, tiempo_estimado_hrs, valor_min_normal, valor_max_normal, activo)
  SELECT 'Clorofila-a', 'Biomasa fitoplanctónica', 'µg/L', 'Fluorometría in vitro (EPA 445.0)', 4, 0.5, 10, TRUE
  WHERE NOT EXISTS (SELECT 1 FROM analisis_catalogo WHERE nombre = 'Clorofila-a');
  SET @a_clor = (SELECT id_analisis FROM analisis_catalogo WHERE nombre = 'Clorofila-a' LIMIT 1);
  INSERT INTO analisis_catalogo (nombre, descripcion, unidad_medida, metodo_referencia, tiempo_estimado_hrs, valor_min_normal, valor_max_normal, activo)
  SELECT 'Temperatura del agua', 'Temperatura al momento del muestreo', '°C', 'Sonda multiparámetro in situ', 0.5, 6, 18, TRUE
  WHERE NOT EXISTS (SELECT 1 FROM analisis_catalogo WHERE nombre = 'Temperatura del agua');
  SET @a_temp = (SELECT id_analisis FROM analisis_catalogo WHERE nombre = 'Temperatura del agua' LIMIT 1);
  INSERT INTO analisis_catalogo (nombre, descripcion, unidad_medida, metodo_referencia, tiempo_estimado_hrs, valor_min_normal, valor_max_normal, activo)
  SELECT 'Salinidad', 'Aguas costeras y estuarinas', 'PSU', 'Conductimetría (sonda multiparámetro)', 0.5, 20, 34, TRUE
  WHERE NOT EXISTS (SELECT 1 FROM analisis_catalogo WHERE nombre = 'Salinidad');
  SET @a_sal = (SELECT id_analisis FROM analisis_catalogo WHERE nombre = 'Salinidad' LIMIT 1);
  INSERT INTO analisis_catalogo (nombre, descripcion, unidad_medida, metodo_referencia, tiempo_estimado_hrs, valor_min_normal, valor_max_normal, activo)
  SELECT 'pH', 'pH de agua de mar / estuarina', 'unid. pH', 'Potenciometría (SM 4500-H+ B)', 1, 7.5, 8.5, TRUE
  WHERE NOT EXISTS (SELECT 1 FROM analisis_catalogo WHERE nombre = 'pH');
  SET @a_ph = (SELECT id_analisis FROM analisis_catalogo WHERE nombre = 'pH' LIMIT 1);
  INSERT INTO analisis_catalogo (nombre, descripcion, unidad_medida, metodo_referencia, tiempo_estimado_hrs, valor_min_normal, valor_max_normal, activo)
  SELECT 'Oxígeno disuelto', 'Oxigenación de la columna de agua', 'mg/L', 'Sensor óptico (sonda multiparámetro)', 0.5, 6, 12, TRUE
  WHERE NOT EXISTS (SELECT 1 FROM analisis_catalogo WHERE nombre = 'Oxígeno disuelto');
  SET @a_od = (SELECT id_analisis FROM analisis_catalogo WHERE nombre = 'Oxígeno disuelto' LIMIT 1);
  SET @eq001 = (SELECT id_equipo FROM equipos WHERE codigo = 'EQ-001');
  SET @eq002 = (SELECT id_equipo FROM equipos WHERE codigo = 'EQ-002');
  SET @eq003 = (SELECT id_equipo FROM equipos WHERE codigo = 'EQ-003');
  SET @st001 = (SELECT id_sitio FROM sitios_muestreo WHERE codigo = 'ST-001');
  SET @st002 = (SELECT id_sitio FROM sitios_muestreo WHERE codigo = 'ST-002');
  SET @st003 = (SELECT id_sitio FROM sitios_muestreo WHERE codigo = 'ST-003');
  SET @st004 = (SELECT id_sitio FROM sitios_muestreo WHERE codigo = 'ST-004');

  -- H-2026-0001 · ST-001 · Feb 2026
  INSERT INTO muestras (id_centro, codigo_unico, tipo_muestra, procedencia, id_sitio, id_investigador, id_usuario_registro, fecha_toma, fecha_recepcion, estado, prioridad, condicion_recepcion, ubicacion_almacenamiento, fecha_vencimiento)
  VALUES (1, 'H-2026-0001', 'Macroalga', 'Bahía Ilque, estación 3', @st001, @inv, @an, '2026-02-08 09:30:00', '2026-02-08 13:30:00', 'Reportada', 'Media', 'Refrigerada 4 °C, envase sellado', 'Congelador -20 °C · Rack B', '2028-02-08');
  SET @m = LAST_INSERT_ID();
  INSERT INTO muestra_historial_estado (id_muestra, estado_anterior, estado_nuevo, id_usuario, fecha_cambio, comentario) VALUES
    (@m, NULL, 'Recibida', @an, '2026-02-08 13:30:00', 'Recepción de la muestra'),
    (@m, 'Recibida', 'Resultados ingresados', @an, '2026-02-09 15:30:00', 'Todos los análisis tienen resultado'),
    (@m, 'Resultados ingresados', 'Aprobada', @sup, '2026-02-10 15:30:00', 'Todos los análisis aprobados'),
    (@m, 'Aprobada', 'Reportada', @sup, '2026-02-10 18:30:00', 'Informe emitido H-2026-0001-v1');
  INSERT INTO custodia_eventos (id_muestra, tipo_evento, entregado_por, id_usuario_recibe, ubicacion, condicion, confirmado, fecha_hora, observaciones)
    VALUES (@m, 'Recepción en laboratorio', 'Paula Rivas', @an, 'Congelador -20 °C · Rack B', 'Refrigerada 4 °C, envase sellado', TRUE, '2026-02-08 13:30:00', 'Firma al registrar la muestra');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_clor, @an, 'Completado', '2026-02-08 13:30:00', '2026-02-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 3.1, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq002), @eq002, @an, '2026-02-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-02-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_temp, @an, 'Completado', '2026-02-08 13:30:00', '2026-02-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 11.28, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-02-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-02-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_sal, @an, 'Completado', '2026-02-08 13:30:00', '2026-02-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 31.82, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-02-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-02-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_ph, @an, 'Completado', '2026-02-08 13:30:00', '2026-02-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 8.04, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq001), @eq001, @an, '2026-02-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-02-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_od, @an, 'Completado', '2026-02-08 13:30:00', '2026-02-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 8.66, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-02-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-02-10 15:30:00');
  INSERT INTO reportes (id_muestra, id_usuario_generador, fecha_generacion, ruta_archivo, version) VALUES (@m, @sup, '2026-02-10 18:30:00', 'reportes/Informe_H-2026-0001-v1.pdf', 1);

  -- H-2026-0002 · ST-001 · Mar 2026
  INSERT INTO muestras (id_centro, codigo_unico, tipo_muestra, procedencia, id_sitio, id_investigador, id_usuario_registro, fecha_toma, fecha_recepcion, estado, prioridad, condicion_recepcion, ubicacion_almacenamiento, fecha_vencimiento)
  VALUES (1, 'H-2026-0002', 'Macroalga', 'Bahía Ilque, estación 3', @st001, @inv, @an, '2026-03-08 09:30:00', '2026-03-08 13:30:00', 'Reportada', 'Media', 'Refrigerada 4 °C, envase sellado', 'Congelador -20 °C · Rack B', '2028-03-07');
  SET @m = LAST_INSERT_ID();
  INSERT INTO muestra_historial_estado (id_muestra, estado_anterior, estado_nuevo, id_usuario, fecha_cambio, comentario) VALUES
    (@m, NULL, 'Recibida', @an, '2026-03-08 13:30:00', 'Recepción de la muestra'),
    (@m, 'Recibida', 'Resultados ingresados', @an, '2026-03-09 15:30:00', 'Todos los análisis tienen resultado'),
    (@m, 'Resultados ingresados', 'Aprobada', @sup, '2026-03-10 15:30:00', 'Todos los análisis aprobados'),
    (@m, 'Aprobada', 'Reportada', @sup, '2026-03-10 18:30:00', 'Informe emitido H-2026-0002-v1');
  INSERT INTO custodia_eventos (id_muestra, tipo_evento, entregado_por, id_usuario_recibe, ubicacion, condicion, confirmado, fecha_hora, observaciones)
    VALUES (@m, 'Recepción en laboratorio', 'Paula Rivas', @an, 'Congelador -20 °C · Rack B', 'Refrigerada 4 °C, envase sellado', TRUE, '2026-03-08 13:30:00', 'Firma al registrar la muestra');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_clor, @an, 'Completado', '2026-03-08 13:30:00', '2026-03-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 3.22, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq002), @eq002, @an, '2026-03-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-03-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_temp, @an, 'Completado', '2026-03-08 13:30:00', '2026-03-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 11.27, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-03-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-03-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_sal, @an, 'Completado', '2026-03-08 13:30:00', '2026-03-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 31.34, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-03-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-03-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_ph, @an, 'Completado', '2026-03-08 13:30:00', '2026-03-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 8.09, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq001), @eq001, @an, '2026-03-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-03-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_od, @an, 'Completado', '2026-03-08 13:30:00', '2026-03-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 8.67, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-03-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-03-10 15:30:00');
  INSERT INTO reportes (id_muestra, id_usuario_generador, fecha_generacion, ruta_archivo, version) VALUES (@m, @sup, '2026-03-10 18:30:00', 'reportes/Informe_H-2026-0002-v1.pdf', 1);

  -- H-2026-0003 · ST-001 · Apr 2026
  INSERT INTO muestras (id_centro, codigo_unico, tipo_muestra, procedencia, id_sitio, id_investigador, id_usuario_registro, fecha_toma, fecha_recepcion, estado, prioridad, condicion_recepcion, ubicacion_almacenamiento, fecha_vencimiento)
  VALUES (1, 'H-2026-0003', 'Macroalga', 'Bahía Ilque, estación 3', @st001, @inv, @an, '2026-04-08 09:30:00', '2026-04-08 13:30:00', 'Reportada', 'Media', 'Refrigerada 4 °C, envase sellado', 'Congelador -20 °C · Rack B', '2028-04-07');
  SET @m = LAST_INSERT_ID();
  INSERT INTO muestra_historial_estado (id_muestra, estado_anterior, estado_nuevo, id_usuario, fecha_cambio, comentario) VALUES
    (@m, NULL, 'Recibida', @an, '2026-04-08 13:30:00', 'Recepción de la muestra'),
    (@m, 'Recibida', 'Resultados ingresados', @an, '2026-04-09 15:30:00', 'Todos los análisis tienen resultado'),
    (@m, 'Resultados ingresados', 'Aprobada', @sup, '2026-04-10 15:30:00', 'Todos los análisis aprobados'),
    (@m, 'Aprobada', 'Reportada', @sup, '2026-04-10 18:30:00', 'Informe emitido H-2026-0003-v1');
  INSERT INTO custodia_eventos (id_muestra, tipo_evento, entregado_por, id_usuario_recibe, ubicacion, condicion, confirmado, fecha_hora, observaciones)
    VALUES (@m, 'Recepción en laboratorio', 'Paula Rivas', @an, 'Congelador -20 °C · Rack B', 'Refrigerada 4 °C, envase sellado', TRUE, '2026-04-08 13:30:00', 'Firma al registrar la muestra');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_clor, @an, 'Completado', '2026-04-08 13:30:00', '2026-04-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 3.99, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq002), @eq002, @an, '2026-04-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-04-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_temp, @an, 'Completado', '2026-04-08 13:30:00', '2026-04-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 10.81, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-04-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-04-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_sal, @an, 'Completado', '2026-04-08 13:30:00', '2026-04-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 31.15, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-04-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-04-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_ph, @an, 'Completado', '2026-04-08 13:30:00', '2026-04-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 8.05, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq001), @eq001, @an, '2026-04-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-04-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_od, @an, 'Completado', '2026-04-08 13:30:00', '2026-04-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 9.15, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-04-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-04-10 15:30:00');
  INSERT INTO reportes (id_muestra, id_usuario_generador, fecha_generacion, ruta_archivo, version) VALUES (@m, @sup, '2026-04-10 18:30:00', 'reportes/Informe_H-2026-0003-v1.pdf', 1);

  -- H-2026-0004 · ST-001 · May 2026
  INSERT INTO muestras (id_centro, codigo_unico, tipo_muestra, procedencia, id_sitio, id_investigador, id_usuario_registro, fecha_toma, fecha_recepcion, estado, prioridad, condicion_recepcion, ubicacion_almacenamiento, fecha_vencimiento)
  VALUES (1, 'H-2026-0004', 'Macroalga', 'Bahía Ilque, estación 3', @st001, @inv, @an, '2026-05-08 09:30:00', '2026-05-08 13:30:00', 'Reportada', 'Media', 'Refrigerada 4 °C, envase sellado', 'Congelador -20 °C · Rack B', '2028-05-07');
  SET @m = LAST_INSERT_ID();
  INSERT INTO muestra_historial_estado (id_muestra, estado_anterior, estado_nuevo, id_usuario, fecha_cambio, comentario) VALUES
    (@m, NULL, 'Recibida', @an, '2026-05-08 13:30:00', 'Recepción de la muestra'),
    (@m, 'Recibida', 'Resultados ingresados', @an, '2026-05-09 15:30:00', 'Todos los análisis tienen resultado'),
    (@m, 'Resultados ingresados', 'Aprobada', @sup, '2026-05-10 15:30:00', 'Todos los análisis aprobados'),
    (@m, 'Aprobada', 'Reportada', @sup, '2026-05-10 18:30:00', 'Informe emitido H-2026-0004-v1');
  INSERT INTO custodia_eventos (id_muestra, tipo_evento, entregado_por, id_usuario_recibe, ubicacion, condicion, confirmado, fecha_hora, observaciones)
    VALUES (@m, 'Recepción en laboratorio', 'Paula Rivas', @an, 'Congelador -20 °C · Rack B', 'Refrigerada 4 °C, envase sellado', TRUE, '2026-05-08 13:30:00', 'Firma al registrar la muestra');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_clor, @an, 'Completado', '2026-05-08 13:30:00', '2026-05-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 3.01, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq002), @eq002, @an, '2026-05-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-05-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_temp, @an, 'Completado', '2026-05-08 13:30:00', '2026-05-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 8.86, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-05-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-05-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_sal, @an, 'Completado', '2026-05-08 13:30:00', '2026-05-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 32.33, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-05-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-05-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_ph, @an, 'Completado', '2026-05-08 13:30:00', '2026-05-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 8.12, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq001), @eq001, @an, '2026-05-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-05-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_od, @an, 'Completado', '2026-05-08 13:30:00', '2026-05-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 8.88, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-05-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-05-10 15:30:00');
  INSERT INTO reportes (id_muestra, id_usuario_generador, fecha_generacion, ruta_archivo, version) VALUES (@m, @sup, '2026-05-10 18:30:00', 'reportes/Informe_H-2026-0004-v1.pdf', 1);

  -- H-2026-0005 · ST-001 · Jun 2026
  INSERT INTO muestras (id_centro, codigo_unico, tipo_muestra, procedencia, id_sitio, id_investigador, id_usuario_registro, fecha_toma, fecha_recepcion, estado, prioridad, condicion_recepcion, ubicacion_almacenamiento, fecha_vencimiento)
  VALUES (1, 'H-2026-0005', 'Macroalga', 'Bahía Ilque, estación 3', @st001, @inv, @an, '2026-06-08 09:30:00', '2026-06-08 13:30:00', 'Reportada', 'Media', 'Refrigerada 4 °C, envase sellado', 'Congelador -20 °C · Rack B', '2028-06-07');
  SET @m = LAST_INSERT_ID();
  INSERT INTO muestra_historial_estado (id_muestra, estado_anterior, estado_nuevo, id_usuario, fecha_cambio, comentario) VALUES
    (@m, NULL, 'Recibida', @an, '2026-06-08 13:30:00', 'Recepción de la muestra'),
    (@m, 'Recibida', 'Resultados ingresados', @an, '2026-06-09 15:30:00', 'Todos los análisis tienen resultado'),
    (@m, 'Resultados ingresados', 'Aprobada', @sup, '2026-06-10 15:30:00', 'Todos los análisis aprobados'),
    (@m, 'Aprobada', 'Reportada', @sup, '2026-06-10 18:30:00', 'Informe emitido H-2026-0005-v1');
  INSERT INTO custodia_eventos (id_muestra, tipo_evento, entregado_por, id_usuario_recibe, ubicacion, condicion, confirmado, fecha_hora, observaciones)
    VALUES (@m, 'Recepción en laboratorio', 'Paula Rivas', @an, 'Congelador -20 °C · Rack B', 'Refrigerada 4 °C, envase sellado', TRUE, '2026-06-08 13:30:00', 'Firma al registrar la muestra');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_clor, @an, 'Completado', '2026-06-08 13:30:00', '2026-06-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 4.2, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq002), @eq002, @an, '2026-06-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-06-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_temp, @an, 'Completado', '2026-06-08 13:30:00', '2026-06-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 11.14, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-06-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-06-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_sal, @an, 'Completado', '2026-06-08 13:30:00', '2026-06-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 31.36, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-06-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-06-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_ph, @an, 'Completado', '2026-06-08 13:30:00', '2026-06-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 8.0, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq001), @eq001, @an, '2026-06-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-06-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_od, @an, 'Completado', '2026-06-08 13:30:00', '2026-06-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 8.86, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-06-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-06-10 15:30:00');
  INSERT INTO reportes (id_muestra, id_usuario_generador, fecha_generacion, ruta_archivo, version) VALUES (@m, @sup, '2026-06-10 18:30:00', 'reportes/Informe_H-2026-0005-v1.pdf', 1);

  -- H-2026-0006 · ST-001 · Jul 2026
  INSERT INTO muestras (id_centro, codigo_unico, tipo_muestra, procedencia, id_sitio, id_investigador, id_usuario_registro, fecha_toma, fecha_recepcion, estado, prioridad, condicion_recepcion, ubicacion_almacenamiento, fecha_vencimiento)
  VALUES (1, 'H-2026-0006', 'Macroalga', 'Bahía Ilque, estación 3', @st001, @inv, @an, '2026-07-08 09:30:00', '2026-07-08 13:30:00', 'Reportada', 'Media', 'Refrigerada 4 °C, envase sellado', 'Congelador -20 °C · Rack B', '2028-07-07');
  SET @m = LAST_INSERT_ID();
  INSERT INTO muestra_historial_estado (id_muestra, estado_anterior, estado_nuevo, id_usuario, fecha_cambio, comentario) VALUES
    (@m, NULL, 'Recibida', @an, '2026-07-08 13:30:00', 'Recepción de la muestra'),
    (@m, 'Recibida', 'Resultados ingresados', @an, '2026-07-09 15:30:00', 'Todos los análisis tienen resultado'),
    (@m, 'Resultados ingresados', 'Aprobada', @sup, '2026-07-10 15:30:00', 'Todos los análisis aprobados'),
    (@m, 'Aprobada', 'Reportada', @sup, '2026-07-10 18:30:00', 'Informe emitido H-2026-0006-v1');
  INSERT INTO custodia_eventos (id_muestra, tipo_evento, entregado_por, id_usuario_recibe, ubicacion, condicion, confirmado, fecha_hora, observaciones)
    VALUES (@m, 'Recepción en laboratorio', 'Paula Rivas', @an, 'Congelador -20 °C · Rack B', 'Refrigerada 4 °C, envase sellado', TRUE, '2026-07-08 13:30:00', 'Firma al registrar la muestra');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_clor, @an, 'Completado', '2026-07-08 13:30:00', '2026-07-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 3.9, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq002), @eq002, @an, '2026-07-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-07-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_temp, @an, 'Completado', '2026-07-08 13:30:00', '2026-07-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 9.7, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-07-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-07-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_sal, @an, 'Completado', '2026-07-08 13:30:00', '2026-07-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 31.29, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-07-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-07-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_ph, @an, 'Completado', '2026-07-08 13:30:00', '2026-07-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 8.03, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq001), @eq001, @an, '2026-07-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-07-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_od, @an, 'Completado', '2026-07-08 13:30:00', '2026-07-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 8.34, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-07-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-07-10 15:30:00');
  INSERT INTO reportes (id_muestra, id_usuario_generador, fecha_generacion, ruta_archivo, version) VALUES (@m, @sup, '2026-07-10 18:30:00', 'reportes/Informe_H-2026-0006-v1.pdf', 1);

  -- H-2026-0007 · ST-001 · Aug 2026
  INSERT INTO muestras (id_centro, codigo_unico, tipo_muestra, procedencia, id_sitio, id_investigador, id_usuario_registro, fecha_toma, fecha_recepcion, estado, prioridad, condicion_recepcion, ubicacion_almacenamiento, fecha_vencimiento)
  VALUES (1, 'H-2026-0007', 'Macroalga', 'Bahía Ilque, estación 3', @st001, @inv, @an, '2026-08-08 09:30:00', '2026-08-08 13:30:00', 'Reportada', 'Media', 'Refrigerada 4 °C, envase sellado', 'Congelador -20 °C · Rack B', '2028-08-07');
  SET @m = LAST_INSERT_ID();
  INSERT INTO muestra_historial_estado (id_muestra, estado_anterior, estado_nuevo, id_usuario, fecha_cambio, comentario) VALUES
    (@m, NULL, 'Recibida', @an, '2026-08-08 13:30:00', 'Recepción de la muestra'),
    (@m, 'Recibida', 'Resultados ingresados', @an, '2026-08-09 15:30:00', 'Todos los análisis tienen resultado'),
    (@m, 'Resultados ingresados', 'Aprobada', @sup, '2026-08-10 15:30:00', 'Todos los análisis aprobados'),
    (@m, 'Aprobada', 'Reportada', @sup, '2026-08-10 18:30:00', 'Informe emitido H-2026-0007-v1');
  INSERT INTO custodia_eventos (id_muestra, tipo_evento, entregado_por, id_usuario_recibe, ubicacion, condicion, confirmado, fecha_hora, observaciones)
    VALUES (@m, 'Recepción en laboratorio', 'Paula Rivas', @an, 'Congelador -20 °C · Rack B', 'Refrigerada 4 °C, envase sellado', TRUE, '2026-08-08 13:30:00', 'Firma al registrar la muestra');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_clor, @an, 'Completado', '2026-08-08 13:30:00', '2026-08-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 5.03, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq002), @eq002, @an, '2026-08-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-08-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_temp, @an, 'Completado', '2026-08-08 13:30:00', '2026-08-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 12.55, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-08-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-08-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_sal, @an, 'Completado', '2026-08-08 13:30:00', '2026-08-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 30.18, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-08-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-08-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_ph, @an, 'Completado', '2026-08-08 13:30:00', '2026-08-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 8.07, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq001), @eq001, @an, '2026-08-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-08-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_od, @an, 'Completado', '2026-08-08 13:30:00', '2026-08-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 9.06, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-08-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-08-10 15:30:00');
  INSERT INTO reportes (id_muestra, id_usuario_generador, fecha_generacion, ruta_archivo, version) VALUES (@m, @sup, '2026-08-10 18:30:00', 'reportes/Informe_H-2026-0007-v1.pdf', 1);

  -- H-2026-0008 · ST-001 · Sep 2026
  INSERT INTO muestras (id_centro, codigo_unico, tipo_muestra, procedencia, id_sitio, id_investigador, id_usuario_registro, fecha_toma, fecha_recepcion, estado, prioridad, condicion_recepcion, ubicacion_almacenamiento, fecha_vencimiento)
  VALUES (1, 'H-2026-0008', 'Macroalga', 'Bahía Ilque, estación 3', @st001, @inv, @an, '2026-09-08 09:30:00', '2026-09-08 13:30:00', 'Reportada', 'Media', 'Refrigerada 4 °C, envase sellado', 'Congelador -20 °C · Rack B', '2028-09-07');
  SET @m = LAST_INSERT_ID();
  INSERT INTO muestra_historial_estado (id_muestra, estado_anterior, estado_nuevo, id_usuario, fecha_cambio, comentario) VALUES
    (@m, NULL, 'Recibida', @an, '2026-09-08 13:30:00', 'Recepción de la muestra'),
    (@m, 'Recibida', 'Resultados ingresados', @an, '2026-09-09 15:30:00', 'Todos los análisis tienen resultado'),
    (@m, 'Resultados ingresados', 'Aprobada', @sup, '2026-09-10 15:30:00', 'Todos los análisis aprobados'),
    (@m, 'Aprobada', 'Reportada', @sup, '2026-09-10 18:30:00', 'Informe emitido H-2026-0008-v1');
  INSERT INTO custodia_eventos (id_muestra, tipo_evento, entregado_por, id_usuario_recibe, ubicacion, condicion, confirmado, fecha_hora, observaciones)
    VALUES (@m, 'Recepción en laboratorio', 'Paula Rivas', @an, 'Congelador -20 °C · Rack B', 'Refrigerada 4 °C, envase sellado', TRUE, '2026-09-08 13:30:00', 'Firma al registrar la muestra');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_clor, @an, 'Completado', '2026-09-08 13:30:00', '2026-09-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 4.71, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq002), @eq002, @an, '2026-09-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-09-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_temp, @an, 'Completado', '2026-09-08 13:30:00', '2026-09-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 12.28, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-09-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-09-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_sal, @an, 'Completado', '2026-09-08 13:30:00', '2026-09-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 30.67, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-09-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-09-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_ph, @an, 'Completado', '2026-09-08 13:30:00', '2026-09-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 8.09, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq001), @eq001, @an, '2026-09-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-09-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_od, @an, 'Completado', '2026-09-08 13:30:00', '2026-09-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 8.64, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-09-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-09-10 15:30:00');
  INSERT INTO reportes (id_muestra, id_usuario_generador, fecha_generacion, ruta_archivo, version) VALUES (@m, @sup, '2026-09-10 18:30:00', 'reportes/Informe_H-2026-0008-v1.pdf', 1);

  -- H-2026-0009 · ST-002 · Feb 2026
  INSERT INTO muestras (id_centro, codigo_unico, tipo_muestra, procedencia, id_sitio, id_investigador, id_usuario_registro, fecha_toma, fecha_recepcion, estado, prioridad, condicion_recepcion, ubicacion_almacenamiento, fecha_vencimiento)
  VALUES (1, 'H-2026-0009', 'Agua de mar', 'Isla Tenglo, sector norte', @st002, @inv, @an, '2026-02-08 09:30:00', '2026-02-08 13:30:00', 'Reportada', 'Media', 'Refrigerada 4 °C, envase sellado', 'Congelador -20 °C · Rack B', '2028-02-08');
  SET @m = LAST_INSERT_ID();
  INSERT INTO muestra_historial_estado (id_muestra, estado_anterior, estado_nuevo, id_usuario, fecha_cambio, comentario) VALUES
    (@m, NULL, 'Recibida', @an, '2026-02-08 13:30:00', 'Recepción de la muestra'),
    (@m, 'Recibida', 'Resultados ingresados', @an, '2026-02-09 15:30:00', 'Todos los análisis tienen resultado'),
    (@m, 'Resultados ingresados', 'Aprobada', @sup, '2026-02-10 15:30:00', 'Todos los análisis aprobados'),
    (@m, 'Aprobada', 'Reportada', @sup, '2026-02-10 18:30:00', 'Informe emitido H-2026-0009-v1');
  INSERT INTO custodia_eventos (id_muestra, tipo_evento, entregado_por, id_usuario_recibe, ubicacion, condicion, confirmado, fecha_hora, observaciones)
    VALUES (@m, 'Recepción en laboratorio', 'Paula Rivas', @an, 'Congelador -20 °C · Rack B', 'Refrigerada 4 °C, envase sellado', TRUE, '2026-02-08 13:30:00', 'Firma al registrar la muestra');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_clor, @an, 'Completado', '2026-02-08 13:30:00', '2026-02-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 6.31, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq002), @eq002, @an, '2026-02-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-02-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_temp, @an, 'Completado', '2026-02-08 13:30:00', '2026-02-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 12.48, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-02-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-02-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_sal, @an, 'Completado', '2026-02-08 13:30:00', '2026-02-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 30.03, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-02-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-02-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_ph, @an, 'Completado', '2026-02-08 13:30:00', '2026-02-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 8.1, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq001), @eq001, @an, '2026-02-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-02-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_od, @an, 'Completado', '2026-02-08 13:30:00', '2026-02-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 7.59, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-02-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-02-10 15:30:00');
  INSERT INTO reportes (id_muestra, id_usuario_generador, fecha_generacion, ruta_archivo, version) VALUES (@m, @sup, '2026-02-10 18:30:00', 'reportes/Informe_H-2026-0009-v1.pdf', 1);

  -- H-2026-0010 · ST-002 · Mar 2026
  INSERT INTO muestras (id_centro, codigo_unico, tipo_muestra, procedencia, id_sitio, id_investigador, id_usuario_registro, fecha_toma, fecha_recepcion, estado, prioridad, condicion_recepcion, ubicacion_almacenamiento, fecha_vencimiento)
  VALUES (1, 'H-2026-0010', 'Agua de mar', 'Isla Tenglo, sector norte', @st002, @inv, @an, '2026-03-08 09:30:00', '2026-03-08 13:30:00', 'Reportada', 'Media', 'Refrigerada 4 °C, envase sellado', 'Congelador -20 °C · Rack B', '2028-03-07');
  SET @m = LAST_INSERT_ID();
  INSERT INTO muestra_historial_estado (id_muestra, estado_anterior, estado_nuevo, id_usuario, fecha_cambio, comentario) VALUES
    (@m, NULL, 'Recibida', @an, '2026-03-08 13:30:00', 'Recepción de la muestra'),
    (@m, 'Recibida', 'Resultados ingresados', @an, '2026-03-09 15:30:00', 'Todos los análisis tienen resultado'),
    (@m, 'Resultados ingresados', 'Aprobada', @sup, '2026-03-10 15:30:00', 'Todos los análisis aprobados'),
    (@m, 'Aprobada', 'Reportada', @sup, '2026-03-10 18:30:00', 'Informe emitido H-2026-0010-v1');
  INSERT INTO custodia_eventos (id_muestra, tipo_evento, entregado_por, id_usuario_recibe, ubicacion, condicion, confirmado, fecha_hora, observaciones)
    VALUES (@m, 'Recepción en laboratorio', 'Paula Rivas', @an, 'Congelador -20 °C · Rack B', 'Refrigerada 4 °C, envase sellado', TRUE, '2026-03-08 13:30:00', 'Firma al registrar la muestra');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_clor, @an, 'Completado', '2026-03-08 13:30:00', '2026-03-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 6.06, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq002), @eq002, @an, '2026-03-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-03-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_temp, @an, 'Completado', '2026-03-08 13:30:00', '2026-03-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 12.45, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-03-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-03-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_sal, @an, 'Completado', '2026-03-08 13:30:00', '2026-03-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 30.53, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-03-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-03-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_ph, @an, 'Completado', '2026-03-08 13:30:00', '2026-03-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 7.88, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq001), @eq001, @an, '2026-03-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-03-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_od, @an, 'Completado', '2026-03-08 13:30:00', '2026-03-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 7.76, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-03-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-03-10 15:30:00');
  INSERT INTO reportes (id_muestra, id_usuario_generador, fecha_generacion, ruta_archivo, version) VALUES (@m, @sup, '2026-03-10 18:30:00', 'reportes/Informe_H-2026-0010-v1.pdf', 1);

  -- H-2026-0011 · ST-002 · Apr 2026
  INSERT INTO muestras (id_centro, codigo_unico, tipo_muestra, procedencia, id_sitio, id_investigador, id_usuario_registro, fecha_toma, fecha_recepcion, estado, prioridad, condicion_recepcion, ubicacion_almacenamiento, fecha_vencimiento)
  VALUES (1, 'H-2026-0011', 'Agua de mar', 'Isla Tenglo, sector norte', @st002, @inv, @an, '2026-04-08 09:30:00', '2026-04-08 13:30:00', 'Reportada', 'Media', 'Refrigerada 4 °C, envase sellado', 'Congelador -20 °C · Rack B', '2028-04-07');
  SET @m = LAST_INSERT_ID();
  INSERT INTO muestra_historial_estado (id_muestra, estado_anterior, estado_nuevo, id_usuario, fecha_cambio, comentario) VALUES
    (@m, NULL, 'Recibida', @an, '2026-04-08 13:30:00', 'Recepción de la muestra'),
    (@m, 'Recibida', 'Resultados ingresados', @an, '2026-04-09 15:30:00', 'Todos los análisis tienen resultado'),
    (@m, 'Resultados ingresados', 'Aprobada', @sup, '2026-04-10 15:30:00', 'Todos los análisis aprobados'),
    (@m, 'Aprobada', 'Reportada', @sup, '2026-04-10 18:30:00', 'Informe emitido H-2026-0011-v1');
  INSERT INTO custodia_eventos (id_muestra, tipo_evento, entregado_por, id_usuario_recibe, ubicacion, condicion, confirmado, fecha_hora, observaciones)
    VALUES (@m, 'Recepción en laboratorio', 'Paula Rivas', @an, 'Congelador -20 °C · Rack B', 'Refrigerada 4 °C, envase sellado', TRUE, '2026-04-08 13:30:00', 'Firma al registrar la muestra');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_clor, @an, 'Completado', '2026-04-08 13:30:00', '2026-04-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 4.55, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq002), @eq002, @an, '2026-04-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-04-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_temp, @an, 'Completado', '2026-04-08 13:30:00', '2026-04-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 11.86, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-04-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-04-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_sal, @an, 'Completado', '2026-04-08 13:30:00', '2026-04-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 30.71, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-04-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-04-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_ph, @an, 'Completado', '2026-04-08 13:30:00', '2026-04-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 8.09, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq001), @eq001, @an, '2026-04-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-04-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_od, @an, 'Completado', '2026-04-08 13:30:00', '2026-04-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 8.44, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-04-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-04-10 15:30:00');
  INSERT INTO reportes (id_muestra, id_usuario_generador, fecha_generacion, ruta_archivo, version) VALUES (@m, @sup, '2026-04-10 18:30:00', 'reportes/Informe_H-2026-0011-v1.pdf', 1);

  -- H-2026-0012 · ST-002 · May 2026
  INSERT INTO muestras (id_centro, codigo_unico, tipo_muestra, procedencia, id_sitio, id_investigador, id_usuario_registro, fecha_toma, fecha_recepcion, estado, prioridad, condicion_recepcion, ubicacion_almacenamiento, fecha_vencimiento)
  VALUES (1, 'H-2026-0012', 'Agua de mar', 'Isla Tenglo, sector norte', @st002, @inv, @an, '2026-05-08 09:30:00', '2026-05-08 13:30:00', 'Reportada', 'Media', 'Refrigerada 4 °C, envase sellado', 'Congelador -20 °C · Rack B', '2028-05-07');
  SET @m = LAST_INSERT_ID();
  INSERT INTO muestra_historial_estado (id_muestra, estado_anterior, estado_nuevo, id_usuario, fecha_cambio, comentario) VALUES
    (@m, NULL, 'Recibida', @an, '2026-05-08 13:30:00', 'Recepción de la muestra'),
    (@m, 'Recibida', 'Resultados ingresados', @an, '2026-05-09 15:30:00', 'Todos los análisis tienen resultado'),
    (@m, 'Resultados ingresados', 'Aprobada', @sup, '2026-05-10 15:30:00', 'Todos los análisis aprobados'),
    (@m, 'Aprobada', 'Reportada', @sup, '2026-05-10 18:30:00', 'Informe emitido H-2026-0012-v1');
  INSERT INTO custodia_eventos (id_muestra, tipo_evento, entregado_por, id_usuario_recibe, ubicacion, condicion, confirmado, fecha_hora, observaciones)
    VALUES (@m, 'Recepción en laboratorio', 'Paula Rivas', @an, 'Congelador -20 °C · Rack B', 'Refrigerada 4 °C, envase sellado', TRUE, '2026-05-08 13:30:00', 'Firma al registrar la muestra');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_clor, @an, 'Completado', '2026-05-08 13:30:00', '2026-05-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 6.09, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq002), @eq002, @an, '2026-05-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-05-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_temp, @an, 'Completado', '2026-05-08 13:30:00', '2026-05-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 10.93, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-05-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-05-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_sal, @an, 'Completado', '2026-05-08 13:30:00', '2026-05-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 30.58, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-05-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-05-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_ph, @an, 'Completado', '2026-05-08 13:30:00', '2026-05-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 8.05, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq001), @eq001, @an, '2026-05-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-05-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_od, @an, 'Completado', '2026-05-08 13:30:00', '2026-05-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 8.73, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-05-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-05-10 15:30:00');
  INSERT INTO reportes (id_muestra, id_usuario_generador, fecha_generacion, ruta_archivo, version) VALUES (@m, @sup, '2026-05-10 18:30:00', 'reportes/Informe_H-2026-0012-v1.pdf', 1);

  -- H-2026-0013 · ST-002 · Jun 2026
  INSERT INTO muestras (id_centro, codigo_unico, tipo_muestra, procedencia, id_sitio, id_investigador, id_usuario_registro, fecha_toma, fecha_recepcion, estado, prioridad, condicion_recepcion, ubicacion_almacenamiento, fecha_vencimiento)
  VALUES (1, 'H-2026-0013', 'Agua de mar', 'Isla Tenglo, sector norte', @st002, @inv, @an, '2026-06-08 09:30:00', '2026-06-08 13:30:00', 'Reportada', 'Media', 'Refrigerada 4 °C, envase sellado', 'Congelador -20 °C · Rack B', '2028-06-07');
  SET @m = LAST_INSERT_ID();
  INSERT INTO muestra_historial_estado (id_muestra, estado_anterior, estado_nuevo, id_usuario, fecha_cambio, comentario) VALUES
    (@m, NULL, 'Recibida', @an, '2026-06-08 13:30:00', 'Recepción de la muestra'),
    (@m, 'Recibida', 'Resultados ingresados', @an, '2026-06-09 15:30:00', 'Todos los análisis tienen resultado'),
    (@m, 'Resultados ingresados', 'Aprobada', @sup, '2026-06-10 15:30:00', 'Todos los análisis aprobados'),
    (@m, 'Aprobada', 'Reportada', @sup, '2026-06-10 18:30:00', 'Informe emitido H-2026-0013-v1');
  INSERT INTO custodia_eventos (id_muestra, tipo_evento, entregado_por, id_usuario_recibe, ubicacion, condicion, confirmado, fecha_hora, observaciones)
    VALUES (@m, 'Recepción en laboratorio', 'Paula Rivas', @an, 'Congelador -20 °C · Rack B', 'Refrigerada 4 °C, envase sellado', TRUE, '2026-06-08 13:30:00', 'Firma al registrar la muestra');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_clor, @an, 'Completado', '2026-06-08 13:30:00', '2026-06-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 14.8, FALSE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq002), @eq002, @an, '2026-06-09 15:30:00', 'Floración algal visible en terreno (coloración pardo-rojiza), confirmada por la investigadora', 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', 'Valor alto consistente con la floración observada; se aprueba', '2026-06-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_temp, @an, 'Completado', '2026-06-08 13:30:00', '2026-06-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 11.61, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-06-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-06-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_sal, @an, 'Completado', '2026-06-08 13:30:00', '2026-06-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 30.26, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-06-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-06-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_ph, @an, 'Completado', '2026-06-08 13:30:00', '2026-06-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 8.1, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq001), @eq001, @an, '2026-06-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-06-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_od, @an, 'Completado', '2026-06-08 13:30:00', '2026-06-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 7.85, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-06-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-06-10 15:30:00');
  INSERT INTO reportes (id_muestra, id_usuario_generador, fecha_generacion, ruta_archivo, version) VALUES (@m, @sup, '2026-06-10 18:30:00', 'reportes/Informe_H-2026-0013-v1.pdf', 1);

  -- H-2026-0014 · ST-002 · Jul 2026
  INSERT INTO muestras (id_centro, codigo_unico, tipo_muestra, procedencia, id_sitio, id_investigador, id_usuario_registro, fecha_toma, fecha_recepcion, estado, prioridad, condicion_recepcion, ubicacion_almacenamiento, fecha_vencimiento)
  VALUES (1, 'H-2026-0014', 'Agua de mar', 'Isla Tenglo, sector norte', @st002, @inv, @an, '2026-07-08 09:30:00', '2026-07-08 13:30:00', 'Reportada', 'Media', 'Refrigerada 4 °C, envase sellado', 'Congelador -20 °C · Rack B', '2028-07-07');
  SET @m = LAST_INSERT_ID();
  INSERT INTO muestra_historial_estado (id_muestra, estado_anterior, estado_nuevo, id_usuario, fecha_cambio, comentario) VALUES
    (@m, NULL, 'Recibida', @an, '2026-07-08 13:30:00', 'Recepción de la muestra'),
    (@m, 'Recibida', 'Resultados ingresados', @an, '2026-07-09 15:30:00', 'Todos los análisis tienen resultado'),
    (@m, 'Resultados ingresados', 'Aprobada', @sup, '2026-07-10 15:30:00', 'Todos los análisis aprobados'),
    (@m, 'Aprobada', 'Reportada', @sup, '2026-07-10 18:30:00', 'Informe emitido H-2026-0014-v1');
  INSERT INTO custodia_eventos (id_muestra, tipo_evento, entregado_por, id_usuario_recibe, ubicacion, condicion, confirmado, fecha_hora, observaciones)
    VALUES (@m, 'Recepción en laboratorio', 'Paula Rivas', @an, 'Congelador -20 °C · Rack B', 'Refrigerada 4 °C, envase sellado', TRUE, '2026-07-08 13:30:00', 'Firma al registrar la muestra');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_clor, @an, 'Completado', '2026-07-08 13:30:00', '2026-07-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 4.94, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq002), @eq002, @an, '2026-07-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-07-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_temp, @an, 'Completado', '2026-07-08 13:30:00', '2026-07-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 13.5, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-07-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-07-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_sal, @an, 'Completado', '2026-07-08 13:30:00', '2026-07-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 30.85, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-07-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-07-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_ph, @an, 'Completado', '2026-07-08 13:30:00', '2026-07-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 7.97, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq001), @eq001, @an, '2026-07-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-07-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_od, @an, 'Completado', '2026-07-08 13:30:00', '2026-07-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 8.66, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-07-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-07-10 15:30:00');
  INSERT INTO reportes (id_muestra, id_usuario_generador, fecha_generacion, ruta_archivo, version) VALUES (@m, @sup, '2026-07-10 18:30:00', 'reportes/Informe_H-2026-0014-v1.pdf', 1);

  -- H-2026-0015 · ST-002 · Aug 2026
  INSERT INTO muestras (id_centro, codigo_unico, tipo_muestra, procedencia, id_sitio, id_investigador, id_usuario_registro, fecha_toma, fecha_recepcion, estado, prioridad, condicion_recepcion, ubicacion_almacenamiento, fecha_vencimiento)
  VALUES (1, 'H-2026-0015', 'Agua de mar', 'Isla Tenglo, sector norte', @st002, @inv, @an, '2026-08-08 09:30:00', '2026-08-08 13:30:00', 'Reportada', 'Media', 'Refrigerada 4 °C, envase sellado', 'Congelador -20 °C · Rack B', '2028-08-07');
  SET @m = LAST_INSERT_ID();
  INSERT INTO muestra_historial_estado (id_muestra, estado_anterior, estado_nuevo, id_usuario, fecha_cambio, comentario) VALUES
    (@m, NULL, 'Recibida', @an, '2026-08-08 13:30:00', 'Recepción de la muestra'),
    (@m, 'Recibida', 'Resultados ingresados', @an, '2026-08-09 15:30:00', 'Todos los análisis tienen resultado'),
    (@m, 'Resultados ingresados', 'Aprobada', @sup, '2026-08-10 15:30:00', 'Todos los análisis aprobados'),
    (@m, 'Aprobada', 'Reportada', @sup, '2026-08-10 18:30:00', 'Informe emitido H-2026-0015-v1');
  INSERT INTO custodia_eventos (id_muestra, tipo_evento, entregado_por, id_usuario_recibe, ubicacion, condicion, confirmado, fecha_hora, observaciones)
    VALUES (@m, 'Recepción en laboratorio', 'Paula Rivas', @an, 'Congelador -20 °C · Rack B', 'Refrigerada 4 °C, envase sellado', TRUE, '2026-08-08 13:30:00', 'Firma al registrar la muestra');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_clor, @an, 'Completado', '2026-08-08 13:30:00', '2026-08-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 5.06, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq002), @eq002, @an, '2026-08-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-08-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_temp, @an, 'Completado', '2026-08-08 13:30:00', '2026-08-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 13.26, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-08-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-08-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_sal, @an, 'Completado', '2026-08-08 13:30:00', '2026-08-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 30.74, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-08-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-08-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_ph, @an, 'Completado', '2026-08-08 13:30:00', '2026-08-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 7.96, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq001), @eq001, @an, '2026-08-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-08-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_od, @an, 'Completado', '2026-08-08 13:30:00', '2026-08-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 9.51, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-08-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-08-10 15:30:00');
  INSERT INTO reportes (id_muestra, id_usuario_generador, fecha_generacion, ruta_archivo, version) VALUES (@m, @sup, '2026-08-10 18:30:00', 'reportes/Informe_H-2026-0015-v1.pdf', 1);

  -- H-2026-0016 · ST-002 · Sep 2026
  INSERT INTO muestras (id_centro, codigo_unico, tipo_muestra, procedencia, id_sitio, id_investigador, id_usuario_registro, fecha_toma, fecha_recepcion, estado, prioridad, condicion_recepcion, ubicacion_almacenamiento, fecha_vencimiento)
  VALUES (1, 'H-2026-0016', 'Agua de mar', 'Isla Tenglo, sector norte', @st002, @inv, @an, '2026-09-08 09:30:00', '2026-09-08 13:30:00', 'Reportada', 'Media', 'Refrigerada 4 °C, envase sellado', 'Congelador -20 °C · Rack B', '2028-09-07');
  SET @m = LAST_INSERT_ID();
  INSERT INTO muestra_historial_estado (id_muestra, estado_anterior, estado_nuevo, id_usuario, fecha_cambio, comentario) VALUES
    (@m, NULL, 'Recibida', @an, '2026-09-08 13:30:00', 'Recepción de la muestra'),
    (@m, 'Recibida', 'Resultados ingresados', @an, '2026-09-09 15:30:00', 'Todos los análisis tienen resultado'),
    (@m, 'Resultados ingresados', 'Aprobada', @sup, '2026-09-10 15:30:00', 'Todos los análisis aprobados'),
    (@m, 'Aprobada', 'Reportada', @sup, '2026-09-10 18:30:00', 'Informe emitido H-2026-0016-v1');
  INSERT INTO custodia_eventos (id_muestra, tipo_evento, entregado_por, id_usuario_recibe, ubicacion, condicion, confirmado, fecha_hora, observaciones)
    VALUES (@m, 'Recepción en laboratorio', 'Paula Rivas', @an, 'Congelador -20 °C · Rack B', 'Refrigerada 4 °C, envase sellado', TRUE, '2026-09-08 13:30:00', 'Firma al registrar la muestra');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_clor, @an, 'Completado', '2026-09-08 13:30:00', '2026-09-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 6.14, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq002), @eq002, @an, '2026-09-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-09-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_temp, @an, 'Completado', '2026-09-08 13:30:00', '2026-09-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 12.16, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-09-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-09-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_sal, @an, 'Completado', '2026-09-08 13:30:00', '2026-09-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 30.75, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-09-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-09-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_ph, @an, 'Completado', '2026-09-08 13:30:00', '2026-09-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 7.93, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq001), @eq001, @an, '2026-09-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-09-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_od, @an, 'Completado', '2026-09-08 13:30:00', '2026-09-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 7.9, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-09-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-09-10 15:30:00');
  INSERT INTO reportes (id_muestra, id_usuario_generador, fecha_generacion, ruta_archivo, version) VALUES (@m, @sup, '2026-09-10 18:30:00', 'reportes/Informe_H-2026-0016-v1.pdf', 1);

  -- H-2026-0017 · ST-003 · Feb 2026
  INSERT INTO muestras (id_centro, codigo_unico, tipo_muestra, procedencia, id_sitio, id_investigador, id_usuario_registro, fecha_toma, fecha_recepcion, estado, prioridad, condicion_recepcion, ubicacion_almacenamiento, fecha_vencimiento)
  VALUES (1, 'H-2026-0017', 'Agua de mar', 'Seno de Reloncaví, Caleta La Arena', @st003, @inv, @an, '2026-02-08 09:30:00', '2026-02-08 13:30:00', 'Reportada', 'Media', 'Refrigerada 4 °C, envase sellado', 'Congelador -20 °C · Rack B', '2028-02-08');
  SET @m = LAST_INSERT_ID();
  INSERT INTO muestra_historial_estado (id_muestra, estado_anterior, estado_nuevo, id_usuario, fecha_cambio, comentario) VALUES
    (@m, NULL, 'Recibida', @an, '2026-02-08 13:30:00', 'Recepción de la muestra'),
    (@m, 'Recibida', 'Resultados ingresados', @an, '2026-02-09 15:30:00', 'Todos los análisis tienen resultado'),
    (@m, 'Resultados ingresados', 'Aprobada', @sup, '2026-02-10 15:30:00', 'Todos los análisis aprobados'),
    (@m, 'Aprobada', 'Reportada', @sup, '2026-02-10 18:30:00', 'Informe emitido H-2026-0017-v1');
  INSERT INTO custodia_eventos (id_muestra, tipo_evento, entregado_por, id_usuario_recibe, ubicacion, condicion, confirmado, fecha_hora, observaciones)
    VALUES (@m, 'Recepción en laboratorio', 'Paula Rivas', @an, 'Congelador -20 °C · Rack B', 'Refrigerada 4 °C, envase sellado', TRUE, '2026-02-08 13:30:00', 'Firma al registrar la muestra');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_clor, @an, 'Completado', '2026-02-08 13:30:00', '2026-02-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 2.2, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq002), @eq002, @an, '2026-02-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-02-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_temp, @an, 'Completado', '2026-02-08 13:30:00', '2026-02-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 13.43, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-02-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-02-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_sal, @an, 'Completado', '2026-02-08 13:30:00', '2026-02-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 25.55, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-02-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-02-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_ph, @an, 'Completado', '2026-02-08 13:30:00', '2026-02-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 7.83, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq001), @eq001, @an, '2026-02-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-02-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_od, @an, 'Completado', '2026-02-08 13:30:00', '2026-02-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 7.64, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-02-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-02-10 15:30:00');
  INSERT INTO reportes (id_muestra, id_usuario_generador, fecha_generacion, ruta_archivo, version) VALUES (@m, @sup, '2026-02-10 18:30:00', 'reportes/Informe_H-2026-0017-v1.pdf', 1);

  -- H-2026-0018 · ST-003 · Mar 2026
  INSERT INTO muestras (id_centro, codigo_unico, tipo_muestra, procedencia, id_sitio, id_investigador, id_usuario_registro, fecha_toma, fecha_recepcion, estado, prioridad, condicion_recepcion, ubicacion_almacenamiento, fecha_vencimiento)
  VALUES (1, 'H-2026-0018', 'Agua de mar', 'Seno de Reloncaví, Caleta La Arena', @st003, @inv, @an, '2026-03-08 09:30:00', '2026-03-08 13:30:00', 'Reportada', 'Media', 'Refrigerada 4 °C, envase sellado', 'Congelador -20 °C · Rack B', '2028-03-07');
  SET @m = LAST_INSERT_ID();
  INSERT INTO muestra_historial_estado (id_muestra, estado_anterior, estado_nuevo, id_usuario, fecha_cambio, comentario) VALUES
    (@m, NULL, 'Recibida', @an, '2026-03-08 13:30:00', 'Recepción de la muestra'),
    (@m, 'Recibida', 'Resultados ingresados', @an, '2026-03-09 15:30:00', 'Todos los análisis tienen resultado'),
    (@m, 'Resultados ingresados', 'Aprobada', @sup, '2026-03-10 15:30:00', 'Todos los análisis aprobados'),
    (@m, 'Aprobada', 'Reportada', @sup, '2026-03-10 18:30:00', 'Informe emitido H-2026-0018-v1');
  INSERT INTO custodia_eventos (id_muestra, tipo_evento, entregado_por, id_usuario_recibe, ubicacion, condicion, confirmado, fecha_hora, observaciones)
    VALUES (@m, 'Recepción en laboratorio', 'Paula Rivas', @an, 'Congelador -20 °C · Rack B', 'Refrigerada 4 °C, envase sellado', TRUE, '2026-03-08 13:30:00', 'Firma al registrar la muestra');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_clor, @an, 'Completado', '2026-03-08 13:30:00', '2026-03-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 3.25, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq002), @eq002, @an, '2026-03-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-03-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_temp, @an, 'Completado', '2026-03-08 13:30:00', '2026-03-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 12.49, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-03-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-03-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_sal, @an, 'Completado', '2026-03-08 13:30:00', '2026-03-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 24.69, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-03-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-03-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_ph, @an, 'Completado', '2026-03-08 13:30:00', '2026-03-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 7.88, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq001), @eq001, @an, '2026-03-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-03-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_od, @an, 'Completado', '2026-03-08 13:30:00', '2026-03-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 8.12, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-03-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-03-10 15:30:00');
  INSERT INTO reportes (id_muestra, id_usuario_generador, fecha_generacion, ruta_archivo, version) VALUES (@m, @sup, '2026-03-10 18:30:00', 'reportes/Informe_H-2026-0018-v1.pdf', 1);

  -- H-2026-0019 · ST-003 · Apr 2026
  INSERT INTO muestras (id_centro, codigo_unico, tipo_muestra, procedencia, id_sitio, id_investigador, id_usuario_registro, fecha_toma, fecha_recepcion, estado, prioridad, condicion_recepcion, ubicacion_almacenamiento, fecha_vencimiento)
  VALUES (1, 'H-2026-0019', 'Agua de mar', 'Seno de Reloncaví, Caleta La Arena', @st003, @inv, @an, '2026-04-08 09:30:00', '2026-04-08 13:30:00', 'Reportada', 'Media', 'Refrigerada 4 °C, envase sellado', 'Congelador -20 °C · Rack B', '2028-04-07');
  SET @m = LAST_INSERT_ID();
  INSERT INTO muestra_historial_estado (id_muestra, estado_anterior, estado_nuevo, id_usuario, fecha_cambio, comentario) VALUES
    (@m, NULL, 'Recibida', @an, '2026-04-08 13:30:00', 'Recepción de la muestra'),
    (@m, 'Recibida', 'Resultados ingresados', @an, '2026-04-09 15:30:00', 'Todos los análisis tienen resultado'),
    (@m, 'Resultados ingresados', 'Aprobada', @sup, '2026-04-10 15:30:00', 'Todos los análisis aprobados'),
    (@m, 'Aprobada', 'Reportada', @sup, '2026-04-10 18:30:00', 'Informe emitido H-2026-0019-v1');
  INSERT INTO custodia_eventos (id_muestra, tipo_evento, entregado_por, id_usuario_recibe, ubicacion, condicion, confirmado, fecha_hora, observaciones)
    VALUES (@m, 'Recepción en laboratorio', 'Paula Rivas', @an, 'Congelador -20 °C · Rack B', 'Refrigerada 4 °C, envase sellado', TRUE, '2026-04-08 13:30:00', 'Firma al registrar la muestra');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_clor, @an, 'Completado', '2026-04-08 13:30:00', '2026-04-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 3.41, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq002), @eq002, @an, '2026-04-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-04-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_temp, @an, 'Completado', '2026-04-08 13:30:00', '2026-04-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 11.46, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-04-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-04-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_sal, @an, 'Completado', '2026-04-08 13:30:00', '2026-04-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 24.91, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-04-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-04-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_ph, @an, 'Completado', '2026-04-08 13:30:00', '2026-04-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 7.72, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq001), @eq001, @an, '2026-04-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-04-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_od, @an, 'Completado', '2026-04-08 13:30:00', '2026-04-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 7.96, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-04-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-04-10 15:30:00');
  INSERT INTO reportes (id_muestra, id_usuario_generador, fecha_generacion, ruta_archivo, version) VALUES (@m, @sup, '2026-04-10 18:30:00', 'reportes/Informe_H-2026-0019-v1.pdf', 1);

  -- H-2026-0020 · ST-003 · May 2026
  INSERT INTO muestras (id_centro, codigo_unico, tipo_muestra, procedencia, id_sitio, id_investigador, id_usuario_registro, fecha_toma, fecha_recepcion, estado, prioridad, condicion_recepcion, ubicacion_almacenamiento, fecha_vencimiento)
  VALUES (1, 'H-2026-0020', 'Agua de mar', 'Seno de Reloncaví, Caleta La Arena', @st003, @inv, @an, '2026-05-08 09:30:00', '2026-05-08 13:30:00', 'Reportada', 'Media', 'Refrigerada 4 °C, envase sellado', 'Congelador -20 °C · Rack B', '2028-05-07');
  SET @m = LAST_INSERT_ID();
  INSERT INTO muestra_historial_estado (id_muestra, estado_anterior, estado_nuevo, id_usuario, fecha_cambio, comentario) VALUES
    (@m, NULL, 'Recibida', @an, '2026-05-08 13:30:00', 'Recepción de la muestra'),
    (@m, 'Recibida', 'Resultados ingresados', @an, '2026-05-09 15:30:00', 'Todos los análisis tienen resultado'),
    (@m, 'Resultados ingresados', 'Aprobada', @sup, '2026-05-10 15:30:00', 'Todos los análisis aprobados'),
    (@m, 'Aprobada', 'Reportada', @sup, '2026-05-10 18:30:00', 'Informe emitido H-2026-0020-v1');
  INSERT INTO custodia_eventos (id_muestra, tipo_evento, entregado_por, id_usuario_recibe, ubicacion, condicion, confirmado, fecha_hora, observaciones)
    VALUES (@m, 'Recepción en laboratorio', 'Paula Rivas', @an, 'Congelador -20 °C · Rack B', 'Refrigerada 4 °C, envase sellado', TRUE, '2026-05-08 13:30:00', 'Firma al registrar la muestra');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_clor, @an, 'Completado', '2026-05-08 13:30:00', '2026-05-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 2.82, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq002), @eq002, @an, '2026-05-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-05-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_temp, @an, 'Completado', '2026-05-08 13:30:00', '2026-05-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 12.89, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-05-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-05-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_sal, @an, 'Completado', '2026-05-08 13:30:00', '2026-05-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 23.76, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-05-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-05-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_ph, @an, 'Completado', '2026-05-08 13:30:00', '2026-05-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 7.87, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq001), @eq001, @an, '2026-05-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-05-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_od, @an, 'Completado', '2026-05-08 13:30:00', '2026-05-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 6.95, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-05-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-05-10 15:30:00');
  INSERT INTO reportes (id_muestra, id_usuario_generador, fecha_generacion, ruta_archivo, version) VALUES (@m, @sup, '2026-05-10 18:30:00', 'reportes/Informe_H-2026-0020-v1.pdf', 1);

  -- H-2026-0021 · ST-003 · Jun 2026
  INSERT INTO muestras (id_centro, codigo_unico, tipo_muestra, procedencia, id_sitio, id_investigador, id_usuario_registro, fecha_toma, fecha_recepcion, estado, prioridad, condicion_recepcion, ubicacion_almacenamiento, fecha_vencimiento)
  VALUES (1, 'H-2026-0021', 'Agua de mar', 'Seno de Reloncaví, Caleta La Arena', @st003, @inv, @an, '2026-06-08 09:30:00', '2026-06-08 13:30:00', 'Reportada', 'Media', 'Refrigerada 4 °C, envase sellado', 'Congelador -20 °C · Rack B', '2028-06-07');
  SET @m = LAST_INSERT_ID();
  INSERT INTO muestra_historial_estado (id_muestra, estado_anterior, estado_nuevo, id_usuario, fecha_cambio, comentario) VALUES
    (@m, NULL, 'Recibida', @an, '2026-06-08 13:30:00', 'Recepción de la muestra'),
    (@m, 'Recibida', 'Resultados ingresados', @an, '2026-06-09 15:30:00', 'Todos los análisis tienen resultado'),
    (@m, 'Resultados ingresados', 'Aprobada', @sup, '2026-06-10 15:30:00', 'Todos los análisis aprobados'),
    (@m, 'Aprobada', 'Reportada', @sup, '2026-06-10 18:30:00', 'Informe emitido H-2026-0021-v1');
  INSERT INTO custodia_eventos (id_muestra, tipo_evento, entregado_por, id_usuario_recibe, ubicacion, condicion, confirmado, fecha_hora, observaciones)
    VALUES (@m, 'Recepción en laboratorio', 'Paula Rivas', @an, 'Congelador -20 °C · Rack B', 'Refrigerada 4 °C, envase sellado', TRUE, '2026-06-08 13:30:00', 'Firma al registrar la muestra');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_clor, @an, 'Completado', '2026-06-08 13:30:00', '2026-06-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 2.66, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq002), @eq002, @an, '2026-06-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-06-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_temp, @an, 'Completado', '2026-06-08 13:30:00', '2026-06-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 11.65, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-06-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-06-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_sal, @an, 'Completado', '2026-06-08 13:30:00', '2026-06-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 24.45, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-06-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-06-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_ph, @an, 'Completado', '2026-06-08 13:30:00', '2026-06-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 7.86, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq001), @eq001, @an, '2026-06-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-06-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_od, @an, 'Completado', '2026-06-08 13:30:00', '2026-06-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 7.66, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-06-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-06-10 15:30:00');
  INSERT INTO reportes (id_muestra, id_usuario_generador, fecha_generacion, ruta_archivo, version) VALUES (@m, @sup, '2026-06-10 18:30:00', 'reportes/Informe_H-2026-0021-v1.pdf', 1);

  -- H-2026-0022 · ST-003 · Jul 2026
  INSERT INTO muestras (id_centro, codigo_unico, tipo_muestra, procedencia, id_sitio, id_investigador, id_usuario_registro, fecha_toma, fecha_recepcion, estado, prioridad, condicion_recepcion, ubicacion_almacenamiento, fecha_vencimiento)
  VALUES (1, 'H-2026-0022', 'Agua de mar', 'Seno de Reloncaví, Caleta La Arena', @st003, @inv, @an, '2026-07-08 09:30:00', '2026-07-08 13:30:00', 'Reportada', 'Media', 'Refrigerada 4 °C, envase sellado', 'Congelador -20 °C · Rack B', '2028-07-07');
  SET @m = LAST_INSERT_ID();
  INSERT INTO muestra_historial_estado (id_muestra, estado_anterior, estado_nuevo, id_usuario, fecha_cambio, comentario) VALUES
    (@m, NULL, 'Recibida', @an, '2026-07-08 13:30:00', 'Recepción de la muestra'),
    (@m, 'Recibida', 'Resultados ingresados', @an, '2026-07-09 15:30:00', 'Todos los análisis tienen resultado'),
    (@m, 'Resultados ingresados', 'Aprobada', @sup, '2026-07-10 15:30:00', 'Todos los análisis aprobados'),
    (@m, 'Aprobada', 'Reportada', @sup, '2026-07-10 18:30:00', 'Informe emitido H-2026-0022-v1');
  INSERT INTO custodia_eventos (id_muestra, tipo_evento, entregado_por, id_usuario_recibe, ubicacion, condicion, confirmado, fecha_hora, observaciones)
    VALUES (@m, 'Recepción en laboratorio', 'Paula Rivas', @an, 'Congelador -20 °C · Rack B', 'Refrigerada 4 °C, envase sellado', TRUE, '2026-07-08 13:30:00', 'Firma al registrar la muestra');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_clor, @an, 'Completado', '2026-07-08 13:30:00', '2026-07-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 2.66, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq002), @eq002, @an, '2026-07-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-07-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_temp, @an, 'Completado', '2026-07-08 13:30:00', '2026-07-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 11.73, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-07-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-07-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_sal, @an, 'Completado', '2026-07-08 13:30:00', '2026-07-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 23.81, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-07-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-07-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_ph, @an, 'Completado', '2026-07-08 13:30:00', '2026-07-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 7.88, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq001), @eq001, @an, '2026-07-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-07-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_od, @an, 'Completado', '2026-07-08 13:30:00', '2026-07-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 8.26, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-07-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-07-10 15:30:00');
  INSERT INTO reportes (id_muestra, id_usuario_generador, fecha_generacion, ruta_archivo, version) VALUES (@m, @sup, '2026-07-10 18:30:00', 'reportes/Informe_H-2026-0022-v1.pdf', 1);

  -- H-2026-0023 · ST-003 · Aug 2026
  INSERT INTO muestras (id_centro, codigo_unico, tipo_muestra, procedencia, id_sitio, id_investigador, id_usuario_registro, fecha_toma, fecha_recepcion, estado, prioridad, condicion_recepcion, ubicacion_almacenamiento, fecha_vencimiento)
  VALUES (1, 'H-2026-0023', 'Agua de mar', 'Seno de Reloncaví, Caleta La Arena', @st003, @inv, @an, '2026-08-08 09:30:00', '2026-08-08 13:30:00', 'Reportada', 'Media', 'Refrigerada 4 °C, envase sellado', 'Congelador -20 °C · Rack B', '2028-08-07');
  SET @m = LAST_INSERT_ID();
  INSERT INTO muestra_historial_estado (id_muestra, estado_anterior, estado_nuevo, id_usuario, fecha_cambio, comentario) VALUES
    (@m, NULL, 'Recibida', @an, '2026-08-08 13:30:00', 'Recepción de la muestra'),
    (@m, 'Recibida', 'Resultados ingresados', @an, '2026-08-09 15:30:00', 'Todos los análisis tienen resultado'),
    (@m, 'Resultados ingresados', 'Aprobada', @sup, '2026-08-10 15:30:00', 'Todos los análisis aprobados'),
    (@m, 'Aprobada', 'Reportada', @sup, '2026-08-10 18:30:00', 'Informe emitido H-2026-0023-v1');
  INSERT INTO custodia_eventos (id_muestra, tipo_evento, entregado_por, id_usuario_recibe, ubicacion, condicion, confirmado, fecha_hora, observaciones)
    VALUES (@m, 'Recepción en laboratorio', 'Paula Rivas', @an, 'Congelador -20 °C · Rack B', 'Refrigerada 4 °C, envase sellado', TRUE, '2026-08-08 13:30:00', 'Firma al registrar la muestra');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_clor, @an, 'Completado', '2026-08-08 13:30:00', '2026-08-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 2.89, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq002), @eq002, @an, '2026-08-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-08-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_temp, @an, 'Completado', '2026-08-08 13:30:00', '2026-08-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 11.36, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-08-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-08-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_sal, @an, 'Completado', '2026-08-08 13:30:00', '2026-08-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 22.92, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-08-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-08-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_ph, @an, 'Completado', '2026-08-08 13:30:00', '2026-08-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 7.89, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq001), @eq001, @an, '2026-08-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-08-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_od, @an, 'Completado', '2026-08-08 13:30:00', '2026-08-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 7.33, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-08-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-08-10 15:30:00');
  INSERT INTO reportes (id_muestra, id_usuario_generador, fecha_generacion, ruta_archivo, version) VALUES (@m, @sup, '2026-08-10 18:30:00', 'reportes/Informe_H-2026-0023-v1.pdf', 1);

  -- H-2026-0024 · ST-003 · Sep 2026
  INSERT INTO muestras (id_centro, codigo_unico, tipo_muestra, procedencia, id_sitio, id_investigador, id_usuario_registro, fecha_toma, fecha_recepcion, estado, prioridad, condicion_recepcion, ubicacion_almacenamiento, fecha_vencimiento)
  VALUES (1, 'H-2026-0024', 'Agua de mar', 'Seno de Reloncaví, Caleta La Arena', @st003, @inv, @an, '2026-09-08 09:30:00', '2026-09-08 13:30:00', 'Reportada', 'Media', 'Refrigerada 4 °C, envase sellado', 'Congelador -20 °C · Rack B', '2028-09-07');
  SET @m = LAST_INSERT_ID();
  INSERT INTO muestra_historial_estado (id_muestra, estado_anterior, estado_nuevo, id_usuario, fecha_cambio, comentario) VALUES
    (@m, NULL, 'Recibida', @an, '2026-09-08 13:30:00', 'Recepción de la muestra'),
    (@m, 'Recibida', 'Resultados ingresados', @an, '2026-09-09 15:30:00', 'Todos los análisis tienen resultado'),
    (@m, 'Resultados ingresados', 'Aprobada', @sup, '2026-09-10 15:30:00', 'Todos los análisis aprobados'),
    (@m, 'Aprobada', 'Reportada', @sup, '2026-09-10 18:30:00', 'Informe emitido H-2026-0024-v1');
  INSERT INTO custodia_eventos (id_muestra, tipo_evento, entregado_por, id_usuario_recibe, ubicacion, condicion, confirmado, fecha_hora, observaciones)
    VALUES (@m, 'Recepción en laboratorio', 'Paula Rivas', @an, 'Congelador -20 °C · Rack B', 'Refrigerada 4 °C, envase sellado', TRUE, '2026-09-08 13:30:00', 'Firma al registrar la muestra');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_clor, @an, 'Completado', '2026-09-08 13:30:00', '2026-09-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 2.76, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq002), @eq002, @an, '2026-09-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-09-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_temp, @an, 'Completado', '2026-09-08 13:30:00', '2026-09-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 10.04, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-09-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-09-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_sal, @an, 'Completado', '2026-09-08 13:30:00', '2026-09-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 25.06, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-09-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-09-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_ph, @an, 'Completado', '2026-09-08 13:30:00', '2026-09-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 7.85, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq001), @eq001, @an, '2026-09-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-09-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_od, @an, 'Completado', '2026-09-08 13:30:00', '2026-09-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 7.54, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-09-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-09-10 15:30:00');
  INSERT INTO reportes (id_muestra, id_usuario_generador, fecha_generacion, ruta_archivo, version) VALUES (@m, @sup, '2026-09-10 18:30:00', 'reportes/Informe_H-2026-0024-v1.pdf', 1);

  -- H-2026-0025 · ST-004 · Feb 2026
  INSERT INTO muestras (id_centro, codigo_unico, tipo_muestra, procedencia, id_sitio, id_investigador, id_usuario_registro, fecha_toma, fecha_recepcion, estado, prioridad, condicion_recepcion, ubicacion_almacenamiento, fecha_vencimiento)
  VALUES (1, 'H-2026-0025', 'Agua de mar', 'Canal de Chacao, Pargua', @st004, @inv, @an, '2026-02-08 09:30:00', '2026-02-08 13:30:00', 'Reportada', 'Media', 'Refrigerada 4 °C, envase sellado', 'Congelador -20 °C · Rack B', '2028-02-08');
  SET @m = LAST_INSERT_ID();
  INSERT INTO muestra_historial_estado (id_muestra, estado_anterior, estado_nuevo, id_usuario, fecha_cambio, comentario) VALUES
    (@m, NULL, 'Recibida', @an, '2026-02-08 13:30:00', 'Recepción de la muestra'),
    (@m, 'Recibida', 'Resultados ingresados', @an, '2026-02-09 15:30:00', 'Todos los análisis tienen resultado'),
    (@m, 'Resultados ingresados', 'Aprobada', @sup, '2026-02-10 15:30:00', 'Todos los análisis aprobados'),
    (@m, 'Aprobada', 'Reportada', @sup, '2026-02-10 18:30:00', 'Informe emitido H-2026-0025-v1');
  INSERT INTO custodia_eventos (id_muestra, tipo_evento, entregado_por, id_usuario_recibe, ubicacion, condicion, confirmado, fecha_hora, observaciones)
    VALUES (@m, 'Recepción en laboratorio', 'Paula Rivas', @an, 'Congelador -20 °C · Rack B', 'Refrigerada 4 °C, envase sellado', TRUE, '2026-02-08 13:30:00', 'Firma al registrar la muestra');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_clor, @an, 'Completado', '2026-02-08 13:30:00', '2026-02-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 2.39, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq002), @eq002, @an, '2026-02-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-02-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_temp, @an, 'Completado', '2026-02-08 13:30:00', '2026-02-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 11.41, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-02-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-02-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_sal, @an, 'Completado', '2026-02-08 13:30:00', '2026-02-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 32.45, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-02-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-02-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_ph, @an, 'Completado', '2026-02-08 13:30:00', '2026-02-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 8.11, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq001), @eq001, @an, '2026-02-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-02-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_od, @an, 'Completado', '2026-02-08 13:30:00', '2026-02-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 9.09, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-02-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-02-10 15:30:00');
  INSERT INTO reportes (id_muestra, id_usuario_generador, fecha_generacion, ruta_archivo, version) VALUES (@m, @sup, '2026-02-10 18:30:00', 'reportes/Informe_H-2026-0025-v1.pdf', 1);

  -- H-2026-0026 · ST-004 · Mar 2026
  INSERT INTO muestras (id_centro, codigo_unico, tipo_muestra, procedencia, id_sitio, id_investigador, id_usuario_registro, fecha_toma, fecha_recepcion, estado, prioridad, condicion_recepcion, ubicacion_almacenamiento, fecha_vencimiento)
  VALUES (1, 'H-2026-0026', 'Agua de mar', 'Canal de Chacao, Pargua', @st004, @inv, @an, '2026-03-08 09:30:00', '2026-03-08 13:30:00', 'Reportada', 'Media', 'Refrigerada 4 °C, envase sellado', 'Congelador -20 °C · Rack B', '2028-03-07');
  SET @m = LAST_INSERT_ID();
  INSERT INTO muestra_historial_estado (id_muestra, estado_anterior, estado_nuevo, id_usuario, fecha_cambio, comentario) VALUES
    (@m, NULL, 'Recibida', @an, '2026-03-08 13:30:00', 'Recepción de la muestra'),
    (@m, 'Recibida', 'Resultados ingresados', @an, '2026-03-09 15:30:00', 'Todos los análisis tienen resultado'),
    (@m, 'Resultados ingresados', 'Aprobada', @sup, '2026-03-10 15:30:00', 'Todos los análisis aprobados'),
    (@m, 'Aprobada', 'Reportada', @sup, '2026-03-10 18:30:00', 'Informe emitido H-2026-0026-v1');
  INSERT INTO custodia_eventos (id_muestra, tipo_evento, entregado_por, id_usuario_recibe, ubicacion, condicion, confirmado, fecha_hora, observaciones)
    VALUES (@m, 'Recepción en laboratorio', 'Paula Rivas', @an, 'Congelador -20 °C · Rack B', 'Refrigerada 4 °C, envase sellado', TRUE, '2026-03-08 13:30:00', 'Firma al registrar la muestra');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_clor, @an, 'Completado', '2026-03-08 13:30:00', '2026-03-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 1.98, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq002), @eq002, @an, '2026-03-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-03-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_temp, @an, 'Completado', '2026-03-08 13:30:00', '2026-03-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 9.69, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-03-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-03-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_sal, @an, 'Completado', '2026-03-08 13:30:00', '2026-03-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 32.8, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-03-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-03-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_ph, @an, 'Completado', '2026-03-08 13:30:00', '2026-03-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 8.08, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq001), @eq001, @an, '2026-03-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-03-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_od, @an, 'Completado', '2026-03-08 13:30:00', '2026-03-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 9.45, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-03-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-03-10 15:30:00');
  INSERT INTO reportes (id_muestra, id_usuario_generador, fecha_generacion, ruta_archivo, version) VALUES (@m, @sup, '2026-03-10 18:30:00', 'reportes/Informe_H-2026-0026-v1.pdf', 1);

  -- H-2026-0027 · ST-004 · Apr 2026
  INSERT INTO muestras (id_centro, codigo_unico, tipo_muestra, procedencia, id_sitio, id_investigador, id_usuario_registro, fecha_toma, fecha_recepcion, estado, prioridad, condicion_recepcion, ubicacion_almacenamiento, fecha_vencimiento)
  VALUES (1, 'H-2026-0027', 'Agua de mar', 'Canal de Chacao, Pargua', @st004, @inv, @an, '2026-04-08 09:30:00', '2026-04-08 13:30:00', 'Reportada', 'Media', 'Refrigerada 4 °C, envase sellado', 'Congelador -20 °C · Rack B', '2028-04-07');
  SET @m = LAST_INSERT_ID();
  INSERT INTO muestra_historial_estado (id_muestra, estado_anterior, estado_nuevo, id_usuario, fecha_cambio, comentario) VALUES
    (@m, NULL, 'Recibida', @an, '2026-04-08 13:30:00', 'Recepción de la muestra'),
    (@m, 'Recibida', 'Resultados ingresados', @an, '2026-04-09 15:30:00', 'Todos los análisis tienen resultado'),
    (@m, 'Resultados ingresados', 'Aprobada', @sup, '2026-04-10 15:30:00', 'Todos los análisis aprobados'),
    (@m, 'Aprobada', 'Reportada', @sup, '2026-04-10 18:30:00', 'Informe emitido H-2026-0027-v1');
  INSERT INTO custodia_eventos (id_muestra, tipo_evento, entregado_por, id_usuario_recibe, ubicacion, condicion, confirmado, fecha_hora, observaciones)
    VALUES (@m, 'Recepción en laboratorio', 'Paula Rivas', @an, 'Congelador -20 °C · Rack B', 'Refrigerada 4 °C, envase sellado', TRUE, '2026-04-08 13:30:00', 'Firma al registrar la muestra');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_clor, @an, 'Completado', '2026-04-08 13:30:00', '2026-04-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 2.96, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq002), @eq002, @an, '2026-04-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-04-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_temp, @an, 'Completado', '2026-04-08 13:30:00', '2026-04-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 10.62, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-04-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-04-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_sal, @an, 'Completado', '2026-04-08 13:30:00', '2026-04-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 32.28, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-04-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-04-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_ph, @an, 'Completado', '2026-04-08 13:30:00', '2026-04-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 8.08, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq001), @eq001, @an, '2026-04-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-04-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_od, @an, 'Completado', '2026-04-08 13:30:00', '2026-04-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 9.6, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-04-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-04-10 15:30:00');
  INSERT INTO reportes (id_muestra, id_usuario_generador, fecha_generacion, ruta_archivo, version) VALUES (@m, @sup, '2026-04-10 18:30:00', 'reportes/Informe_H-2026-0027-v1.pdf', 1);

  -- H-2026-0028 · ST-004 · May 2026
  INSERT INTO muestras (id_centro, codigo_unico, tipo_muestra, procedencia, id_sitio, id_investigador, id_usuario_registro, fecha_toma, fecha_recepcion, estado, prioridad, condicion_recepcion, ubicacion_almacenamiento, fecha_vencimiento)
  VALUES (1, 'H-2026-0028', 'Agua de mar', 'Canal de Chacao, Pargua', @st004, @inv, @an, '2026-05-08 09:30:00', '2026-05-08 13:30:00', 'Reportada', 'Media', 'Refrigerada 4 °C, envase sellado', 'Congelador -20 °C · Rack B', '2028-05-07');
  SET @m = LAST_INSERT_ID();
  INSERT INTO muestra_historial_estado (id_muestra, estado_anterior, estado_nuevo, id_usuario, fecha_cambio, comentario) VALUES
    (@m, NULL, 'Recibida', @an, '2026-05-08 13:30:00', 'Recepción de la muestra'),
    (@m, 'Recibida', 'Resultados ingresados', @an, '2026-05-09 15:30:00', 'Todos los análisis tienen resultado'),
    (@m, 'Resultados ingresados', 'Aprobada', @sup, '2026-05-10 15:30:00', 'Todos los análisis aprobados'),
    (@m, 'Aprobada', 'Reportada', @sup, '2026-05-10 18:30:00', 'Informe emitido H-2026-0028-v1');
  INSERT INTO custodia_eventos (id_muestra, tipo_evento, entregado_por, id_usuario_recibe, ubicacion, condicion, confirmado, fecha_hora, observaciones)
    VALUES (@m, 'Recepción en laboratorio', 'Paula Rivas', @an, 'Congelador -20 °C · Rack B', 'Refrigerada 4 °C, envase sellado', TRUE, '2026-05-08 13:30:00', 'Firma al registrar la muestra');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_clor, @an, 'Completado', '2026-05-08 13:30:00', '2026-05-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 2.49, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq002), @eq002, @an, '2026-05-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-05-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_temp, @an, 'Completado', '2026-05-08 13:30:00', '2026-05-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 10.78, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-05-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-05-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_sal, @an, 'Completado', '2026-05-08 13:30:00', '2026-05-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 32.77, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-05-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-05-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_ph, @an, 'Completado', '2026-05-08 13:30:00', '2026-05-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 8.07, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq001), @eq001, @an, '2026-05-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-05-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_od, @an, 'Completado', '2026-05-08 13:30:00', '2026-05-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 8.9, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-05-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-05-10 15:30:00');
  INSERT INTO reportes (id_muestra, id_usuario_generador, fecha_generacion, ruta_archivo, version) VALUES (@m, @sup, '2026-05-10 18:30:00', 'reportes/Informe_H-2026-0028-v1.pdf', 1);

  -- H-2026-0029 · ST-004 · Jun 2026
  INSERT INTO muestras (id_centro, codigo_unico, tipo_muestra, procedencia, id_sitio, id_investigador, id_usuario_registro, fecha_toma, fecha_recepcion, estado, prioridad, condicion_recepcion, ubicacion_almacenamiento, fecha_vencimiento)
  VALUES (1, 'H-2026-0029', 'Agua de mar', 'Canal de Chacao, Pargua', @st004, @inv, @an, '2026-06-08 09:30:00', '2026-06-08 13:30:00', 'Reportada', 'Media', 'Refrigerada 4 °C, envase sellado', 'Congelador -20 °C · Rack B', '2028-06-07');
  SET @m = LAST_INSERT_ID();
  INSERT INTO muestra_historial_estado (id_muestra, estado_anterior, estado_nuevo, id_usuario, fecha_cambio, comentario) VALUES
    (@m, NULL, 'Recibida', @an, '2026-06-08 13:30:00', 'Recepción de la muestra'),
    (@m, 'Recibida', 'Resultados ingresados', @an, '2026-06-09 15:30:00', 'Todos los análisis tienen resultado'),
    (@m, 'Resultados ingresados', 'Aprobada', @sup, '2026-06-10 15:30:00', 'Todos los análisis aprobados'),
    (@m, 'Aprobada', 'Reportada', @sup, '2026-06-10 18:30:00', 'Informe emitido H-2026-0029-v1');
  INSERT INTO custodia_eventos (id_muestra, tipo_evento, entregado_por, id_usuario_recibe, ubicacion, condicion, confirmado, fecha_hora, observaciones)
    VALUES (@m, 'Recepción en laboratorio', 'Paula Rivas', @an, 'Congelador -20 °C · Rack B', 'Refrigerada 4 °C, envase sellado', TRUE, '2026-06-08 13:30:00', 'Firma al registrar la muestra');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_clor, @an, 'Completado', '2026-06-08 13:30:00', '2026-06-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 2.24, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq002), @eq002, @an, '2026-06-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-06-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_temp, @an, 'Completado', '2026-06-08 13:30:00', '2026-06-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 10.93, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-06-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-06-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_sal, @an, 'Completado', '2026-06-08 13:30:00', '2026-06-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 32.08, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-06-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-06-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_ph, @an, 'Completado', '2026-06-08 13:30:00', '2026-06-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 8.08, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq001), @eq001, @an, '2026-06-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-06-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_od, @an, 'Completado', '2026-06-08 13:30:00', '2026-06-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 9.13, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-06-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-06-10 15:30:00');
  INSERT INTO reportes (id_muestra, id_usuario_generador, fecha_generacion, ruta_archivo, version) VALUES (@m, @sup, '2026-06-10 18:30:00', 'reportes/Informe_H-2026-0029-v1.pdf', 1);

  -- H-2026-0030 · ST-004 · Jul 2026
  INSERT INTO muestras (id_centro, codigo_unico, tipo_muestra, procedencia, id_sitio, id_investigador, id_usuario_registro, fecha_toma, fecha_recepcion, estado, prioridad, condicion_recepcion, ubicacion_almacenamiento, fecha_vencimiento)
  VALUES (1, 'H-2026-0030', 'Agua de mar', 'Canal de Chacao, Pargua', @st004, @inv, @an, '2026-07-08 09:30:00', '2026-07-08 13:30:00', 'Reportada', 'Media', 'Refrigerada 4 °C, envase sellado', 'Congelador -20 °C · Rack B', '2028-07-07');
  SET @m = LAST_INSERT_ID();
  INSERT INTO muestra_historial_estado (id_muestra, estado_anterior, estado_nuevo, id_usuario, fecha_cambio, comentario) VALUES
    (@m, NULL, 'Recibida', @an, '2026-07-08 13:30:00', 'Recepción de la muestra'),
    (@m, 'Recibida', 'Resultados ingresados', @an, '2026-07-09 15:30:00', 'Todos los análisis tienen resultado'),
    (@m, 'Resultados ingresados', 'Aprobada', @sup, '2026-07-10 15:30:00', 'Todos los análisis aprobados'),
    (@m, 'Aprobada', 'Reportada', @sup, '2026-07-10 18:30:00', 'Informe emitido H-2026-0030-v1');
  INSERT INTO custodia_eventos (id_muestra, tipo_evento, entregado_por, id_usuario_recibe, ubicacion, condicion, confirmado, fecha_hora, observaciones)
    VALUES (@m, 'Recepción en laboratorio', 'Paula Rivas', @an, 'Congelador -20 °C · Rack B', 'Refrigerada 4 °C, envase sellado', TRUE, '2026-07-08 13:30:00', 'Firma al registrar la muestra');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_clor, @an, 'Completado', '2026-07-08 13:30:00', '2026-07-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 2.06, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq002), @eq002, @an, '2026-07-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-07-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_temp, @an, 'Completado', '2026-07-08 13:30:00', '2026-07-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 10.22, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-07-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-07-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_sal, @an, 'Completado', '2026-07-08 13:30:00', '2026-07-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 32.62, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-07-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-07-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_ph, @an, 'Completado', '2026-07-08 13:30:00', '2026-07-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 8.07, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq001), @eq001, @an, '2026-07-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-07-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_od, @an, 'Completado', '2026-07-08 13:30:00', '2026-07-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 9.02, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-07-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-07-10 15:30:00');
  INSERT INTO reportes (id_muestra, id_usuario_generador, fecha_generacion, ruta_archivo, version) VALUES (@m, @sup, '2026-07-10 18:30:00', 'reportes/Informe_H-2026-0030-v1.pdf', 1);

  -- H-2026-0031 · ST-004 · Aug 2026
  INSERT INTO muestras (id_centro, codigo_unico, tipo_muestra, procedencia, id_sitio, id_investigador, id_usuario_registro, fecha_toma, fecha_recepcion, estado, prioridad, condicion_recepcion, ubicacion_almacenamiento, fecha_vencimiento)
  VALUES (1, 'H-2026-0031', 'Agua de mar', 'Canal de Chacao, Pargua', @st004, @inv, @an, '2026-08-08 09:30:00', '2026-08-08 13:30:00', 'Reportada', 'Media', 'Refrigerada 4 °C, envase sellado', 'Congelador -20 °C · Rack B', '2028-08-07');
  SET @m = LAST_INSERT_ID();
  INSERT INTO muestra_historial_estado (id_muestra, estado_anterior, estado_nuevo, id_usuario, fecha_cambio, comentario) VALUES
    (@m, NULL, 'Recibida', @an, '2026-08-08 13:30:00', 'Recepción de la muestra'),
    (@m, 'Recibida', 'Resultados ingresados', @an, '2026-08-09 15:30:00', 'Todos los análisis tienen resultado'),
    (@m, 'Resultados ingresados', 'Aprobada', @sup, '2026-08-10 15:30:00', 'Todos los análisis aprobados'),
    (@m, 'Aprobada', 'Reportada', @sup, '2026-08-10 18:30:00', 'Informe emitido H-2026-0031-v1');
  INSERT INTO custodia_eventos (id_muestra, tipo_evento, entregado_por, id_usuario_recibe, ubicacion, condicion, confirmado, fecha_hora, observaciones)
    VALUES (@m, 'Recepción en laboratorio', 'Paula Rivas', @an, 'Congelador -20 °C · Rack B', 'Refrigerada 4 °C, envase sellado', TRUE, '2026-08-08 13:30:00', 'Firma al registrar la muestra');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_clor, @an, 'Completado', '2026-08-08 13:30:00', '2026-08-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 2.81, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq002), @eq002, @an, '2026-08-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-08-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_temp, @an, 'Completado', '2026-08-08 13:30:00', '2026-08-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 11.04, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-08-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-08-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_sal, @an, 'Completado', '2026-08-08 13:30:00', '2026-08-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 33.06, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-08-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-08-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_ph, @an, 'Completado', '2026-08-08 13:30:00', '2026-08-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 8.1, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq001), @eq001, @an, '2026-08-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-08-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_od, @an, 'Completado', '2026-08-08 13:30:00', '2026-08-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 8.61, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-08-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-08-10 15:30:00');
  INSERT INTO reportes (id_muestra, id_usuario_generador, fecha_generacion, ruta_archivo, version) VALUES (@m, @sup, '2026-08-10 18:30:00', 'reportes/Informe_H-2026-0031-v1.pdf', 1);

  -- H-2026-0032 · ST-004 · Sep 2026
  INSERT INTO muestras (id_centro, codigo_unico, tipo_muestra, procedencia, id_sitio, id_investigador, id_usuario_registro, fecha_toma, fecha_recepcion, estado, prioridad, condicion_recepcion, ubicacion_almacenamiento, fecha_vencimiento)
  VALUES (1, 'H-2026-0032', 'Agua de mar', 'Canal de Chacao, Pargua', @st004, @inv, @an, '2026-09-08 09:30:00', '2026-09-08 13:30:00', 'Reportada', 'Media', 'Refrigerada 4 °C, envase sellado', 'Congelador -20 °C · Rack B', '2028-09-07');
  SET @m = LAST_INSERT_ID();
  INSERT INTO muestra_historial_estado (id_muestra, estado_anterior, estado_nuevo, id_usuario, fecha_cambio, comentario) VALUES
    (@m, NULL, 'Recibida', @an, '2026-09-08 13:30:00', 'Recepción de la muestra'),
    (@m, 'Recibida', 'Resultados ingresados', @an, '2026-09-09 15:30:00', 'Todos los análisis tienen resultado'),
    (@m, 'Resultados ingresados', 'Aprobada', @sup, '2026-09-10 15:30:00', 'Todos los análisis aprobados'),
    (@m, 'Aprobada', 'Reportada', @sup, '2026-09-10 18:30:00', 'Informe emitido H-2026-0032-v1');
  INSERT INTO custodia_eventos (id_muestra, tipo_evento, entregado_por, id_usuario_recibe, ubicacion, condicion, confirmado, fecha_hora, observaciones)
    VALUES (@m, 'Recepción en laboratorio', 'Paula Rivas', @an, 'Congelador -20 °C · Rack B', 'Refrigerada 4 °C, envase sellado', TRUE, '2026-09-08 13:30:00', 'Firma al registrar la muestra');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_clor, @an, 'Completado', '2026-09-08 13:30:00', '2026-09-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 2.64, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq002), @eq002, @an, '2026-09-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-09-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_temp, @an, 'Completado', '2026-09-08 13:30:00', '2026-09-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 11.13, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-09-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-09-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_sal, @an, 'Completado', '2026-09-08 13:30:00', '2026-09-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 32.73, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-09-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-09-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_ph, @an, 'Completado', '2026-09-08 13:30:00', '2026-09-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 8.05, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq001), @eq001, @an, '2026-09-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-09-10 15:30:00');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, id_analista_asignado, estado, fecha_asignacion, fecha_completado) VALUES (@m, @a_od, @an, 'Completado', '2026-09-08 13:30:00', '2026-09-09 15:30:00');
  SET @ma = LAST_INSERT_ID();
  INSERT INTO resultados (id_muestra_analisis, valor_resultado, dentro_rango, instrumento_utilizado, id_equipo, id_usuario_ingreso, fecha_ingreso, observaciones, version, es_anomalo) VALUES (@ma, 9.05, TRUE, (SELECT CONCAT(codigo, ' · ', nombre) FROM equipos WHERE id_equipo = @eq003), @eq003, @an, '2026-09-09 15:30:00', NULL, 1, FALSE);
  SET @r = LAST_INSERT_ID();
  INSERT INTO resultado_versiones (id_resultado, version, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario, fecha) SELECT id_resultado, 1, valor_resultado, dentro_rango, id_equipo, instrumento_utilizado, observaciones, es_anomalo, id_usuario_ingreso, fecha_ingreso FROM resultados WHERE id_resultado = @r;
  INSERT INTO aprobaciones (id_resultado, version_resultado, id_supervisor, estado_aprobacion, comentario, fecha_aprobacion) VALUES (@r, 1, @sup, 'Aprobado', NULL, '2026-09-10 15:30:00');
  INSERT INTO reportes (id_muestra, id_usuario_generador, fecha_generacion, ruta_archivo, version) VALUES (@m, @sup, '2026-09-10 18:30:00', 'reportes/Informe_H-2026-0032-v1.pdf', 1);

  -- D-2026-0001: muestra recibida para la demostración en vivo
  INSERT INTO muestras (id_centro, codigo_unico, tipo_muestra, procedencia, id_sitio, id_investigador, id_usuario_registro, fecha_toma, fecha_recepcion, estado, prioridad, condicion_recepcion, ubicacion_almacenamiento, fecha_vencimiento)
  VALUES (1, 'D-2026-0001', 'Macroalga', 'Bahía Ilque, estación 3', @st001, @inv, @an, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 'Recibida', 'Alta', 'Refrigerada 4 °C, envase sellado', 'Refrigerador 4 °C · Estante 1', DATE_ADD(CURDATE(), INTERVAL 2 YEAR));
  SET @m = LAST_INSERT_ID();
  INSERT INTO muestra_historial_estado (id_muestra, estado_anterior, estado_nuevo, id_usuario, comentario) VALUES (@m, NULL, 'Recibida', @an, 'Recepción de la muestra');
  INSERT INTO custodia_eventos (id_muestra, tipo_evento, entregado_por, id_usuario_recibe, ubicacion, condicion, confirmado, observaciones)
    VALUES (@m, 'Recepción en laboratorio', 'Paula Rivas', @an, 'Refrigerador 4 °C · Estante 1', 'Refrigerada 4 °C, envase sellado', TRUE, 'Firma al registrar la muestra');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, estado, fecha_asignacion) VALUES (@m, @a_clor, 'Pendiente', NOW());
  INSERT INTO muestra_analisis (id_muestra, id_analisis, estado, fecha_asignacion) VALUES (@m, @a_temp, 'Pendiente', NOW());
  INSERT INTO muestra_analisis (id_muestra, id_analisis, estado, fecha_asignacion) VALUES (@m, @a_sal, 'Pendiente', NOW());
  INSERT INTO muestra_analisis (id_muestra, id_analisis, estado, fecha_asignacion) VALUES (@m, @a_ph, 'Pendiente', NOW());

  -- D-2026-0002: muestra recibida para la demostración en vivo
  INSERT INTO muestras (id_centro, codigo_unico, tipo_muestra, procedencia, id_sitio, id_investigador, id_usuario_registro, fecha_toma, fecha_recepcion, estado, prioridad, condicion_recepcion, ubicacion_almacenamiento, fecha_vencimiento)
  VALUES (1, 'D-2026-0002', 'Agua de mar', 'Seno de Reloncaví, Caleta La Arena', @st003, @inv, @an, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 'Recibida', 'Alta', 'Refrigerada 4 °C, envase sellado', 'Refrigerador 4 °C · Estante 1', DATE_ADD(CURDATE(), INTERVAL 2 YEAR));
  SET @m = LAST_INSERT_ID();
  INSERT INTO muestra_historial_estado (id_muestra, estado_anterior, estado_nuevo, id_usuario, comentario) VALUES (@m, NULL, 'Recibida', @an, 'Recepción de la muestra');
  INSERT INTO custodia_eventos (id_muestra, tipo_evento, entregado_por, id_usuario_recibe, ubicacion, condicion, confirmado, observaciones)
    VALUES (@m, 'Recepción en laboratorio', 'Paula Rivas', @an, 'Refrigerador 4 °C · Estante 1', 'Refrigerada 4 °C, envase sellado', TRUE, 'Firma al registrar la muestra');
  INSERT INTO muestra_analisis (id_muestra, id_analisis, estado, fecha_asignacion) VALUES (@m, @a_clor, 'Pendiente', NOW());
  INSERT INTO muestra_analisis (id_muestra, id_analisis, estado, fecha_asignacion) VALUES (@m, @a_temp, 'Pendiente', NOW());
  INSERT INTO muestra_analisis (id_muestra, id_analisis, estado, fecha_asignacion) VALUES (@m, @a_sal, 'Pendiente', NOW());
  INSERT INTO muestra_analisis (id_muestra, id_analisis, estado, fecha_asignacion) VALUES (@m, @a_ph, 'Pendiente', NOW());

  END IF;
END //
DELIMITER ;

CALL cargar_demo_v5();
DROP PROCEDURE cargar_demo_v5;
