package com.duoc.lims.limsbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** v5 — Borrador de interpretación de resultados (lo revisa y edita un supervisor antes de guardarlo). */
@Getter
@Setter
@AllArgsConstructor
public class InterpretacionDTO {
    private String texto;
    private String origen;   // "IA (Claude)" o "Reglas automáticas"
    private String aviso;    // por qué se usó el respaldo por reglas, si aplica
}
