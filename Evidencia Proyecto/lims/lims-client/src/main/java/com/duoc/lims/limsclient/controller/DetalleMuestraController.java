package com.duoc.lims.limsclient.controller;

import com.duoc.lims.limsclient.Navigator;
import com.duoc.lims.limsclient.model.AnalisisCatalogo;
import com.duoc.lims.limsclient.model.Muestra;
import com.duoc.lims.limsclient.model.MuestraDetalle;
import com.duoc.lims.limsclient.model.MuestraDetalle.AnalisisItem;
import com.duoc.lims.limsclient.model.MuestraDetalle.HistorialItem;
import com.duoc.lims.limsclient.model.MuestraDetalle.CustodiaItem;
import com.duoc.lims.limsclient.model.CustodiaRequest;
import com.duoc.lims.limsclient.ui.Formularios;
import javafx.event.ActionEvent;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import com.duoc.lims.limsclient.service.ApiClient;
import com.duoc.lims.limsclient.ui.Badges;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

public class DetalleMuestraController {

    @FXML private Label lblTitulo;
    @FXML private Label lblTipo;
    @FXML private Label lblEstadoMuestra;
    @FXML private Label lblCliente;
    @FXML private Label lblPrioridad;
    @FXML private Label lblFecha;
    @FXML private Label lblRegistradoPor;
    @FXML private Label lblObservaciones;
    @FXML private Label lblInvestigador;
    @FXML private Label lblFechaToma;
    @FXML private Label lblUbicacion;
    @FXML private Label lblCondicion;
    @FXML private Label lblVencimiento;

    @FXML private TableView<CustodiaItem> tablaCustodia;
    @FXML private TableColumn<CustodiaItem, String> colCusFecha;
    @FXML private TableColumn<CustodiaItem, String> colCusEvento;
    @FXML private TableColumn<CustodiaItem, String> colCusEntrega;
    @FXML private TableColumn<CustodiaItem, String> colCusRecibe;
    @FXML private TableColumn<CustodiaItem, String> colCusUbicacion;
    @FXML private TableColumn<CustodiaItem, String> colCusCondicion;

    /** Eventos de custodia (deben coincidir con TipoEventoCustodia del backend). */
    private static final List<String> EVENTOS_CUSTODIA = List.of(
            "Recepción en laboratorio", "Traslado", "Retiro para análisis",
            "Devolución a almacenamiento", "Devolución al investigador");
    private String investigadorActual;
    @FXML private Label lblMensaje;

    @FXML private ComboBox<AnalisisCatalogo> cbAnalisis;
    @FXML private Button btnAgregar;
    @FXML private Button btnReporte;

    @FXML private TableView<AnalisisItem> tablaAnalisis;
    @FXML private TableColumn<AnalisisItem, String> colAnalisis;
    @FXML private TableColumn<AnalisisItem, String> colRango;
    @FXML private TableColumn<AnalisisItem, String> colEstadoAnalisis;
    @FXML private TableColumn<AnalisisItem, String> colResultado;
    @FXML private TableColumn<AnalisisItem, String> colAprobacion;

    @FXML private TableView<HistorialItem> tablaHistorial;
    @FXML private TableColumn<HistorialItem, String> colHistFecha;
    @FXML private TableColumn<HistorialItem, String> colHistAnterior;
    @FXML private TableColumn<HistorialItem, String> colHistNuevo;
    @FXML private TableColumn<HistorialItem, String> colHistUsuario;
    @FXML private TableColumn<HistorialItem, String> colHistComentario;

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private final ApiClient apiClient = new ApiClient();
    private Integer idMuestra;
    private String codigoMuestra;

