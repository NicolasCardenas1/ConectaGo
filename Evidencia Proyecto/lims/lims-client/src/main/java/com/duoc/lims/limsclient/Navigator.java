package com.duoc.lims.limsclient;

import com.duoc.lims.limsclient.controller.DetalleMuestraController;
import com.duoc.lims.limsclient.controller.MainLayoutController;
import com.duoc.lims.limsclient.model.UsuarioResponse;
import com.duoc.lims.limsclient.service.ApiClient;
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

    /** Mensaje que el login muestra al abrirse (ej. "tu sesión expiró"). Se consume una sola vez. */
    private static String mensajeParaLogin;

    public static void irALogin() {
        usuarioActual = null;
        marco = null;
        ApiClient.olvidarSesion();
        cambiarVentanaCompleta("login-view.fxml", "LIMS - Inicio de Sesión");
    }

    /** Lo llama ApiClient cuando el backend responde 401 (token vencido o inválido). */
    public static void irALoginPorSesionExpirada() {
        if (usuarioActual == null) {
            return;   // ya estamos en el login
        }
        mensajeParaLogin = "Tu sesión expiró. Inicia sesión nuevamente.";
        irALogin();
    }

    public static String tomarMensajeParaLogin() {
        String m = mensajeParaLogin;
        mensajeParaLogin = null;
        return m;
    }

    // ---------- Permisos por rol (RF06) ----------

    public static boolean esRol(String... roles) {
        if (usuarioActual == null || usuarioActual.getNombreRol() == null) {
            return false;
        }
        for (String r : roles) {
            if (r.equalsIgnoreCase(usuarioActual.getNombreRol())) {
                return true;
            }
        }
        return false;
    }

    /** Supervisor o Administrador: pueden aprobar resultados y emitir informes. */
    public static boolean puedeAprobar() {
        return esRol("Supervisor", "Administrador");
    }

    public static boolean esAdministrador() {
        return esRol("Administrador");
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

    public static void irAInvestigadores() {
        mostrarEnMarco("investigadores.fxml", "Investigadores", "investigadores");
    }

    public static void irACatalogo() {
        mostrarEnMarco("catalogo.fxml", "Parámetros y rangos", "catalogo");
    }

    public static void irAUsuarios() {
        mostrarEnMarco("usuarios.fxml", "Administración de Usuarios", "usuarios");
    }

    public static void irAAprobaciones() {
        mostrarEnMarco("aprobaciones.fxml", "Aprobación de Resultados", "aprobaciones");
    }

    public static void irAReportes() {
        mostrarEnMarco("reportes.fxml", "Informes emitidos", "reportes");
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
