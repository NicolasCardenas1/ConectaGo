package com.duoc.lims.limsbackend.model;

import com.duoc.lims.limsbackend.model.converter.EstadoAprobacionConverter;
import com.duoc.lims.limsbackend.model.enums.EstadoAprobacion;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "aprobaciones")
@Getter
@Setter
@NoArgsConstructor
public class Aprobacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_aprobacion")
    private Integer id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_resultado")
    private Resultado resultado;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_supervisor")
    private Usuario supervisor;

    @Convert(converter = EstadoAprobacionConverter.class)
    @Column(name = "estado_aprobacion", nullable = false)
    private EstadoAprobacion estadoAprobacion;

    @Column(name = "comentario", length = 255)
    private String comentario;

    @CreationTimestamp
    @Column(name = "fecha_aprobacion", nullable = false, updatable = false)
    private LocalDateTime fechaAprobacion;
}