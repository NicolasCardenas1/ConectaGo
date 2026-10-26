package com.duoc.lims.limsclient.model;

/** v5 — Cuerpo de POST/PUT /api/ubicaciones. */
public record UbicacionRequest(String nombre, String tipo, String temperatura, String descripcion) {}
