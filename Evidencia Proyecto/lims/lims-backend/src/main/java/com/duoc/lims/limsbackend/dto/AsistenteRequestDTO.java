package com.duoc.lims.limsbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** v6 — Pregunta al chatbot de trazabilidad (POST /api/asistente). */
@Getter
@Setter
public class AsistenteRequestDTO {

    @NotBlank(message = "escribe una pregunta")
    @Size(max = 1000, message = "la pregunta no puede superar los 1000 caracteres")
    private String pregunta;

    // Mensajes anteriores de la conversación (para preguntas de seguimiento como "¿y dónde está guardada?").
    private List<Mensaje> historial;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Mensaje {
        private String rol;     // "usuario" o "asistente"
        private String texto;
    }
}
