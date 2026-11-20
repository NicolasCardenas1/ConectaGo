package com.duoc.lims.limsbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** v5 — Resultado de comparar un valor contra el historial del mismo parámetro (y sitio). */
@Getter
@Setter
@AllArgsConstructor
public class AnomaliaDTO {
    private boolean evaluado;        // false si no hay historial suficiente
    private boolean anomalo;
    private BigDecimal puntaje;      // puntaje z robusto (mediana / MAD)
    private int tamanoHistorial;
    private BigDecimal mediana;
    private String referencia;       // con qué grupo se comparó (sitio, tipo de muestra...)
    private String mensaje;          // texto listo para mostrar
}
