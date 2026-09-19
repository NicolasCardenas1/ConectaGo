package com.duoc.lims.limsbackend.model;

import com.duoc.lims.limsbackend.model.converter.EstadoMuestraConverter;
import com.duoc.lims.limsbackend.model.converter.PrioridadConverter;
import com.duoc.lims.limsbackend.model.converter.TipoMuestraConverter;
import com.duoc.lims.limsbackend.model.converter.TipoRegistroConverter;
import com.duoc.lims.limsbackend.model.enums.EstadoMuestra;
import com.duoc.lims.limsbackend.model.enums.Prioridad;
import com.duoc.lims.limsbackend.model.enums.TipoMuestra;
import com.duoc.lims.limsbackend.model.enums.TipoRegistro;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "muestras", uniqueConstraints = @UniqueConstraint(columnNames = {"id_centro", "codigo_unico"}))
@Getter
@Setter
@NoArgsConstructor
public class Muestra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_muestra")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_centro", nullable = false)
    private Centro centro;

    @Column(name = "codigo_unico", nullable = false, length = 30)
    private String codigoUnico;

    @Convert(converter = TipoMuestraConverter.class)
    @Column(name = "tipo_muestra", nullable = false)
    private TipoMuestra tipoMuestra;

    @Column(name = "procedencia", length = 150)
    private String procedencia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_registro", nullable = false)
    private Usuario usuarioRegistro;

    @Column(name = "fecha_toma")
    private LocalDateTime fechaToma;

    @CreationTimestamp
    @Column(name = "fecha_recepcion", nullable = false, updatable = false)
    private LocalDateTime fechaRecepcion;

    @Convert(converter = EstadoMuestraConverter.class)
    @Column(name = "estado", nullable = false)
    private EstadoMuestra estado = EstadoMuestra.RECIBIDA;

    @Convert(converter = TipoRegistroConverter.class)
    @Column(name = "tipo_registro", nullable = false)
    private TipoRegistro tipoRegistro = TipoRegistro.MUESTRA;

    @Convert(converter = PrioridadConverter.class)
    @Column(name = "prioridad", nullable = false)
    private Prioridad prioridad = Prioridad.MEDIA;

    @Column(name = "condicion_recepcion", length = 150)
    private String condicionRecepcion;

    @Column(name = "ubicacion_almacenamiento", length = 100)
    private String ubicacionAlmacenamiento;

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;
}