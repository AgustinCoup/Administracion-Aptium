package com.example.ui.common;

import javax.swing.JLabel;
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
}


