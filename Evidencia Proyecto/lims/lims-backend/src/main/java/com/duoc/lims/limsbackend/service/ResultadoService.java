package com.duoc.lims.limsbackend.service;

import com.duoc.lims.limsbackend.dto.ResultadoRequestDTO;
import com.duoc.lims.limsbackend.dto.ResultadoResponseDTO;
import com.duoc.lims.limsbackend.model.AnalisisCatalogo;
import com.duoc.lims.limsbackend.model.MuestraAnalisis;
import com.duoc.lims.limsbackend.model.Resultado;
import com.duoc.lims.limsbackend.model.Usuario;
import com.duoc.lims.limsbackend.model.enums.EstadoMuestraAnalisis;
import com.duoc.lims.limsbackend.repository.MuestraAnalisisRepository;
import com.duoc.lims.limsbackend.repository.ResultadoRepository;
import com.duoc.lims.limsbackend.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ResultadoService {

    private final ResultadoRepository resultadoRepository;
    private final MuestraAnalisisRepository muestraAnalisisRepository;
    private final UsuarioRepository usuarioRepository;

    public ResultadoService(ResultadoRepository resultadoRepository,
                            MuestraAnalisisRepository muestraAnalisisRepository,
                            UsuarioRepository usuarioRepository) {
        this.resultadoRepository = resultadoRepository;
        this.muestraAnalisisRepository = muestraAnalisisRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public ResultadoResponseDTO ingresar(ResultadoRequestDTO dto) {
        MuestraAnalisis ma = muestraAnalisisRepository.findById(dto.getIdMuestraAnalisis())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe la solicitud de análisis (muestra_analisis) con id "
                                + dto.getIdMuestraAnalisis()));

        if (resultadoRepository.existsByMuestraAnalisis_Id(dto.getIdMuestraAnalisis())) {
            throw new IllegalArgumentException(
                    "Ese análisis solicitado ya tiene un resultado ingresado");
        }

        Usuario usuario = usuarioRepository.findById(dto.getIdUsuarioIngreso())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el usuario con id " + dto.getIdUsuarioIngreso()));

        // --- RF03: validación automática de rango ---
        AnalisisCatalogo analisis = ma.getAnalisis();
        boolean dentroRango = estaDentroDeRango(
                dto.getValorResultado(),
                analisis.getValorMinNormal(),
                analisis.getValorMaxNormal());

        // Regla ISO 17025 (CHECK del esquema): un resultado fuera de rango
        // exige justificación en observaciones.
        if (!dentroRango && (dto.getObservaciones() == null || dto.getObservaciones().isBlank())) {
            throw new IllegalArgumentException(
                    "El resultado está fuera del rango normal (" + analisis.getValorMinNormal()
                            + " - " + analisis.getValorMaxNormal()
                            + "); debe justificarlo en 'observaciones'");
        }

        Resultado resultado = new Resultado();
        resultado.setMuestraAnalisis(ma);
        resultado.setValorResultado(dto.getValorResultado());
        resultado.setDentroRango(dentroRango);
        resultado.setInstrumentoUtilizado(dto.getInstrumentoUtilizado());
        resultado.setUsuarioIngreso(usuario);
        resultado.setObservaciones(dto.getObservaciones());

        Resultado guardado = resultadoRepository.save(resultado);

        // Al ingresar el resultado, el análisis solicitado pasa a "Completado".
        ma.setEstado(EstadoMuestraAnalisis.COMPLETADO);
        ma.setFechaCompletado(LocalDateTime.now());
        muestraAnalisisRepository.save(ma);

        return aDTO(guardado);
    }

    @Transactional(readOnly = true)
    public List<ResultadoResponseDTO> listar(boolean soloPendientes) {
        List<Resultado> resultados = soloPendientes
                ? resultadoRepository.findPendientesDeAprobacion()
                : resultadoRepository.findAll();
        return resultados.stream().map(this::aDTO).toList();
    }

    private boolean estaDentroDeRango(BigDecimal valor, BigDecimal min, BigDecimal max) {
        return valor.compareTo(min) >= 0 && valor.compareTo(max) <= 0;
    }

    public ResultadoResponseDTO aDTO(Resultado r) {
        MuestraAnalisis ma = r.getMuestraAnalisis();
        AnalisisCatalogo analisis = ma.getAnalisis();

        return new ResultadoResponseDTO(
                r.getId(),
                ma.getId(),
                analisis.getNombre(),
                ma.getMuestra().getCodigoUnico(),
                r.getValorResultado(),
                analisis.getUnidadMedida(),
                analisis.getValorMinNormal(),
                analisis.getValorMaxNormal(),
                r.getDentroRango(),
                r.getInstrumentoUtilizado(),
                r.getUsuarioIngreso().getNombre() + " " + r.getUsuarioIngreso().getApellido(),
                r.getFechaIngreso(),
                r.getObservaciones()
        );
    }
}