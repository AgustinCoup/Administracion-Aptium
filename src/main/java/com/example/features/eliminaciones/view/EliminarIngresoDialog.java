package com.example.features.eliminaciones.view;

import com.example.common.constants.Constantes.Botones;
import com.example.common.constants.Constantes.Mensajes;
import com.example.ui.common.Estilos;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.Optional;

/**
 * Diálogo modal de confirmación de la eliminación de un ingreso: lo que se elimina (en un área con
 * scroll, porque un ingreso puede tener cientos de líneas), el motivo y la contraseña.
 *
 * <p><b>Sólo muestra y recoge.</b> No decide qué texto mostrar ni qué hacer con lo tipeado: el
 * texto lo arma {@code TextoEliminacion}, la rama la decide {@code DecisionDialogoEliminacion} y el
 * flujo lo orquesta {@code FlujoEliminacion}. Por eso no importa ningún service.</p>
 *
 * <p><b>El botón "Eliminar" no se habilita sin motivo ni contraseña.</b> El service revalida el
 * motivo igual (y verifica la contraseña); esto sólo ahorra el viaje de ida y vuelta.</p>
 */
public final class EliminarIngresoDialog extends JDialog {

    private static final int FILAS_MOTIVO = 3;
    private static final int COLUMNAS_MOTIVO = 40;
    private static final int COLUMNAS_PASSWORD = 20;

    /**
     * Lo que el operador tipeó. <b>No es un {@code record}</b>: el {@code toString} automático de un
     * record imprimiría el array de la contraseña (y lo tentaría a viajar más lejos de lo
     * necesario). {@link #limpiar()} la borra; quien la use es quien la limpia.
     */
    public static final class Datos {

        private final String motivo;
        private final char[] password;

        Datos(String motivo, char[] password) {
            this.motivo = motivo;
            this.password = password;
        }

        public String motivo() {
            return motivo;
        }

        /** El array vivo, no una copia: es lo que hay que limpiar con {@link #limpiar()}. */
        public char[] password() {
            return password;
        }

        /** Pisa la contraseña con ceros. Idempotente. */
        public void limpiar() {
            Arrays.fill(password, '\0');
        }

        @Override
        public String toString() {
            return "Datos[motivo=" + motivo + ", password=***]";
        }
    }

    private final JTextArea areaMotivo = new JTextArea(FILAS_MOTIVO, COLUMNAS_MOTIVO);
    private final JPasswordField campoPassword = new JPasswordField(COLUMNAS_PASSWORD);
    private final JButton botonEliminar = new JButton(Botones.ELIMINAR_CONFIRMAR);

    private Datos resultado;

