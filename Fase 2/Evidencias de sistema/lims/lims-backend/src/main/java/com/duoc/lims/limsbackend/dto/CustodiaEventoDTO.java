package com.duoc.lims.limsbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
public class CustodiaEventoDTO {
    private Integer id;
    private String tipoEvento;
    private String entregadoPor;
    private String recibidoPor;
    private String ubicacion;
    private String condicion;
    private LocalDateTime fechaHora;
    private String observaciones;
}
