package com.example.features.lavadero.view;

import com.example.features.lavadero.model.ElementoCicloItem;
import com.example.features.lavadero.model.JabonCatalogo;
import com.example.features.lavadero.model.TipoLavado;
import com.example.features.lavadero.view.helpers.LavarropasCardTableModel;
import com.example.ui.common.RestriccionesCampo;
import com.example.ui.common.TableStyler;
import com.example.ui.common.dnd.TableSelectionSupport;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class LavarropasCard extends JPanel {

    private static final Font  FONT_CARD    = new Font(Font.SANS_SERIF, Font.BOLD, 11);
    private static final Font  FONT_CONFIG  = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    private static final Color COLOR_OCUPADO = new Color(0xC8C8C8);

    private final int numero;
    private final LavarropasCardTableModel tableModel = new LavarropasCardTableModel();
    private final JTable tabla;

    private final JPanel panelNorth = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
    private final JLabel lblToggle  = new JLabel("▼ ");
    private final JLabel lblTitulo;
    private final JLabel lblEstado  = new JLabel("[LIBRE]");

    private final JComboBox<TipoLavado>    cmbTipoLavado    = new JComboBox<>(TipoLavado.values());
    private final JComboBox<JabonCatalogo> cmbJabon         = new JComboBox<>();
    private final JTextField              txtLitrosJabon   = new JTextField(4);
    private final JCheckBox               chkSuavizante    = new JCheckBox("Suavizante");
    private final JCheckBox               chkPotenciador   = new JCheckBox("Potenciador");
    private final JTextField           txtLitrosTotales = new JTextField(4);
    private final JButton              btnAccion        = new JButton("Lanzar");
    private final JPanel               panelConfig;

    // Promovidos a campo para poder mostrar/ocultar desde toggleColapso()
    private final JScrollPane scrollTabla;
    private final JPanel      panelSouth;

    private boolean activo      = false;
    private Integer cicloActivo = null;
    private boolean collapsed   = false;

    private Runnable onAccion;
    private Runnable onConfiguracionChanged;

    public LavarropasCard(int numero) {
        this.numero    = numero;
        this.lblTitulo = new JLabel("Lavarropas #" + numero);

        setLayout(new BorderLayout(0, 2));
        setBorder(BorderFactory.createLineBorder(Color.GRAY));
        setBackground(Color.WHITE);

        // North: toggle + título + estado
        lblToggle.setFont(FONT_CARD);
        lblTitulo.setFont(FONT_CARD);
        lblEstado.setFont(FONT_CONFIG);
        panelNorth.setOpaque(true);
        panelNorth.add(lblToggle);
        panelNorth.add(lblTitulo);
        panelNorth.add(lblEstado);
        panelNorth.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { toggleColapso(); }
        });
        add(panelNorth, BorderLayout.NORTH);

        // Center: table
        tabla = new JTable(tableModel);
        TableSelectionSupport.enableMultiSelection(tabla);
        TableStyler.applyStandard(tabla);
        TableStyler.centerColumns(tabla, 1);
        tabla.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        tabla.setFillsViewportHeight(true);
        scrollTabla = new JScrollPane(tabla,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollTabla.setMinimumSize(new Dimension(0, 60));
        scrollTabla.setPreferredSize(new Dimension(0, 90));
        add(scrollTabla, BorderLayout.CENTER);

        // South: config + button
        panelConfig = buildConfigPanel();
        panelSouth = new JPanel(new BorderLayout());
        panelSouth.add(panelConfig, BorderLayout.CENTER);
        btnAccion.setFont(FONT_CARD);
        btnAccion.setEnabled(false);
        btnAccion.addActionListener(e -> { if (onAccion != null) onAccion.run(); });
        panelSouth.add(btnAccion, BorderLayout.SOUTH);
        add(panelSouth, BorderLayout.SOUTH);

        actualizarToggle();

        // Colapsar por defecto: todos los cards arrancan libres y vacíos
        collapsed = true;
        aplicarColapso();
    }

    /**
     * Se estira a lo ancho de su columna pero nunca a lo alto: sin este límite, el
     * {@code BoxLayout} de {@code construirGrillaDeCards()} reparte el espacio sobrante de la
     * columna entre todas las cards (no solo en el glue final), dejando un hueco en blanco
     * debajo incluso con la card contraída.
     */
    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    private JPanel buildConfigPanel() {
        JPanel config = new JPanel();
        config.setLayout(new BoxLayout(config, BoxLayout.Y_AXIS));
        config.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        for (JComponent c : new JComponent[]{cmbTipoLavado, cmbJabon, txtLitrosJabon,
                                            chkSuavizante, chkPotenciador, txtLitrosTotales}) {
            c.setFont(FONT_CONFIG);
        }
        RestriccionesCampo.soloNumerosDecimales(txtLitrosJabon);
        RestriccionesCampo.soloNumerosDecimales(txtLitrosTotales);

        // Obligatorios y sin default: arrancan vacíos para forzar una elección explícita.
        cmbTipoLavado.setSelectedItem(null);
        cmbTipoLavado.addActionListener(e -> notificarConfiguracionChanged());
        cmbJabon.addActionListener(e -> notificarConfiguracionChanged());

        config.add(rowPanel("Tipo:", cmbTipoLavado));
        config.add(rowPanel("Jabón:", cmbJabon));
        config.add(rowPanel("mL Jabón:", txtLitrosJabon));
        JPanel chkRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 1));
        chkRow.add(chkSuavizante);
        chkRow.add(chkPotenciador);
        config.add(chkRow);
        config.add(rowPanel("L Tot.:", txtLitrosTotales));

        txtLitrosJabon.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { notificarConfiguracionChanged(); }
            @Override public void removeUpdate(DocumentEvent e)  { notificarConfiguracionChanged(); }
            @Override public void changedUpdate(DocumentEvent e) { notificarConfiguracionChanged(); }
        });

        return config;
    }

    /** Un solo canal para todos los campos obligatorios de la config (tipo, jabón y mL de jabón). */
    private void notificarConfiguracionChanged() {
        if (onConfiguracionChanged != null) SwingUtilities.invokeLater(onConfiguracionChanged);
    }

    private static JPanel rowPanel(String labelText, JComponent field) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 1));
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(FONT_CONFIG);
        row.add(lbl);
        row.add(field);
        return row;
    }

    // ── Colapso ──────────────────────────────────────────────────────────────

    public boolean puedeContraerse() {
        return activo || !tieneItems();
    }

    public void toggleColapso() {
        if (!puedeContraerse()) return;
        collapsed = !collapsed;
        aplicarColapso();
    }

    public void colapsarSiPuede() {
        if (puedeContraerse() && !collapsed) {
            collapsed = true;
            aplicarColapso();
        }
    }

    private void aplicarColapso() {
        scrollTabla.setVisible(!collapsed);
        panelSouth.setVisible(!collapsed);
        lblToggle.setText(collapsed ? "▶ " : "▼ ");
        revalidate();
        repaint();
    }

    private void actualizarToggle() {
        boolean puede = puedeContraerse();
        lblToggle.setVisible(puede);
        panelNorth.setCursor(puede
            ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            : Cursor.getDefaultCursor());

        // Si acaba de recibir ítems en staging, forzar expansión
        if (!puede && collapsed) {
            collapsed = false;
            aplicarColapso();
        } else {
            lblToggle.setText(collapsed ? "▶ " : "▼ ");
        }
    }

    // ── Estado ───────────────────────────────────────────────────────────────

    public void setModoActivo(int cicloId) {
        this.activo      = true;
        this.cicloActivo = cicloId;
        lblEstado.setText("[OCUPADO]");
        panelNorth.setBackground(COLOR_OCUPADO);
        panelConfig.setVisible(false);
        btnAccion.setText("Finalizar");
        btnAccion.setEnabled(true);
        actualizarToggle();
        revalidate();
        repaint();
    }

    public void setModoStaging() {
        this.activo      = false;
        this.cicloActivo = null;
        lblEstado.setText("[LIBRE]");
        panelNorth.setBackground(UIManager.getColor("Panel.background"));
        panelConfig.setVisible(true);
        btnAccion.setText("Lanzar");
        actualizarBtnAccion();
        actualizarToggle();
        revalidate();
        repaint();
    }

    public boolean estaActivo()     { return activo; }
    public Integer getCicloActivo() { return cicloActivo; }

    /**
     * Deja la configuración (tipo de lavado, jabón, mililitros, suavizante, potenciador,
     * litros totales) en su estado inicial. Se llama al abrir la pantalla, no en cada
     * refresco: pisar esto durante un lanzamiento borraría lo que el operador está
     * tipeando en otra card.
     */
    public void resetConfiguracion() {
        cmbTipoLavado.setSelectedItem(null);
        cmbJabon.setSelectedItem(null);
        txtLitrosJabon.setText("");
        chkSuavizante.setSelected(false);
        chkPotenciador.setSelected(false);
        txtLitrosTotales.setText("");
        actualizarBtnAccion();
    }

    // ── Datos ────────────────────────────────────────────────────────────────

    public void setItems(List<ElementoCicloItem> items, Map<Integer, Integer> fracciones) {
        tableModel.setItems(
            items      != null ? items      : Collections.emptyList(),
            fracciones != null ? fracciones : Collections.emptyMap()
        );
        actualizarToggle();
    }

    public boolean tieneItems() { return tableModel.getRowCount() > 0; }

    // ── Config getters ────────────────────────────────────────────────────────

    /** Puede ser {@code null}: el combo arranca sin selección y el tipo es obligatorio. */
    public TipoLavado getTipoLavado() { return (TipoLavado) cmbTipoLavado.getSelectedItem(); }

    public JabonCatalogo getJabon()  { return (JabonCatalogo) cmbJabon.getSelectedItem(); }

    /**
     * Deja el combo sin selección después de llenarlo: {@code addItem} autoselecciona el primer
     * ítem, y el jabón es una elección explícita del operador como el tipo de lavado, no un
     * default que se lleva puesto sin mirar.
     */
    public void setJabones(java.util.List<JabonCatalogo> jabones) {
        cmbJabon.removeAllItems();
        for (JabonCatalogo j : jabones) cmbJabon.addItem(j);
        cmbJabon.setSelectedItem(null);
    }

    public BigDecimal getLitrosJabon() {
        try {
            String t = txtLitrosJabon.getText().trim().replace(",", ".");
            if (t.isEmpty()) return null;
            BigDecimal v = new BigDecimal(t);
            return v.compareTo(BigDecimal.ZERO) > 0 ? v : null;
        } catch (NumberFormatException e) { return null; }
    }

    public boolean isSuavizante()   { return chkSuavizante.isSelected(); }
    public boolean isPotenciador()  { return chkPotenciador.isSelected(); }

    public BigDecimal getLitrosTotales() {
        try {
            String t = txtLitrosTotales.getText().trim().replace(",", ".");
            if (t.isEmpty()) return null;
            BigDecimal v = new BigDecimal(t);
            return v.compareTo(BigDecimal.ZERO) > 0 ? v : null;
        } catch (NumberFormatException e) { return null; }
    }

    // ── DnD ──────────────────────────────────────────────────────────────────

    public JTable getTabla() { return tabla; }

    /** Ítems de las filas seleccionadas en la tabla de la card. */
    public List<ElementoCicloItem> getItemsSeleccionados() {
        return TableSelectionSupport.selectedItems(tabla, tableModel::getItemAt);
    }

    // ── Listeners ────────────────────────────────────────────────────────────

    public void setOnAccion(Runnable r)               { this.onAccion = r; }
    public void setOnConfiguracionChanged(Runnable r) { this.onConfiguracionChanged = r; }

    /**
     * Apaga el botón de acción mientras hay una escritura en vuelo, para que un segundo
     * click no vuelva a lanzar (o finalizar) lo mismo. No hace falta volver a encenderlo a
     * mano: el refresco posterior repinta la card y {@link #actualizarBtnAccion()} recalcula.
     */
    public void deshabilitarAccion() {
        btnAccion.setEnabled(false);
    }

    /**
     * Los tres campos obligatorios del ciclo: tipo de lavado, jabón y mililitros de jabón. Es
     * la <b>única</b> definición de "config completa" de la pantalla: la usa esta card para
     * decidir si se puede lanzar y {@code CiclosController} para validar los grupos repartidos.
     */
    public boolean tieneConfiguracionCompleta() {
        return getTipoLavado() != null && getJabon() != null && getLitrosJabon() != null;
    }

    public void actualizarBtnAccion() {
        if (activo) {
            btnAccion.setEnabled(true);
        } else {
            btnAccion.setEnabled(tieneItems() && tieneConfiguracionCompleta());
        }
    }
}
