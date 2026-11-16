package com.duoc.lims.limsbackend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** v5 — Ubicación física de almacenamiento (lista desplegable al registrar o trasladar una muestra). */
@Entity
@Table(name = "ubicaciones_almacenamiento")
@Getter
@Setter
@NoArgsConstructor
public class UbicacionAlmacenamiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ubicacion")
    private Integer id;

    @Column(name = "nombre", nullable = false, unique = true, length = 100)
    private String nombre;

    @Column(name = "tipo", nullable = false, length = 40)
    private String tipo;

    @Column(name = "temperatura", length = 30)
    private String temperatura;

    @Column(name = "descripcion", length = 255)
    private String descripcion;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;
}
