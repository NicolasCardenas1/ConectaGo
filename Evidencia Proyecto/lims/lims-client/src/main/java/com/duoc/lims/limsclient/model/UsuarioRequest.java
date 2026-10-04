package com.duoc.lims.limsclient.model;

public class UsuarioRequest {
    private Integer idCentro;
    private String nombre;
    private String apellido;
    private String email;
    private String username;
    private String password;
    private Integer idRol;

    public UsuarioRequest() {}

    public UsuarioRequest(Integer idCentro, String nombre, String apellido, String email,
                           String username, String password, Integer idRol) {
        this.idCentro = idCentro;
        this.nombre = nombre;
        this.apellido = apellido;
        this.email = email;
        this.username = username;
        this.password = password;
        this.idRol = idRol;
    }

    public Integer getIdCentro() { return idCentro; }
    public void setIdCentro(Integer idCentro) { this.idCentro = idCentro; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getApellido() { return apellido; }
    public void setApellido(String apellido) { this.apellido = apellido; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Integer getIdRol() { return idRol; }
    public void setIdRol(Integer idRol) { this.idRol = idRol; }
}