package com.example.features.equipos.ortopedias.view.helpers;

import javax.swing.table.AbstractTableModel;

import com.example.common.model.EquipoRegistrableInterface;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;

import java.util.ArrayList;
import java.util.List;

/**
 * Modelo para la tabla de equipos.
 *
 * Ahora trabaja con {@link EquipoRegistrableInterface} en lugar de
 * {@link com.example.features.equipos.model.Equipo} para poder mostrar
 * tanto equipos de ortopedia como equipos "otros" en la misma tabla.
 *
 * La columna de descripción secundaria (índice 1) muestra la institución
 * para ortopedia y una cadena vacía para "otros".
 */
public class EquipoTableModel extends AbstractTableModel {

    private String[] columnas;
    private List<Object[]>            filas   = new ArrayList<>();
    private List<EstadoEquipo>        estados = new ArrayList<>();
    private List<EquipoRegistrableInterface>  equipos = new ArrayList<>();

    public EquipoTableModel(String[] columnas) {
        this.columnas = columnas;
    }

    @Override public int    getRowCount()                    { return filas.size(); }
    @Override public int    getColumnCount()                 { return columnas.length; }
    @Override public String getColumnName(int column)       { return columnas[column]; }
    @Override public Object getValueAt(int row, int column) { return filas.get(row)[column]; }
    @Override public boolean isCellEditable(int row, int column) { return false; }

    /**
     * Retorna el equipo (como interfaz) para una fila.
     * Los callers que necesiten el tipo concreto hacen el cast con {@code instanceof}.
     */
    public EquipoRegistrableInterface getEquipoAt(int row) {
        if (row < 0 || row >= equipos.size()) return null;
        return equipos.get(row);
    }

    /**
     * Actualiza el modelo ordenando por estado (más atrasado primero).
     * Acepta cualquier implementación de {@link EquipoRegistrableInterface}.
     */
    public void actualizarDatos(List<EquipoRegistrableInterface> equiposCompletos) {
        filas.clear();
        estados.clear();
        equipos.clear();

        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < equiposCompletos.size(); i++) indices.add(i);

        indices.sort((i1, i2) -> {
            EstadoEquipo e1 = equiposCompletos.get(i1).calcularEstado();
            EstadoEquipo e2 = equiposCompletos.get(i2).calcularEstado();
            return Integer.compare(e1.getOrden(), e2.getOrden());
        });

        for (int idx : indices) {
            EquipoRegistrableInterface eq = equiposCompletos.get(idx);
            EstadoEquipo estadoCalculado = eq.calcularEstado();

            filas.add(new Object[]{
                eq.getClienteNombre(),
                eq.getDescripcionSecundaria(),
                estadoCalculado.getNombre()
            });
            estados.add(estadoCalculado);
            equipos.add(eq);
        }

        fireTableDataChanged();
    }

    /** Recalcula el estado mostrado sin reordenar filas (usado en previews en memoria). */
    public void refrescarEstados() {
        if (equipos.isEmpty()) return;
        for (int i = 0; i < equipos.size(); i++) {
            EstadoEquipo ec = equipos.get(i).calcularEstado();
            filas.get(i)[2] = ec.getNombre();
            if (i < estados.size()) estados.set(i, ec);
        }
        fireTableRowsUpdated(0, filas.size() - 1);
    }
}