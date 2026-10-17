package com.duoc.lims.limsbackend.controller;

import com.duoc.lims.limsbackend.dto.InvestigadorRequestDTO;
import com.duoc.lims.limsbackend.dto.InvestigadorResponseDTO;
import com.duoc.lims.limsbackend.service.InvestigadorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Investigadores (cualquier usuario con sesión puede consultarlos y registrarlos). */
@RestController
@RequestMapping("/api/investigadores")
public class InvestigadorController {

    private final InvestigadorService service;

    public InvestigadorController(InvestigadorService service) {
        this.service = service;
    }

    @GetMapping
    public List<InvestigadorResponseDTO> listar(@RequestParam(defaultValue = "false") boolean activos) {
        return service.listar(activos);
    }

    @GetMapping("/{id}")
    public InvestigadorResponseDTO obtener(@PathVariable Integer id) {
        return service.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvestigadorResponseDTO crear(@Valid @RequestBody InvestigadorRequestDTO dto) {
        return service.crear(dto);
    }

    @PutMapping("/{id}")
    public InvestigadorResponseDTO actualizar(@PathVariable Integer id, @Valid @RequestBody InvestigadorRequestDTO dto) {
        return service.actualizar(id, dto);
    }

    @PatchMapping("/{id}/estado")
    public InvestigadorResponseDTO cambiarEstado(@PathVariable Integer id, @RequestParam boolean activo) {
        return service.cambiarEstado(id, activo);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String manejarArgumentoInvalido(IllegalArgumentException ex) {
        return ex.getMessage();
    }
}
