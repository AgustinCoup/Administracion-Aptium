package com.example.view;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import com.example.model.Equipo;

import java.awt.*;
import java.util.*;
import java.util.List;

public class PanelVerCDEv2 extends JPanel {
    
    private DefaultTableModel modelo;
    private Map<String, Color> estadoColores;
    private static final String[] ORDEN_ESTADOS = {"Nuevo", "Lavando", "Lavado", "Empaquetado", "Esterilizando", "Esterilizado"};

    public PanelVerCDEv2(CardLayout navegador, JPanel contenedor) {
        setLayout(new BorderLayout());

        // Mapa de colores por estado
        estadoColores = new HashMap<>();
        estadoColores.put("Nuevo", Color.LIGHT_GRAY);
        estadoColores.put("Lavando", Color.CYAN);
        estadoColores.put("Lavado", new Color(135, 206, 250));
        estadoColores.put("Empaquetado", Color.ORANGE);
        estadoColores.put("Esterilizando", Color.PINK);
        estadoColores.put("Esterilizado", Color.GREEN);

        JPanel panelNorte = new JPanel();
        panelNorte.setLayout(new BoxLayout(panelNorte, BoxLayout.Y_AXIS));

        JPanel panelTitulo = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        JLabel titulo = new JLabel("ESTADO DE PROCESOS EN TIEMPO REAL");
        titulo.setFont(new Font("Arial", Font.BOLD, 26));
        panelTitulo.add(titulo);

        JButton btnVolver = new JButton("<- Volver");
        JPanel panelBotonVolver = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        panelBotonVolver.add(btnVolver);

        panelNorte.add(panelBotonVolver);
        panelNorte.add(panelTitulo);

        add(panelNorte, BorderLayout.NORTH);

        btnVolver.addActionListener(e -> navegador.show(contenedor, "ESTERILIZACION"));

        // Crear tabla con dos columnas: Equipo y Estado
        String[] columnas = {"Equipo", "Estado"};
        this.modelo = new DefaultTableModel(columnas, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // No editable
            }
        };

        ordenarModeloPorEstado(modelo);

        JTable tabla = new JTable(modelo);
        tabla.setFont(new Font("Arial", Font.PLAIN, 14));
        tabla.setRowHeight(30);
        tabla.getTableHeader().setFont(new Font("Arial", Font.BOLD, 16));

        // Renderizador personalizado para colorear la celda de estado
        tabla.getColumnModel().getColumn(1).setCellRenderer(crearRendererEstado());

        JScrollPane scroll = new JScrollPane(tabla);
        add(scroll, BorderLayout.CENTER);
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
                    c.setForeground(Color.BLACK);
                    setHorizontalAlignment(SwingConstants.CENTER);
                    setFont(new Font("Arial", Font.BOLD, 14));
                }
                
                return c;
            }
        };
    }

    private void ordenarModeloPorEstado(DefaultTableModel modelo) {
        // Crear lista con todas las filas
        List<Object[]> filas = new ArrayList<>();
        for (int i = 0; i < modelo.getRowCount(); i++) {
            Object[] fila = new Object[modelo.getColumnCount()];
            for (int j = 0; j < modelo.getColumnCount(); j++) {
                fila[j] = modelo.getValueAt(i, j);
            }
            filas.add(fila);
        }

        // Ordenar por estado según ORDEN_ESTADOS
        filas.sort((fila1, fila2) -> {
            String estado1 = fila1[1].toString();
            String estado2 = fila2[1].toString();
            int indice1 = obtenerIndiceEstado(estado1);
            int indice2 = obtenerIndiceEstado(estado2);
            return Integer.compare(indice1, indice2);
        });

        // Limpiar el modelo y agregar las filas ordenadas
        modelo.setRowCount(0);
        for (Object[] fila : filas) {
            modelo.addRow(fila);
        }
    }

    private int obtenerIndiceEstado(String estado) {
        for (int i = 0; i < ORDEN_ESTADOS.length; i++) {
            if (ORDEN_ESTADOS[i].equals(estado)) {
                return i;
            }
        }
        return Integer.MAX_VALUE; // Si no está en el orden, va al final
    }

    public void actualizarTabla(List<Equipo> equipos) {
        modelo.setRowCount(0); // Limpiar tabla
        for (Equipo eq : equipos) {
            modelo.addRow(new Object[]{eq.getCodigoEquipo(), eq.getEstado()});
        }
        ordenarModeloPorEstado(modelo);
    }
}
