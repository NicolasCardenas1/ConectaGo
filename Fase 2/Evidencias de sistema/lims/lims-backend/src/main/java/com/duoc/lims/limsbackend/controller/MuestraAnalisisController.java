package com.duoc.lims.limsbackend.controller;

import com.duoc.lims.limsbackend.dto.MuestraAnalisisRequestDTO;
import com.duoc.lims.limsbackend.dto.MuestraAnalisisResponseDTO;
import com.duoc.lims.limsbackend.service.MuestraAnalisisService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/muestra-analisis")
public class MuestraAnalisisController {

    private final MuestraAnalisisService muestraAnalisisService;

    public MuestraAnalisisController(MuestraAnalisisService muestraAnalisisService) {
        this.muestraAnalisisService = muestraAnalisisService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MuestraAnalisisResponseDTO asignar(@Valid @RequestBody MuestraAnalisisRequestDTO dto) {
        return muestraAnalisisService.asignar(dto);
    }

    @GetMapping
    public List<MuestraAnalisisResponseDTO> listarPorMuestra(@RequestParam Integer idMuestra) {
        return muestraAnalisisService.listarPorMuestra(idMuestra);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String manejarArgumentoInvalido(IllegalArgumentException ex) {
        return ex.getMessage();
    }
}