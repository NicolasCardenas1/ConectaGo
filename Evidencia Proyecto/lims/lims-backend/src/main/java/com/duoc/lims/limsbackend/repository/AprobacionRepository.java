package com.duoc.lims.limsbackend.repository;

import com.duoc.lims.limsbackend.model.Aprobacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AprobacionRepository extends JpaRepository<Aprobacion, Integer> {
    List<Aprobacion> findByResultado_Id(Integer idResultado);
}