package com.duoc.lims.limsclient.controller;

import com.duoc.lims.limsclient.Navigator;
import com.duoc.lims.limsclient.model.AnalisisCatalogo;
import com.duoc.lims.limsclient.model.AnalisisCatalogoRequest;
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
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Catálogo de parámetros con su rango normal: es la "tabla de rangos" que se muestra en la demo.
 * Todos los roles pueden verla; solo el Administrador crea, edita o desactiva parámetros.
 */
public class CatalogoController {

    @FXML private TableView<AnalisisCatalogo> tabla;
    @FXML private TableColumn<AnalisisCatalogo, String> colNombre;
    @FXML private TableColumn<AnalisisCatalogo, String> colUnidad;
    @FXML private TableColumn<AnalisisCatalogo, String> colMin;
    @FXML private TableColumn<AnalisisCatalogo, String> colMax;
    @FXML private TableColumn<AnalisisCatalogo, String> colMetodo;
    @FXML private TableColumn<AnalisisCatalogo, String> colDescripcion;
    @FXML private TableColumn<AnalisisCatalogo, String> colEstado;
    @FXML private Button btnNuevo;
    @FXML private Button btnEditar;
    @FXML private Button btnEstado;
    @FXML private CheckBox chkInactivos;
    @FXML private Label lblMensaje;
    @FXML private Label lblAyuda;

    private final ApiClient apiClient = new ApiClient();
    private final ObservableList<AnalisisCatalogo> todos = FXCollections.observableArrayList();
    private final FilteredList<AnalisisCatalogo> visibles = new FilteredList<>(todos, a -> Boolean.TRUE.equals(a.activo()));
    private final boolean esAdmin = Navigator.esAdministrador();

    @FXML
    public void initialize() {
        colNombre.setCellValueFactory(d -> texto(d.getValue().nombre()));
        colUnidad.setCellValueFactory(d -> texto(d.getValue().unidadMedida()));
        colMin.setCellValueFactory(d -> texto(numero(d.getValue().valorMinNormal())));
        colMax.setCellValueFactory(d -> texto(numero(d.getValue().valorMaxNormal())));
        colMetodo.setCellValueFactory(d -> texto(d.getValue().metodoReferencia()));
        colDescripcion.setCellValueFactory(d -> texto(d.getValue().descripcion()));
        colEstado.setCellValueFactory(d -> texto(Boolean.TRUE.equals(d.getValue().activo()) ? "Activo" : "Inactivo"));
        colEstado.setCellFactory(c -> Badges.celda());

        tabla.setItems(visibles);
        chkInactivos.selectedProperty().addListener((o, a, ver) ->
                visibles.setPredicate(x -> ver || Boolean.TRUE.equals(x.activo())));
        tabla.getSelectionModel().selectedItemProperty().addListener((o, a, x) -> actualizarBotones(x));

        // Solo el Administrador edita el catálogo (el backend también lo exige).
        for (Button b : List.of(btnNuevo, btnEditar, btnEstado)) {
            b.setVisible(esAdmin);
            b.setManaged(esAdmin);
        }
        lblAyuda.setText(esAdmin
                ? "Los rangos deben provenir de una fuente validada (norma, literatura o protocolo del laboratorio). "
                  + "Registra la fuente en Método / referencia."
                : "Solo el Administrador puede modificar el catálogo.");
        actualizarBotones(null);
        cargar();
    }

    @FXML
    private void onNuevo() {
        abrirFormulario(null).ifPresent(datos ->
                ejecutar(() -> apiClient.crearAnalisis(datos), "Parámetro " + datos.nombre() + " agregado."));
    }

    @FXML
    private void onEditar() {
        AnalisisCatalogo sel = tabla.getSelectionModel().getSelectedItem();
        if (sel == null) {
            mostrarError("Selecciona un parámetro de la tabla.");
            return;
        }
        abrirFormulario(sel).ifPresent(datos ->
                ejecutar(() -> apiClient.actualizarAnalisis(sel.id(), datos), "Parámetro " + datos.nombre() + " actualizado."));
    }

