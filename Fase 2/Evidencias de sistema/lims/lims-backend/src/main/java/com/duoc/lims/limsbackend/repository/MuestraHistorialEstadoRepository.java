package com.duoc.lims.limsbackend.repository;

import com.duoc.lims.limsbackend.model.MuestraHistorialEstado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MuestraHistorialEstadoRepository extends JpaRepository<MuestraHistorialEstado, Integer> {

    List<MuestraHistorialEstado> findByMuestra_IdOrderByIdAsc(Integer idMuestra);
}
