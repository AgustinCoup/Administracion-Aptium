package com.example.features.lotes.service;

import com.example.features.lotes.model.Lote;
import com.example.features.lotes.model.LoteReporteDTO;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.view.JasperViewer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio responsable de:
 * 1. Consultar los datos de lotes en el rango pedido (via LoteService).
 * 2. Armar la lista de {@link LoteReporteDTO} (una fila por lote,
 *    materiales y clientes concatenados).
 * 3. Compilar el .jrxml, llenarlo con los datos y abrir JasperViewer.
 *
 * JasperViewer ya incluye botones para imprimir físicamente y exportar a PDF.
 */
public class LoteReporteService {

    private static final Logger log = LoggerFactory.getLogger(LoteReporteService.class);

    /** Ruta del .jrxml dentro del classpath (src/main/resources/reports/). */
    private static final String JRXML_PATH = "/reports/ReporteLotes.jrxml";

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LoteService loteService;

    public LoteReporteService(LoteService loteService) {
        if (loteService == null) throw new IllegalArgumentException("LoteService no puede ser nulo");
        this.loteService = loteService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // API pública
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Genera el reporte para el rango [desde, hasta] y lo muestra en JasperViewer.
     * Debe llamarse desde el Event Dispatch Thread (EDT).
     *
     * @throws RuntimeException si ocurre cualquier error al compilar o llenar el reporte.
     */
    public void generarYMostrarReporte(LocalDate desde, LocalDate hasta) {
        try {
            List<LoteReporteDTO> datos = construirDatos(desde, hasta);

            // Compilar el .jrxml desde el classpath
            InputStream jrxmlStream = getClass().getResourceAsStream(JRXML_PATH);
            if (jrxmlStream == null) {
                throw new IllegalStateException(
                    "No se encontró el archivo de reporte en el classpath: " + JRXML_PATH +
                    "\nAsegurate de colocar ReporteLotes.jrxml en src/main/resources/reports/");
            }

            JasperReport jasperReport = JasperCompileManager.compileReport(jrxmlStream);

            // Parámetros del reporte
            Map<String, Object> params = new HashMap<>();
            // Permite que JasperReports resuelva imágenes (ej. Aptium_logo.png)
            // desde el classpath. El logo debe estar en src/main/resources/
            params.put(JRParameter.REPORT_CLASS_LOADER, getClass().getClassLoader());

            // Origen de datos: lista de beans que matchean los $F{...} del JRXML
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(datos);

            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, params, dataSource);

            // Abrir el visor (false = no cerrar la app al cerrar el visor)
            JasperViewer viewer = new JasperViewer(jasperPrint, false);
            viewer.setTitle("Reporte de Lotes  —  "
                + desde.format(FMT) + "  al  " + hasta.format(FMT));
            viewer.setVisible(true);

        } catch (Exception e) {
            log.error("Error al generar reporte de lotes [{} - {}]", desde, hasta, e);
            throw new RuntimeException("No se pudo generar el reporte: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Ensamblado de DTOs
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Construye la lista de DTOs consultando lotes, materiales y clientes.
     * Cada lote produce UNA fila; materiales y clientes van concatenados.
     */
    private List<LoteReporteDTO> construirDatos(LocalDate desde, LocalDate hasta) {
        List<Lote> lotes = loteService.obtenerLotesEnRango(desde, hasta);
        List<LoteReporteDTO> dtos = new ArrayList<>(lotes.size());

        for (Lote lote : lotes) {
            String fechaStr = lote.getFechaInicio() != null
                ? lote.getFechaInicio().toLocalDate().format(FMT) : "";

            Map<String, List<String>> ortopedias =
                loteService.obtenerMaterialesPorClientePorLote(lote.getId());
            Map<String, List<String>> otros =
                loteService.obtenerOtrosPorClientePorLote(lote.getId());

            String detalles;
            if (ortopedias.isEmpty() && otros.isEmpty()) {
                detalles = "(sin materiales)";
            } else {
                StringBuilder sb = new StringBuilder();
                if (!ortopedias.isEmpty()) {
                    sb.append("— Ortopedias —\n");
                    for (Map.Entry<String, List<String>> entry : ortopedias.entrySet()) {
                        sb.append(entry.getKey()).append(":\n");
                        for (String mat : entry.getValue()) sb.append("  • ").append(mat).append("\n");
                        sb.append("\n");
                    }
                }
                if (!otros.isEmpty()) {
                    sb.append("— Otros —\n");
                    for (Map.Entry<String, List<String>> entry : otros.entrySet()) {
                        sb.append(entry.getKey()).append(":\n");
                        for (String mat : entry.getValue()) sb.append("  • ").append(mat).append("\n");
                        sb.append("\n");
                    }
                }
                detalles = sb.toString().stripTrailing();
            }

            dtos.add(new LoteReporteDTO(
                fechaStr, lote.getIdNegocio(), lote.getAutoclaveNombre(), detalles));
        }
        return dtos;
    }
}