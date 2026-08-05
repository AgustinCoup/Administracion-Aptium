package com.example.ui.common;

import com.example.common.util.Validador;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import javax.swing.JComponent;
import javax.swing.JTextField;

/**
 * Detecta y resalta filas duplicadas en paneles dinámicos: materiales de
 * equipos (código numérico en ortopedias, descripción de texto libre en
 * otros) y elementos de clasificación de Lavadero (selección de un combo).
 *
 * El llamador decide, vía {@code extractorValor} + {@code normalizador}, qué
 * valor identifica cada fila y qué cuenta como "vacío/inválido" (devolviendo
 * cadena vacía), ya que esa regla difiere según el panel.
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
        return marcar(campos, JTextField::getText, normalizador, colorNormal, tooltipDuplicado);
    }

    /**
     * Variante genérica: sirve para cualquier componente, no solo campos de texto.
     * El {@code extractorValor} obtiene de cada componente el dato que identifica
     * la fila (el texto de un {@code JTextField}, el id del ítem elegido en un
     * {@code JComboBox}, etc.) antes de pasarlo por el {@code normalizador}.
     *
     * @return true si se encontró al menos un duplicado
     */
    public static <T extends JComponent> boolean marcar(List<T> componentes,
                                                         Function<T, String> extractorValor,
                                                         Function<String, String> normalizador,
                                                         Color colorNormal,
                                                         String tooltipDuplicado) {
        List<String> valores = new ArrayList<>();
        for (T componente : componentes) {
            valores.add(normalizador.apply(extractorValor.apply(componente)));
        }

        Set<String> duplicados = Validador.detectarDuplicados(valores);

        for (int i = 0; i < componentes.size(); i++) {
            T componente = componentes.get(i);
            if (duplicados.contains(valores.get(i))) {
                componente.setBackground(COLOR_DUPLICADO);
                componente.setToolTipText(tooltipDuplicado);
            } else {
                componente.setBackground(colorNormal);
                componente.setToolTipText(null);
            }
        }
        return !duplicados.isEmpty();
    }
}
