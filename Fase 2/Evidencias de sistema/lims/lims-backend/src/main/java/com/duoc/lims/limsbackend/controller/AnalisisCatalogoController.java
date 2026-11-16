package com.duoc.lims.limsbackend.controller;

import com.duoc.lims.limsbackend.dto.AnalisisCatalogoRequestDTO;
import com.duoc.lims.limsbackend.dto.AnalisisCatalogoResponseDTO;
import com.duoc.lims.limsbackend.service.AnalisisCatalogoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/analisis")
public class AnalisisCatalogoController {

    private final AnalisisCatalogoService analisisService;

    public AnalisisCatalogoController(AnalisisCatalogoService analisisService) {
        this.analisisService = analisisService;
    }

    @GetMapping
    public List<AnalisisCatalogoResponseDTO> listar() {
        return analisisService.listar();
    }

    @GetMapping("/{id}")
    public AnalisisCatalogoResponseDTO obtener(@PathVariable Integer id) {
        return analisisService.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AnalisisCatalogoResponseDTO crear(@Valid @RequestBody AnalisisCatalogoRequestDTO dto) {
        return analisisService.crear(dto);
    }

    @PutMapping("/{id}")
    public AnalisisCatalogoResponseDTO actualizar(@PathVariable Integer id,
                                                  @Valid @RequestBody AnalisisCatalogoRequestDTO dto) {
        return analisisService.actualizar(id, dto);
    }

    @PatchMapping("/{id}/estado")
    public AnalisisCatalogoResponseDTO cambiarEstado(@PathVariable Integer id,
                                                     @RequestParam boolean activo) {
        return analisisService.cambiarEstado(id, activo);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String manejarArgumentoInvalido(IllegalArgumentException ex) {
        return ex.getMessage();
    }
}