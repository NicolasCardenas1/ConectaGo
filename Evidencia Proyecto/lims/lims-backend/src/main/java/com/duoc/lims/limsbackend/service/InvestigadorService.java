package com.duoc.lims.limsbackend.service;

import com.duoc.lims.limsbackend.dto.InvestigadorRequestDTO;
import com.duoc.lims.limsbackend.dto.InvestigadorResponseDTO;
import com.duoc.lims.limsbackend.model.Investigador;
import com.duoc.lims.limsbackend.repository.InvestigadorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Investigadores que encargan muestreos y reciben los informes. */
@Service
public class InvestigadorService {

    private final InvestigadorRepository repository;

    public InvestigadorService(InvestigadorRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<InvestigadorResponseDTO> listar(boolean soloActivos) {
        return repository.findAllByOrderByApellidoAscNombreAsc().stream()
                .filter(i -> !soloActivos || i.isActivo())
                .map(this::aDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public InvestigadorResponseDTO obtener(Integer id) {
        return aDTO(buscar(id));
    }

    @Transactional
    public InvestigadorResponseDTO crear(InvestigadorRequestDTO dto) {
        Investigador i = new Investigador();
        aplicar(i, dto);
        i.setActivo(true);
        return aDTO(repository.save(i));
    }

    @Transactional
    public InvestigadorResponseDTO actualizar(Integer id, InvestigadorRequestDTO dto) {
        Investigador i = buscar(id);
        aplicar(i, dto);
        return aDTO(repository.save(i));
    }

    @Transactional
    public InvestigadorResponseDTO cambiarEstado(Integer id, boolean activo) {
        Investigador i = buscar(id);
        i.setActivo(activo);
        return aDTO(repository.save(i));
    }

    private Investigador buscar(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe el investigador con id " + id));
    }

    private void aplicar(Investigador i, InvestigadorRequestDTO dto) {
        i.setNombre(dto.getNombre().trim());
        i.setApellido(dto.getApellido().trim());
        i.setInstitucion(vacioANull(dto.getInstitucion()));
        i.setEmail(vacioANull(dto.getEmail()));
        i.setTelefono(vacioANull(dto.getTelefono()));
        i.setProyecto(vacioANull(dto.getProyecto()));
    }

    private String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    InvestigadorResponseDTO aDTO(Investigador i) {
        return new InvestigadorResponseDTO(i.getId(), i.getNombre(), i.getApellido(), i.getInstitucion(),
                i.getEmail(), i.getTelefono(), i.getProyecto(), i.isActivo());
    }
}
