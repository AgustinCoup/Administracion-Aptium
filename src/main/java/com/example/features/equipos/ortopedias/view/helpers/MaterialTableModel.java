package com.example.features.equipos.ortopedias.view.helpers;

import javax.swing.table.AbstractTableModel;

import com.example.common.model.EquipoRegistrableInterface;
import com.example.common.model.MaterialRegistrableInterface;
import com.example.common.constants.Constantes;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Modelo para la tabla de materiales de un equipo seleccionado.
 *
 * Ahora trabaja con {@link MaterialRegistrableInterface} para poder mostrar
 * materiales de ortopedia y de "otros" en la misma tabla.
 */
public class MaterialTableModel extends AbstractTableModel {

    private final String[] columnas = {
        Constantes.Textos.COLUMNA_MATERIAL,
        Constantes.Textos.COLUMNA_CANTIDAD,
        Constantes.Textos.COLUMNA_ESTADO,
        Constantes.Textos.COLUMNA_ULTIMO_MOVIMIENTO
    };
    private List<Object[]>           filas     = new ArrayList<>();
    private List<MaterialRegistrableInterface> materiales = new ArrayList<>();

    private static final DateTimeFormatter FECHA_FORMAT =
        DateTimeFormatter.ofPattern(Constantes.Formatos.FORMATO_FECHA_HORA);

    @Override public int    getRowCount()                    { return filas.size(); }
    @Override public int    getColumnCount()                 { return columnas.length; }
    @Override public String getColumnName(int column)       { return columnas[column]; }
    @Override public Object getValueAt(int row, int column) { return filas.get(row)[column]; }
    @Override public boolean isCellEditable(int row, int col){ return false; }

    /**
     * Retorna el material en la fila indicada como {@link MaterialRegistrableInterface}.
     * Usado por {@link com.example.features.equipos.controller.RegistrarEstadoController}
     * para obtener el material seleccionado.
     */
    public MaterialRegistrableInterface getMaterialAt(int row) {
        if (row < 0 || row >= materiales.size()) return null;
        return materiales.get(row);
    }

    /**
     * Carga los materiales de un equipo registrable (ortopedia u otros).
     */
    public void cargarMateriales(EquipoRegistrableInterface equipo) {
        filas.clear();
        materiales.clear();

        if (equipo == null) { fireTableDataChanged(); return; }

        List<MaterialRegistrableInterface> lista = equipo.getMaterialesRegistrables();
        if (lista.isEmpty()) { fireTableDataChanged(); return; }

        for (MaterialRegistrableInterface mat : lista) {
            String ultimoMovimiento = mat.getUltimoMovimiento() != null
                ? mat.getUltimoMovimiento().format(FECHA_FORMAT)
                : Constantes.Textos.SIN_MOVIMIENTO;
            filas.add(new Object[]{
                mat.getDescripcion(),
                mat.getCantidad(),
                mat.getEstado().getNombre(),
                ultimoMovimiento
            });
            materiales.add(mat);
        }
        fireTableDataChanged();
    }

    public void limpiar() {
        filas.clear();
        materiales.clear();
        fireTableDataChanged();
    }
}