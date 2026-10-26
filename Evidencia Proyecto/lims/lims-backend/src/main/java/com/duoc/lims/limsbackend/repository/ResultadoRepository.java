package com.duoc.lims.limsbackend.repository;

import com.duoc.lims.limsbackend.model.Resultado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ResultadoRepository extends JpaRepository<Resultado, Integer> {

    boolean existsByMuestraAnalisis_Id(Integer idMuestraAnalisis);

    Optional<Resultado> findByMuestraAnalisis_Id(Integer idMuestraAnalisis);

    // v5: historial de un parámetro (detección de anomalías y tendencias)
    List<Resultado> findByMuestraAnalisis_Analisis_Id(Integer idAnalisis);

    // Resultados cuya versión vigente todavía no tiene evaluación del supervisor.
    // v5: un resultado rechazado y luego corregido (versión nueva) vuelve a quedar pendiente.
    @Query("""
           SELECT r FROM Resultado r
           WHERE NOT EXISTS (SELECT a FROM Aprobacion a
                             WHERE a.resultado = r AND a.versionResultado = r.version)
           ORDER BY r.fechaIngreso
           """)
    List<Resultado> findPendientesDeAprobacion();
}