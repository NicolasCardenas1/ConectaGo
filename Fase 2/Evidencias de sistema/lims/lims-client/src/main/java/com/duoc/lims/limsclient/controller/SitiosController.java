package com.duoc.lims.limsclient.controller;

import com.duoc.lims.limsclient.Navigator;
import com.duoc.lims.limsclient.model.Sitio;
import com.duoc.lims.limsclient.model.SitioRequest;
import com.duoc.lims.limsclient.service.ApiClient;
import com.duoc.lims.limsclient.ui.Badges;
import com.duoc.lims.limsclient.ui.FormularioSitio;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v5 — Sitios de muestreo en un mapa (Leaflet/OpenStreetMap en un WebView) y en una tabla.
 * Clic en un sitio del mapa = lo selecciona en la tabla; clic en el mapa = registrar un sitio en ese punto.
 */
public class SitiosController {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    @FXML private WebView mapa;
    @FXML private TableView<Sitio> tabla;
    @FXML private TableColumn<Sitio, String> colCodigo;
    @FXML private TableColumn<Sitio, String> colNombre;
    @FXML private TableColumn<Sitio, String> colTipo;
    @FXML private TableColumn<Sitio, String> colComuna;
    @FXML private TableColumn<Sitio, String> colMuestras;
    @FXML private TableColumn<Sitio, String> colEstado;
    @FXML private TextField txtBuscar;
    @FXML private Button btnEditar;
    @FXML private Button btnEstado;
    @FXML private Label lblMensaje;

    private final ApiClient apiClient = new ApiClient();
    private final ObjectMapper json = new ObjectMapper();
    private final ObservableList<Sitio> todos = FXCollections.observableArrayList();
    private final FilteredList<Sitio> filtrados = new FilteredList<>(todos, s -> true);
    // Referencia fuerte: si el puente se recolecta, el JavaScript deja de poder llamar a Java.
    private final PuenteMapa puente = new PuenteMapa();
    private boolean mapaListo = false;

