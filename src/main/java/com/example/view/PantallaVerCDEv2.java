package com.example.view;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;

import com.example.constants.Constantes;
import com.example.model.Equipo;
import com.example.model.EstadoEquipo;

import java.awt.*;
import java.util.*;
import java.util.List;

public class PantallaVerCDEv2 extends JPanel {
    
    private EquipoTableModel modeloEquipos;
    private MaterialTableModel modeloMateriales;
    private Map<String, Color> estadoColores;
    private JTable tablaEquipos;
    private JTable tablaMateriales;

    public PantallaVerCDEv2(CardLayout navegador, JPanel contenedor) {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Mapa de colores por estado
        estadoColores = new HashMap<>();
        for (EstadoEquipo estado : EstadoEquipo.values()) {
            estadoColores.put(estado.getNombre(), estado.getColor());
        }

        // Header reutilizable con título y botón de navegación
        PanelHeader header = new PanelHeader(
            Constantes.Titulos.ESTADO_PROCESOS, 
            navegador, 
            contenedor, 
            Constantes.Pantallas.ESTERILIZACION
        );
        add(header, BorderLayout.NORTH);

        // Panel central con dos secciones: Equipos y Materiales
        JPanel panelCentral = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0; // Expande horizontalmente

        // ===== TABLA DE EQUIPOS =====
        JLabel lblEquipos = new JLabel("Equipos / Clientes");
        lblEquipos.setFont(new Font("Arial", Font.BOLD, 16));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weighty = 0;
        panelCentral.add(lblEquipos, gbc);

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
        panelCentral.add(scrollEquipos, gbc);

        // Listener de selección para cargar materiales
        tablaEquipos.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                int selectedRow = tablaEquipos.getSelectedRow();
                if (selectedRow >= 0) {
                    Equipo equipoSeleccionado = modeloEquipos.getEquipoAt(selectedRow);
                    modeloMateriales.cargarMateriales(equipoSeleccionado);
                } else {
                    modeloMateriales.limpiar();
                }
            }
        });

        // ===== TABLA DE MATERIALES =====
        JLabel lblMateriales = new JLabel("Materiales del Equipo Seleccionado");
        lblMateriales.setFont(new Font("Arial", Font.BOLD, 16));
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weighty = 0;
        panelCentral.add(lblMateriales, gbc);

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

        JScrollPane scrollMateriales = new JScrollPane(tablaMateriales);
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weighty = 0.4;
        panelCentral.add(scrollMateriales, gbc);

        add(panelCentral, BorderLayout.CENTER);
    }

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

    public void actualizarTabla(List<Equipo> equipos) {
        modeloEquipos.actualizarDatos(equipos);
        modeloMateriales.limpiar();
    }
}
