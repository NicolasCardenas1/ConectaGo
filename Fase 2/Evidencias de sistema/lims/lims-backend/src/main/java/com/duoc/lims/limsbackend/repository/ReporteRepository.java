package com.duoc.lims.limsbackend.repository;

import com.duoc.lims.limsbackend.model.Reporte;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReporteRepository extends JpaRepository<Reporte, Integer> {

    /** Último informe emitido para una muestra (para calcular la siguiente versión). */
    Optional<Reporte> findTopByMuestra_IdOrderByVersionDesc(Integer idMuestra);

    /** Historial de informes emitidos, mas recientes primero (RF05). */
    List<Reporte> findAllByOrderByFechaGeneracionDesc();

    /** v5: informes de una muestra (línea de tiempo de trazabilidad). */
    List<Reporte> findByMuestra_IdOrderByVersionAsc(Integer idMuestra);
}
