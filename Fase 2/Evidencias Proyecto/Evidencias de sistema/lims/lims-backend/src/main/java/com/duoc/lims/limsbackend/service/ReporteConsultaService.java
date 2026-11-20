package com.duoc.lims.limsbackend.service;

import com.duoc.lims.limsbackend.dto.ReporteResponseDTO;
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
 */
@Service
public class ReporteConsultaService {

    /** PDF guardado en disco más su nombre de archivo, para re-descargar. */
    public record PdfGuardado(byte[] pdf, String nombreArchivo) {}

    private final ReporteRepository reporteRepository;

    public ReporteConsultaService(ReporteRepository reporteRepository) {
        this.reporteRepository = reporteRepository;
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
            throw new IllegalArgumentException(
                    "El archivo del informe ya no está disponible en el servidor que lo emitio");
        }
        try {
            byte[] bytes = Files.readAllBytes(ruta);
            return new PdfGuardado(bytes, ruta.getFileName().toString());
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el archivo del informe", e);
        }
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
