package com.example.features.equipos.otros.service;

import com.example.app.AppModel;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.EquipoOtrosReporteDTO;
import com.example.features.equipos.otros.model.MaterialOtros;
import com.example.features.equipos.otros.model.TipoIngresoOtros;
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

public class EquipoOtrosReporteService {

    private static final Logger log = LoggerFactory.getLogger(EquipoOtrosReporteService.class);
    private static final String JRXML_PATH = "/reports/ReporteOtros.jrxml";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AppModel model;

    public EquipoOtrosReporteService(AppModel model) {
        if (model == null) throw new IllegalArgumentException("AppModel no puede ser nulo");
        this.model = model;
    }

    public void generarYMostrarReporte(LocalDate desde, LocalDate hasta) {
        try {
            List<EquipoOtrosReporteDTO> datos = construirDatos(desde, hasta);

            InputStream jrxmlStream = getClass().getResourceAsStream(JRXML_PATH);
            if (jrxmlStream == null) {
                throw new IllegalStateException("No se encontró el archivo de reporte: " + JRXML_PATH);
            }

            JasperReport jasperReport = JasperCompileManager.compileReport(jrxmlStream);

            Map<String, Object> params = new HashMap<>();
            params.put(JRParameter.REPORT_CLASS_LOADER, getClass().getClassLoader());

            JasperPrint jasperPrint = JasperFillManager.fillReport(
                jasperReport, params, new JRBeanCollectionDataSource(datos));

            JasperViewer viewer = new JasperViewer(jasperPrint, false);
            viewer.setTitle("Reporte Equipos Otros  —  " + desde.format(FMT) + "  al  " + hasta.format(FMT));
            viewer.setVisible(true);

        } catch (Exception e) {
            log.error("Error al generar reporte de otros [{} - {}]", desde, hasta, e);
            throw new RuntimeException("No se pudo generar el reporte: " + e.getMessage(), e);
        }
    }

    private List<EquipoOtrosReporteDTO> construirDatos(LocalDate desde, LocalDate hasta) {
        List<EquipoOtros> equipos = model.obtenerEquiposOtrosEntreFechas(desde, hasta);
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
            for (MaterialOtros mat : eq.getMateriales()) {
                sb.append(mat.getDescripcion())
                  .append("  x").append(mat.getCantidad()).append("\n");
            }
            if (sb.length() == 0 && eq.getTipoIngreso() == TipoIngresoOtros.REMITO
                    && eq.getRemitoCantidad() != null) {
                sb.append("Cantidad: ").append(eq.getRemitoCantidad());
            }
            String materiales = sb.length() > 0 ? sb.toString().stripTrailing() : "(sin materiales)";

            dtos.add(new EquipoOtrosReporteDTO(fecha, cliente, tipoIngreso, materiales));
        }
        return dtos;
    }
}