    @FXML
    public void initialize() {
        colCodigo.setCellValueFactory(d -> texto(d.getValue().codigo()));
        colNombre.setCellValueFactory(d -> texto(d.getValue().nombre()));
        colTipo.setCellValueFactory(d -> texto(d.getValue().tipoAmbiente()));
        colComuna.setCellValueFactory(d -> texto(d.getValue().comuna()));
        colMuestras.setCellValueFactory(d -> texto(String.valueOf(d.getValue().totalMuestras())));
        colEstado.setCellValueFactory(d -> texto(d.getValue().activo() ? "Activo" : "Inactivo"));
        colEstado.setCellFactory(c -> Badges.celda());

        tabla.setItems(filtrados);
        txtBuscar.textProperty().addListener((o, a, t) -> {
            String b = t == null ? "" : t.trim().toLowerCase();
            filtrados.setPredicate(s -> b.isEmpty() || contiene(s.codigo(), b) || contiene(s.nombre(), b)
                    || contiene(s.comuna(), b));
        });
        tabla.getSelectionModel().selectedItemProperty().addListener((o, a, s) -> {
            actualizarBotones(s);
            if (s != null && mapaListo) {
                mapa.getEngine().executeScript("centrarEn(" + s.id() + ")");
            }
        });
        tabla.setRowFactory(tv -> {
            TableRow<Sitio> fila = new TableRow<>();
            fila.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !fila.isEmpty()) {
                    onEditar();
                }
            });
            return fila;
        });
        actualizarBotones(null);
        iniciarMapa();
        cargar();
    }

    private void iniciarMapa() {
        WebEngine engine = mapa.getEngine();
        engine.setUserAgent(engine.getUserAgent() + " LIMS-ConectaGo/1.0");   // política de uso de OpenStreetMap
        engine.getLoadWorker().stateProperty().addListener((o, a, estado) -> {
            if (estado == Worker.State.SUCCEEDED) {
                JSObject ventana = (JSObject) engine.executeScript("window");
                ventana.setMember("java", puente);
                mapaListo = true;
                pintarMapa();
            }
        });
        engine.load(Navigator.class.getResource("mapa.html").toExternalForm());
    }

    @FXML
    private void onNuevo() {
        registrar(null, null);
    }

    @FXML
    private void onEditar() {
        Sitio sel = tabla.getSelectionModel().getSelectedItem();
        if (sel == null) {
            mostrarError("Selecciona un sitio de la tabla.");
            return;
        }
        FormularioSitio.abrir(tabla.getScene(), sel, null, null).ifPresent(datos ->
                ejecutar(() -> apiClient.actualizarSitio(sel.id(), datos), "Sitio " + sel.codigo() + " actualizado."));
    }

    @FXML
    private void onCambiarEstado() {
        Sitio sel = tabla.getSelectionModel().getSelectedItem();
        if (sel == null) {
            mostrarError("Selecciona un sitio de la tabla.");
            return;
        }
        boolean activar = !sel.activo();
        ejecutar(() -> apiClient.cambiarEstadoSitio(sel.id(), activar),
                sel.codigo() + (activar ? " activado." : " desactivado (ya no aparecerá al registrar muestras)."));
    }

    private void registrar(BigDecimal lat, BigDecimal lon) {
        FormularioSitio.abrir(tabla.getScene(), null, lat, lon).ifPresent((SitioRequest datos) ->
                ejecutar(() -> apiClient.crearSitio(datos), "Sitio " + datos.nombre() + " registrado."));
    }

    // ---------- carga ----------

    private void cargar() {
        Thread hilo = new Thread(() -> {
            try {
                List<Sitio> lista = apiClient.listarSitios(false);
                Platform.runLater(() -> {
                    todos.setAll(lista);
                    pintarMapa();
                });
            } catch (Exception e) {
                Platform.runLater(() -> mostrarError("No se pudo cargar la lista: " + e.getMessage()));
            }
        });
        hilo.setDaemon(true);
        hilo.start();
    }

    private void pintarMapa() {
        if (!mapaListo) {
            return;
        }
        try {
            List<Map<String, Object>> datos = todos.stream().map(s -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", s.id());
                m.put("codigo", s.codigo());
                m.put("nombre", s.nombre());
                m.put("tipo", s.tipoAmbiente());
                m.put("comuna", s.comuna());
                m.put("lat", s.latitud());
                m.put("lon", s.longitud());
                m.put("activo", s.activo());
                m.put("totalMuestras", s.totalMuestras());
                m.put("resultadosFueraRango", s.resultadosFueraRango());
                m.put("resultadosAnomalos", s.resultadosAnomalos());
                m.put("ultimo", s.ultimoMuestreo() == null ? null : s.ultimoMuestreo().format(FECHA));
                return m;
            }).toList();
            mapa.getEngine().executeScript("cargarSitios(" + json.writeValueAsString(datos) + ")");
        } catch (Exception e) {
            mostrarError("No se pudo dibujar el mapa: " + e.getMessage());
        }
    }

    /** Métodos que el JavaScript del mapa llama como window.java.xxx(...). Deben ser públicos. */
    public class PuenteMapa {
        public void seleccionarSitio(int id) {
            Platform.runLater(() -> todos.stream().filter(s -> s.id() == id).findFirst().ifPresent(s -> {
                txtBuscar.clear();
                tabla.getSelectionModel().select(s);
                tabla.scrollTo(s);
            }));
        }

        public void clicEnMapa(double lat, double lon) {
            Platform.runLater(() -> registrar(BigDecimal.valueOf(lat), BigDecimal.valueOf(lon)));
        }
    }

    // ---------- helpers ----------

    @FunctionalInterface
    private interface Accion {
        void ejecutar() throws Exception;
    }

    private void ejecutar(Accion accion, String mensajeOk) {
        Thread hilo = new Thread(() -> {
            try {
                accion.ejecutar();
                Platform.runLater(() -> {
                    mostrarOk(mensajeOk);
                    cargar();
                });
            } catch (Exception e) {
                Platform.runLater(() -> mostrarError(e.getMessage()));
            }
        });
        hilo.setDaemon(true);
        hilo.start();
    }

    private void actualizarBotones(Sitio s) {
        btnEditar.setDisable(s == null);
        btnEstado.setDisable(s == null);
        btnEstado.setText(s != null && !s.activo() ? "Activar" : "Desactivar");
    }

    private boolean contiene(String campo, String buscado) {
        return campo != null && campo.toLowerCase().contains(buscado);
    }

    private SimpleStringProperty texto(String s) {
        return new SimpleStringProperty(s == null || s.isBlank() ? "—" : s);
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
