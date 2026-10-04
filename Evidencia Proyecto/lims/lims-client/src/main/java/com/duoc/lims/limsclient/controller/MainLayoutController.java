package com.duoc.lims.limsclient.controller;

import com.duoc.lims.limsclient.Navigator;
import com.duoc.lims.limsclient.model.UsuarioResponse;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

/**
 * Marco principal: barra lateral + área de contenido.
 * Se carga una sola vez después del login; Navigator solo reemplaza el contenido central.
 */
public class MainLayoutController {

    @FXML private StackPane contenido;
    @FXML private Button btnMuestras;
    @FXML private Button btnAprobaciones;
    @FXML private Label lblUsuario;
    @FXML private Label lblRol;

    @FXML
    public void initialize() {
        UsuarioResponse usuario = Navigator.getUsuarioActual();
        if (usuario != null) {
            lblUsuario.setText(usuario.getNombre() + " " + usuario.getApellido());
            lblRol.setText(usuario.getNombreRol());
        }
    }

    /** Reemplaza la vista que se muestra a la derecha de la barra lateral. */
    public void setContenido(Node vista) {
        contenido.getChildren().setAll(vista);
    }

    /** Resalta en la barra lateral la sección actual ("muestras" o "aprobaciones"). */
    public void marcarSeccion(String seccion) {
        btnMuestras.getStyleClass().remove("activo");
        btnAprobaciones.getStyleClass().remove("activo");
        if ("muestras".equals(seccion)) {
            btnMuestras.getStyleClass().add("activo");
        } else if ("aprobaciones".equals(seccion)) {
            btnAprobaciones.getStyleClass().add("activo");
        }
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
    private void onCerrarSesion() {
        Navigator.irALogin();
    }
}
