package com.duoc.lims.limsbackend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class EquipoRequestDTO {

    @NotBlank(message = "el código del equipo es obligatorio")
    private String codigo;

    @NotBlank(message = "el nombre del equipo es obligatorio")
    private String nombre;

    private String marca;

    private String modelo;

    private String numeroSerie;

    // v6: Terreno / Laboratorio / Ambos (si viene vacío se asume Laboratorio)
    private String uso;

    private LocalDate fechaUltimaCalibracion;

    private LocalDate fechaProximaCalibracion;
}
