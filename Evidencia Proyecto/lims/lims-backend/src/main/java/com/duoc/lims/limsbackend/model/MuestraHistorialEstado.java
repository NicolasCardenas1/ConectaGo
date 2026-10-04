package com.duoc.lims.limsbackend.model;

import com.duoc.lims.limsbackend.model.converter.EstadoMuestraConverter;
import com.duoc.lims.limsbackend.model.enums.EstadoMuestra;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Registro de cada cambio de estado de una muestra (quién, cuándo, de qué estado a cuál y por qué).
 * Es el "audit trail" acotado que exige ISO/IEC 17025 sobre la trazabilidad de la muestra.
 */
@Entity
@Table(name = "muestra_historial_estado")
@Getter
@Setter
@NoArgsConstructor
public class MuestraHistorialEstado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_historial")
    private Integer id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_muestra")
    private Muestra muestra;

    @Convert(converter = EstadoMuestraConverter.class)
    @Column(name = "estado_anterior")
    private EstadoMuestra estadoAnterior;          // null en el registro inicial (recepción)

    @Convert(converter = EstadoMuestraConverter.class)
    @Column(name = "estado_nuevo", nullable = false)
    private EstadoMuestra estadoNuevo;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;                        // quién provocó el cambio

    @CreationTimestamp
    @Column(name = "fecha_cambio", nullable = false, updatable = false)
    private LocalDateTime fechaCambio;

    @Column(name = "comentario", length = 255)
    private String comentario;
}
