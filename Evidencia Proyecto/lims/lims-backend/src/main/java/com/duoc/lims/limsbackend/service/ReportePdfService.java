package com.duoc.lims.limsbackend.service;

import com.duoc.lims.limsbackend.model.AnalisisCatalogo;
import com.duoc.lims.limsbackend.model.Aprobacion;
import com.duoc.lims.limsbackend.model.Centro;
import com.duoc.lims.limsbackend.model.Muestra;
import com.duoc.lims.limsbackend.model.MuestraAnalisis;
import com.duoc.lims.limsbackend.model.Reporte;
import com.duoc.lims.limsbackend.model.Resultado;
import com.duoc.lims.limsbackend.model.Usuario;
import com.duoc.lims.limsbackend.model.enums.EstadoMuestra;
import com.duoc.lims.limsbackend.repository.AprobacionRepository;
import com.duoc.lims.limsbackend.repository.MuestraAnalisisRepository;
import com.duoc.lims.limsbackend.repository.MuestraRepository;
import com.duoc.lims.limsbackend.repository.ReporteRepository;
import com.duoc.lims.limsbackend.repository.ResultadoRepository;
import com.duoc.lims.limsbackend.repository.UsuarioRepository;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * RF05 — Genera el informe de resultados de una muestra en PDF.
 * Solo se emite para muestras Aprobadas (o ya Reportadas, en cuyo caso se crea una nueva versión).
 * Cada emisión queda registrada en la tabla "reportes" y el archivo se guarda en disco.
 */
@Service
public class ReportePdfService {

    /** Resultado de la generación: los bytes del PDF + datos para el nombre del archivo. */
    public record ReporteGenerado(byte[] pdf, String nombreArchivo, int version) {}

    // ---------- estilo del documento ----------
    private static final Color AZUL = new Color(0x1E, 0x4F, 0x80);
    private static final Color GRIS_FONDO = new Color(0xF1, 0xF5, 0xF9);
    private static final Color GRIS_BORDE = new Color(0xCB, 0xD5, 0xE1);
    private static final Color GRIS_TEXTO = new Color(0x64, 0x74, 0x8B);
    private static final Color TEXTO = new Color(0x1E, 0x29, 0x3B);
    private static final Color VERDE_FONDO = new Color(0xDC, 0xFC, 0xE7);
    private static final Color VERDE_TEXTO = new Color(0x16, 0x65, 0x34);
    private static final Color ROJO_FONDO = new Color(0xFE, 0xE2, 0xE2);
    private static final Color ROJO_TEXTO = new Color(0x99, 0x1B, 0x1B);

