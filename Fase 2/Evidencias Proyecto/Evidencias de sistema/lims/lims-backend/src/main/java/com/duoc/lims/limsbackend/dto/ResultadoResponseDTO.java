package com.duoc.lims.limsbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
public class ResultadoResponseDTO {
    private Integer id;
    private Integer idMuestraAnalisis;
    private String nombreAnalisis;
    private String codigoMuestra;
    private BigDecimal valorResultado;
    private String unidadMedida;
    private BigDecimal valorMinNormal;
    private BigDecimal valorMaxNormal;
    private Boolean dentroRango;
    private String instrumentoUtilizado;
    private String usuarioIngreso;
    private LocalDateTime fechaIngreso;
    private String observaciones;
    // ---- v5: trazabilidad del dato ----
    private Integer version;              // versión vigente (1 = sin correcciones)
    private LocalDateTime fechaModificacion;
    private Integer idEquipo;
    private String estadoCalibracionEquipo;
    private boolean anomalo;
    private BigDecimal puntajeAnomalia;
    private String detalleAnomalia;
    private Integer idMuestra;
    private String sitio;                 // código y nombre del sitio de muestreo
}