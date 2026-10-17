package com.duoc.lims.limsclient.controller;

import com.duoc.lims.limsclient.Navigator;
import com.duoc.lims.limsclient.model.UsuarioRequest;
import com.duoc.lims.limsclient.model.UsuarioResponse;
import com.duoc.lims.limsclient.service.ApiClient;
import com.duoc.lims.limsclient.ui.Badges;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.util.List;
import java.util.Optional;

/** RF06 — Administración de usuarios (pantalla solo visible para el rol Administrador). */
public class UsuariosController {

    @FXML private TableView<UsuarioResponse> tablaUsuarios;
    @FXML private TableColumn<UsuarioResponse, String> colNombre;
    @FXML private TableColumn<UsuarioResponse, String> colUsername;
    @FXML private TableColumn<UsuarioResponse, String> colEmail;
    @FXML private TableColumn<UsuarioResponse, String> colRol;
    @FXML private TableColumn<UsuarioResponse, String> colEstado;
    @FXML private TableColumn<UsuarioResponse, String> colReset;
    @FXML private Button btnResetear;
    @FXML private Button btnEstado;
    @FXML private Label lblMensaje;

    private static final int ID_CENTRO_UNICO = 1;
    private static final List<String> ROLES = List.of("Analista", "Supervisor", "Administrador");

    private final ApiClient apiClient = new ApiClient();

    @FXML
    public void initialize() {
        colNombre.setCellValueFactory(d -> texto(d.getValue().getNombre() + " " + d.getValue().getApellido()));
        colUsername.setCellValueFactory(d -> texto(d.getValue().getUsername()));
        colEmail.setCellValueFactory(d -> texto(d.getValue().getEmail()));
        colRol.setCellValueFactory(d -> texto(d.getValue().getNombreRol()));
        colRol.setCellFactory(c -> Badges.celda());
        colEstado.setCellValueFactory(d -> texto(d.getValue().isActivo() ? "Activo" : "Inactivo"));
        colEstado.setCellFactory(c -> Badges.celda());
        colReset.setCellValueFactory(d -> texto(d.getValue().isRequiereResetPassword() ? "Reset solicitado" : "—"));
        colReset.setCellFactory(c -> Badges.celda());

        tablaUsuarios.getSelectionModel().selectedItemProperty()
                .addListener((obs, anterior, u) -> actualizarBotones(u));
        actualizarBotones(null);
        cargarUsuarios();
    }

    @FXML
    private void onActualizar() {
        lblMensaje.setText("");
        cargarUsuarios();
    }

    // ---------- crear usuario con rol ----------

