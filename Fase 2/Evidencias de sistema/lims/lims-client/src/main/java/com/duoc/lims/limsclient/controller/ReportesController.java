package com.duoc.lims.limsclient.controller;

import com.duoc.lims.limsclient.Navigator;
import com.duoc.lims.limsclient.model.ReporteResumen;
import com.duoc.lims.limsclient.service.ApiClient;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Historial de informes emitidos (RF05). Disponible para Supervisor y Administrador.
 * Lista los informes ya generados y permite volver a abrir/guardar el PDF guardado.
 */
public class ReportesController {

    @FXML private TableView<ReporteResumen> tabla;
    @FXML private TableColumn<ReporteResumen, String> colNumero;
    @FXML private TableColumn<ReporteResumen, String> colMuestra;
    @FXML private TableColumn<ReporteResumen, String> colTipo;
    @FXML private TableColumn<ReporteResumen, String> colInvestigador;
    @FXML private TableColumn<ReporteResumen, String> colVersion;
    @FXML private TableColumn<ReporteResumen, String> colEmitidoPor;
    @FXML private TableColumn<ReporteResumen, String> colFecha;
    @FXML private TextField txtBuscar;
    @FXML private Button btnDescargar;
    @FXML private Label lblMensaje;

    private final ApiClient apiClient = new ApiClient();
    private final ObservableList<ReporteResumen> todos = FXCollections.observableArrayList();
    private final FilteredList<ReporteResumen> filtrados = new FilteredList<>(todos, r -> true);

    @FXML
    public void initialize() {
        colNumero.setCellValueFactory(d -> texto(d.getValue().numeroInforme()));
        colMuestra.setCellValueFactory(d -> texto(d.getValue().codigoMuestra()));
        colTipo.setCellValueFactory(d -> texto(d.getValue().tipoMuestra()));
        colInvestigador.setCellValueFactory(d -> texto(d.getValue().investigador()));
        colVersion.setCellValueFactory(d -> texto(d.getValue().version() == null ? null : "v" + d.getValue().version()));
        colEmitidoPor.setCellValueFactory(d -> texto(d.getValue().emitidoPor()));
        colFecha.setCellValueFactory(d -> texto(fecha(d.getValue().fechaGeneracion())));

        tabla.setItems(filtrados);
        txtBuscar.textProperty().addListener((o, a, t) -> {
            String b = t == null ? "" : t.trim().toLowerCase();
            filtrados.setPredicate(r -> b.isEmpty()
                    || contiene(r.numeroInforme(), b) || contiene(r.codigoMuestra(), b) || contiene(r.investigador(), b));
        });
        tabla.getSelectionModel().selectedItemProperty().addListener((o, a, r) -> btnDescargar.setDisable(r == null));
        tabla.setRowFactory(tv -> {
            TableRow<ReporteResumen> fila = new TableRow<>();
            fila.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !fila.isEmpty()) {
                    onDescargar();
                }
            });
            return fila;
        });
        btnDescargar.setDisable(true);
        cargar();
    }

    @FXML
    private void onActualizar() {
        cargar();
    }

    @FXML
    private void onDescargar() {
        ReporteResumen sel = tabla.getSelectionModel().getSelectedItem();
        if (sel == null) {
            mostrarError("Selecciona un informe de la tabla.");
            return;
        }
        mostrarOk("Abriendo informe " + sel.numeroInforme() + "...");
        Thread hilo = new Thread(() -> {
            try {
                byte[] pdf = apiClient.descargarReportePdf(sel.id());
                Path temporal = Files.createTempDirectory("lims-informe")
                        .resolve("Informe_" + sel.numeroInforme() + ".pdf");
                Files.write(temporal, pdf);
                Platform.runLater(() -> {
                    Navigator.abrirDocumento(temporal);
                    mostrarOk("Informe " + sel.numeroInforme() + " abierto.");
                });
            } catch (Exception e) {
                Platform.runLater(() -> mostrarError("No se pudo abrir el informe: " + e.getMessage()));
            }
        });
        hilo.setDaemon(true);
        hilo.start();
    }

    private void cargar() {
        Thread hilo = new Thread(() -> {
            try {
                List<ReporteResumen> lista = apiClient.listarReportes();
                Platform.runLater(() -> todos.setAll(lista));
            } catch (Exception e) {
                Platform.runLater(() -> mostrarError("No se pudo cargar el historial: " + e.getMessage()));
            }
        });
        hilo.setDaemon(true);
        hilo.start();
    }

    private boolean contiene(String campo, String buscado) {
        return campo != null && campo.toLowerCase().contains(buscado);
    }

    private SimpleStringProperty texto(String s) {
        return new SimpleStringProperty(s == null || s.isBlank() ? "—" : s);
    }

    /** Pasa "2026-10-18T18:08:29" a "2026-10-18 18:08". */
    private String fecha(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        String s = iso.replace('T', ' ');
        return s.length() >= 16 ? s.substring(0, 16) : s;
    }

    private void mostrarError(String m) {
        lblMensaje.setStyle("-fx-text-fill: #dc2626;");
        lblMensaje.setText(m);
    }

    private void mostrarOk(String m) {
        lblMensaje.setStyle("-fx-text-fill: #16a34a;");
        lblMensaje.setText(m);
    }
}
