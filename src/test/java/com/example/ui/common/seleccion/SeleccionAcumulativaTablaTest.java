package com.example.ui.common.seleccion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

import static com.example.ui.common.seleccion.SeleccionAcumulativaTabla.ACCION_SUMAR_ANTERIOR;
import static com.example.ui.common.seleccion.SeleccionAcumulativaTabla.ACCION_SUMAR_SIGUIENTE;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless: el foco se simula invocando los {@code FocusListener} con un
 * {@link FocusEvent} armado a mano, y las teclas invocando la acción del
 * {@code ActionMap} que resuelve el {@code InputMap}. No hace falta foco real.
 */
class SeleccionAcumulativaTablaTest {

    private JTable tabla;
    private JButton exento;
    private JButton otro;

    @BeforeEach
    void setUp() {
        tabla = new JTable(new DefaultTableModel(5, 1));
        exento = new JButton("Avanzar");
        otro = new JButton("Confirmar");
        SeleccionAcumulativaTabla.instalar(tabla, exento);
    }

    @Test
    void instalar_poneSeleccionMultiple() {
        assertEquals(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
            tabla.getSelectionModel().getSelectionMode());
    }

    @Test
    void ctrlAbajo_estaLigadoALaAccionPropia() {
        assertEquals(ACCION_SUMAR_SIGUIENTE, accionLigadaA("ctrl DOWN"));
        assertEquals(ACCION_SUMAR_SIGUIENTE, accionLigadaA("ctrl KP_DOWN"));
    }

    @Test
    void ctrlArriba_estaLigadoALaAccionPropia() {
        assertEquals(ACCION_SUMAR_ANTERIOR, accionLigadaA("ctrl UP"));
        assertEquals(ACCION_SUMAR_ANTERIOR, accionLigadaA("ctrl KP_UP"));
    }

    @Test
    void instalar_noPisaElBindingDeOtrasTablas() {
        JTable ajena = new JTable(new DefaultTableModel(5, 1));

        Object ligada = ajena.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
            .get(KeyStroke.getKeyStroke("ctrl DOWN"));

        assertNotEquals(ACCION_SUMAR_SIGUIENTE, ligada);
    }

    @Test
    void accionSumarSiguiente_sumaSinQuitar() {
        tabla.addRowSelectionInterval(1, 1);
        tabla.addRowSelectionInterval(3, 3);
        tabla.addRowSelectionInterval(1, 1); // lead en 1, {1, 3}

        invocar(ACCION_SUMAR_SIGUIENTE);

        assertArrayEquals(new int[] {1, 2, 3}, tabla.getSelectedRows());
        assertEquals(2, tabla.getSelectionModel().getLeadSelectionIndex());
    }

    @Test
    void accionSumarAnterior_sumaSinQuitar() {
        tabla.addRowSelectionInterval(0, 0);
        tabla.addRowSelectionInterval(3, 3); // lead en 3, {0, 3}

        invocar(ACCION_SUMAR_ANTERIOR);

        assertArrayEquals(new int[] {0, 2, 3}, tabla.getSelectedRows());
        assertEquals(2, tabla.getSelectionModel().getLeadSelectionIndex());
    }

    @Test
    void accionSumarSiguiente_sinSeleccion_noSeleccionaNada() {
        invocar(ACCION_SUMAR_SIGUIENTE);

        assertEquals(0, tabla.getSelectedRowCount());
    }

    @Test
    void accionSumarSiguiente_enLaUltimaFila_noCambia() {
        tabla.addRowSelectionInterval(4, 4);

        invocar(ACCION_SUMAR_SIGUIENTE);

        assertArrayEquals(new int[] {4}, tabla.getSelectedRows());
    }

    @Test
    void focoDeTablaANoExento_vacia() {
        tabla.addRowSelectionInterval(1, 2);

        perderFoco(tabla, otro, false);

        assertEquals(0, tabla.getSelectedRowCount());
    }

    @Test
    void focoDeTablaAExento_conserva() {
        tabla.addRowSelectionInterval(1, 2);

        perderFoco(tabla, exento, false);

        assertEquals(2, tabla.getSelectedRowCount());
    }

    @Test
    void focoDeExentoANoExento_vacia() {
        tabla.addRowSelectionInterval(1, 2);

        perderFoco(exento, otro, false);

        assertEquals(0, tabla.getSelectedRowCount());
    }

    @Test
    void focoDeExentoATabla_conserva() {
        tabla.addRowSelectionInterval(1, 2);

        perderFoco(exento, tabla, false);

        assertEquals(2, tabla.getSelectedRowCount());
    }

    @Test
    void focoPerdidoTemporal_conserva() {
        tabla.addRowSelectionInterval(1, 2);

        perderFoco(tabla, otro, true);
        perderFoco(exento, otro, true);

        assertEquals(2, tabla.getSelectedRowCount());
    }

    @Test
    void focoPerdidoConOpuestoNull_noExplota() {
        tabla.addRowSelectionInterval(1, 2);

        assertDoesNotThrow(() -> perderFoco(tabla, null, false));
        assertEquals(0, tabla.getSelectedRowCount());
    }

    private Object accionLigadaA(String tecla) {
        return tabla.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
            .get(KeyStroke.getKeyStroke(tecla));
    }

    private void invocar(String accion) {
        tabla.getActionMap().get(accion)
            .actionPerformed(new ActionEvent(tabla, ActionEvent.ACTION_PERFORMED, accion));
    }

    private static void perderFoco(Component origen, Component destino, boolean temporal) {
        FocusEvent e = new FocusEvent(origen, FocusEvent.FOCUS_LOST, temporal, destino);
        for (FocusListener l : origen.getFocusListeners()) {
            l.focusLost(e);
        }
    }
}
