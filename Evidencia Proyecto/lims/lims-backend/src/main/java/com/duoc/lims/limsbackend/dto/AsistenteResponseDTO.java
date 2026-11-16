package com.duoc.lims.limsbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** v6 — Respuesta del chatbot de trazabilidad. */
@Getter
@Setter
@AllArgsConstructor
public class AsistenteResponseDTO {
    private String respuesta;
    private String origen;             // "IA (Claude)" o "Consulta directa (sin IA)"
    private List<String> consultas;    // consultas que se hicieron a la base de datos (transparencia)
    private String aviso;              // null si todo funcionó normal
}