    @FXML
    public void initialize() {
        colAnalisis.setCellValueFactory(d -> texto(d.getValue().nombreAnalisis()));
        colRango.setCellValueFactory(d -> {
            AnalisisItem a = d.getValue();
            return texto(numero(a.valorMinNormal()) + " – " + numero(a.valorMaxNormal()) + " " + a.unidadMedida());
        });
        colEstadoAnalisis.setCellValueFactory(d -> texto(d.getValue().estado()));
        colEstadoAnalisis.setCellFactory(c -> Badges.celda());
        colResultado.setCellValueFactory(d -> texto(textoResultado(d.getValue())));
        colAprobacion.setCellValueFactory(d -> texto(textoAprobacion(d.getValue())));
        colAprobacion.setCellFactory(c -> Badges.celda());

        // Cadena de custodia
        colCusFecha.setCellValueFactory(d -> texto(d.getValue().fechaHora() != null
                ? d.getValue().fechaHora().format(FORMATO_FECHA) : null));
        colCusEvento.setCellValueFactory(d -> texto(d.getValue().tipoEvento()));
        colCusEntrega.setCellValueFactory(d -> texto(d.getValue().entregadoPor()));
        colCusRecibe.setCellValueFactory(d -> texto(d.getValue().recibidoPor() + "  ✔"));
        colCusUbicacion.setCellValueFactory(d -> texto(d.getValue().ubicacion()));
        colCusCondicion.setCellValueFactory(d -> texto(d.getValue().condicion()));

        // Historial de estados
        colHistFecha.setCellValueFactory(d -> texto(d.getValue().fecha() != null
                ? d.getValue().fecha().format(FORMATO_FECHA) : null));
        colHistAnterior.setCellValueFactory(d -> texto(d.getValue().estadoAnterior()));
        colHistAnterior.setCellFactory(c -> Badges.celda());
        colHistNuevo.setCellValueFactory(d -> texto(d.getValue().estadoNuevo()));
        colHistNuevo.setCellFactory(c -> Badges.celda());
        colHistUsuario.setCellValueFactory(d -> texto(d.getValue().usuario()));
        colHistComentario.setCellValueFactory(d -> texto(d.getValue().comentario()));
    }

    /** Lo llama Navigator justo después de abrir la pantalla. */
    public void cargarMuestra(int idMuestra) {
        this.idMuestra = idMuestra;
        cargarDetalle();
        cargarCatalogo();
    }

    @FXML
    private void onVolver() {
        Navigator.irAListado();
    }

    @FXML
    private void onAgregarAnalisis() {
        AnalisisCatalogo seleccionado = cbAnalisis.getValue();
        if (seleccionado == null) {
            mostrarError("Selecciona un análisis del catálogo.");
            return;
        }

        btnAgregar.setDisable(true);
        Thread hilo = new Thread(() -> {
            try {
                apiClient.asignarAnalisis(idMuestra, seleccionado.id());
                Platform.runLater(() -> {
                    mostrarOk("Análisis agregado: " + seleccionado.nombre());
                    cbAnalisis.getSelectionModel().clearSelection();
                    btnAgregar.setDisable(false);
                    cargarDetalle();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    mostrarError(e.getMessage());
                    btnAgregar.setDisable(false);
                });
            }
        });
        hilo.setDaemon(true);
        hilo.start();
    }

    @FXML
    private void onIngresarResultado() {
        AnalisisItem seleccionado = tablaAnalisis.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            mostrarError("Selecciona un análisis de la tabla.");
            return;
        }
        if (seleccionado.resultado() != null) {
            mostrarError("Ese análisis ya tiene un resultado ingresado.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(Navigator.class.getResource("ingreso-resultado.fxml"));
            Parent root = loader.load();
            IngresoResultadoController dialogo = loader.getController();
            dialogo.setAnalisis(seleccionado);

            Stage ventana = new Stage();
            ventana.setTitle("LIMS - Ingresar resultado");
            ventana.initModality(Modality.APPLICATION_MODAL);           // bloquea la ventana de atrás
            ventana.initOwner(tablaAnalisis.getScene().getWindow());
            Scene escena = new Scene(root);
            escena.getStylesheets().addAll(tablaAnalisis.getScene().getStylesheets());   // mismo tema
            ventana.setScene(escena);
            ventana.setResizable(false);
            ventana.showAndWait();                                       // espera a que se cierre

            if (dialogo.isGuardado()) {
                mostrarOk("Resultado ingresado para " + seleccionado.nombreAnalisis());
                cargarDetalle();
            }
        } catch (IOException e) {
            mostrarError("No se pudo abrir el diálogo: " + e.getMessage());
        }
    }
    // ---------- Cadena de custodia ----------

