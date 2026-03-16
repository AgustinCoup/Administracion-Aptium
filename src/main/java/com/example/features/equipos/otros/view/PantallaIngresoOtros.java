package com.example.features.equipos.otros.view;

import com.example.common.constants.Constantes;
import com.example.features.equipos.otros.view.helpers.PanelMaterialesOtros;
import com.example.ui.common.Estilos;
import com.example.ui.common.PanelHeader;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Pantalla de ingreso para equipos de tipo "Otros".
 *
 * Campos:
 * - Cliente (autocompletado, reutiliza la misma lógica que PantallaIngresoOrtopedia)
 * - Requiere Lavado / Requiere Empaquetado (checkboxes)
 * - Panel de materiales con descripción libre y cantidad
 *
 * La pantalla no contiene lógica de negocio; delega todo al
 * {@link com.example.features.otros.controller.OtrosInputController}.
 */
public class PantallaIngresoOtros extends JPanel {

    // ── UI ────────────────────────────────────────────────────────────────────
    private JTextField txtCliente;
    private JCheckBox  chkRequiereLavado;
    private JCheckBox  chkRequiereEmpaque;
    private JButton    btnGuardar;
    private JButton    btnCancelar;
    private PanelMaterialesOtros panelMateriales;

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

        // Fila 0: Cliente
        agregarFila(formulario, gbc, labelFont, 0, Constantes.Textos.LABEL_CLIENTE, txtCliente);

        // Fila 1: Checkboxes
        JPanel panelOpciones = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        chkRequiereLavado  = new JCheckBox(Constantes.Textos.CHECK_REQUIERE_LAVADO,  true);
        chkRequiereEmpaque = new JCheckBox(Constantes.Textos.CHECK_REQUIERE_EMPAQUE, true);
        chkRequiereLavado.setFont(inputFont);
        chkRequiereEmpaque.setFont(inputFont);
        chkRequiereEmpaque.setEnabled(false); // igual que en ortopedia: se habilita solo si lavado=false
        panelOpciones.add(chkRequiereLavado);
        panelOpciones.add(chkRequiereEmpaque);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.WEST;
        formulario.add(panelOpciones, gbc);
        gbc.gridwidth = 1;

        // Fila 2: Panel de materiales
        JLabel lblMat = new JLabel(Constantes.Textos.LABEL_MATERIAL);
        lblMat.setFont(labelFont);
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        gbc.insets = new Insets(2, 5, 2, 10);
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill   = GridBagConstraints.NONE;
        formulario.add(lblMat, gbc);

        panelMateriales = new PanelMaterialesOtros(inputFont, inputHeight);
        gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 1; gbc.weighty = 1;
        gbc.insets = new Insets(2, 5, 5, 5);
        gbc.fill   = GridBagConstraints.BOTH;
        formulario.add(panelMateriales, gbc);

        return formulario;
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
        for (java.awt.event.ActionListener al : header.getBtnVolver().getActionListeners()) {
            header.getBtnVolver().removeActionListener(al);
        }
        header.getBtnVolver().addActionListener(e -> {
            limpiarFormulario();
            navegador.show(contenedor, Constantes.Pantallas.ES_ORTOPEDIA);
        });
    }

    // ── Getters para el controller ────────────────────────────────────────────

    public JTextField getTxtCliente()           { return txtCliente; }
    public JButton    getBtnGuardar()           { return btnGuardar; }
    public JButton    getBtnCancelar()          { return btnCancelar; }
    public int        getSelectedClienteId()    { return selectedClienteId; }
    public void       setSelectedClienteId(int id) { this.selectedClienteId = id; }

    public boolean isRequiereLavado()           { return chkRequiereLavado.isSelected(); }
    public boolean isRequiereEmpaque()          { return chkRequiereEmpaque.isSelected(); }

    public void setRequiereEmpaqueSelected(boolean v) { chkRequiereEmpaque.setSelected(v); }
    public void setRequiereEmpaqueEnabled(boolean v)  { chkRequiereEmpaque.setEnabled(v); }

    public void setOnRequiereLavadoChanged(ActionListener l) { chkRequiereLavado.addActionListener(l); }

    public PanelMaterialesOtros getPanelMateriales() { return panelMateriales; }

    public List<PanelMaterialesOtros.OtrosMaterialRow> getMaterialFilas() {
        return panelMateriales.getFilas();
    }

    // ── Operaciones de formulario ─────────────────────────────────────────────

    public void limpiarFormulario() {
        txtCliente.setText("");
        panelMateriales.limpiar();
        chkRequiereLavado.setSelected(true);
        chkRequiereEmpaque.setSelected(true);
        chkRequiereEmpaque.setEnabled(false);
        selectedClienteId = -1;
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