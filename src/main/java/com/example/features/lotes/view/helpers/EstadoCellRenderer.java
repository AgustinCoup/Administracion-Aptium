package com.example.features.lotes.view.helpers;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/**
 * Renderer personalizado para pintar la celda de estado con colores
 */
public class EstadoCellRenderer extends DefaultTableCellRenderer {
    
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int column) {
        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        
        if (value != null && !isSelected) {
            String estado = value.toString().trim().toUpperCase();
            switch (estado) {
                case "EXITOSO":
                    c.setBackground(new Color(144, 238, 144)); // Verde claro
                    break;
                case "FALLIDO":
                    c.setBackground(new Color(255, 182, 193)); // Rojo claro
                    break;
                case "ACTIVO":
                    c.setBackground(new Color(173, 216, 230)); // Azul claro
                    break;
                default:
                    c.setBackground(Color.WHITE);
                    break;
            }
        } else if (isSelected) {
            c.setBackground(table.getSelectionBackground());
        }
        
        setHorizontalAlignment(CENTER);
        return c;
    }
}
