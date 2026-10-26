package com.duoc.lims.limsbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
public class SitioResponseDTO {
    private Integer id;
    private String codigo;
    private String nombre;
    private BigDecimal latitud;
    private BigDecimal longitud;
    private String tipoAmbiente;
    private String comuna;
    private String region;
    private String descripcion;
    private boolean activo;
    // Resumen para el mapa
    private long totalMuestras;
    private long resultadosFueraRango;
    private long resultadosAnomalos;
    private LocalDateTime ultimoMuestreo;
}
