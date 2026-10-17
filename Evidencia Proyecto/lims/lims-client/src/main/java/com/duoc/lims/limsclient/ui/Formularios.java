package com.duoc.lims.limsclient.ui;

import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

import java.util.List;

/** Ayudas para armar diálogos con formulario (etiqueta + campo) con el estilo de la app. */
public final class Formularios {

    private Formularios() {
    }

    /** Grilla de dos columnas: etiqueta a la izquierda, campo a la derecha. */
    public static GridPane grilla(Object... etiquetasYCampos) {
        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(10);
        g.setStyle("-fx-padding: 6 4 0 4;");
        for (int i = 0; i < etiquetasYCampos.length; i += 2) {
            Label l = new Label((String) etiquetasYCampos[i]);
            l.getStyleClass().add("field-name");
            Node campo = (Node) etiquetasYCampos[i + 1];
            campo.getStyleClass().add("form-field");
            if (campo instanceof TextField tf) {
                tf.setPrefWidth(280);
            }
            g.add(l, 0, i / 2);
            g.add(campo, 1, i / 2);
        }
        return g;
    }

    /** Diálogo modal con el tema de la app y un botón de confirmación con el texto indicado. */
    public static Dialog<ButtonType> dialogo(Window duenio, List<String> estilos, String titulo,
                                             String encabezado, Node contenido, ButtonType confirmar) {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("LIMS - " + titulo);
        d.setHeaderText(encabezado);
        d.getDialogPane().setContent(contenido);
        d.getDialogPane().getButtonTypes().addAll(confirmar, ButtonType.CANCEL);
        d.initOwner(duenio);
        d.getDialogPane().getStylesheets().addAll(estilos);
        return d;
    }

    public static ButtonType boton(String texto) {
        return new ButtonType(texto, ButtonBar.ButtonData.OK_DONE);
    }

    public static boolean vacio(TextField campo) {
        return campo.getText() == null || campo.getText().isBlank();
    }

    public static String texto(TextField campo) {
        return vacio(campo) ? null : campo.getText().trim();
    }

    public static Label lblError() {
        Label error = new Label();
        error.getStyleClass().add("status-text");
        error.setWrapText(true);
        return error;
    }
}
