package com.duoc.lims.limsbackend.repository;

import com.duoc.lims.limsbackend.model.MuestraAnalisis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MuestraAnalisisRepository extends JpaRepository<MuestraAnalisis, Integer> {

    List<MuestraAnalisis> findByMuestra_Id(Integer idMuestra);

    boolean existsByMuestra_IdAndAnalisis_Id(Integer idMuestra, Integer idAnalisis);
}