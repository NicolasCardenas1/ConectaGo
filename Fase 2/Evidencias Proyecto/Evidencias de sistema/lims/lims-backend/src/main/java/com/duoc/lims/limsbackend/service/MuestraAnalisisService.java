package com.duoc.lims.limsbackend.service;

import com.duoc.lims.limsbackend.dto.MuestraAnalisisRequestDTO;
import com.duoc.lims.limsbackend.dto.MuestraAnalisisResponseDTO;
import com.duoc.lims.limsbackend.model.AnalisisCatalogo;
import com.duoc.lims.limsbackend.model.Muestra;
import com.duoc.lims.limsbackend.model.MuestraAnalisis;
import com.duoc.lims.limsbackend.model.Usuario;
import com.duoc.lims.limsbackend.repository.AnalisisCatalogoRepository;
import com.duoc.lims.limsbackend.repository.MuestraAnalisisRepository;
import com.duoc.lims.limsbackend.repository.MuestraRepository;
import com.duoc.lims.limsbackend.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MuestraAnalisisService {

    private final MuestraAnalisisRepository muestraAnalisisRepository;
    private final MuestraRepository muestraRepository;
    private final AnalisisCatalogoRepository analisisRepository;
    private final UsuarioRepository usuarioRepository;

    public MuestraAnalisisService(MuestraAnalisisRepository muestraAnalisisRepository,
                                  MuestraRepository muestraRepository,
                                  AnalisisCatalogoRepository analisisRepository,
                                  UsuarioRepository usuarioRepository) {
        this.muestraAnalisisRepository = muestraAnalisisRepository;
        this.muestraRepository = muestraRepository;
        this.analisisRepository = analisisRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public MuestraAnalisisResponseDTO asignar(MuestraAnalisisRequestDTO dto) {
        Muestra muestra = muestraRepository.findById(dto.getIdMuestra())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe la muestra con id " + dto.getIdMuestra()));

        AnalisisCatalogo analisis = analisisRepository.findById(dto.getIdAnalisis())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el análisis con id " + dto.getIdAnalisis()));

        if (Boolean.FALSE.equals(analisis.getActivo())) {
            throw new IllegalArgumentException(
                    "El análisis '" + analisis.getNombre() + "' está inactivo y no puede asignarse");
        }

        if (muestraAnalisisRepository.existsByMuestra_IdAndAnalisis_Id(
                dto.getIdMuestra(), dto.getIdAnalisis())) {
            throw new IllegalArgumentException(
                    "Ese análisis ya está asignado a la muestra");
        }

        MuestraAnalisis ma = new MuestraAnalisis();
        ma.setMuestra(muestra);
        ma.setAnalisis(analisis);
        ma.setFechaAsignacion(LocalDateTime.now());

        if (dto.getIdAnalistaAsignado() != null) {
            Usuario analista = usuarioRepository.findById(dto.getIdAnalistaAsignado())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No existe el usuario (analista) con id " + dto.getIdAnalistaAsignado()));
            ma.setAnalistaAsignado(analista);
        }

        return aDTO(muestraAnalisisRepository.save(ma));
    }

    @Transactional(readOnly = true)
    public List<MuestraAnalisisResponseDTO> listarPorMuestra(Integer idMuestra) {
        return muestraAnalisisRepository.findByMuestra_Id(idMuestra)
                .stream()
                .map(this::aDTO)
                .toList();
    }

    private MuestraAnalisisResponseDTO aDTO(MuestraAnalisis ma) {
        String nombreAnalista = ma.getAnalistaAsignado() == null
                ? null
                : ma.getAnalistaAsignado().getNombre() + " " + ma.getAnalistaAsignado().getApellido();

        return new MuestraAnalisisResponseDTO(
                ma.getId(),
                ma.getMuestra().getId(),
                ma.getMuestra().getCodigoUnico(),
                ma.getAnalisis().getId(),
                ma.getAnalisis().getNombre(),
                nombreAnalista,
                ma.getEstado().getValorDb(),
                ma.getFechaAsignacion(),
                ma.getFechaCompletado()
        );
    }
}