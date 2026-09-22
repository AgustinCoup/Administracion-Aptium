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

    /**
     * Número de lavarropas → su card, en el orden en que se dibujan. El orden de inserción es
     * parte del contrato de {@link #getAllCards()}: {@link #reconstruirGrilla} lo repuebla desde
     * cero, ascendente, en vez de agregarle los nuevos al final.
     */
    private Map<Integer, LavarropasCard> cards = new LinkedHashMap<>();

    /** Contenedor de las columnas de cards. Se vacía y se rehace en cada reconstrucción. */
    private final JPanel panelCards =
        new JPanel(new GridLayout(1, Constantes.Lavadero.LAVARROPAS_POR_FILA, 8, 0));

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

        // La grilla nace vacía: qué lavarropas hay es un dato de la base, y llega con el
        // primer pintado vía reconstruirGrilla(). Ninguna card se crea en el constructor.
        panelCards.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

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
     * Rehace la grilla de cards para los lavarropas de {@code numeros}, en
     * {@code LAVARROPAS_POR_FILA} columnas independientes ("masonry"): cada columna es su propio
     * {@code BoxLayout} vertical, así que expandir una card sólo empuja hacia abajo a las demás
     * cards de su misma columna, sin dejar hueco en las columnas vecinas (a diferencia de un grid
     * de filas compartidas, donde una card alta infla toda la fila).
     *
     * <p><b>Reusa la card de cada número que ya existía</b>: sólo crea las nuevas y descarta las
     * que se fueron. Recrearlas todas sería más corto y borraría la configuración que el operador
     * está tipeando en cards que no cambiaron — dar de baja el #13 no puede vaciarle el tipo de
     * lavado a medio cargar del #1. Es el mismo invariante que hace que {@code recargar()} no
     * llame a {@code resetConfiguracion()}, entrando por otra puerta.</p>
     *
     * <p>El mapa se <b>repuebla desde cero</b> en el orden de {@code numeros}, en vez de hacerle
     * {@code put} de los nuevos al final del mapa viejo: es un {@code LinkedHashMap} y su orden de
     * inserción es lo que ve {@code getAllCards()}. Un mapa desordenado dibujaría las columnas
     * fuera de orden y haría fallar cualquier comparación por lista contra los números leídos.</p>
     *
     * <p>Quien la llama tiene que volver a cablear las cards ({@code setOnAccion}, DnD, catálogos):
     * el mapa que devuelve {@code getAllCards()} es otro. Todos esos cableados son <i>setters</i>,
     * así que correrlos sobre el mapa entero es idempotente.</p>
     */
    public void reconstruirGrilla(List<Integer> numeros) {
        final int porFila = Constantes.Lavadero.LAVARROPAS_POR_FILA;

        panelCards.removeAll();
        JPanel[] columnas = new JPanel[porFila];
        for (int c = 0; c < porFila; c++) {
            columnas[c] = new JPanel();
            columnas[c].setLayout(new BoxLayout(columnas[c], BoxLayout.Y_AXIS));
            panelCards.add(columnas[c]);
        }

        Map<Integer, LavarropasCard> anteriores = cards;
        Map<Integer, LavarropasCard> nuevas = new LinkedHashMap<>();
        int posicion = 0;
        for (int numero : numeros) {
            LavarropasCard card = anteriores.get(numero);
            if (card == null) {
                card = new LavarropasCard(numero);
                card.setAlignmentX(Component.LEFT_ALIGNMENT);
            }
            nuevas.put(numero, card);
            JPanel columna = columnas[posicion++ % porFila];
            if (columna.getComponentCount() > 0) {
                columna.add(Box.createVerticalStrut(8));
            }
            columna.add(card);
        }
        for (JPanel columna : columnas) {
            columna.add(Box.createVerticalGlue());
        }
        cards = nuevas;

        panelCards.revalidate();
        panelCards.repaint();
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

    /** Cablea el botón "Actualizar" (y F5) del header a la relectura de la pantalla. */
    public void setAccionRefrescar(Runnable accion) {
        header.setAccionRefrescar(accion);
    }

    /**
     * Guarda del botón "Actualizar": pregunta antes de refrescar si hay staging sin
     * lanzar. En Ciclos la config tipeada <b>se conserva</b> ({@code recargar()} no la
     * toca), así que {@code onDescartar} va en {@code null}.
     */
    public void setGuardRefresco(Supplier<Boolean> hayPendientes, String mensaje, Runnable onDescartar) {
        header.setGuardRefresco(hayPendientes, mensaje, onDescartar);
    }

    /** Muestra la hora del último pintado en el header. */
    public void marcarActualizado() {
        header.marcarActualizado();
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
