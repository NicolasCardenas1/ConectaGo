package com.duoc.lims.limsclient.ui;

import com.duoc.lims.limsclient.model.Muestra;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PageLayout;
import javafx.print.PrinterJob;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;
import javafx.stage.Window;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Etiqueta adhesiva de 50 × 25 mm para el envase de una muestra.
 *
 * A la vista muestra solo lo necesario para reconocer la muestra en el congelador o estante
 * (código, tipo, sitio, fecha de toma, almacenamiento y prioridad). El código QR guarda el detalle
 * completo en texto plano, legible con cualquier app de cámara del celular sin abrir el LIMS.
 *
 * La etiqueta se dibuja en puntos de impresora (1/72 de pulgada), que es la unidad que usa
 * JavaFX al imprimir: así sale a su tamaño real. En pantalla se muestra ampliada.
 */
public final class EtiquetaMuestra {

    public static final double ANCHO_MM = 50;
    public static final double ALTO_MM = 25;

    private static final double PUNTOS_POR_MM = 72 / 25.4;
    private static final double ANCHO = ANCHO_MM * PUNTOS_POR_MM;   // ≈ 141,7 pt
    private static final double ALTO = ALTO_MM * PUNTOS_POR_MM;     // ≈ 70,9 pt
    private static final double MARGEN = 1.2 * PUNTOS_POR_MM;       // 1,2 mm de borde
    private static final double SEPARACION = 1.0 * PUNTOS_POR_MM;   // entre el QR y el texto
    private static final double LADO_QR = ALTO - 2 * MARGEN;

    /** Módulos en blanco alrededor del QR (zona de silencio) para que el lector lo detecte. */
    private static final int ZONA_SILENCIO = 2;

    /** Escala de la vista previa en pantalla (la etiqueta real es muy pequeña). */
    private static final double ESCALA_VISTA_PREVIA = 3.2;

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private EtiquetaMuestra() {
    }

    // ---------- contenido ----------

    /** Texto que guarda el QR: el detalle completo de la muestra, una línea por dato. */
    public static String contenidoQr(Muestra m) {
        List<String> lineas = new ArrayList<>();
        lineas.add("LIMS " + m.getCodigoUnico());
        agregar(lineas, "Tipo", m.getTipoMuestra());
        agregar(lineas, "Sitio", sitioCompleto(m));
        if (m.getSitioCodigo() != null) {
            agregar(lineas, "Punto", m.getProcedencia());   // con sitio de catálogo, "procedencia" es el punto exacto
        }
        agregar(lineas, "Toma", m.getFechaToma() == null ? null : m.getFechaToma().format(FECHA_HORA));
        agregar(lineas, "Investigador", m.getInvestigador());
        agregar(lineas, "Almacenar", m.getUbicacionAlmacenamiento());
        agregar(lineas, "Conservar hasta", m.getFechaVencimiento() == null ? null : m.getFechaVencimiento().format(FECHA));
        agregar(lineas, "Prioridad", m.getPrioridad());
        return String.join("\n", lineas);
    }

    /** Construye la etiqueta a tamaño real (en puntos de impresora). */
    public static Region crear(Muestra m) {
        Node qr = codigoQr(contenidoQr(m), LADO_QR);

        Label prioridad = texto("Prioridad " + minuscula(m.getPrioridad()), 4.6, true);
        prioridad.setStyle(prioridad.getStyle()
                + "-fx-border-color: black; -fx-border-width: 0.5; -fx-border-radius: 1.5; -fx-padding: 0 1.6 0 1.6;");
        HBox filaPrioridad = new HBox(prioridad);
        filaPrioridad.setAlignment(Pos.TOP_RIGHT);

        Label codigo = texto(m.getCodigoUnico(), 8.6, true);
        codigo.setStyle(codigo.getStyle() + "-fx-font-family: 'Monospaced';"
                + "-fx-border-color: transparent transparent black transparent; -fx-border-width: 0 0 0.6 0;"
                + "-fx-padding: 0 0 1 0;");

        String toma = m.getFechaToma() != null ? "Toma " + m.getFechaToma().format(FECHA)
                : m.getFechaRecepcion() != null ? "Recepción " + m.getFechaRecepcion().format(FECHA) : "Toma —";

        VBox datos = new VBox(1.4,
                filaPrioridad,
                codigo,
                texto(m.getTipoMuestra(), 5.6, true),
                texto(m.getSitioCodigo() != null ? m.getSitioCodigo() : vacioGuion(m.getProcedencia()), 5.0, false),
                texto(toma, 5.0, false),
                texto(abreviarUbicacion(m.getUbicacionAlmacenamiento()), 5.0, false));
        double anchoTexto = ANCHO - 2 * MARGEN - LADO_QR - SEPARACION;
        datos.setPrefWidth(anchoTexto);
        datos.setMaxWidth(anchoTexto);
        datos.setMinWidth(anchoTexto);
        // misma altura que el QR, con la prioridad pegada arriba a la derecha
        datos.setMinHeight(LADO_QR);
        datos.setPrefHeight(LADO_QR);
        datos.setAlignment(Pos.TOP_LEFT);
        datos.getChildren().forEach(n -> {
            if (n instanceof Label l) {
                l.setMaxWidth(anchoTexto);
            }
        });
        codigo.setMaxWidth(Double.MAX_VALUE);

        HBox contenido = new HBox(SEPARACION, qr, datos);
        contenido.setAlignment(Pos.CENTER_LEFT);
        contenido.setPadding(new Insets(MARGEN));

        StackPane etiqueta = new StackPane(contenido);
        etiqueta.setStyle("-fx-background-color: white;");
        etiqueta.setMinSize(ANCHO, ALTO);
        etiqueta.setPrefSize(ANCHO, ALTO);
        etiqueta.setMaxSize(ANCHO, ALTO);
        etiqueta.setClip(new Rectangle(ANCHO, ALTO));
        return etiqueta;
    }

