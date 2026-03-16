package com.example.features.equipos.otros.view.helpers;

import com.example.common.constants.Constantes;
import com.example.ui.common.Estilos;

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Panel de materiales para el ingreso de tipo "Otros".
 *
 * Diferencias respecto a {@link com.example.features.equipos.view.helpers.PanelMateriales}:
 * - El primer campo de cada fila es un {@link JTextField} de texto libre (descripción).
 * - Al escribir, dispara un autocomplete con sugerencias de {@code catalogo_otros}
 *   a partir de 1 carácter, con popup flotante idéntico al {@code AutocompleteListener}.
 * - No hay campo de código numérico.
 * - No hay lógica de duplicados por código (no aplica para texto libre).
 *
 * La comunicación con el controller se hace mediante un callback:
 * {@code setOnDescripcionChangedListener(BiConsumer<String, Consumer<List<String>>>)}
 * El controller recibe el texto y un Consumer para entregar las sugerencias de vuelta.
 */
public class PanelMaterialesOtros extends JPanel {

    // ── Estado ────────────────────────────────────────────────────────────────
    private final List<OtrosMaterialRow> filas = new ArrayList<>();
    private JLabel  lblTotal;
    private final Font  inputFont;
    private final int   inputHeight;
    private JPanel listPanel;

    /**
     * Callback para el autocomplete de descripción.
     * El controller recibe: (textoEscrito, consumerDeSugerencias)
     * Puede ser null si no hay controller configurado aún.
     */
    private BiConsumer<String, java.util.function.Consumer<List<String>>> onDescripcionChangedListener;

    private static final int MAX_VISIBLE_ROWS = 10;

    // ── Constructor ───────────────────────────────────────────────────────────

    public PanelMaterialesOtros(Font inputFont, int inputHeight) {
        this.inputFont   = inputFont;
        this.inputHeight = inputHeight;

        setLayout(new BorderLayout(5, 5));

        listPanel = new JPanel(new GridBagLayout());
        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);

