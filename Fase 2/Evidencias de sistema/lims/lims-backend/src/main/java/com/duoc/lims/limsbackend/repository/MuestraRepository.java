package com.duoc.lims.limsbackend.repository;

import com.duoc.lims.limsbackend.model.Muestra;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MuestraRepository extends JpaRepository<Muestra, Integer> {
    long countByCentro_Id(Integer idCentro);
}