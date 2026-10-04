package com.duoc.lims.limsclient.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Un resultado devuelto por GET /api/resultados (por ejemplo, ?pendientes=true). */
public record ResultadoPendiente(
        Integer id,
        Integer idMuestraAnalisis,
        String nombreAnalisis,
        String codigoMuestra,
        BigDecimal valorResultado,
        String unidadMedida,
        BigDecimal valorMinNormal,
        BigDecimal valorMaxNormal,
        Boolean dentroRango,
        String instrumentoUtilizado,
        String usuarioIngreso,
        LocalDateTime fechaIngreso,
        String observaciones) {}
