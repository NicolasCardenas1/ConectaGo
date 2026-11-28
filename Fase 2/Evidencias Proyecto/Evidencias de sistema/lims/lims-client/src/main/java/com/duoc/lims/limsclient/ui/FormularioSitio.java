package com.duoc.lims.limsclient.ui;

import com.duoc.lims.limsclient.model.Sitio;
import com.duoc.lims.limsclient.model.SitioRequest;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

import java.lang.ref.Reference;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * v5 — Diálogo de alta/edición de un sitio de muestreo (lo usan Sitios y Registro de muestra).
 * Incluye un mapa (mapa-selector.html) para elegir el punto con un clic en vez de escribir coordenadas.
 */
public final class FormularioSitio {

    /** Tipos de ambiente (lista desplegable). */
    public static final List<String> TIPOS_AMBIENTE = List.of(
            "Marino costero", "Estuario", "Lago", "Río", "Humedal", "Otro");

    private FormularioSitio() {
    }

    /**
     * Abre el formulario. {@code actual} null = alta; {@code lat}/{@code lon} prellenan las coordenadas
     * (por ejemplo, desde un clic en el mapa). Devuelve vacío si el usuario cancela.
     */
    public static Optional<SitioRequest> abrir(Scene escena, Sitio actual, BigDecimal lat, BigDecimal lon) {
        TextField codigo = new TextField();
        TextField nombre = new TextField();
        ComboBox<String> tipo = new ComboBox<>();
        tipo.getItems().setAll(TIPOS_AMBIENTE);
        tipo.setMaxWidth(Double.MAX_VALUE);
        TextField latitud = new TextField();
        TextField longitud = new TextField();
        TextField comuna = new TextField();
        TextField region = new TextField("Los Lagos");
        TextField descripcion = new TextField();
        codigo.setPromptText("Vacío = se genera (ST-007...)");
        nombre.setPromptText("Ej: Bahía Ilque, estación 3");
        latitud.setPromptText("Ej: -41.6215 (sur = negativo)");
        longitud.setPromptText("Ej: -73.0608 (oeste = negativo)");
        if (actual != null) {
            codigo.setText(actual.codigo());
            codigo.setDisable(true);
            nombre.setText(actual.nombre());
            tipo.setValue(actual.tipoAmbiente());
            latitud.setText(texto(actual.latitud()));
            longitud.setText(texto(actual.longitud()));
            comuna.setText(actual.comuna());
            region.setText(actual.region());
            descripcion.setText(actual.descripcion());
        }
        if (lat != null && lon != null) {
            latitud.setText(lat.setScale(6, RoundingMode.HALF_UP).toPlainString());
            longitud.setText(lon.setScale(6, RoundingMode.HALF_UP).toPlainString());
        }
        Label error = Formularios.lblError();
        error.setMaxWidth(360);
        GridPane form = Formularios.grilla("Código", codigo, "Nombre *", nombre, "Tipo de ambiente *", tipo,
                "Latitud *", latitud, "Longitud *", longitud, "Comuna", comuna, "Región", region,
                "Descripción", descripcion);
        form.add(error, 0, 8, 2, 1);

        // Mapa para elegir el punto: clic o arrastre llenan latitud/longitud; escribirlas mueve el marcador.
        WebView mapa = new WebView();
        mapa.setPrefSize(480, 420);
        mapa.setMinSize(320, 300);
        PuenteSelector puente = new PuenteSelector(latitud, longitud, error);
        WebEngine engine = mapa.getEngine();
        engine.setUserAgent(engine.getUserAgent() + " LIMS-ConectaGo/1.0");   // política de uso de OpenStreetMap
        engine.getLoadWorker().stateProperty().addListener((o, a, estado) -> {
            if (estado == Worker.State.SUCCEEDED) {
                ((JSObject) engine.executeScript("window")).setMember("java", puente);
                puente.listo = true;
                moverMarcador(engine, latitud, longitud, true);
            }
        });
        latitud.textProperty().addListener((o, a, n) -> {
            if (puente.listo && !puente.escribiendo) {
                moverMarcador(engine, latitud, longitud, true);
            }
        });
        longitud.textProperty().addListener((o, a, n) -> {
            if (puente.listo && !puente.escribiendo) {
                moverMarcador(engine, latitud, longitud, true);
            }
        });
        engine.load(FormularioSitio.class.getResource("/com/duoc/lims/limsclient/mapa-selector.html").toExternalForm());

        Label ayudaMapa = new Label("Busca el lugar y haz clic en el mapa para fijar las coordenadas.");
        ayudaMapa.getStyleClass().add("field-name");
        VBox panelMapa = new VBox(6, ayudaMapa, mapa);
        VBox.setVgrow(mapa, Priority.ALWAYS);
        HBox contenido = new HBox(16, form, panelMapa);
        HBox.setHgrow(panelMapa, Priority.ALWAYS);

        ButtonType guardar = Formularios.boton(actual == null ? "Registrar sitio" : "Guardar cambios");
        Dialog<ButtonType> d = Formularios.dialogo(escena.getWindow(), escena.getStylesheets(),
                actual == null ? "Nuevo sitio de muestreo" : "Editar sitio",
                actual == null ? "Punto georreferenciado donde se toman muestras" : actual.codigo() + " · " + actual.nombre(),
                contenido, guardar);
        d.setResizable(true);
        d.getDialogPane().lookupButton(guardar).addEventFilter(ActionEvent.ACTION, ev -> {
            String problema = validar(nombre, tipo, latitud, longitud);
            if (problema != null) {
                error.setText(problema);
                ev.consume();
            }
        });
        Optional<ButtonType> r = d.showAndWait();
        // El WebView solo guarda una referencia débil al puente: se mantiene vivo mientras el diálogo esté abierto.
        Reference.reachabilityFence(puente);
        if (r.isEmpty() || r.get() != guardar) {
            return Optional.empty();
        }
        return Optional.of(new SitioRequest(actual == null ? Formularios.texto(codigo) : actual.codigo(),
                Formularios.texto(nombre), numero(latitud), numero(longitud), tipo.getValue(),
                Formularios.texto(comuna), Formularios.texto(region), Formularios.texto(descripcion)));
    }

