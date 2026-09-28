package com.example.features.equipos.ortopedias.view.helpers;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

import com.example.common.constants.Constantes;

public class MaterialEntregaTableModel extends AbstractTableModel {
    private final String[] columnas = {
        Constantes.Textos.COLUMNA_INGRESO,
        Constantes.Textos.COLUMNA_MATERIAL,
        Constantes.Textos.COLUMNA_CANTIDAD
    };
    private final List<MaterialEntregaItem> filas = new ArrayList<>();

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
        MaterialEntregaItem item = filas.get(row);
        switch (column) {
            case 0:
                return item.ingreso();
            case 1:
                return item.material();
            case 2:
                return item.cantidad();
            default:
                return "";
        }
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }

    public MaterialEntregaItem getItemAt(int row) {
        return filas.get(row);
    }

    public void actualizarDatos(List<MaterialEntregaItem> materiales) {
        filas.clear();
        if (materiales != null) {
            filas.addAll(materiales);
        }
        fireTableDataChanged();
    }

    public void limpiar() {
        filas.clear();
        fireTableDataChanged();
    }
}


