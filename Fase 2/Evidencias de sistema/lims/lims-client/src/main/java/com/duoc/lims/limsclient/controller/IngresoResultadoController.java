package com.duoc.lims.limsclient.controller;

import com.duoc.lims.limsclient.Navigator;
import com.duoc.lims.limsclient.model.Anomalia;
import com.duoc.lims.limsclient.model.CorreccionResultadoRequest;
import com.duoc.lims.limsclient.model.Equipo;
import com.duoc.lims.limsclient.model.IngresoResultadoRequest;
import com.duoc.lims.limsclient.model.MuestraDetalle.AnalisisItem;
import com.duoc.lims.limsclient.model.MuestraDetalle.ResultadoItem;
import com.duoc.lims.limsclient.service.ApiClient;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ingreso de un resultado (RF02/RF03). v5: equipo calibrado obligatorio, aviso de valor atípico en
 * vivo y modo "corrección", que crea una versión nueva del resultado con motivo obligatorio.
 */
public class IngresoResultadoController {

    @FXML private Label lblTituloDialogo;
    @FXML private Label lblAnalisis;
    @FXML private Label lblRango;
    @FXML private Label lblContextoCorreccion;
    @FXML private Label lblAvisoRango;
    @FXML private Label lblAnomalia;
    @FXML private Label lblAvisoEquipo;
    @FXML private Label lblObservacionesTitulo;
    @FXML private Label lblMotivoTitulo;
    @FXML private Label lblError;
    @FXML private TextField txtValor;
    @FXML private ComboBox<Equipo> cbEquipo;
    @FXML private TextArea txtObservaciones;
    @FXML private TextArea txtMotivo;
    @FXML private Button btnGuardar;

    private final ApiClient apiClient = new ApiClient();
    // Espera a que el analista deje de escribir antes de consultar el historial (evita una consulta por tecla).
    private final PauseTransition pausa = new PauseTransition(Duration.millis(450));
    private AnalisisItem analisis;
    private ResultadoItem vigente;   // != null en modo corrección
    private boolean guardado = false;

    @FXML
    public void initialize() {
        // Cada vez que cambia el valor, se revisa el rango (RF03) y luego la anomalía (v5) en vivo.
        txtValor.textProperty().addListener((obs, anterior, nuevo) -> {
            actualizarAvisoRango();
            pausa.playFromStart();
        });
        pausa.setOnFinished(e -> consultarAnomalia());
        cbEquipo.valueProperty().addListener((o, a, e) -> lblAvisoEquipo.setText(e != null && e.calibracionVencida()
                ? "⚠ Este equipo tiene la calibración vencida: no se puede usar para informar resultados."
                : ""));
        cargarEquipos();
    }

    /** Modo ingreso: lo llama DetalleMuestraController antes de mostrar el diálogo. */
    public void setAnalisis(AnalisisItem analisis) {
        this.analisis = analisis;
        lblAnalisis.setText(analisis.nombreAnalisis());
        lblRango.setText("Rango normal: " + numero(analisis.valorMinNormal()) + " – "
                + numero(analisis.valorMaxNormal()) + " " + analisis.unidadMedida());
    }

