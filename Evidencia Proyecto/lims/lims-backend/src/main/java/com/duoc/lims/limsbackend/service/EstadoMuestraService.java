package com.duoc.lims.limsbackend.service;

import com.duoc.lims.limsbackend.model.Muestra;
import com.duoc.lims.limsbackend.model.MuestraHistorialEstado;
import com.duoc.lims.limsbackend.model.Usuario;
import com.duoc.lims.limsbackend.model.enums.EstadoMuestra;
import com.duoc.lims.limsbackend.repository.MuestraHistorialEstadoRepository;
import com.duoc.lims.limsbackend.repository.MuestraRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Único punto por donde cambia el estado de una muestra.
 * Cada cambio queda registrado en muestra_historial_estado (trazabilidad ISO 17025).
 */
@Service
public class EstadoMuestraService {

    private static final int LARGO_MAX_COMENTARIO = 255;

    private final MuestraRepository muestraRepository;
    private final MuestraHistorialEstadoRepository historialRepository;

    public EstadoMuestraService(MuestraRepository muestraRepository,
                                MuestraHistorialEstadoRepository historialRepository) {
        this.muestraRepository = muestraRepository;
        this.historialRepository = historialRepository;
    }

    /** Registra el estado inicial de una muestra recién creada (sin estado anterior). */
    @Transactional
    public void registrarRecepcion(Muestra muestra, Usuario usuario) {
        registrar(muestra, null, muestra.getEstado(), usuario, "Recepción de la muestra");
    }

    /**
     * Cambia el estado de la muestra y deja el registro en el historial.
     * Si la muestra ya está en ese estado no hace nada (no se duplican registros).
     */
    @Transactional
    public void cambiarEstado(Muestra muestra, EstadoMuestra nuevo, Usuario usuario, String comentario) {
        EstadoMuestra anterior = muestra.getEstado();
        if (anterior == nuevo) {
            return;
        }
        muestra.setEstado(nuevo);
        muestraRepository.save(muestra);
        registrar(muestra, anterior, nuevo, usuario, comentario);
    }

    private void registrar(Muestra muestra, EstadoMuestra anterior, EstadoMuestra nuevo,
                           Usuario usuario, String comentario) {
        MuestraHistorialEstado h = new MuestraHistorialEstado();
        h.setMuestra(muestra);
        h.setEstadoAnterior(anterior);
        h.setEstadoNuevo(nuevo);
        h.setUsuario(usuario);
        h.setComentario(recortar(comentario));
        historialRepository.save(h);
    }

    private String recortar(String texto) {
        if (texto == null || texto.length() <= LARGO_MAX_COMENTARIO) {
            return texto;
        }
        return texto.substring(0, LARGO_MAX_COMENTARIO - 3) + "...";
    }
}
