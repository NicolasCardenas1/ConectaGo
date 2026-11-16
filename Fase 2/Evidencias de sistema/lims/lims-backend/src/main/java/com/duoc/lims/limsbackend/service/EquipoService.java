package com.duoc.lims.limsbackend.service;

import com.duoc.lims.limsbackend.dto.EquipoRequestDTO;
import com.duoc.lims.limsbackend.dto.EquipoResponseDTO;
import com.duoc.lims.limsbackend.model.Equipo;
import com.duoc.lims.limsbackend.repository.EquipoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** v5 — Equipos del laboratorio y el estado de su calibración. */
@Service
public class EquipoService {

    /** Días antes del vencimiento en que la calibración se marca "Por vencer". */
    public static final int DIAS_AVISO_CALIBRACION = 30;

    private final EquipoRepository repository;

    public EquipoService(EquipoRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<EquipoResponseDTO> listar(boolean soloActivos) {
        return repository.findAllByOrderByCodigoAsc().stream()
                .filter(e -> !soloActivos || e.isActivo())
                .map(this::aDTO)
                .toList();
    }

    @Transactional
    public EquipoResponseDTO crear(EquipoRequestDTO dto) {
        String codigo = dto.getCodigo().trim().toUpperCase();
        if (repository.existsByCodigoIgnoreCase(codigo)) {
            throw new IllegalArgumentException("Ya existe un equipo con el código " + codigo);
        }
        Equipo e = new Equipo();
        e.setCodigo(codigo);
        aplicar(e, dto);
        e.setActivo(true);
        return aDTO(repository.save(e));
    }

    @Transactional
    public EquipoResponseDTO actualizar(Integer id, EquipoRequestDTO dto) {
        Equipo e = buscar(id);
        String codigo = dto.getCodigo().trim().toUpperCase();
        if (!codigo.equalsIgnoreCase(e.getCodigo()) && repository.existsByCodigoIgnoreCase(codigo)) {
            throw new IllegalArgumentException("Ya existe un equipo con el código " + codigo);
        }
        e.setCodigo(codigo);
        aplicar(e, dto);
        return aDTO(repository.save(e));
    }

    @Transactional
    public EquipoResponseDTO cambiarEstado(Integer id, boolean activo) {
        Equipo e = buscar(id);
        e.setActivo(activo);
        return aDTO(repository.save(e));
    }

    public Equipo buscar(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe el equipo con id " + id));
    }

    /**
     * Regla de trazabilidad metrológica (ISO/IEC 17025): un resultado solo puede medirse con un
     * equipo activo y con la calibración vigente.
     */
    public Equipo validarParaUso(Integer idEquipo) {
        Equipo e = buscar(idEquipo);
        if (!e.isActivo()) {
            throw new IllegalArgumentException("El equipo " + e.getEtiqueta() + " está dado de baja");
        }
        if (e.isCalibracionVencida()) {
            throw new IllegalArgumentException("El equipo " + e.getEtiqueta()
                    + " tiene la calibración vencida desde el " + e.getFechaProximaCalibracion()
                    + ". Regístrelo calibrado o use otro equipo.");
        }
        return e;
    }

    private void aplicar(Equipo e, EquipoRequestDTO dto) {
        if (dto.getFechaUltimaCalibracion() != null && dto.getFechaProximaCalibracion() != null
                && dto.getFechaProximaCalibracion().isBefore(dto.getFechaUltimaCalibracion())) {
            throw new IllegalArgumentException("La próxima calibración no puede ser anterior a la última");
        }
        e.setNombre(dto.getNombre().trim());
        e.setMarca(vacioANull(dto.getMarca()));
        e.setModelo(vacioANull(dto.getModelo()));
        e.setNumeroSerie(vacioANull(dto.getNumeroSerie()));
        e.setFechaUltimaCalibracion(dto.getFechaUltimaCalibracion());
        e.setFechaProximaCalibracion(dto.getFechaProximaCalibracion());
    }

    private String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Vigente / Por vencer (30 días o menos) / Vencida / Sin registro. */
    public static String estadoCalibracion(Equipo e) {
        if (e.getFechaProximaCalibracion() == null) {
            return "Sin registro";
        }
        long dias = ChronoUnit.DAYS.between(LocalDate.now(), e.getFechaProximaCalibracion());
        if (dias < 0) {
            return "Vencida";
        }
        return dias <= DIAS_AVISO_CALIBRACION ? "Por vencer" : "Vigente";
    }

    public EquipoResponseDTO aDTO(Equipo e) {
        Long dias = e.getFechaProximaCalibracion() == null ? null
                : ChronoUnit.DAYS.between(LocalDate.now(), e.getFechaProximaCalibracion());
        return new EquipoResponseDTO(e.getId(), e.getCodigo(), e.getNombre(), e.getMarca(), e.getModelo(),
                e.getNumeroSerie(), e.getFechaUltimaCalibracion(), e.getFechaProximaCalibracion(), e.isActivo(),
                estadoCalibracion(e), dias);
    }
}
