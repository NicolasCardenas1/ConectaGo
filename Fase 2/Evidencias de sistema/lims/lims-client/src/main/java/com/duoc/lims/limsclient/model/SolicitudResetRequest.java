package com.duoc.lims.limsclient.model;

public class SolicitudResetRequest {
    private String username;

    public SolicitudResetRequest() {}

    public SolicitudResetRequest(String username) {
        this.username = username;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}