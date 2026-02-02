package com.example.view.helpers;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import com.example.model.Equipo;
import com.example.model.EstadoEquipo;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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
    private Map<String, Color> estadoColores;
    private JTable tablaEquipos;
    private JTable tablaMateriales;
    private Consumer<Equipo> onEquipoSeleccionado;

    /**
     * Constructor del panel reutilizable.
     * 
     * @param tituloEquipos Título para la sección de equipos (ej: "Equipos / Clientes")
     * @param tituloMateriales Título para la sección de materiales (ej: "Materiales del Equipo")
     * @param materialesEditable Si la tabla de materiales debe permitir selección
     */
    public PanelEquipoMaterial(String tituloEquipos, String tituloMateriales, boolean materialesEditable) {
        setLayout(new GridBagLayout());
        
        // Mapa de colores por estado
        estadoColores = new HashMap<>();
        for (EstadoEquipo estado : EstadoEquipo.values()) {
            estadoColores.put(estado.getNombre(), estado.getColor());
        }

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;

        // ===== TABLA DE EQUIPOS =====
        JLabel lblEquipos = new JLabel(tituloEquipos);
        lblEquipos.setFont(new Font("Arial", Font.BOLD, 16));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weighty = 0;
        add(lblEquipos, gbc);

        String[] columnasEquipos = {"Cliente", "Institución", "Estado"};
        this.modeloEquipos = new EquipoTableModel(columnasEquipos);
        tablaEquipos = new JTable(modeloEquipos);
        tablaEquipos.setFont(Estilos.Fuentes.TABLA_CONTENIDO);
        tablaEquipos.setRowHeight(Estilos.Dimensiones.ALTURA_FILA_TABLA);
        tablaEquipos.getTableHeader().setFont(Estilos.Fuentes.TABLA_ENCABEZADO);
        tablaEquipos.getColumnModel().getColumn(2).setCellRenderer(crearRendererEstado());

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
        JLabel lblMateriales = new JLabel(tituloMateriales);
        lblMateriales.setFont(new Font("Arial", Font.BOLD, 16));
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weighty = 0;
        add(lblMateriales, gbc);

        this.modeloMateriales = new MaterialTableModel();
        tablaMateriales = new JTable(modeloMateriales);
        tablaMateriales.setFont(Estilos.Fuentes.TABLA_CONTENIDO);
        tablaMateriales.setRowHeight(Estilos.Dimensiones.ALTURA_FILA_TABLA);
        tablaMateriales.getTableHeader().setFont(Estilos.Fuentes.TABLA_ENCABEZADO);
        tablaMateriales.getColumnModel().getColumn(2).setCellRenderer(crearRendererEstado());

        // Centrar la columna de Cantidad
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        tablaMateriales.getColumnModel().getColumn(1).setCellRenderer(centerRenderer);

        // Configurar selección de materiales
        if (materialesEditable) {
            tablaMateriales.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        } else {
            tablaMateriales.setEnabled(false);
        }

        JScrollPane scrollMateriales = new JScrollPane(tablaMateriales);
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weighty = 0.4;
        add(scrollMateriales, gbc);
    }

    /**
     * Crea el renderer para colorear celdas de estado.
     */
    private DefaultTableCellRenderer crearRendererEstado() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                
                if (value != null) {
                    String estado = value.toString();
                    Color color = estadoColores.getOrDefault(estado, Color.WHITE);
                    c.setBackground(color);
                    c.setForeground(Estilos.Colores.TEXTO_NORMAL);
                    setHorizontalAlignment(SwingConstants.CENTER);
                    setFont(Estilos.Fuentes.TABLA_ENFASIS);
                }
                
                return c;
            }
        };
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
     * Establece un listener que se ejecuta cuando se selecciona un equipo.
     */
    public void setOnEquipoSeleccionado(Consumer<Equipo> listener) {
        this.onEquipoSeleccionado = listener;
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
