package com.duoc.lims.limsbackend.controller;

import com.duoc.lims.limsbackend.dto.AnalisisDeMuestraDTO;
import com.duoc.lims.limsbackend.dto.MuestraDetalleDTO;
import com.duoc.lims.limsbackend.dto.MuestraResponseDTO;
import com.duoc.lims.limsbackend.dto.ResultadoResponseDTO;
import com.duoc.lims.limsbackend.model.Muestra;
import com.duoc.lims.limsbackend.model.Reporte;
import com.duoc.lims.limsbackend.repository.MuestraRepository;
import com.duoc.lims.limsbackend.repository.ReporteRepository;
import com.duoc.lims.limsbackend.service.EnlaceQrService;
import com.duoc.lims.limsbackend.service.MuestraService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * v9 — Página que abre el código QR de la etiqueta en el celular.
 *
 * Es pública (el celular no tiene sesión en el LIMS), de solo lectura y exige la firma {@code k} que
 * va en el enlace impreso: sin la etiqueta no se puede ver la muestra. Muestra el estado actual, no el
 * del momento de imprimir. Los valores de los resultados solo aparecen cuando ya fueron aprobados.
 */
@RestController
public class ConsultaQrController {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private final EnlaceQrService enlaceQrService;
    private final MuestraRepository muestraRepository;
    private final MuestraService muestraService;
    private final ReporteRepository reporteRepository;

    public ConsultaQrController(EnlaceQrService enlaceQrService, MuestraRepository muestraRepository,
                                MuestraService muestraService, ReporteRepository reporteRepository) {
        this.enlaceQrService = enlaceQrService;
        this.muestraRepository = muestraRepository;
        this.muestraService = muestraService;
        this.reporteRepository = reporteRepository;
    }

