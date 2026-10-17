package com.duoc.lims.limsbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
public class MuestraResponseDTO {
    private Integer id;
    private String codigoUnico;
    private String tipoMuestra;
    private String procedencia;          // sitio de muestreo
    private String prioridad;
    private String estado;
    private LocalDateTime fechaRecepcion;
    private String usuarioRegistro;
    private String observaciones;
    // v4 — investigador y almacenamiento
    private Integer idInvestigador;
    private String investigador;         // nombre completo
    private String institucion;
    private LocalDateTime fechaToma;
    private String condicionRecepcion;
    private String ubicacionAlmacenamiento;
    private LocalDate fechaVencimiento;
}
