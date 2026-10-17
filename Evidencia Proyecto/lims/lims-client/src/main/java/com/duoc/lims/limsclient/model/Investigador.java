package com.duoc.lims.limsclient.model;

/** Investigador que encarga el muestreo (GET /api/investigadores). */
public record Investigador(
        Integer id,
        String nombre,
        String apellido,
        String institucion,
        String email,
        String telefono,
        String proyecto,
        boolean activo) {

    public String nombreCompleto() {
        return nombre + " " + apellido;
    }

    // Lo que muestra el ComboBox del registro de muestra.
    @Override
    public String toString() {
        return nombreCompleto() + (institucion == null || institucion.isBlank() ? "" : " — " + institucion);
    }
}
