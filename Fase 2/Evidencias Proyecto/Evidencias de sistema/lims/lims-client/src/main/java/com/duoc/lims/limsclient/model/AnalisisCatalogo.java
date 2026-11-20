package com.duoc.lims.limsclient.model;

import java.math.BigDecimal;

/** Parámetro del catálogo con su rango normal (GET /api/analisis). */
public record AnalisisCatalogo(
        Integer id,
        String nombre,
        String descripcion,
        String unidadMedida,
        String metodoReferencia,
        BigDecimal tiempoEstimadoHrs,
        BigDecimal valorMinNormal,
        BigDecimal valorMaxNormal,
        Boolean activo) {

    // Es lo que muestra el ComboBox en cada opción.
    @Override
    public String toString() {
        return nombre + " (" + unidadMedida + ")";
    }
}
