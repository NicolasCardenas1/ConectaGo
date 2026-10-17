package com.duoc.lims.limsclient.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Muestra {
    private Integer id;
    private String codigoUnico;
    private String tipoMuestra;
    private String procedencia;
    private String prioridad;
    private String estado;
    private LocalDateTime fechaRecepcion;
    private String usuarioRegistro;
    private String observaciones;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getCodigoUnico() { return codigoUnico; }
    public void setCodigoUnico(String codigoUnico) { this.codigoUnico = codigoUnico; }

    public String getTipoMuestra() { return tipoMuestra; }
    public void setTipoMuestra(String tipoMuestra) { this.tipoMuestra = tipoMuestra; }

    public String getProcedencia() { return procedencia; }
    public void setProcedencia(String procedencia) { this.procedencia = procedencia; }

    public String getPrioridad() { return prioridad; }
    public void setPrioridad(String prioridad) { this.prioridad = prioridad; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getFechaRecepcion() { return fechaRecepcion; }
    public void setFechaRecepcion(LocalDateTime fechaRecepcion) { this.fechaRecepcion = fechaRecepcion; }

    public String getUsuarioRegistro() { return usuarioRegistro; }
    public void setUsuarioRegistro(String usuarioRegistro) { this.usuarioRegistro = usuarioRegistro; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }

    // ---- v4: investigador y almacenamiento ----
    private Integer idInvestigador;
    private String investigador;
    private String institucion;
    private LocalDateTime fechaToma;
    private String condicionRecepcion;
    private String ubicacionAlmacenamiento;
    private java.time.LocalDate fechaVencimiento;

    public Integer getIdInvestigador() { return idInvestigador; }
    public void setIdInvestigador(Integer idInvestigador) { this.idInvestigador = idInvestigador; }

    public String getInvestigador() { return investigador; }
    public void setInvestigador(String investigador) { this.investigador = investigador; }

    public String getInstitucion() { return institucion; }
    public void setInstitucion(String institucion) { this.institucion = institucion; }

    public LocalDateTime getFechaToma() { return fechaToma; }
    public void setFechaToma(LocalDateTime fechaToma) { this.fechaToma = fechaToma; }

    public String getCondicionRecepcion() { return condicionRecepcion; }
    public void setCondicionRecepcion(String condicionRecepcion) { this.condicionRecepcion = condicionRecepcion; }

    public String getUbicacionAlmacenamiento() { return ubicacionAlmacenamiento; }
    public void setUbicacionAlmacenamiento(String ubicacionAlmacenamiento) { this.ubicacionAlmacenamiento = ubicacionAlmacenamiento; }

    public java.time.LocalDate getFechaVencimiento() { return fechaVencimiento; }
    public void setFechaVencimiento(java.time.LocalDate fechaVencimiento) { this.fechaVencimiento = fechaVencimiento; }
}