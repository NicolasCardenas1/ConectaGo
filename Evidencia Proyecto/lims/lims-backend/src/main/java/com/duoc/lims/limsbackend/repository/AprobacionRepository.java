package com.duoc.lims.limsbackend.repository;

import com.duoc.lims.limsbackend.model.Aprobacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AprobacionRepository extends JpaRepository<Aprobacion, Integer> {
    List<Aprobacion> findByResultado_Id(Integer idResultado);

    Optional<Aprobacion> findTopByResultado_IdOrderByIdDesc(Integer idResultado);
}
