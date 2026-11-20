package com.duoc.lims.limsbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** v5 — Un hito de la línea de tiempo de trazabilidad de una muestra. */
@Getter
@Setter
@AllArgsConstructor
public class TrazabilidadEventoDTO {
    private LocalDateTime fecha;
    // MUESTREO, ESTADO, CUSTODIA, ANALISIS, RESULTADO, CORRECCION, APROBACION, RECHAZO, INTERPRETACION, INFORME
    private String categoria;
    private String titulo;
    private String detalle;
    private String usuario;
    private boolean alerta;   // resalta el hito (fuera de rango, atípico, rechazo...)
}
