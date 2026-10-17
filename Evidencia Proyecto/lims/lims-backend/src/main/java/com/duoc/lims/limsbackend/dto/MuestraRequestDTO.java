package com.duoc.lims.limsbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class MuestraRequestDTO {

    @NotNull(message = "tipoMuestra es obligatorio")
    private String tipoMuestra;

    // Investigador que encarga el muestreo (destinatario del informe).
    @NotNull(message = "idInvestigador es obligatorio")
    private Integer idInvestigador;

    // Sitio / origen del muestreo (se guarda en la columna "procedencia").
    @NotBlank(message = "el sitio de muestreo es obligatorio")
    private String procedencia;

    @NotNull(message = "prioridad es obligatorio")
    private String prioridad;

    private String observaciones;

    // ---- Muestreo y almacenamiento ----
    private LocalDateTime fechaToma;
    private String condicionRecepcion;
    private String ubicacionAlmacenamiento;
    private LocalDate fechaVencimiento;   // conservar hasta

    // ---- Firma de recepción (primer evento de la cadena de custodia) ----
    private String entregadaPor;          // si viene vacío se usa el nombre del investigador
    private Boolean firmaRecepcion;       // true = quien registra confirma que recibió la muestra

    // RF06: lo completa el backend con el usuario de la sesión (el valor que envíe el cliente se ignora).
    private Integer idUsuarioRegistro;
}
