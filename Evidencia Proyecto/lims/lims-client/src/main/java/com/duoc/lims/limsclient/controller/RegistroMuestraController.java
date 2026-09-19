package com.duoc.lims.limsclient.controller;

import com.duoc.lims.limsclient.Navigator;
import com.duoc.lims.limsclient.model.MuestraRequest;
import com.duoc.lims.limsclient.service.ApiClient;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public class RegistroMuestraController {

    @FXML private TextField txtCodigo;
    @FXML private ComboBox<String> cbTipoMuestra;
    @FXML private TextField txtCliente;
    @FXML private ComboBox<String> cbPrioridad;
    @FXML private TextArea txtObservaciones;
    @FXML private Label lblEstado;
    @FXML private Button btnGuardar;
    @FXML private Button btnCancelar;

    // Temporal: hasta que exista login, se registra siempre con este usuario de prueba.
    private static final int ID_USUARIO_REGISTRO_TEMPORAL = 1;

    private final ApiClient apiClient = new ApiClient();

    @FXML
    public void initialize() {
        cbTipoMuestra.setItems(FXCollections.observableArrayList(
                "Agua potable", "Agua residual", "Sangre", "Suelo", "Alimento", "Otro"));
        cbPrioridad.setItems(FXCollections.observableArrayList("Baja", "Media", "Alta"));
        cbPrioridad.setValue("Media");
    }

    @FXML
    private void onCancelar() {
        Navigator.irAListado();
    }

    @FXML
    private void onGuardar() {
        String tipoMuestra = cbTipoMuestra.getValue();
        String cliente = txtCliente.getText();
        String prioridad = cbPrioridad.getValue();

        if (tipoMuestra == null || cliente == null || cliente.isBlank() || prioridad == null) {
            lblEstado.setText("Completa tipo de muestra, cliente y prioridad antes de guardar.");
            return;
        }

        MuestraRequest datos = new MuestraRequest();
        datos.setTipoMuestra(tipoMuestra);
        datos.setProcedencia(cliente);
        datos.setPrioridad(prioridad);
        datos.setObservaciones(txtObservaciones.getText());
        datos.setIdUsuarioRegistro(ID_USUARIO_REGISTRO_TEMPORAL);

        btnGuardar.setDisable(true);
        lblEstado.setText("Guardando...");

        Thread hilo = new Thread(() -> {
            try {
                apiClient.crearMuestra(datos);
                Platform.runLater(Navigator::irAListado);
            } catch (Exception e) {
                Platform.runLater(() -> {
                    lblEstado.setText("Error al guardar: " + e.getMessage());
                    btnGuardar.setDisable(false);
                });
            }
        });
        hilo.setDaemon(true);
        hilo.start();
    }
}