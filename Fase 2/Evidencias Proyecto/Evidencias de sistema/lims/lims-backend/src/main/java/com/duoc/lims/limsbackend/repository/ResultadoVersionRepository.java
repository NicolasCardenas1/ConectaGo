package com.duoc.lims.limsbackend.repository;

import com.duoc.lims.limsbackend.model.ResultadoVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResultadoVersionRepository extends JpaRepository<ResultadoVersion, Integer> {

    List<ResultadoVersion> findByResultado_IdOrderByVersionAsc(Integer idResultado);

    // v6: en qué mediciones se usó un equipo (chatbot de trazabilidad)
    List<ResultadoVersion> findByEquipo_IdOrderByFechaDesc(Integer idEquipo);
}
