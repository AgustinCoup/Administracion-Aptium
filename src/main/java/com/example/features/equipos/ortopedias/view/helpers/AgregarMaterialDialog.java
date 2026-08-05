package com.example.features.equipos.ortopedias.view.helpers;

import com.example.common.constants.Constantes;
import com.example.common.util.Validador;
import com.example.ui.common.RestriccionesCampo;
import com.example.ui.common.Estilos;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.function.BiConsumer;

/**
 * Panel de formulario para el diálogo "Agregar Material al Equipo".
 *
 * Encapsula la construcción de la UI del formulario, separándola de la lógica
 * de orquestación que vive en {@link com.example.features.equipos.ortopedias.view.PantallaCorrecciones}.
 */
public class AgregarMaterialDialog {

    public final JPanel    panel;
    private final JTextField txtCodigo;
    private final JTextField txtDesc;
    private final JSpinner   spinCant;
    private final JTextArea  txtMot;

    /**
     * @param onCodigoChanged Callback invocado al modificar el código para mostrar
     *                        la descripción del catálogo en tiempo real.
     */
    public AgregarMaterialDialog(BiConsumer<Integer, JTextField> onCodigoChanged) {
        panel    = new JPanel(new GridBagLayout());
        txtCodigo = new JTextField(10);
        txtDesc   = new JTextField();
        spinCant  = new JSpinner(new SpinnerNumberModel(1, 1, 10000, 1));
        txtMot    = new JTextArea(3, 25);

        construirPanel();
        adjuntarListenerCodigo(onCodigoChanged);
    }

    // ── API pública ────────────────────────────────────────────────────────────

    public String getCodigo()      { return txtCodigo.getText().trim(); }
    public String getDescripcion() { return txtDesc.getText().trim(); }
    public int    getCantidad()    { return (Integer) spinCant.getValue(); }
    public String getMotivo()      { return txtMot.getText().trim(); }

    // ── Construcción de la UI ─────────────────────────────────────────────────

    private void construirPanel() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets  = new Insets(6, 6, 6, 6);
        gbc.fill    = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.3;
        JLabel lblCod = new JLabel("Código:");
        lblCod.setFont(Estilos.Fuentes.LABEL);
        panel.add(lblCod, gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        txtCodigo.setFont(Estilos.Fuentes.INPUT);
        RestriccionesCampo.soloNumeros(txtCodigo);
        panel.add(txtCodigo, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.3;
        JLabel lblDesc = new JLabel("Descripción:");
        lblDesc.setFont(Estilos.Fuentes.LABEL);
        panel.add(lblDesc, gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        txtDesc.setFont(Estilos.Fuentes.INPUT);
        txtDesc.setEditable(false);
        txtDesc.setBackground(new Color(240, 240, 240));
        panel.add(txtDesc, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.3;
        JLabel lblCant = new JLabel("Cantidad:");
        lblCant.setFont(Estilos.Fuentes.LABEL);
        panel.add(lblCant, gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        spinCant.setFont(Estilos.Fuentes.INPUT);
        panel.add(spinCant, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.3;
        JLabel lblMot = new JLabel("Motivo:");
        lblMot.setFont(Estilos.Fuentes.LABEL);
        panel.add(lblMot, gbc);
        gbc.gridx = 1; gbc.weightx = 0.7; gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        txtMot.setFont(Estilos.Fuentes.INPUT);
        txtMot.setLineWrap(true);
        txtMot.setWrapStyleWord(true);
        panel.add(new JScrollPane(txtMot), gbc);
    }

    private void adjuntarListenerCodigo(BiConsumer<Integer, JTextField> onCodigoChanged) {
        txtCodigo.getDocument().addDocumentListener(simpleListener(() -> {
            String cod = txtCodigo.getText().trim();
            if (cod.isEmpty()) { txtDesc.setText(""); return; }
            if (!Validador.soloNumeros(cod)) { txtDesc.setText(Constantes.Textos.CODIGO_INVALIDO); return; }
            if (onCodigoChanged != null) {
                try { onCodigoChanged.accept(Integer.parseInt(cod), txtDesc); }
                catch (NumberFormatException ex) { txtDesc.setText(Constantes.Textos.CODIGO_INVALIDO); }
            }
        }));
    }

    private static DocumentListener simpleListener(Runnable r) {
        return new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { r.run(); }
            @Override public void removeUpdate(DocumentEvent e)  { r.run(); }
            @Override public void changedUpdate(DocumentEvent e) { r.run(); }
        };
    }
}
