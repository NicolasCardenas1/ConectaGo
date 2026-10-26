package com.duoc.lims.limsbackend.service;

import com.duoc.lims.limsbackend.dto.AnomaliaDTO;
import com.duoc.lims.limsbackend.dto.CorreccionResultadoDTO;
import com.duoc.lims.limsbackend.dto.ResultadoRequestDTO;
import com.duoc.lims.limsbackend.dto.ResultadoResponseDTO;
import com.duoc.lims.limsbackend.dto.ResultadoVersionDTO;
import com.duoc.lims.limsbackend.model.AnalisisCatalogo;
import com.duoc.lims.limsbackend.model.Aprobacion;
import com.duoc.lims.limsbackend.model.Equipo;
import com.duoc.lims.limsbackend.model.Muestra;
import com.duoc.lims.limsbackend.model.MuestraAnalisis;
import com.duoc.lims.limsbackend.model.Resultado;
import com.duoc.lims.limsbackend.model.ResultadoVersion;
import com.duoc.lims.limsbackend.model.Usuario;
import com.duoc.lims.limsbackend.model.enums.EstadoAprobacion;
import com.duoc.lims.limsbackend.model.enums.EstadoMuestra;
import com.duoc.lims.limsbackend.model.enums.EstadoMuestraAnalisis;
import com.duoc.lims.limsbackend.repository.AprobacionRepository;
import com.duoc.lims.limsbackend.repository.MuestraAnalisisRepository;
import com.duoc.lims.limsbackend.repository.ResultadoRepository;
import com.duoc.lims.limsbackend.repository.ResultadoVersionRepository;
import com.duoc.lims.limsbackend.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ResultadoService {

    private final ResultadoRepository resultadoRepository;
    private final MuestraAnalisisRepository muestraAnalisisRepository;
    private final UsuarioRepository usuarioRepository;
    private final EstadoMuestraService estadoMuestraService;
    private final ResultadoVersionRepository versionRepository;
    private final AprobacionRepository aprobacionRepository;
    private final EquipoService equipoService;
    private final AnomaliaService anomaliaService;

    public ResultadoService(ResultadoRepository resultadoRepository,
                            MuestraAnalisisRepository muestraAnalisisRepository,
                            UsuarioRepository usuarioRepository,
                            EstadoMuestraService estadoMuestraService,
                            ResultadoVersionRepository versionRepository,
                            AprobacionRepository aprobacionRepository,
                            EquipoService equipoService,
                            AnomaliaService anomaliaService) {
        this.resultadoRepository = resultadoRepository;
        this.muestraAnalisisRepository = muestraAnalisisRepository;
        this.usuarioRepository = usuarioRepository;
        this.estadoMuestraService = estadoMuestraService;
        this.versionRepository = versionRepository;
        this.aprobacionRepository = aprobacionRepository;
        this.equipoService = equipoService;
        this.anomaliaService = anomaliaService;
    }

    @Transactional
    public ResultadoResponseDTO ingresar(ResultadoRequestDTO dto) {
        MuestraAnalisis ma = muestraAnalisisRepository.findById(dto.getIdMuestraAnalisis())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe la solicitud de análisis (muestra_analisis) con id "
                                + dto.getIdMuestraAnalisis()));

        if (resultadoRepository.existsByMuestraAnalisis_Id(dto.getIdMuestraAnalisis())) {
            throw new IllegalArgumentException(
                    "Ese análisis solicitado ya tiene un resultado ingresado (para cambiarlo use 'Corregir resultado')");
        }

        Usuario usuario = usuarioRepository.findById(dto.getIdUsuarioIngreso())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el usuario con id " + dto.getIdUsuarioIngreso()));

        // v5: trazabilidad metrológica — equipo activo y con calibración vigente
        Equipo equipo = equipoService.validarParaUso(dto.getIdEquipo());

        // --- RF03: validación automática de rango ---
        boolean dentroRango = validarRango(ma.getAnalisis(), dto.getValorResultado(), dto.getObservaciones());

        // v5: detección de anomalías contra el historial del sitio
        AnomaliaDTO anomalia = anomaliaService.evaluar(ma, dto.getValorResultado(), null);

        Resultado resultado = new Resultado();
        resultado.setMuestraAnalisis(ma);
        resultado.setValorResultado(dto.getValorResultado());
        resultado.setDentroRango(dentroRango);
        resultado.setEquipo(equipo);
        resultado.setInstrumentoUtilizado(equipo.getEtiqueta());
        resultado.setUsuarioIngreso(usuario);
        resultado.setObservaciones(vacioANull(dto.getObservaciones()));
        resultado.setVersion(1);
        aplicarAnomalia(resultado, anomalia);

        Resultado guardado = resultadoRepository.save(resultado);
        registrarVersion(guardado, usuario, null);

        // Al ingresar el resultado, el análisis solicitado pasa a "Completado".
        ma.setEstado(EstadoMuestraAnalisis.COMPLETADO);
        ma.setFechaCompletado(LocalDateTime.now());
        if (ma.getAnalistaAsignado() == null) {
            ma.setAnalistaAsignado(usuario);
        }
        muestraAnalisisRepository.save(ma);

        // La muestra avanza: "En analisis" con el primer resultado y
        // "Resultados ingresados" cuando todos sus análisis tienen resultado.
        actualizarEstadoMuestra(ma, usuario);

        return aDTO(guardado);
    }

    /**
     * v5 — Corrige un resultado creando una versión nueva. El valor anterior NO se pierde: queda en
     * resultado_versiones con quién, cuándo y por qué se cambió. Un resultado aprobado o ya informado
     * no se puede modificar (lo que en una planilla Excel sí se puede hacer sin dejar rastro).
     */
    @Transactional
    public ResultadoResponseDTO corregir(Integer idResultado, CorreccionResultadoDTO dto) {
        Resultado resultado = resultadoRepository.findById(idResultado)
                .orElseThrow(() -> new IllegalArgumentException("No existe el resultado con id " + idResultado));
        MuestraAnalisis ma = resultado.getMuestraAnalisis();
        Muestra muestra = ma.getMuestra();

        if (muestra.getEstado() == EstadoMuestra.REPORTADA) {
            throw new IllegalArgumentException(
                    "La muestra ya fue informada al investigador: sus resultados no se pueden modificar");
        }
        Optional<Aprobacion> evaluacion = evaluacionVigente(resultado);
        if (evaluacion.isPresent() && evaluacion.get().getEstadoAprobacion() == EstadoAprobacion.APROBADO) {
            throw new IllegalArgumentException(
                    "Este resultado ya fue aprobado por " + nombre(evaluacion.get().getSupervisor())
                            + ": un dato aprobado no se puede modificar (ISO/IEC 17025)");
        }

        Usuario usuario = usuarioRepository.findById(dto.getIdUsuario())
                .orElseThrow(() -> new IllegalArgumentException("No existe el usuario con id " + dto.getIdUsuario()));
        Equipo equipo = equipoService.validarParaUso(dto.getIdEquipo());

        boolean dentroRango = validarRango(ma.getAnalisis(), dto.getValorResultado(), dto.getObservaciones());
        AnomaliaDTO anomalia = anomaliaService.evaluar(ma, dto.getValorResultado(), resultado.getId());

        BigDecimal anterior = resultado.getValorResultado();

        resultado.setValorResultado(dto.getValorResultado());
        resultado.setDentroRango(dentroRango);
        resultado.setEquipo(equipo);
        resultado.setInstrumentoUtilizado(equipo.getEtiqueta());
        resultado.setObservaciones(vacioANull(dto.getObservaciones()));
        resultado.setUsuarioIngreso(usuario);   // quien responde por el valor vigente
        resultado.setVersion(resultado.getVersion() + 1);
        resultado.setFechaModificacion(LocalDateTime.now());
        aplicarAnomalia(resultado, anomalia);
        Resultado guardado = resultadoRepository.save(resultado);
        registrarVersion(guardado, usuario, dto.getMotivoCambio().trim());

        // Si la muestra estaba rechazada y ya no queda ningún rechazo sin corregir, vuelve a revisión.
        if (muestra.getEstado() == EstadoMuestra.RECHAZADA && !tieneRechazoSinCorregir(muestra)) {
            estadoMuestraService.cambiarEstado(muestra,
                    todosCompletados(muestra) ? EstadoMuestra.RESULTADOS_INGRESADOS : EstadoMuestra.EN_ANALISIS,
                    usuario, "Resultado corregido (v" + guardado.getVersion() + ") de " + ma.getAnalisis().getNombre()
                            + ": " + numero(anterior) + " → " + numero(guardado.getValorResultado()));
        } else {
            actualizarEstadoMuestra(ma, usuario);
        }
        return aDTO(guardado);
    }

    /** v5 — Todas las versiones de un resultado, cada una con la evaluación que recibió. */
    @Transactional(readOnly = true)
    public List<ResultadoVersionDTO> versiones(Integer idResultado) {
        Resultado resultado = resultadoRepository.findById(idResultado)
                .orElseThrow(() -> new IllegalArgumentException("No existe el resultado con id " + idResultado));
        String unidad = resultado.getMuestraAnalisis().getAnalisis().getUnidadMedida();
        Map<Integer, Aprobacion> evaluacionPorVersion = aprobacionRepository.findByResultado_IdOrderByIdAsc(idResultado)
                .stream()
                .collect(Collectors.toMap(Aprobacion::getVersionResultado, Function.identity(), (a, b) -> b));

        return versionRepository.findByResultado_IdOrderByVersionAsc(idResultado).stream()
                .map(v -> {
                    Aprobacion a = evaluacionPorVersion.get(v.getVersion());
                    return new ResultadoVersionDTO(v.getVersion(), v.getValorResultado(), unidad, v.getDentroRango(),
                            v.getInstrumentoUtilizado(), v.getObservaciones(), v.isAnomalo(), v.getDetalleAnomalia(),
                            nombre(v.getUsuario()), v.getFecha(), v.getMotivoCambio(),
                            a == null ? null : a.getEstadoAprobacion().getValorDb(),
                            a == null ? null : nombre(a.getSupervisor()),
                            a == null ? null : a.getComentario(),
                            a == null ? null : a.getFechaAprobacion());
                })
                .toList();
    }

    /** v5 — Vista previa de la detección de anomalías mientras el analista escribe el valor. */
    @Transactional(readOnly = true)
    public AnomaliaDTO evaluarAnomalia(Integer idMuestraAnalisis, BigDecimal valor, Integer idResultado) {
        MuestraAnalisis ma = muestraAnalisisRepository.findById(idMuestraAnalisis)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe la solicitud de análisis con id " + idMuestraAnalisis));
        return anomaliaService.evaluar(ma, valor, idResultado);
    }

    @Transactional(readOnly = true)
    public List<ResultadoResponseDTO> listar(boolean soloPendientes) {
        List<Resultado> resultados = soloPendientes
                ? resultadoRepository.findPendientesDeAprobacion()
                : resultadoRepository.findAll();
        return resultados.stream().map(this::aDTO).toList();
    }

    // ---------- reglas ----------

    /** RF03: devuelve si está en rango; si no lo está, exige justificación (CHECK del esquema). */
    private boolean validarRango(AnalisisCatalogo analisis, BigDecimal valor, String observaciones) {
        boolean dentroRango = valor.compareTo(analisis.getValorMinNormal()) >= 0
                && valor.compareTo(analisis.getValorMaxNormal()) <= 0;
        if (!dentroRango && (observaciones == null || observaciones.isBlank())) {
            throw new IllegalArgumentException(
                    "El resultado está fuera del rango normal (" + numero(analisis.getValorMinNormal())
                            + " - " + numero(analisis.getValorMaxNormal())
                            + "); debe justificarlo en 'observaciones'");
        }
        return dentroRango;
    }

    private void aplicarAnomalia(Resultado r, AnomaliaDTO anomalia) {
        r.setPuntajeAnomalia(anomalia.getPuntaje());
        r.setAnomalo(anomalia.isAnomalo());
        r.setDetalleAnomalia(anomalia.isEvaluado() ? anomalia.getMensaje() : null);
    }

    private void registrarVersion(Resultado r, Usuario usuario, String motivo) {
        ResultadoVersion v = new ResultadoVersion();
        v.setResultado(r);
        v.setVersion(r.getVersion());
        v.setValorResultado(r.getValorResultado());
        v.setDentroRango(r.getDentroRango());
        v.setEquipo(r.getEquipo());
        v.setInstrumentoUtilizado(r.getInstrumentoUtilizado());
        v.setObservaciones(r.getObservaciones());
        v.setAnomalo(r.isAnomalo());
        v.setDetalleAnomalia(r.getDetalleAnomalia());
        v.setUsuario(usuario);
        v.setMotivoCambio(motivo);
        versionRepository.save(v);
    }

    /** Evaluación del supervisor sobre la versión VIGENTE del resultado (si existe). */
    private Optional<Aprobacion> evaluacionVigente(Resultado r) {
        return aprobacionRepository.findTopByResultado_IdOrderByIdDesc(r.getId())
                .filter(a -> Objects.equals(a.getVersionResultado(), r.getVersion()));
    }

    private boolean tieneRechazoSinCorregir(Muestra muestra) {
        return muestraAnalisisRepository.findByMuestra_Id(muestra.getId()).stream()
                .map(x -> resultadoRepository.findByMuestraAnalisis_Id(x.getId()))
                .flatMap(Optional::stream)
                .map(this::evaluacionVigente)
                .flatMap(Optional::stream)
                .anyMatch(a -> a.getEstadoAprobacion() == EstadoAprobacion.RECHAZADO);
    }

    private boolean todosCompletados(Muestra muestra) {
        return muestraAnalisisRepository.findByMuestra_Id(muestra.getId()).stream()
                .allMatch(x -> x.getEstado() == EstadoMuestraAnalisis.COMPLETADO);
    }

    private void actualizarEstadoMuestra(MuestraAnalisis ma, Usuario usuario) {
        Muestra muestra = ma.getMuestra();
        // Una muestra rechazada solo sale de ese estado cuando se corrige el resultado rechazado.
        if (muestra.getEstado() == EstadoMuestra.RECHAZADA) {
            return;
        }
        if (todosCompletados(muestra)) {
            estadoMuestraService.cambiarEstado(muestra, EstadoMuestra.RESULTADOS_INGRESADOS, usuario,
                    "Todos los análisis tienen resultado");
        } else {
            estadoMuestraService.cambiarEstado(muestra, EstadoMuestra.EN_ANALISIS, usuario,
                    "Resultado ingresado: " + ma.getAnalisis().getNombre());
        }
    }

    private String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private String numero(BigDecimal n) {
        return n == null ? "" : n.stripTrailingZeros().toPlainString();
    }

    private String nombre(Usuario u) {
        return u == null ? null : u.getNombre() + " " + u.getApellido();
    }

    public ResultadoResponseDTO aDTO(Resultado r) {
        MuestraAnalisis ma = r.getMuestraAnalisis();
        AnalisisCatalogo analisis = ma.getAnalisis();
        Muestra muestra = ma.getMuestra();
        Equipo equipo = r.getEquipo();

        return new ResultadoResponseDTO(
                r.getId(),
                ma.getId(),
                analisis.getNombre(),
                muestra.getCodigoUnico(),
                r.getValorResultado(),
                analisis.getUnidadMedida(),
                analisis.getValorMinNormal(),
                analisis.getValorMaxNormal(),
                r.getDentroRango(),
                r.getInstrumentoUtilizado(),
                nombre(r.getUsuarioIngreso()),
                r.getFechaIngreso(),
                r.getObservaciones(),
                r.getVersion(),
                r.getFechaModificacion(),
                equipo == null ? null : equipo.getId(),
                equipo == null ? null : EquipoService.estadoCalibracion(equipo),
                r.isAnomalo(),
                r.getPuntajeAnomalia(),
                r.getDetalleAnomalia(),
                muestra.getId(),
                muestra.getSitio() == null ? muestra.getProcedencia()
                        : muestra.getSitio().getCodigo() + " · " + muestra.getSitio().getNombre()
        );
    }
}
