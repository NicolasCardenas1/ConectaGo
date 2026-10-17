package com.duoc.lims.limsbackend.repository;

import com.duoc.lims.limsbackend.model.CustodiaEvento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustodiaEventoRepository extends JpaRepository<CustodiaEvento, Integer> {

    List<CustodiaEvento> findByMuestra_IdOrderByIdAsc(Integer idMuestra);
}
