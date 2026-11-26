package com.duoc.lims.limsbackend.service;

import com.duoc.lims.limsbackend.dto.ReporteResponseDTO;
import com.duoc.lims.limsbackend.dto.VerificacionInformeDTO;
import com.duoc.lims.limsbackend.model.Usuario;
import com.duoc.lims.limsbackend.model.Investigador;
import com.duoc.lims.limsbackend.model.Muestra;
import com.duoc.lims.limsbackend.model.Reporte;
import com.duoc.lims.limsbackend.repository.ReporteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Consulta del historial de informes emitidos (RF05). No genera PDF nuevo: lista
 * los reportes ya guardados y re-entrega el PDF que quedó en disco al emitirlos.
 * Si el archivo no está en este equipo, entrega una copia regenerada y marcada como tal.
 */
@Service
public class ReporteConsultaService {

    /** PDF guardado en disco más su nombre de archivo, para re-descargar. */
    public record PdfGuardado(byte[] pdf, String nombreArchivo) {}

    private final ReporteRepository reporteRepository;
    private final ReportePdfService reportePdfService;

    public ReporteConsultaService(ReporteRepository reporteRepository, ReportePdfService reportePdfService) {
        this.reporteRepository = reporteRepository;
        this.reportePdfService = reportePdfService;
    }

    @Transactional(readOnly = true)
    public List<ReporteResponseDTO> listar() {
        return reporteRepository.findAllByOrderByFechaGeneracionDesc()
                .stream()
                .map(this::aDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public PdfGuardado obtenerPdf(Integer idReporte) {
        Reporte reporte = reporteRepository.findById(idReporte)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el informe con id " + idReporte));

        Path ruta = Path.of(reporte.getRutaArchivo());
        if (!Files.exists(ruta)) {
            // El archivo no está en este equipo: se entrega una copia regenerada (marcada como tal).
            String nombre = "Informe_" + reporte.getMuestra().getCodigoUnico() + "-v" + reporte.getVersion()
                    + "-copia.pdf";
            return new PdfGuardado(reportePdfService.reconstruir(reporte), nombre);
        }
        try {
            byte[] bytes = Files.readAllBytes(ruta);
            return new PdfGuardado(bytes, ruta.getFileName().toString());
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el archivo del informe", e);
        }
    }

    // ---------- v7: verificación de informes firmados ----------

    /** Busca el informe por el código impreso en su recuadro de firma. */
    @Transactional(readOnly = true)
    public VerificacionInformeDTO verificarCodigo(String codigo) {
        String texto = codigo == null ? "" : codigo.trim().toUpperCase();
        String limpio = texto.length() == 8 && texto.indexOf('-') < 0
                ? texto.substring(0, 4) + "-" + texto.substring(4)   // acepta el código sin guion
                : texto;
        return reporteRepository.findByCodigoVerificacion(limpio)
                .map(r -> verificacion(true, "Código válido: corresponde a un informe emitido por el laboratorio. "
                        + "Compare el número, la fecha y el emisor con los del documento que tiene.", r))
                .orElseGet(() -> new VerificacionInformeDTO(false,
                        "El código " + limpio + " no corresponde a ningún informe emitido por este laboratorio.",
                        null, null, null, null, null));
    }

    /** Compara la huella SHA-256 del archivo con la de los informes emitidos: detecta cualquier modificación. */
    @Transactional(readOnly = true)
    public VerificacionInformeDTO verificarArchivo(byte[] pdf) {
        if (pdf == null || pdf.length == 0) {
            throw new IllegalArgumentException("El archivo está vacío");
        }
        return reporteRepository.findFirstByHashSha256(FirmaDigitalService.sha256(pdf))
                .map(r -> verificacion(true, "Archivo auténtico: es idéntico al informe firmado que emitió el "
                        + "laboratorio y no fue modificado.", r))
                .orElseGet(() -> new VerificacionInformeDTO(false,
                        "El archivo NO coincide con ningún informe firmado: fue modificado, es una copia regenerada "
                                + "o se emitió antes de la firma digital (v7).", null, null, null, null, null));
    }

    private VerificacionInformeDTO verificacion(boolean ok, String mensaje, Reporte r) {
        Usuario u = r.getUsuarioGenerador();
        return new VerificacionInformeDTO(ok, mensaje,
                r.getMuestra().getCodigoUnico() + "-v" + r.getVersion(), r.getMuestra().getCodigoUnico(),
                u.getNombre() + " " + u.getApellido() + " (" + u.getRol().getNombreRol() + ")",
                r.getFechaGeneracion(), r.getCodigoVerificacion());
    }

    private ReporteResponseDTO aDTO(Reporte r) {
        Muestra m = r.getMuestra();
        String numero = m.getCodigoUnico() + "-v" + r.getVersion();

        Investigador inv = m.getInvestigador();
        String investigador = (inv == null) ? null : inv.getNombre() + " " + inv.getApellido();

        String emisor = r.getUsuarioGenerador().getNombre() + " " + r.getUsuarioGenerador().getApellido();

        return new ReporteResponseDTO(
                r.getId(),
                numero,
                m.getId(),
                m.getCodigoUnico(),
                m.getTipoMuestra().getValorDb(),
                investigador,
                r.getVersion(),
                emisor,
                r.getFechaGeneracion()
        );
    }
}
