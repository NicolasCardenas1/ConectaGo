package com.duoc.lims.limsbackend.controller;

import com.duoc.lims.limsbackend.dto.DashboardDTO;
import com.duoc.lims.limsbackend.service.DashboardService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** v5 — Panel de inicio (cualquier usuario con sesión). */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping
    public DashboardDTO resumen() {
        return service.resumen();
    }

    /** Serie de un parámetro en el tiempo; sin idSitio incluye todos los sitios. */
    @GetMapping("/tendencia")
    public DashboardDTO.Tendencia tendencia(@RequestParam Integer idAnalisis,
                                            @RequestParam(required = false) Integer idSitio) {
        return service.tendencia(idAnalisis, idSitio);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String manejarArgumentoInvalido(IllegalArgumentException ex) {
        return ex.getMessage();
    }
}
