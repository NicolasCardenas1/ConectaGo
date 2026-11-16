package com.duoc.lims.limsclient.controller;

import com.duoc.lims.limsclient.Navigator;
import com.duoc.lims.limsclient.model.Equipo;
import com.duoc.lims.limsclient.model.EquipoRequest;
import com.duoc.lims.limsclient.model.Ubicacion;
import com.duoc.lims.limsclient.model.UbicacionRequest;
import com.duoc.lims.limsclient.service.ApiClient;
import com.duoc.lims.limsclient.ui.Badges;
import com.duoc.lims.limsclient.ui.Formularios;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/** v5 — Equipos con su calibración y ubicaciones de almacenamiento. Solo el Administrador edita. */
public class EquiposController {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final List<String> TIPOS_UBICACION = List.of(
            "Ultracongelador", "Congelador", "Refrigerador", "Cámara", "Estante", "Otro");

    @FXML private TableView<Equipo> tablaEquipos;
    @FXML private TableColumn<Equipo, String> colEqCodigo;
    @FXML private TableColumn<Equipo, String> colEqNombre;
    @FXML private TableColumn<Equipo, String> colEqMarca;
    @FXML private TableColumn<Equipo, String> colEqSerie;
    @FXML private TableColumn<Equipo, String> colEqUltima;
    @FXML private TableColumn<Equipo, String> colEqProxima;
    @FXML private TableColumn<Equipo, String> colEqEstado;
    @FXML private TableColumn<Equipo, String> colEqActivo;
    @FXML private TableView<Ubicacion> tablaUbicaciones;
    @FXML private TableColumn<Ubicacion, String> colUbNombre;
    @FXML private TableColumn<Ubicacion, String> colUbTipo;
    @FXML private TableColumn<Ubicacion, String> colUbTemp;
    @FXML private TableColumn<Ubicacion, String> colUbDesc;
    @FXML private TableColumn<Ubicacion, String> colUbActivo;
    @FXML private Button btnNuevoEquipo;
    @FXML private Button btnEditarEquipo;
    @FXML private Button btnEstadoEquipo;
    @FXML private Button btnNuevaUbicacion;
    @FXML private Button btnEditarUbicacion;
    @FXML private Button btnEstadoUbicacion;
    @FXML private Label lblAyudaEquipos;
    @FXML private Label lblMensaje;

    private final ApiClient apiClient = new ApiClient();
    private final boolean esAdmin = Navigator.esAdministrador();

    @FXML
    public void initialize() {
        colEqCodigo.setCellValueFactory(d -> texto(d.getValue().codigo()));
        colEqNombre.setCellValueFactory(d -> texto(d.getValue().nombre()));
        colEqMarca.setCellValueFactory(d -> texto(unir(d.getValue().marca(), d.getValue().modelo())));
        colEqSerie.setCellValueFactory(d -> texto(d.getValue().numeroSerie()));
        colEqUltima.setCellValueFactory(d -> texto(fecha(d.getValue().fechaUltimaCalibracion())));
        colEqProxima.setCellValueFactory(d -> texto(textoProxima(d.getValue())));
        colEqEstado.setCellValueFactory(d -> texto(d.getValue().estadoCalibracion()));
        colEqEstado.setCellFactory(c -> Badges.celda());
        colEqActivo.setCellValueFactory(d -> texto(d.getValue().activo() ? "Activo" : "Inactivo"));
        colEqActivo.setCellFactory(c -> Badges.celda());

        colUbNombre.setCellValueFactory(d -> texto(d.getValue().nombre()));
        colUbTipo.setCellValueFactory(d -> texto(d.getValue().tipo()));
        colUbTemp.setCellValueFactory(d -> texto(d.getValue().temperatura()));
        colUbDesc.setCellValueFactory(d -> texto(d.getValue().descripcion()));
        colUbActivo.setCellValueFactory(d -> texto(d.getValue().activo() ? "Activo" : "Inactivo"));
        colUbActivo.setCellFactory(c -> Badges.celda());

        // RF06: solo el Administrador mantiene estos catálogos
        for (Button b : List.of(btnNuevoEquipo, btnEditarEquipo, btnEstadoEquipo,
                btnNuevaUbicacion, btnEditarUbicacion, btnEstadoUbicacion)) {
            b.setVisible(esAdmin);
            b.setManaged(esAdmin);
        }
        lblAyudaEquipos.setText(esAdmin
                ? "Registra cada calibración actualizando sus fechas"
                : "Solo el Administrador puede registrar equipos y calibraciones");
        tablaEquipos.getSelectionModel().selectedItemProperty().addListener((o, a, e) ->
                btnEstadoEquipo.setText(e != null && !e.activo() ? "Reactivar" : "Dar de baja"));
        tablaUbicaciones.getSelectionModel().selectedItemProperty().addListener((o, a, u) ->
                btnEstadoUbicacion.setText(u != null && !u.activo() ? "Activar" : "Desactivar"));
        cargar();
    }

