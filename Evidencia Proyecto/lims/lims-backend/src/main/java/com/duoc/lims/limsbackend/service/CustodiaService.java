package com.duoc.lims.limsbackend.service;

import com.duoc.lims.limsbackend.dto.CustodiaEventoDTO;
import com.duoc.lims.limsbackend.dto.CustodiaRequestDTO;
import com.duoc.lims.limsbackend.model.CustodiaEvento;
import com.duoc.lims.limsbackend.model.Muestra;
import com.duoc.lims.limsbackend.model.Usuario;
import com.duoc.lims.limsbackend.model.enums.TipoEventoCustodia;
import com.duoc.lims.limsbackend.repository.CustodiaEventoRepository;
import com.duoc.lims.limsbackend.repository.MuestraRepository;
import com.duoc.lims.limsbackend.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cadena de custodia: cada entrega, traslado o retiro de la muestra queda "firmado" por el
 * usuario que la recibe (confirmación explícita + fecha y hora).
 * La ubicación declarada en el evento pasa a ser la ubicación actual de la muestra.
 */
@Service
public class CustodiaService {

    private final CustodiaEventoRepository custodiaRepository;
    private final MuestraRepository muestraRepository;
    private final UsuarioRepository usuarioRepository;

    public CustodiaService(CustodiaEventoRepository custodiaRepository,
                           MuestraRepository muestraRepository,
                           UsuarioRepository usuarioRepository) {
        this.custodiaRepository = custodiaRepository;
        this.muestraRepository = muestraRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public List<CustodiaEventoDTO> listar(Integer idMuestra) {
        return custodiaRepository.findByMuestra_IdOrderByIdAsc(idMuestra).stream()
                .map(this::aDTO)
                .toList();
    }

    @Transactional
    public CustodiaEventoDTO registrar(Integer idMuestra, CustodiaRequestDTO dto, Integer idUsuarioRecibe) {
        Muestra muestra = muestraRepository.findById(idMuestra)
                .orElseThrow(() -> new IllegalArgumentException("No existe la muestra con id " + idMuestra));
        Usuario receptor = usuarioRepository.findById(idUsuarioRecibe)
                .orElseThrow(() -> new IllegalArgumentException("No existe el usuario con id " + idUsuarioRecibe));

        if (!Boolean.TRUE.equals(dto.getConfirmado())) {
            throw new IllegalArgumentException(
                    "Para registrar el evento debes confirmar (firmar) que recibiste la muestra");
        }

        return aDTO(registrar(muestra, TipoEventoCustodia.fromValorDb(dto.getTipoEvento()),
                dto.getEntregadoPor(), receptor, dto.getUbicacion(), dto.getCondicion(), dto.getObservaciones()));
    }

    /** Uso interno: registra el evento y actualiza ubicación/condición de la muestra. */
    @Transactional
    public CustodiaEvento registrar(Muestra muestra, TipoEventoCustodia tipo, String entregadoPor, Usuario receptor,
                                    String ubicacion, String condicion, String observaciones) {
        if (entregadoPor == null || entregadoPor.isBlank()) {
            throw new IllegalArgumentException("Indica quién entrega la muestra");
        }
        CustodiaEvento evento = new CustodiaEvento();
        evento.setMuestra(muestra);
        evento.setTipoEvento(tipo);
        evento.setEntregadoPor(entregadoPor.trim());
        evento.setUsuarioRecibe(receptor);
        evento.setUbicacion(vacioANull(ubicacion));
        evento.setCondicion(vacioANull(condicion));
        evento.setConfirmado(true);
        evento.setObservaciones(vacioANull(observaciones));
        CustodiaEvento guardado = custodiaRepository.save(evento);

        // La ubicación declarada pasa a ser la ubicación actual de la muestra.
        boolean cambio = false;
        if (evento.getUbicacion() != null) {
            muestra.setUbicacionAlmacenamiento(evento.getUbicacion());
            cambio = true;
        }
        if (tipo == TipoEventoCustodia.RECEPCION && evento.getCondicion() != null) {
            muestra.setCondicionRecepcion(evento.getCondicion());
            cambio = true;
        }
        if (cambio) {
            muestraRepository.save(muestra);
        }
        return guardado;
    }

    private String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public CustodiaEventoDTO aDTO(CustodiaEvento e) {
        return new CustodiaEventoDTO(
                e.getId(),
                e.getTipoEvento().getValorDb(),
                e.getEntregadoPor(),
                e.getUsuarioRecibe().getNombre() + " " + e.getUsuarioRecibe().getApellido(),
                e.getUbicacion(),
                e.getCondicion(),
                e.getFechaHora(),
                e.getObservaciones());
    }
}
