package com.duoc.lims.limsbackend.model.enums;

/** Tipos de muestra del dominio de investigación ambiental (v4). */
public enum TipoMuestra {
    MACROALGA("Macroalga"),
    MICROALGA("Microalga"),
    AGUA_DE_MAR("Agua de mar"),
    AGUA_DULCE("Agua dulce"),
    SEDIMENTO("Sedimento"),
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
        throw new IllegalArgumentException("Tipo de muestra no válido: " + valorDb
                + " (use Macroalga, Microalga, Agua de mar, Agua dulce, Sedimento u Otro)");
    }
}
