package com.duoc.lims.limsclient.model;

public class UsuarioResponse {
    private Integer id;
    private String nombre;
    private String apellido;
    private String email;
    private String username;
    private String nombreRol;
    private String nombreCentro;
    private boolean activo;
    private boolean requiereResetPassword;

    public UsuarioResponse() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getApellido() { return apellido; }
    public void setApellido(String apellido) { this.apellido = apellido; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getNombreRol() { return nombreRol; }
    public void setNombreRol(String nombreRol) { this.nombreRol = nombreRol; }

    public String getNombreCentro() { return nombreCentro; }
    public void setNombreCentro(String nombreCentro) { this.nombreCentro = nombreCentro; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    public boolean isRequiereResetPassword() { return requiereResetPassword; }
    public void setRequiereResetPassword(boolean requiereResetPassword) { this.requiereResetPassword = requiereResetPassword; }
}