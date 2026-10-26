package com.duoc.lims.limsbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
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
    // v5 — sitio georreferenciado e interpretación del informe
    private Integer idSitio;
    private String sitioCodigo;
    private String sitioNombre;
    private BigDecimal latitud;
    private BigDecimal longitud;
    private String interpretacion;
    private String interpretacionOrigen;
    private String interpretacionPor;
    private LocalDateTime fechaInterpretacion;
}
