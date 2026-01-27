package com.example.view;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import com.example.constants.Constantes;
import com.example.model.Equipo;
import com.example.model.EstadoEquipo;

import java.awt.*;
import java.util.*;
import java.util.List;

public class PantallaVerCDEv2 extends JPanel {
    
    private DefaultTableModel modelo;
    private Map<String, Color> estadoColores;

    public PantallaVerCDEv2(CardLayout navegador, JPanel contenedor) {
        setLayout(new BorderLayout());

        // Mapa de colores por estado usando el Enum
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

        // Crear tabla con dos columnas: Cliente y Estado
        String[] columnas = {"Cliente", "Estado"};
        this.modelo = new DefaultTableModel(columnas, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // No editable
            }
        };

        JTable tabla = new JTable(modelo);
        tabla.setFont(Estilos.Fuentes.TABLA_CONTENIDO);
        tabla.setRowHeight(Estilos.Dimensiones.ALTURA_FILA_TABLA);
        tabla.getTableHeader().setFont(Estilos.Fuentes.TABLA_ENCABEZADO);

        // Renderizador personalizado para colorear la celda de estado (columna 1)
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
                    c.setForeground(Estilos.Colores.TEXTO_NORMAL);
                    setHorizontalAlignment(SwingConstants.CENTER);
                    setFont(Estilos.Fuentes.TABLA_ENFASIS);
                }
                
                return c;
            }
        };
    }

    /**
     * Ordena el modelo de la tabla por estado según el orden definido en EstadoEquipo.
     * Extrae todas las filas, las ordena y luego las reinsertas en el modelo.
     */
    private void ordenarModeloPorEstado(DefaultTableModel modelo) {
        // Extraer todas las filas como una lista de arrays
        List<Object[]> filas = new ArrayList<>();
        for (int i = 0; i < modelo.getRowCount(); i++) {
            Object[] fila = modelo.getDataVector().elementAt(i).toArray();
            filas.add(fila);
        }

        // Ordenar por el orden del estado en el enum
        filas.sort((fila1, fila2) -> {
            int indice1 = EstadoEquipo.desdeBD(fila1[1].toString()).getOrden();
            int indice2 = EstadoEquipo.desdeBD(fila2[1].toString()).getOrden();
            return Integer.compare(indice1, indice2);
        });

        // Reinsertar las filas ordenadas
        modelo.setRowCount(0);
        filas.forEach(modelo::addRow);
    }

    public void actualizarTabla(List<Equipo> equipos) {
        modelo.setRowCount(0); // Limpiar tabla
        equipos.forEach(eq -> modelo.addRow(new Object[]{
            eq.getClienteNombre(),
            eq.getEstado().getNombre()
        }));
        ordenarModeloPorEstado(modelo);
    }
}
