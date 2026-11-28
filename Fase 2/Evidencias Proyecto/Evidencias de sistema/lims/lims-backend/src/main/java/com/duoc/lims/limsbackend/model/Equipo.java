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
 * v6 — También los equipos de terreno (uso): cada muestra indica con qué equipo se tomó, y cada
 * calibración queda en el historial equipo_calibraciones.
 */
@Entity
@Table(name = "equipos")
@Getter
@Setter
@NoArgsConstructor
public class Equipo {

    public static final String USO_TERRENO = "Terreno";
    public static final String USO_LABORATORIO = "Laboratorio";
    public static final String USO_AMBOS = "Ambos";

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

    /** v6: Terreno (toma de muestras), Laboratorio (análisis) o Ambos. */
    @Column(name = "uso", nullable = false, length = 20)
    private String uso = USO_LABORATORIO;

    @Column(name = "fecha_ultima_calibracion")
    private LocalDate fechaUltimaCalibracion;

    @Column(name = "fecha_proxima_calibracion")
    private LocalDate fechaProximaCalibracion;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    @Column(name = "fecha_creacion", nullable = false, insertable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    /**
     * Vencida = la próxima calibración ya pasó. Sin fecha registrada no cuenta como vencida (los
     * equipos de terreno como botellas o cuadrantes no se calibran), pero para medir resultados
     * {@code EquipoService.validarParaAnalisis} exige una calibración registrada.
     */
    public boolean isCalibracionVencida() {
        return fechaProximaCalibracion != null && fechaProximaCalibracion.isBefore(LocalDate.now());
    }

    /** Texto legible que se guarda también en resultados.instrumento_utilizado. */
    public String getEtiqueta() {
        return codigo + " · " + nombre;
    }

    /** v6: se puede usar para tomar muestras en terreno. */
    public boolean isUsoTerreno() {
        return USO_TERRENO.equals(uso) || USO_AMBOS.equals(uso);
    }

    /** v6: se puede usar para medir resultados en el laboratorio. */
    public boolean isUsoLaboratorio() {
        return USO_LABORATORIO.equals(uso) || USO_AMBOS.equals(uso);
    }
}
