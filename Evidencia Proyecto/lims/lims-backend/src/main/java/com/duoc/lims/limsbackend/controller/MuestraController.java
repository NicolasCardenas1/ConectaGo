package com.duoc.lims.limsbackend.controller;

import com.duoc.lims.limsbackend.dto.MuestraRequestDTO;
import com.duoc.lims.limsbackend.dto.MuestraResponseDTO;
import com.duoc.lims.limsbackend.service.MuestraService;
import com.duoc.lims.limsbackend.service.ReportePdfService;
import com.duoc.lims.limsbackend.service.ReportePdfService.ReporteGenerado;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.duoc.lims.limsbackend.dto.MuestraDetalleDTO;

import java.util.List;

@RestController
@RequestMapping("/api/muestras")
public class MuestraController {

    private final MuestraService muestraService;
    private final ReportePdfService reportePdfService;

    public MuestraController(MuestraService muestraService, ReportePdfService reportePdfService) {
        this.muestraService = muestraService;
        this.reportePdfService = reportePdfService;
    }

    @GetMapping
    public List<MuestraResponseDTO> listar() {
        return muestraService.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MuestraResponseDTO crear(@Valid @RequestBody MuestraRequestDTO dto) {
        return muestraService.crear(dto);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String manejarArgumentoInvalido(IllegalArgumentException ex) {
        return ex.getMessage();
    }

    /**
     * RF05 — Emite el informe PDF de una muestra aprobada.
     * Devuelve el PDF; la muestra pasa a "Reportada" y queda registrado en la tabla reportes.
     */
    @PostMapping("/{id}/reporte")
    public ResponseEntity<byte[]> generarReporte(@PathVariable Integer id, @RequestParam Integer idUsuario) {
        ReporteGenerado reporte = reportePdfService.generar(id, idUsuario);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + reporte.nombreArchivo() + "\"")
                .header("X-Reporte-Version", String.valueOf(reporte.version()))
                .body(reporte.pdf());
    }

    @GetMapping("/{id}")
    public MuestraDetalleDTO obtenerDetalle(@PathVariable Integer id) {
        return muestraService.obtenerDetalle(id);
    }
}