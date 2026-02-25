package com.example.features.equipos.view.helpers;

import javax.swing.table.AbstractTableModel;

import com.example.features.equipos.model.Equipo;
import com.example.features.equipos.model.EstadoEquipo;

import java.util.ArrayList;
import java.util.List;

/**
 * Modelo para la tabla de equipos (solo clientes y su estado calculado).
 * No incluye materiales; estos se muestran en una tabla separada.
 */
public class EquipoTableModel extends AbstractTableModel {
    private String[] columnas;
    private List<Object[]> filas;
    private List<EstadoEquipo> estados; // Para ordenamiento
    private List<Equipo> equipos;      // Referencia a equipos completos

    public EquipoTableModel(String[] columnas) {
        this.columnas = columnas;
        this.filas = new ArrayList<>();
        this.estados = new ArrayList<>();
        this.equipos = new ArrayList<>();
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
     * Retorna el equipo completo para una fila específica.
     * Usado para obtener materiales cuando se selecciona una fila.
     */
    public Equipo getEquipoAt(int row) {
        if (row < 0 || row >= equipos.size()) return null;
        return equipos.get(row);
    }

    /**
     * Actualiza el modelo con una lista de equipos.
     * Muestra: Cliente, Institución y Estado (calculado).
     */
    public void actualizarDatos(List<Equipo> equiposCompletos) {
        filas.clear();
        estados.clear();
        equipos.clear();

        // Construir lista ordenada de equipos
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < equiposCompletos.size(); i++) {
            indices.add(i);
        }

        // Ordenar por estado (más atrasado primero)
        indices.sort((i1, i2) -> {
            EstadoEquipo e1 = equiposCompletos.get(i1).calcularEstado();
            EstadoEquipo e2 = equiposCompletos.get(i2).calcularEstado();
            return Integer.compare(e1.getOrden(), e2.getOrden());
        });

        // Agregar filas ordenadas
        for (int idx : indices) {
            Equipo eq = equiposCompletos.get(idx);
            EstadoEquipo estadoCalculado = eq.calcularEstado();

            filas.add(new Object[]{
                eq.getClienteNombre(),
                eq.getInstitucionNombre(),
                estadoCalculado.getNombre()
            });
            estados.add(estadoCalculado);
            equipos.add(eq);
        }

        fireTableDataChanged();
    }

    /**
     * Recalcula el estado mostrado sin reordenar filas.
     * Usado para previews en memoria.
     */
    public void refrescarEstados() {
        if (equipos.isEmpty()) {
            return;
        }

        for (int i = 0; i < equipos.size(); i++) {
            EstadoEquipo estadoCalculado = equipos.get(i).calcularEstado();
            filas.get(i)[2] = estadoCalculado.getNombre();
            if (i < estados.size()) {
                estados.set(i, estadoCalculado);
            }
        }

        fireTableRowsUpdated(0, filas.size() - 1);
    }
}