        lblTotal = new JLabel(String.format(Constantes.Textos.TOTAL_ELEMENTOS, 0));
        lblTotal.setFont(Estilos.Fuentes.LABEL);
        JPanel panelTotal = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 5));
        panelTotal.add(lblTotal);

        JButton btnAgregar  = new JButton(Constantes.Botones.AGREGAR);
        JButton btnEliminar = new JButton(Constantes.Botones.ELIMINAR);
        btnAgregar.setFont(Estilos.Fuentes.BOTON_PEQUENO);
        btnEliminar.setFont(Estilos.Fuentes.BOTON_PEQUENO);
        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        panelBotones.add(btnEliminar);
        panelBotones.add(btnAgregar);

        JPanel panelSur = new JPanel(new BorderLayout());
        panelSur.add(panelTotal,   BorderLayout.WEST);
        panelSur.add(panelBotones, BorderLayout.EAST);
        add(panelSur, BorderLayout.SOUTH);

        agregarFila();

        btnAgregar.addActionListener(e -> {
            agregarFila();
            listPanel.revalidate();
            listPanel.repaint();
        });
        btnEliminar.addActionListener(e -> {
            eliminarUltimaFila();
            listPanel.revalidate();
            listPanel.repaint();
        });
    }

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Registra el callback de autocomplete.
     * Debe llamarse desde el controller antes de que el usuario empiece a escribir.
     */
    public void setOnDescripcionChangedListener(
            BiConsumer<String, java.util.function.Consumer<List<String>>> listener) {
        this.onDescripcionChangedListener = listener;
    }

    public List<OtrosMaterialRow> getFilas() {
        return filas;
    }

    /** Resetea el panel a una sola fila vacía. */
    public void limpiar() {
        while (!filas.isEmpty()) eliminarFila(filas.get(filas.size() - 1));
        agregarFila();
        listPanel.revalidate();
        listPanel.repaint();
        actualizarTotal();
    }

    // ── Construcción de filas ─────────────────────────────────────────────────

    private void agregarFila() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 0, 5, 10);
        gbc.fill   = GridBagConstraints.HORIZONTAL;
        int rowIdx = filas.size();

        // Campo descripción (texto libre con autocomplete)
        JTextField txtDesc = new JTextField();
        txtDesc.setFont(inputFont);
        txtDesc.setColumns(35);
        txtDesc.setMargin(Estilos.Espaciados.INSETS_INPUT);
        txtDesc.setPreferredSize(new Dimension(250, inputHeight));
        txtDesc.setMinimumSize(new Dimension(120, inputHeight));

        // Popup de autocomplete
        JPopupMenu popup           = new JPopupMenu();
        DefaultListModel<String> listModel = new DefaultListModel<>();
        JList<String> lista        = new JList<>(listModel);
        lista.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollPopup    = new JScrollPane(lista);
        scrollPopup.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPopup.setPreferredSize(new Dimension(250, 0));
        popup.add(scrollPopup);
        popup.setFocusable(false);

        // Listener de documento → actualizar sugerencias
        txtDesc.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { actualizarSugerencias(txtDesc, popup, listModel, scrollPopup, lista); }
            @Override public void removeUpdate(DocumentEvent e)  { actualizarSugerencias(txtDesc, popup, listModel, scrollPopup, lista); }
            @Override public void changedUpdate(DocumentEvent e) {}
        });

        // Selección con mouse
        lista.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) seleccionarSugerencia(txtDesc, lista, popup);
            }
        });

        // Navegación con teclado desde el campo
        txtDesc.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (!popup.isVisible()) return;
                int idx  = lista.getSelectedIndex();
                int size = listModel.getSize();
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_DOWN:
                        if (size > 0) { lista.setSelectedIndex((idx + 1) % size); lista.ensureIndexIsVisible(lista.getSelectedIndex()); }
                        e.consume(); break;
                    case KeyEvent.VK_UP:
                        if (size > 0) { lista.setSelectedIndex((idx - 1 + size) % size); lista.ensureIndexIsVisible(lista.getSelectedIndex()); }
                        e.consume(); break;
                    case KeyEvent.VK_ENTER:
                        if (idx >= 0) { seleccionarSugerencia(txtDesc, lista, popup); e.consume(); }
                        break;
                    case KeyEvent.VK_ESCAPE:
                        popup.setVisible(false); e.consume(); break;
                }
            }
        });

        // Spinner cantidad (idéntico al de PanelMateriales)
        SpinnerNumberModel spModel = new SpinnerNumberModel(1.0, 1.0, null, 1.0);
        JSpinner spCantidad = new JSpinner(spModel);
        spCantidad.setFont(inputFont);
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spCantidad, Constantes.Formatos.FORMATO_SPINNER_CANTIDAD);
        spCantidad.setEditor(editor);
        editor.getTextField().setColumns(4);
        spCantidad.addChangeListener(ev -> actualizarTotal());

        // Botón eliminar fila
        JButton btnDel = new JButton(Constantes.Botones.ELIMINAR_FILA);
        btnDel.setFont(Estilos.Fuentes.BOTON_PEQUENO);
        btnDel.setPreferredSize(new Dimension(45, inputHeight));
        btnDel.setMargin(new Insets(0, 0, 0, 0));
        btnDel.setToolTipText(Constantes.Textos.TOOLTIP_ELIMINAR_FILA);

        OtrosMaterialRow fila = new OtrosMaterialRow(txtDesc, spCantidad, btnDel);

        btnDel.addActionListener(e -> eliminarFila(fila));

        // Agregar componentes al panel
        gbc.gridx = 0; gbc.gridy = rowIdx; gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        listPanel.add(txtDesc, gbc);

        gbc.gridx = 1; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        listPanel.add(spCantidad, gbc);

        gbc.gridx = 2;
        gbc.insets = new Insets(5, 5, 5, 0);
        listPanel.add(btnDel, gbc);
        gbc.insets = new Insets(5, 0, 5, 10); // reset

        filas.add(fila);
        actualizarTotal();
    }

    private void actualizarSugerencias(JTextField txtDesc, JPopupMenu popup,
                                        DefaultListModel<String> listModel,
                                        JScrollPane scrollPopup, JList<String> lista) {
        String texto = txtDesc.getText().trim();
        if (texto.isEmpty()) {
            popup.setVisible(false);
            return;
        }
        if (onDescripcionChangedListener == null) return;

        // Pedir sugerencias al controller de forma síncrona en el EDT
        onDescripcionChangedListener.accept(texto, sugerencias -> {
            listModel.clear();
            if (sugerencias == null || sugerencias.isEmpty()) {
                popup.setVisible(false);
                return;
            }
            for (String s : sugerencias) listModel.addElement(s);
            lista.setSelectedIndex(0);

            int visibles = Math.min(listModel.getSize(), MAX_VISIBLE_ROWS);
            int cellH    = 22; // estimado conservador
            scrollPopup.setPreferredSize(new Dimension(txtDesc.getWidth(), visibles * cellH + 2));
            scrollPopup.revalidate();

            if (!popup.isVisible()) {
                popup.show(txtDesc, 0, txtDesc.getHeight());
            } else {
                popup.pack();
            }
        });
    }

    private void seleccionarSugerencia(JTextField txtDesc, JList<String> lista, JPopupMenu popup) {
        String seleccionada = lista.getSelectedValue();
        if (seleccionada != null) {
            txtDesc.setText(seleccionada);
            popup.setVisible(false);
        }
    }

    private void eliminarUltimaFila() {
        if (!filas.isEmpty()) eliminarFila(filas.get(filas.size() - 1));
    }

    private void eliminarFila(OtrosMaterialRow fila) {
        if (filas.remove(fila)) {
            listPanel.remove(fila.txtDescripcion);
            listPanel.remove(fila.spCantidad);
            listPanel.remove(fila.btnEliminar);
            listPanel.revalidate();
            listPanel.repaint();
            actualizarTotal();
        }
    }

    private void actualizarTotal() {
        double total = filas.stream()
            .mapToDouble(f -> {
                Object v = f.spCantidad.getValue();
                return (v instanceof Number) ? ((Number) v).doubleValue() : 0;
            }).sum();
        lblTotal.setText(String.format(Constantes.Textos.TOTAL_ELEMENTOS, (int) total));
    }

    // ── Clase de fila ─────────────────────────────────────────────────────────

    public static class OtrosMaterialRow {
        public final JTextField txtDescripcion;
        public final JSpinner   spCantidad;
        public final JButton    btnEliminar;

        public OtrosMaterialRow(JTextField txtDescripcion, JSpinner spCantidad, JButton btnEliminar) {
            this.txtDescripcion = txtDescripcion;
            this.spCantidad     = spCantidad;
            this.btnEliminar    = btnEliminar;
        }
    }
}