    @GetMapping(value = "/m/{codigo}", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public ResponseEntity<String> consultar(@PathVariable String codigo,
                                            @RequestParam(name = "k", required = false) String firma) {
        Optional<Muestra> muestra = enlaceQrService.firmaValida(codigo, firma)
                ? muestraRepository.findByCodigoUnicoIgnoreCase(codigo)
                : Optional.empty();
        if (muestra.isEmpty()) {
            // mismo mensaje si el código no existe o la firma no calza: no revela qué muestras hay
            return respuesta(HttpStatus.NOT_FOUND, pagina("Enlace no válido",
                    "<div class='tarjeta'><h1>Enlace no válido</h1><p class='suave'>Este código QR no corresponde a "
                            + "ninguna muestra de este laboratorio o el enlace está incompleto. Escanea de nuevo la "
                            + "etiqueta del envase.</p></div>"));
        }
        MuestraDetalleDTO detalle = muestraService.obtenerDetalle(muestra.get().getId());
        List<Reporte> informes = reporteRepository.findByMuestra_IdOrderByVersionAsc(muestra.get().getId());
        return respuesta(HttpStatus.OK, pagina("Muestra " + detalle.getMuestra().getCodigoUnico(),
                cuerpo(detalle, informes)));
    }

    private static ResponseEntity<String> respuesta(HttpStatus estado, String html) {
        return ResponseEntity.status(estado)
                .cacheControl(CacheControl.noStore())   // siempre el estado actual
                .header("X-Robots-Tag", "noindex")
                .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
                .body(html);
    }

    // ---------- contenido ----------

    private static String cuerpo(MuestraDetalleDTO d, List<Reporte> informes) {
        MuestraResponseDTO m = d.getMuestra();
        StringBuilder sb = new StringBuilder();

        sb.append("<div class='tarjeta'><div class='fila-titulo'><h1>").append(e(m.getCodigoUnico())).append("</h1>")
                .append("<span class='estado ").append(claseEstado(m.getEstado())).append("'>")
                .append(e(estadoTexto(m.getEstado()))).append("</span></div>")
                .append("<p class='suave'>").append(e(m.getTipoMuestra())).append(" · prioridad ")
                .append(e(m.getPrioridad() == null ? "—" : m.getPrioridad().toLowerCase())).append("</p><dl>");
        dato(sb, "Sitio", m.getSitioCodigo() == null ? m.getProcedencia()
                : m.getSitioCodigo() + " · " + m.getSitioNombre());
        if (m.getSitioCodigo() != null && m.getProcedencia() != null && !m.getProcedencia().equals(m.getSitioNombre())) {
            dato(sb, "Punto", m.getProcedencia());
        }
        dato(sb, "Toma", fechaHora(m.getFechaToma()));
        dato(sb, "Recepción", fechaHora(m.getFechaRecepcion()));
        dato(sb, "Investigador", m.getInvestigador() == null ? null
                : m.getInvestigador() + (m.getInstitucion() == null ? "" : " (" + m.getInstitucion() + ")"));
        dato(sb, "Almacenamiento", m.getUbicacionAlmacenamiento());
        dato(sb, "Conservar hasta", m.getFechaVencimiento() == null ? null : m.getFechaVencimiento().format(FECHA));
        dato(sb, "Equipo de toma", m.getEquipoTomaCodigo() == null ? null
                : m.getEquipoTomaCodigo() + " · " + m.getEquipoTomaNombre());
        sb.append("</dl></div>");

        sb.append("<div class='tarjeta'><h2>Análisis</h2>");
        if (d.getAnalisis().isEmpty()) {
            sb.append("<p class='suave'>Todavía no se le asignan análisis.</p>");
        }
        for (AnalisisDeMuestraDTO a : d.getAnalisis()) {
            sb.append("<div class='analisis'><div><strong>").append(e(a.getNombreAnalisis())).append("</strong>")
                    .append("<div class='suave'>").append(e(textoAnalisis(a))).append("</div></div>");
            ResultadoResponseDTO r = a.getResultado();
            if (r != null && "Aprobado".equals(a.getEstadoAprobacion())) {
                boolean fuera = Boolean.FALSE.equals(r.getDentroRango());
                sb.append("<div class='valor").append(fuera ? " fuera" : "").append("'>")
                        .append(e(numero(r.getValorResultado()))).append(" ").append(e(a.getUnidadMedida()))
                        .append(fuera ? "<small>fuera de rango</small>" : "").append("</div>");
            }
            sb.append("</div>");
        }
        sb.append("</div>");

        if (!informes.isEmpty()) {
            Reporte ultimo = informes.get(informes.size() - 1);
            sb.append("<div class='tarjeta'><h2>Informe</h2><dl>");
            dato(sb, "Versión", ultimo.getVersion() + (informes.size() > 1 ? " de " + informes.size() : ""));
            dato(sb, "Emitido", fechaHora(ultimo.getFechaGeneracion()));
            dato(sb, "Código de verificación", ultimo.getCodigoVerificacion());
            sb.append("</dl><p class='suave'>El informe PDF está firmado digitalmente. Su autenticidad se "
                    + "comprueba en el LIMS, en Reportes → Verificar informe.</p></div>");
        }

        sb.append("<p class='pie'>Consulta de solo lectura · actualizado ")
                .append(LocalDateTime.now().format(FECHA_HORA))
                .append("<br>Los valores aparecen cuando el supervisor aprueba el resultado.</p>");
        return sb.toString();
    }

    private static String textoAnalisis(AnalisisDeMuestraDTO a) {
        if (a.getResultado() == null) {
            return "En proceso".equals(a.getEstado()) ? "En proceso" : "Pendiente";
        }
        if (a.getEstadoAprobacion() == null) {
            return "Resultado en revisión";
        }
        if ("Rechazado".equals(a.getEstadoAprobacion())) {
            return "Rechazado, en corrección";
        }
        return "Aprobado";
    }

    private static void dato(StringBuilder sb, String nombre, String valor) {
        if (valor != null && !valor.isBlank()) {
            sb.append("<dt>").append(e(nombre)).append("</dt><dd>").append(e(valor)).append("</dd>");
        }
    }

    private static String estadoTexto(String estado) {
        return "En analisis".equals(estado) ? "En análisis" : estado;
    }

    private static String claseEstado(String estado) {
        if (estado == null) {
            return "";
        }
        return switch (estado) {
            // mismos colores que las etiquetas de estado del LIMS (Badges.java)
            case "Aprobada" -> "ok";
            case "Reportada" -> "cerrada";
            case "Rechazada" -> "mal";
            case "Recibida" -> "neutro";
            default -> "proceso";
        };
    }

    private static String numero(BigDecimal v) {
        return v == null ? "—" : v.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static String fechaHora(LocalDateTime f) {
        return f == null ? null : f.format(FECHA_HORA);
    }

    /** Escapa texto para HTML (los datos vienen de la base y pueden tener cualquier carácter). */
    private static String e(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '&' -> sb.append("&amp;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String pagina(String titulo, String cuerpo) {
        return """
                <!doctype html>
                <html lang="es"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta name="robots" content="noindex">
                <title>%s · ConectaGo LIMS</title>
                <style>
                /* verde normal · ámbar anormal (fuera de rango) · rojo falla · azul en curso (IEC 60073) */
                :root{--fondo:#f4f6f5;--tarjeta:#fff;--texto:#1d2b27;--suave:#5f6f6a;--linea:#e1e6e4;
                --verde:#1f7a5a;--verde-f:#e3f3ec;--rojo:#b3261e;--rojo-f:#fbe9e7;--ambar:#8a5a00;--ambar-f:#fdf1dc;
                --azul:#1d5fa8;--azul-f:#e5eef9;--morado:#5b21b6;--morado-f:#ede9fe;--gris:#475569;--gris-f:#e2e8f0}
                @media (prefers-color-scheme:dark){:root{--fondo:#111816;--tarjeta:#1b2421;--texto:#e6ece9;
                --suave:#9aaba5;--linea:#2c3834;--verde:#6fd3a6;--verde-f:#1d3a2f;--rojo:#f2a49d;--rojo-f:#3d2220;
                --ambar:#f0c46b;--ambar-f:#3a2f17;--azul:#8db8ec;--azul-f:#1c2c40;--morado:#c4b5fd;--morado-f:#2e2446;--gris:#cbd5e1;--gris-f:#2c3834}}
                *{box-sizing:border-box}
                body{margin:0;background:var(--fondo);color:var(--texto);
                font:16px/1.45 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}
                header{background:var(--verde);color:#fff;padding:14px 16px;font-weight:600;letter-spacing:.2px}
                @media (prefers-color-scheme:dark){header{background:#16503c}}
                main{max-width:560px;margin:0 auto;padding:12px 16px 24px}
                .tarjeta{background:var(--tarjeta);border:1px solid var(--linea);border-radius:12px;padding:16px;margin:12px 0}
                h1{font-size:22px;margin:0}h2{font-size:17px;margin:0 0 8px}
                .fila-titulo{display:flex;justify-content:space-between;align-items:center;gap:8px;flex-wrap:wrap}
                .suave{color:var(--suave);margin:4px 0 0;font-size:14px}
                dl{display:grid;grid-template-columns:auto 1fr;gap:6px 14px;margin:12px 0 0}
                dt{color:var(--suave);font-size:14px}dd{margin:0;overflow-wrap:anywhere}
                .estado{font-size:13px;font-weight:600;padding:4px 10px;border-radius:999px;background:var(--azul-f);color:var(--azul)}
                .estado.ok{background:var(--verde-f);color:var(--verde)}
                .estado.mal{background:var(--rojo-f);color:var(--rojo)}
                .estado.cerrada{background:var(--morado-f);color:var(--morado)}
                .estado.neutro{background:var(--gris-f);color:var(--gris)}
                .analisis{display:flex;justify-content:space-between;gap:12px;padding:10px 0;border-top:1px solid var(--linea)}
                .analisis:first-of-type{border-top:0}
                .valor{font-weight:600;text-align:right;white-space:nowrap}
                .valor small{display:block;font-weight:500;color:var(--ambar);font-size:12px}
                .valor.fuera{color:var(--ambar)}
                .pie{color:var(--suave);font-size:13px;text-align:center;margin-top:16px}
                </style></head>
                <body><header>ConectaGo LIMS</header><main>%s</main></body></html>
                """.formatted(e(titulo), cuerpo);
    }
}
