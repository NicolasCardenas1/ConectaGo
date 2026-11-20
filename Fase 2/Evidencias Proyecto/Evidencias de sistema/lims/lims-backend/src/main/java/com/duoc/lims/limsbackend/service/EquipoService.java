package com.duoc.lims.limsbackend.service;

import com.duoc.lims.limsbackend.dto.CalibracionRequestDTO;
import com.duoc.lims.limsbackend.dto.CalibracionResponseDTO;
import com.duoc.lims.limsbackend.dto.EquipoRequestDTO;
import com.duoc.lims.limsbackend.dto.EquipoResponseDTO;
import com.duoc.lims.limsbackend.model.Equipo;
import com.duoc.lims.limsbackend.model.EquipoCalibracion;
import com.duoc.lims.limsbackend.repository.EquipoCalibracionRepository;
import com.duoc.lims.limsbackend.repository.EquipoRepository;
import com.duoc.lims.limsbackend.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * v5 — Equipos del laboratorio y el estado de su calibración.
 * v6 — Equipos de terreno (uso) e historial de calibraciones: cada calibración es una fila nueva en
 * equipo_calibraciones; las fechas del equipo reflejan siempre la calibración vigente.
 */
@Service
public class EquipoService {

    /** Días antes del vencimiento en que la calibración se marca "Por vencer". */
    public static final int DIAS_AVISO_CALIBRACION = 30;

    public static final Set<String> USOS = Set.of(Equipo.USO_TERRENO, Equipo.USO_LABORATORIO, Equipo.USO_AMBOS);

    private final EquipoRepository repository;
    private final EquipoCalibracionRepository calibracionRepository;
    private final UsuarioRepository usuarioRepository;

