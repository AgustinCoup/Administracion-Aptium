package com.example.features.equipos.controller;

import com.example.app.AppModel;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.service.EquipoReporteService;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.TipoIngresoOtros;
import com.example.features.equipos.otros.service.EquipoOtrosReporteService;
import com.example.features.equipos.view.PantallaVerEquipos;
import com.example.features.equipos.view.helpers.DetalleOrtopediaDialog;
import com.example.features.equipos.view.helpers.DetalleOtrosDialog;
import com.example.features.equipos.view.helpers.ImprimirEquiposDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;


public class VerEquiposController {

    private static final Logger log = LoggerFactory.getLogger(VerEquiposController.class);

    private final PantallaVerEquipos       panel;
    private final AppModel                 model;
    private final EquipoReporteService     equipoReporteService;
    private final EquipoOtrosReporteService equipoOtrosReporteService;

    private List<Equipo>      todosOrtopedia = List.of();
    private List<EquipoOtros> todosOtros     = List.of();
    private boolean           cargado        = false;

    public VerEquiposController(PantallaVerEquipos panel, AppModel model,
                                EquipoReporteService equipoReporteService,
                                EquipoOtrosReporteService equipoOtrosReporteService) {
        this.panel                   = panel;
        this.model                   = model;
        this.equipoReporteService    = equipoReporteService;
        this.equipoOtrosReporteService = equipoOtrosReporteService;

        panel.setOnImprimirOrtopedias(this::abrirDialogoOrtopedias);
        panel.setOnImprimirOtros(this::abrirDialogoOtros);
        panel.configurarFiltros(this::aplicarFiltros);

        panel.addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent e) {
                cargarDatos();
            }
        });

        panel.getTablaOrtopedias().addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) abrirDetalleOrtopedia();
            }
        });

        panel.getTablaOtros().addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) abrirDetalleOtros();
            }
        });
    }

    // ── Carga de datos ────────────────────────────────────────────────────────

    public void cargarDatos() {
        new Thread(() -> {
            try {
                List<Equipo>      ortopedia = model.obtenerTodosLosEquipos();
                List<EquipoOtros> otros     = model.obtenerTodosLosEquiposOtros();
                SwingUtilities.invokeLater(() -> {
                    todosOrtopedia = ortopedia;
                    todosOtros     = otros;
                    cargado        = true;
                    aplicarFiltros();
                    log.info("Ver equipos: {} ortopedia, {} otros", ortopedia.size(), otros.size());
                });
            } catch (Exception ex) {
                log.error("Error al cargar equipos para vista", ex);
            }
        }, "ver-equipos-loader").start();
    }

    // ── Filtrado ──────────────────────────────────────────────────────────────

    private void aplicarFiltros() {
        if (!cargado) return;

        List<String> estados      = panel.getCmbEstados().getSelectedItems();
        String       cliente      = panel.getTxtCliente().getText().trim().toLowerCase();
        String       profesional  = panel.getTxtProfesional().getText().trim().toLowerCase();
        String       paciente     = panel.getTxtPaciente().getText().trim().toLowerCase();
        String       institucion  = panel.getTxtInstitucion().getText().trim().toLowerCase();
        List<String> tiposIngreso = panel.getCmbTipoIngreso().getSelectedItems();
        LocalDate    desde        = toLocalDate(panel.getDateDesde().getDate());
        LocalDate    hasta        = toLocalDate(panel.getDateHasta().getDate());

        List<Equipo> filtradosOrt = todosOrtopedia.stream()
            .filter(e -> cumpleEstado(e.getEstado(), estados))
            .filter(e -> cumpleTexto(e.getClienteNombre(),   cliente))
            .filter(e -> cumpleTexto(e.getProfesionalNombre(), profesional))
            .filter(e -> cumpleTexto(e.getPacienteNombre(),  paciente))
            .filter(e -> cumpleTexto(e.getInstitucionNombre(), institucion))
            .filter(e -> cumpleFecha(e.getFechaIngreso(), desde, hasta))
            .collect(Collectors.toList());

        List<EquipoOtros> filtradosOtros = todosOtros.stream()
            .filter(e -> cumpleEstado(e.getEstado(), estados))
            .filter(e -> cumpleTexto(e.getClienteNombre(), cliente))
            .filter(e -> cumpleTipoIngreso(e.getTipoIngreso(), tiposIngreso))
            .filter(e -> cumpleFecha(e.getFechaIngreso(), desde, hasta))
            .collect(Collectors.toList());

        panel.setDatosOrtopedia(filtradosOrt);
        panel.setDatosOtros(filtradosOtros);
    }

    private boolean cumpleEstado(EstadoEquipo estado, List<String> seleccionados) {
        return seleccionados.isEmpty() || seleccionados.contains(estado.getNombre());
    }

    private boolean cumpleTexto(String campo, String filtro) {
        if (filtro.isEmpty()) return true;
        return campo != null && campo.toLowerCase().contains(filtro);
    }

    private boolean cumpleTipoIngreso(TipoIngresoOtros tipo, List<String> seleccionados) {
        return seleccionados.isEmpty() || seleccionados.contains(tipo.getNombre());
    }

    private boolean cumpleFecha(LocalDateTime fecha, LocalDate desde, LocalDate hasta) {
        if (fecha == null) return desde == null && hasta == null;
        LocalDate dia = fecha.toLocalDate();
        if (desde != null && dia.isBefore(desde)) return false;
        if (hasta != null && dia.isAfter(hasta))  return false;
        return true;
    }

    private LocalDate toLocalDate(Date date) {
        if (date == null) return null;
        return date.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
    }

    // ── Detalle ───────────────────────────────────────────────────────────────

    private void abrirDetalleOrtopedia() {
        int viewRow = panel.getTablaOrtopedias().getSelectedRow();
        if (viewRow < 0) return;
        int modelRow = panel.getTablaOrtopedias().convertRowIndexToModel(viewRow);
        Equipo equipo = panel.getEquipoOrtopediaAt(modelRow);
        if (equipo == null) return;
        Window ventana = SwingUtilities.getWindowAncestor(panel);
        new DetalleOrtopediaDialog(ventana, equipo).setVisible(true);
    }

    private void abrirDetalleOtros() {
        int viewRow = panel.getTablaOtros().getSelectedRow();
        if (viewRow < 0) return;
        int modelRow = panel.getTablaOtros().convertRowIndexToModel(viewRow);
        EquipoOtros equipo = panel.getEquipoOtrosAt(modelRow);
        if (equipo == null) return;
        EquipoOtros equipoConMateriales = model.obtenerEquipoOtrosPorId(equipo.getId());
        if (equipoConMateriales == null) return;
        Window ventana = SwingUtilities.getWindowAncestor(panel);
        new DetalleOtrosDialog(ventana, equipoConMateriales).setVisible(true);
    }

    // ── Impresión ─────────────────────────────────────────────────────────────

    private void abrirDialogoOrtopedias() {
        Frame ventana = (Frame) SwingUtilities.getWindowAncestor(panel);
        new ImprimirEquiposDialog(ventana, "Imprimir Reporte de Ortopedias", (desde, hasta) ->
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() {
                    equipoReporteService.generarYMostrarReporte(desde, hasta);
                    return null;
                }
                @Override protected void done() {
                    try { get(); } catch (InterruptedException | ExecutionException ex) {
                        log.error("Error al generar reporte de ortopedias", ex);
                        JOptionPane.showMessageDialog(panel,
                            "Error al generar el reporte:\n" + ex.getCause().getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute()
        ).setVisible(true);
    }

    private void abrirDialogoOtros() {
        Frame ventana = (Frame) SwingUtilities.getWindowAncestor(panel);
        new ImprimirEquiposDialog(ventana, "Imprimir Reporte de Otros", (desde, hasta) ->
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() {
                    equipoOtrosReporteService.generarYMostrarReporte(desde, hasta);
                    return null;
                }
                @Override protected void done() {
                    try { get(); } catch (InterruptedException | ExecutionException ex) {
                        log.error("Error al generar reporte de otros", ex);
                        JOptionPane.showMessageDialog(panel,
                            "Error al generar el reporte:\n" + ex.getCause().getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute()
        ).setVisible(true);
    }
}
