package com.duoc.lims.limsclient.ui;

import javafx.scene.control.Label;
import javafx.scene.control.TableCell;

/**
 * Etiquetas de color para los estados (muestra, análisis y aprobación).
 * Los colores están definidos en styles.css (clases .badge-*).
 */
public final class Badges {

    private Badges() {
    }

    /** Crea una etiqueta de color con el texto del estado. */
    public static Label crear(String estado) {
        Label etiqueta = new Label(textoVisible(estado));
        etiqueta.getStyleClass().addAll("badge", claseColor(estado));
        return etiqueta;
    }

    /** Clase CSS de color según el estado. */
    public static String claseColor(String estado) {
        if (estado == null) {
            return "badge-gris";
        }
        return switch (estado) {
            case "Recibida", "Pendiente" -> "badge-amarillo";
            case "En analisis", "En proceso", "Resultados ingresados", "Completado" -> "badge-azul";
            case "Aprobada", "Aprobado", "En rango" -> "badge-verde";
            case "Rechazada", "Rechazado", "Fuera de rango" -> "badge-rojo";
            case "Reportada" -> "badge-morado";
            default -> "badge-gris";
        };
    }

    /** La BD guarda "En analisis" sin tilde; aquí se muestra bien escrito. */
    public static String textoVisible(String estado) {
        return "En analisis".equals(estado) ? "En análisis" : estado;
    }

    /** Celda de tabla que dibuja el valor como etiqueta de color. Uso: col.setCellFactory(c -> Badges.celda()); */
    public static <S> TableCell<S, String> celda() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String valor, boolean empty) {
                super.updateItem(valor, empty);
                if (empty || valor == null || "—".equals(valor)) {
                    setGraphic(null);
                    setText(empty ? null : valor);
                } else {
                    setText(null);
                    setGraphic(crear(valor));
                }
            }
        };
    }
}
