package com.duoc.lims.limsbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Resumen de un informe emitido, para el historial de reportes (RF05). */
@Getter
@Setter
@AllArgsConstructor
public class ReporteResponseDTO {
    private Integer id;
    private String numeroInforme;   // ej. M-2026-0001-v2
    private Integer idMuestra;
    private String codigoMuestra;
    private String tipoMuestra;
    private String investigador;
    private Integer version;
    private String emitidoPor;
    private LocalDateTime fechaGeneracion;
}
