package com.duoc.lims.limsclient.controller;

import com.duoc.lims.limsclient.Navigator;
import com.duoc.lims.limsclient.model.Muestra;
import com.duoc.lims.limsclient.service.ApiClient;
import com.duoc.lims.limsclient.ui.Badges;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.Duration;

import java.time.format.DateTimeFormatter;

public class ListadoMuestrasController {

    @FXML private TableView<Muestra> tablaMuestras;
    @FXML private TableColumn<Muestra, String> colCodigo;
    @FXML private TableColumn<Muestra, String> colTipo;
    @FXML private TableColumn<Muestra, String> colCliente;
    @FXML private TableColumn<Muestra, String> colPrioridad;
    @FXML private TableColumn<Muestra, String> colEstado;
    @FXML private TableColumn<Muestra, String> colFecha;
    @FXML private TextField txtBuscar;
    @FXML private Label lblEstado;
    @FXML private Button btnNuevaMuestra;
    @FXML private Button btnActualizar;

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private final ApiClient apiClient = new ApiClient();

    // Todas las muestras que llegan del backend; la tabla muestra solo las que pasan el filtro.
    private final ObservableList<Muestra> todas = FXCollections.observableArrayList();
    private final FilteredList<Muestra> filtradas = new FilteredList<>(todas, m -> true);

    @FXML
    public void initialize() {
        colCodigo.setCellValueFactory(new PropertyValueFactory<>("codigoUnico"));
        colTipo.setCellValueFactory(new PropertyValueFactory<>("tipoMuestra"));
        colCliente.setCellValueFactory(new PropertyValueFactory<>("procedencia"));
        colPrioridad.setCellValueFactory(new PropertyValueFactory<>("prioridad"));
        colEstado.setCellValueFactory(new PropertyValueFactory<>("estado"));
        colEstado.setCellFactory(c -> Badges.celda());   // estado como etiqueta de color
        colFecha.setCellValueFactory(data -> {
            var fecha = data.getValue().getFechaRecepcion();
            return new SimpleStringProperty(fecha != null ? fecha.format(FORMATO_FECHA) : "");
        });

        // Filtrado en vivo + se mantiene el ordenamiento al hacer clic en las cabeceras.
        SortedList<Muestra> ordenadas = new SortedList<>(filtradas);
        ordenadas.comparatorProperty().bind(tablaMuestras.comparatorProperty());
        tablaMuestras.setItems(ordenadas);
        txtBuscar.textProperty().addListener((obs, anterior, texto) -> aplicarFiltro(texto));

        tablaMuestras.setRowFactory(tv -> {
            TableRow<Muestra> fila = new TableRow<>() {
                @Override
                protected void updateItem(Muestra muestra, boolean empty) {
                    super.updateItem(muestra, empty);
                    if (empty || muestra == null
                            || muestra.getObservaciones() == null
                            || muestra.getObservaciones().isBlank()) {
                        setTooltip(null);
                    } else {
                        Tooltip tooltip = new Tooltip(muestra.getObservaciones());
                        tooltip.setShowDelay(Duration.millis(100));
                        tooltip.setHideDelay(Duration.ZERO);
                        setTooltip(tooltip);
                    }
                }
            };
            // Doble clic en una fila -> abre el detalle de esa muestra
            fila.setOnMouseClicked(evento -> {
                if (evento.getClickCount() == 2 && !fila.isEmpty()) {
                    Navigator.irADetalleMuestra(fila.getItem().getId());
                }
            });
            return fila;
        });

        cargarMuestras();
    }

    @FXML
    private void onActualizar() {
        cargarMuestras();
    }

    @FXML
    private void onNuevaMuestra() {
        Navigator.irARegistroMuestra();
    }

    @FXML
    private void onVerDetalle() {
        Muestra seleccionada = tablaMuestras.getSelectionModel().getSelectedItem();
        if (seleccionada == null) {
            lblEstado.setText("Selecciona una muestra de la tabla.");
            return;
        }
        Navigator.irADetalleMuestra(seleccionada.getId());
    }

    private void aplicarFiltro(String texto) {
        String buscado = texto == null ? "" : texto.trim().toLowerCase();
        filtradas.setPredicate(m -> buscado.isEmpty()
                || contiene(m.getCodigoUnico(), buscado)
                || contiene(m.getProcedencia(), buscado)
                || contiene(m.getTipoMuestra(), buscado));
    }

    private boolean contiene(String campo, String buscado) {
        return campo != null && campo.toLowerCase().contains(buscado);
    }

    private void cargarMuestras() {
        lblEstado.setText("Cargando...");
        Thread hilo = new Thread(() -> {
            try {
                var muestras = apiClient.listarMuestras();
                Platform.runLater(() -> {
                    todas.setAll(muestras);
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
