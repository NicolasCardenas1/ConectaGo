package com.duoc.lims.limsclient.controller;

import java.io.IOException;

import com.duoc.lims.limsclient.Navigator;
import com.duoc.lims.limsclient.model.LoginRequest;
import com.duoc.lims.limsclient.model.UsuarioResponse;
import com.duoc.lims.limsclient.service.ApiClient;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    private final ApiClient apiClient = new ApiClient();

    @FXML
    private void onIniciarSesion() {
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            mostrarError("Ingresa usuario y contraseña.");
            return;
        }

        try {
            LoginRequest request = new LoginRequest(username, password);
            UsuarioResponse usuario = apiClient.login(request);
            Navigator.setUsuarioActual(usuario);
            Navigator.irAListado();
        } catch (IOException e) {
            mostrarError(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            mostrarError("No se pudo conectar con el servidor.");
        }
    }

    @FXML
    private void onCrearUsuario() {
        Navigator.irACrearUsuario();
    }

    @FXML
    private void onOlvidoPassword() {
        Navigator.irARecuperarPassword();
    }

    private void mostrarError(String mensaje) {
        errorLabel.setText(mensaje);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }
}