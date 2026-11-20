package com.duoc.lims.limsbackend.model;

import com.duoc.lims.limsbackend.model.converter.EstadoMuestraAnalisisConverter;
import com.duoc.lims.limsbackend.model.enums.EstadoMuestraAnalisis;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "muestra_analisis")
@Getter
@Setter
@NoArgsConstructor
public class MuestraAnalisis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_muestra_analisis")
    private Integer id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_muestra")
    private Muestra muestra;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_analisis")
    private AnalisisCatalogo analisis;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_analista_asignado")
    private Usuario analistaAsignado;

    @Convert(converter = EstadoMuestraAnalisisConverter.class)
    @Column(name = "estado", nullable = false)
    private EstadoMuestraAnalisis estado = EstadoMuestraAnalisis.PENDIENTE;

    @Column(name = "fecha_asignacion")
    private LocalDateTime fechaAsignacion;

    @Column(name = "fecha_completado")
    private LocalDateTime fechaCompletado;
}