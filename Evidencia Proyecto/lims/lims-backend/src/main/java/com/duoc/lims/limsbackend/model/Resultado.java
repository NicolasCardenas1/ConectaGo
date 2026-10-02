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

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_ingreso")
    private Usuario usuarioIngreso;

    @CreationTimestamp
    @Column(name = "fecha_ingreso", nullable = false, updatable = false)
    private LocalDateTime fechaIngreso;

    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;
}