    public EquipoService(EquipoRepository repository, EquipoCalibracionRepository calibracionRepository,
                         UsuarioRepository usuarioRepository) {
        this.repository = repository;
        this.calibracionRepository = calibracionRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public List<EquipoResponseDTO> listar(boolean soloActivos) {
        return repository.findAllByOrderByCodigoAsc().stream()
                .filter(e -> !soloActivos || e.isActivo())
                .map(this::aDTO)
                .toList();
    }

    @Transactional
    public EquipoResponseDTO crear(EquipoRequestDTO dto, Integer idUsuario) {
        String codigo = dto.getCodigo().trim().toUpperCase();
        if (repository.existsByCodigoIgnoreCase(codigo)) {
            throw new IllegalArgumentException("Ya existe un equipo con el código " + codigo);
        }
        Equipo e = new Equipo();
        e.setCodigo(codigo);
        aplicar(e, dto);
        e.setActivo(true);
        Equipo guardado = repository.save(e);
        registrarEnHistorialSiCambio(guardado, null, null, idUsuario, "Calibración registrada al dar de alta el equipo");
        return aDTO(guardado);
    }

    @Transactional
    public EquipoResponseDTO actualizar(Integer id, EquipoRequestDTO dto, Integer idUsuario) {
        Equipo e = buscar(id);
        String codigo = dto.getCodigo().trim().toUpperCase();
        if (!codigo.equalsIgnoreCase(e.getCodigo()) && repository.existsByCodigoIgnoreCase(codigo)) {
            throw new IllegalArgumentException("Ya existe un equipo con el código " + codigo);
        }
        LocalDate ultimaAntes = e.getFechaUltimaCalibracion();
        LocalDate proximaAntes = e.getFechaProximaCalibracion();
        e.setCodigo(codigo);
        aplicar(e, dto);
        Equipo guardado = repository.save(e);
        // v6: si cambiaron las fechas, la calibración nueva queda en el historial (no se pierde la anterior)
        registrarEnHistorialSiCambio(guardado, ultimaAntes, proximaAntes, idUsuario,
                "Calibración registrada al editar el equipo");
        return aDTO(guardado);
    }

    // ---------- v6: historial de calibraciones ----------

    @Transactional(readOnly = true)
    public List<CalibracionResponseDTO> listarCalibraciones(Integer idEquipo) {
        Equipo e = buscar(idEquipo);
        List<EquipoCalibracion> historial = calibracionRepository.findByEquipo_IdOrderByFechaCalibracionDescIdDesc(idEquipo);
        Integer idVigente = historial.isEmpty() ? null : historial.get(0).getId();
        return historial.stream().map(c -> calibracionADTO(c, Objects.equals(c.getId(), idVigente)
                && Objects.equals(c.getFechaVencimiento(), e.getFechaProximaCalibracion()))).toList();
    }

    /**
     * Registra una calibración nueva. Si es la más reciente, pasa a ser la vigente del equipo
     * (actualiza sus fechas); si es una calibración antigua que se ingresa tarde, solo queda en el historial.
     */
    @Transactional
    public CalibracionResponseDTO registrarCalibracion(Integer idEquipo, CalibracionRequestDTO dto, Integer idUsuario) {
        Equipo e = buscar(idEquipo);
        if (dto.getFechaVencimiento().isBefore(dto.getFechaCalibracion())) {
            throw new IllegalArgumentException("El vencimiento no puede ser anterior a la fecha de calibración");
        }
        if (dto.getFechaCalibracion().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha de calibración no puede estar en el futuro");
        }
        EquipoCalibracion c = nuevaCalibracion(e, dto.getFechaCalibracion(), dto.getFechaVencimiento(), idUsuario);
        c.setRealizadaPor(vacioANull(dto.getRealizadaPor()));
        c.setCertificado(vacioANull(dto.getCertificado()));
        c.setObservaciones(vacioANull(dto.getObservaciones()));
        EquipoCalibracion guardada = calibracionRepository.save(c);

        boolean esLaMasReciente = e.getFechaUltimaCalibracion() == null
                || !dto.getFechaCalibracion().isBefore(e.getFechaUltimaCalibracion());
        if (esLaMasReciente) {
            e.setFechaUltimaCalibracion(dto.getFechaCalibracion());
            e.setFechaProximaCalibracion(dto.getFechaVencimiento());
            repository.save(e);
        }
        return calibracionADTO(guardada, esLaMasReciente);
    }

    /** Calibración vigente en una fecha dada (para responder "¿estaba calibrado cuando se usó?"). */
    @Transactional(readOnly = true)
    public java.util.Optional<EquipoCalibracion> calibracionVigenteEn(Integer idEquipo, LocalDate fecha) {
        return calibracionRepository
                .findTopByEquipo_IdAndFechaCalibracionLessThanEqualOrderByFechaCalibracionDescIdDesc(idEquipo, fecha);
    }

    private void registrarEnHistorialSiCambio(Equipo e, LocalDate ultimaAntes, LocalDate proximaAntes,
                                              Integer idUsuario, String observacion) {
        if (e.getFechaUltimaCalibracion() == null || e.getFechaProximaCalibracion() == null) {
            return;
        }
        if (Objects.equals(ultimaAntes, e.getFechaUltimaCalibracion())
                && Objects.equals(proximaAntes, e.getFechaProximaCalibracion())) {
            return;
        }
        EquipoCalibracion c = nuevaCalibracion(e, e.getFechaUltimaCalibracion(), e.getFechaProximaCalibracion(), idUsuario);
        c.setObservaciones(observacion);
        calibracionRepository.save(c);
    }

    private EquipoCalibracion nuevaCalibracion(Equipo e, LocalDate fecha, LocalDate vencimiento, Integer idUsuario) {
        EquipoCalibracion c = new EquipoCalibracion();
        c.setEquipo(e);
        c.setFechaCalibracion(fecha);
        c.setFechaVencimiento(vencimiento);
        if (idUsuario != null) {
            usuarioRepository.findById(idUsuario).ifPresent(c::setUsuario);
        }
        return c;
    }

    private CalibracionResponseDTO calibracionADTO(EquipoCalibracion c, boolean vigente) {
        String usuario = c.getUsuario() == null ? null
                : c.getUsuario().getNombre() + " " + c.getUsuario().getApellido();
        return new CalibracionResponseDTO(c.getId(), c.getFechaCalibracion(), c.getFechaVencimiento(),
                c.getRealizadaPor(), c.getCertificado(), c.getObservaciones(), usuario, c.getFechaRegistro(), vigente);
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

    /** v6: equipo para medir un resultado en el laboratorio (activo, calibrado y de uso Laboratorio/Ambos). */
    public Equipo validarParaAnalisis(Integer idEquipo) {
        Equipo e = validarParaUso(idEquipo);
        if (!e.isUsoLaboratorio()) {
            throw new IllegalArgumentException("El equipo " + e.getEtiqueta()
                    + " es de terreno: no se puede usar para medir resultados en el laboratorio");
        }
        return e;
    }

    /** v6: equipo con que se toma una muestra en terreno (activo, calibrado y de uso Terreno/Ambos). */
    public Equipo validarParaToma(Integer idEquipo) {
        Equipo e = validarParaUso(idEquipo);
        if (!e.isUsoTerreno()) {
            throw new IllegalArgumentException("El equipo " + e.getEtiqueta()
                    + " es de laboratorio: no se puede registrar como equipo de toma en terreno");
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
        String uso = vacioANull(dto.getUso());
        if (uso == null) {
            uso = e.getUso() == null ? Equipo.USO_LABORATORIO : e.getUso();
        } else if (!USOS.contains(uso)) {
            throw new IllegalArgumentException("Uso no válido: use Terreno, Laboratorio o Ambos");
        }
        e.setUso(uso);
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
                estadoCalibracion(e), dias, e.getUso());
    }
}
