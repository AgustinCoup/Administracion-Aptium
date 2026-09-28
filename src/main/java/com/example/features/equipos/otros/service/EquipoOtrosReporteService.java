package com.example.features.equipos.otros.service;

import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.EquipoOtrosReporteDTO;
import com.example.features.equipos.otros.model.MaterialOtros;
import com.example.features.equipos.otros.model.TipoIngresoOtros;
import net.sf.jasperreports.engine.JRException;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EquipoOtrosReporteService {

    private static final Logger log = LoggerFactory.getLogger(EquipoOtrosReporteService.class);
    private static final String JRXML_PATH = "/reports/ReporteOtros.jrxml";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EquipoOtrosService equipoOtrosService;

    public EquipoOtrosReporteService(EquipoOtrosService equipoOtrosService) {
        if (equipoOtrosService == null) throw new IllegalArgumentException("EquipoOtrosService no puede ser nulo");
        this.equipoOtrosService = equipoOtrosService;
    }

    public void generarYMostrarReporte(LocalDate desde, LocalDate hasta, Integer clienteId) {
        try {
            JasperPrint jasperPrint = llenar(construirDatos(desde, hasta, clienteId));

            JasperViewer viewer = new JasperViewer(jasperPrint, false);
            viewer.setTitle("Reporte Equipos Otros  —  " + desde.format(FMT) + "  al  " + hasta.format(FMT));
            viewer.setVisible(true);

        } catch (Exception e) {
            log.error("Error al generar reporte de otros [{} - {}]", desde, hasta, e);
            throw new RuntimeException("No se pudo generar el reporte: " + e.getMessage(), e);
        }
    }

    /** Compila el JRXML y lo llena, sin mostrarlo. Package-private para testear el reporte sin viewer. */
    JasperPrint llenar(List<EquipoOtrosReporteDTO> datos) throws JRException {
        InputStream jrxmlStream = getClass().getResourceAsStream(JRXML_PATH);
        if (jrxmlStream == null) {
            throw new IllegalStateException("No se encontró el archivo de reporte: " + JRXML_PATH);
        }

        JasperReport jasperReport = JasperCompileManager.compileReport(jrxmlStream);

        Map<String, Object> params = new HashMap<>();
        params.put(JRParameter.REPORT_CLASS_LOADER, getClass().getClassLoader());

        return JasperFillManager.fillReport(
            jasperReport, params, new JRBeanCollectionDataSource(datos));
    }

    /**
     * Litros: {@code volumen_equipo}, que acumula sólo los litros de <b>este</b> ingreso
     * ({@code lote_otros_volumenes} es por lote e ingreso, así que un lote compartido no se
     * cuenta entero) y sólo de lotes <b>finalizados OK</b> ({@code LoteDAO.acumularVolumenEquipoOtros}
     * corre al finalizar; un lote fallido no suma). Decisión del usuario.
     */
    List<EquipoOtrosReporteDTO> construirDatos(LocalDate desde, LocalDate hasta, Integer clienteId) {
        List<EquipoOtros> equipos = equipoOtrosService.obtenerEntreFechas(desde, hasta, clienteId);
        List<EquipoOtrosReporteDTO> dtos = new ArrayList<>(equipos.size());

        for (EquipoOtros eq : equipos) {
            String fecha   = eq.getFechaIngreso() != null
                ? eq.getFechaIngreso().toLocalDate().format(FMT) : "";
            String cliente = eq.getClienteNombre() != null ? eq.getClienteNombre() : "—";

            String tipoIngreso;
            if (eq.getTipoIngreso() == TipoIngresoOtros.REMITO) {
                tipoIngreso = "REMITO\n" + (eq.getRemitoId() != null ? eq.getRemitoId() : "");
            } else {
                tipoIngreso = "DETALLES";
            }

            StringBuilder sb = new StringBuilder();
            Set<String> lotesUnicos = new LinkedHashSet<>();
            for (MaterialOtros mat : eq.getMateriales()) {
                sb.append(mat.getDescripcion())
                  .append("  x").append(mat.getCantidad()).append("\n");
                if (mat.getLoteIdNegocio() != null) {
                    lotesUnicos.add(mat.getLoteIdNegocio());
                }
            }
            if (sb.length() == 0 && eq.getTipoIngreso() == TipoIngresoOtros.REMITO
                    && eq.getRemitoCantidad() != null) {
                sb.append("Cantidad: ").append(eq.getRemitoCantidad());
            }
            String materiales = sb.length() > 0 ? sb.toString().stripTrailing() : "(sin materiales)";
            String lotes = lotesUnicos.isEmpty() ? "" : String.join("\n", lotesUnicos);

            dtos.add(new EquipoOtrosReporteDTO(fecha, cliente, tipoIngreso, materiales, lotes,
                eq.getVolumenEquipo()));
        }
        return dtos;
    }
}
