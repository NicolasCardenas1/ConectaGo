package com.duoc.lims.limsbackend.repository;

import com.duoc.lims.limsbackend.model.Equipo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipoRepository extends JpaRepository<Equipo, Integer> {

    List<Equipo> findAllByOrderByCodigoAsc();

    boolean existsByCodigoIgnoreCase(String codigo);
}
