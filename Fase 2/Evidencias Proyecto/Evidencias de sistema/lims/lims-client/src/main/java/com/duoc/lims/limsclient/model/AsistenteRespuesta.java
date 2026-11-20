package com.duoc.lims.limsclient.model;

import java.util.List;

/** v6 — Respuesta del chatbot de trazabilidad. */
public record AsistenteRespuesta(
        String respuesta,
        String origen,            // "IA (Claude)" o "Consulta directa (sin IA)"
        List<String> consultas,   // consultas que hizo a la base de datos
        String aviso) {}
