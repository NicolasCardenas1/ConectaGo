package com.duoc.lims.limsbackend.service;

import com.duoc.lims.limsbackend.dto.AnomaliaDTO;
import com.duoc.lims.limsbackend.model.AnalisisCatalogo;
import com.duoc.lims.limsbackend.model.Muestra;
import com.duoc.lims.limsbackend.model.MuestraAnalisis;
import com.duoc.lims.limsbackend.model.Resultado;
import com.duoc.lims.limsbackend.model.enums.TipoRegistro;
import com.duoc.lims.limsbackend.repository.ResultadoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * v5 — Detección automática de valores atípicos (componente de "IA" del LIMS).
 *
 * Compara el valor nuevo contra el historial del MISMO parámetro en el MISMO sitio de muestreo
 * (o, si no hay historial suficiente, del mismo tipo de muestra) usando el puntaje z modificado de
 * Iglewicz y Hoaglin:  z = 0,6745 · (x − mediana) / MAD.  Es un método robusto: a diferencia del
 * promedio y la desviación estándar, la mediana y la MAD no se dejan arrastrar por los propios
 * valores atípicos del historial. Se marca como anómalo cuando |z| > 3,5 (umbral recomendado).
 *
 * Esto es distinto del rango normal del catálogo (RF03): un valor puede estar dentro del rango
 * y aun así ser muy raro para ESE sitio (posible error de digitación, contaminación o un evento
 * ambiental real). El sistema no bloquea: avisa al analista y destaca el resultado al supervisor.
 */
@Service
public class AnomaliaService {

    /** Umbral recomendado para el puntaje z modificado. */
    public static final BigDecimal UMBRAL = new BigDecimal("3.5");
    /** Mínimo de resultados históricos para que la comparación tenga sentido. */
    public static final int MIN_HISTORIAL = 5;

    private final ResultadoRepository resultadoRepository;

    public AnomaliaService(ResultadoRepository resultadoRepository) {
        this.resultadoRepository = resultadoRepository;
    }

    /**
     * Evalúa un valor para un análisis solicitado.
     * @param excluirResultado id del resultado que se está corrigiendo (no debe compararse consigo mismo), o null.
     */
    @Transactional(readOnly = true)
    public AnomaliaDTO evaluar(MuestraAnalisis ma, BigDecimal valor, Integer excluirResultado) {
        AnalisisCatalogo analisis = ma.getAnalisis();
        Muestra muestra = ma.getMuestra();

        List<Resultado> historial = resultadoRepository.findByMuestraAnalisis_Analisis_Id(analisis.getId()).stream()
                .filter(r -> !Objects.equals(r.getId(), excluirResultado))
                .filter(r -> !Objects.equals(r.getMuestraAnalisis().getMuestra().getId(), muestra.getId()))
                // las muestras de control de calidad (blancos, estándares) no representan el ambiente
                .filter(r -> r.getMuestraAnalisis().getMuestra().getTipoRegistro() == TipoRegistro.MUESTRA)
                .toList();

        // 1° mismo sitio; 2° mismo tipo de muestra
        List<BigDecimal> valores = List.of();
        String referencia = null;
        if (muestra.getSitio() != null) {
            Integer idSitio = muestra.getSitio().getId();
            valores = historial.stream()
                    .filter(r -> r.getMuestraAnalisis().getMuestra().getSitio() != null
                            && idSitio.equals(r.getMuestraAnalisis().getMuestra().getSitio().getId()))
                    .map(Resultado::getValorResultado)
                    .toList();
            referencia = "sitio " + muestra.getSitio().getCodigo() + " " + muestra.getSitio().getNombre();
        }
        if (valores.size() < MIN_HISTORIAL) {
            valores = historial.stream()
                    .filter(r -> r.getMuestraAnalisis().getMuestra().getTipoMuestra() == muestra.getTipoMuestra())
                    .map(Resultado::getValorResultado)
                    .toList();
            referencia = "muestras de tipo " + muestra.getTipoMuestra().getValorDb();
        }
        if (valores.size() < MIN_HISTORIAL) {
            return new AnomaliaDTO(false, false, null, valores.size(), null, referencia,
                    "Sin historial suficiente para detectar anomalías (" + valores.size() + " de "
                            + MIN_HISTORIAL + " resultados previos de " + analisis.getNombre() + ").");
        }

        double[] datos = valores.stream().mapToDouble(BigDecimal::doubleValue).sorted().toArray();
        double x = valor.doubleValue();
        double mediana = mediana(datos);
        double[] desvios = new double[datos.length];
        for (int i = 0; i < datos.length; i++) {
            desvios[i] = Math.abs(datos[i] - mediana);
        }
        Arrays.sort(desvios);
        double mad = mediana(desvios);

        double puntaje;
        if (mad > 0) {
            puntaje = 0.6745 * (x - mediana) / mad;
        } else {
            // Historial sin variación (MAD = 0): se usa la desviación media como respaldo.
            double media = Arrays.stream(datos).average().orElse(0);
            double desvMedia = Arrays.stream(datos).map(d -> Math.abs(d - media)).average().orElse(0);
            puntaje = desvMedia > 0 ? (x - mediana) / (1.253314 * desvMedia) : (x == mediana ? 0 : 99);
        }

        BigDecimal puntajeBD = BigDecimal.valueOf(puntaje).setScale(2, RoundingMode.HALF_UP);
        BigDecimal medianaBD = BigDecimal.valueOf(mediana).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros();
        boolean anomalo = puntajeBD.abs().compareTo(UMBRAL) > 0;
        String unidad = analisis.getUnidadMedida();
        String direccion = puntaje > 0 ? "sobre" : "bajo";

        String mensaje = anomalo
                ? String.format("Valor atípico: puntaje %s (umbral ±%s), muy %s la mediana histórica de %s %s "
                        + "(n=%d, %s). Verifique digitación, equipo y muestra.",
                        puntajeBD.toPlainString(), UMBRAL.toPlainString(), direccion, medianaBD.toPlainString(), unidad,
                        datos.length, referencia)
                : String.format("Consistente con el historial: puntaje %s respecto de la mediana %s %s (n=%d, %s).",
                        puntajeBD.toPlainString(), medianaBD.toPlainString(), unidad, datos.length, referencia);

        return new AnomaliaDTO(true, anomalo, puntajeBD, datos.length, medianaBD, referencia, recortar(mensaje));
    }

    private double mediana(double[] ordenados) {
        int n = ordenados.length;
        return n % 2 == 1 ? ordenados[n / 2] : (ordenados[n / 2 - 1] + ordenados[n / 2]) / 2.0;
    }

    private String recortar(String s) {
        return s.length() <= 255 ? s : s.substring(0, 252) + "...";
    }
}
