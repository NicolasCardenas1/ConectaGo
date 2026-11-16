package com.duoc.lims.limsbackend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "resultados")
@Getter
@Setter
@NoArgsConstructor
public class Resultado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_resultado")
    private Integer id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_muestra_analisis")
    private MuestraAnalisis muestraAnalisis;

    @Column(name = "valor_resultado", nullable = false, precision = 12, scale = 4)
    private BigDecimal valorResultado;

    @Column(name = "dentro_rango", nullable = false)
    private Boolean dentroRango;

    @Column(name = "instrumento_utilizado", length = 100)
    private String instrumentoUtilizado;

    // v5: equipo calibrado con que se midió (null en resultados anteriores a v5)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_equipo")
    private Equipo equipo;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_ingreso")
    private Usuario usuarioIngreso;

    @CreationTimestamp
    @Column(name = "fecha_ingreso", nullable = false, updatable = false)
    private LocalDateTime fechaIngreso;

    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;

    // v5: versión vigente; el detalle de cada versión está en resultado_versiones
    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "fecha_modificacion")
    private LocalDateTime fechaModificacion;

    // v5: detección de anomalías contra el historial del mismo parámetro y sitio
    @Column(name = "puntaje_anomalia", precision = 8, scale = 2)
    private BigDecimal puntajeAnomalia;

    @Column(name = "es_anomalo", nullable = false)
    private boolean anomalo;

    @Column(name = "detalle_anomalia", length = 255)
    private String detalleAnomalia;
}