package com.duoc.lims.limsbackend.service;

import com.duoc.lims.limsbackend.dto.MuestraRequestDTO;
import com.duoc.lims.limsbackend.dto.MuestraResponseDTO;
import com.duoc.lims.limsbackend.model.Centro;
import com.duoc.lims.limsbackend.model.Muestra;
import com.duoc.lims.limsbackend.model.Usuario;
import com.duoc.lims.limsbackend.model.enums.Prioridad;
import com.duoc.lims.limsbackend.model.enums.TipoMuestra;
import com.duoc.lims.limsbackend.repository.CentroRepository;
import com.duoc.lims.limsbackend.repository.MuestraRepository;
import com.duoc.lims.limsbackend.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;
import com.duoc.lims.limsbackend.dto.AnalisisDeMuestraDTO;
import com.duoc.lims.limsbackend.dto.MuestraDetalleDTO;
import com.duoc.lims.limsbackend.dto.ResultadoResponseDTO;
import com.duoc.lims.limsbackend.model.AnalisisCatalogo;
import com.duoc.lims.limsbackend.model.Aprobacion;
import com.duoc.lims.limsbackend.model.MuestraAnalisis;
import com.duoc.lims.limsbackend.model.Resultado;
import com.duoc.lims.limsbackend.repository.AprobacionRepository;
import com.duoc.lims.limsbackend.repository.MuestraAnalisisRepository;
import com.duoc.lims.limsbackend.repository.ResultadoRepository;
import java.util.Optional;

import java.time.Year;
import java.util.List;

@Service
public class MuestraService {

    // MVP de un solo centro (ver nota en el esquema SQL): se usa siempre
    // el centro sembrado con id 1. Si el sistema soporta más de un
    // laboratorio más adelante, este valor debe salir del usuario autenticado.
    private static final int ID_CENTRO_DEFAULT = 1;

    private final MuestraRepository muestraRepository;
    private final CentroRepository centroRepository;
    private final UsuarioRepository usuarioRepository;
    private final MuestraAnalisisRepository muestraAnalisisRepository;
    private final ResultadoRepository resultadoRepository;
    private final AprobacionRepository aprobacionRepository;
    private final ResultadoService resultadoService;

    public MuestraService(MuestraRepository muestraRepository,
                          CentroRepository centroRepository,
                          UsuarioRepository usuarioRepository,
                          MuestraAnalisisRepository muestraAnalisisRepository,
                          ResultadoRepository resultadoRepository,
                          AprobacionRepository aprobacionRepository,
                          ResultadoService resultadoService) {
        this.muestraRepository = muestraRepository;
        this.centroRepository = centroRepository;
        this.usuarioRepository = usuarioRepository;
        this.muestraAnalisisRepository = muestraAnalisisRepository;
        this.resultadoRepository = resultadoRepository;
        this.aprobacionRepository = aprobacionRepository;
        this.resultadoService = resultadoService;
    }

    @Transactional
    public MuestraResponseDTO crear(MuestraRequestDTO dto) {
        Centro centro = centroRepository.findById(ID_CENTRO_DEFAULT)
                .orElseThrow(() -> new IllegalStateException("No existe el centro por defecto (id=1)"));

        Usuario usuario = usuarioRepository.findById(dto.getIdUsuarioRegistro())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el usuario con id " + dto.getIdUsuarioRegistro()));

        Muestra muestra = new Muestra();
        muestra.setCentro(centro);
        muestra.setCodigoUnico(generarCodigoUnico(centro.getId()));
        muestra.setTipoMuestra(TipoMuestra.fromValorDb(dto.getTipoMuestra()));
        muestra.setProcedencia(dto.getProcedencia());
        muestra.setUsuarioRegistro(usuario);
        muestra.setPrioridad(Prioridad.fromValorDb(dto.getPrioridad()));
        muestra.setObservaciones(dto.getObservaciones());

        Muestra guardada = muestraRepository.save(muestra);
        return aDTO(guardada);
    }

    public List<MuestraResponseDTO> listar() {
        return muestraRepository.findAll()
                .stream()
                .map(this::aDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public MuestraDetalleDTO obtenerDetalle(Integer id) {
        Muestra muestra = muestraRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe la muestra con id " + id));

        List<AnalisisDeMuestraDTO> analisis = muestraAnalisisRepository.findByMuestra_Id(id)
                .stream()
                .map(this::analisisADTO)
                .toList();

        return new MuestraDetalleDTO(aDTO(muestra), analisis);
    }

    private AnalisisDeMuestraDTO analisisADTO(MuestraAnalisis ma) {
        AnalisisCatalogo cat = ma.getAnalisis();
        String analista = ma.getAnalistaAsignado() == null ? null
                : ma.getAnalistaAsignado().getNombre() + " " + ma.getAnalistaAsignado().getApellido();

        ResultadoResponseDTO resultadoDTO = null;
        String estadoAprobacion = null;
        String comentario = null;

        Optional<Resultado> resultado = resultadoRepository.findByMuestraAnalisis_Id(ma.getId());
        if (resultado.isPresent()) {
            resultadoDTO = resultadoService.aDTO(resultado.get());
            Optional<Aprobacion> ultima =
                    aprobacionRepository.findTopByResultado_IdOrderByIdDesc(resultado.get().getId());
            if (ultima.isPresent()) {
                estadoAprobacion = ultima.get().getEstadoAprobacion().getValorDb();
                comentario = ultima.get().getComentario();
            }
        }

        return new AnalisisDeMuestraDTO(
                ma.getId(), cat.getId(), cat.getNombre(), cat.getUnidadMedida(),
                cat.getValorMinNormal(), cat.getValorMaxNormal(),
                analista, ma.getEstado().getValorDb(),
                resultadoDTO, estadoAprobacion, comentario);
    }

    private String generarCodigoUnico(Integer idCentro) {
        long correlativo = muestraRepository.countByCentro_Id(idCentro) + 1;
        int anio = Year.now().getValue();
        return String.format("M-%d-%04d", anio, correlativo);
    }

    private MuestraResponseDTO aDTO(Muestra m) {
        return new MuestraResponseDTO(
                m.getId(),
                m.getCodigoUnico(),
                m.getTipoMuestra().getValorDb(),
                m.getProcedencia(),
                m.getPrioridad().getValorDb(),
                m.getEstado().getValorDb(),
                m.getFechaRecepcion(),
                m.getUsuarioRegistro().getNombre() + " " + m.getUsuarioRegistro().getApellido(),
                m.getObservaciones()
        );
    }
}