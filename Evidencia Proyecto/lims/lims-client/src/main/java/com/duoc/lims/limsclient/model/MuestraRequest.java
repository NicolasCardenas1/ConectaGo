package com.duoc.lims.limsclient.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Cuerpo de POST /api/muestras. */
public class MuestraRequest {
    private String tipoMuestra;
    private Integer idInvestigador;
    private String procedencia;            // sitio de muestreo
    private String prioridad;
    private String observaciones;
    private LocalDateTime fechaToma;
    private String condicionRecepcion;
    private String ubicacionAlmacenamiento;
    private LocalDate fechaVencimiento;    // conservar hasta
    private String entregadaPor;
    private Boolean firmaRecepcion;

    public String getTipoMuestra() { return tipoMuestra; }
    public void setTipoMuestra(String tipoMuestra) { this.tipoMuestra = tipoMuestra; }

    public Integer getIdInvestigador() { return idInvestigador; }
    public void setIdInvestigador(Integer idInvestigador) { this.idInvestigador = idInvestigador; }

    public String getProcedencia() { return procedencia; }
    public void setProcedencia(String procedencia) { this.procedencia = procedencia; }

    public String getPrioridad() { return prioridad; }
    public void setPrioridad(String prioridad) { this.prioridad = prioridad; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }

    public LocalDateTime getFechaToma() { return fechaToma; }
    public void setFechaToma(LocalDateTime fechaToma) { this.fechaToma = fechaToma; }

    public String getCondicionRecepcion() { return condicionRecepcion; }
    public void setCondicionRecepcion(String condicionRecepcion) { this.condicionRecepcion = condicionRecepcion; }

    public String getUbicacionAlmacenamiento() { return ubicacionAlmacenamiento; }
    public void setUbicacionAlmacenamiento(String ubicacionAlmacenamiento) { this.ubicacionAlmacenamiento = ubicacionAlmacenamiento; }

    public LocalDate getFechaVencimiento() { return fechaVencimiento; }
    public void setFechaVencimiento(LocalDate fechaVencimiento) { this.fechaVencimiento = fechaVencimiento; }

    public String getEntregadaPor() { return entregadaPor; }
    public void setEntregadaPor(String entregadaPor) { this.entregadaPor = entregadaPor; }

    public Boolean getFirmaRecepcion() { return firmaRecepcion; }
    public void setFirmaRecepcion(Boolean firmaRecepcion) { this.firmaRecepcion = firmaRecepcion; }
}
