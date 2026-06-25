package com.example.features.lavadero.view;

import com.example.common.constants.Constantes;
import com.example.features.lavadero.model.ElementoCicloItem;
import com.example.features.lavadero.view.helpers.ElementoDisponibleTableModel;
import com.example.ui.common.Estilos;
import com.example.ui.common.LabelFactory;
import com.example.ui.common.PanelHeader;
import com.example.ui.common.TableStyler;

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

        tablaDisponibles = buildTable(modeloDisponibles, 1, 2);

        JPanel panelTop = new JPanel(new BorderLayout());
        panelTop.add(LabelFactory.createSectionLabel("Elementos disponibles para lavar"),
            BorderLayout.NORTH);
        panelTop.add(scroll(tablaDisponibles), BorderLayout.CENTER);

        // GridBagLayout con fill=HORIZONTAL y weighty=0: cada card conserva su propio
        // preferred height (no se estira verticalmente) y se alinea al tope de su celda.
        // Cuando todos los cards de una fila están contraídos la fila mide ~28px.
        JPanel panelCards = new JPanel(new GridBagLayout());
        panelCards.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill    = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.2;
        gbc.weighty = 0.0;
        gbc.anchor  = GridBagConstraints.NORTH;
        gbc.insets  = new Insets(4, 4, 4, 4);

        for (int i = 1; i <= 13; i++) {
            gbc.gridx = (i - 1) % 5;
            gbc.gridy = (i - 1) / 5;
            LavarropasCard card = new LavarropasCard(i);
            cards.put(i, card);
            panelCards.add(card, gbc);
        }
        // Relleno para las dos posiciones vacías de la última fila
        gbc.gridx = 3; gbc.gridy = 2; panelCards.add(new JPanel(), gbc);
        gbc.gridx = 4; gbc.gridy = 2; panelCards.add(new JPanel(), gbc);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            panelTop,
            new JScrollPane(panelCards,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER));
        split.setResizeWeight(0.35);
        add(split, BorderLayout.CENTER);

        for (JButton btn : new JButton[]{btnDescartarTodos, btnLanzarTodos}) {
            btn.setFont(Estilos.Fuentes.BOTON);
            btn.setEnabled(false);
        }
        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        panelBotones.setBorder(Estilos.Espaciados.BORDE_PRINCIPAL);
        panelBotones.add(btnDescartarTodos);
        panelBotones.add(btnLanzarTodos);
        add(panelBotones, BorderLayout.SOUTH);
    }

    private JTable buildTable(javax.swing.table.AbstractTableModel model, int... centeredCols) {
        JTable t = new JTable(model);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
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

    public ElementoCicloItem getElementoDisponibleSeleccionado() {
        int row = tablaDisponibles.getSelectedRow();
        return row >= 0 ? modeloDisponibles.getItemAt(row) : null;
    }

    // ── Cards ─────────────────────────────────────────────────────────────────

    public LavarropasCard getCard(int lavarropasNumero) { return cards.get(lavarropasNumero); }

    public Map<Integer, LavarropasCard> getAllCards() {
        return Collections.unmodifiableMap(cards);
    }

    // ── Botones globales ──────────────────────────────────────────────────────

    public JButton getBtnLanzarTodos()    { return btnLanzarTodos; }
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