    private static final Font F_TITULO_LAB = new Font(Font.HELVETICA, 15, Font.BOLD, AZUL);
    private static final Font F_NORMAL = new Font(Font.HELVETICA, 9, Font.NORMAL, TEXTO);
    private static final Font F_NEGRITA = new Font(Font.HELVETICA, 9, Font.BOLD, TEXTO);
    private static final Font F_ETIQUETA = new Font(Font.HELVETICA, 8.5f, Font.BOLD, GRIS_TEXTO);
    private static final Font F_SECCION = new Font(Font.HELVETICA, 11, Font.BOLD, AZUL);
    private static final Font F_CAJA_TITULO = new Font(Font.HELVETICA, 11, Font.BOLD, Color.WHITE);
    private static final Font F_CAJA = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.WHITE);
    private static final Font F_TABLA_CAB = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);
    private static final Font F_TABLA = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, TEXTO);
    private static final Font F_NOTA = new Font(Font.HELVETICA, 7.5f, Font.ITALIC, GRIS_TEXTO);

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private final MuestraRepository muestraRepository;
    private final UsuarioRepository usuarioRepository;
    private final MuestraAnalisisRepository muestraAnalisisRepository;
    private final ResultadoRepository resultadoRepository;
    private final AprobacionRepository aprobacionRepository;
    private final ReporteRepository reporteRepository;
    private final EstadoMuestraService estadoMuestraService;
    private final Path carpetaReportes;

    public ReportePdfService(MuestraRepository muestraRepository,
                             UsuarioRepository usuarioRepository,
                             MuestraAnalisisRepository muestraAnalisisRepository,
                             ResultadoRepository resultadoRepository,
                             AprobacionRepository aprobacionRepository,
                             ReporteRepository reporteRepository,
                             EstadoMuestraService estadoMuestraService,
                             @Value("${lims.reportes.dir:reportes}") String carpetaReportes) {
        this.muestraRepository = muestraRepository;
        this.usuarioRepository = usuarioRepository;
        this.muestraAnalisisRepository = muestraAnalisisRepository;
        this.resultadoRepository = resultadoRepository;
        this.aprobacionRepository = aprobacionRepository;
        this.reporteRepository = reporteRepository;
        this.estadoMuestraService = estadoMuestraService;
        this.carpetaReportes = Paths.get(carpetaReportes);
    }

    @Transactional
    public ReporteGenerado generar(Integer idMuestra, Integer idUsuario) {
        Muestra muestra = muestraRepository.findById(idMuestra)
                .orElseThrow(() -> new IllegalArgumentException("No existe la muestra con id " + idMuestra));

        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new IllegalArgumentException("No existe el usuario con id " + idUsuario));

        if (muestra.getEstado() != EstadoMuestra.APROBADA && muestra.getEstado() != EstadoMuestra.REPORTADA) {
            throw new IllegalArgumentException(
                    "Solo se puede emitir el informe de una muestra Aprobada (estado actual: "
                            + muestra.getEstado().getValorDb() + ")");
        }

        List<MuestraAnalisis> analisis = muestraAnalisisRepository.findByMuestra_Id(idMuestra);
        if (analisis.isEmpty()) {
            throw new IllegalArgumentException("La muestra no tiene análisis para informar");
        }

        int version = reporteRepository.findTopByMuestra_IdOrderByVersionDesc(idMuestra)
                .map(r -> r.getVersion() + 1)
                .orElse(1);
        String numeroInforme = muestra.getCodigoUnico() + "-v" + version;
        LocalDateTime emision = LocalDateTime.now();

        byte[] pdf = construirPdf(muestra, analisis, usuario, numeroInforme, emision);

        String nombreArchivo = "Informe_" + numeroInforme + ".pdf";
        Path ruta = guardarEnDisco(nombreArchivo, pdf);

        Reporte reporte = new Reporte();
        reporte.setMuestra(muestra);
        reporte.setUsuarioGenerador(usuario);
        reporte.setRutaArchivo(ruta.toAbsolutePath().toString());
        reporte.setVersion(version);
        reporteRepository.save(reporte);

        // La muestra pasa a "Reportada" (si ya lo estaba, no se duplica el historial).
        estadoMuestraService.cambiarEstado(muestra, EstadoMuestra.REPORTADA, usuario,
                "Informe emitido " + numeroInforme);

        return new ReporteGenerado(pdf, nombreArchivo, version);
    }

    // =====================================================================
    // Construcción del documento
    // =====================================================================

    private byte[] construirPdf(Muestra muestra, List<MuestraAnalisis> analisis, Usuario emisor,
                                String numeroInforme, LocalDateTime emision) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 40, 55);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, salida);
            writer.setPageEvent(new PieDePagina(numeroInforme));
            doc.open();

            doc.add(cabecera(muestra.getCentro(), numeroInforme, emision));
            doc.add(seccion("1. Datos de la muestra"));
            doc.add(datosMuestra(muestra));
            doc.add(seccion("2. Resultados de los análisis"));

            Set<String> validadores = new LinkedHashSet<>();
            StringBuilder observaciones = new StringBuilder();
            doc.add(tablaResultados(analisis, validadores, observaciones));

            if (!observaciones.isEmpty()) {
                doc.add(seccion("3. Observaciones del análisis"));
                Paragraph p = new Paragraph(observaciones.toString(), F_NORMAL);
                p.setLeading(13);
                doc.add(p);
            }

            doc.add(seccion((observaciones.isEmpty() ? "3" : "4") + ". Validación y emisión"));
            doc.add(validacion(validadores, emisor, emision));

            Paragraph nota = new Paragraph(
                    "Los resultados de este informe se refieren únicamente a la muestra recibida y analizada. "
                            + "Este informe no debe reproducirse parcialmente sin la autorización escrita del laboratorio. "
                            + "Documento generado por LIMS - Sistema de Gestión de Información de Laboratorio.",
                    F_NOTA);
            nota.setSpacingBefore(22);
            doc.add(nota);

            doc.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF: " + e.getMessage(), e);
        }
        return salida.toByteArray();
    }

    private PdfPTable cabecera(Centro centro, String numeroInforme, LocalDateTime emision) throws DocumentException {
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{3f, 2f});

        // Izquierda: datos del laboratorio
        PdfPCell lab = new PdfPCell();
        lab.setBorder(Rectangle.NO_BORDER);
        lab.addElement(new Paragraph(centro.getNombreCentro(), F_TITULO_LAB));
        if (centro.getTipoCentro() != null) {
            lab.addElement(new Paragraph("Laboratorio " + centro.getTipoCentro().getValorDb(), F_NORMAL));
        }
        agregarSiExiste(lab, centro.getDireccion(), "");
        agregarSiExiste(lab, centro.getTelefono(), "Tel: ");
        agregarSiExiste(lab, centro.getEmail(), "");
        t.addCell(lab);

        // Derecha: caja azul con el número de informe
        PdfPCell caja = new PdfPCell();
        caja.setBackgroundColor(AZUL);
        caja.setBorder(Rectangle.NO_BORDER);
        caja.setPadding(10);
        Paragraph titulo = new Paragraph("INFORME DE RESULTADOS", F_CAJA_TITULO);
        titulo.setAlignment(Element.ALIGN_RIGHT);
        caja.addElement(titulo);
        Paragraph numero = new Paragraph("N° " + numeroInforme, F_CAJA);
        numero.setAlignment(Element.ALIGN_RIGHT);
        caja.addElement(numero);
        Paragraph fecha = new Paragraph("Emitido: " + emision.format(FECHA_HORA), F_CAJA);
        fecha.setAlignment(Element.ALIGN_RIGHT);
        caja.addElement(fecha);
        t.addCell(caja);

        t.setSpacingAfter(6);
        return t;
    }

    private void agregarSiExiste(PdfPCell celda, String valor, String prefijo) {
        if (valor != null && !valor.isBlank() && !"Por definir".equalsIgnoreCase(valor)) {
            celda.addElement(new Paragraph(prefijo + valor, F_NORMAL));
        }
    }

    private Paragraph seccion(String titulo) {
        Paragraph p = new Paragraph(titulo, F_SECCION);
        p.setSpacingBefore(14);
        p.setSpacingAfter(6);
        return p;
    }

    private PdfPTable datosMuestra(Muestra m) throws DocumentException {
        PdfPTable t = new PdfPTable(4);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{1.3f, 2.2f, 1.3f, 2.2f});

        par(t, "Código", m.getCodigoUnico());
        par(t, "Tipo de muestra", m.getTipoMuestra().getValorDb());
        par(t, "Cliente / procedencia", m.getProcedencia());
        par(t, "Prioridad", m.getPrioridad().getValorDb());
        par(t, "Fecha de recepción", m.getFechaRecepcion() != null ? m.getFechaRecepcion().format(FECHA_HORA) : null);
        par(t, "Registrada por", nombre(m.getUsuarioRegistro()));
        par(t, "Fecha de toma", m.getFechaToma() != null ? m.getFechaToma().format(FECHA_HORA) : null);
        par(t, "Condición de recepción", m.getCondicionRecepcion());

        t.addCell(etiqueta("Observaciones"));
        PdfPCell obs = valor(m.getObservaciones());
        obs.setColspan(3);
        t.addCell(obs);
        return t;
    }

    private PdfPTable tablaResultados(List<MuestraAnalisis> analisis, Set<String> validadores,
                                      StringBuilder observaciones) throws DocumentException {
        String[] cabeceras = {"Análisis", "Método", "Resultado", "Unidad", "Rango de referencia",
                "Control", "Analista", "Validado por"};
        PdfPTable t = new PdfPTable(cabeceras.length);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{2.0f, 1.7f, 1.2f, 1.1f, 1.6f, 1.3f, 1.6f, 1.8f});
        t.setHeaderRows(1);   // la cabecera se repite si la tabla continúa en otra página

        for (String c : cabeceras) {
            PdfPCell celda = new PdfPCell(new Phrase(c, F_TABLA_CAB));
            celda.setBackgroundColor(AZUL);
            celda.setBorderColor(AZUL);
            celda.setPadding(5);
            t.addCell(celda);
        }

        for (MuestraAnalisis ma : analisis) {
            AnalisisCatalogo cat = ma.getAnalisis();
            Optional<Resultado> resultado = resultadoRepository.findByMuestraAnalisis_Id(ma.getId());

            t.addCell(celdaTabla(cat.getNombre()));
            t.addCell(celdaTabla(cat.getMetodoReferencia()));

            if (resultado.isEmpty()) {
                t.addCell(celdaTabla("Sin resultado"));
                t.addCell(celdaTabla(cat.getUnidadMedida()));
                t.addCell(celdaTabla(numero(cat.getValorMinNormal()) + " - " + numero(cat.getValorMaxNormal())));
                t.addCell(celdaTabla(null));
                t.addCell(celdaTabla(null));
                t.addCell(celdaTabla(null));
                continue;
            }

            Resultado r = resultado.get();
            PdfPCell valor = celdaTabla(numero(r.getValorResultado()));
            valor.setPhrase(new Phrase(numero(r.getValorResultado()), new Font(Font.HELVETICA, 8.5f, Font.BOLD, TEXTO)));
            t.addCell(valor);
            t.addCell(celdaTabla(cat.getUnidadMedida()));
            t.addCell(celdaTabla(numero(cat.getValorMinNormal()) + " - " + numero(cat.getValorMaxNormal())));
            t.addCell(celdaControl(Boolean.TRUE.equals(r.getDentroRango())));
            t.addCell(celdaTabla(nombre(r.getUsuarioIngreso())));

            Optional<Aprobacion> aprobacion = aprobacionRepository.findTopByResultado_IdOrderByIdDesc(r.getId());
            if (aprobacion.isPresent()) {
                Aprobacion a = aprobacion.get();
                t.addCell(celdaTabla(nombre(a.getSupervisor()) + "\n" + a.getFechaAprobacion().format(FECHA_HORA)));
                validadores.add(nombre(a.getSupervisor()) + " (" + a.getSupervisor().getRol().getNombreRol() + ")");
            } else {
                t.addCell(celdaTabla("Pendiente"));
            }

            if (r.getObservaciones() != null && !r.getObservaciones().isBlank()) {
                observaciones.append("- ").append(cat.getNombre()).append(": ")
                        .append(r.getObservaciones().trim()).append("\n");
            }
        }
        return t;
    }

    private PdfPTable validacion(Set<String> validadores, Usuario emisor, LocalDateTime emision)
            throws DocumentException {
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{1.3f, 4.4f});
        t.addCell(etiqueta("Resultados validados por"));
        t.addCell(valor(validadores.isEmpty() ? null : String.join("\n", validadores)));
        t.addCell(etiqueta("Informe emitido por"));
        t.addCell(valor(nombre(emisor) + " (" + emisor.getRol().getNombreRol() + ") el " + emision.format(FECHA_HORA)));
        return t;
    }

    // ---------- celdas ----------

    private void par(PdfPTable t, String etiqueta, String valor) {
        t.addCell(etiqueta(etiqueta));
        t.addCell(valor(valor));
    }

    private PdfPCell etiqueta(String texto) {
        PdfPCell c = new PdfPCell(new Phrase(texto, F_ETIQUETA));
        c.setBackgroundColor(GRIS_FONDO);
        c.setBorderColor(GRIS_BORDE);
        c.setPadding(5);
        return c;
    }

    private PdfPCell valor(String texto) {
        PdfPCell c = new PdfPCell(new Phrase(vacio(texto), F_NORMAL));
        c.setBorderColor(GRIS_BORDE);
        c.setPadding(5);
        return c;
    }

    private PdfPCell celdaTabla(String texto) {
        PdfPCell c = new PdfPCell(new Phrase(vacio(texto), F_TABLA));
        c.setBorderColor(GRIS_BORDE);
        c.setPadding(5);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return c;
    }

    private PdfPCell celdaControl(boolean dentroRango) {
        Font f = new Font(Font.HELVETICA, 8, Font.BOLD, dentroRango ? VERDE_TEXTO : ROJO_TEXTO);
        PdfPCell c = new PdfPCell(new Phrase(dentroRango ? "En rango" : "Fuera de rango", f));
        c.setBackgroundColor(dentroRango ? VERDE_FONDO : ROJO_FONDO);
        c.setBorderColor(GRIS_BORDE);
        c.setPadding(5);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return c;
    }

    // ---------- utilidades ----------

    private Path guardarEnDisco(String nombreArchivo, byte[] pdf) {
        try {
            Files.createDirectories(carpetaReportes);
            Path ruta = carpetaReportes.resolve(nombreArchivo);
            Files.write(ruta, pdf);
            return ruta;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar el informe en disco: " + e.getMessage(), e);
        }
    }

    private String nombre(Usuario u) {
        return u == null ? null : u.getNombre() + " " + u.getApellido();
    }

    private String numero(BigDecimal n) {
        return n == null ? "" : n.stripTrailingZeros().toPlainString();
    }

    private String vacio(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    /** Pie de página con el número de informe y el número de página. */
    private static class PieDePagina extends PdfPageEventHelper {
        private final String numeroInforme;

        PieDePagina(String numeroInforme) {
            this.numeroInforme = numeroInforme;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            float y = document.bottom() - 18;
            cb.setColorStroke(GRIS_BORDE);
            cb.setLineWidth(0.5f);
            cb.moveTo(document.left(), y + 12);
            cb.lineTo(document.right(), y + 12);
            cb.stroke();
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                    new Phrase("Informe N° " + numeroInforme, F_NOTA), document.left(), y, 0);
            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                    new Phrase("Página " + writer.getPageNumber(), F_NOTA), document.right(), y, 0);
        }
    }
}
