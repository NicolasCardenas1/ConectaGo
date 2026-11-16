package com.duoc.lims.limsbackend.model.enums;

public enum EstadoMuestraAnalisis {
    PENDIENTE("Pendiente"),
    EN_PROCESO("En proceso"),
    COMPLETADO("Completado");

    private final String valorDb;

    EstadoMuestraAnalisis(String valorDb) {
        this.valorDb = valorDb;
    }

    public String getValorDb() {
        return valorDb;
    }

    public static EstadoMuestraAnalisis fromValorDb(String valor) {
        for (EstadoMuestraAnalisis e : values()) {
            if (e.valorDb.equalsIgnoreCase(valor)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Estado de análisis inválido: " + valor);
    }
}