    /** Modo corrección (v5): crea la versión siguiente del resultado; el valor anterior se conserva. */
    public void setCorreccion(AnalisisItem analisis) {
        setAnalisis(analisis);
        this.vigente = analisis.resultado();
        int siguiente = (vigente.version() == null ? 1 : vigente.version()) + 1;
        lblTituloDialogo.setText("Corregir resultado (versión " + siguiente + ")");
        String contexto = "Valor vigente (v" + (siguiente - 1) + "): " + numero(vigente.valorResultado()) + " "
                + analisis.unidadMedida() + ", ingresado por " + vigente.usuarioIngreso() + ".";
        if ("Rechazado".equals(analisis.estadoAprobacion()) && analisis.comentarioAprobacion() != null) {
            contexto += "\nMotivo del rechazo: " + analisis.comentarioAprobacion();
        }
        contexto += "\nEl valor anterior NO se borra: queda en el historial de versiones.";
        lblContextoCorreccion.setText(contexto);
        mostrar(lblContextoCorreccion, true);
        mostrar(lblMotivoTitulo, true);
        mostrar(txtMotivo, true);
        txtValor.setText(numero(vigente.valorResultado()));
        txtObservaciones.setText(vigente.observaciones());
        // si la lista de equipos ya llegó, se preselecciona el equipo de la versión vigente
        cbEquipo.getItems().stream().filter(e -> e.id().equals(vigente.idEquipo())).findFirst()
                .ifPresent(cbEquipo::setValue);
        btnGuardar.setText("Guardar versión " + siguiente);
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
        Equipo equipo = cbEquipo.getValue();
        if (equipo == null) {
            lblError.setText("Selecciona el equipo con que se midió (trazabilidad metrológica).");
            return;
        }
        if (equipo.calibracionVencida()) {
            lblError.setText("El equipo " + equipo.codigo() + " tiene la calibración vencida: usa otro equipo.");
            return;
        }
        String observaciones = textoDe(txtObservaciones.getText());
        if (fueraDeRango(valor) && observaciones == null) {
            lblError.setText("El valor está fuera de rango: debes justificarlo en observaciones.");
            return;
        }
        String motivo = textoDe(txtMotivo.getText());
        if (vigente != null && motivo == null) {
            lblError.setText("Indica el motivo del cambio: toda corrección de un dato debe justificarse.");
            return;
        }

        btnGuardar.setDisable(true);
        Thread hilo = new Thread(() -> {
            try {
                if (vigente == null) {
                    apiClient.ingresarResultado(new IngresoResultadoRequest(analisis.idMuestraAnalisis(), valor,
                            equipo.id(), null, Navigator.getUsuarioActual().getId(), observaciones));
                } else {
                    apiClient.corregirResultado(vigente.id(),
                            new CorreccionResultadoRequest(valor, equipo.id(), observaciones, motivo));
                }
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

    private void cargarEquipos() {
        Thread hilo = new Thread(() -> {
            try {
                List<Equipo> equipos = apiClient.listarEquipos(true);
                Platform.runLater(() -> {
                    cbEquipo.setItems(FXCollections.observableArrayList(equipos));
                    if (vigente != null && vigente.idEquipo() != null) {
                        equipos.stream().filter(e -> e.id().equals(vigente.idEquipo())).findFirst()
                                .ifPresent(cbEquipo::setValue);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> lblError.setText("No se pudo cargar la lista de equipos: " + e.getMessage()));
            }
        });
        hilo.setDaemon(true);
        hilo.start();
    }

    /** v5: compara el valor con el historial del sitio (no guarda nada). */
    private void consultarAnomalia() {
        BigDecimal valor = leerValor();
        if (valor == null || analisis == null) {
            mostrar(lblAnomalia, false);
            return;
        }
        Integer idResultado = vigente == null ? null : vigente.id();
        Thread hilo = new Thread(() -> {
            try {
                Anomalia a = apiClient.evaluarAnomalia(analisis.idMuestraAnalisis(), valor, idResultado);
                Platform.runLater(() -> {
                    if (!valor.equals(leerValor())) {
                        return;   // el analista ya escribió otro valor
                    }
                    lblAnomalia.getStyleClass().setAll("label",
                            !a.evaluado() ? "aviso-info" : a.anomalo() ? "aviso-alerta" : "aviso-ok");
                    lblAnomalia.setText((a.anomalo() ? "⚠ " : a.evaluado() ? "✓ " : "ℹ ") + a.mensaje()
                            + (a.anomalo() ? "\nPuedes guardarlo igual: el supervisor lo verá destacado." : ""));
                    mostrar(lblAnomalia, true);
                });
            } catch (Exception e) {
                Platform.runLater(() -> mostrar(lblAnomalia, false));
            }
        });
        hilo.setDaemon(true);
        hilo.start();
    }

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

    private void mostrar(javafx.scene.Node nodo, boolean visible) {
        nodo.setVisible(visible);
        nodo.setManaged(visible);
        // la ventana crece o se achica con el aviso, para que no quede texto cortado
        if (nodo.getScene() != null && nodo.getScene().getWindow() instanceof Stage s) {
            s.sizeToScene();
        }
    }

    private void cerrar() {
        pausa.stop();
        ((Stage) btnGuardar.getScene().getWindow()).close();
    }
}
