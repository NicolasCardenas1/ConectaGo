package com.duoc.lims.limsclient.model;

public class MuestraRequest {
    private String tipoMuestra;
    private String procedencia;
    private String prioridad;
    private String observaciones;
    private Integer idUsuarioRegistro;

    public String getTipoMuestra() { return tipoMuestra; }
    public void setTipoMuestra(String tipoMuestra) { this.tipoMuestra = tipoMuestra; }

    public String getProcedencia() { return procedencia; }
    public void setProcedencia(String procedencia) { this.procedencia = procedencia; }

    public String getPrioridad() { return prioridad; }
    public void setPrioridad(String prioridad) { this.prioridad = prioridad; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }

    public Integer getIdUsuarioRegistro() { return idUsuarioRegistro; }
    public void setIdUsuarioRegistro(Integer idUsuarioRegistro) { this.idUsuarioRegistro = idUsuarioRegistro; }
}