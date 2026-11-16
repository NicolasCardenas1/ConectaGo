package com.duoc.lims.limsbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
public class CalibracionResponseDTO {
    private Integer id;
    private LocalDate fechaCalibracion;
    private LocalDate fechaVencimiento;
    private String realizadaPor;
    private String certificado;
    private String observaciones;
    private String registradaPor;       // usuario que la ingresó al sistema
    private LocalDateTime fechaRegistro;
    private boolean vigente;            // es la calibración vigente del equipo
}
