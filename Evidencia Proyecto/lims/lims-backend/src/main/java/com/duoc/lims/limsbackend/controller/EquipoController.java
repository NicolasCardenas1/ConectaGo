package com.duoc.lims.limsbackend.controller;

import com.duoc.lims.limsbackend.dto.CalibracionRequestDTO;
import com.duoc.lims.limsbackend.dto.CalibracionResponseDTO;
import com.duoc.lims.limsbackend.dto.EquipoRequestDTO;
import com.duoc.lims.limsbackend.security.UsuarioAutenticado;
import com.duoc.lims.limsbackend.dto.EquipoResponseDTO;
import com.duoc.lims.limsbackend.service.EquipoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** v5 — Equipos y calibración. Consultar: cualquier usuario; crear/editar/calibrar: Administrador (SecurityConfig). */
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
    public EquipoResponseDTO crear(@Valid @RequestBody EquipoRequestDTO dto,
                                   @AuthenticationPrincipal UsuarioAutenticado yo) {
        return service.crear(dto, yo == null ? null : yo.id());
    }

    @PutMapping("/{id}")
    public EquipoResponseDTO actualizar(@PathVariable Integer id, @Valid @RequestBody EquipoRequestDTO dto,
                                        @AuthenticationPrincipal UsuarioAutenticado yo) {
        return service.actualizar(id, dto, yo == null ? null : yo.id());
    }

    /** v6 — Historial de calibraciones del equipo (la más reciente primero). */
    @GetMapping("/{id}/calibraciones")
    public List<CalibracionResponseDTO> calibraciones(@PathVariable Integer id) {
        return service.listarCalibraciones(id);
    }

    /** v6 — Registra una calibración nueva (Administrador). No borra las anteriores. */
    @PostMapping("/{id}/calibraciones")
    @ResponseStatus(HttpStatus.CREATED)
    public CalibracionResponseDTO registrarCalibracion(@PathVariable Integer id,
                                                      @Valid @RequestBody CalibracionRequestDTO dto,
                                                      @AuthenticationPrincipal UsuarioAutenticado yo) {
        return service.registrarCalibracion(id, dto, yo == null ? null : yo.id());
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
