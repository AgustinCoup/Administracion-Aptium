package com.example.features.lavadero.view.helpers;

import com.example.features.lavadero.model.CicloLavadero;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.awt.Component;

/**
 * Renderer de la celda de estado de un ciclo de Lavadero.
 * Propio de esta feature: los estados de un ciclo (ACTIVO / FINALIZADO) no se
 * solapan con los de un lote, así que no comparte código con Lotes.
 */
public class CicloEstadoCellRenderer extends DefaultTableCellRenderer {

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int column) {
        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        if (value != null && !isSelected) {
            String estado = value.toString().trim().toUpperCase();
            switch (estado) {
                case CicloLavadero.ESTADO_ACTIVO:
                    c.setBackground(new Color(173, 216, 230)); // Azul claro
                    break;
                case CicloLavadero.ESTADO_FINALIZADO:
                    c.setBackground(new Color(211, 211, 211)); // Gris claro
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
