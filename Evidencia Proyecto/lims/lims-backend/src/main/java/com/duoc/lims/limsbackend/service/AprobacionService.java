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
import com.duoc.lims.limsbackend.repository.MuestraRepository;
import com.duoc.lims.limsbackend.repository.ResultadoRepository;
import com.duoc.lims.limsbackend.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AprobacionService {

    private final AprobacionRepository aprobacionRepository;
    private final ResultadoRepository resultadoRepository;
    private final UsuarioRepository usuarioRepository;
    private final MuestraAnalisisRepository muestraAnalisisRepository;
    private final MuestraRepository muestraRepository;

    public AprobacionService(AprobacionRepository aprobacionRepository,
                             ResultadoRepository resultadoRepository,
                             UsuarioRepository usuarioRepository,
                             MuestraAnalisisRepository muestraAnalisisRepository,
                             MuestraRepository muestraRepository) {
        this.aprobacionRepository = aprobacionRepository;
        this.resultadoRepository = resultadoRepository;
        this.usuarioRepository = usuarioRepository;
        this.muestraAnalisisRepository = muestraAnalisisRepository;
        this.muestraRepository = muestraRepository;
    }

    @Transactional
    public AprobacionResponseDTO evaluar(AprobacionRequestDTO dto) {
        Resultado resultado = resultadoRepository.findById(dto.getIdResultado())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el resultado con id " + dto.getIdResultado()));

        Usuario supervisor = usuarioRepository.findById(dto.getIdSupervisor())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el usuario (supervisor) con id " + dto.getIdSupervisor()));

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
        actualizarEstadoMuestra(muestra, estado);

        return aDTO(guardada, muestra);
    }

    private void actualizarEstadoMuestra(Muestra muestra, EstadoAprobacion estado) {
        if (estado == EstadoAprobacion.RECHAZADO) {
            // Un solo rechazo deja la muestra como Rechazada.
            muestra.setEstado(EstadoMuestra.RECHAZADA);
        } else {
            // Aprobado: la muestra queda Aprobada solo si TODOS sus análisis
            // están completados y su último estado no es un rechazo.
            if (todosLosAnalisisAprobados(muestra)) {
                muestra.setEstado(EstadoMuestra.APROBADA);
            } else {
                muestra.setEstado(EstadoMuestra.RESULTADOS_INGRESADOS);
            }
        }
        muestraRepository.save(muestra);
    }

    private boolean todosLosAnalisisAprobados(Muestra muestra) {
        List<MuestraAnalisis> analisis =
                muestraAnalisisRepository.findByMuestra_Id(muestra.getId());

        if (analisis.isEmpty()) {
            return false;
        }

        for (MuestraAnalisis ma : analisis) {
            // Todos deben estar completados...
            if (ma.getEstado() != EstadoMuestraAnalisis.COMPLETADO) {
                return false;
            }
            // ...y su resultado debe tener una última aprobación "Aprobado".
            if (!resultadoAprobado(ma)) {
                return false;
            }
        }
        return true;
    }

    private boolean resultadoAprobado(MuestraAnalisis ma) {
        List<Resultado> resultados = resultadoRepository.findAll().stream()
                .filter(r -> r.getMuestraAnalisis().getId().equals(ma.getId()))
                .toList();
        if (resultados.isEmpty()) {
            return false;
        }
        Resultado r = resultados.get(0);
        List<Aprobacion> aprobaciones = aprobacionRepository.findByResultado_Id(r.getId());
        if (aprobaciones.isEmpty()) {
            return false;
        }
        // La última aprobación registrada manda.
        Aprobacion ultima = aprobaciones.get(aprobaciones.size() - 1);
        return ultima.getEstadoAprobacion() == EstadoAprobacion.APROBADO;
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