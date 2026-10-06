package com.duoc.lims.limsbackend.repository;

import com.duoc.lims.limsbackend.model.Reporte;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReporteRepository extends JpaRepository<Reporte, Integer> {

    /** Último informe emitido para una muestra (para calcular la siguiente versión). */
    Optional<Reporte> findTopByMuestra_IdOrderByVersionDesc(Integer idMuestra);
}
