package com.duoc.lims.limsclient.controller;

import com.duoc.lims.limsclient.Navigator;
import com.duoc.lims.limsclient.model.UsuarioResponse;
import com.duoc.lims.limsclient.service.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

import java.util.List;

/**
 * Marco principal: barra lateral + área de contenido.
 * Se carga una sola vez después del login; Navigator solo reemplaza el contenido central.
 * RF06: la barra lateral muestra solo las opciones que corresponden al rol del usuario.
 */
public class MainLayoutController {

    @FXML private StackPane contenido;
    @FXML private Button btnInicio;
    @FXML private Button btnSitios;
    @FXML private Button btnEquipos;
    @FXML private Button btnMuestras;
    @FXML private Button btnAprobaciones;
    @FXML private Button btnUsuarios;
    @FXML private Button btnInvestigadores;
    @FXML private Button btnCatalogo;
    @FXML private Button btnReportes;
    @FXML private Label lblUsuario;
    @FXML private Label lblRol;

    @FXML
    public void initialize() {
        UsuarioResponse usuario = Navigator.getUsuarioActual();
        if (usuario != null) {
            lblUsuario.setText(usuario.getNombre() + " " + usuario.getApellido());
            lblRol.setText(usuario.getNombreRol());
        }

        // Opciones según el rol
        mostrar(btnAprobaciones, Navigator.puedeAprobar());   // Supervisor y Administrador
        mostrar(btnUsuarios, Navigator.esAdministrador());    // solo Administrador
        mostrar(btnReportes, Navigator.puedeAprobar());       // Supervisor y Administrador
    }

    /** Reemplaza la vista que se muestra a la derecha de la barra lateral. */
    public void setContenido(Node vista) {
        contenido.getChildren().setAll(vista);
    }

    /** Resalta en la barra lateral la sección actual ("muestras", "aprobaciones" o "usuarios"). */
    public void marcarSeccion(String seccion) {
        for (Button b : List.of(btnInicio, btnMuestras, btnAprobaciones, btnUsuarios, btnInvestigadores, btnCatalogo,
                btnReportes, btnSitios, btnEquipos)) {
            b.getStyleClass().remove("activo");
        }
        switch (seccion) {
            case "inicio" -> btnInicio.getStyleClass().add("activo");
            case "sitios" -> btnSitios.getStyleClass().add("activo");
            case "equipos" -> btnEquipos.getStyleClass().add("activo");
            case "muestras" -> btnMuestras.getStyleClass().add("activo");
            case "aprobaciones" -> btnAprobaciones.getStyleClass().add("activo");
            case "usuarios" -> btnUsuarios.getStyleClass().add("activo");
            case "investigadores" -> btnInvestigadores.getStyleClass().add("activo");
            case "catalogo" -> btnCatalogo.getStyleClass().add("activo");
            case "reportes" -> btnReportes.getStyleClass().add("activo");
            default -> { }
        }
    }

    @FXML
    private void onInicio() {
        Navigator.irADashboard();
    }

    @FXML
    private void onSitios() {
        Navigator.irASitios();
    }

    @FXML
    private void onEquipos() {
        Navigator.irAEquipos();
    }

    @FXML
    private void onMuestras() {
        Navigator.irAListado();
    }

    @FXML
    private void onAprobaciones() {
        Navigator.irAAprobaciones();
    }

    @FXML
    private void onInvestigadores() {
        Navigator.irAInvestigadores();
    }

    @FXML
    private void onCatalogo() {
        Navigator.irACatalogo();
    }

    @FXML
    private void onReportes() {
        Navigator.irAReportes();
    }

    @FXML
    private void onUsuarios() {
        Navigator.irAUsuarios();
    }

    @FXML
    private void onCerrarSesion() {
        // Se avisa al backend en segundo plano (no bloquea la interfaz) y se vuelve al login.
        ApiClient api = new ApiClient();
        String token = ApiClient.tokenActual();   // se captura antes de que irALogin() lo borre
        Thread hilo = new Thread(() -> api.cerrarSesion(token));
        hilo.setDaemon(true);
        hilo.start();
        Navigator.irALogin();
    }

    private void mostrar(Button boton, boolean visible) {
        boton.setVisible(visible);
        boton.setManaged(visible);   // si no se muestra, tampoco ocupa espacio
    }
}
