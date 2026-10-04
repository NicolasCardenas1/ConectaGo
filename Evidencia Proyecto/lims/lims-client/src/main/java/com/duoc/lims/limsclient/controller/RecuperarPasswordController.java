package com.duoc.lims.limsclient.controller;

import java.io.IOException;

import com.duoc.lims.limsclient.Navigator;
import com.duoc.lims.limsclient.service.ApiClient;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class RecuperarPasswordController {

    @FXML private TextField usernameField;
    @FXML private Label errorLabel;
    @FXML private Label successLabel;

    private final ApiClient apiClient = new ApiClient();

    @FXML
    private void onEnviarSolicitud() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        successLabel.setVisible(false);
        successLabel.setManaged(false);

        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        if (username.isEmpty()) {
            mostrarError("Ingresa tu usuario.");
            return;
        }

        try {
            apiClient.solicitarResetPassword(username);
            successLabel.setText("Tu solicitud fue registrada. Un administrador se pondrá en contacto contigo para restablecer tu contraseña.");
            successLabel.setVisible(true);
            successLabel.setManaged(true);
        } catch (IOException e) {
            mostrarError(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            mostrarError("No se pudo conectar con el servidor.");
        }
    }

    @FXML
    private void onVolver() {
        Navigator.irALogin();
    }

    private void mostrarError(String mensaje) {
        errorLabel.setText(mensaje);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }
}