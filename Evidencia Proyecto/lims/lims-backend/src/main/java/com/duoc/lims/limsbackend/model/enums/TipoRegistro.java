package com.duoc.lims.limsbackend.model.enums;

public enum TipoRegistro {
    MUESTRA("Muestra"),
    BLANCO("Blanco"),
    DUPLICADO("Duplicado"),
    ESTANDAR("Estandar");

    private final String valorDb;

    TipoRegistro(String valorDb) {
        this.valorDb = valorDb;
    }

    public String getValorDb() {
        return valorDb;
    }

    public static TipoRegistro fromValorDb(String valorDb) {
        for (TipoRegistro t : values()) {
            if (t.valorDb.equals(valorDb)) return t;
        }
        throw new IllegalArgumentException("Valor no reconocido para TipoRegistro: " + valorDb);
    }
}