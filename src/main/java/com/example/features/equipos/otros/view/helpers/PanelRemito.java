package com.example.features.equipos.otros.view.helpers;

import com.example.common.constants.Constantes;
import com.example.ui.common.Estilos;

import javax.swing.*;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Panel de formulario para el modo "Remito" del ingreso de Otros.
 *
 * Contiene:
 * <ul>
 *   <li>Campo de solo lectura con la fecha del día en formato ddmmaaaa
 *       (el sufijo {@code -{id}} se agrega tras persistir).</li>
 *   <li>Spinner de cantidad (entero, mínimo 1).</li>
 *   <li>Área de texto opcional para observaciones, con límite de
 *       {@link Constantes.Formatos#REMITO_OBS_MAX_CHARS} caracteres.</li>
 * </ul>
 *
 * La pantalla no contiene lógica de negocio; todos los datos se exponen
 * mediante getters para que el controller los consuma.
 */
public class PanelRemito extends JPanel {

    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("ddMMyyyy");

    private final JTextField txtRemitoIdPreview;
    private final JSpinner   spCantidad;
    private final JTextArea  txtObservaciones;

    public PanelRemito(Font inputFont, int inputHeight) {
        setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets  = new Insets(8, 5, 8, 10);
        gbc.fill    = GridBagConstraints.HORIZONTAL;
        gbc.anchor  = GridBagConstraints.WEST;

        Font labelFont = Estilos.Fuentes.LABEL;

        // ── Fila 0: Identificador (readonly) ──────────────────────────────────
        JLabel lblId = new JLabel(Constantes.Textos.LABEL_REMITO_ID);
        lblId.setFont(labelFont);
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        add(lblId, gbc);

        String fechaHoy = LocalDate.now().format(FMT_FECHA);
        txtRemitoIdPreview = new JTextField(fechaHoy + "-*");
        txtRemitoIdPreview.setFont(inputFont);
        txtRemitoIdPreview.setEditable(false);
        txtRemitoIdPreview.setBackground(UIManager.getColor("TextField.inactiveBackground"));
        txtRemitoIdPreview.setToolTipText(Constantes.Textos.TOOLTIP_REMITO_ID);
        txtRemitoIdPreview.setPreferredSize(new Dimension(160, inputHeight));
        gbc.gridx = 1; gbc.weightx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        add(txtRemitoIdPreview, gbc);

        // ── Fila 1: Cantidad ──────────────────────────────────────────────────
        JLabel lblCant = new JLabel(Constantes.Textos.LABEL_REMITO_CANTIDAD);
        lblCant.setFont(labelFont);
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        add(lblCant, gbc);

        SpinnerNumberModel spModel = new SpinnerNumberModel(1, 1, Integer.MAX_VALUE, 1);
        spCantidad = new JSpinner(spModel);
        spCantidad.setFont(inputFont);
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spCantidad, "#0");
        spCantidad.setEditor(editor);
        editor.getTextField().setColumns(6);
        spCantidad.setPreferredSize(new Dimension(120, inputHeight));
        gbc.gridx = 1; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        add(spCantidad, gbc);

        // ── Fila 2: Observaciones ─────────────────────────────────────────────
        JLabel lblObs = new JLabel(Constantes.Textos.LABEL_OBSERVACIONES);
        lblObs.setFont(labelFont);
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        add(lblObs, gbc);

        txtObservaciones = new JTextArea(5, 30);
        txtObservaciones.setFont(inputFont);
        txtObservaciones.setLineWrap(true);
        txtObservaciones.setWrapStyleWord(true);
        txtObservaciones.setMargin(Estilos.Espaciados.INSETS_INPUT);
        aplicarLimiteCaracteres(txtObservaciones, Constantes.Formatos.REMITO_OBS_MAX_CHARS);

        JScrollPane scrollObs = new JScrollPane(txtObservaciones);
        scrollObs.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollObs.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        gbc.gridx = 1; gbc.weightx = 1; gbc.weighty = 1;
        gbc.fill  = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        add(scrollObs, gbc);

        // ── Relleno inferior ──────────────────────────────────────────────────
        // evita que los componentes se expandan verticalmente si el panel crece
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        gbc.weighty = 0; gbc.fill = GridBagConstraints.NONE;
        add(Box.createVerticalStrut(0), gbc);
    }

    // ── API pública ───────────────────────────────────────────────────────────

    /** Cantidad seleccionada en el spinner (siempre >= 1). */
    public int getCantidad() {
        Object v = spCantidad.getValue();
        return (v instanceof Number) ? ((Number) v).intValue() : 1;
    }

    /**
     * Texto de observaciones, o {@code null} si el campo está vacío.
     */
    public String getObservaciones() {
        String txt = txtObservaciones.getText().trim();
        return txt.isEmpty() ? null : txt;
    }

    /** Reinicia el panel a sus valores por defecto. */
    public void limpiar() {
        spCantidad.setValue(1);
        txtObservaciones.setText("");
        // Actualizar el preview de fecha por si cambió de día
        txtRemitoIdPreview.setText(LocalDate.now().format(FMT_FECHA) + "-*");
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    /**
     * Aplica un {@link DocumentFilter} que impide superar {@code maxChars} caracteres
     * en el área de texto dada.
     */
    private static void aplicarLimiteCaracteres(JTextArea area, int maxChars) {
        ((AbstractDocument) area.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                    throws BadLocationException {
                int actual = fb.getDocument().getLength();
                if (actual + string.length() <= maxChars) {
                    super.insertString(fb, offset, string, attr);
                } else {
                    int permitido = maxChars - actual;
                    if (permitido > 0) {
                        super.insertString(fb, offset, string.substring(0, permitido), attr);
                    }
                }
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                    throws BadLocationException {
                int actual    = fb.getDocument().getLength();
                int resultado = actual - length + (text != null ? text.length() : 0);
                if (resultado <= maxChars) {
                    super.replace(fb, offset, length, text, attrs);
                } else {
                    int permitido = maxChars - actual + length;
                    if (permitido > 0 && text != null) {
                        super.replace(fb, offset, length, text.substring(0, permitido), attrs);
                    }
                }
            }
        });
    }
}