    // ---------- equipos ----------

    @FXML
    private void onNuevoEquipo() {
        formularioEquipo(null).ifPresent(datos ->
                ejecutar(() -> apiClient.crearEquipo(datos), "Equipo " + datos.codigo() + " registrado."));
    }

    @FXML
    private void onEditarEquipo() {
        Equipo sel = tablaEquipos.getSelectionModel().getSelectedItem();
        if (sel == null) {
            mostrarError("Selecciona un equipo.");
            return;
        }
        formularioEquipo(sel).ifPresent(datos ->
                ejecutar(() -> apiClient.actualizarEquipo(sel.id(), datos), "Equipo " + datos.codigo() + " actualizado."));
    }

    @FXML
    private void onEstadoEquipo() {
        Equipo sel = tablaEquipos.getSelectionModel().getSelectedItem();
        if (sel == null) {
            mostrarError("Selecciona un equipo.");
            return;
        }
        ejecutar(() -> apiClient.cambiarEstadoEquipo(sel.id(), !sel.activo()),
                sel.codigo() + (sel.activo() ? " dado de baja." : " reactivado."));
    }

    private Optional<EquipoRequest> formularioEquipo(Equipo actual) {
        TextField codigo = new TextField();
        TextField nombre = new TextField();
        TextField marca = new TextField();
        TextField modelo = new TextField();
        TextField serie = new TextField();
        DatePicker ultima = new DatePicker();
        DatePicker proxima = new DatePicker();
        codigo.setPromptText("Ej: EQ-006 (etiqueta física)");
        if (actual != null) {
            codigo.setText(actual.codigo());
            nombre.setText(actual.nombre());
            marca.setText(actual.marca());
            modelo.setText(actual.modelo());
            serie.setText(actual.numeroSerie());
            ultima.setValue(actual.fechaUltimaCalibracion());
            proxima.setValue(actual.fechaProximaCalibracion());
        }
        Label error = Formularios.lblError();
        GridPane form = Formularios.grilla("Código *", codigo, "Nombre *", nombre, "Marca", marca, "Modelo", modelo,
                "N° de serie", serie, "Última calibración", ultima, "Próxima calibración", proxima);
        form.add(error, 0, 7, 2, 1);
        ButtonType guardar = Formularios.boton(actual == null ? "Registrar" : "Guardar cambios");
        Dialog<ButtonType> d = Formularios.dialogo(tablaEquipos.getScene().getWindow(),
                tablaEquipos.getScene().getStylesheets(), actual == null ? "Nuevo equipo" : "Editar equipo",
                actual == null ? "Equipo de medición del laboratorio" : actual.codigo() + " · " + actual.nombre(),
                form, guardar);
        d.getDialogPane().lookupButton(guardar).addEventFilter(ActionEvent.ACTION, ev -> {
            if (Formularios.vacio(codigo) || Formularios.vacio(nombre)) {
                error.setText("Código y nombre son obligatorios.");
                ev.consume();
            } else if (ultima.getValue() != null && proxima.getValue() != null
                    && proxima.getValue().isBefore(ultima.getValue())) {
                error.setText("La próxima calibración no puede ser anterior a la última.");
                ev.consume();
            }
        });
        Optional<ButtonType> r = d.showAndWait();
        if (r.isEmpty() || r.get() != guardar) {
            return Optional.empty();
        }
        return Optional.of(new EquipoRequest(Formularios.texto(codigo), Formularios.texto(nombre),
                Formularios.texto(marca), Formularios.texto(modelo), Formularios.texto(serie),
                ultima.getValue(), proxima.getValue()));
    }

    // ---------- ubicaciones ----------

