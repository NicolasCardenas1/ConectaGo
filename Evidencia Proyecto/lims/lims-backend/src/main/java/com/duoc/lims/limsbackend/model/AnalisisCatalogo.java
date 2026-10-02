package com.duoc.lims.limsbackend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "analisis_catalogo")
@Getter
@Setter
@NoArgsConstructor
public class AnalisisCatalogo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_analisis")
    private Integer id;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "descripcion", length = 255)
    private String descripcion;

    @Column(name = "unidad_medida", nullable = false, length = 20)
    private String unidadMedida;

    @Column(name = "metodo_referencia", length = 150)
    private String metodoReferencia;

    @Column(name = "tiempo_estimado_hrs", precision = 6, scale = 2)
    private BigDecimal tiempoEstimadoHrs;

    @Column(name = "valor_min_normal", nullable = false, precision = 12, scale = 4)
    private BigDecimal valorMinNormal;

    @Column(name = "valor_max_normal", nullable = false, precision = 12, scale = 4)
    private BigDecimal valorMaxNormal;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;
}