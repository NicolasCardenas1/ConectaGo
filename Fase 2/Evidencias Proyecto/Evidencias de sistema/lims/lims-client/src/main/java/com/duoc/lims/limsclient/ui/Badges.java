package com.duoc.lims.limsclient.ui;

import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.layout.Region;

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

    /**
     * Clase CSS de color según el estado. Significado de los colores (IEC 60073 / ISO 3864, ver styles.css):
     * verde = normal o aprobado, ámbar = condición anormal o precaución, rojo = falla o acción obligatoria,
     * azul = en curso, gris = neutro, morado = cerrado (informado). El texto siempre acompaña al color.
     */
    public static String claseColor(String estado) {
        if (estado == null) {
            return "badge-gris";
        }
        return switch (estado) {
            case "Recibida", "Pendiente" -> "badge-gris";          // estado inicial normal: neutro, no alerta
            case "En analisis", "En proceso", "Resultados ingresados", "Completado" -> "badge-azul";
            case "Aprobada", "Aprobado", "En rango" -> "badge-verde";
            case "Rechazada", "Rechazado" -> "badge-rojo";
            case "Fuera de rango" -> "badge-amarillo";             // condición anormal (no es una falla)
            case "Reportada" -> "badge-morado";
            // roles: mismos colores que la barra lateral de cada rol (MainLayoutController)
            case "Administrador" -> "badge-morado";
            case "Analista" -> "badge-azul";
            case "Supervisor" -> "badge-cian";
            case "Activo" -> "badge-verde";
            case "Inactivo" -> "badge-gris";
            case "Reset solicitado" -> "badge-amarillo";
            // v5: calibración de equipos y detección de anomalías
            case "Vigente" -> "badge-verde";
            case "Por vencer", "Sin registro" -> "badge-amarillo";
            case "Vencida", "Atípico" -> "badge-rojo";
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
                    Label etiqueta = crear(valor);
                    etiqueta.setMinWidth(Region.USE_PREF_SIZE);   // la etiqueta nunca se corta ("Por ven...")
                    setGraphic(etiqueta);
                }
            }
        };
    }
}
