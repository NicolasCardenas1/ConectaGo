package com.duoc.lims.limsbackend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * v5 — Una versión de un resultado. Cada ingreso o corrección agrega una fila: el dato anterior
 * nunca se pierde y queda registrado quién lo cambió, cuándo, con qué equipo y por qué.
 */
@Entity
@Table(name = "resultado_versiones",
        uniqueConstraints = @UniqueConstraint(columnNames = {"id_resultado", "version"}))
@Getter
@Setter
@NoArgsConstructor
public class ResultadoVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_version")
    private Integer id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_resultado")
    private Resultado resultado;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "valor_resultado", nullable = false, precision = 12, scale = 4)
    private BigDecimal valorResultado;

    @Column(name = "dentro_rango", nullable = false)
    private Boolean dentroRango;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_equipo")
    private Equipo equipo;

    @Column(name = "instrumento_utilizado", length = 100)
    private String instrumentoUtilizado;

    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "es_anomalo", nullable = false)
    private boolean anomalo;

    @Column(name = "detalle_anomalia", length = 255)
    private String detalleAnomalia;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    @CreationTimestamp
    @Column(name = "fecha", nullable = false, updatable = false)
    private LocalDateTime fecha;

    @Column(name = "motivo_cambio", length = 255)
    private String motivoCambio;
}
