package com.duoc.lims.limsclient.controller;

import com.duoc.lims.limsclient.Navigator;
import com.duoc.lims.limsclient.model.Investigador;
import com.duoc.lims.limsclient.model.InvestigadorRequest;
import com.duoc.lims.limsclient.model.MuestraRequest;
import com.duoc.lims.limsclient.service.ApiClient;
import com.duoc.lims.limsclient.ui.Formularios;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/** Registro de una muestra de terreno (RF01) con investigador, almacenamiento y firma de recepción. */
public class RegistroMuestraController {

    /** Tipos de muestra del dominio ambiental (deben coincidir con el ENUM de la BD). */
    private static final List<String> TIPOS = List.of(
            "Macroalga", "Microalga", "Agua de mar", "Agua dulce", "Sedimento", "Otro");

    @FXML private TextField txtCodigo;
    @FXML private ComboBox<Investigador> cbInvestigador;
    @FXML private ComboBox<String> cbTipoMuestra;
    @FXML private TextField txtSitio;
    @FXML private DatePicker dpFechaToma;
    @FXML private TextField txtHoraToma;
    @FXML private ComboBox<String> cbPrioridad;
    @FXML private TextField txtCondicion;
    @FXML private TextField txtUbicacion;
    @FXML private DatePicker dpVencimiento;
    @FXML private TextArea txtObservaciones;
    @FXML private TextField txtEntregadaPor;
    @FXML private TextField txtRecibidaPor;
    @FXML private CheckBox chkFirma;
    @FXML private Label lblEstado;
    @FXML private Button btnGuardar;
    @FXML private Button btnCancelar;

    private final ApiClient apiClient = new ApiClient();

    @FXML
    public void initialize() {
        cbTipoMuestra.setItems(FXCollections.observableArrayList(TIPOS));
        cbPrioridad.setItems(FXCollections.observableArrayList("Baja", "Media", "Alta"));
        cbPrioridad.setValue("Media");
        if (Navigator.getUsuarioActual() != null) {
            txtRecibidaPor.setText(Navigator.getUsuarioActual().getNombre() + " "
                    + Navigator.getUsuarioActual().getApellido() + " (usuario actual)");
        }
        // "Entregada por" sugiere el nombre del investigador elegido.
        cbInvestigador.valueProperty().addListener((o, anterior, nuevo) -> {
            if (nuevo != null && (txtEntregadaPor.getText() == null || txtEntregadaPor.getText().isBlank()
                    || (anterior != null && txtEntregadaPor.getText().equals(anterior.nombreCompleto())))) {
                txtEntregadaPor.setText(nuevo.nombreCompleto());
            }
        });
        cargarInvestigadores(null);
    }

    @FXML
    private void onCancelar() {
        Navigator.irAListado();
    }

    /** Alta rápida de un investigador sin salir del registro. */
    @FXML
    private void onNuevoInvestigador() {
        TextField nombre = new TextField();
        TextField apellido = new TextField();
        TextField institucion = new TextField();
        TextField proyecto = new TextField();
        TextField email = new TextField();
        Label error = Formularios.lblError();
        GridPane form = Formularios.grilla("Nombre *", nombre, "Apellido *", apellido,
                "Institución", institucion, "Proyecto / línea", proyecto, "Email", email);
        form.add(error, 0, 5, 2, 1);
        ButtonType registrar = Formularios.boton("Registrar");
        Dialog<ButtonType> d = Formularios.dialogo(btnGuardar.getScene().getWindow(),
                btnGuardar.getScene().getStylesheets(), "Nuevo investigador",
                "Registra a quien encarga el muestreo", form, registrar);
        d.getDialogPane().lookupButton(registrar).addEventFilter(ActionEvent.ACTION, ev -> {
            if (Formularios.vacio(nombre) || Formularios.vacio(apellido)) {
                error.setText("Nombre y apellido son obligatorios.");
                ev.consume();
            }
        });
        Optional<ButtonType> r = d.showAndWait();
        if (r.isEmpty() || r.get() != registrar) {
            return;
        }
        InvestigadorRequest datos = new InvestigadorRequest(Formularios.texto(nombre), Formularios.texto(apellido),
                Formularios.texto(institucion), Formularios.texto(email), null, Formularios.texto(proyecto));
        Thread hilo = new Thread(() -> {
            try {
                Investigador creado = apiClient.crearInvestigador(datos);
                Platform.runLater(() -> cargarInvestigadores(creado.id()));
            } catch (Exception e) {
                Platform.runLater(() -> lblEstado.setText("No se pudo registrar el investigador: " + e.getMessage()));
            }
        });
        hilo.setDaemon(true);
        hilo.start();
    }

