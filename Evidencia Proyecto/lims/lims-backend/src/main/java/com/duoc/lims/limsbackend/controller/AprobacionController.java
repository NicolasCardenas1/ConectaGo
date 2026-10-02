package com.duoc.lims.limsbackend.controller;

import com.duoc.lims.limsbackend.dto.AprobacionRequestDTO;
import com.duoc.lims.limsbackend.dto.AprobacionResponseDTO;
import com.duoc.lims.limsbackend.service.AprobacionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/aprobaciones")
public class AprobacionController {

    private final AprobacionService aprobacionService;

    public AprobacionController(AprobacionService aprobacionService) {
        this.aprobacionService = aprobacionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AprobacionResponseDTO evaluar(@Valid @RequestBody AprobacionRequestDTO dto) {
        return aprobacionService.evaluar(dto);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String manejarArgumentoInvalido(IllegalArgumentException ex) {
        return ex.getMessage();
    }
}