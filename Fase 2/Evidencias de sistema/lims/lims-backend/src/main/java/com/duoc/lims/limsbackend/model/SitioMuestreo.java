package com.duoc.lims.limsbackend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * v5 — Sitio de muestreo georreferenciado (latitud/longitud en grados decimales).
 * Permite ver las muestras en un mapa y comparar resultados del mismo sitio en el tiempo.
 */
@Entity
@Table(name = "sitios_muestreo")
@Getter
@Setter
@NoArgsConstructor
public class SitioMuestreo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_sitio")
    private Integer id;

    @Column(name = "codigo", nullable = false, unique = true, length = 20)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "latitud", nullable = false, precision = 9, scale = 6)
    private BigDecimal latitud;

    @Column(name = "longitud", nullable = false, precision = 9, scale = 6)
    private BigDecimal longitud;

    @Column(name = "tipo_ambiente", nullable = false, length = 40)
    private String tipoAmbiente;

    @Column(name = "comuna", length = 80)
    private String comuna;

    @Column(name = "region", length = 80)
    private String region;

    @Column(name = "descripcion", length = 255)
    private String descripcion;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    @Column(name = "fecha_creacion", nullable = false, insertable = false, updatable = false)
    private LocalDateTime fechaCreacion;
}
