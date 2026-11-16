package com.duoc.lims.limsbackend.model.enums;

public enum Prioridad {
    BAJA("Baja"),
    MEDIA("Media"),
    ALTA("Alta");

    private final String valorDb;

    Prioridad(String valorDb) {
        this.valorDb = valorDb;
    }

    public String getValorDb() {
        return valorDb;
    }

    public static Prioridad fromValorDb(String valorDb) {
        for (Prioridad p : values()) {
            if (p.valorDb.equals(valorDb)) return p;
        }
        throw new IllegalArgumentException("Valor no reconocido para Prioridad: " + valorDb);
    }
}