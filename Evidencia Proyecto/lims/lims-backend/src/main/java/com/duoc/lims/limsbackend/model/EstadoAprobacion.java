package com.duoc.lims.limsbackend.model.enums;

public enum EstadoAprobacion {
    APROBADO("Aprobado"),
    RECHAZADO("Rechazado");

    private final String valorDb;

    EstadoAprobacion(String valorDb) {
        this.valorDb = valorDb;
    }

    public String getValorDb() {
        return valorDb;
    }

    public static EstadoAprobacion fromValorDb(String valor) {
        for (EstadoAprobacion e : values()) {
            if (e.valorDb.equalsIgnoreCase(valor)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Estado de aprobación inválido: " + valor
                + " (use 'Aprobado' o 'Rechazado')");
    }
}