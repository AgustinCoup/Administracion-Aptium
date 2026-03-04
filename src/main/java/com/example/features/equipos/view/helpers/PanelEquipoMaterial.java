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
 * Panel reutilizable que muestra dos tablas: equipos y materiales.
 * Encapsula la lógica común entre PantallaVerCDEv2 y PantallaRegistrarEstado.
 * 
 * Responsabilidad:
 * - Gestionar la visualización de tablas de equipos y materiales
 * - Sincronizar la selección de equipos con la tabla de materiales
 * - Renderizar colores de estados consistentemente
 */
public class PanelEquipoMaterial extends JPanel {
    
    private EquipoTableModel modeloEquipos;
    private MaterialTableModel modeloMateriales;
    private JTable tablaEquipos;
    private JTable tablaMateriales;
    private Consumer<Equipo> onEquipoSeleccionado;
    private Runnable onMaterialSelectionChanged;

    /**
     * Constructor del panel reutilizable.
     * 
     * @param tituloEquipos Título para la sección de equipos (ej: "Equipos / Clientes")
     * @param tituloMateriales Título para la sección de materiales (ej: "Materiales del Equipo")
     * @param materialesEditable Si la tabla de materiales debe permitir selección
     */
    public PanelEquipoMaterial(String tituloEquipos, String tituloMateriales, boolean materialesEditable) {
        setLayout(new GridBagLayout());
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;

        // ===== TABLA DE EQUIPOS =====
        JLabel lblEquipos = LabelFactory.createSectionLabel(tituloEquipos);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weighty = 0;
        add(lblEquipos, gbc);

        String[] columnasEquipos = {
            Constantes.Textos.COLUMNA_CLIENTE,
            Constantes.Textos.COLUMNA_INSTITUCION,
            Constantes.Textos.COLUMNA_ESTADO
        };
        this.modeloEquipos = new EquipoTableModel(columnasEquipos);
        tablaEquipos = new JTable(modeloEquipos);
        TableStyler.applyStandard(tablaEquipos);
        tablaEquipos.getColumnModel().getColumn(2).setCellRenderer(TableStyler.createEstadoRenderer());

        JScrollPane scrollEquipos = new JScrollPane(tablaEquipos);
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weighty = 0.6;
        add(scrollEquipos, gbc);

        // Listener de selección para cargar materiales
        tablaEquipos.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = tablaEquipos.getSelectedRow();
                if (selectedRow >= 0) {
                    Equipo equipoSeleccionado = modeloEquipos.getEquipoAt(selectedRow);
                    modeloMateriales.cargarMateriales(equipoSeleccionado);
                    
                    // Notificar al listener externo si existe
                    if (onEquipoSeleccionado != null && equipoSeleccionado != null) {
                        onEquipoSeleccionado.accept(equipoSeleccionado);
                    }
                } else {
                    modeloMateriales.limpiar();
                }
            }
        });

        // ===== TABLA DE MATERIALES =====
        JLabel lblMateriales = LabelFactory.createSectionLabel(tituloMateriales);
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weighty = 0;
        add(lblMateriales, gbc);

        this.modeloMateriales = new MaterialTableModel();
        tablaMateriales = new JTable(modeloMateriales);
        TableStyler.applyStandard(tablaMateriales);
        tablaMateriales.getColumnModel().getColumn(2).setCellRenderer(TableStyler.createEstadoRenderer());

        // Centrar la columna de Cantidad
        TableStyler.centerColumns(tablaMateriales, 1);

        // Configurar selección de materiales
        tablaMateriales.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onMaterialSelectionChanged();
            }
        });
        if (materialesEditable) {
            tablaMateriales.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        } else {
            tablaMateriales.setEnabled(false);
        }

        JScrollPane scrollMateriales = new JScrollPane(tablaMateriales);
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weighty = 1.0;
        add(scrollMateriales, gbc);
    }

    /**
     * Callback interno cuando cambia la selección de material.
     */
    private void onMaterialSelectionChanged() {
        if (onMaterialSelectionChanged != null) {
            onMaterialSelectionChanged.run();
        }
    }

    /**
     * Actualiza la tabla de equipos con nuevos datos.
     */
    public void actualizarEquipos(List<Equipo> equipos) {
        modeloEquipos.actualizarDatos(equipos);
        modeloMateriales.limpiar();
    }

    /**
     * Obtiene el equipo actualmente seleccionado.
     */
    public Equipo getEquipoSeleccionado() {
        int selectedRow = tablaEquipos.getSelectedRow();
        if (selectedRow >= 0) {
            return modeloEquipos.getEquipoAt(selectedRow);
        }
        return null;
    }

    /**
     * Obtiene el índice del material seleccionado en la tabla.
     */
    public int getMaterialSeleccionadoIndex() {
        return tablaMateriales.getSelectedRow();
    }

    /**
     * Recarga los materiales del equipo actualmente seleccionado.
     * Útil después de modificar el estado de un material.
     */
    public void recargarMateriales() {
        Equipo equipoActual = getEquipoSeleccionado();
        if (equipoActual != null) {
            modeloMateriales.cargarMateriales(equipoActual);
        }
    }

    /**
     * Refresca el estado mostrado de los equipos sin recargar desde BD.
     */
    public void refrescarEstadosEquipos() {
        modeloEquipos.refrescarEstados();
    }

    /**
     * Establece un listener que se ejecuta cuando se selecciona un equipo.
     */
    public void setOnEquipoSeleccionado(Consumer<Equipo> listener) {
        this.onEquipoSeleccionado = listener;
    }

    /**
     * Establece un listener que se ejecuta cuando cambia la selección de material.
     */
    public void setOnMaterialSelectionChanged(Runnable listener) {
        this.onMaterialSelectionChanged = listener;
    }

    /**
     * Limpia la selección de ambas tablas.
     */
    public void limpiarSeleccion() {
        tablaEquipos.clearSelection();
        modeloMateriales.limpiar();
    }

    /**
     * Obtiene la tabla de materiales para agregar listeners adicionales si es necesario.
     */
    public JTable getTablaMateriales() {
        return tablaMateriales;
    }

    /**
     * Obtiene la tabla de equipos para agregar listeners adicionales si es necesario.
     */
    public JTable getTablaEquipos() {
        return tablaEquipos;
    }
}


