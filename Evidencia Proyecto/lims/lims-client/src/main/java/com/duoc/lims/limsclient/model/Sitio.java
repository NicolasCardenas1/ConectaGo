package com.duoc.lims.limsclient.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** v5 — Sitio de muestreo georreferenciado (GET /api/sitios). */
public record Sitio(
        Integer id,
        String codigo,
        String nombre,
        BigDecimal latitud,
        BigDecimal longitud,
        String tipoAmbiente,
        String comuna,
        String region,
        String descripcion,
        boolean activo,
        long totalMuestras,
        long resultadosFueraRango,
        long resultadosAnomalos,
        LocalDateTime ultimoMuestreo) {

    // Lo que muestra el ComboBox del registro de muestra.
    @Override
    public String toString() {
        return codigo + " · " + nombre + (comuna == null ? "" : " (" + comuna + ")");
    }
}
