package com.duoc.lims.limsbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** v7 — Resultado de verificar un informe por su código o por el archivo PDF. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class VerificacionInformeDTO {
    private boolean autentico;          // true = el código existe o el archivo es idéntico al emitido
    private String mensaje;             // explicación para el usuario
    private String numeroInforme;       // ej. H-2026-0013-v2 (null si no se encontró)
    private String codigoMuestra;
    private String emitidoPor;          // nombre (rol)
    private LocalDateTime fechaEmision;
    private String codigoVerificacion;
}
