package com.example.features.lavadero.view;

import com.example.common.constants.Constantes;
import com.example.features.lavadero.model.ElementoCicloItem;
import com.example.features.lavadero.model.TipoJabon;
import com.example.features.lavadero.view.helpers.ElementoCargadoTableModel;
import com.example.features.lavadero.view.helpers.ElementoDisponibleTableModel;
import com.example.features.lavadero.view.helpers.LavarropasItem;
import com.example.features.lavadero.view.helpers.LavarropasTableModel;
import com.example.ui.common.Estilos;
import com.example.ui.common.LabelFactory;
import com.example.ui.common.PanelHeader;
import com.example.ui.common.TableStyler;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class PantallaCiclos extends JPanel {

    private final PanelHeader header;

    private final LavarropasTableModel        modeloLavarropas  = new LavarropasTableModel();
    private final ElementoDisponibleTableModel modeloDisponibles = new ElementoDisponibleTableModel();
    private final ElementoCargadoTableModel    modeloCiclo       = new ElementoCargadoTableModel();

    private final JTable tablaLavarropas;
    private final JTable tablaDisponibles;
    private final JTable tablaCiclo;

    private final JComboBox<TipoJabon> cmbTipoJabon     = new JComboBox<>(TipoJabon.values());
    private final JTextField           txtLitrosJabon   = new JTextField(6);
    private final JCheckBox            chkSuavizante    = new JCheckBox("Suavizante");
    private final JTextField           txtLitrosTotales = new JTextField(6);

    private final JButton btnQuitar         = new JButton(Constantes.Botones.QUITAR);
    private final JButton btnFinalizarCiclo = new JButton(Constantes.Botones.FINALIZAR_CICLO);
    private final JButton btnLanzarCiclo    = new JButton(Constantes.Botones.LANZAR_CICLO);

    private Consumer<LavarropasItem> onLavarropasSeleccionado;
    private Runnable onLitrosJabonChanged;

    private static final int ANCHO_LATERAL = 260;

    public PantallaCiclos(CardLayout navegador, JPanel contenedor) {
        setLayout(new BorderLayout());

        header = new PanelHeader(
            Constantes.Titulos.CICLOS_LAVADERO,
            navegador,
            contenedor,
            Constantes.Pantallas.LAVADERO
        );
        add(header, BorderLayout.NORTH);

        tablaLavarropas  = buildTable(modeloLavarropas, 0, 1, 2);
        tablaDisponibles = buildTable(modeloDisponibles, 1, 2);
        tablaCiclo       = buildTable(modeloCiclo, 1, 2);

        // ── Centro: disponibles arriba, ciclo abajo ─────────────────────────────
        JPanel centro = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets  = new Insets(5, 5, 5, 5);
        gbc.fill    = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weighty = 0;
        centro.add(LabelFactory.createSectionLabel("Elementos disponibles para lavar"), gbc);

        gbc.gridy = 1; gbc.weighty = 0.5;
        centro.add(scroll(tablaDisponibles), gbc);

        gbc.gridy = 2; gbc.weighty = 0;
        centro.add(LabelFactory.createSectionLabel("Elementos cargados en el ciclo"), gbc);

        gbc.gridy = 3; gbc.weighty = 0.5;
        centro.add(scroll(tablaCiclo), gbc);

        add(centro, BorderLayout.CENTER);

        // ── Lateral: lavarropas + config ────────────────────────────────────────
        JPanel lateral = new JPanel(new BorderLayout());
        lateral.setPreferredSize(new Dimension(ANCHO_LATERAL, 0));
        lateral.setMinimumSize(new Dimension(ANCHO_LATERAL, 0));

        lateral.add(LabelFactory.createSectionLabel("Lavarropas"), BorderLayout.NORTH);
        lateral.add(new JScrollPane(tablaLavarropas,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);
        lateral.add(buildConfigPanel(), BorderLayout.SOUTH);

        add(lateral, BorderLayout.EAST);

        // ── Botones ────────────────────────────────────────────────────────────
        for (JButton btn : new JButton[]{btnQuitar, btnFinalizarCiclo, btnLanzarCiclo}) {
            btn.setFont(Estilos.Fuentes.BOTON);
            btn.setEnabled(false);
        }

        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        panelBotones.setBorder(Estilos.Espaciados.BORDE_PRINCIPAL);
        panelBotones.add(btnQuitar);
        panelBotones.add(btnFinalizarCiclo);
        panelBotones.add(btnLanzarCiclo);
        add(panelBotones, BorderLayout.SOUTH);

        // ── Listener de selección de lavarropas ─────────────────────────────────
        tablaLavarropas.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int row = tablaLavarropas.getSelectedRow();
            if (row >= 0 && onLavarropasSeleccionado != null) {
                onLavarropasSeleccionado.accept(modeloLavarropas.getItemAt(row));
            }
        });
    }

    private JTable buildTable(javax.swing.table.AbstractTableModel model, int... centeredCols) {
        JTable t = new JTable(model);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        TableStyler.applyStandard(t);
        TableStyler.centerColumns(t, centeredCols);
        t.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        return t;
    }

    private static JScrollPane scroll(JTable t) {
        return new JScrollPane(t,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    }

    private JPanel buildConfigPanel() {
        txtLitrosJabon.setFont(Estilos.Fuentes.LABEL);
        txtLitrosTotales.setFont(Estilos.Fuentes.LABEL);
        chkSuavizante.setFont(Estilos.Fuentes.LABEL);
        setConfigEnabled(false);

        JPanel config = new JPanel(new GridBagLayout());
        config.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 2, 3, 2);
        c.anchor = GridBagConstraints.WEST;
        c.fill   = GridBagConstraints.HORIZONTAL;

        c.gridx = 0; c.gridy = 0; c.weightx = 0;
        config.add(lbl("Tipo de Jabón:"), c);
        c.gridx = 1; c.weightx = 1.0;
        config.add(cmbTipoJabon, c);

        c.gridx = 0; c.gridy = 1; c.weightx = 0;
        config.add(lbl("Litros de Jabón:"), c);
        c.gridx = 1; c.weightx = 1.0;
        config.add(txtLitrosJabon, c);

        c.gridx = 0; c.gridy = 2; c.gridwidth = 2; c.weightx = 1.0;
        config.add(chkSuavizante, c);

        c.gridx = 0; c.gridy = 3; c.gridwidth = 1; c.weightx = 0;
        config.add(lbl("Litros Totales:"), c);
        c.gridx = 1; c.weightx = 1.0;
        config.add(txtLitrosTotales, c);

        txtLitrosJabon.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { notificar(); }
            @Override public void removeUpdate(DocumentEvent e)  { notificar(); }
            @Override public void changedUpdate(DocumentEvent e) { notificar(); }
            private void notificar() {
                if (onLitrosJabonChanged != null) SwingUtilities.invokeLater(onLitrosJabonChanged);
            }
        });

        return config;
    }

    private static JLabel lbl(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Estilos.Fuentes.LABEL);
        return l;
    }

    // ── API pública ────────────────────────────────────────────────────────────

    public void setLavarropas(List<LavarropasItem> items) {
        modeloLavarropas.setItems(items != null ? items : Collections.emptyList());
        if (items != null && !items.isEmpty()) tablaLavarropas.setRowSelectionInterval(0, 0);
    }

    public void seleccionarLavarropas(int numero) {
        for (int i = 0; i < modeloLavarropas.getRowCount(); i++) {
            LavarropasItem item = modeloLavarropas.getItemAt(i);
            if (item != null && item.getNumero() == numero) {
                tablaLavarropas.setRowSelectionInterval(i, i);
                return;
            }
        }
    }

    public LavarropasItem getLavarropasSeleccionado() {
        int row = tablaLavarropas.getSelectedRow();
        return row >= 0 ? modeloLavarropas.getItemAt(row) : null;
    }

    public void setOnLavarropasSeleccionado(Consumer<LavarropasItem> listener) {
        this.onLavarropasSeleccionado = listener;
    }

    public void setElementosDisponibles(List<ElementoCicloItem> items) { modeloDisponibles.setItems(items); }
    public void setElementosCiclo(List<ElementoCicloItem> items)       { modeloCiclo.setItems(items); }

    public ElementoCicloItem getElementoDisponibleSeleccionado() {
        int row = tablaDisponibles.getSelectedRow();
        return row >= 0 ? modeloDisponibles.getItemAt(row) : null;
    }

    public ElementoCicloItem getElementoCicloSeleccionado() {
        int row = tablaCiclo.getSelectedRow();
        return row >= 0 ? modeloCiclo.getItemAt(row) : null;
    }

    public JTable getTablaDisponibles() { return tablaDisponibles; }
    public JTable getTablaCiclo()       { return tablaCiclo; }

    public TipoJabon getTipoJabonSeleccionado() { return (TipoJabon) cmbTipoJabon.getSelectedItem(); }

    public BigDecimal getLitrosJabon() {
        try {
            String t = txtLitrosJabon.getText().trim().replace(",", ".");
            if (t.isEmpty()) return null;
            BigDecimal v = new BigDecimal(t);
            return v.compareTo(BigDecimal.ZERO) > 0 ? v : null;
        } catch (NumberFormatException e) { return null; }
    }

    public boolean isSuavizante() { return chkSuavizante.isSelected(); }

    public BigDecimal getLitrosTotales() {
        try {
            String t = txtLitrosTotales.getText().trim().replace(",", ".");
            if (t.isEmpty()) return null;
            BigDecimal v = new BigDecimal(t);
            return v.compareTo(BigDecimal.ZERO) > 0 ? v : null;
        } catch (NumberFormatException e) { return null; }
    }

    public void setConfigEnabled(boolean enabled) {
        cmbTipoJabon.setEnabled(enabled);
        txtLitrosJabon.setEnabled(enabled);
        chkSuavizante.setEnabled(enabled);
        txtLitrosTotales.setEnabled(enabled);
    }

    public void setOnLitrosJabonChanged(Runnable listener) { this.onLitrosJabonChanged = listener; }

    public void setOnLanzar(java.awt.event.ActionListener l)    { btnLanzarCiclo.addActionListener(l); }
    public void setOnFinalizar(java.awt.event.ActionListener l) { btnFinalizarCiclo.addActionListener(l); }
    public void setOnQuitar(java.awt.event.ActionListener l)    { btnQuitar.addActionListener(l); }

    public void setLanzarEnabled(boolean enabled)    { btnLanzarCiclo.setEnabled(enabled); }
    public void setFinalizarEnabled(boolean enabled) { btnFinalizarCiclo.setEnabled(enabled); }
    public void setQuitarEnabled(boolean enabled)    { btnQuitar.setEnabled(enabled); }

    public void setGuardVolver(Supplier<Boolean> hayPendientes, String mensaje, Runnable onDescartar) {
        header.setGuardNavegacion(hayPendientes, mensaje, onDescartar);
    }

    public void mostrarAdvertencia(String msg) {
        JOptionPane.showMessageDialog(this, msg, Constantes.Mensajes.TITULO_ADVERTENCIA, JOptionPane.WARNING_MESSAGE);
    }

    public void mostrarError(String msg) {
        JOptionPane.showMessageDialog(this, msg, Constantes.Mensajes.TITULO_ERROR, JOptionPane.ERROR_MESSAGE);
    }

    public boolean confirmar(String msg, String titulo) {
        return JOptionPane.showConfirmDialog(this, msg, titulo,
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }
}
