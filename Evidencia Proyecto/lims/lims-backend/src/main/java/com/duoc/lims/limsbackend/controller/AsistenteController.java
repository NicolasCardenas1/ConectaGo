package com.duoc.lims.limsbackend.controller;

import com.duoc.lims.limsbackend.dto.AsistenteRequestDTO;
import com.duoc.lims.limsbackend.dto.AsistenteResponseDTO;
import com.duoc.lims.limsbackend.service.AsistenteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** v6 — Chatbot de trazabilidad (solo lectura). Disponible para cualquier usuario con sesión. */
@RestController
@RequestMapping("/api/asistente")
public class AsistenteController {

    private final AsistenteService service;

    public AsistenteController(AsistenteService service) {
        this.service = service;
    }

    @PostMapping
    public AsistenteResponseDTO consultar(@Valid @RequestBody AsistenteRequestDTO dto) {
        return service.consultar(dto);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String manejarArgumentoInvalido(IllegalArgumentException ex) {
        return ex.getMessage();
    }
}
