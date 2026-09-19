package com.duoc.lims.limsbackend.model.enums;

public enum TipoMuestra {
    AGUA_POTABLE("Agua potable"),
    AGUA_RESIDUAL("Agua residual"),
    SANGRE("Sangre"),
    SUELO("Suelo"),
    ALIMENTO("Alimento"),
    OTRO("Otro");

    private final String valorDb;

    TipoMuestra(String valorDb) {
        this.valorDb = valorDb;
    }

    public String getValorDb() {
        return valorDb;
    }

    public static TipoMuestra fromValorDb(String valorDb) {
        for (TipoMuestra t : values()) {
            if (t.valorDb.equals(valorDb)) return t;
        }
        throw new IllegalArgumentException("Valor no reconocido para TipoMuestra: " + valorDb);
    }
}