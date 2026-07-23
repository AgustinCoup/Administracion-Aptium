package com.example.features.lotes.controller;

import com.example.app.ui.DatosRefresco;
import com.example.common.util.AbstractFilterController;
import com.example.common.util.FilterStrategy;
import com.example.features.autoclaves.model.Autoclave;
import com.example.features.lotes.controller.helpers.LotesFilterCriteria;
import com.example.features.lotes.controller.helpers.LotesFilterStrategy;
import com.example.features.lotes.model.Lote;
import com.example.features.lotes.service.LoteReporteService;
import com.example.features.lotes.view.helpers.ImprimirLotesDialog;
import com.example.features.lotes.view.PantallaVerLotes;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class VerLotesController extends AbstractFilterController<Lote> {

    private final PantallaVerLotes                  panel;
    private final FilterStrategy<Lote, LotesFilterCriteria> filterStrategy;
    private final LoteReporteService                reporteService;

    /** Alcance: pintar la grilla desde el refresco global, más la impresión del reporte. */
    public VerLotesController(PantallaVerLotes panel,
                              LoteReporteService reporteService,
                              Runnable solicitarRefresco) {
        this.panel            = panel;
        this.filterStrategy   = new LotesFilterStrategy();
        this.reporteService   = reporteService;
        Objects.requireNonNull(solicitarRefresco, "solicitarRefresco");

        this.panel.setOnFiltrosChanged(this::aplicarFiltros);
        this.panel.setOnImprimir(this::abrirDialogoImprimir);

        this.panel.addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent e) { solicitarRefresco.run(); }
        });
    }

    /** Vuelca el snapshot a la grilla y al combo de autoclaves. Sin I/O. */
    public void pintar(DatosRefresco datos) {
        List<String> autoclaves = datos.autoclaves().stream()
            .map(Autoclave::getNombre)
            .sorted(String::compareToIgnoreCase)
            .collect(Collectors.toList());
        panel.setEquiposFiltro(autoclaves);

        recargarCache(datos.todosLosLotes());
    }

    @Override
    protected void aplicarFiltros() {
        LotesFilterCriteria criteria = new LotesFilterCriteria(
            panel.getFiltroId(),
            panel.getFiltroAutoclaves(),
            panel.getFiltroEstados(),
            panel.getFiltroFechaDesde(),
            panel.getFiltroFechaHasta()
        );

        List<Lote> filtrados = filterStrategy.filter(getCache(), criteria);
        panel.actualizarLotes(filtrados);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lógica de impresión
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Obtiene la ventana padre y abre el diálogo de rango de fechas.
     */
    private void abrirDialogoImprimir() {
        Frame parent = (Frame) SwingUtilities.getWindowAncestor(panel);
        ImprimirLotesDialog dialog = new ImprimirLotesDialog(parent, this::generarReporte);
        dialog.setVisible(true);
    }

    /**
     * Llamado por el diálogo cuando el usuario confirmó fechas válidas.
     * Genera el reporte y lo muestra en JasperViewer.
     * En caso de error muestra un JOptionPane con el mensaje.
     */
    private void generarReporte(LocalDate desde, LocalDate hasta) {
        try {
            // Ejecutar fuera del EDT para no bloquear la UI durante la compilación
            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() {
                    reporteService.generarYMostrarReporte(desde, hasta);
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get(); // re-lanza excepciones del worker
                    } catch (Exception e) {
                        mostrarErrorReporte(e.getCause() != null ? e.getCause() : e);
                    }
                }
            };
            worker.execute();

        } catch (Exception e) {
            mostrarErrorReporte(e);
        }
    }

    private void mostrarErrorReporte(Throwable e) {
        JOptionPane.showMessageDialog(
            SwingUtilities.getWindowAncestor(panel),
            "No se pudo generar el reporte:\n" + e.getMessage(),
            "Error en reporte",
            JOptionPane.ERROR_MESSAGE);
    }
}