    @FXML
    private void onFirmarCustodia() {
        javafx.scene.control.ComboBox<String> tipo =
                new javafx.scene.control.ComboBox<>(FXCollections.observableArrayList(EVENTOS_CUSTODIA));
        tipo.setValue("Traslado");
        tipo.setMaxWidth(Double.MAX_VALUE);
        TextField entregadoPor = new TextField();
        TextField ubicacion = new TextField();
        TextField condicion = new TextField();
        TextField observaciones = new TextField();
        entregadoPor.setPromptText("Quién entrega físicamente la muestra");
        ubicacion.setPromptText("Dónde queda la muestra (ej: Congelador -80 °C, rack A)");
        if (investigadorActual != null) {
            entregadoPor.setText(investigadorActual);
        }
        CheckBox firma = new CheckBox("Confirmo que recibí la muestra (firma con fecha y hora)");
        Label error = Formularios.lblError();
        GridPane form = Formularios.grilla("Evento *", tipo, "Entregado por *", entregadoPor,
                "Ubicación", ubicacion, "Condición", condicion, "Observaciones", observaciones);
        form.add(firma, 0, 5, 2, 1);
        form.add(error, 0, 6, 2, 1);

        javafx.scene.control.ButtonType firmar = Formularios.boton("Firmar");
        Dialog<javafx.scene.control.ButtonType> d = Formularios.dialogo(btnReporte.getScene().getWindow(),
                btnReporte.getScene().getStylesheets(), "Cadena de custodia",
                "Muestra " + codigoMuestra + " — recibe: " + Navigator.getUsuarioActual().getNombre() + " "
                        + Navigator.getUsuarioActual().getApellido(), form, firmar);
        d.getDialogPane().lookupButton(firmar).addEventFilter(ActionEvent.ACTION, ev -> {
            if (Formularios.vacio(entregadoPor)) {
                error.setText("Indica quién entrega la muestra.");
                ev.consume();
            } else if (!firma.isSelected()) {
                error.setText("Debes confirmar la recepción para firmar.");
                ev.consume();
            }
        });
        Optional<javafx.scene.control.ButtonType> r = d.showAndWait();
        if (r.isEmpty() || r.get() != firmar) {
            return;
        }
        CustodiaRequest datos = new CustodiaRequest(tipo.getValue(), Formularios.texto(entregadoPor),
                Formularios.texto(ubicacion), Formularios.texto(condicion), true, Formularios.texto(observaciones));
        Thread hilo = new Thread(() -> {
            try {
                apiClient.registrarCustodia(idMuestra, datos);
                Platform.runLater(() -> {
                    mostrarOk("Evento de custodia firmado: " + datos.tipoEvento());
                    cargarDetalle();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> mostrarError(ex.getMessage()));
            }
        });
        hilo.setDaemon(true);
        hilo.start();
    }

    private String vacioGuion(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    // ---------- RF05: informe PDF ----------

    @FXML
    private void onGenerarReporte() {
        btnReporte.setDisable(true);
        mostrarOk("Generando informe...");

        Thread hilo = new Thread(() -> {
            try {
                byte[] pdf = apiClient.generarReporte(idMuestra);
                // Se guarda una copia temporal para la vista previa.
                Path temporal = Files.createTempDirectory("lims-informe").resolve("Informe_" + codigoMuestra + ".pdf");
                Files.write(temporal, pdf);
                Platform.runLater(() -> {
                    mostrarOk("Informe generado. La muestra quedó en estado Reportada.");
                    preguntarQueHacerConInforme(temporal);
                    cargarDetalle();   // refresca estado e historial
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    mostrarError(e.getMessage());
                    btnReporte.setDisable(false);
                });
            }
        });
        hilo.setDaemon(true);
        hilo.start();
    }

    private void preguntarQueHacerConInforme(Path pdf) {
        ButtonType verPrevia = new ButtonType("Ver vista previa", ButtonBar.ButtonData.OK_DONE);
        ButtonType guardar = new ButtonType("Guardar como...", ButtonBar.ButtonData.OTHER);
        ButtonType cerrar = new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert dialogo = new Alert(Alert.AlertType.INFORMATION, "", verPrevia, guardar, cerrar);
        dialogo.setTitle("LIMS - Informe de resultados");
        dialogo.setHeaderText("Informe de la muestra " + codigoMuestra + " generado");
        dialogo.setContentText("¿Qué deseas hacer con el informe PDF?");
        dialogo.initOwner(btnReporte.getScene().getWindow());
        dialogo.getDialogPane().getStylesheets().addAll(btnReporte.getScene().getStylesheets());

        Optional<ButtonType> eleccion = dialogo.showAndWait();
        if (eleccion.isEmpty() || eleccion.get() == cerrar) {
            return;
        }
        if (eleccion.get() == verPrevia) {
            Navigator.abrirDocumento(pdf);
        } else if (eleccion.get() == guardar) {
            guardarInforme(pdf);
        }
    }

    private void guardarInforme(Path pdf) {
        FileChooser selector = new FileChooser();
        selector.setTitle("Guardar informe PDF");
        selector.setInitialFileName(pdf.getFileName().toString());
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documento PDF", "*.pdf"));
        File destino = selector.showSaveDialog(btnReporte.getScene().getWindow());
        if (destino == null) {
            return;
        }
        try {
            Files.copy(pdf, destino.toPath(), StandardCopyOption.REPLACE_EXISTING);
            mostrarOk("Informe guardado en " + destino.getAbsolutePath());
        } catch (IOException e) {
            mostrarError("No se pudo guardar el informe: " + e.getMessage());
        }
    }

    // ---------- carga de datos (en hilo aparte, igual que el listado) ----------

    private void cargarDetalle() {
        Thread hilo = new Thread(() -> {
            try {
                MuestraDetalle detalle = apiClient.obtenerDetalleMuestra(idMuestra);
                Platform.runLater(() -> mostrarDetalle(detalle));
            } catch (Exception e) {
                Platform.runLater(() -> mostrarError("No se pudo cargar la muestra: " + e.getMessage()));
            }
        });
        hilo.setDaemon(true);
        hilo.start();
    }

    private void cargarCatalogo() {
        Thread hilo = new Thread(() -> {
            try {
                List<AnalisisCatalogo> activos = apiClient.listarAnalisisCatalogo().stream()
                        .filter(a -> Boolean.TRUE.equals(a.activo()))
                        .toList();
                Platform.runLater(() -> cbAnalisis.setItems(FXCollections.observableArrayList(activos)));
            } catch (Exception e) {
                Platform.runLater(() -> mostrarError("No se pudo cargar el catálogo de análisis: " + e.getMessage()));
            }
        });
        hilo.setDaemon(true);
        hilo.start();
    }

    private void mostrarDetalle(MuestraDetalle detalle) {
        Muestra m = detalle.muestra();
        lblTitulo.setText("Muestra " + m.getCodigoUnico());
        codigoMuestra = m.getCodigoUnico();

        // RF05: el informe solo se puede emitir para muestras Aprobadas (o re-emitir si ya está Reportada).
        boolean puedeInformar = Navigator.puedeAprobar()     // RF06: solo Supervisor/Administrador
                && ("Aprobada".equals(m.getEstado()) || "Reportada".equals(m.getEstado()));
        btnReporte.setDisable(!puedeInformar);
        btnReporte.setText("Reportada".equals(m.getEstado()) ? "Re-emitir informe PDF" : "Generar informe PDF");
        lblTipo.setText(m.getTipoMuestra());
        lblEstadoMuestra.setText(Badges.textoVisible(m.getEstado()));
        lblEstadoMuestra.getStyleClass().setAll("label", "badge", Badges.claseColor(m.getEstado()));
        lblCliente.setText(m.getProcedencia());
        investigadorActual = m.getInvestigador();
        lblInvestigador.setText(m.getInvestigador() == null ? "— (muestra anterior a v4)"
                : m.getInvestigador() + (m.getInstitucion() == null ? "" : " · " + m.getInstitucion()));
        lblFechaToma.setText(m.getFechaToma() != null ? m.getFechaToma().format(FORMATO_FECHA) : "—");
        lblUbicacion.setText(vacioGuion(m.getUbicacionAlmacenamiento()));
        lblCondicion.setText(vacioGuion(m.getCondicionRecepcion()));
        lblVencimiento.setText(m.getFechaVencimiento() != null
                ? m.getFechaVencimiento().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")) : "—");
        lblPrioridad.setText(m.getPrioridad());
        lblFecha.setText(m.getFechaRecepcion() != null ? m.getFechaRecepcion().format(FORMATO_FECHA) : "—");
        lblRegistradoPor.setText(m.getUsuarioRegistro());
        lblObservaciones.setText(m.getObservaciones() == null || m.getObservaciones().isBlank()
                ? "—" : m.getObservaciones());

        tablaAnalisis.setItems(FXCollections.observableArrayList(detalle.analisis()));
        tablaCustodia.setItems(FXCollections.observableArrayList(
                detalle.custodia() == null ? List.of() : detalle.custodia()));
        tablaHistorial.setItems(FXCollections.observableArrayList(
                detalle.historial() == null ? List.of() : detalle.historial()));
    }

    // ---------- helpers de formato ----------

    private SimpleStringProperty texto(String s) {
        return new SimpleStringProperty(s == null ? "—" : s);
    }

    // 4.5000 -> "4.5"
    private String numero(BigDecimal n) {
        return n == null ? "" : n.stripTrailingZeros().toPlainString();
    }

    private String textoResultado(AnalisisItem a) {
        if (a.resultado() == null) {
            return "—";
        }
        String txt = numero(a.resultado().valorResultado()) + " " + a.unidadMedida();
        return Boolean.FALSE.equals(a.resultado().dentroRango()) ? txt + "  ⚠ fuera de rango" : txt;
    }

    private String textoAprobacion(AnalisisItem a) {
        if (a.resultado() == null) {
            return "—";
        }
        return a.estadoAprobacion() == null ? "Pendiente" : a.estadoAprobacion();
    }

    private void mostrarError(String mensaje) {
        lblMensaje.setStyle("-fx-text-fill: #dc2626;");
        lblMensaje.setText(mensaje);
    }

    private void mostrarOk(String mensaje) {
        lblMensaje.setStyle("-fx-text-fill: #16a34a;");
        lblMensaje.setText(mensaje);
    }
}