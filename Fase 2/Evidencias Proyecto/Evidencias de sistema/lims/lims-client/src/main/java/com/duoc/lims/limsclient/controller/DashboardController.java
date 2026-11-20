package com.duoc.lims.limsclient.controller;

import com.duoc.lims.limsclient.Navigator;
import com.duoc.lims.limsclient.model.AnalisisCatalogo;
import com.duoc.lims.limsclient.model.Dashboard;
import com.duoc.lims.limsclient.model.Dashboard.Alerta;
import com.duoc.lims.limsclient.model.Dashboard.Punto;
import com.duoc.lims.limsclient.model.Dashboard.Tendencia;
import com.duoc.lims.limsclient.model.Sitio;
import com.duoc.lims.limsclient.service.ApiClient;
import com.duoc.lims.limsclient.ui.Badges;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * v5 — Panel de inicio: indicadores del laboratorio, alertas de calidad (equipos, valores atípicos,
 * rechazos, conservación) y la tendencia de un parámetro en un sitio con su rango normal.
 */
public class DashboardController {

    private static final DateTimeFormatter FECHA_CORTA = DateTimeFormatter.ofPattern("dd-MM-yy");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    /** Opción "todos los sitios" del filtro (id null). */
    private static final Sitio TODOS = new Sitio(null, "", "Todos los sitios", null, null, null, null, null, null,
            true, 0, 0, 0, null);

    @FXML private Label lblSaludo;
    @FXML private Label lblMensaje;
    @FXML private Label kpiMuestras;
    @FXML private Label kpiMuestrasSub;
    @FXML private Label kpiPendientes;
    @FXML private Label kpiFueraRango;
    @FXML private Label kpiAtipicos;
    @FXML private Label kpiTiempo;
    @FXML private Label kpiEquipos;
    @FXML private Label kpiEquiposSub;
    @FXML private BarChart<Number, String> graficoEstados;
    @FXML private ListView<Alerta> listaAlertas;
    @FXML private Label lblTendencia;
    @FXML private ComboBox<AnalisisCatalogo> cbParametro;
    @FXML private ComboBox<Sitio> cbSitio;
    @FXML private LineChart<Number, Number> graficoTendencia;
    @FXML private NumberAxis ejeFecha;
    @FXML private NumberAxis ejeValor;
    @FXML private Label lblResumenTendencia;

    private final ApiClient apiClient = new ApiClient();

    @FXML
    public void initialize() {
        if (Navigator.getUsuarioActual() != null) {
            lblSaludo.setText("Hola, " + Navigator.getUsuarioActual().getNombre());
        }
        listaAlertas.setCellFactory(l -> new CeldaAlerta());
        listaAlertas.setOnMouseClicked(ev -> {
            Alerta a = listaAlertas.getSelectionModel().getSelectedItem();
            if (ev.getClickCount() == 2 && a != null && a.idMuestra() != null) {
                Navigator.irADetalleMuestra(a.idMuestra());
            }
        });

        // Eje X: fechas como número de días (permite espaciado real entre muestreos)
        ejeFecha.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number n) {
                return LocalDate.ofEpochDay(n.longValue()).format(FECHA_CORTA);
            }

