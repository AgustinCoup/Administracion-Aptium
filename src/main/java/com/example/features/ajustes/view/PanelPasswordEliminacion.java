package com.example.features.ajustes.view;

import com.example.common.constants.Constantes;

import javax.swing.*;
import java.awt.*;

/**
 * Pestaña Seguridad de Ajustes: cambiar la password que pide eliminar un ingreso.
 *
 * <p><b>Las passwords salen como {@code char[]} y nunca como {@code String}</b>
 * ({@code JPasswordField.getPassword()}, jamás {@code getText()}): un {@code String} no se puede
 * limpiar y queda en el heap hasta que el recolector quiera. Quien las toma las limpia con
 * {@code Arrays.fill}; {@link #limpiarCampos()} vacía además los campos de la pantalla.</p>
 */
public class PanelPasswordEliminacion extends JPanel {

    private static final int ANCHO_CAMPO = 20;

    private final JPasswordField campoActual   = new JPasswordField(ANCHO_CAMPO);
    private final JPasswordField campoNueva    = new JPasswordField(ANCHO_CAMPO);
    private final JPasswordField campoRepetida = new JPasswordField(ANCHO_CAMPO);
    private final JButton        btnCambiar    = new JButton(Constantes.Botones.CAMBIAR_PASSWORD);
    private final JLabel         avisoInicial  = new JLabel(Constantes.Mensajes.PASSWORD_AVISO_INICIAL);

    private Runnable onCambiar;

    public PanelPasswordEliminacion() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        avisoInicial.setForeground(new Color(0xB2, 0x5A, 0x00));
        avisoInicial.setVisible(false);
        add(avisoInicial, BorderLayout.NORTH);
        add(crearFormulario(), BorderLayout.CENTER);

        btnCambiar.addActionListener(e -> { if (onCambiar != null) onCambiar.run(); });
    }

    private JPanel crearFormulario() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("Contraseña de eliminación"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;

        agregarFila(form, c, 0, "Contraseña actual:", campoActual);
        agregarFila(form, c, 1, "Contraseña nueva:", campoNueva);
        agregarFila(form, c, 2, "Repetir contraseña nueva:", campoRepetida);

        c.gridx = 1;
        c.gridy = 3;
        form.add(btnCambiar, c);

        JPanel envoltorio = new JPanel(new FlowLayout(FlowLayout.LEFT));
        envoltorio.add(form);
        return envoltorio;
    }

    private static void agregarFila(JPanel form, GridBagConstraints c, int fila, String rotulo, JComponent campo) {
        c.gridx = 0;
        c.gridy = fila;
        form.add(new JLabel(rotulo), c);
        c.gridx = 1;
        form.add(campo, c);
    }

    /** Copia nueva cada vez: quien la recibe la limpia sin afectar al campo. */
    public char[] getPasswordActual()   { return campoActual.getPassword(); }
    public char[] getPasswordNueva()    { return campoNueva.getPassword(); }
    public char[] getPasswordRepetida() { return campoRepetida.getPassword(); }

    public void limpiarCampos() {
        campoActual.setText("");
        campoNueva.setText("");
        campoRepetida.setText("");
    }

    public void setAvisoInicialVisible(boolean visible) { avisoInicial.setVisible(visible); }

    public void setCambiarHabilitado(boolean habilitado) { btnCambiar.setEnabled(habilitado); }

    public void setOnCambiar(Runnable r) { onCambiar = r; }
}
