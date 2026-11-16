package com.duoc.lims.limsclient.model;

/** Cuerpo de POST /api/muestras/{id}/custodia. Quien recibe y firma es el usuario de la sesión. */
public record CustodiaRequest(
        String tipoEvento,
        String entregadoPor,
        String ubicacion,
        String condicion,
        Boolean confirmado,
        String observaciones) {}
