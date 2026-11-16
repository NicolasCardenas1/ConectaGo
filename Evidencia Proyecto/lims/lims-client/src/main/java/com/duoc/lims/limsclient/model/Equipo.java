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
        Long diasParaVencer,
        String uso) {               // v6: Terreno / Laboratorio / Ambos

    public boolean usoTerreno() {
        return "Terreno".equals(uso) || "Ambos".equals(uso);
    }

    public boolean usoLaboratorio() {
        // Equipos anteriores a v6 (sin uso) se consideran de laboratorio.
        return uso == null || "Laboratorio".equals(uso) || "Ambos".equals(uso);
    }

    public boolean calibracionVencida() {
        return "Vencida".equals(estadoCalibracion);
    }

    // Lo que muestra el ComboBox del ingreso de resultados y del equipo de toma.
    @Override
    public String toString() {
        String estado = fechaProximaCalibracion == null ? "sin calibración registrada"
                : calibracionVencida() ? "CALIBRACIÓN VENCIDA" : "calibrado hasta " + fechaProximaCalibracion;
        return codigo + " · " + nombre + " — " + estado;
    }
}
