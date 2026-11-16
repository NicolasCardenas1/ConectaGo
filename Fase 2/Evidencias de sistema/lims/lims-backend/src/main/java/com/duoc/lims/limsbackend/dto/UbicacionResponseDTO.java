package com.duoc.lims.limsbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class UbicacionResponseDTO {
    private Integer id;
    private String nombre;
    private String tipo;
    private String temperatura;
    private String descripcion;
    private boolean activo;
}
