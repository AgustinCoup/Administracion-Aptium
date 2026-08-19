package com.example.features.lavadero.view.helpers;

import com.example.common.constants.Constantes;
import com.example.features.lavadero.model.ElementoLavadoPendiente;

import javax.swing.table.AbstractTableModel;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Tabla izquierda de Salidas de Lavadero: tandas lavadas pendientes de secado y doblado. */
public class ElementoLavadoTableModel extends AbstractTableModel {

    private static final DateTimeFormatter FORMATO_FECHA =
        DateTimeFormatter.ofPattern(Constantes.Formatos.FORMATO_FECHA_HORA);

    private final String[] columnas = {
        Constantes.Textos.COLUMNA_ELEMENTO,
        Constantes.Textos.COLUMNA_PENDIENTE,
        Constantes.Textos.COLUMNA_CLIENTE,
        Constantes.Textos.COLUMNA_LAVARROPAS,
        Constantes.Textos.COLUMNA_LAVADO_EL
    };
    private List<ElementoLavadoPendiente> filas = new ArrayList<>();

    @Override public int getRowCount()    { return filas.size(); }
    @Override public int getColumnCount() { return columnas.length; }
    @Override public String getColumnName(int column) { return columnas[column]; }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        ElementoLavadoPendiente item = filas.get(rowIndex);
        switch (columnIndex) {
            case 0: return item.elementoNombre();
            case 1: return item.cantidadPendiente();
            case 2: return item.clienteNombre();
            case 3: return item.lavarropasNumero();
            case 4: return item.fechaFinCiclo() != null ? item.fechaFinCiclo().format(FORMATO_FECHA) : "";
            default: return null;
        }
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return (columnIndex == 1 || columnIndex == 3) ? Integer.class : String.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) { return false; }

    public void setItems(List<ElementoLavadoPendiente> items) {
        filas = items != null ? new ArrayList<>(items) : new ArrayList<>();
        fireTableDataChanged();
    }

    public ElementoLavadoPendiente getItemAt(int row) {
        return (row >= 0 && row < filas.size()) ? filas.get(row) : null;
    }
}
