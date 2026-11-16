package com.duoc.lims.limsbackend.model.enums;

public enum TipoCentro {
    CLINICO("Clinico"),
    ACUICOLA("Acuicola"),
    AMBIENTAL("Ambiental"),
    UNIVERSITARIO("Universitario"),
    OTRO("Otro");

    private final String valorDb;

    TipoCentro(String valorDb) {
        this.valorDb = valorDb;
    }

    public String getValorDb() {
        return valorDb;
    }

    public static TipoCentro fromValorDb(String valorDb) {
        for (TipoCentro t : values()) {
            if (t.valorDb.equals(valorDb)) return t;
        }
        throw new IllegalArgumentException("Valor no reconocido para TipoCentro: " + valorDb);
    }
}