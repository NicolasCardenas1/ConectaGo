package com.duoc.lims.limsclient.model;

/** Cuerpo de POST /api/aprobaciones. estadoAprobacion: "Aprobado" o "Rechazado". */
public record AprobacionRequest(
        Integer idResultado,
        Integer idSupervisor,
        String estadoAprobacion,
        String comentario) {}
