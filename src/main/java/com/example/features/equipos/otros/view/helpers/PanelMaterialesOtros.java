package com.example.features.equipos.otros.view.helpers;

import com.example.common.constants.Constantes;
import com.example.ui.common.Hotkeys;
import com.example.ui.common.AutocompleteListener;
import com.example.ui.common.DuplicadoHighlighter;
import com.example.ui.common.Estilos;
import com.example.ui.common.FilterUiHelper;
import com.example.ui.dialogs.NuevoElementoDialog;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import javax.swing.*;

/**
 * Panel de materiales para el ingreso de tipo "Otros".
 *
 * Diferencias respecto a {@link com.example.features.equipos.ortopedias.view.helpers.PanelMateriales}:
 * - El primer campo de cada fila es un {@link JTextField} de texto libre (descripción).
 * - Al escribir dispara autocompletado contra {@code catalogo_otros} desde 1 carácter.
 * - Al perder el foco con texto no presente en el catálogo abre {@link NuevoElementoDialog}.
 * - No hay código numérico: la identidad de la fila es la descripción normalizada
 *   (trim + minúsculas). Igual que en ortopedias, {@link #tieneDuplicados()} resalta
 *   en rojo las filas en conflicto y permite al Controller bloquear el guardado.
 */
public class PanelMaterialesOtros extends JPanel {

    // ── Estado ────────────────────────────────────────────────────────────────
    private final List<OtrosMaterialRow> filas = new ArrayList<>();
    private JLabel  lblTotal;
    private final Font  inputFont;
    private final int   inputHeight;
    private JPanel listPanel;

    // Color de fondo normal de los JTextField (se captura la primera vez que se crea uno)
    private Color colorNormal = null;

    private Function<String, List<String>> buscarFn;
    private Function<String, Boolean>      verificarFn;

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
        btnEliminar.addActionListener(e -> eliminarUltimaFila());

        Hotkeys.registrarMateriales(this,
            () -> { agregarFila(); listPanel.revalidate(); listPanel.repaint(); },
            () -> eliminarUltimaFila()
        );
    }

    // ── API pública ───────────────────────────────────────────────────────────

    public void configurarAutocompletado(Function<String, List<String>> buscar,
                                         Function<String, Boolean> verificar) {
        this.buscarFn    = buscar;
        this.verificarFn = verificar;
    }

    public List<OtrosMaterialRow> getFilas() {
        return filas;
    }

    // ── Validación de duplicados ─────────────────────────────────────────────

    /**
     * Verifica si existe alguna descripción repetida (normalizada: trim +
     * minúsculas) entre las filas actuales. Actualiza el color de fondo y
     * tooltip de cada campo descripción igual que {@code PanelMateriales}.
     *
     * Llamar desde el Controller antes de intentar guardar el equipo.
     *
     * @return true si hay al menos una descripción duplicada
     */
    public boolean tieneDuplicados() {
        List<JTextField> campos = new ArrayList<>();
        for (OtrosMaterialRow fila : filas) campos.add(fila.txtDescripcion);

        return DuplicadoHighlighter.marcar(
            campos,
            desc -> desc.trim().toLowerCase(),
            colorNormal,
            "Material duplicado: unifique estas filas antes de guardar");
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

    void agregarFila() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 0, 5, 10);
        gbc.fill   = GridBagConstraints.HORIZONTAL;
        int rowIdx = filas.size();

        JTextField txtDesc = new JTextField();
        txtDesc.setFont(inputFont);
        txtDesc.setColumns(35);
        txtDesc.setMargin(Estilos.Espaciados.INSETS_INPUT);
        txtDesc.setPreferredSize(new Dimension(250, inputHeight));
        txtDesc.setMinimumSize(new Dimension(120, inputHeight));

        // Capturar el color normal la primera vez
        if (colorNormal == null) {
            colorNormal = txtDesc.getBackground();
        }
        FilterUiHelper.bindOnTextChange(this::tieneDuplicados, txtDesc);

        // confirmedText rastrea el último valor aceptado para evitar re-preguntar
        // si el usuario mueve el foco sin haber cambiado el texto.
        String[] confirmedText = {null};

        new AutocompleteListener<>(
            txtDesc,
            text -> buscarFn != null ? buscarFn.apply(text) : List.of(),
            s    -> confirmedText[0] = s,
            text -> {
                if (verificarFn == null) return;
                if (text.equals(confirmedText[0])) return;
                if (verificarFn.apply(text)) { confirmedText[0] = text; return; }
                Window w = SwingUtilities.getWindowAncestor(PanelMaterialesOtros.this);
                NuevoElementoDialog d = new NuevoElementoDialog(
                    w, Constantes.Textos.ENTIDAD_CATALOGO_OTROS, text);
                d.setVisible(true);
                String nombre = d.obtenerResultado();
                if (nombre != null) { confirmedText[0] = nombre; txtDesc.setText(nombre); }
                else                { confirmedText[0] = null;   txtDesc.setText(""); }
            },
            1 /* minChars */
        );

        SpinnerNumberModel spModel = new SpinnerNumberModel(1.0, 1.0, null, 1.0);
        JSpinner spCantidad = new JSpinner(spModel);
        spCantidad.setFont(inputFont);
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spCantidad, Constantes.Formatos.FORMATO_SPINNER_CANTIDAD);
        spCantidad.setEditor(editor);
        editor.getTextField().setColumns(4);
        spCantidad.addChangeListener(ev -> actualizarTotal());

        JButton btnDel = new JButton(Constantes.Botones.ELIMINAR_FILA);
        btnDel.setFont(Estilos.Fuentes.BOTON_PEQUENO);
        btnDel.setPreferredSize(new Dimension(45, inputHeight));
        btnDel.setMargin(new Insets(0, 0, 0, 0));
        btnDel.setToolTipText(Constantes.Textos.TOOLTIP_ELIMINAR_FILA);

        OtrosMaterialRow fila = new OtrosMaterialRow(txtDesc, spCantidad, btnDel);
        btnDel.addActionListener(e -> {
            eliminarFila(fila);
            // Re-evaluar duplicados por si la fila eliminada resolvía un conflicto
            tieneDuplicados();
        });

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
