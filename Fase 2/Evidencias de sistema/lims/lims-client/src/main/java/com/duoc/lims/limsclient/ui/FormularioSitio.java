package com.duoc.lims.limsclient.ui;

import com.duoc.lims.limsclient.model.Sitio;
import com.duoc.lims.limsclient.model.SitioRequest;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/** v5 — Diálogo de alta/edición de un sitio de muestreo (lo usan Sitios y Registro de muestra). */
public final class FormularioSitio {

    /** Tipos de ambiente (lista desplegable). */
    public static final List<String> TIPOS_AMBIENTE = List.of(
            "Marino costero", "Estuario", "Lago", "Río", "Humedal", "Otro");

    private FormularioSitio() {
    }

    /**
     * Abre el formulario. {@code actual} null = alta; {@code lat}/{@code lon} prellenan las coordenadas
     * (por ejemplo, desde un clic en el mapa). Devuelve vacío si el usuario cancela.
     */
    public static Optional<SitioRequest> abrir(Scene escena, Sitio actual, BigDecimal lat, BigDecimal lon) {
        TextField codigo = new TextField();
        TextField nombre = new TextField();
        ComboBox<String> tipo = new ComboBox<>();
        tipo.getItems().setAll(TIPOS_AMBIENTE);
        tipo.setMaxWidth(Double.MAX_VALUE);
        TextField latitud = new TextField();
        TextField longitud = new TextField();
        TextField comuna = new TextField();
        TextField region = new TextField("Los Lagos");
        TextField descripcion = new TextField();
        codigo.setPromptText("Vacío = se genera (ST-007...)");
        nombre.setPromptText("Ej: Bahía Ilque, estación 3");
        latitud.setPromptText("Ej: -41.6215 (sur = negativo)");
        longitud.setPromptText("Ej: -73.0608 (oeste = negativo)");
        if (actual != null) {
            codigo.setText(actual.codigo());
            codigo.setDisable(true);
            nombre.setText(actual.nombre());
            tipo.setValue(actual.tipoAmbiente());
            latitud.setText(texto(actual.latitud()));
            longitud.setText(texto(actual.longitud()));
            comuna.setText(actual.comuna());
            region.setText(actual.region());
            descripcion.setText(actual.descripcion());
        }
        if (lat != null && lon != null) {
            latitud.setText(lat.setScale(6, RoundingMode.HALF_UP).toPlainString());
            longitud.setText(lon.setScale(6, RoundingMode.HALF_UP).toPlainString());
        }
        Label error = Formularios.lblError();
        GridPane form = Formularios.grilla("Código", codigo, "Nombre *", nombre, "Tipo de ambiente *", tipo,
                "Latitud *", latitud, "Longitud *", longitud, "Comuna", comuna, "Región", region,
                "Descripción", descripcion);
        form.add(error, 0, 8, 2, 1);

        ButtonType guardar = Formularios.boton(actual == null ? "Registrar sitio" : "Guardar cambios");
        Dialog<ButtonType> d = Formularios.dialogo(escena.getWindow(), escena.getStylesheets(),
                actual == null ? "Nuevo sitio de muestreo" : "Editar sitio",
                actual == null ? "Punto georreferenciado donde se toman muestras" : actual.codigo() + " · " + actual.nombre(),
                form, guardar);
        d.getDialogPane().lookupButton(guardar).addEventFilter(ActionEvent.ACTION, ev -> {
            String problema = validar(nombre, tipo, latitud, longitud);
            if (problema != null) {
                error.setText(problema);
                ev.consume();
            }
        });
        Optional<ButtonType> r = d.showAndWait();
        if (r.isEmpty() || r.get() != guardar) {
            return Optional.empty();
        }
        return Optional.of(new SitioRequest(actual == null ? Formularios.texto(codigo) : actual.codigo(),
                Formularios.texto(nombre), numero(latitud), numero(longitud), tipo.getValue(),
                Formularios.texto(comuna), Formularios.texto(region), Formularios.texto(descripcion)));
    }

    private static String validar(TextField nombre, ComboBox<String> tipo, TextField lat, TextField lon) {
        if (Formularios.vacio(nombre) || tipo.getValue() == null) {
            return "Nombre y tipo de ambiente son obligatorios.";
        }
        BigDecimal la = numero(lat);
        BigDecimal lo = numero(lon);
        if (la == null || lo == null) {
            return "Latitud y longitud deben ser números en grados decimales (ej: -41.6215).";
        }
        if (la.abs().compareTo(BigDecimal.valueOf(90)) > 0 || lo.abs().compareTo(BigDecimal.valueOf(180)) > 0) {
            return "Coordenadas fuera de rango: latitud entre -90 y 90, longitud entre -180 y 180.";
        }
        return null;
    }

    private static BigDecimal numero(TextField campo) {
        String t = Formularios.texto(campo);
        if (t == null) {
            return null;
        }
        try {
            return new BigDecimal(t.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String texto(BigDecimal n) {
        return n == null ? "" : n.stripTrailingZeros().toPlainString();
    }
}
