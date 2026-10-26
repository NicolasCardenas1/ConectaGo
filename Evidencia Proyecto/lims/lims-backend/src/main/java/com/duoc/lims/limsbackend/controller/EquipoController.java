package com.duoc.lims.limsbackend.controller;

import com.duoc.lims.limsbackend.dto.EquipoRequestDTO;
import com.duoc.lims.limsbackend.dto.EquipoResponseDTO;
import com.duoc.lims.limsbackend.service.EquipoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** v5 — Equipos y calibración. Consultar: cualquier usuario; crear/editar: Administrador (SecurityConfig). */
@RestController
@RequestMapping("/api/equipos")
public class EquipoController {

    private final EquipoService service;

    public EquipoController(EquipoService service) {
        this.service = service;
    }

    @GetMapping
    public List<EquipoResponseDTO> listar(@RequestParam(defaultValue = "false") boolean activos) {
        return service.listar(activos);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EquipoResponseDTO crear(@Valid @RequestBody EquipoRequestDTO dto) {
        return service.crear(dto);
    }

    @PutMapping("/{id}")
    public EquipoResponseDTO actualizar(@PathVariable Integer id, @Valid @RequestBody EquipoRequestDTO dto) {
        return service.actualizar(id, dto);
    }

    @PatchMapping("/{id}/estado")
    public EquipoResponseDTO cambiarEstado(@PathVariable Integer id, @RequestParam boolean activo) {
        return service.cambiarEstado(id, activo);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String manejarArgumentoInvalido(IllegalArgumentException ex) {
        return ex.getMessage();
    }
}
