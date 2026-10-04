package com.duoc.lims.limsclient.model;

import java.math.BigDecimal;

public record AnalisisCatalogo(
        Integer id,
        String nombre,
        String unidadMedida,
        BigDecimal valorMinNormal,
        BigDecimal valorMaxNormal,
        Boolean activo) {

    // Es lo que muestra el ComboBox en cada opción.
    @Override
    public String toString() {
        return nombre + " (" + unidadMedida + ")";
    }
}