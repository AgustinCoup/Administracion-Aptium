package com.example.features.equipos.ortopedias.service;

import com.example.app.AppModel;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.EquipoReporteDTO;
import com.example.features.equipos.ortopedias.model.Material;
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

public class EquipoReporteService {

    private static final Logger log = LoggerFactory.getLogger(EquipoReporteService.class);
    private static final String JRXML_PATH = "/reports/ReporteOrtopedias.jrxml";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AppModel model;

    public EquipoReporteService(AppModel model) {
        if (model == null) throw new IllegalArgumentException("AppModel no puede ser nulo");
        this.model = model;
    }

    public void generarYMostrarReporte(LocalDate desde, LocalDate hasta, Integer clienteId, Integer institucionId) {
        try {
            List<EquipoReporteDTO> datos = construirDatos(desde, hasta, clienteId, institucionId);

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
            viewer.setTitle("Reporte Ortopedias  —  " + desde.format(FMT) + "  al  " + hasta.format(FMT));
            viewer.setVisible(true);

        } catch (Exception e) {
            log.error("Error al generar reporte de ortopedias [{} - {}]", desde, hasta, e);
            throw new RuntimeException("No se pudo generar el reporte: " + e.getMessage(), e);
        }
    }

    private List<EquipoReporteDTO> construirDatos(LocalDate desde, LocalDate hasta, Integer clienteId, Integer institucionId) {
        List<Equipo> equipos = model.obtenerEquiposEntreFechas(desde, hasta, clienteId, institucionId);
        List<EquipoReporteDTO> dtos = new ArrayList<>(equipos.size());

        for (Equipo eq : equipos) {
            String fecha = eq.getFechaIngreso() != null
                ? eq.getFechaIngreso().toLocalDate().format(FMT) : "";

            String cliente     = eq.getClienteNombre()     != null ? eq.getClienteNombre()     : "—";
            String profesional = eq.getProfesionalNombre() != null ? eq.getProfesionalNombre() : "—";
            String paciente    = eq.getPacienteNombre()    != null ? eq.getPacienteNombre()    : "—";
            String institucion = eq.getInstitucionNombre() != null ? eq.getInstitucionNombre() : "—";

            StringBuilder sb = new StringBuilder();
            Set<String> lotesUnicos = new LinkedHashSet<>();
            for (Material mat : eq.getMateriales()) {
                sb.append(mat.getCodigo())
                  .append(" - ").append(mat.getDescripcion())
                  .append("  x").append(mat.getCantidad()).append("\n");
                if (mat.getLoteIdNegocio() != null) {
                    lotesUnicos.add(mat.getLoteIdNegocio());
                }
            }
            String materiales = sb.length() > 0 ? sb.toString().stripTrailing() : "(sin materiales)";
            String lotes = lotesUnicos.isEmpty() ? "" : String.join("\n", lotesUnicos);

            dtos.add(new EquipoReporteDTO(fecha, cliente, profesional, paciente, institucion, materiales, lotes));
        }
        return dtos;
    }
}
