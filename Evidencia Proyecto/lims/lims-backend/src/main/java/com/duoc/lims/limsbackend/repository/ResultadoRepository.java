package com.duoc.lims.limsbackend.repository;

import com.duoc.lims.limsbackend.model.Resultado;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResultadoRepository extends JpaRepository<Resultado, Integer> {
    boolean existsByMuestraAnalisis_Id(Integer idMuestraAnalisis);
}