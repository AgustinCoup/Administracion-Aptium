package com.example.view.helpers;

import javax.swing.table.AbstractTableModel;

import com.example.model.Equipo;
import com.example.model.Material;
import com.example.constants.Constantes;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.List;

/**
 * Modelo para la tabla de materiales de un equipo seleccionado.
 * Muestra: Descripción, Cantidad, Estado
 */
public class MaterialTableModel extends AbstractTableModel {
    private String[] columnas = {
        Constantes.Textos.COLUMNA_MATERIAL,
        Constantes.Textos.COLUMNA_CANTIDAD,
        Constantes.Textos.COLUMNA_ESTADO,
        Constantes.Textos.COLUMNA_ULTIMO_MOVIMIENTO
    };
    private List<Object[]> filas;
    private static final DateTimeFormatter FECHA_FORMAT =
        DateTimeFormatter.ofPattern(Constantes.Formatos.FORMATO_FECHA_HORA);

    public MaterialTableModel() {
        this.filas = new ArrayList<>();
    }

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
        return filas.get(row)[column];
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }

    /**
     * Carga los materiales de un equipo en la tabla.
     */
    public void cargarMateriales(Equipo equipo) {
        filas.clear();

        if (equipo == null || equipo.getMateriales().isEmpty()) {
            fireTableDataChanged();
            return;
        }

        // Agregar cada material
        for (Material mat : equipo.getMateriales()) {
            String ultimoMovimiento = mat.getUltimoMovimiento() != null
                ? mat.getUltimoMovimiento().format(FECHA_FORMAT)
                : Constantes.Textos.SIN_MOVIMIENTO;
            filas.add(new Object[]{
                mat.getDescripcion(),
                mat.getCantidad(),
                mat.getEstado().getNombre(),
                ultimoMovimiento
            });
        }

        fireTableDataChanged();
    }

    /**
     * Limpia la tabla.
     */
    public void limpiar() {
        filas.clear();
        fireTableDataChanged();
    }
}
