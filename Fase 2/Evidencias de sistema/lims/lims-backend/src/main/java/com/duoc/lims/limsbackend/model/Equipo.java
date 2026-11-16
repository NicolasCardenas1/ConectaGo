package com.duoc.lims.limsbackend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * v5 — Equipo del laboratorio con su calibración. Cada resultado indica con qué equipo se midió;
 * un equipo con la calibración vencida no puede usarse (trazabilidad metrológica, ISO/IEC 17025).
 */
@Entity
@Table(name = "equipos")
@Getter
@Setter
@NoArgsConstructor
public class Equipo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_equipo")
    private Integer id;

    @Column(name = "codigo", nullable = false, unique = true, length = 30)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "marca", length = 80)
    private String marca;

    @Column(name = "modelo", length = 80)
    private String modelo;

    @Column(name = "numero_serie", length = 80)
    private String numeroSerie;

    @Column(name = "fecha_ultima_calibracion")
    private LocalDate fechaUltimaCalibracion;

    @Column(name = "fecha_proxima_calibracion")
    private LocalDate fechaProximaCalibracion;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    @Column(name = "fecha_creacion", nullable = false, insertable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    /** Vencida = la próxima calibración ya pasó. Sin fecha registrada se considera no vencida. */
    public boolean isCalibracionVencida() {
        return fechaProximaCalibracion != null && fechaProximaCalibracion.isBefore(LocalDate.now());
    }

    /** Texto legible que se guarda también en resultados.instrumento_utilizado. */
    public String getEtiqueta() {
        return codigo + " · " + nombre;
    }
}
