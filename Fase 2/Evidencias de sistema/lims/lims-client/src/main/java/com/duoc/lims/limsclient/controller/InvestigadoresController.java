package com.duoc.lims.limsclient.controller;

import com.duoc.lims.limsclient.model.Investigador;
import com.duoc.lims.limsclient.model.InvestigadorRequest;
import com.duoc.lims.limsclient.service.ApiClient;
import com.duoc.lims.limsclient.ui.Badges;
import com.duoc.lims.limsclient.ui.Formularios;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.util.List;
import java.util.Optional;

/** Pantalla de investigadores (disponible para todos los roles). */
public class InvestigadoresController {

    @FXML private TableView<Investigador> tabla;
    @FXML private TableColumn<Investigador, String> colNombre;
    @FXML private TableColumn<Investigador, String> colInstitucion;
    @FXML private TableColumn<Investigador, String> colProyecto;
    @FXML private TableColumn<Investigador, String> colEmail;
    @FXML private TableColumn<Investigador, String> colTelefono;
    @FXML private TableColumn<Investigador, String> colEstado;
    @FXML private TextField txtBuscar;
    @FXML private Button btnEditar;
    @FXML private Button btnEstado;
    @FXML private Label lblMensaje;

    private final ApiClient apiClient = new ApiClient();
    private final ObservableList<Investigador> todos = FXCollections.observableArrayList();
    private final FilteredList<Investigador> filtrados = new FilteredList<>(todos, i -> true);

    @FXML
    public void initialize() {
        colNombre.setCellValueFactory(d -> texto(d.getValue().nombreCompleto()));
        colInstitucion.setCellValueFactory(d -> texto(d.getValue().institucion()));
        colProyecto.setCellValueFactory(d -> texto(d.getValue().proyecto()));
        colEmail.setCellValueFactory(d -> texto(d.getValue().email()));
        colTelefono.setCellValueFactory(d -> texto(d.getValue().telefono()));
        colEstado.setCellValueFactory(d -> texto(d.getValue().activo() ? "Activo" : "Inactivo"));
        colEstado.setCellFactory(c -> Badges.celda());

        tabla.setItems(filtrados);
        txtBuscar.textProperty().addListener((o, a, t) -> {
            String b = t == null ? "" : t.trim().toLowerCase();
            filtrados.setPredicate(i -> b.isEmpty()
                    || contiene(i.nombreCompleto(), b) || contiene(i.institucion(), b) || contiene(i.proyecto(), b));
        });
        tabla.getSelectionModel().selectedItemProperty().addListener((o, a, i) -> actualizarBotones(i));
        tabla.setRowFactory(tv -> {
            TableRow<Investigador> fila = new TableRow<>();
            fila.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !fila.isEmpty()) {
                    onEditar();
                }
            });
            return fila;
        });
        actualizarBotones(null);
        cargar();
    }

    @FXML
    private void onNuevo() {
        abrirFormulario(null).ifPresent(datos ->
                ejecutar(() -> apiClient.crearInvestigador(datos),
                        "Investigador " + datos.nombre() + " " + datos.apellido() + " registrado."));
    }

    @FXML
    private void onEditar() {
        Investigador sel = tabla.getSelectionModel().getSelectedItem();
        if (sel == null) {
            mostrarError("Selecciona un investigador de la tabla.");
            return;
        }
        abrirFormulario(sel).ifPresent(datos ->
                ejecutar(() -> apiClient.actualizarInvestigador(sel.id(), datos),
                        "Datos de " + datos.nombre() + " " + datos.apellido() + " actualizados."));
    }

    @FXML
    private void onCambiarEstado() {
        Investigador sel = tabla.getSelectionModel().getSelectedItem();
        if (sel == null) {
            mostrarError("Selecciona un investigador de la tabla.");
            return;
        }
        boolean activar = !sel.activo();
        ejecutar(() -> apiClient.cambiarEstadoInvestigador(sel.id(), activar),
                sel.nombreCompleto() + (activar ? " activado." : " desactivado (ya no aparecerá al registrar muestras)."));
    }

    /** Formulario de alta/edición. Devuelve vacío si el usuario cancela. */
    private Optional<InvestigadorRequest> abrirFormulario(Investigador actual) {
        TextField nombre = new TextField();
        TextField apellido = new TextField();
        TextField institucion = new TextField();
        TextField proyecto = new TextField();
        TextField email = new TextField();
        TextField telefono = new TextField();
        institucion.setPromptText("Ej: Universidad de Los Lagos");
        proyecto.setPromptText("Ej: Monitoreo de macroalgas en el seno de Reloncaví");
        if (actual != null) {
            nombre.setText(actual.nombre());
            apellido.setText(actual.apellido());
            institucion.setText(actual.institucion());
            proyecto.setText(actual.proyecto());
            email.setText(actual.email());
            telefono.setText(actual.telefono());
        }
        Label error = Formularios.lblError();
        GridPane form = Formularios.grilla("Nombre *", nombre, "Apellido *", apellido, "Institución", institucion,
                "Proyecto / línea", proyecto, "Email", email, "Teléfono", telefono);
        form.add(error, 0, 6, 2, 1);

        ButtonType guardar = Formularios.boton(actual == null ? "Registrar" : "Guardar cambios");
        Dialog<ButtonType> d = Formularios.dialogo(tabla.getScene().getWindow(), tabla.getScene().getStylesheets(),
                actual == null ? "Nuevo investigador" : "Editar investigador",
                actual == null ? "Registra a quien encarga el muestreo" : actual.nombreCompleto(), form, guardar);
        d.getDialogPane().lookupButton(guardar).addEventFilter(ActionEvent.ACTION, ev -> {
            if (Formularios.vacio(nombre) || Formularios.vacio(apellido)) {
                error.setText("Nombre y apellido son obligatorios.");
                ev.consume();
            }
        });

        Optional<ButtonType> r = d.showAndWait();
        if (r.isEmpty() || r.get() != guardar) {
            return Optional.empty();
        }
        return Optional.of(new InvestigadorRequest(Formularios.texto(nombre), Formularios.texto(apellido),
                Formularios.texto(institucion), Formularios.texto(email), Formularios.texto(telefono),
                Formularios.texto(proyecto)));
    }

    // ---------- carga y helpers ----------

    private void cargar() {
        Thread hilo = new Thread(() -> {
            try {
                List<Investigador> lista = apiClient.listarInvestigadores(false);
                Platform.runLater(() -> todos.setAll(lista));
            } catch (Exception e) {
                Platform.runLater(() -> mostrarError("No se pudo cargar la lista: " + e.getMessage()));
            }
        });
        hilo.setDaemon(true);
        hilo.start();
    }

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

    private void actualizarBotones(Investigador i) {
        btnEditar.setDisable(i == null);
        btnEstado.setDisable(i == null);
        btnEstado.setText(i != null && !i.activo() ? "Activar" : "Desactivar");
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
