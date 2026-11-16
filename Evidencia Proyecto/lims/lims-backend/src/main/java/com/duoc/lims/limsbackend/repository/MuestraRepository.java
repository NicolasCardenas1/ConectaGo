package com.duoc.lims.limsbackend.repository;

import com.duoc.lims.limsbackend.model.Muestra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MuestraRepository extends JpaRepository<Muestra, Integer> {
    long countByCentro_Id(Integer idCentro);

    // v6: consultas del chatbot de trazabilidad
    Optional<Muestra> findByCodigoUnicoIgnoreCase(String codigoUnico);

    List<Muestra> findByEquipoToma_IdOrderByFechaTomaDesc(Integer idEquipo);
}