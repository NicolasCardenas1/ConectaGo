package com.duoc.lims.limsclient.controller;

import com.duoc.lims.limsclient.Navigator;
import com.duoc.lims.limsclient.model.AprobacionRequest;
import com.duoc.lims.limsclient.model.ResultadoPendiente;
import com.duoc.lims.limsclient.service.ApiClient;
import com.duoc.lims.limsclient.ui.Badges;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Pantalla 11.5 — Aprobación de resultados por un supervisor (RF04). */
public class AprobacionesController {

    @FXML private TableView<ResultadoPendiente> tablaPendientes;
    @FXML private TableColumn<ResultadoPendiente, String> colMuestra;
    @FXML private TableColumn<ResultadoPendiente, String> colAnalisis;
    @FXML private TableColumn<ResultadoPendiente, String> colValor;
    @FXML private TableColumn<ResultadoPendiente, String> colControl;
    @FXML private TableColumn<ResultadoPendiente, String> colAnalista;
    @FXML private TableColumn<ResultadoPendiente, String> colFecha;

    @FXML private Label lblContador;
    @FXML private Label lblMensaje;

    @FXML private VBox panelRevision;
    @FXML private Label lblDetTitulo;
    @FXML private Label lblDetValor;
    @FXML private Label lblDetRango;
    @FXML private Label lblDetControl;
    @FXML private Label lblDetInstrumento;
    @FXML private Label lblDetAnalista;
    @FXML private Label lblDetFecha;
    @FXML private Label lblDetObservaciones;
    @FXML private TextArea txtComentario;
    @FXML private Button btnAprobar;
    @FXML private Button btnRechazar;

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private final ApiClient apiClient = new ApiClient();

    @FXML
    public void initialize() {
        colMuestra.setCellValueFactory(d -> texto(d.getValue().codigoMuestra()));
        colAnalisis.setCellValueFactory(d -> texto(d.getValue().nombreAnalisis()));
        colValor.setCellValueFactory(d -> texto(numero(d.getValue().valorResultado()) + " " + d.getValue().unidadMedida()));
        colControl.setCellValueFactory(d -> texto(textoControl(d.getValue())));
        colControl.setCellFactory(c -> Badges.celda());
        colAnalista.setCellValueFactory(d -> texto(d.getValue().usuarioIngreso()));
        colFecha.setCellValueFactory(d -> texto(fecha(d.getValue())));

        // Al seleccionar una fila se llena el panel de revisión.
        tablaPendientes.getSelectionModel().selectedItemProperty()
                .addListener((obs, anterior, seleccionado) -> mostrarRevision(seleccionado));

        limpiarRevision();
        cargarPendientes();
    }

    @FXML
    private void onActualizar() {
        lblMensaje.setText("");
        cargarPendientes();
    }

    @FXML
    private void onAprobar() {
        evaluar("Aprobado");
    }

    @FXML
    private void onRechazar() {
        if (comentario() == null) {
            mostrarError("Para rechazar debes escribir un comentario con el motivo.");
            txtComentario.requestFocus();
            return;
        }
        evaluar("Rechazado");
    }

    // ---------- lógica ----------

    private void evaluar(String estado) {
        ResultadoPendiente seleccionado = tablaPendientes.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            mostrarError("Selecciona un resultado de la tabla.");
            return;
        }

        AprobacionRequest datos = new AprobacionRequest(
                seleccionado.id(),
                Navigator.getUsuarioActual().getId(),   // el supervisor que inició sesión
                estado,
                comentario());

        btnAprobar.setDisable(true);
        btnRechazar.setDisable(true);
        Thread hilo = new Thread(() -> {
            try {
                apiClient.evaluarResultado(datos);
                Platform.runLater(() -> {
                    String accion = "Aprobado".equals(estado) ? "aprobado" : "rechazado";
                    mostrarOk(seleccionado.nombreAnalisis() + " de " + seleccionado.codigoMuestra() + " " + accion + ".");
                    cargarPendientes();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    mostrarError(e.getMessage());
                    btnAprobar.setDisable(false);
                    btnRechazar.setDisable(false);
                });
            }
        });
        hilo.setDaemon(true);
        hilo.start();
    }

    private void cargarPendientes() {
        Thread hilo = new Thread(() -> {
            try {
                List<ResultadoPendiente> pendientes = apiClient.listarResultadosPendientes();
                Platform.runLater(() -> {
                    tablaPendientes.setItems(FXCollections.observableArrayList(pendientes));
                    lblContador.setText(pendientes.size() == 1
                            ? "1 resultado pendiente"
                            : pendientes.size() + " resultados pendientes");
                    limpiarRevision();
                });
            } catch (Exception e) {
                Platform.runLater(() -> mostrarError("No se pudo cargar la lista: " + e.getMessage()));
            }
        });
        hilo.setDaemon(true);
        hilo.start();
    }

    private void mostrarRevision(ResultadoPendiente r) {
        if (r == null) {
            limpiarRevision();
            return;
        }
        lblDetTitulo.setText(r.nombreAnalisis() + " · " + r.codigoMuestra());
        lblDetValor.setText(numero(r.valorResultado()) + " " + r.unidadMedida());
        lblDetRango.setText(numero(r.valorMinNormal()) + " – " + numero(r.valorMaxNormal()) + " " + r.unidadMedida());
        lblDetControl.setText(textoControl(r));
        lblDetControl.getStyleClass().setAll("label", "badge", Badges.claseColor(textoControl(r)));
        lblDetInstrumento.setText(vacio(r.instrumentoUtilizado()));
        lblDetAnalista.setText(vacio(r.usuarioIngreso()));
        lblDetFecha.setText(fecha(r));
        lblDetObservaciones.setText(vacio(r.observaciones()));
        txtComentario.clear();
        panelRevision.setDisable(false);
        btnAprobar.setDisable(false);
        btnRechazar.setDisable(false);
    }

    private void limpiarRevision() {
        lblDetTitulo.setText("Selecciona un resultado de la tabla");
        for (Label l : List.of(lblDetValor, lblDetRango, lblDetInstrumento, lblDetAnalista, lblDetFecha, lblDetObservaciones)) {
            l.setText("—");
        }
        lblDetControl.setText("");
        lblDetControl.getStyleClass().setAll("label");
        txtComentario.clear();
        panelRevision.setDisable(true);
    }

    // ---------- helpers ----------

    private String textoControl(ResultadoPendiente r) {
        return Boolean.TRUE.equals(r.dentroRango()) ? "En rango" : "Fuera de rango";
    }

    private String comentario() {
        String t = txtComentario.getText();
        return t == null || t.isBlank() ? null : t.trim();
    }

    private String fecha(ResultadoPendiente r) {
        return r.fechaIngreso() != null ? r.fechaIngreso().format(FORMATO_FECHA) : "—";
    }

    private String numero(BigDecimal n) {
        return n == null ? "" : n.stripTrailingZeros().toPlainString();
    }

    private String vacio(String s) {
        return s == null || s.isBlank() ? "—" : s;
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
