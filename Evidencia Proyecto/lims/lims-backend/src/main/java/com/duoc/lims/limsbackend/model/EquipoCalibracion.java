package com.duoc.lims.limsbackend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * v6 — Una calibración de un equipo. Nunca se sobrescribe: cada calibración nueva es una fila nueva,
 * así se puede demostrar qué calibración estaba vigente cuando se tomó o midió una muestra (ISO/IEC 17025).
 */
@Entity
@Table(name = "equipo_calibraciones")
@Getter
@Setter
@NoArgsConstructor
public class EquipoCalibracion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_calibracion")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_equipo", nullable = false)
    private Equipo equipo;

    @Column(name = "fecha_calibracion", nullable = false)
    private LocalDate fechaCalibracion;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Column(name = "realizada_por", length = 120)
    private String realizadaPor;

    @Column(name = "certificado", length = 80)
    private String certificado;

    @Column(name = "observaciones", length = 255)
    private String observaciones;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    @Column(name = "fecha_registro", nullable = false, insertable = false, updatable = false)
    private LocalDateTime fechaRegistro;
}
