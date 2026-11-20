package com.duoc.lims.limsclient.model;

/** Cuerpo de POST/PUT /api/investigadores. */
public record InvestigadorRequest(
        String nombre,
        String apellido,
        String institucion,
        String email,
        String telefono,
        String proyecto) {}
