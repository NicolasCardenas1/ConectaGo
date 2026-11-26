package com.duoc.lims.limsclient.model;

/** v7 — Resultado de verificar un informe firmado (por código o por archivo PDF). */
public record VerificacionInforme(
        boolean autentico,
        String mensaje,
        String numeroInforme,
        String codigoMuestra,
        String emitidoPor,
        String fechaEmision,
        String codigoVerificacion) {
}
