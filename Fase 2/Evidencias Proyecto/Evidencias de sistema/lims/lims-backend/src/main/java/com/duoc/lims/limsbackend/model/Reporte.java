package com.duoc.lims.limsbackend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** RF05 — Registro de cada informe PDF emitido (se conservan todas las versiones). */
@Entity
@Table(name = "reportes")
@Getter
@Setter
@NoArgsConstructor
public class Reporte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_reporte")
    private Integer id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_muestra")
    private Muestra muestra;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_generador")
    private Usuario usuarioGenerador;

    @CreationTimestamp
    @Column(name = "fecha_generacion", nullable = false, updatable = false)
    private LocalDateTime fechaGeneracion;

    @Column(name = "ruta_archivo", nullable = false, length = 255)
    private String rutaArchivo;

    @Column(name = "version", nullable = false)
    private Integer version;

    /** v7: código impreso en el informe para verificarlo en el LIMS (null en informes anteriores a v7). */
    @Column(name = "codigo_verificacion", length = 9, unique = true)
    private String codigoVerificacion;

    /** v7: huella SHA-256 del PDF firmado tal como se emitió (detecta cualquier modificación del archivo). */
    @Column(name = "hash_sha256", length = 64)
    private String hashSha256;
}
