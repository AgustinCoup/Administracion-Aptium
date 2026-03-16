package com.example.features.equipos.ortopedias.view.helpers;

import javax.swing.*;
import com.example.common.model.EquipoRegistrableInterface;
import com.example.common.model.MaterialRegistrableInterface;
import com.example.common.constants.Constantes;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;
import com.example.ui.common.LabelFactory;
import com.example.ui.common.TableStyler;

/**
 * Panel reutilizable que muestra dos tablas (equipos y materiales) en un JSplitPane.
 *
 * Refactorizado para trabajar con {@link EquipoRegistrableInterface} e
 * {@link MaterialRegistrableInterface}, de modo que puede mostrar tanto equipos
 * de ortopedia como equipos "otros" sin cambios en los callers.
 *
 * La API pública es retrocompatible: los controllers existentes no necesitan cambios
 * excepto reemplazar el tipo {@code Equipo} por {@code EquipoRegistrableInterface} en sus
 * referencias locales.
 */
public class PanelEquipoMaterial extends JPanel {

    private final EquipoTableModel   modeloEquipos;
    private final MaterialTableModel modeloMateriales;
    private final JTable             tablaEquipos;
    private final JTable             tablaMateriales;

    private Consumer<EquipoRegistrableInterface> onEquipoSeleccionado;
    private Runnable                     onMaterialSelectionChanged;

    public PanelEquipoMaterial(String tituloEquipos, String tituloMateriales,
                               boolean materialesEditable) {
        setLayout(new BorderLayout());

        modeloEquipos    = new EquipoTableModel(new String[]{
            Constantes.Textos.COLUMNA_CLIENTE,
            Constantes.Textos.COLUMNA_INSTITUCION,
            Constantes.Textos.COLUMNA_ESTADO
        });
        modeloMateriales = new MaterialTableModel();

        tablaEquipos = new JTable(modeloEquipos);
        TableStyler.applyStandard(tablaEquipos);
        tablaEquipos.getColumnModel().getColumn(2)
            .setCellRenderer(TableStyler.createEstadoRenderer());

        tablaMateriales = new JTable(modeloMateriales);
        TableStyler.applyStandard(tablaMateriales);
        tablaMateriales.getColumnModel().getColumn(2)
            .setCellRenderer(TableStyler.createEstadoRenderer());
        TableStyler.centerColumns(tablaMateriales, 1);

        if (materialesEditable) {
            tablaMateriales.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        } else {
            tablaMateriales.setEnabled(false);
        }

        tablaEquipos.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = tablaEquipos.getSelectedRow();
                if (row >= 0) {
                    EquipoRegistrableInterface eq = modeloEquipos.getEquipoAt(row);
                    modeloMateriales.cargarMateriales(eq);
                    if (onEquipoSeleccionado != null && eq != null) {
                        onEquipoSeleccionado.accept(eq);
                    }
                } else {
                    modeloMateriales.limpiar();
                }
            }
        });

        tablaMateriales.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) notificarMaterialSelectionChanged();
        });

        JPanel panelEquipos = new JPanel(new BorderLayout(0, 3));
        panelEquipos.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        panelEquipos.add(LabelFactory.createSectionLabel(tituloEquipos), BorderLayout.NORTH);
        panelEquipos.add(new JScrollPane(tablaEquipos), BorderLayout.CENTER);

        JPanel panelMateriales = new JPanel(new BorderLayout(0, 3));
        panelMateriales.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        panelMateriales.add(LabelFactory.createSectionLabel(tituloMateriales), BorderLayout.NORTH);
        panelMateriales.add(new JScrollPane(tablaMateriales), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, panelEquipos, panelMateriales);
        split.setResizeWeight(0.5);
        split.setOneTouchExpandable(true);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);
    }

    private void notificarMaterialSelectionChanged() {
        if (onMaterialSelectionChanged != null) onMaterialSelectionChanged.run();
    }

    // ── API pública ───────────────────────────────────────────────────────────

    public void actualizarEquipos(List<EquipoRegistrableInterface> equipos) {
        modeloEquipos.actualizarDatos(equipos);
        modeloMateriales.limpiar();
    }

    public EquipoRegistrableInterface getEquipoSeleccionado() {
        int row = tablaEquipos.getSelectedRow();
        return row >= 0 ? modeloEquipos.getEquipoAt(row) : null;
    }

    /** Índice del material seleccionado, o -1. */
    public int getMaterialSeleccionadoIndex() {
        return tablaMateriales.getSelectedRow();
    }

    /** Material seleccionado como interfaz, o null. */
    public MaterialRegistrableInterface getMaterialSeleccionado() {
        int row = tablaMateriales.getSelectedRow();
        return row >= 0 ? modeloMateriales.getMaterialAt(row) : null;
    }

    public void recargarMateriales() {
        EquipoRegistrableInterface eq = getEquipoSeleccionado();
        if (eq != null) modeloMateriales.cargarMateriales(eq);
    }

    public void refrescarEstadosEquipos() {
        modeloEquipos.refrescarEstados();
    }

    public void setOnEquipoSeleccionado(Consumer<EquipoRegistrableInterface> listener) {
        this.onEquipoSeleccionado = listener;
    }

    public void setOnMaterialSelectionChanged(Runnable listener) {
        this.onMaterialSelectionChanged = listener;
    }

    public void limpiarSeleccion() {
        tablaEquipos.clearSelection();
        modeloMateriales.limpiar();
    }

    public JTable getTablaMateriales() { return tablaMateriales; }
    public JTable getTablaEquipos()    { return tablaEquipos; }
}