    @FXML
    private void onNuevoUsuario() {
        TextField nombre = new TextField();
        TextField apellido = new TextField();
        TextField email = new TextField();
        TextField username = new TextField();
        PasswordField password = new PasswordField();
        ComboBox<String> rol = new ComboBox<>(FXCollections.observableArrayList(ROLES));
        rol.setValue("Analista");
        rol.setMaxWidth(Double.MAX_VALUE);
        Label error = new Label();
        error.getStyleClass().add("status-text");

        GridPane form = formulario(
                "Nombre", nombre, "Apellido", apellido, "Email", email,
                "Usuario", username, "Contraseña", password, "Rol", rol);
        form.add(error, 0, 6, 2, 1);

        Dialog<ButtonType> dialogo = dialogo("Nuevo usuario", "Crea una cuenta y asígnale su rol", form);
        ButtonType crear = new ButtonType("Crear usuario", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(crear, ButtonType.CANCEL);

        // Validación: si falta algo, el diálogo no se cierra.
        Node botonCrear = dialogo.getDialogPane().lookupButton(crear);
        botonCrear.addEventFilter(ActionEvent.ACTION, ev -> {
            if (vacio(nombre) || vacio(apellido) || vacio(email) || vacio(username) || password.getText().isBlank()) {
                error.setText("Completa todos los campos.");
                ev.consume();
            }
        });

        Optional<ButtonType> r = dialogo.showAndWait();
        if (r.isEmpty() || r.get() != crear) {
            return;
        }

        UsuarioRequest datos = new UsuarioRequest(ID_CENTRO_UNICO, nombre.getText().trim(), apellido.getText().trim(),
                email.getText().trim(), username.getText().trim(), password.getText(), idRol(rol.getValue()));
        ejecutar(() -> apiClient.crearUsuarioConRol(datos),
                "Usuario " + datos.getUsername() + " creado como " + rol.getValue() + ".");
    }

    // ---------- resetear contraseña ----------

    @FXML
    private void onResetearPassword() {
        UsuarioResponse u = tablaUsuarios.getSelectionModel().getSelectedItem();
        if (u == null) {
            mostrarError("Selecciona un usuario de la tabla.");
            return;
        }
        PasswordField nueva = new PasswordField();
        PasswordField confirmar = new PasswordField();
        Label error = new Label();
        error.getStyleClass().add("status-text");
        GridPane form = formulario("Nueva contraseña", nueva, "Confirmar", confirmar);
        form.add(error, 0, 2, 2, 1);

        Dialog<ButtonType> dialogo = dialogo("Resetear contraseña",
                "Nueva contraseña para " + u.getNombre() + " " + u.getApellido() + " (" + u.getUsername() + ")", form);
        ButtonType guardar = new ButtonType("Guardar", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(guardar, ButtonType.CANCEL);
        dialogo.getDialogPane().lookupButton(guardar).addEventFilter(ActionEvent.ACTION, ev -> {
            if (nueva.getText().isBlank()) {
                error.setText("Escribe la nueva contraseña.");
                ev.consume();
            } else if (!nueva.getText().equals(confirmar.getText())) {
                error.setText("Las contraseñas no coinciden.");
                ev.consume();
            }
        });

        Optional<ButtonType> r = dialogo.showAndWait();
        if (r.isPresent() && r.get() == guardar) {
            String pass = nueva.getText();
            ejecutar(() -> apiClient.resetearPassword(u.getId(), pass),
                    "Contraseña de " + u.getUsername() + " actualizada. Sus sesiones abiertas se cerraron.");
        }
    }

    // ---------- activar / desactivar ----------

    @FXML
    private void onCambiarEstado() {
        UsuarioResponse u = tablaUsuarios.getSelectionModel().getSelectedItem();
        if (u == null) {
            mostrarError("Selecciona un usuario de la tabla.");
            return;
        }
        boolean activar = !u.isActivo();
        Alert confirmar = new Alert(Alert.AlertType.CONFIRMATION,
                (activar ? "¿Activar" : "¿Desactivar") + " la cuenta de " + u.getNombre() + " " + u.getApellido() + "?"
                        + (activar ? "" : "\nNo podrá iniciar sesión hasta que se vuelva a activar."),
                ButtonType.OK, ButtonType.CANCEL);
        confirmar.setHeaderText(null);
        confirmar.setTitle("LIMS - Usuarios");
        confirmar.initOwner(tablaUsuarios.getScene().getWindow());
        confirmar.getDialogPane().getStylesheets().addAll(tablaUsuarios.getScene().getStylesheets());

        Optional<ButtonType> r = confirmar.showAndWait();
        if (r.isPresent() && r.get() == ButtonType.OK) {
            ejecutar(() -> apiClient.cambiarEstadoUsuario(u.getId(), activar),
                    "Cuenta de " + u.getUsername() + (activar ? " activada." : " desactivada."));
        }
    }

    // ---------- carga y helpers ----------

    private void cargarUsuarios() {
        Thread hilo = new Thread(() -> {
            try {
                List<UsuarioResponse> usuarios = apiClient.listarUsuarios();
                Platform.runLater(() -> tablaUsuarios.setItems(FXCollections.observableArrayList(usuarios)));
            } catch (Exception e) {
                Platform.runLater(() -> mostrarError("No se pudo cargar la lista: " + e.getMessage()));
            }
        });
        hilo.setDaemon(true);
        hilo.start();
    }

    /** Acción contra el backend que puede fallar. */
    @FunctionalInterface
    private interface Accion {
        void ejecutar() throws Exception;
    }

    /** Ejecuta la acción en segundo plano, muestra el resultado y recarga la tabla. */
    private void ejecutar(Accion accion, String mensajeOk) {
        Thread hilo = new Thread(() -> {
            try {
                accion.ejecutar();
                Platform.runLater(() -> {
                    mostrarOk(mensajeOk);
                    cargarUsuarios();
                });
            } catch (Exception e) {
                Platform.runLater(() -> mostrarError(e.getMessage()));
            }
        });
        hilo.setDaemon(true);
        hilo.start();
    }

    private void actualizarBotones(UsuarioResponse u) {
        btnResetear.setDisable(u == null);
        btnEstado.setDisable(u == null || esYo(u));   // no puedes desactivarte a ti mismo
        btnEstado.setText(u != null && !u.isActivo() ? "Activar" : "Desactivar");
    }

    private boolean esYo(UsuarioResponse u) {
        return Navigator.getUsuarioActual() != null && Navigator.getUsuarioActual().getId().equals(u.getId());
    }

    private GridPane formulario(Object... etiquetasYCampos) {
        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(10);
        g.setStyle("-fx-padding: 6 4 0 4;");
        for (int i = 0; i < etiquetasYCampos.length; i += 2) {
            Label l = new Label((String) etiquetasYCampos[i]);
            l.getStyleClass().add("field-name");
            Node campo = (Node) etiquetasYCampos[i + 1];
            campo.getStyleClass().add("form-field");
            if (campo instanceof TextField tf) {
                tf.setPrefWidth(260);
            }
            g.add(l, 0, i / 2);
            g.add(campo, 1, i / 2);
        }
        return g;
    }

    private Dialog<ButtonType> dialogo(String titulo, String encabezado, Node contenido) {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("LIMS - " + titulo);
        d.setHeaderText(encabezado);
        d.getDialogPane().setContent(contenido);
        d.initOwner(tablaUsuarios.getScene().getWindow());
        d.getDialogPane().getStylesheets().addAll(tablaUsuarios.getScene().getStylesheets());
        return d;
    }

    private int idRol(String nombreRol) {
        return switch (nombreRol) {
            case "Administrador" -> 1;
            case "Supervisor" -> 2;
            default -> 3;   // Analista
        };
    }

    private boolean vacio(TextField campo) {
        return campo.getText() == null || campo.getText().isBlank();
    }

    private SimpleStringProperty texto(String s) {
        return new SimpleStringProperty(s == null ? "—" : s);
    }

    private void mostrarError(String mensaje) {
        lblMensaje.setStyle("-fx-text-fill: #dc2626;");
        lblMensaje.setText(mensaje);
    }

    private void mostrarOk(String mensaje) {
        lblMensaje.setStyle("-fx-text-fill: #16a34a;");
        lblMensaje.setText(mensaje);
    }
}