    /** Pone el marcador en las coordenadas escritas, si son válidas. */
    private static void moverMarcador(WebEngine engine, TextField latitud, TextField longitud, boolean centrar) {
        BigDecimal la = numero(latitud);
        BigDecimal lo = numero(longitud);
        if (la == null || lo == null
                || la.abs().compareTo(BigDecimal.valueOf(90)) > 0 || lo.abs().compareTo(BigDecimal.valueOf(180)) > 0) {
            return;
        }
        try {
            engine.executeScript("ponerMarcador(" + la.toPlainString() + "," + lo.toPlainString() + "," + centrar + ")");
        } catch (RuntimeException ignorada) {
            // Sin conexión no hay Leaflet: el formulario sigue funcionando con las coordenadas escritas.
        }
    }

    /** Métodos que llama el JavaScript del mapa como window.java.xxx(...). Deben ser públicos. */
    public static final class PuenteSelector {
        private final TextField latitud;
        private final TextField longitud;
        private final Label error;
        boolean listo = false;
        boolean escribiendo = false;

        PuenteSelector(TextField latitud, TextField longitud, Label error) {
            this.latitud = latitud;
            this.longitud = longitud;
            this.error = error;
        }

        public void ubicar(double lat, double lon) {
            Platform.runLater(() -> {
                escribiendo = true;   // evita que el cambio de texto vuelva a centrar el mapa
                latitud.setText(BigDecimal.valueOf(lat).setScale(6, RoundingMode.HALF_UP).toPlainString());
                longitud.setText(BigDecimal.valueOf(lon).setScale(6, RoundingMode.HALF_UP).toPlainString());
                escribiendo = false;
                error.setText("");
            });
        }
    }

    private static String validar(TextField nombre, ComboBox<String> tipo, TextField lat, TextField lon) {
        if (Formularios.vacio(nombre) || tipo.getValue() == null) {
            return "Nombre y tipo de ambiente son obligatorios.";
        }
        BigDecimal la = numero(lat);
        BigDecimal lo = numero(lon);
        if (la == null || lo == null) {
            return "Latitud y longitud deben ser números en grados decimales (ej: -41.6215).";
        }
        if (la.abs().compareTo(BigDecimal.valueOf(90)) > 0 || lo.abs().compareTo(BigDecimal.valueOf(180)) > 0) {
            return "Coordenadas fuera de rango: latitud entre -90 y 90, longitud entre -180 y 180.";
        }
        return null;
    }

    private static BigDecimal numero(TextField campo) {
        String t = Formularios.texto(campo);
        if (t == null) {
            return null;
        }
        try {
            return new BigDecimal(t.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String texto(BigDecimal n) {
        return n == null ? "" : n.stripTrailingZeros().toPlainString();
    }
}
