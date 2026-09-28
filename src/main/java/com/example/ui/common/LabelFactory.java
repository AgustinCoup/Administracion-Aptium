package com.example.ui.common;

import javax.swing.JLabel;
import javax.swing.JTextArea;
import java.awt.Font;

import com.example.common.constants.Constantes;

public final class LabelFactory {
    private LabelFactory() {
        throw new UnsupportedOperationException("Clase de estilos no instanciable");
    }

    public static JLabel createSectionLabel(String texto) {
        JLabel label = new JLabel(texto);
        label.setFont(new Font(Constantes.Defaults.FUENTE_PRINCIPAL, Font.BOLD, 16));
        return label;
    }

    /**
     * Texto de ayuda que se parte en líneas según el ancho disponible.
     *
     * <p>Es un {@link JTextArea} de sólo lectura y no un {@link JLabel} porque el ancho
     * <b>mínimo</b> de un {@code JLabel} de texto plano es su ancho preferido: una frase larga
     * no se achica, se corta, y además le impone ese mínimo al contenedor. Dentro de un
     * {@code JSplitPane} eso traba el divisor, que no puede dejar a ningún lado por debajo
     * de su mínimo.</p>
     */
    public static JTextArea createHelpText(String texto) {
        JTextArea area = new JTextArea(texto);
        area.setFont(Estilos.Fuentes.LABEL);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setBorder(null);
        return area;
    }
}


