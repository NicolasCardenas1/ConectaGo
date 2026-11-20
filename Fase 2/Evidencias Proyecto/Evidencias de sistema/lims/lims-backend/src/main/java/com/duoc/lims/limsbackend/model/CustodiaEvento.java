package com.duoc.lims.limsbackend.model;

import com.duoc.lims.limsbackend.model.converter.TipoEventoCustodiaConverter;
import com.duoc.lims.limsbackend.model.enums.TipoEventoCustodia;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Evento de la cadena de custodia: quién entrega la muestra, quién la recibe y firma
 * (confirmación + fecha y hora), dónde queda y en qué condición.
 */
@Entity
@Table(name = "custodia_eventos")
@Getter
@Setter
@NoArgsConstructor
public class CustodiaEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_custodia")
    private Integer id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_muestra")
    private Muestra muestra;

    @Convert(converter = TipoEventoCustodiaConverter.class)
    @Column(name = "tipo_evento", nullable = false, length = 40)
    private TipoEventoCustodia tipoEvento;

    @Column(name = "entregado_por", nullable = false, length = 150)
    private String entregadoPor;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_recibe")
    private Usuario usuarioRecibe;

    @Column(name = "ubicacion", length = 100)
    private String ubicacion;

    @Column(name = "condicion", length = 150)
    private String condicion;

    @Column(name = "confirmado", nullable = false)
    private boolean confirmado;

    @CreationTimestamp
    @Column(name = "fecha_hora", nullable = false, updatable = false)
    private LocalDateTime fechaHora;

    @Column(name = "observaciones", length = 255)
    private String observaciones;
}
