package com.duoc.lims.limsbackend.service;

import com.duoc.lims.limsbackend.dto.AnalisisCatalogoRequestDTO;
import com.duoc.lims.limsbackend.dto.AnalisisCatalogoResponseDTO;
import com.duoc.lims.limsbackend.model.AnalisisCatalogo;
import com.duoc.lims.limsbackend.repository.AnalisisCatalogoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AnalisisCatalogoService {

    private final AnalisisCatalogoRepository analisisRepository;

    public AnalisisCatalogoService(AnalisisCatalogoRepository analisisRepository) {
        this.analisisRepository = analisisRepository;
    }

    @Transactional(readOnly = true)
    public List<AnalisisCatalogoResponseDTO> listar() {
        return analisisRepository.findAll()
                .stream()
                .map(this::aDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public AnalisisCatalogoResponseDTO obtener(Integer id) {
        AnalisisCatalogo analisis = buscarOExcepcion(id);
        return aDTO(analisis);
    }

    @Transactional
    public AnalisisCatalogoResponseDTO crear(AnalisisCatalogoRequestDTO dto) {
        validarRango(dto);

        AnalisisCatalogo analisis = new AnalisisCatalogo();
        aplicar(analisis, dto);
        analisis.setActivo(true);

        AnalisisCatalogo guardado = analisisRepository.save(analisis);
        return aDTO(guardado);
    }

    @Transactional
    public AnalisisCatalogoResponseDTO actualizar(Integer id, AnalisisCatalogoRequestDTO dto) {
        validarRango(dto);

        AnalisisCatalogo analisis = buscarOExcepcion(id);
        aplicar(analisis, dto);

        AnalisisCatalogo guardado = analisisRepository.save(analisis);
        return aDTO(guardado);
    }

    @Transactional
    public AnalisisCatalogoResponseDTO cambiarEstado(Integer id, boolean activo) {
        AnalisisCatalogo analisis = buscarOExcepcion(id);
        analisis.setActivo(activo);
        return aDTO(analisisRepository.save(analisis));
    }

    // ---------- helpers privados ----------

    private AnalisisCatalogo buscarOExcepcion(Integer id) {
        return analisisRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el análisis con id " + id));
    }

    private void validarRango(AnalisisCatalogoRequestDTO dto) {
        if (dto.getValorMinNormal().compareTo(dto.getValorMaxNormal()) > 0) {
            throw new IllegalArgumentException(
                    "valorMinNormal no puede ser mayor que valorMaxNormal");
        }
    }

    private void aplicar(AnalisisCatalogo analisis, AnalisisCatalogoRequestDTO dto) {
        analisis.setNombre(dto.getNombre());
        analisis.setDescripcion(dto.getDescripcion());
        analisis.setUnidadMedida(dto.getUnidadMedida());
        analisis.setMetodoReferencia(dto.getMetodoReferencia());
        analisis.setTiempoEstimadoHrs(dto.getTiempoEstimadoHrs());
        analisis.setValorMinNormal(dto.getValorMinNormal());
        analisis.setValorMaxNormal(dto.getValorMaxNormal());
    }

    private AnalisisCatalogoResponseDTO aDTO(AnalisisCatalogo a) {
        return new AnalisisCatalogoResponseDTO(
                a.getId(),
                a.getNombre(),
                a.getDescripcion(),
                a.getUnidadMedida(),
                a.getMetodoReferencia(),
                a.getTiempoEstimadoHrs(),
                a.getValorMinNormal(),
                a.getValorMaxNormal(),
                a.getActivo()
        );
    }
}