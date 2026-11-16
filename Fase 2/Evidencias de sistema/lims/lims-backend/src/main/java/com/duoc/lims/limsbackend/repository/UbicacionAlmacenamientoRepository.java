package com.duoc.lims.limsbackend.repository;

import com.duoc.lims.limsbackend.model.UbicacionAlmacenamiento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UbicacionAlmacenamientoRepository extends JpaRepository<UbicacionAlmacenamiento, Integer> {

    List<UbicacionAlmacenamiento> findAllByOrderByNombreAsc();

    boolean existsByNombreIgnoreCase(String nombre);
}