    private EliminarIngresoDialog(Window padre, String textoConfirmacion, boolean avisoPasswordInicial,
                                  String motivoPrevio, String mensajeError) {
        super(padre, Mensajes.TITULO_ELIMINAR_INGRESO, ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        add(crearPanelAvisos(avisoPasswordInicial, mensajeError), BorderLayout.NORTH);
        add(crearScrollConfirmacion(textoConfirmacion), BorderLayout.CENTER);
        add(crearPanelFormulario(motivoPrevio), BorderLayout.SOUTH);

        registrarEscape();
        habilitarEliminarSoloConDatos();
        addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent e) {
                (areaMotivo.getText().isBlank() ? areaMotivo : campoPassword).requestFocusInWindow();
            }
            @Override public void windowClosed(WindowEvent e) {
                campoPassword.setText("");
            }
        });

        pack();
        setLocationRelativeTo(padre);
    }

    /**
     * Abre el diálogo y espera. Vacío si el operador cancela o cierra la ventana.
     *
     * @param mensajeError el motivo por el que se reabre (contraseña incorrecta, motivo vacío…), o
     *                     {@code null} si es la primera vez
     */
    public static Optional<Datos> mostrar(Component padre, String textoConfirmacion,
                                          boolean avisoPasswordInicial, String motivoPrevio,
                                          String mensajeError) {
        Window ventana = padre instanceof Window w ? w : SwingUtilities.getWindowAncestor(padre);
        EliminarIngresoDialog dialogo = new EliminarIngresoDialog(
            ventana, textoConfirmacion, avisoPasswordInicial, motivoPrevio, mensajeError);
        dialogo.setVisible(true);
        return Optional.ofNullable(dialogo.resultado);
    }

    private JPanel crearPanelAvisos(boolean avisoPasswordInicial, String mensajeError) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        if (mensajeError != null && !mensajeError.isBlank()) {
            panel.add(textoMultilinea(mensajeError, Estilos.Colores.TEXTO_ERROR));
            panel.add(Box.createVerticalStrut(6));
        }
        if (avisoPasswordInicial) {
            panel.add(textoMultilinea(Mensajes.PASSWORD_AVISO_INICIAL, Estilos.Colores.TEXTO_AVISO));
            panel.add(Box.createVerticalStrut(6));
        }
        return panel;
    }

    private JScrollPane crearScrollConfirmacion(String texto) {
        JTextArea area = new JTextArea(texto);
        area.setEditable(false);
        area.setFont(Estilos.Fuentes.TABLA_CONTENIDO);
        area.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(
            Estilos.Dimensiones.CONFIRMACION_ELIMINACION_ANCHO, Estilos.Dimensiones.CONFIRMACION_ELIMINACION_ALTO));
        return scroll;
    }

    private JPanel crearPanelFormulario(String motivoPrevio) {
        areaMotivo.setFont(Estilos.Fuentes.INPUT);
        areaMotivo.setLineWrap(true);
        areaMotivo.setWrapStyleWord(true);
        areaMotivo.setText(motivoPrevio == null ? "" : motivoPrevio);
        campoPassword.setFont(Estilos.Fuentes.INPUT);

        JButton botonCancelar = new JButton(Botones.CANCELAR);
        botonCancelar.setFont(Estilos.Fuentes.BOTON_PEQUENO);
        botonCancelar.addActionListener(e -> cancelar());
        botonEliminar.setFont(Estilos.Fuentes.BOTON_PEQUENO);
        botonEliminar.addActionListener(e -> confirmar());

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        botones.add(botonCancelar);
        botones.add(botonEliminar);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(etiqueta(Mensajes.ELIMINAR_ETIQUETA_MOTIVO));
        panel.add(alineado(new JScrollPane(areaMotivo)));
        panel.add(Box.createVerticalStrut(8));
        panel.add(etiqueta(Mensajes.ELIMINAR_ETIQUETA_PASSWORD));
        panel.add(alineado(campoPassword));
        panel.add(Box.createVerticalStrut(10));
        panel.add(alineado(botones));
        return panel;
    }

    private void habilitarEliminarSoloConDatos() {
        DocumentListener alCambiar = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { actualizarBoton(); }
            @Override public void removeUpdate(DocumentEvent e) { actualizarBoton(); }
            @Override public void changedUpdate(DocumentEvent e) { actualizarBoton(); }
        };
        areaMotivo.getDocument().addDocumentListener(alCambiar);
        // El largo del documento y no getText(): la contraseña nunca se convierte en String.
        campoPassword.getDocument().addDocumentListener(alCambiar);
        actualizarBoton();
    }

    private void actualizarBoton() {
        botonEliminar.setEnabled(!areaMotivo.getText().isBlank() && campoPassword.getDocument().getLength() > 0);
    }

    private void confirmar() {
        if (!botonEliminar.isEnabled()) return;
        resultado = new Datos(areaMotivo.getText(), campoPassword.getPassword());
        dispose();
    }

    private void cancelar() {
        resultado = null;
        dispose();
    }

    private void registrarEscape() {
        getRootPane().registerKeyboardAction(e -> cancelar(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private static JLabel etiqueta(String texto) {
        JLabel label = new JLabel(texto);
        label.setFont(Estilos.Fuentes.LABEL);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static JTextArea textoMultilinea(String texto, Color color) {
        JTextArea area = new JTextArea(texto);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(Estilos.Fuentes.LABEL);
        area.setForeground(color);
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        return area;
    }

    private static <T extends JComponent> T alineado(T componente) {
        componente.setAlignmentX(Component.LEFT_ALIGNMENT);
        return componente;
    }
}
