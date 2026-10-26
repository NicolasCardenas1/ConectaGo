package com.duoc.lims.limsbackend.controller;

import com.duoc.lims.limsbackend.dto.UbicacionRequestDTO;
import com.duoc.lims.limsbackend.dto.UbicacionResponseDTO;
import com.duoc.lims.limsbackend.service.UbicacionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** v5 — Ubicaciones de almacenamiento. Consultar: cualquier usuario; crear/editar: Administrador (SecurityConfig). */
@RestController
@RequestMapping("/api/ubicaciones")
public class UbicacionController {

    private final UbicacionService service;

    public UbicacionController(UbicacionService service) {
        this.service = service;
    }

    @GetMapping
    public List<UbicacionResponseDTO> listar(@RequestParam(defaultValue = "false") boolean activas) {
        return service.listar(activas);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UbicacionResponseDTO crear(@Valid @RequestBody UbicacionRequestDTO dto) {
        return service.crear(dto);
    }

    @PutMapping("/{id}")
    public UbicacionResponseDTO actualizar(@PathVariable Integer id, @Valid @RequestBody UbicacionRequestDTO dto) {
        return service.actualizar(id, dto);
    }

    @PatchMapping("/{id}/estado")
    public UbicacionResponseDTO cambiarEstado(@PathVariable Integer id, @RequestParam boolean activo) {
        return service.cambiarEstado(id, activo);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String manejarArgumentoInvalido(IllegalArgumentException ex) {
        return ex.getMessage();
    }
}