    @FXML
    private void onCambiarEstado() {
        AnalisisCatalogo sel = tabla.getSelectionModel().getSelectedItem();
        if (sel == null) {
            mostrarError("Selecciona un parámetro de la tabla.");
            return;
        }
        boolean activar = !Boolean.TRUE.equals(sel.activo());
        ejecutar(() -> apiClient.cambiarEstadoAnalisis(sel.id(), activar),
                sel.nombre() + (activar ? " activado." : " desactivado (ya no se podrá asignar a nuevas muestras)."));
    }

    private Optional<AnalisisCatalogoRequest> abrirFormulario(AnalisisCatalogo actual) {
        TextField nombre = new TextField();
        TextField unidad = new TextField();
        TextField min = new TextField();
        TextField max = new TextField();
        TextField metodo = new TextField();
        TextField descripcion = new TextField();
        TextField horas = new TextField();
        nombre.setPromptText("Ej: Clorofila-a");
        unidad.setPromptText("Ej: µg/L");
        metodo.setPromptText("Método y fuente del rango");
        if (actual != null) {
            nombre.setText(actual.nombre());
            unidad.setText(actual.unidadMedida());
            min.setText(numero(actual.valorMinNormal()));
            max.setText(numero(actual.valorMaxNormal()));
            metodo.setText(actual.metodoReferencia());
            descripcion.setText(actual.descripcion());
            horas.setText(numero(actual.tiempoEstimadoHrs()));
        }
        Label error = Formularios.lblError();
        GridPane form = Formularios.grilla("Parámetro *", nombre, "Unidad *", unidad, "Mínimo normal *", min,
                "Máximo normal *", max, "Método / referencia", metodo, "Descripción", descripcion,
                "Tiempo estimado (h)", horas);
        form.add(error, 0, 7, 2, 1);

        ButtonType guardar = Formularios.boton(actual == null ? "Agregar" : "Guardar cambios");
        Dialog<ButtonType> d = Formularios.dialogo(tabla.getScene().getWindow(), tabla.getScene().getStylesheets(),
                actual == null ? "Nuevo parámetro" : "Editar parámetro",
                "Define el rango normal contra el que se validarán los resultados", form, guardar);
        d.getDialogPane().lookupButton(guardar).addEventFilter(ActionEvent.ACTION, ev -> {
            BigDecimal vMin = decimal(min.getText());
            BigDecimal vMax = decimal(max.getText());
            if (Formularios.vacio(nombre) || Formularios.vacio(unidad)) {
                error.setText("Parámetro y unidad son obligatorios.");
                ev.consume();
            } else if (vMin == null || vMax == null) {
                error.setText("Mínimo y máximo deben ser números (ej: 6.5).");
                ev.consume();
            } else if (vMin.compareTo(vMax) > 0) {
                error.setText("El mínimo no puede ser mayor que el máximo.");
                ev.consume();
            } else if (!Formularios.vacio(horas) && decimal(horas.getText()) == null) {
                error.setText("El tiempo estimado debe ser un número.");
                ev.consume();
            }
        });

        Optional<ButtonType> r = d.showAndWait();
        if (r.isEmpty() || r.get() != guardar) {
            return Optional.empty();
        }
        return Optional.of(new AnalisisCatalogoRequest(Formularios.texto(nombre), Formularios.texto(descripcion),
                Formularios.texto(unidad), Formularios.texto(metodo), decimal(horas.getText()),
                decimal(min.getText()), decimal(max.getText())));
    }

    // ---------- carga y helpers ----------

    private void cargar() {
        Thread hilo = new Thread(() -> {
            try {
                List<AnalisisCatalogo> lista = apiClient.listarAnalisisCatalogo();
                Platform.runLater(() -> todos.setAll(lista));
            } catch (Exception e) {
                Platform.runLater(() -> mostrarError("No se pudo cargar el catálogo: " + e.getMessage()));
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

    private void actualizarBotones(AnalisisCatalogo x) {
        btnEditar.setDisable(x == null);
        btnEstado.setDisable(x == null);
        btnEstado.setText(x != null && !Boolean.TRUE.equals(x.activo()) ? "Activar" : "Desactivar");
    }

    // Acepta "6.5" y "6,5"
    private BigDecimal decimal(String t) {
        if (t == null || t.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(t.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String numero(BigDecimal n) {
        return n == null ? "" : n.stripTrailingZeros().toPlainString();
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
