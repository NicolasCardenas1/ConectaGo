package com.duoc.lims.limsbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** v5 — Una versión de un resultado con la evaluación que recibió del supervisor (si la tuvo). */
@Getter
@Setter
@AllArgsConstructor
public class ResultadoVersionDTO {
    private Integer version;
    private BigDecimal valorResultado;
    private String unidadMedida;
    private Boolean dentroRango;
    private String equipo;
    private String observaciones;
    private boolean anomalo;
    private String detalleAnomalia;
    private String usuario;
    private LocalDateTime fecha;
    private String motivoCambio;          // null en la versión 1
    private String estadoAprobacion;      // evaluación de ESTA versión (null si no se evaluó)
    private String supervisor;
    private String comentarioAprobacion;
    private LocalDateTime fechaAprobacion;
}
