package com.example.features.equipos.view.helpers;

import javax.swing.*;
import com.example.features.equipos.model.Equipo;
import com.example.common.constants.Constantes;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;
import com.example.ui.common.LabelFactory;
import com.example.ui.common.TableStyler;

/**
 * Panel reutilizable que muestra dos tablas (equipos y materiales) separadas por un
 * {@link JSplitPane} vertical redimensionable.
 *
 * El espacio se divide 50/50 por defecto ({@code resizeWeight = 0.5}), de modo que
 * ambas tablas crecen y se encogen simétricamente al cambiar el tamaño de la ventana.
 * El usuario puede arrastrar el divisor para ajustar la proporción manualmente.
 *
 * API pública idéntica a la versión anterior: los controllers que usan este panel
 * no necesitan ningún cambio.
 */
public class PanelEquipoMaterial extends JPanel {

    private final EquipoTableModel   modeloEquipos;
    private final MaterialTableModel modeloMateriales;
    private final JTable             tablaEquipos;
    private final JTable             tablaMateriales;

    private Consumer<Equipo> onEquipoSeleccionado;
    private Runnable         onMaterialSelectionChanged;

    /**
     * @param tituloEquipos      Título para la sección de equipos (ej: "Equipos / Clientes")
     * @param tituloMateriales   Título para la sección de materiales
     * @param materialesEditable Si true, la tabla de materiales permite selección de filas
     */
    public PanelEquipoMaterial(String tituloEquipos, String tituloMateriales,
                               boolean materialesEditable) {
        setLayout(new BorderLayout());

        // ── 1. Crear modelos primero ──────────────────────────────────────────
        // Es importante que los modelos existan antes de crear las tablas
        // y antes de registrar cualquier listener que los referencie.
        modeloEquipos    = new EquipoTableModel(new String[]{
            Constantes.Textos.COLUMNA_CLIENTE,
            Constantes.Textos.COLUMNA_INSTITUCION,
            Constantes.Textos.COLUMNA_ESTADO
        });
        modeloMateriales = new MaterialTableModel();

        // ── 2. Crear tablas ───────────────────────────────────────────────────
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

        // ── 3. Registrar listeners (modelos y tablas ya existen) ──────────────
        tablaEquipos.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = tablaEquipos.getSelectedRow();
                if (row >= 0) {
                    Equipo eq = modeloEquipos.getEquipoAt(row);
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
            if (!e.getValueIsAdjusting()) {
                notificarMaterialSelectionChanged();
            }
        });

        // ── 4. Armar paneles con etiquetas ────────────────────────────────────
        JPanel panelEquipos = new JPanel(new BorderLayout(0, 3));
        panelEquipos.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        panelEquipos.add(LabelFactory.createSectionLabel(tituloEquipos), BorderLayout.NORTH);
        panelEquipos.add(new JScrollPane(tablaEquipos), BorderLayout.CENTER);

        JPanel panelMateriales = new JPanel(new BorderLayout(0, 3));
        panelMateriales.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        panelMateriales.add(LabelFactory.createSectionLabel(tituloMateriales), BorderLayout.NORTH);
        panelMateriales.add(new JScrollPane(tablaMateriales), BorderLayout.CENTER);

        // ── 5. JSplitPane proporcional 50/50 ──────────────────────────────────
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, panelEquipos, panelMateriales);
        split.setResizeWeight(0.5);        // ambas mitades crecen simétricamente
        split.setOneTouchExpandable(true); // flechitas para colapsar rápido
        split.setBorder(null);

        add(split, BorderLayout.CENTER);
    }

    // ── Callbacks internos ───────────────────────────────────────────────────

    private void notificarMaterialSelectionChanged() {
        if (onMaterialSelectionChanged != null) onMaterialSelectionChanged.run();
    }

    // ── API pública ──────────────────────────────────────────────────────────

    /** Actualiza la tabla de equipos con nuevos datos y limpia la de materiales. */
    public void actualizarEquipos(List<Equipo> equipos) {
        modeloEquipos.actualizarDatos(equipos);
        modeloMateriales.limpiar();
    }

    /** Obtiene el equipo actualmente seleccionado, o null si no hay selección. */
    public Equipo getEquipoSeleccionado() {
        int row = tablaEquipos.getSelectedRow();
        return row >= 0 ? modeloEquipos.getEquipoAt(row) : null;
    }

    /** Obtiene el índice de fila del material seleccionado, o -1 si no hay selección. */
    public int getMaterialSeleccionadoIndex() {
        return tablaMateriales.getSelectedRow();
    }

    /** Recarga los materiales del equipo actualmente seleccionado. */
    public void recargarMateriales() {
        Equipo eq = getEquipoSeleccionado();
        if (eq != null) modeloMateriales.cargarMateriales(eq);
    }

    /** Refresca visualmente el estado de los equipos sin recargar desde BD. */
    public void refrescarEstadosEquipos() {
        modeloEquipos.refrescarEstados();
    }

    /** Registra un listener que se ejecuta al seleccionar un equipo. */
    public void setOnEquipoSeleccionado(Consumer<Equipo> listener) {
        this.onEquipoSeleccionado = listener;
    }

    /** Registra un listener que se ejecuta al cambiar la selección de material. */
    public void setOnMaterialSelectionChanged(Runnable listener) {
        this.onMaterialSelectionChanged = listener;
    }

    /** Limpia la selección de ambas tablas. */
    public void limpiarSeleccion() {
        tablaEquipos.clearSelection();
        modeloMateriales.limpiar();
    }

    public JTable getTablaMateriales() { return tablaMateriales; }
    public JTable getTablaEquipos()    { return tablaEquipos; }
}