    // ---------- vista previa e impresión ----------

    /**
     * Muestra la vista previa de la etiqueta y, si el usuario acepta, la imprime.
     * Se usa al registrar una muestra ("¿Imprimir etiqueta?") y desde el detalle (reimprimir).
     */
    public static void ofrecerImpresion(Muestra m, Window duenio, List<String> estilos,
                                        String titulo, String encabezado, String textoCancelar) {
        ButtonType imprimir = new ButtonType("Imprimir etiqueta", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelar = new ButtonType(textoCancelar, ButtonBar.ButtonData.CANCEL_CLOSE);

        Label ayuda = new Label("Etiqueta de " + (int) ANCHO_MM + " × " + (int) ALTO_MM
                + " mm. El código QR contiene el detalle completo de la muestra.");
        ayuda.setWrapText(true);
        ayuda.setMaxWidth(ANCHO * ESCALA_VISTA_PREVIA + 24);

        VBox contenido = new VBox(12, vistaPrevia(m), ayuda);
        contenido.setPadding(new Insets(8, 4, 0, 4));

        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("LIMS - " + titulo);
        d.setHeaderText(encabezado);
        d.getDialogPane().setContent(contenido);
        d.getDialogPane().getButtonTypes().addAll(imprimir, cancelar);
        if (duenio != null) {
            d.initOwner(duenio);
        }
        if (estilos != null) {
            d.getDialogPane().getStylesheets().addAll(estilos);
        }

        if (d.showAndWait().filter(imprimir::equals).isEmpty()) {
            return;
        }
        try {
            imprimir(m, duenio);
        } catch (Exception e) {
            Alert error = new Alert(Alert.AlertType.ERROR, e.getMessage(), ButtonType.OK);
            error.setTitle("LIMS - Imprimir etiqueta");
            error.setHeaderText("No se pudo imprimir la etiqueta");
            if (duenio != null) {
                error.initOwner(duenio);
            }
            error.showAndWait();
        }
    }

    /** La etiqueta ampliada, con un borde gris que simula el contorno del adhesivo. */
    public static Node vistaPrevia(Muestra m) {
        // la escala va en la etiqueta (no en el Group) para que el Group ocupe el tamaño ampliado
        Region etiqueta = crear(m);
        etiqueta.getTransforms().add(new Scale(ESCALA_VISTA_PREVIA, ESCALA_VISTA_PREVIA));
        Group ampliada = new Group(etiqueta);
        StackPane marco = new StackPane(ampliada);
        marco.setStyle("-fx-background-color: white; -fx-border-color: #b4b2a9; -fx-border-radius: 8;"
                + "-fx-background-radius: 8; -fx-border-width: 1;");
        marco.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane fondo = new StackPane(marco);
        fondo.setPadding(new Insets(14));
        fondo.setStyle("-fx-background-color: #f1efe8; -fx-background-radius: 10;");
        return fondo;
    }

    /**
     * Abre el diálogo de impresión del sistema e imprime la etiqueta a tamaño real.
     * Con una impresora de etiquetas (papel de 50 × 25 mm) ocupa la etiqueta completa; con una
     * impresora normal sale en la esquina de la hoja. Si el papel elegido es más chico o está
     * girado, la etiqueta se rota o se reduce para que quepa.
     *
     * @return true si se envió a la impresora; false si el usuario canceló.
     */
    public static boolean imprimir(Muestra m, Window duenio) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            throw new IllegalStateException("No hay impresoras instaladas en este equipo.");
        }
        job.getJobSettings().setJobName("Etiqueta " + m.getCodigoUnico());
        if (!job.showPrintDialog(duenio)) {
            job.cancelJob();
            return false;
        }

        PageLayout pagina = job.getJobSettings().getPageLayout();
        Node pieza = ajustarAPagina(crear(m), pagina.getPrintableWidth(), pagina.getPrintableHeight());

