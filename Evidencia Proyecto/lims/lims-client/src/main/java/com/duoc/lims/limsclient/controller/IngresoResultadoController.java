package com.duoc.lims.limsclient.controller;

import com.duoc.lims.limsclient.Navigator;
import com.duoc.lims.limsclient.model.IngresoResultadoRequest;
import com.duoc.lims.limsclient.model.MuestraDetalle.AnalisisItem;
import com.duoc.lims.limsclient.service.ApiClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.math.BigDecimal;

public class IngresoResultadoController {

    @FXML private Label lblAnalisis;
    @FXML private Label lblRango;
    @FXML private Label lblAvisoRango;
    @FXML private Label lblObservacionesTitulo;
    @FXML private Label lblError;
    @FXML private TextField txtValor;
    @FXML private TextField txtInstrumento;
    @FXML private TextArea txtObservaciones;
    @FXML private Button btnGuardar;

    private final ApiClient apiClient = new ApiClient();
    private AnalisisItem analisis;
    private boolean guardado = false;

    @FXML
    public void initialize() {
        // Cada vez que cambia el valor, se revisa el rango en vivo (RF03 visible para el usuario).
        txtValor.textProperty().addListener((obs, anterior, nuevo) -> actualizarAvisoRango());
    }

    /** Lo llama DetalleMuestraController antes de mostrar el diálogo. */
    public void setAnalisis(AnalisisItem analisis) {
        this.analisis = analisis;
        lblAnalisis.setText(analisis.nombreAnalisis());
        lblRango.setText("Rango normal: " + numero(analisis.valorMinNormal()) + " – "
                + numero(analisis.valorMaxNormal()) + " " + analisis.unidadMedida());
    }

    /** DetalleMuestraController lo consulta al cerrarse el diálogo para saber si debe recargar. */
    public boolean isGuardado() {
        return guardado;
    }

    @FXML
    private void onGuardar() {
        lblError.setText("");

        BigDecimal valor = leerValor();
        if (valor == null) {
            lblError.setText("Ingresa un valor numérico válido (ej: 7.2).");
            return;
        }

        String observaciones = textoDe(txtObservaciones.getText());
        if (fueraDeRango(valor) && observaciones == null) {
            lblError.setText("El valor está fuera de rango: debes justificarlo en observaciones.");
            return;
        }

        IngresoResultadoRequest datos = new IngresoResultadoRequest(
                analisis.idMuestraAnalisis(),
                valor,
                textoDe(txtInstrumento.getText()),
                Navigator.getUsuarioActual().getId(),   // el analista que inició sesión
                observaciones);

        btnGuardar.setDisable(true);
        Thread hilo = new Thread(() -> {
            try {
                apiClient.ingresarResultado(datos);
                Platform.runLater(() -> {
                    guardado = true;
                    cerrar();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    lblError.setText(e.getMessage());
                    btnGuardar.setDisable(false);
                });
            }
        });
        hilo.setDaemon(true);
        hilo.start();
    }

    @FXML
    private void onCancelar() {
        cerrar();
    }

    // ---------- helpers ----------

    private void actualizarAvisoRango() {
        BigDecimal valor = leerValor();
        if (valor == null) {
            lblAvisoRango.setText("");
            lblObservacionesTitulo.setText("Observaciones");
            return;
        }
        if (fueraDeRango(valor)) {
            lblAvisoRango.setStyle("-fx-text-fill: #b45309;");
            lblAvisoRango.setText("⚠ Valor fuera del rango normal. Debes justificarlo en observaciones.");
            lblObservacionesTitulo.setText("Observaciones (obligatorio) *");
        } else {
            lblAvisoRango.setStyle("-fx-text-fill: #16a34a;");
            lblAvisoRango.setText("✓ Dentro del rango normal");
            lblObservacionesTitulo.setText("Observaciones");
        }
    }

    // Acepta "7.2" y también "7,2" (coma decimal chilena).
    private BigDecimal leerValor() {
        String texto = txtValor.getText() == null ? "" : txtValor.getText().trim().replace(',', '.');
        if (texto.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(texto);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean fueraDeRango(BigDecimal valor) {
        return valor.compareTo(analisis.valorMinNormal()) < 0
                || valor.compareTo(analisis.valorMaxNormal()) > 0;
    }

    // Devuelve null si el campo está vacío (así el backend lo guarda como NULL).
    private String textoDe(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private String numero(BigDecimal n) {
        return n == null ? "" : n.stripTrailingZeros().toPlainString();
    }

    private void cerrar() {
        ((Stage) btnGuardar.getScene().getWindow()).close();
    }
}