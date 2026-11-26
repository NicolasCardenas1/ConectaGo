package com.duoc.lims.limsclient.controller;

import com.duoc.lims.limsclient.model.AsistenteRequest;
import com.duoc.lims.limsclient.model.AsistenteRespuesta;
import com.duoc.lims.limsclient.service.ApiClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * v6 — Chatbot de trazabilidad. Envía la pregunta (y la conversación previa, para preguntas de
 * seguimiento) a POST /api/asistente y muestra la respuesta con las consultas que hizo el backend.
 * La llamada corre en un hilo aparte para no congelar la interfaz (la IA puede tardar unos segundos).
 */
public class AsistenteController {

    private static final List<String> SUGERENCIAS = List.of(
            "Resumen del laboratorio",
            "¿Qué resultados faltan por aprobar?",
            "¿Qué equipos tienen la calibración vencida o por vencer?",
            "Resultados fuera de rango",
            "¿Qué muestras se tomaron en Bahía Ilque?",
            "Salinidad en Bahía Ilque");

    @FXML private FlowPane panelSugerencias;
    @FXML private ScrollPane scrollChat;
    @FXML private VBox contenedorChat;
    @FXML private TextField txtPregunta;
    @FXML private Button btnEnviar;
    @FXML private Label lblEstado;

    private final ApiClient apiClient = new ApiClient();
    private final List<AsistenteRequest.Mensaje> historial = new ArrayList<>();
    private boolean esperando = false;

    @FXML
    public void initialize() {
        for (String s : SUGERENCIAS) {
            Button b = new Button(s);
            b.getStyleClass().add("btn-secondary");
            b.setOnAction(e -> enviar(s));
            panelSugerencias.getChildren().add(b);
        }
        // baja automáticamente al último mensaje
        contenedorChat.heightProperty().addListener((o, a, n) -> scrollChat.setVvalue(1.0));
        bienvenida();
    }

    /** Abre el asistente con una pregunta ya enviada (ej. desde el detalle de una muestra). */
    public void preguntar(String pregunta) {
        enviar(pregunta);
    }

    @FXML
    private void onEnviar() {
        enviar(txtPregunta.getText());
    }

    @FXML
    private void onNuevaConversacion() {
        if (esperando) {
            return;
        }
        historial.clear();
        contenedorChat.getChildren().clear();
        bienvenida();
    }

    private void enviar(String texto) {
        if (esperando || texto == null || texto.isBlank()) {
            return;
        }
        String pregunta = texto.trim();
        txtPregunta.clear();
        agregarBurbuja(pregunta, true, null);
        Label pensando = agregarBurbuja("Consultando la trazabilidad en la base de datos...", false, null);
        setEsperando(true);

        AsistenteRequest datos = new AsistenteRequest(pregunta, List.copyOf(historial));
        Thread hilo = new Thread(() -> {
            try {
                AsistenteRespuesta r = apiClient.consultarAsistente(datos);
                Platform.runLater(() -> {
                    quitarBurbuja(pensando);
                    agregarBurbuja(r.respuesta(), false, r);
                    historial.add(new AsistenteRequest.Mensaje("usuario", pregunta));
                    historial.add(new AsistenteRequest.Mensaje("asistente", r.respuesta()));
                    setEsperando(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    quitarBurbuja(pensando);
                    agregarBurbuja("No se pudo consultar al asistente: " + e.getMessage(), false, null);
                    setEsperando(false);
                });
            }
        });
        hilo.setDaemon(true);
        hilo.start();
    }

    private void bienvenida() {
        agregarBurbuja("Hola. Respondo con lo registrado en el LIMS. Puedes preguntarme por:\n"
                + "- Una muestra por su código (H-2026-0001): dónde está guardada, con qué equipo se tomó, "
                + "quién la aprobó... y seguir preguntando sin repetir el código.\n"
                + "- Un equipo (EQ-005) o los equipos con calibración vencida.\n"
                + "- Pendientes de aprobación, resultados fuera de rango, valores atípicos, rechazos y correcciones.\n"
                + "- Muestras por sitio, tipo, estado, investigador o fecha, y valores de un parámetro "
                + "(\"salinidad en Bahía Ilque\").\n"
                + "Escribe \"ayuda\" para ver más ejemplos.", false, null);
    }

    private void setEsperando(boolean valor) {
        esperando = valor;
        btnEnviar.setDisable(valor);
        txtPregunta.setDisable(valor);
        panelSugerencias.setDisable(valor);
        if (!valor) {
            txtPregunta.requestFocus();
        }
    }

    /** Agrega un mensaje: el del usuario a la derecha (azul), el del asistente a la izquierda (blanco). */
    private Label agregarBurbuja(String texto, boolean deUsuario, AsistenteRespuesta meta) {
        Label burbuja = new Label(texto);
        burbuja.setWrapText(true);
        burbuja.setMaxWidth(720);
        burbuja.setMinHeight(Region.USE_PREF_SIZE);
        burbuja.setStyle(deUsuario
                ? "-fx-background-color: #1e4f80; -fx-text-fill: white; -fx-padding: 10 14; -fx-background-radius: 12; -fx-font-size: 13px;"
                : "-fx-background-color: white; -fx-text-fill: #0f172a; -fx-padding: 10 14; -fx-background-radius: 12;"
                  + " -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-font-size: 13px;");

        VBox columna = new VBox(4, burbuja);
        columna.setAlignment(deUsuario ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        if (meta != null) {
            StringBuilder pie = new StringBuilder(meta.origen() == null ? "" : meta.origen());
            if (meta.consultas() != null && !meta.consultas().isEmpty()) {
                pie.append(" · consultó: ").append(String.join("; ", meta.consultas()));
            }
            Label lblPie = new Label(pie.toString());
            lblPie.setWrapText(true);
            lblPie.setMaxWidth(720);
            lblPie.getStyleClass().add("hint");
            columna.getChildren().add(lblPie);
            if (meta.aviso() != null) {
                Label lblAviso = new Label(meta.aviso());
                lblAviso.setWrapText(true);
                lblAviso.setMaxWidth(720);
                lblAviso.setStyle("-fx-text-fill: #b45309; -fx-font-size: 11px;");
                columna.getChildren().add(lblAviso);
            }
        }
        HBox fila = new HBox(columna);
        fila.setAlignment(deUsuario ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        burbuja.setUserData(fila);
        contenedorChat.getChildren().add(fila);
        return burbuja;
    }

    private void quitarBurbuja(Label burbuja) {
        contenedorChat.getChildren().remove((HBox) burbuja.getUserData());
    }
}
