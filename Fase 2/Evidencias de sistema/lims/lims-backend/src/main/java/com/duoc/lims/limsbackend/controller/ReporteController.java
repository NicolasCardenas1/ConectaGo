package com.duoc.lims.limsbackend.controller;

import com.duoc.lims.limsbackend.dto.ReporteResponseDTO;
import com.duoc.lims.limsbackend.service.ReporteConsultaService;
import com.duoc.lims.limsbackend.service.ReporteConsultaService.PdfGuardado;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * RF05 — Historial de informes emitidos.
 * GET /api/reportes          lista los informes ya emitidos (más recientes primero).
 * GET /api/reportes/{id}/pdf re-descarga el PDF guardado (sin regenerar ni subir version).
 */
@RestController
@RequestMapping("/api/reportes")
public class ReporteController {

    private final ReporteConsultaService reporteConsultaService;

    public ReporteController(ReporteConsultaService reporteConsultaService) {
        this.reporteConsultaService = reporteConsultaService;
    }

    @GetMapping
    public List<ReporteResponseDTO> listar() {
        return reporteConsultaService.listar();
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> descargar(@PathVariable Integer id) {
        PdfGuardado pdf = reporteConsultaService.obtenerPdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + pdf.nombreArchivo() + "\"")
                .body(pdf.pdf());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String manejarArgumentoInvalido(IllegalArgumentException ex) {
        return ex.getMessage();
    }
}
