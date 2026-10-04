package com.duoc.lims.limsbackend.service;

import com.duoc.lims.limsbackend.dto.AprobacionRequestDTO;
import com.duoc.lims.limsbackend.dto.AprobacionResponseDTO;
import com.duoc.lims.limsbackend.model.Aprobacion;
import com.duoc.lims.limsbackend.model.Muestra;
import com.duoc.lims.limsbackend.model.MuestraAnalisis;
import com.duoc.lims.limsbackend.model.Resultado;
import com.duoc.lims.limsbackend.model.Usuario;
import com.duoc.lims.limsbackend.model.enums.EstadoAprobacion;
import com.duoc.lims.limsbackend.model.enums.EstadoMuestra;
import com.duoc.lims.limsbackend.model.enums.EstadoMuestraAnalisis;
import com.duoc.lims.limsbackend.repository.AprobacionRepository;
import com.duoc.lims.limsbackend.repository.MuestraAnalisisRepository;
import com.duoc.lims.limsbackend.repository.ResultadoRepository;
import com.duoc.lims.limsbackend.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** RF04 — Aprobación o rechazo de resultados por un supervisor. */
@Service
public class AprobacionService {

    /** Roles que pueden aprobar/rechazar resultados (decisión del equipo). */
    private static final Set<String> ROLES_APROBADORES = Set.of("Supervisor", "Administrador");

    private final AprobacionRepository aprobacionRepository;
    private final ResultadoRepository resultadoRepository;
    private final UsuarioRepository usuarioRepository;
    private final MuestraAnalisisRepository muestraAnalisisRepository;
    private final EstadoMuestraService estadoMuestraService;

    public AprobacionService(AprobacionRepository aprobacionRepository,
                             ResultadoRepository resultadoRepository,
                             UsuarioRepository usuarioRepository,
                             MuestraAnalisisRepository muestraAnalisisRepository,
                             EstadoMuestraService estadoMuestraService) {
        this.aprobacionRepository = aprobacionRepository;
        this.resultadoRepository = resultadoRepository;
        this.usuarioRepository = usuarioRepository;
        this.muestraAnalisisRepository = muestraAnalisisRepository;
        this.estadoMuestraService = estadoMuestraService;
    }

    @Transactional
    public AprobacionResponseDTO evaluar(AprobacionRequestDTO dto) {
        Resultado resultado = resultadoRepository.findById(dto.getIdResultado())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el resultado con id " + dto.getIdResultado()));

        Usuario supervisor = usuarioRepository.findById(dto.getIdSupervisor())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el usuario (supervisor) con id " + dto.getIdSupervisor()));

        validarQuePuedeEvaluar(supervisor, resultado);

        // Un resultado se evalúa una sola vez (evita aprobaciones duplicadas).
        if (aprobacionRepository.existsByResultado_Id(resultado.getId())) {
            throw new IllegalArgumentException("Este resultado ya fue evaluado anteriormente");
        }

        EstadoAprobacion estado = EstadoAprobacion.fromValorDb(dto.getEstadoAprobacion());

        // Regla ISO 17025 (CHECK del esquema): un rechazo exige motivo.
        if (estado == EstadoAprobacion.RECHAZADO
                && (dto.getComentario() == null || dto.getComentario().isBlank())) {
            throw new IllegalArgumentException(
                    "Un rechazo debe incluir un comentario que justifique el motivo");
        }

        Aprobacion aprobacion = new Aprobacion();
        aprobacion.setResultado(resultado);
        aprobacion.setSupervisor(supervisor);
        aprobacion.setEstadoAprobacion(estado);
        aprobacion.setComentario(dto.getComentario());
        Aprobacion guardada = aprobacionRepository.save(aprobacion);

        // --- Avanzar el estado de la muestra según la evaluación ---
        Muestra muestra = resultado.getMuestraAnalisis().getMuestra();
        actualizarEstadoMuestra(muestra, resultado, estado, supervisor, dto.getComentario());

        return aDTO(guardada, muestra);
    }

    // ---------- reglas ----------

    private void validarQuePuedeEvaluar(Usuario supervisor, Resultado resultado) {
        if (!supervisor.isActivo()) {
            throw new IllegalArgumentException("El usuario evaluador está inactivo");
        }

        String rol = supervisor.getRol().getNombreRol();
        if (!ROLES_APROBADORES.contains(rol)) {
            throw new IllegalArgumentException(
                    "Solo un Supervisor o Administrador puede aprobar o rechazar resultados (tu rol: " + rol + ")");
        }

        // Segregación de funciones (ISO 17025): quien ingresa un resultado no puede aprobarlo.
        if (resultado.getUsuarioIngreso().getId().equals(supervisor.getId())) {
            throw new IllegalArgumentException(
                    "No puedes evaluar un resultado que tú mismo ingresaste");
        }
    }

    private void actualizarEstadoMuestra(Muestra muestra, Resultado resultado, EstadoAprobacion estado,
                                         Usuario supervisor, String comentario) {
        String analisis = resultado.getMuestraAnalisis().getAnalisis().getNombre();

        if (estado == EstadoAprobacion.RECHAZADO) {
            // Un solo rechazo deja la muestra como Rechazada.
            // (El flujo de reingreso tras un rechazo queda pendiente por decisión del equipo.)
            estadoMuestraService.cambiarEstado(muestra, EstadoMuestra.RECHAZADA, supervisor,
                    "Rechazado " + analisis + ": " + comentario);
        } else if (todosLosAnalisisAprobados(muestra)) {
            estadoMuestraService.cambiarEstado(muestra, EstadoMuestra.APROBADA, supervisor,
                    "Todos los análisis aprobados");
        }
        // Si aún faltan análisis por aprobar, la muestra conserva su estado actual.
    }

    private boolean todosLosAnalisisAprobados(Muestra muestra) {
        List<MuestraAnalisis> analisis = muestraAnalisisRepository.findByMuestra_Id(muestra.getId());
        if (analisis.isEmpty()) {
            return false;
        }
        for (MuestraAnalisis ma : analisis) {
            if (ma.getEstado() != EstadoMuestraAnalisis.COMPLETADO || !resultadoAprobado(ma)) {
                return false;
            }
        }
        return true;
    }

    private boolean resultadoAprobado(MuestraAnalisis ma) {
        Optional<Resultado> resultado = resultadoRepository.findByMuestraAnalisis_Id(ma.getId());
        if (resultado.isEmpty()) {
            return false;
        }
        // La última evaluación registrada es la que manda.
        return aprobacionRepository.findTopByResultado_IdOrderByIdDesc(resultado.get().getId())
                .map(a -> a.getEstadoAprobacion() == EstadoAprobacion.APROBADO)
                .orElse(false);
    }

    private AprobacionResponseDTO aDTO(Aprobacion a, Muestra muestra) {
        Resultado r = a.getResultado();
        return new AprobacionResponseDTO(
                a.getId(),
                r.getId(),
                r.getMuestraAnalisis().getMuestra().getCodigoUnico(),
                r.getMuestraAnalisis().getAnalisis().getNombre(),
                a.getSupervisor().getNombre() + " " + a.getSupervisor().getApellido(),
                a.getEstadoAprobacion().getValorDb(),
                a.getComentario(),
                a.getFechaAprobacion(),
                muestra.getEstado().getValorDb()
        );
    }
}
