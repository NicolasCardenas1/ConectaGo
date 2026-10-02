package com.duoc.lims.limsbackend.controller;

import com.duoc.lims.limsbackend.dto.ResultadoRequestDTO;
import com.duoc.lims.limsbackend.dto.ResultadoResponseDTO;
import com.duoc.lims.limsbackend.service.ResultadoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/resultados")
public class ResultadoController {

    private final ResultadoService resultadoService;

    public ResultadoController(ResultadoService resultadoService) {
        this.resultadoService = resultadoService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResultadoResponseDTO ingresar(@Valid @RequestBody ResultadoRequestDTO dto) {
        return resultadoService.ingresar(dto);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String manejarArgumentoInvalido(IllegalArgumentException ex) {
        return ex.getMessage();
    }
}