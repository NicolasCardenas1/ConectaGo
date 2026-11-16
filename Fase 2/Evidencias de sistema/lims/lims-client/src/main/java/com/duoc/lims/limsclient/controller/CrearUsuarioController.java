package com.duoc.lims.limsclient.controller;

import com.duoc.lims.limsclient.Navigator;
import com.duoc.lims.limsclient.model.UsuarioRequest;
import com.duoc.lims.limsclient.service.ApiClient;

import javafx.fxml.FXML;
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
    @FXML private Label errorLabel;
    @FXML private Label successLabel;

    private static final int ID_CENTRO_UNICO = 1; // Laboratorio Central (único centro del MVP)
    private static final int ID_ROL_ANALISTA = 3;

    private final ApiClient apiClient = new ApiClient();

    @FXML
    private void onCrearCuenta() {
        ocultarMensajes();

        String nombre = textoDe(nombreField);
        String apellido = textoDe(apellidoField);
        String email = textoDe(emailField);
        String username = textoDe(usernameField);
        String password = passwordField.getText() == null ? "" : passwordField.getText();
        String confirmar = confirmarPasswordField.getText() == null ? "" : confirmarPasswordField.getText();

        if (nombre.isEmpty() || apellido.isEmpty() || email.isEmpty() || username.isEmpty() || password.isEmpty()) {
            mostrarError("Completa todos los campos.");
            return;
        }
        if (!password.equals(confirmar)) {
            mostrarError("Las contraseñas no coinciden.");
            return;
        }
        // RF06: el registro público siempre crea Analistas (el backend lo impone de todas formas).
        Integer idRol = ID_ROL_ANALISTA;

        try {
            UsuarioRequest request = new UsuarioRequest(ID_CENTRO_UNICO, nombre, apellido, email, username, password, idRol);
            apiClient.registrarse(request);
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