package com.duoc.lims.limsclient.controller;

import com.duoc.lims.limsclient.Navigator;
import com.duoc.lims.limsclient.model.UsuarioRequest;
import com.duoc.lims.limsclient.service.ApiClient;

import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class CrearUsuarioController {

    @FXML private TextField nombreField;
    @FXML private TextField apellidoField;
    @FXML private TextField emailField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmarPasswordField;
    @FXML private ComboBox<String> rolComboBox;
    @FXML private Label errorLabel;
    @FXML private Label successLabel;

    private static final int ID_CENTRO_UNICO = 1; // Laboratorio Central (único centro del MVP)

    private final ApiClient apiClient = new ApiClient();

    @FXML
    private void initialize() {
        rolComboBox.getItems().addAll("Administrador", "Supervisor", "Analista");
        rolComboBox.getSelectionModel().select("Analista");
    }

    @FXML
    private void onCrearCuenta() {
        ocultarMensajes();

        String nombre = textoDe(nombreField);
        String apellido = textoDe(apellidoField);
        String email = textoDe(emailField);
        String username = textoDe(usernameField);
        String password = passwordField.getText() == null ? "" : passwordField.getText();
        String confirmar = confirmarPasswordField.getText() == null ? "" : confirmarPasswordField.getText();
        String rolSeleccionado = rolComboBox.getValue();

        if (nombre.isEmpty() || apellido.isEmpty() || email.isEmpty() || username.isEmpty() || password.isEmpty()) {
            mostrarError("Completa todos los campos.");
            return;
        }
        if (!password.equals(confirmar)) {
            mostrarError("Las contraseñas no coinciden.");
            return;
        }
        if (rolSeleccionado == null) {
            mostrarError("Selecciona un rol.");
            return;
        }

        Integer idRol = idRolDe(rolSeleccionado);

        try {
            UsuarioRequest request = new UsuarioRequest(ID_CENTRO_UNICO, nombre, apellido, email, username, password, idRol);
            apiClient.crearUsuario(request);
            successLabel.setText("Cuenta creada correctamente. Ya puedes iniciar sesión.");
            successLabel.setVisible(true);
            successLabel.setManaged(true);
        } catch (Exception e) {
            mostrarError("No se pudo crear la cuenta: " + (e.getMessage() != null ? e.getMessage() : "no se pudo conectar con el servidor."));
        }
    }

    @FXML
    private void onVolver() {
        Navigator.irALogin();
    }

    private Integer idRolDe(String nombreRol) {
        return switch (nombreRol) {
            case "Administrador" -> 1;
            case "Supervisor" -> 2;
            default -> 3; // Analista
        };
    }

    private String textoDe(TextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    private void mostrarError(String mensaje) {
        errorLabel.setText(mensaje);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void ocultarMensajes() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        successLabel.setVisible(false);
        successLabel.setManaged(false);
    }
}