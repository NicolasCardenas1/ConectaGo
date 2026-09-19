package com.duoc.lims.limsclient.controller;

import com.duoc.lims.limsclient.model.Muestra;
import com.duoc.lims.limsclient.service.ApiClient;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.format.DateTimeFormatter;

public class ListadoMuestrasController {

    @FXML private TableView<Muestra> tablaMuestras;
    @FXML private TableColumn<Muestra, String> colCodigo;
    @FXML private TableColumn<Muestra, String> colTipo;
    @FXML private TableColumn<Muestra, String> colCliente;
    @FXML private TableColumn<Muestra, String> colPrioridad;
    @FXML private TableColumn<Muestra, String> colEstado;
    @FXML private TableColumn<Muestra, String> colFecha;
    @FXML private Label lblEstado;
    @FXML private Button btnNuevaMuestra;
    @FXML private Button btnActualizar;

    private final ApiClient apiClient = new ApiClient();
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    @FXML
    public void initialize() {
        colCodigo.setCellValueFactory(new PropertyValueFactory<>("codigoUnico"));
        colTipo.setCellValueFactory(new PropertyValueFactory<>("tipoMuestra"));
        colCliente.setCellValueFactory(new PropertyValueFactory<>("procedencia"));
        colPrioridad.setCellValueFactory(new PropertyValueFactory<>("prioridad"));
        colEstado.setCellValueFactory(new PropertyValueFactory<>("estado"));
        colFecha.setCellValueFactory(data -> {
            var fecha = data.getValue().getFechaRecepcion();
            return new SimpleStringProperty(fecha != null ? fecha.format(FORMATO_FECHA) : "");
        });

        cargarMuestras();
    }

    @FXML
    private void onActualizar() {
        cargarMuestras();
    }

    @FXML
    private void onNuevaMuestra() {
        // Se conecta en el próximo paso: navegación a la pantalla de registro.
    }

    private void cargarMuestras() {
        lblEstado.setText("Cargando...");
        Thread hilo = new Thread(() -> {
            try {
                var muestras = apiClient.listarMuestras();
                ObservableList<Muestra> datos = FXCollections.observableArrayList(muestras);
                Platform.runLater(() -> {
                    tablaMuestras.setItems(datos);
                    lblEstado.setText("");
                });
            } catch (Exception e) {
                Platform.runLater(() -> lblEstado.setText("No se pudo conectar al servidor: " + e.getMessage()));
            }
        });
        hilo.setDaemon(true);
        hilo.start();
    }
}