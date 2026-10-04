package com.duoc.lims.limsbackend.repository;

import com.duoc.lims.limsbackend.model.Resultado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ResultadoRepository extends JpaRepository<Resultado, Integer> {

    boolean existsByMuestraAnalisis_Id(Integer idMuestraAnalisis);

    Optional<Resultado> findByMuestraAnalisis_Id(Integer idMuestraAnalisis);

    // Resultados que todavía no tienen ninguna evaluación del supervisor.
    @Query("""
           SELECT r FROM Resultado r
           WHERE NOT EXISTS (SELECT a FROM Aprobacion a WHERE a.resultado = r)
           ORDER BY r.fechaIngreso
           """)
    List<Resultado> findPendientesDeAprobacion();
}