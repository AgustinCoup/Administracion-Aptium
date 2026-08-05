package com.example.ui.common;

import com.example.common.util.Validador;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import javax.swing.JTextField;

/**
 * Detecta y resalta filas duplicadas en paneles de materiales dinámicos
 * (código numérico en ortopedias, descripción de texto libre en otros).
 *
 * El llamador decide, vía {@code normalizador}, qué valor identifica cada
 * fila y qué cuenta como "vacío/inválido" (devolviendo cadena vacía), ya
 * que esa regla difiere según el tipo de equipo.
 */
public final class DuplicadoHighlighter {

    private static final Color COLOR_DUPLICADO = new Color(255, 200, 200);

    private DuplicadoHighlighter() {
        throw new UnsupportedOperationException("Clase utilitaria no instanciable");
    }

    /**
     * Recorre los campos, normaliza su texto y marca en rojo (+ tooltip) los que
     * comparten valor normalizado con otro campo. Restaura color y tooltip en los
     * que no están en conflicto.
     *
     * @return true si se encontró al menos un duplicado
     */
    public static boolean marcar(List<JTextField> campos,
                                  Function<String, String> normalizador,
                                  Color colorNormal,
                                  String tooltipDuplicado) {
        List<String> valores = new ArrayList<>();
        for (JTextField campo : campos) {
            valores.add(normalizador.apply(campo.getText()));
        }

        Set<String> duplicados = Validador.detectarDuplicados(valores);

        for (int i = 0; i < campos.size(); i++) {
            JTextField campo = campos.get(i);
            if (duplicados.contains(valores.get(i))) {
                campo.setBackground(COLOR_DUPLICADO);
                campo.setToolTipText(tooltipDuplicado);
            } else {
                campo.setBackground(colorNormal);
                campo.setToolTipText(null);
            }
        }
        return !duplicados.isEmpty();
    }
}
