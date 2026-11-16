package com.duoc.lims.limsclient.model;

import java.util.List;

/** v6 — Pregunta al chatbot de trazabilidad (POST /api/asistente). */
public record AsistenteRequest(String pregunta, List<Mensaje> historial) {

    /** Un mensaje anterior de la conversación: rol "usuario" o "asistente". */
    public record Mensaje(String rol, String texto) {}
}
