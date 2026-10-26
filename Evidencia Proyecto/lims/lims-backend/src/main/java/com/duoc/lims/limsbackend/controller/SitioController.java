package com.duoc.lims.limsbackend.controller;

import com.duoc.lims.limsbackend.dto.SitioRequestDTO;
import com.duoc.lims.limsbackend.dto.SitioResponseDTO;
import com.duoc.lims.limsbackend.service.SitioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** v5 — Sitios de muestreo georreferenciados (cualquier usuario con sesión puede consultarlos y registrarlos). */
@RestController
@RequestMapping("/api/sitios")
public class SitioController {

    private final SitioService service;

    public SitioController(SitioService service) {
        this.service = service;
    }

    @GetMapping
    public List<SitioResponseDTO> listar(@RequestParam(defaultValue = "false") boolean activos) {
        return service.listar(activos);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SitioResponseDTO crear(@Valid @RequestBody SitioRequestDTO dto) {
        return service.crear(dto);
    }

    @PutMapping("/{id}")
    public SitioResponseDTO actualizar(@PathVariable Integer id, @Valid @RequestBody SitioRequestDTO dto) {
        return service.actualizar(id, dto);
    }

    @PatchMapping("/{id}/estado")
    public SitioResponseDTO cambiarEstado(@PathVariable Integer id, @RequestParam boolean activo) {
        return service.cambiarEstado(id, activo);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String manejarArgumentoInvalido(IllegalArgumentException ex) {
        return ex.getMessage();
    }
}
