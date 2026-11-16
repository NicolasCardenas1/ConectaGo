package com.duoc.lims.limsclient.model;

/** v5 — Ubicación de almacenamiento (GET /api/ubicaciones). */
public record Ubicacion(
        Integer id,
        String nombre,
        String tipo,
        String temperatura,
        String descripcion,
        boolean activo) {

    @Override
    public String toString() {
        return nombre;
    }
}
