package com.duoc.lims.limsbackend.repository;

import com.duoc.lims.limsbackend.model.Aprobacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AprobacionRepository extends JpaRepository<Aprobacion, Integer> {
    List<Aprobacion> findByResultado_Id(Integer idResultado);

    Optional<Aprobacion> findTopByResultado_IdOrderByIdDesc(Integer idResultado);

    boolean existsByResultado_Id(Integer idResultado);

    // v5: cada versión del resultado se evalúa una sola vez
    boolean existsByResultado_IdAndVersionResultado(Integer idResultado, Integer versionResultado);

    List<Aprobacion> findByResultado_IdOrderByIdAsc(Integer idResultado);
}
