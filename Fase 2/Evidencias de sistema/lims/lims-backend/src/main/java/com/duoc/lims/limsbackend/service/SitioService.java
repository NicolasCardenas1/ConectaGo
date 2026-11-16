package com.duoc.lims.limsbackend.service;

import com.duoc.lims.limsbackend.dto.SitioRequestDTO;
import com.duoc.lims.limsbackend.dto.SitioResponseDTO;
import com.duoc.lims.limsbackend.model.Muestra;
import com.duoc.lims.limsbackend.model.Resultado;
import com.duoc.lims.limsbackend.model.SitioMuestreo;
import com.duoc.lims.limsbackend.repository.MuestraRepository;
import com.duoc.lims.limsbackend.repository.ResultadoRepository;
import com.duoc.lims.limsbackend.repository.SitioMuestreoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** v5 — Sitios de muestreo georreferenciados (catálogo desplegable + mapa). */
@Service
public class SitioService {

    private final SitioMuestreoRepository repository;
    private final MuestraRepository muestraRepository;
    private final ResultadoRepository resultadoRepository;

    public SitioService(SitioMuestreoRepository repository, MuestraRepository muestraRepository,
                        ResultadoRepository resultadoRepository) {
        this.repository = repository;
        this.muestraRepository = muestraRepository;
        this.resultadoRepository = resultadoRepository;
    }

    /** Lista los sitios con un resumen de sus muestras (para pintar el mapa). */
    @Transactional(readOnly = true)
    public List<SitioResponseDTO> listar(boolean soloActivos) {
        Map<Integer, List<Muestra>> muestrasPorSitio = muestraRepository.findAll().stream()
                .filter(m -> m.getSitio() != null)
                .collect(Collectors.groupingBy(m -> m.getSitio().getId()));
        Map<Integer, List<Resultado>> resultadosPorSitio = resultadoRepository.findAll().stream()
                .filter(r -> r.getMuestraAnalisis().getMuestra().getSitio() != null)
                .collect(Collectors.groupingBy(r -> r.getMuestraAnalisis().getMuestra().getSitio().getId()));

        return repository.findAllByOrderByCodigoAsc().stream()
                .filter(s -> !soloActivos || s.isActivo())
                .map(s -> aDTO(s, muestrasPorSitio.getOrDefault(s.getId(), List.of()),
                        resultadosPorSitio.getOrDefault(s.getId(), List.of())))
                .toList();
    }

    @Transactional
    public SitioResponseDTO crear(SitioRequestDTO dto) {
        SitioMuestreo s = new SitioMuestreo();
        String codigo = vacioANull(dto.getCodigo());
        if (codigo == null) {
            codigo = siguienteCodigo();
        } else if (repository.existsByCodigoIgnoreCase(codigo)) {
            throw new IllegalArgumentException("Ya existe un sitio con el código " + codigo);
        }
        s.setCodigo(codigo.toUpperCase());
        aplicar(s, dto);
        s.setActivo(true);
        return aDTO(repository.save(s), List.of(), List.of());
    }

    @Transactional
    public SitioResponseDTO actualizar(Integer id, SitioRequestDTO dto) {
        SitioMuestreo s = buscar(id);
        aplicar(s, dto);
        return aDTO(repository.save(s), List.of(), List.of());
    }

    @Transactional
    public SitioResponseDTO cambiarEstado(Integer id, boolean activo) {
        SitioMuestreo s = buscar(id);
        s.setActivo(activo);
        return aDTO(repository.save(s), List.of(), List.of());
    }

    public SitioMuestreo buscar(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe el sitio de muestreo con id " + id));
    }

    private String siguienteCodigo() {
        int max = repository.findAll().stream()
                .map(SitioMuestreo::getCodigo)
                .filter(c -> c != null && c.matches("ST-\\d+"))
                .mapToInt(c -> Integer.parseInt(c.substring(3)))
                .max().orElse(0);
        return String.format("ST-%03d", max + 1);
    }

    private void aplicar(SitioMuestreo s, SitioRequestDTO dto) {
        s.setNombre(dto.getNombre().trim());
        s.setLatitud(dto.getLatitud());
        s.setLongitud(dto.getLongitud());
        s.setTipoAmbiente(dto.getTipoAmbiente().trim());
        s.setComuna(vacioANull(dto.getComuna()));
        s.setRegion(vacioANull(dto.getRegion()));
        s.setDescripcion(vacioANull(dto.getDescripcion()));
    }

    private String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private SitioResponseDTO aDTO(SitioMuestreo s, List<Muestra> muestras, List<Resultado> resultados) {
        LocalDateTime ultimo = muestras.stream()
                .map(m -> m.getFechaToma() != null ? m.getFechaToma() : m.getFechaRecepcion())
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        long fueraRango = resultados.stream().filter(r -> Boolean.FALSE.equals(r.getDentroRango())).count();
        long anomalos = resultados.stream().filter(Resultado::isAnomalo).count();
        return new SitioResponseDTO(s.getId(), s.getCodigo(), s.getNombre(), s.getLatitud(), s.getLongitud(),
                s.getTipoAmbiente(), s.getComuna(), s.getRegion(), s.getDescripcion(), s.isActivo(),
                muestras.size(), fueraRango, anomalos, ultimo);
    }
}
