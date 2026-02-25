package com.example.ui.common;


import com.example.ui.common.Estilos;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.util.HashMap;
import java.util.Map;

import com.example.common.constants.Constantes;
import com.example.features.equipos.model.EstadoEquipo;

public final class TableStyler {
    private static final Map<String, Color> ESTADO_COLORES = crearEstadoColores();

    private TableStyler() {
        throw new UnsupportedOperationException("Clase de estilos no instanciable");
    }

    public static void applyStandard(JTable table) {
        table.setFont(Estilos.Fuentes.TABLA_CONTENIDO);
        table.setRowHeight(Estilos.Dimensiones.ALTURA_FILA_TABLA);
        table.getTableHeader().setFont(Estilos.Fuentes.TABLA_ENCABEZADO);
    }

    public static void centerColumns(JTable table, int... columns) {
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        for (int column : columns) {
            table.getColumnModel().getColumn(column).setCellRenderer(centerRenderer);
        }
    }

    public static DefaultTableCellRenderer createEntregadoRenderer() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                boolean entregado = false;
                if (value instanceof Boolean) {
                    entregado = (Boolean) value;
                } else if (value instanceof String) {
                    entregado = Constantes.Textos.ENTREGADO_SI.equalsIgnoreCase(value.toString());
                }

                String texto = entregado
                    ? Constantes.Textos.ENTREGADO_SI
                    : Constantes.Textos.ENTREGADO_NO;
                c.setBackground(entregado ? new Color(144, 238, 144) : new Color(255, 160, 122));
                c.setForeground(Estilos.Colores.TEXTO_NORMAL);
                setHorizontalAlignment(SwingConstants.CENTER);
                setFont(Estilos.Fuentes.TABLA_ENFASIS);
                setText(texto);

                return c;
            }
        };
    }

    public static DefaultTableCellRenderer createEstadoRenderer() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                if (value != null) {
                    String estado = value.toString();
                    Color color = ESTADO_COLORES.getOrDefault(estado, Color.WHITE);
                    c.setBackground(color);
                    c.setForeground(Estilos.Colores.TEXTO_NORMAL);
                    setHorizontalAlignment(SwingConstants.CENTER);
                    setFont(Estilos.Fuentes.TABLA_ENFASIS);
                }

                return c;
            }
        };
    }

    private static Map<String, Color> crearEstadoColores() {
        Map<String, Color> colores = new HashMap<>();
        for (EstadoEquipo estado : EstadoEquipo.values()) {
            colores.put(estado.getNombre(), estado.getColor());
        }
        return colores;
    }
}



