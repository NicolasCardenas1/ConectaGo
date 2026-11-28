-- =====================================================================
-- MIGRACIÓN v7 — Firma digital de los informes PDF
-- =====================================================================
-- Cada informe emitido desde v7 queda firmado con el certificado del laboratorio y guarda:
--   * codigo_verificacion: código impreso en el informe (ej. 7F3A-9C2K) para verificarlo en el LIMS;
--   * hash_sha256: huella del PDF firmado tal como se emitió, para detectar cualquier modificación.
-- Los informes anteriores quedan con ambos campos en NULL (se emitieron sin firma).
--
-- Requisito: la base debe tener v4, v5 y v6. Ejecutar UNA sola vez, desde lims-backend:
--   docker cp db-migrations\v7-firma-digital-informes.sql lims-mysql:/tmp/m.sql
--   docker exec lims-mysql sh -c "mysql --default-character-set=utf8mb4 -ulims_user -plims_pass lims_db < /tmp/m.sql"
-- Si aparece "Duplicate column name 'codigo_verificacion'", ya estaba aplicada.
-- =====================================================================

SET NAMES utf8mb4;

ALTER TABLE reportes
    ADD COLUMN codigo_verificacion VARCHAR(9) NULL UNIQUE AFTER version,
    ADD COLUMN hash_sha256 VARCHAR(64) NULL AFTER codigo_verificacion;
