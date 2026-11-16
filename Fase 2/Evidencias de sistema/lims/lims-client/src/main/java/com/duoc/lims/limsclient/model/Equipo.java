package com.duoc.lims.limsclient.model;

import java.time.LocalDate;

/** v5 — Equipo del laboratorio con su calibración (GET /api/equipos). */
public record Equipo(
        Integer id,
        String codigo,
        String nombre,
        String marca,
        String modelo,
        String numeroSerie,
        LocalDate fechaUltimaCalibracion,
        LocalDate fechaProximaCalibracion,
        boolean activo,
        String estadoCalibracion,   // Vigente / Por vencer / Vencida / Sin registro
        Long diasParaVencer) {

    public boolean calibracionVencida() {
        return "Vencida".equals(estadoCalibracion);
    }

    // Lo que muestra el ComboBox del ingreso de resultados.
    @Override
    public String toString() {
        String estado = fechaProximaCalibracion == null ? "sin calibración registrada"
                : calibracionVencida() ? "CALIBRACIÓN VENCIDA" : "calibrado hasta " + fechaProximaCalibracion;
        return codigo + " · " + nombre + " — " + estado;
    }
}
