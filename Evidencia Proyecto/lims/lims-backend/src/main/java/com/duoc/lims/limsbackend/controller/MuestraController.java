package com.duoc.lims.limsbackend.controller;

import com.duoc.lims.limsbackend.dto.MuestraRequestDTO;
import com.duoc.lims.limsbackend.dto.MuestraResponseDTO;
import com.duoc.lims.limsbackend.service.MuestraService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/muestras")
public class MuestraController {

    private final MuestraService muestraService;

    public MuestraController(MuestraService muestraService) {
        this.muestraService = muestraService;
    }

    @GetMapping
    public List<MuestraResponseDTO> listar() {
        return muestraService.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MuestraResponseDTO crear(@Valid @RequestBody MuestraRequestDTO dto) {
        return muestraService.crear(dto);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String manejarArgumentoInvalido(IllegalArgumentException ex) {
        return ex.getMessage();
    }
}