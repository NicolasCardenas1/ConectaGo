package com.duoc.lims.limsbackend.repository;

import com.duoc.lims.limsbackend.model.Investigador;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvestigadorRepository extends JpaRepository<Investigador, Integer> {

    List<Investigador> findAllByOrderByApellidoAscNombreAsc();
}
