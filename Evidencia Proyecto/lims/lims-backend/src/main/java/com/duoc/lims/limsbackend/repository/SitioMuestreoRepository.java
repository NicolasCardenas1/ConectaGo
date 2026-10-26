package com.duoc.lims.limsbackend.repository;

import com.duoc.lims.limsbackend.model.SitioMuestreo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SitioMuestreoRepository extends JpaRepository<SitioMuestreo, Integer> {

    List<SitioMuestreo> findAllByOrderByCodigoAsc();

    boolean existsByCodigoIgnoreCase(String codigo);
}
