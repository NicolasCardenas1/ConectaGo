package com.duoc.lims.limsbackend.repository;

import com.duoc.lims.limsbackend.model.EquipoCalibracion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EquipoCalibracionRepository extends JpaRepository<EquipoCalibracion, Integer> {

    List<EquipoCalibracion> findByEquipo_IdOrderByFechaCalibracionDescIdDesc(Integer idEquipo);

    /** La calibración que estaba vigente en una fecha (la más reciente hecha antes o ese día). */
    Optional<EquipoCalibracion> findTopByEquipo_IdAndFechaCalibracionLessThanEqualOrderByFechaCalibracionDescIdDesc(
            Integer idEquipo, LocalDate fecha);
}
