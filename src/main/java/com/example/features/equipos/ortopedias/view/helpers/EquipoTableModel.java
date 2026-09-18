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
     * Actualiza el modelo <b>ordenando por estado</b> (más atrasado primero), sobre la lista
     * completa que recibe. Acepta cualquier implementación de {@link EquipoRegistrableInterface}.
     *
     * <p>La usan las pantallas que reciben <b>todo</b> lo que van a mostrar en una sola lista —
     * Registrar Estado y Correcciones, que comen de la cola operativa—. Para una lista que es una
     * <b>página</b> de un conjunto más grande está {@link #actualizarDatosEnOrden(List)}: ver ahí
     * por qué ordenar acá rompería el orden global.
     */
    public void actualizarDatos(List<EquipoRegistrableInterface> equiposCompletos) {
        List<EquipoRegistrableInterface> ordenados = new ArrayList<>(equiposCompletos);
        ordenados.sort((e1, e2) ->
            Integer.compare(e1.calcularEstado().getOrden(), e2.calcularEstado().getOrden()));
        volcar(ordenados);
    }

    /**
     * Actualiza el modelo <b>respetando el orden en que viene la lista</b>.
     *
     * <h2>Por qué Estado de Procesos entra por acá y no por {@link #actualizarDatos(List)}</h2>
     * Esa pantalla dejó de recibir el histórico completo: recibe una <b>página de 50</b> que la
     * base ya ordenó por estado sobre el conjunto entero. Reordenar acá ordenaría <em>dentro de la
     * página</em>, que no es lo mismo: la página 2 volvería a empezar por los más atrasados de esa
     * página y el operador vería el mismo estado repetirse pestaña tras pestaña. Un orden global
     * sólo puede salir del único lugar que ve todas las filas, que es SQL
     * ({@code CdeConsultaDAO.SQL_ORDEN}).
     *
     * <p>El criterio es el mismo en los dos lados —{@code EstadoEquipo.getOrden()} ascendente—, así
     * que lo que cambia es <b>dónde</b> se ordena, no cómo.
     */
    public void actualizarDatosEnOrden(List<EquipoRegistrableInterface> pagina) {
        volcar(pagina);
    }

    private void volcar(List<EquipoRegistrableInterface> enOrden) {
        filas.clear();
        estados.clear();
        equipos.clear();

        for (EquipoRegistrableInterface eq : enOrden) {
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