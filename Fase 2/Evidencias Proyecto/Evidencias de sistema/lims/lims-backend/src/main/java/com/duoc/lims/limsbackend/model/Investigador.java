package com.duoc.lims.limsbackend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Investigador que encarga el muestreo y recibe el informe de resultados.
 * No es usuario del sistema (no inicia sesión).
 */
@Entity
@Table(name = "investigadores")
@Getter
@Setter
@NoArgsConstructor
public class Investigador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_investigador")
    private Integer id;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "apellido", nullable = false, length = 100)
    private String apellido;

    @Column(name = "institucion", length = 150)
    private String institucion;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "telefono", length = 30)
    private String telefono;

    @Column(name = "proyecto", length = 200)
    private String proyecto;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    @Column(name = "fecha_creacion", nullable = false, insertable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    public String getNombreCompleto() {
        return nombre + " " + apellido;
    }
}
