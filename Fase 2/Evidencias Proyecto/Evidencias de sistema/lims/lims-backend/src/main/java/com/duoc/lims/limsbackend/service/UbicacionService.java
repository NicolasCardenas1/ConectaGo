package com.duoc.lims.limsbackend.service;

import com.duoc.lims.limsbackend.dto.UbicacionRequestDTO;
import com.duoc.lims.limsbackend.dto.UbicacionResponseDTO;
import com.duoc.lims.limsbackend.model.UbicacionAlmacenamiento;
import com.duoc.lims.limsbackend.repository.UbicacionAlmacenamientoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** v5 — Ubicaciones físicas de almacenamiento (lista desplegable). */
@Service
public class UbicacionService {

    private final UbicacionAlmacenamientoRepository repository;

    public UbicacionService(UbicacionAlmacenamientoRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<UbicacionResponseDTO> listar(boolean soloActivas) {
        return repository.findAllByOrderByNombreAsc().stream()
                .filter(u -> !soloActivas || u.isActivo())
                .map(this::aDTO)
                .toList();
    }

    @Transactional
    public UbicacionResponseDTO crear(UbicacionRequestDTO dto) {
        if (repository.existsByNombreIgnoreCase(dto.getNombre().trim())) {
            throw new IllegalArgumentException("Ya existe la ubicación " + dto.getNombre().trim());
        }
        UbicacionAlmacenamiento u = new UbicacionAlmacenamiento();
        aplicar(u, dto);
        u.setActivo(true);
        return aDTO(repository.save(u));
    }

    @Transactional
    public UbicacionResponseDTO actualizar(Integer id, UbicacionRequestDTO dto) {
        UbicacionAlmacenamiento u = buscar(id);
        String nombre = dto.getNombre().trim();
        if (!nombre.equalsIgnoreCase(u.getNombre()) && repository.existsByNombreIgnoreCase(nombre)) {
            throw new IllegalArgumentException("Ya existe la ubicación " + nombre);
        }
        aplicar(u, dto);
        return aDTO(repository.save(u));
    }

    @Transactional
    public UbicacionResponseDTO cambiarEstado(Integer id, boolean activo) {
        UbicacionAlmacenamiento u = buscar(id);
        u.setActivo(activo);
        return aDTO(repository.save(u));
    }

    private UbicacionAlmacenamiento buscar(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe la ubicación con id " + id));
    }

    private void aplicar(UbicacionAlmacenamiento u, UbicacionRequestDTO dto) {
        u.setNombre(dto.getNombre().trim());
        u.setTipo(dto.getTipo().trim());
        u.setTemperatura(vacioANull(dto.getTemperatura()));
        u.setDescripcion(vacioANull(dto.getDescripcion()));
    }

    private String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private UbicacionResponseDTO aDTO(UbicacionAlmacenamiento u) {
        return new UbicacionResponseDTO(u.getId(), u.getNombre(), u.getTipo(), u.getTemperatura(),
                u.getDescripcion(), u.isActivo());
    }
}
