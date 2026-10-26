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
import com.duoc.lims.limsbackend.dto.HistorialEstadoDTO;
import com.duoc.lims.limsbackend.dto.CustodiaEventoDTO;
import com.duoc.lims.limsbackend.model.Investigador;
import com.duoc.lims.limsbackend.model.SitioMuestreo;
import com.duoc.lims.limsbackend.model.enums.TipoEventoCustodia;
import com.duoc.lims.limsbackend.repository.InvestigadorRepository;
import com.duoc.lims.limsbackend.model.MuestraHistorialEstado;
import com.duoc.lims.limsbackend.repository.MuestraHistorialEstadoRepository;
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
    private final EstadoMuestraService estadoMuestraService;
    private final MuestraHistorialEstadoRepository historialRepository;
    private final InvestigadorRepository investigadorRepository;
    private final CustodiaService custodiaService;
    private final SitioService sitioService;

    public MuestraService(MuestraRepository muestraRepository,
                          CentroRepository centroRepository,
                          UsuarioRepository usuarioRepository,
                          MuestraAnalisisRepository muestraAnalisisRepository,
                          ResultadoRepository resultadoRepository,
                          AprobacionRepository aprobacionRepository,
                          ResultadoService resultadoService,
                          EstadoMuestraService estadoMuestraService,
                          MuestraHistorialEstadoRepository historialRepository,
                          InvestigadorRepository investigadorRepository,
                          CustodiaService custodiaService,
                          SitioService sitioService) {
        this.muestraRepository = muestraRepository;
        this.centroRepository = centroRepository;
        this.usuarioRepository = usuarioRepository;
        this.muestraAnalisisRepository = muestraAnalisisRepository;
        this.resultadoRepository = resultadoRepository;
        this.aprobacionRepository = aprobacionRepository;
        this.resultadoService = resultadoService;
        this.estadoMuestraService = estadoMuestraService;
        this.historialRepository = historialRepository;
        this.investigadorRepository = investigadorRepository;
        this.custodiaService = custodiaService;
        this.sitioService = sitioService;
    }

    @Transactional
    public MuestraResponseDTO crear(MuestraRequestDTO dto) {
        Centro centro = centroRepository.findById(ID_CENTRO_DEFAULT)
                .orElseThrow(() -> new IllegalStateException("No existe el centro por defecto (id=1)"));

        Usuario usuario = usuarioRepository.findById(dto.getIdUsuarioRegistro())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el usuario con id " + dto.getIdUsuarioRegistro()));

        Investigador investigador = investigadorRepository.findById(dto.getIdInvestigador())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el investigador con id " + dto.getIdInvestigador()));
        if (!investigador.isActivo()) {
            throw new IllegalArgumentException("El investigador " + investigador.getNombreCompleto() + " está inactivo");
        }
        SitioMuestreo sitio = sitioService.buscar(dto.getIdSitio());
        if (!sitio.isActivo()) {
            throw new IllegalArgumentException("El sitio " + sitio.getCodigo() + " está inactivo");
        }
        if (dto.getFechaToma() != null && dto.getFechaVencimiento() != null
                && dto.getFechaVencimiento().isBefore(dto.getFechaToma().toLocalDate())) {
            throw new IllegalArgumentException("La fecha 'conservar hasta' no puede ser anterior a la fecha de toma");
        }

        Muestra muestra = new Muestra();
        muestra.setCentro(centro);
        muestra.setInvestigador(investigador);
        muestra.setFechaToma(dto.getFechaToma());
        muestra.setCondicionRecepcion(vacioANull(dto.getCondicionRecepcion()));
        muestra.setUbicacionAlmacenamiento(vacioANull(dto.getUbicacionAlmacenamiento()));
        muestra.setFechaVencimiento(dto.getFechaVencimiento());
        muestra.setCodigoUnico(generarCodigoUnico(centro.getId()));
        muestra.setTipoMuestra(TipoMuestra.fromValorDb(dto.getTipoMuestra()));
        muestra.setSitio(sitio);
        String detalle = vacioANull(dto.getProcedencia());
        String procedencia = detalle == null ? sitio.getNombre() : sitio.getNombre() + " — " + detalle;
        muestra.setProcedencia(procedencia.length() > 150 ? procedencia.substring(0, 150) : procedencia);
        muestra.setUsuarioRegistro(usuario);
        muestra.setPrioridad(Prioridad.fromValorDb(dto.getPrioridad()));
        muestra.setObservaciones(dto.getObservaciones());

        Muestra guardada = muestraRepository.save(muestra);
        estadoMuestraService.registrarRecepcion(guardada, usuario);   // primer registro del historial

        // Firma de recepción: primer evento de la cadena de custodia.
        if (Boolean.TRUE.equals(dto.getFirmaRecepcion())) {
            String entrega = dto.getEntregadaPor() == null || dto.getEntregadaPor().isBlank()
                    ? investigador.getNombreCompleto()
                    : dto.getEntregadaPor();
            custodiaService.registrar(guardada, TipoEventoCustodia.RECEPCION, entrega, usuario,
                    dto.getUbicacionAlmacenamiento(), dto.getCondicionRecepcion(), "Firma al registrar la muestra");
        }
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

        List<HistorialEstadoDTO> historial = historialRepository.findByMuestra_IdOrderByIdAsc(id)
                .stream()
                .map(this::historialADTO)
                .toList();

        List<CustodiaEventoDTO> custodia = custodiaService.listar(id);

        return new MuestraDetalleDTO(aDTO(muestra), analisis, historial, custodia);
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
                m.getObservaciones(),
                m.getInvestigador() == null ? null : m.getInvestigador().getId(),
                m.getInvestigador() == null ? null : m.getInvestigador().getNombreCompleto(),
                m.getInvestigador() == null ? null : m.getInvestigador().getInstitucion(),
                m.getFechaToma(),
                m.getCondicionRecepcion(),
                m.getUbicacionAlmacenamiento(),
                m.getFechaVencimiento(),
                m.getSitio() == null ? null : m.getSitio().getId(),
                m.getSitio() == null ? null : m.getSitio().getCodigo(),
                m.getSitio() == null ? null : m.getSitio().getNombre(),
                m.getSitio() == null ? null : m.getSitio().getLatitud(),
                m.getSitio() == null ? null : m.getSitio().getLongitud(),
                m.getInterpretacion(),
                m.getInterpretacionOrigen(),
                m.getUsuarioInterpretacion() == null ? null
                        : m.getUsuarioInterpretacion().getNombre() + " " + m.getUsuarioInterpretacion().getApellido(),
                m.getFechaInterpretacion()
        );
    }

    private String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private HistorialEstadoDTO historialADTO(MuestraHistorialEstado h) {
        return new HistorialEstadoDTO(
                h.getEstadoAnterior() == null ? null : h.getEstadoAnterior().getValorDb(),
                h.getEstadoNuevo().getValorDb(),
                h.getUsuario().getNombre() + " " + h.getUsuario().getApellido(),
                h.getFechaCambio(),
                h.getComentario());
    }
}
