package com.duoc.lims.limsbackend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ResultadoRequestDTO {

    @NotNull(message = "idMuestraAnalisis es obligatorio")
    private Integer idMuestraAnalisis;

    @NotNull(message = "valorResultado es obligatorio")
    private BigDecimal valorResultado;

    // v5: equipo calibrado con que se midió (obligatorio; reemplaza al texto libre)
    @NotNull(message = "idEquipo es obligatorio: indica con qué equipo se midió")
    private Integer idEquipo;

    // Texto legible del equipo; si viene vacío el backend lo arma desde el equipo.
    private String instrumentoUtilizado;

    // RF06: lo completa el backend con el usuario de la sesión (el valor que envíe el cliente se ignora).
    private Integer idUsuarioIngreso;

    private String observaciones;
}