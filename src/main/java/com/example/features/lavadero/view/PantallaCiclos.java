package com.example.features.lavadero.view;

import com.example.common.constants.Constantes;
import com.example.features.lavadero.model.ElementoCicloItem;
import com.example.features.lavadero.view.helpers.ElementoDisponibleTableModel;
import com.example.ui.common.Estilos;
import com.example.ui.common.LabelFactory;
import com.example.ui.common.PanelHeader;
import com.example.ui.common.TableStyler;
import com.example.ui.common.dnd.TableSelectionSupport;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class PantallaCiclos extends JPanel {

    private final PanelHeader header;

    private final ElementoDisponibleTableModel modeloDisponibles = new ElementoDisponibleTableModel();
    private final JTable tablaDisponibles;

    private final Map<Integer, LavarropasCard> cards = new LinkedHashMap<>();

    private final JButton btnLanzarTodos    = new JButton(Constantes.Botones.LANZAR_TODOS);
    private final JButton btnFinalizarTodos = new JButton(Constantes.Botones.FINALIZAR_TODOS);
    private final JButton btnDescartarTodos = new JButton(Constantes.Botones.DESCARTAR_TODOS);

    public PantallaCiclos(CardLayout navegador, JPanel contenedor) {
        setLayout(new BorderLayout());

        header = new PanelHeader(
            Constantes.Titulos.CICLOS_LAVADERO,
            navegador,
            contenedor,
            Constantes.Pantallas.LAVADERO
        );
        add(header, BorderLayout.NORTH);

        tablaDisponibles = buildTable(modeloDisponibles, 1);

        JPanel panelTop = new JPanel(new BorderLayout());
        panelTop.add(LabelFactory.createSectionLabel("Elementos disponibles para lavar"),
            BorderLayout.NORTH);
        panelTop.add(scroll(tablaDisponibles), BorderLayout.CENTER);

        JPanel panelCards = construirGrillaDeCards();

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            panelTop,
            new JScrollPane(panelCards,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER));
        split.setResizeWeight(0.35);
        add(split, BorderLayout.CENTER);

        for (JButton btn : new JButton[]{btnDescartarTodos, btnLanzarTodos, btnFinalizarTodos}) {
            btn.setFont(Estilos.Fuentes.BOTON);
            btn.setEnabled(false);
        }
        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        panelBotones.setBorder(Estilos.Espaciados.BORDE_PRINCIPAL);
        panelBotones.add(btnDescartarTodos);
        panelBotones.add(btnLanzarTodos);
        panelBotones.add(btnFinalizarTodos);
        add(panelBotones, BorderLayout.SOUTH);
    }

    /**
     * Grilla de cards de lavarropas, en {@code LAVARROPAS_POR_FILA} columnas independientes
     * ("masonry"): cada columna es su propio {@code BoxLayout} vertical, así que expandir una
     * card sólo empuja hacia abajo a las demás cards de su misma columna, sin dejar hueco en
     * las columnas vecinas (a diferencia de un grid de filas compartidas, donde una card alta
     * infla toda la fila).
     */
    private JPanel construirGrillaDeCards() {
        final int porFila = Constantes.Lavadero.LAVARROPAS_POR_FILA;
        final int total   = Constantes.Lavadero.CANTIDAD_LAVARROPAS;

        JPanel panelCards = new JPanel(new GridLayout(1, porFila, 8, 0));
        panelCards.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel[] columnas = new JPanel[porFila];
        for (int c = 0; c < porFila; c++) {
            columnas[c] = new JPanel();
            columnas[c].setLayout(new BoxLayout(columnas[c], BoxLayout.Y_AXIS));
            panelCards.add(columnas[c]);
        }

        for (int i = 1; i <= total; i++) {
            JPanel columna = columnas[(i - 1) % porFila];
            LavarropasCard card = new LavarropasCard(i);
            card.setAlignmentX(Component.LEFT_ALIGNMENT);
            cards.put(i, card);
            if (columna.getComponentCount() > 0) {
                columna.add(Box.createVerticalStrut(8));
            }
            columna.add(card);
        }
        for (JPanel columna : columnas) {
            columna.add(Box.createVerticalGlue());
        }
        return panelCards;
    }

    private JTable buildTable(javax.swing.table.AbstractTableModel model, int... centeredCols) {
        JTable t = new JTable(model);
        TableSelectionSupport.enableMultiSelection(t);
        TableStyler.applyStandard(t);
        TableStyler.centerColumns(t, centeredCols);
        t.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        return t;
    }

    private static JScrollPane scroll(JComponent c) {
        return new JScrollPane(c,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    }

    // ── Disponibles ───────────────────────────────────────────────────────────

    public void setElementosDisponibles(List<ElementoCicloItem> items) {
        modeloDisponibles.setItems(items != null ? items : Collections.emptyList());
    }

    public JTable getTablaDisponibles() { return tablaDisponibles; }

    /** Elementos de las filas seleccionadas en la tabla de disponibles (origen del DnD). */
    public List<ElementoCicloItem> getElementosDisponiblesSeleccionados() {
        return TableSelectionSupport.selectedItems(tablaDisponibles, modeloDisponibles::getItemAt);
    }

    // ── Cards ─────────────────────────────────────────────────────────────────

    public LavarropasCard getCard(int lavarropasNumero) { return cards.get(lavarropasNumero); }

    public Map<Integer, LavarropasCard> getAllCards() {
        return Collections.unmodifiableMap(cards);
    }

    // ── Botones globales ──────────────────────────────────────────────────────

    public JButton getBtnLanzarTodos()    { return btnLanzarTodos; }
    public JButton getBtnFinalizarTodos() { return btnFinalizarTodos; }
    public JButton getBtnDescartarTodos() { return btnDescartarTodos; }

    // ── Guard y diálogos ─────────────────────────────────────────────────────

    public void setGuardVolver(Supplier<Boolean> hayPendientes, String mensaje, Runnable onDescartar) {
        header.setGuardNavegacion(hayPendientes, mensaje, onDescartar);
    }

    public boolean confirmar(String msg, String titulo) {
        return JOptionPane.showConfirmDialog(this, msg, titulo,
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    public void mostrarAdvertencia(String msg) {
        JOptionPane.showMessageDialog(this, msg,
            Constantes.Mensajes.TITULO_ADVERTENCIA, JOptionPane.WARNING_MESSAGE);
    }

    public void mostrarError(String msg) {
        JOptionPane.showMessageDialog(this, msg,
            Constantes.Mensajes.TITULO_ERROR, JOptionPane.ERROR_MESSAGE);
    }
}
