package com.duoc.lims.limsclient;

import com.duoc.lims.limsclient.controller.DetalleMuestraController;
import com.duoc.lims.limsclient.controller.MainLayoutController;
import com.duoc.lims.limsclient.model.UsuarioResponse;
import javafx.application.HostServices;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Navegación de la app.
 * - Login, Crear usuario y Recuperar contraseña ocupan la ventana completa.
 * - El resto de pantallas se muestran DENTRO del marco principal (barra lateral + contenido).
 */
public class Navigator {

    private static Stage stage;
    private static UsuarioResponse usuarioActual;
    private static MainLayoutController marco;   // null mientras no se haya iniciado sesión
    private static HostServices hostServices;

    public static void setStage(Stage primaryStage) {
        stage = primaryStage;
    }

    public static void setHostServices(HostServices servicios) {
        hostServices = servicios;
    }

    /** Abre un archivo (ej. un PDF) con el programa predeterminado del sistema operativo. */
    public static void abrirDocumento(Path archivo) {
        hostServices.showDocument(archivo.toUri().toString());
    }

    public static void setUsuarioActual(UsuarioResponse usuario) {
        usuarioActual = usuario;
    }

    public static UsuarioResponse getUsuarioActual() {
        return usuarioActual;
    }

    // ---------- Pantallas a ventana completa (sin barra lateral) ----------

    public static void irALogin() {
        usuarioActual = null;
        marco = null;
        cambiarVentanaCompleta("login-view.fxml", "LIMS - Inicio de Sesión");
    }

    public static void irACrearUsuario() {
        cambiarVentanaCompleta("crear-usuario-view.fxml", "LIMS - Crear Usuario");
    }

    public static void irARecuperarPassword() {
        cambiarVentanaCompleta("recuperar-password-view.fxml", "LIMS - Recuperar Contraseña");
    }

    // ---------- Pantallas dentro del marco (con barra lateral) ----------

    public static void irAListado() {
        mostrarEnMarco("listado-muestras.fxml", "Listado de Muestras", "muestras");
    }

    public static void irARegistroMuestra() {
        mostrarEnMarco("registro-muestra.fxml", "Registrar Nueva Muestra", "muestras");
    }

    public static void irAAprobaciones() {
        mostrarEnMarco("aprobaciones.fxml", "Aprobación de Resultados", "aprobaciones");
    }

    public static void irADetalleMuestra(int idMuestra) {
        DetalleMuestraController controller =
                mostrarEnMarco("detalle-muestra.fxml", "Detalle de Muestra", "muestras");
        controller.cargarMuestra(idMuestra);
    }

    // ---------- Helpers ----------

    private static void cambiarVentanaCompleta(String fxml, String titulo) {
        try {
            FXMLLoader loader = new FXMLLoader(Navigator.class.getResource(fxml));
            Parent root = loader.load();
            stage.getScene().setRoot(root);
            stage.setTitle(titulo);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo cargar la vista: " + fxml, e);
        }
    }

    private static <T> T mostrarEnMarco(String fxml, String titulo, String seccion) {
        try {
            // La primera vez después del login se carga el marco y se agranda la ventana.
            if (marco == null) {
                FXMLLoader loaderMarco = new FXMLLoader(Navigator.class.getResource("main-layout.fxml"));
                Parent raiz = loaderMarco.load();
                stage.getScene().setRoot(raiz);
                marco = loaderMarco.getController();
                if (stage.getWidth() < 1150) {
                    stage.setWidth(1150);
                    stage.setHeight(720);
                    stage.centerOnScreen();
                }
            }

            FXMLLoader loader = new FXMLLoader(Navigator.class.getResource(fxml));
            Parent vista = loader.load();
            marco.setContenido(vista);
            marco.marcarSeccion(seccion);
            stage.setTitle("LIMS - " + titulo);
            return loader.getController();
        } catch (IOException e) {
            throw new RuntimeException("No se pudo cargar la vista: " + fxml, e);
        }
    }
}
