package com.duoc.lims.limsbackend.model.enums;

/** Eventos de la cadena de custodia de una muestra. */
public enum TipoEventoCustodia {
    RECEPCION("Recepción en laboratorio"),
    TRASLADO("Traslado"),
    RETIRO_ANALISIS("Retiro para análisis"),
    DEVOLUCION_ALMACENAMIENTO("Devolución a almacenamiento"),
    DEVOLUCION_INVESTIGADOR("Devolución al investigador");

    private final String valorDb;

    TipoEventoCustodia(String valorDb) {
        this.valorDb = valorDb;
    }

    public String getValorDb() {
        return valorDb;
    }

    public static TipoEventoCustodia fromValorDb(String valorDb) {
        for (TipoEventoCustodia t : values()) {
            if (t.valorDb.equalsIgnoreCase(valorDb)) return t;
        }
        throw new IllegalArgumentException("Tipo de evento de custodia no válido: " + valorDb);
    }
}