    @FXML
    private void onGuardar() {
        lblEstado.setText("");
        Investigador investigador = cbInvestigador.getValue();
        String tipo = cbTipoMuestra.getValue();
        String sitio = txtSitio.getText();
        String prioridad = cbPrioridad.getValue();

        if (investigador == null || tipo == null || sitio == null || sitio.isBlank() || prioridad == null) {
            lblEstado.setText("Completa investigador, tipo de muestra, sitio de muestreo y prioridad.");
            return;
        }

        LocalDateTime fechaToma;
        try {
            fechaToma = fechaHoraToma();
        } catch (DateTimeParseException e) {
            lblEstado.setText("La hora de toma debe tener el formato HH:mm (ej: 09:30).");
            return;
        }
        if (fechaToma != null && fechaToma.isAfter(LocalDateTime.now())) {
            lblEstado.setText("La fecha de toma no puede ser futura.");
            return;
        }
        LocalDate vencimiento = dpVencimiento.getValue();
        if (fechaToma != null && vencimiento != null && vencimiento.isBefore(fechaToma.toLocalDate())) {
            lblEstado.setText("'Conservar hasta' no puede ser anterior a la fecha de toma.");
            return;
        }
        if (!chkFirma.isSelected()) {
            lblEstado.setText("Debes confirmar (firmar) la recepción de la muestra para registrarla.");
            return;
        }

        MuestraRequest datos = new MuestraRequest();
        datos.setIdInvestigador(investigador.id());
        datos.setTipoMuestra(tipo);
        datos.setProcedencia(sitio.trim());
        datos.setPrioridad(prioridad);
        datos.setObservaciones(txtObservaciones.getText());
        datos.setFechaToma(fechaToma);
        datos.setCondicionRecepcion(Formularios.texto(txtCondicion));
        datos.setUbicacionAlmacenamiento(Formularios.texto(txtUbicacion));
        datos.setFechaVencimiento(vencimiento);
        datos.setEntregadaPor(Formularios.texto(txtEntregadaPor));
        datos.setFirmaRecepcion(true);

        btnGuardar.setDisable(true);
        lblEstado.setText("Guardando...");

        Thread hilo = new Thread(() -> {
            try {
                apiClient.crearMuestra(datos);
                Platform.runLater(Navigator::irAListado);
            } catch (Exception e) {
                Platform.runLater(() -> {
                    lblEstado.setText("Error al guardar: " + e.getMessage());
                    btnGuardar.setDisable(false);
                });
            }
        });
        hilo.setDaemon(true);
        hilo.start();
    }

    // ---------- helpers ----------

    /** Fecha del DatePicker + hora escrita (HH:mm). Sin fecha devuelve null; sin hora usa 00:00. */
    private LocalDateTime fechaHoraToma() {
        LocalDate fecha = dpFechaToma.getValue();
        if (fecha == null) {
            return null;
        }
        String hora = txtHoraToma.getText();
        LocalTime t = hora == null || hora.isBlank() ? LocalTime.MIDNIGHT : LocalTime.parse(hora.trim());
        return LocalDateTime.of(fecha, t);
    }

    private void cargarInvestigadores(Integer seleccionarId) {
        Thread hilo = new Thread(() -> {
            try {
                List<Investigador> lista = apiClient.listarInvestigadores(true);
                Platform.runLater(() -> {
                    cbInvestigador.setItems(FXCollections.observableArrayList(lista));
                    if (seleccionarId != null) {
                        lista.stream().filter(i -> i.id().equals(seleccionarId)).findFirst()
                                .ifPresent(cbInvestigador::setValue);
                    }
                    if (lista.isEmpty()) {
                        lblEstado.setText("No hay investigadores registrados: usa + Nuevo investigador.");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> lblEstado.setText("No se pudo cargar la lista de investigadores: " + e.getMessage()));
            }
        });
        hilo.setDaemon(true);
        hilo.start();
    }
}