            @Override
            public Number fromString(String s) {
                return 0;
            }
        });
        cbSitio.setConverter(new StringConverter<>() {
            @Override
            public String toString(Sitio s) {
                return s == null ? "" : s.id() == null ? s.nombre() : s.toString();
            }

            @Override
            public Sitio fromString(String s) {
                return null;
            }
        });
        cbParametro.valueProperty().addListener((o, a, n) -> cargarTendencia());
        cbSitio.valueProperty().addListener((o, a, n) -> cargarTendencia());

        cargarResumen();
        cargarFiltros();
    }

    @FXML
    private void onActualizar() {
        lblMensaje.setText("");
        cargarResumen();
        cargarTendencia();
    }

    @FXML
    private void onRegistrar() {
        Navigator.irARegistroMuestra();
    }

    // ---------- carga ----------

    private void cargarResumen() {
        enSegundoPlano(() -> {
            Dashboard d = apiClient.dashboard();
            Platform.runLater(() -> mostrarResumen(d));
        });
    }

    private void cargarFiltros() {
        enSegundoPlano(() -> {
            List<AnalisisCatalogo> parametros = apiClient.listarAnalisisCatalogo().stream()
                    .filter(a -> Boolean.TRUE.equals(a.activo())).toList();
            List<Sitio> sitios = apiClient.listarSitios(true);
            Platform.runLater(() -> {
                cbSitio.getItems().setAll(TODOS);
                cbSitio.getItems().addAll(sitios);
                cbParametro.setItems(FXCollections.observableArrayList(parametros));
                // Por defecto: el sitio con más muestras y clorofila-a si existe
                cbSitio.setValue(sitios.stream().max((a, b) -> Long.compare(a.totalMuestras(), b.totalMuestras()))
                        .filter(s -> s.totalMuestras() > 0).orElse(TODOS));
                cbParametro.setValue(parametros.stream().filter(p -> p.nombre().startsWith("Clorofila")).findFirst()
                        .orElse(parametros.isEmpty() ? null : parametros.get(0)));
            });
        });
    }

    private void cargarTendencia() {
        AnalisisCatalogo parametro = cbParametro.getValue();
        Sitio sitio = cbSitio.getValue();
        if (parametro == null || sitio == null) {
            return;
        }
        enSegundoPlano(() -> {
            Tendencia t = apiClient.tendencia(parametro.id(), sitio.id());
            Platform.runLater(() -> mostrarTendencia(t, sitio));
        });
    }

    // ---------- pintar ----------

    private void mostrarResumen(Dashboard d) {
        kpiMuestras.setText(String.valueOf(d.totalMuestras()));
        long enProceso = d.muestrasPorEstado().entrySet().stream()
                .filter(e -> !e.getKey().equals("Reportada")).mapToLong(Map.Entry::getValue).sum();
        kpiMuestrasSub.setText(enProceso + " en proceso · " + d.muestrasPorEstado().getOrDefault("Reportada", 0L) + " informadas");
        kpiPendientes.setText(String.valueOf(d.pendientesAprobacion()));
        kpiFueraRango.setText(String.valueOf(d.fueraDeRango30Dias()));
        kpiAtipicos.setText(String.valueOf(d.anomalias30Dias()));
        marcar(kpiAtipicos, d.anomalias30Dias() > 0);
        kpiTiempo.setText(d.horasPromedioHastaAprobacion() == null ? "—"
                : d.horasPromedioHastaAprobacion() >= 48
                ? String.format("%.1f días", d.horasPromedioHastaAprobacion() / 24)
                : String.format("%.1f h", d.horasPromedioHastaAprobacion()));
        kpiEquipos.setText(String.valueOf(d.equiposCalibracionVencida()));
        marcar(kpiEquipos, d.equiposCalibracionVencida() > 0);
        kpiEquiposSub.setText("con calibración vencida · " + d.equiposCalibracionPorVencer() + " por vencer en 30 días");

        // Barras horizontales: las etiquetas de estado se leen sin rotar. Orden del flujo de arriba hacia abajo.
        XYChart.Series<Number, String> serie = new XYChart.Series<>();
        List<Map.Entry<String, Long>> estados = List.copyOf(d.muestrasPorEstado().entrySet());
        for (int i = estados.size() - 1; i >= 0; i--) {
            serie.getData().add(new XYChart.Data<>(estados.get(i).getValue(), Badges.textoVisible(estados.get(i).getKey())));
        }
        graficoEstados.getData().setAll(serie);
        for (XYChart.Data<Number, String> dato : serie.getData()) {
            Tooltip.install(dato.getNode(), tooltip(dato.getYValue() + ": " + dato.getXValue() + " muestra(s)"));
        }
        listaAlertas.setItems(FXCollections.observableArrayList(d.alertas()));
    }

    private void mostrarTendencia(Tendencia t, Sitio sitio) {
        lblTendencia.setText("Tendencia de " + t.analisis() + " (" + t.unidad() + ")");
        graficoTendencia.getData().clear();
        List<Punto> puntos = t.puntos();
        if (puntos.isEmpty()) {
            lblResumenTendencia.setText("Sin resultados de " + t.analisis() + " en " + sitio.nombre() + ".");
            return;
        }
        long desde = puntos.get(0).fecha().toLocalDate().toEpochDay();
        long hasta = puntos.get(puntos.size() - 1).fecha().toLocalDate().toEpochDay();
        long margen = Math.max(3, (hasta - desde) / 20);
        ejeFecha.setLowerBound(desde - margen);
        ejeFecha.setUpperBound(hasta + margen);
        ejeFecha.setTickUnit(Math.max(1, (hasta - desde + 2 * margen) / 8.0));

        XYChart.Series<Number, Number> min = lineaReferencia(desde - margen, hasta + margen, t.valorMinNormal());
        XYChart.Series<Number, Number> max = lineaReferencia(desde - margen, hasta + margen, t.valorMaxNormal());
        XYChart.Series<Number, Number> valores = new XYChart.Series<>();
        for (Punto p : puntos) {
            valores.getData().add(new XYChart.Data<>(p.fecha().toLocalDate().toEpochDay(), p.valor()));
        }
        graficoTendencia.getData().setAll(List.of(min, max, valores));

        estiloReferencia(min);
        estiloReferencia(max);
        valores.getNode().setStyle("-fx-stroke: #1e4f80; -fx-stroke-width: 2px;");
        for (int i = 0; i < puntos.size(); i++) {
            Punto p = puntos.get(i);
            Node nodo = valores.getData().get(i).getNode();
            String color = p.anomalo() ? "#b91c1c" : !p.dentroRango() ? "#d97706" : "#1e4f80";
            String forma = p.anomalo() ? "-fx-shape: \"M5,0 L10,5 L5,10 L0,5 Z\"; -fx-padding: 7px;" : "-fx-padding: 4.5px;";
            nodo.setStyle("-fx-background-color: white, " + color + "; -fx-background-insets: 0, 2;"
                    + "-fx-background-radius: 6px;" + forma);
            nodo.setOnMouseClicked(ev -> Navigator.irADetalleMuestra(p.idMuestra()));
            nodo.setCursor(javafx.scene.Cursor.HAND);
            Tooltip.install(nodo, tooltip(p.codigoMuestra() + " · " + p.sitio() + "\n"
                    + p.fecha().toLocalDate().format(FECHA) + "\n"
                    + numero(p.valor()) + " " + t.unidad()
                    + (p.anomalo() ? "\n⚠ Valor atípico para el sitio" : "")
                    + (!p.dentroRango() ? "\n⚠ Fuera del rango normal" : "")
                    + "\nClic para abrir la muestra"));
        }
        long atipicos = puntos.stream().filter(Punto::anomalo).count();
        long fuera = puntos.stream().filter(p -> !p.dentroRango()).count();
        lblResumenTendencia.setText(puntos.size() + " resultados · rango normal " + numero(t.valorMinNormal()) + "–"
                + numero(t.valorMaxNormal()) + " " + t.unidad() + " · " + fuera + " fuera de rango · " + atipicos + " atípicos");
    }

    private XYChart.Series<Number, Number> lineaReferencia(long desde, long hasta, BigDecimal valor) {
        XYChart.Series<Number, Number> s = new XYChart.Series<>();
        s.getData().add(new XYChart.Data<>(desde, valor));
        s.getData().add(new XYChart.Data<>(hasta, valor));
        return s;
    }

    private void estiloReferencia(XYChart.Series<Number, Number> s) {
        s.getNode().setStyle("-fx-stroke: #94a3b8; -fx-stroke-width: 1.5px; -fx-stroke-dash-array: 6 5;");
        for (XYChart.Data<Number, Number> d : s.getData()) {
            d.getNode().setVisible(false);
        }
    }

    // ---------- helpers ----------

    private void marcar(Label kpi, boolean alerta) {
        kpi.getStyleClass().remove("kpi-valor-alerta");
        if (alerta) {
            kpi.getStyleClass().add("kpi-valor-alerta");
        }
    }

    private Tooltip tooltip(String texto) {
        Tooltip t = new Tooltip(texto);
        t.setShowDelay(Duration.millis(80));
        return t;
    }

    private String numero(BigDecimal n) {
        return n == null ? "" : n.stripTrailingZeros().toPlainString();
    }

    @FunctionalInterface
    private interface Tarea {
        void ejecutar() throws Exception;
    }

    private void enSegundoPlano(Tarea tarea) {
        Thread hilo = new Thread(() -> {
            try {
                tarea.ejecutar();
            } catch (Exception e) {
                Platform.runLater(() -> {
                    lblMensaje.setStyle("-fx-text-fill: #dc2626;");
                    lblMensaje.setText("No se pudo cargar el panel: " + e.getMessage());
                });
            }
        });
        hilo.setDaemon(true);
        hilo.start();
    }

    /** Celda de alerta: etiqueta del tipo (texto + color) y el mensaje. */
    private static class CeldaAlerta extends ListCell<Alerta> {
        @Override
        protected void updateItem(Alerta a, boolean empty) {
            super.updateItem(a, empty);
            if (empty || a == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            String tipo = switch (a.tipo()) {
                case "ANOMALIA" -> "Atípico";
                case "FUERA_RANGO" -> "Fuera de rango";
                case "RECHAZO" -> "Rechazada";
                case "CALIBRACION" -> "alta".equals(a.nivel()) ? "Vencida" : "Por vencer";
                case "CONSERVACION" -> "Conservación";
                default -> a.tipo();
            };
            Label badge = Badges.crear(tipo);
            if ("CONSERVACION".equals(a.tipo())) {
                badge.getStyleClass().setAll("label", "badge", "alta".equals(a.nivel()) ? "badge-rojo" : "badge-amarillo");
            }
            badge.setMinWidth(Region.USE_PREF_SIZE);
            Label mensaje = new Label(a.mensaje());
            mensaje.setWrapText(true);
            mensaje.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(mensaje, Priority.ALWAYS);
            HBox fila = new HBox(10, badge, mensaje);
            fila.setStyle("-fx-alignment: center-left; -fx-padding: 4 2;");
            fila.setMaxWidth(Double.MAX_VALUE);
            fila.prefWidthProperty().bind(getListView().widthProperty().subtract(30));
            setText(null);
            setGraphic(fila);
        }
    }
}