    @FXML
    private void onNuevaUbicacion() {
        formularioUbicacion(null).ifPresent(datos ->
                ejecutar(() -> apiClient.crearUbicacion(datos), "Ubicación " + datos.nombre() + " registrada."));
    }

    @FXML
    private void onEditarUbicacion() {
        Ubicacion sel = tablaUbicaciones.getSelectionModel().getSelectedItem();
        if (sel == null) {
            mostrarError("Selecciona una ubicación.");
            return;
        }
        formularioUbicacion(sel).ifPresent(datos ->
                ejecutar(() -> apiClient.actualizarUbicacion(sel.id(), datos), "Ubicación actualizada."));
    }

    @FXML
    private void onEstadoUbicacion() {
        Ubicacion sel = tablaUbicaciones.getSelectionModel().getSelectedItem();
        if (sel == null) {
            mostrarError("Selecciona una ubicación.");
            return;
        }
        ejecutar(() -> apiClient.cambiarEstadoUbicacion(sel.id(), !sel.activo()),
                sel.nombre() + (sel.activo() ? " desactivada." : " activada."));
    }

    private Optional<UbicacionRequest> formularioUbicacion(Ubicacion actual) {
        TextField nombre = new TextField();
        ComboBox<String> tipo = new ComboBox<>(FXCollections.observableArrayList(TIPOS_UBICACION));
        tipo.setMaxWidth(Double.MAX_VALUE);
        TextField temperatura = new TextField();
        TextField descripcion = new TextField();
        nombre.setPromptText("Ej: Congelador -20 °C · Rack C");
        temperatura.setPromptText("Ej: -20 °C");
        if (actual != null) {
            nombre.setText(actual.nombre());
            tipo.setValue(actual.tipo());
            temperatura.setText(actual.temperatura());
            descripcion.setText(actual.descripcion());
        }
        Label error = Formularios.lblError();
        GridPane form = Formularios.grilla("Nombre *", nombre, "Tipo *", tipo, "Temperatura", temperatura,
                "Descripción", descripcion);
        form.add(error, 0, 4, 2, 1);
        ButtonType guardar = Formularios.boton(actual == null ? "Registrar" : "Guardar cambios");
        Dialog<ButtonType> d = Formularios.dialogo(tablaUbicaciones.getScene().getWindow(),
                tablaUbicaciones.getScene().getStylesheets(), actual == null ? "Nueva ubicación" : "Editar ubicación",
                "Lugar físico donde se guardan las muestras", form, guardar);
        d.getDialogPane().lookupButton(guardar).addEventFilter(ActionEvent.ACTION, ev -> {
            if (Formularios.vacio(nombre) || tipo.getValue() == null) {
                error.setText("Nombre y tipo son obligatorios.");
                ev.consume();
            }
        });
        Optional<ButtonType> r = d.showAndWait();
        if (r.isEmpty() || r.get() != guardar) {
            return Optional.empty();
        }
        return Optional.of(new UbicacionRequest(Formularios.texto(nombre), tipo.getValue(),
                Formularios.texto(temperatura), Formularios.texto(descripcion)));
    }

    // ---------- carga y helpers ----------

    private void cargar() {
        Thread hilo = new Thread(() -> {
            try {
                List<Equipo> equipos = apiClient.listarEquipos(false);
                List<Ubicacion> ubicaciones = apiClient.listarUbicaciones(false);
                Platform.runLater(() -> {
                    tablaEquipos.setItems(FXCollections.observableArrayList(equipos));
                    tablaUbicaciones.setItems(FXCollections.observableArrayList(ubicaciones));
                });
            } catch (Exception e) {
                Platform.runLater(() -> mostrarError("No se pudo cargar: " + e.getMessage()));
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

    private String textoProxima(Equipo e) {
        if (e.fechaProximaCalibracion() == null) {
            return null;
        }
        long dias = e.diasParaVencer() == null ? 0 : e.diasParaVencer();
        String cuando = dias < 0 ? "venció hace " + (-dias) + " días" : "en " + dias + " días";
        return fecha(e.fechaProximaCalibracion()) + " (" + cuando + ")";
    }

    private String unir(String a, String b) {
        if (a == null) {
            return b;
        }
        return b == null ? a : a + " " + b;
    }

    private String fecha(LocalDate f) {
        return f == null ? null : f.format(FECHA);
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
