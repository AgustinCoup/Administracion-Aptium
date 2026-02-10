package com.example.view.helpers;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

import com.example.constants.Constantes;

public class InstitucionEntregaTableModel extends AbstractTableModel {
    private final String[] columnas = {
        Constantes.Textos.COLUMNA_INSTITUCION,
        Constantes.Textos.COLUMNA_EQUIPOS
    };
    private final List<InstitucionEntregaItem> filas = new ArrayList<>();

    @Override
    public int getRowCount() {
        return filas.size();
    }

    @Override
    public int getColumnCount() {
        return columnas.length;
    }

    @Override
    public String getColumnName(int column) {
        return columnas[column];
    }

    @Override
    public Object getValueAt(int row, int column) {
        InstitucionEntregaItem item = filas.get(row);
        if (column == 0) {
            return item.getNombre();
        }
        return item.getEquiposCount();
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }

    public InstitucionEntregaItem getInstitucionAt(int row) {
        if (row < 0 || row >= filas.size()) {
            return null;
        }
        return filas.get(row);
    }

    public void actualizarDatos(List<InstitucionEntregaItem> instituciones) {
        filas.clear();
        if (instituciones != null) {
            filas.addAll(instituciones);
        }
        fireTableDataChanged();
    }
}
