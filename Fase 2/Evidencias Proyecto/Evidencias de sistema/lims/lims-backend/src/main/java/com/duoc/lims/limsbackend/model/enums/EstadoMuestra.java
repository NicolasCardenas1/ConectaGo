package com.duoc.lims.limsbackend.model.enums;

public enum EstadoMuestra {
    RECIBIDA("Recibida"),
    EN_ANALISIS("En analisis"),
    RESULTADOS_INGRESADOS("Resultados ingresados"),
    APROBADA("Aprobada"),
    RECHAZADA("Rechazada"),
    REPORTADA("Reportada");

    private final String valorDb;

    EstadoMuestra(String valorDb) {
        this.valorDb = valorDb;
    }

    public String getValorDb() {
        return valorDb;
    }

    public static EstadoMuestra fromValorDb(String valorDb) {
        for (EstadoMuestra e : values()) {
            if (e.valorDb.equals(valorDb)) return e;
        }
        throw new IllegalArgumentException("Valor no reconocido para EstadoMuestra: " + valorDb);
    }
}