        boolean ok = job.printPage(pagina, pieza);
        if (!ok) {
            job.cancelJob();
            throw new IllegalStateException("La impresora rechazó el trabajo.");
        }
        return job.endJob();
    }

    /** Rota y/o reduce la etiqueta para que quepa en el área imprimible (nunca la agranda). */
    static Node ajustarAPagina(Region etiqueta, double anchoPagina, double altoPagina) {
        double escalaNormal = Math.min(anchoPagina / ANCHO, altoPagina / ALTO);
        double escalaRotada = Math.min(anchoPagina / ALTO, altoPagina / ANCHO);
        boolean rotar = escalaNormal < 1 && escalaRotada > escalaNormal;
        double escala = Math.min(1, rotar ? escalaRotada : escalaNormal);

        Group grupo = new Group(etiqueta);
        if (rotar) {
            // gira 90° y la deja dentro del área positiva de la página
            grupo.getTransforms().addAll(new Scale(escala, escala), new Translate(ALTO, 0), new Rotate(90));
        } else if (escala < 1) {
            grupo.getTransforms().add(new Scale(escala, escala));
        }
        // la etiqueta no está en ninguna ventana: se le da una escena propia para calcular estilos y tamaños
        Group raiz = new Group(grupo);
        new Scene(raiz);
        raiz.applyCss();
        raiz.layout();
        return raiz;
    }

    // ---------- QR ----------

    /** Dibuja el QR como un único trazado vectorial (nítido en impresoras de 203 o 300 dpi). */
    static Node codigoQr(String contenido, double lado) {
        BitMatrix matriz;
        try {
            matriz = new QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, 0, 0, Map.of(
                    EncodeHintType.CHARACTER_SET, "UTF-8",
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L,
                    EncodeHintType.MARGIN, 0));
        } catch (WriterException e) {
            throw new IllegalStateException("No se pudo generar el código QR", e);
        }

        int n = matriz.getWidth();
        double modulo = lado / (n + 2 * ZONA_SILENCIO);
        double origen = ZONA_SILENCIO * modulo;

        Path trazado = new Path();
        for (int y = 0; y < n; y++) {
            int x = 0;
            while (x < n) {
                if (!matriz.get(x, y)) {
                    x++;
                    continue;
                }
                int inicio = x;
                while (x < n && matriz.get(x, y)) {
                    x++;
                }
                double x0 = origen + inicio * modulo;
                double x1 = origen + x * modulo;
                double y0 = origen + y * modulo;
                // se solapa un poco con la fila siguiente para que no queden líneas blancas entre filas
                double y1 = origen + (y + 1) * modulo + (y < n - 1 ? modulo * 0.04 : 0);
                trazado.getElements().addAll(new MoveTo(x0, y0), new LineTo(x1, y0),
                        new LineTo(x1, y1), new LineTo(x0, y1), new ClosePath());
            }
        }
        trazado.setFill(Color.BLACK);
        trazado.setStroke(null);

        Pane qr = new Pane(trazado);
        qr.setStyle("-fx-background-color: white;");
        qr.setMinSize(lado, lado);
        qr.setPrefSize(lado, lado);
        qr.setMaxSize(lado, lado);
        return qr;
    }

    // ---------- helpers ----------

    /**
     * Texto de la etiqueta con estilo en línea: así la hoja de estilos de la app (tema, colores)
     * no lo altera y se imprime siempre negro sobre blanco.
     */
    private static Label texto(String valor, double tamanio, boolean negrita) {
        Label l = new Label(valor == null ? "—" : valor);
        l.setStyle("-fx-font-family: 'Arial'; -fx-font-size: " + tamanio + "px;"
                + "-fx-font-weight: " + (negrita ? "bold" : "normal") + ";"
                + "-fx-text-fill: black; -fx-padding: 0; -fx-background-color: transparent;");
        l.setTextOverrun(OverrunStyle.ELLIPSIS);
        l.setMinWidth(0);
        return l;
    }

    /** Versión corta de la ubicación para que quepa en la etiqueta (en el QR va completa). */
    static String abreviarUbicacion(String ubicacion) {
        if (ubicacion == null || ubicacion.isBlank()) {
            return "Sin ubicación";
        }
        return ubicacion.trim()
                .replace("Ultracongelador", "Ultracong.")
                .replace("Congelador", "Cong.")
                .replace("Refrigerador", "Refrig.")
                .replace("Cámara de cultivo", "Cám. cultivo")
                .replace("Estante temperatura ambiente", "Estante T° amb.")
                .replace("temperatura ambiente", "T° amb.");
    }

    private static String sitioCompleto(Muestra m) {
        if (m.getSitioCodigo() != null) {
            return m.getSitioCodigo() + (m.getSitioNombre() == null ? "" : " " + m.getSitioNombre());
        }
        return m.getProcedencia();
    }

    private static void agregar(List<String> lineas, String nombre, String valor) {
        if (valor != null && !valor.isBlank()) {
            lineas.add(nombre + ": " + valor.trim());
        }
    }

    private static String minuscula(String s) {
        return s == null ? "—" : s.toLowerCase();
    }

    private static String vacioGuion(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }
}
