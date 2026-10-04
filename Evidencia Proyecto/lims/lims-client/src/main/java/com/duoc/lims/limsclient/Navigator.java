package com.duoc.lims.limsclient;

import com.duoc.lims.limsclient.model.UsuarioResponse;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.stage.Stage;

import java.io.IOException;

public class Navigator {

    private static Stage stage;
    private static UsuarioResponse usuarioActual;

    public static void setStage(Stage primaryStage) {
        stage = primaryStage;
    }

    public static void setUsuarioActual(UsuarioResponse usuario) {
        usuarioActual = usuario;
    }

    public static UsuarioResponse getUsuarioActual() {
        return usuarioActual;
    }

    public static void irALogin() {
        usuarioActual = null;
        cambiarVista("login-view.fxml", "LIMS - Inicio de Sesión");
    }

    public static void irACrearUsuario() {
        cambiarVista("crear-usuario-view.fxml", "LIMS - Crear Usuario");
    }

    public static void irARecuperarPassword() {
        cambiarVista("recuperar-password-view.fxml", "LIMS - Recuperar Contraseña");
    }

    public static void irAListado() {
        cambiarVista("listado-muestras.fxml", "LIMS - Listado de Muestras");
    }

    public static void irARegistroMuestra() {
        cambiarVista("registro-muestra.fxml", "LIMS - Registrar Nueva Muestra");
    }

    private static void cambiarVista(String fxml, String titulo) {
        try {
            FXMLLoader loader = new FXMLLoader(Navigator.class.getResource(fxml));
            Parent root = loader.load();
            stage.getScene().setRoot(root);
            stage.setTitle(titulo);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo cargar la vista: " + fxml, e);
        }
    }
}