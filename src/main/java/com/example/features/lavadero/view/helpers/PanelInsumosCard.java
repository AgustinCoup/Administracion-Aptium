package com.example.features.lavadero.view.helpers;

import com.example.features.lavadero.model.InsumoCatalogo;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Lista dinámica de insumos extra de una card de lavarropas: un combo con botón "+" para agregar
 * y una fila por insumo ya elegido, con un botón "×" para quitarlo. Sin I/O ni services: recibe
 * el catálogo ya leído por el controller.
 *
 * <p>La deduplicación es por {@link InsumoCatalogo#id()}, nunca por {@code equals} del objeto ni
 * por referencia: dos lecturas del catálogo pueden traer instancias distintas del mismo insumo.
 * El combo directamente no ofrece los insumos ya elegidos, así que el "+" no puede duplicar.</p>
 */
public class PanelInsumosCard extends JPanel {

    private static final Font FONT_CONFIG = new Font(Font.SANS_SERIF, Font.PLAIN, 11);

    private final JComboBox<InsumoCatalogo> combo    = new JComboBox<>();
    private final JButton                   btnMas   = new JButton("+");
    private final JPanel                    filas    = new JPanel();

    private List<InsumoCatalogo> catalogo      = List.of();
    private final List<InsumoCatalogo> seleccionados = new ArrayList<>();

    private Runnable onCambio;

    public PanelInsumosCard() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        combo.setFont(FONT_CONFIG);
        btnMas.setFont(FONT_CONFIG);
        btnMas.setMargin(new Insets(0, 4, 0, 4));
        btnMas.addActionListener(e -> agregarSeleccionDelCombo());

        JPanel filaCombo = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 1));
        filaCombo.add(combo);
        filaCombo.add(btnMas);
        add(filaCombo);

        filas.setLayout(new BoxLayout(filas, BoxLayout.Y_AXIS));
        add(filas);
    }

    /**
     * Repuebla sólo el combo: no toca las filas ya elegidas. Es la misma trampa que
     * {@code LavarropasCard.setJabones} documenta para el jabón, y se vuelve crítica en cuanto el
     * plan de Ajustes recargue el catálogo en cada apertura de pantalla.
     */
    public void setCatalogo(List<InsumoCatalogo> catalogo) {
        this.catalogo = catalogo == null ? List.of() : List.copyOf(catalogo);
        actualizarCombo();
    }

    /** Insumos elegidos, en el orden en que se agregaron. */
    public List<InsumoCatalogo> getSeleccionados() {
        return List.copyOf(seleccionados);
    }

    /** Vacía las filas elegidas. El catálogo del combo no cambia. */
    public void limpiar() {
        seleccionados.clear();
        filas.removeAll();
        actualizarCombo();
        revalidate();
        repaint();
    }

    public void setOnCambio(Runnable onCambio) {
        this.onCambio = onCambio;
    }

    private void agregarSeleccionDelCombo() {
        InsumoCatalogo elegido = (InsumoCatalogo) combo.getSelectedItem();
        if (elegido == null || yaElegido(elegido.id())) return;

        seleccionados.add(elegido);
        filas.add(filaDe(elegido));
        actualizarCombo();
        revalidate();
        repaint();
        notificarCambio();
    }

    private void quitar(InsumoCatalogo insumo) {
        seleccionados.removeIf(i -> i.id() == insumo.id());
        actualizarFilas();
        actualizarCombo();
        revalidate();
        repaint();
        notificarCambio();
    }

    private void actualizarFilas() {
        filas.removeAll();
        for (InsumoCatalogo i : seleccionados) filas.add(filaDe(i));
    }

    private JPanel filaDe(InsumoCatalogo insumo) {
        JPanel fila = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        JLabel lbl = new JLabel(insumo.nombre());
        lbl.setFont(FONT_CONFIG);
        JButton btnQuitar = new JButton("×");
        btnQuitar.setFont(FONT_CONFIG);
        btnQuitar.setMargin(new Insets(0, 4, 0, 4));
        btnQuitar.addActionListener(e -> quitar(insumo));
        fila.add(lbl);
        fila.add(btnQuitar);
        return fila;
    }

    /** El combo nunca ofrece un insumo ya elegido: por eso el "+" no puede duplicar. */
    private void actualizarCombo() {
        combo.removeAllItems();
        for (InsumoCatalogo i : catalogo) {
            if (!yaElegido(i.id())) combo.addItem(i);
        }
        combo.setSelectedItem(null);
    }

    private boolean yaElegido(int id) {
        return seleccionados.stream().anyMatch(i -> i.id() == id);
    }

    private void notificarCambio() {
        if (onCambio != null) onCambio.run();
    }
}
