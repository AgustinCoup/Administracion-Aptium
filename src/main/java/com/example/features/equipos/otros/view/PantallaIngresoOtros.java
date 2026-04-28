package com.example.features.equipos.otros.view;

import com.example.common.constants.Constantes;
import com.example.features.equipos.common.view.PantallaIngresoBase;
import com.example.features.equipos.otros.view.helpers.PanelMaterialesOtros;
import com.example.features.equipos.otros.view.helpers.PanelRemito;
import com.example.ui.common.Estilos;
import com.example.ui.common.PanelHeader;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * Pantalla de ingreso para equipos de tipo "Otros".
 *
 * Campos comunes:
 * <ul>
 *   <li>Cliente (autocompletado)</li>
 *   <li>Requiere Lavado / Requiere Empaquetado (checkboxes)</li>
 * </ul>
 *
 * Modos de ingreso (radio buttons):
 * <ul>
 *   <li><b>Remito</b>: muestra {@link PanelRemito} con identificador de fecha,
 *       cantidad global y observaciones opcionales.</li>
 *   <li><b>Detalles</b>: muestra {@link PanelMaterialesOtros} con filas de
 *       material y cantidad, igual que antes.</li>
 * </ul>
 *
 * El cambio de modo se gestiona internamente mediante un {@link CardLayout};
 * el controller solo lee {@link #isRemito()} para saber qué panel está activo.
 *
 * La pantalla no contiene lógica de negocio; delega todo al
 * {@link com.example.features.equipos.otros.controller.OtrosInputController}.
 */
public class PantallaIngresoOtros extends JPanel implements PantallaIngresoBase {

    // ── Nombres de tarjetas del CardLayout interno ────────────────────────────
    private static final String CARD_REMITO   = "REMITO";
    private static final String CARD_DETALLES = "DETALLES";

    // ── UI ────────────────────────────────────────────────────────────────────
    private JTextField           txtCliente;
    private JCheckBox            chkRequiereLavado;
    private JCheckBox            chkRequiereEmpaque;
    private JRadioButton         rdRemito;
    private JRadioButton         rdDetalles;
    private PanelRemito          panelRemito;
    private PanelMaterialesOtros panelMateriales;
    private CardLayout           cardModo;
    private JPanel               contenedorModo;
    private JButton              btnGuardar;
    private JButton              btnCancelar;

    // ── Navegación ────────────────────────────────────────────────────────────
    private final CardLayout navegador;
    private final JPanel     contenedor;

    // ── Estado del autocompletado ─────────────────────────────────────────────
    private int selectedClienteId = -1;

    // ── Constructor ───────────────────────────────────────────────────────────

    public PantallaIngresoOtros(CardLayout navegador, JPanel contenedor) {
        this.navegador  = navegador;
        this.contenedor = contenedor;

        setLayout(new BorderLayout());

        PanelHeader header = new PanelHeader(
            Constantes.Titulos.INGRESO_OTROS,
            navegador,
            contenedor,
            Constantes.Pantallas.ES_ORTOPEDIA
        );
        configurarVolverConLimpieza(header);
        add(header, BorderLayout.NORTH);

        add(construirFormulario(), BorderLayout.CENTER);
        add(construirPanelBotones(), BorderLayout.SOUTH);
    }

    // ── Construcción de la UI ─────────────────────────────────────────────────

    private JPanel construirFormulario() {
        JPanel formulario = new JPanel(new GridBagLayout());
        formulario.setBorder(Estilos.Espaciados.BORDE_FORMULARIO);

        Font labelFont  = Estilos.Fuentes.LABEL;
        Font inputFont  = Estilos.Fuentes.INPUT;
        int  inputHeight = Estilos.Dimensiones.calcularAlturaInput();

        txtCliente = crearTextField(inputFont, inputHeight);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 5, 8, 10);
        gbc.fill   = GridBagConstraints.HORIZONTAL;

        // ── Fila 0: Cliente ───────────────────────────────────────────────────
        agregarFila(formulario, gbc, labelFont, 0, Constantes.Textos.LABEL_CLIENTE, txtCliente);

        // ── Fila 1: Checkboxes ────────────────────────────────────────────────
        JPanel panelOpciones = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        chkRequiereLavado  = new JCheckBox(Constantes.Textos.CHECK_REQUIERE_LAVADO,  true);
        chkRequiereEmpaque = new JCheckBox(Constantes.Textos.CHECK_REQUIERE_EMPAQUE, true);
        chkRequiereLavado.setFont(inputFont);
        chkRequiereEmpaque.setFont(inputFont);
        chkRequiereEmpaque.setEnabled(false);
        panelOpciones.add(chkRequiereLavado);
        panelOpciones.add(chkRequiereEmpaque);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.WEST;
        formulario.add(panelOpciones, gbc);
        gbc.gridwidth = 1;

        // ── Fila 2: Selector de modo (radio buttons) ──────────────────────────
        JPanel panelModo = construirSelectorModo(inputFont);
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(4, 5, 4, 10);
        gbc.fill   = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        formulario.add(panelModo, gbc);
        gbc.gridwidth = 1;
        gbc.insets = new Insets(8, 5, 8, 10);

        // ── Fila 3: Panel de contenido (Remito o Detalles) ────────────────────
        cardModo         = new CardLayout();
        contenedorModo   = new JPanel(cardModo);
        panelRemito      = new PanelRemito(inputFont, inputHeight);
        panelMateriales  = new PanelMaterialesOtros(inputFont, inputHeight);
        contenedorModo.add(panelRemito,    CARD_REMITO);
        contenedorModo.add(panelMateriales, CARD_DETALLES);
        cardModo.show(contenedorModo, CARD_DETALLES);   // detalles por defecto

        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 1; gbc.weighty = 1;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(2, 5, 5, 5);
        gbc.fill   = GridBagConstraints.BOTH;
        formulario.add(contenedorModo, gbc);

        return formulario;
    }

    /**
     * Construye el panel con los dos radio buttons (Remito / Detalles)
     * y conecta la lógica de cambio de tarjeta internamente.
     */
    private JPanel construirSelectorModo(Font inputFont) {
        JLabel lblTipo = new JLabel(Constantes.Textos.LABEL_TIPO_INGRESO);
        lblTipo.setFont(Estilos.Fuentes.LABEL);

        rdRemito   = new JRadioButton(Constantes.Textos.RADIO_REMITO);
        rdDetalles = new JRadioButton(Constantes.Textos.RADIO_DETALLES, true);
        rdRemito.setFont(inputFont);
        rdDetalles.setFont(inputFont);

        ButtonGroup grupo = new ButtonGroup();
        grupo.add(rdRemito);
        grupo.add(rdDetalles);

        // Cambio de tarjeta: responsabilidad puramente visual, vive en la vista
        rdRemito.addActionListener(e   -> cardModo.show(contenedorModo, CARD_REMITO));
        rdDetalles.addActionListener(e -> cardModo.show(contenedorModo, CARD_DETALLES));

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        panel.add(lblTipo);
        panel.add(rdRemito);
        panel.add(rdDetalles);
        return panel;
    }

    private void agregarFila(JPanel panel, GridBagConstraints gbc, Font labelFont,
                              int row, String labelText, JTextField textField) {
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(labelFont);
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill   = GridBagConstraints.NONE;
        panel.add(lbl, gbc);

        gbc.gridx = 1; gbc.weightx = 1;
        gbc.fill  = GridBagConstraints.HORIZONTAL;
        panel.add(textField, gbc);
    }

    private JTextField crearTextField(Font font, int height) {
        JTextField tf = new JTextField();
        tf.setFont(font);
        tf.setColumns(20);
        tf.setMargin(Estilos.Espaciados.INSETS_INPUT);
        tf.setPreferredSize(new Dimension(0, height));
        return tf;
    }

    private JPanel construirPanelBotones() {
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        btnGuardar  = new JButton(Constantes.Botones.GUARDAR);
        btnCancelar = new JButton(Constantes.Botones.CANCELAR);
        btnGuardar.setFont(Estilos.Fuentes.INPUT);
        btnCancelar.setFont(Estilos.Fuentes.INPUT);

        btnCancelar.addActionListener(e -> {
            limpiarFormulario();
            navegador.show(contenedor, Constantes.Pantallas.MENU_PRINCIPAL);
        });

        botones.add(btnGuardar);
        botones.add(btnCancelar);
        return botones;
    }

    private void configurarVolverConLimpieza(PanelHeader header) {
        for (ActionListener al : header.getBtnVolver().getActionListeners()) {
            header.getBtnVolver().removeActionListener(al);
        }
        header.getBtnVolver().addActionListener(e -> {
            limpiarFormulario();
            navegador.show(contenedor, Constantes.Pantallas.ES_ORTOPEDIA);
        });
    }

    // ── Getters para el controller ────────────────────────────────────────────

    public JTextField          getTxtCliente()           { return txtCliente; }
    public JButton             getBtnGuardar()           { return btnGuardar; }
    public JButton             getBtnCancelar()          { return btnCancelar; }
    public int                 getSelectedClienteId()    { return selectedClienteId; }
    public void                setSelectedClienteId(int id) { this.selectedClienteId = id; }

    public boolean isRequiereLavado()                    { return chkRequiereLavado.isSelected(); }
    public boolean isRequiereEmpaque()                   { return chkRequiereEmpaque.isSelected(); }
    public void    setRequiereEmpaqueSelected(boolean v) { chkRequiereEmpaque.setSelected(v); }
    public void    setRequiereEmpaqueEnabled(boolean v)  { chkRequiereEmpaque.setEnabled(v); }

    public void    setOnRequiereLavadoChanged(ActionListener l) { chkRequiereLavado.addActionListener(l); }

    /** {@code true} si el modo activo es Remito; {@code false} si es Detalles. */
    public boolean isRemito()                            { return rdRemito.isSelected(); }

    public PanelRemito          getPanelRemito()          { return panelRemito; }
    public PanelMaterialesOtros getPanelMateriales()      { return panelMateriales; }

    public List<PanelMaterialesOtros.OtrosMaterialRow> getMaterialFilas() {
        return panelMateriales.getFilas();
    }

    // ── Operaciones de formulario ─────────────────────────────────────────────

    public void limpiarFormulario() {
        txtCliente.setText("");
        selectedClienteId = -1;

        chkRequiereLavado.setSelected(true);
        chkRequiereEmpaque.setSelected(true);
        chkRequiereEmpaque.setEnabled(false);

        // Resetear a modo Detalles
        rdDetalles.setSelected(true);
        cardModo.show(contenedorModo, CARD_DETALLES);

        panelRemito.limpiar();
        panelMateriales.limpiar();
    }

    // ── Diálogos ─────────────────────────────────────────────────────────────

    public void mostrarError(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje,
            Constantes.Mensajes.TITULO_ERROR, JOptionPane.ERROR_MESSAGE);
    }

    public void mostrarInfo(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje,
            Constantes.Mensajes.TITULO_EXITO, JOptionPane.INFORMATION_MESSAGE);
    }

    public void mostrarAdvertencia(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje,
            Constantes.Mensajes.TITULO_ADVERTENCIA, JOptionPane.WARNING_MESSAGE);
    }
}