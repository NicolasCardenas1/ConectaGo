package com.duoc.lims.limsbackend.model;

import com.duoc.lims.limsbackend.model.converter.TipoCentroConverter;
import com.duoc.lims.limsbackend.model.enums.TipoCentro;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "centros")
@Getter
@Setter
@NoArgsConstructor
public class Centro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_centro")
    private Integer id;

    @Column(name = "nombre_centro", nullable = false, length = 150)
    private String nombreCentro;

    @Convert(converter = TipoCentroConverter.class)
    @Column(name = "tipo_centro", nullable = false)
    private TipoCentro tipoCentro;

    @Column(name = "direccion", length = 255)
    private String direccion;

    @Column(name = "telefono", length = 30)
    private String telefono;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;
}