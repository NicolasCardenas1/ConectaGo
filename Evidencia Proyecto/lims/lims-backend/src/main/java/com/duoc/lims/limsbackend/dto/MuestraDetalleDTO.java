package com.duoc.lims.limsbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class MuestraDetalleDTO {
    private MuestraResponseDTO muestra;
    private List<AnalisisDeMuestraDTO> analisis;
    private List<HistorialEstadoDTO> historial;   // trazabilidad ISO 17025
}