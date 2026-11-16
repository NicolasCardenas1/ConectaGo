package com.duoc.lims.limsclient.model;

/** v5 — Interpretación revisada que guarda el supervisor (PUT /api/muestras/{id}/interpretacion). */
public record InterpretacionRequest(String texto, String origen) {}
