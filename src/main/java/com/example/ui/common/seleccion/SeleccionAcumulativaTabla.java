package com.example.ui.common.seleccion;

import com.example.ui.common.dnd.TableSelectionSupport;
import com.example.ui.common.seleccion.ReglasSeleccionAcumulativa.Seleccion;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

/**
 * Cablea {@link ReglasSeleccionAcumulativa} a una {@link JTable}: selección múltiple,
 * Ctrl+↓ / Ctrl+↑ que suman, y la selección que se vacía cuando el foco sale de la zona.
 *
 * <p><b>Lo que no se toca:</b> click, Ctrl+click, Shift+click, Shift+flecha, arrastre y
 * Ctrl+A ya los resuelve {@code DefaultListSelectionModel} en
 * {@code MULTIPLE_INTERVAL_SELECTION}, y quedan como en Swing.
 *
 * <p><b>Por qué hay exentos.</b> La "zona" es la tabla más los componentes que operan
 * sobre su selección (p. ej. el botón Avanzar). Sin exentos, el click en ese botón
 * vaciaría la selección antes de usarla. El diálogo modal que abra después no necesita
 * ser exento: su pérdida de foco es temporal, y la regla la cubre por ese lado.
 *
 * <p><b>Por qué el listener va también en cada exento.</b> Una vez que el foco pasó al
 * botón exento, la tabla ya no lo tiene: un click posterior en cualquier otro lado no
 * dispara ningún {@code focusLost} en la tabla, y la selección sobreviviría. Por eso los
 * exentos se pasan como componentes y no como un predicado: el helper necesita a quién
 * escuchar.
 */
public final class SeleccionAcumulativaTabla {

    public static final String ACCION_SUMAR_SIGUIENTE = "seleccion-acumulativa-sumar-siguiente";
    public static final String ACCION_SUMAR_ANTERIOR  = "seleccion-acumulativa-sumar-anterior";

    private SeleccionAcumulativaTabla() {
        throw new UnsupportedOperationException("Clase de utilidades no instanciable");
    }

    /**
     * Instala la selección acumulativa en la tabla.
     *
     * @param tabla   la tabla
     * @param exentos componentes a los que el foco puede ir sin vaciar la selección
     */
    public static void instalar(JTable tabla, JComponent... exentos) {
        TableSelectionSupport.enableMultiSelection(tabla);
        pisarCtrlFlechas(tabla);

        List<JComponent> zona = Arrays.asList(exentos);
        FocusListener vaciarAlSalir = new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                boolean destinoEnLaZona = enLaZona(e.getOppositeComponent(), tabla, zona);
                if (!ReglasSeleccionAcumulativa.conservarAlPerderFoco(e.isTemporary(), destinoEnLaZona)) {
                    tabla.clearSelection();
                }
            }
        };
        tabla.addFocusListener(vaciarAlSalir);
        zona.forEach(exento -> exento.addFocusListener(vaciarAlSalir));
    }

    /**
     * En {@code JTable}, Ctrl+↓ / Ctrl+↑ están ligados en
     * {@code WHEN_ANCESTOR_OF_FOCUSED_COMPONENT} a {@code selectNextRowChangeLead} /
     * {@code selectPreviousRowChangeLead}, que mueven el lead <b>sin seleccionar</b>. Se
     * pisan en el {@code InputMap} propio de la tabla (el del look and feel es su padre y
     * queda intacto para las demás tablas), incluidas las flechas del teclado numérico.
     */
    private static void pisarCtrlFlechas(JTable tabla) {
        InputMap im = tabla.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        im.put(KeyStroke.getKeyStroke("ctrl DOWN"),    ACCION_SUMAR_SIGUIENTE);
        im.put(KeyStroke.getKeyStroke("ctrl KP_DOWN"), ACCION_SUMAR_SIGUIENTE);
        im.put(KeyStroke.getKeyStroke("ctrl UP"),      ACCION_SUMAR_ANTERIOR);
        im.put(KeyStroke.getKeyStroke("ctrl KP_UP"),   ACCION_SUMAR_ANTERIOR);

        ActionMap am = tabla.getActionMap();
        am.put(ACCION_SUMAR_SIGUIENTE, accionSumar(tabla, ReglasSeleccionAcumulativa::sumarSiguiente));
        am.put(ACCION_SUMAR_ANTERIOR,  accionSumar(tabla, ReglasSeleccionAcumulativa::sumarAnterior));
    }

    private static AbstractAction accionSumar(JTable tabla, Regla regla) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Seleccion actual = seleccionActual(tabla);
                Seleccion nueva = regla.aplicar(actual, tabla.getRowCount());
                if (nueva.ancla() == actual.ancla()) {
                    return;
                }
                // Sólo la fila nueva: así el lead queda en ella y nada se deselecciona.
                int fila = nueva.ancla();
                tabla.addRowSelectionInterval(fila, fila);
                tabla.scrollRectToVisible(tabla.getCellRect(fila, 0, true));
            }
        };
    }

    private static Seleccion seleccionActual(JTable tabla) {
        TreeSet<Integer> filas = new TreeSet<>();
        for (int fila : tabla.getSelectedRows()) {
            filas.add(fila);
        }
        ListSelectionModel modelo = tabla.getSelectionModel();
        return new Seleccion(filas, modelo.getLeadSelectionIndex());
    }

    /**
     * {@code null} no está en la zona: el foco se fue a otra aplicación, y esa pérdida
     * suele ser temporal. Los descendientes cuentan (p. ej. el editor de una celda).
     */
    private static boolean enLaZona(Component destino, JTable tabla, List<JComponent> exentos) {
        if (destino == null) {
            return false;
        }
        if (SwingUtilities.isDescendingFrom(destino, tabla)) {
            return true;
        }
        return exentos.stream().anyMatch(exento -> SwingUtilities.isDescendingFrom(destino, exento));
    }

    @FunctionalInterface
    private interface Regla {
        Seleccion aplicar(Seleccion actual, int cantidadFilas);
    }
}
