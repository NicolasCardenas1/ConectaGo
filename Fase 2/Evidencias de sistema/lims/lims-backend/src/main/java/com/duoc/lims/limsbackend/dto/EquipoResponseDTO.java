package com.duoc.lims.limsbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
public class EquipoResponseDTO {
    private Integer id;
    private String codigo;
    private String nombre;
    private String marca;
    private String modelo;
    private String numeroSerie;
    private LocalDate fechaUltimaCalibracion;
    private LocalDate fechaProximaCalibracion;
    private boolean activo;
    private String estadoCalibracion;   // Vigente / Por vencer / Vencida / Sin registro
    private Long diasParaVencer;        // negativo si ya venció; null sin fecha
}
