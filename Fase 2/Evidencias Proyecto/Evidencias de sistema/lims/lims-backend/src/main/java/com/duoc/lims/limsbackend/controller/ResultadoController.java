package com.duoc.lims.limsbackend.controller;

import com.duoc.lims.limsbackend.dto.AnomaliaDTO;
import com.duoc.lims.limsbackend.dto.CorreccionResultadoDTO;
import com.duoc.lims.limsbackend.dto.ResultadoRequestDTO;
import com.duoc.lims.limsbackend.dto.ResultadoResponseDTO;
import com.duoc.lims.limsbackend.dto.ResultadoVersionDTO;
import com.duoc.lims.limsbackend.service.ResultadoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.duoc.lims.limsbackend.security.UsuarioAutenticado;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/resultados")
public class ResultadoController {

    private final ResultadoService resultadoService;

    public ResultadoController(ResultadoService resultadoService) {
        this.resultadoService = resultadoService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResultadoResponseDTO ingresar(@Valid @RequestBody ResultadoRequestDTO dto,
                                         @AuthenticationPrincipal UsuarioAutenticado yo) {
        dto.setIdUsuarioIngreso(yo.id());   // analista = usuario de la sesión
        return resultadoService.ingresar(dto);
    }

    @GetMapping
    public List<ResultadoResponseDTO> listar(@RequestParam(defaultValue = "false") boolean pendientes) {
        return resultadoService.listar(pendientes);
    }

    /** v5 — Corrige un resultado (por ejemplo, después de un rechazo): crea una versión nueva. */
    @PutMapping("/{id}")
    public ResultadoResponseDTO corregir(@PathVariable Integer id,
                                         @Valid @RequestBody CorreccionResultadoDTO dto,
                                         @AuthenticationPrincipal UsuarioAutenticado yo) {
        dto.setIdUsuario(yo.id());
        return resultadoService.corregir(id, dto);
    }

    /** v5 — Historial de versiones de un resultado con la evaluación de cada una. */
    @GetMapping("/{id}/versiones")
    public List<ResultadoVersionDTO> versiones(@PathVariable Integer id) {
        return resultadoService.versiones(id);
    }

    /** v5 — Vista previa de la detección de anomalías (no guarda nada). */
    @GetMapping("/anomalia")
    public AnomaliaDTO evaluarAnomalia(@RequestParam Integer idMuestraAnalisis,
                                       @RequestParam BigDecimal valor,
                                       @RequestParam(required = false) Integer idResultado) {
        return resultadoService.evaluarAnomalia(idMuestraAnalisis, valor, idResultado);
    }
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String manejarArgumentoInvalido(IllegalArgumentException ex) {
        return ex.getMessage();
    }
}