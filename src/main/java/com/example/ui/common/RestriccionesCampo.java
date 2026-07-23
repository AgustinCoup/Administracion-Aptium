package com.example.ui.common;

import javax.swing.JTextField;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * Restricciones de tecleo sobre {@link JTextField}: filtran la tecla en el
 * momento en que se escribe.
 *
 * <p>Vive en {@code ui/common} y no en {@code common/util} a propósito: son
 * manipulación de widgets, no reglas de negocio. Las reglas equivalentes en
 * texto ya validado están en {@code Validador} ({@code soloNumeros}), que no
 * depende de Swing.
 */
public final class RestriccionesCampo {

    private RestriccionesCampo() {
        throw new UnsupportedOperationException("Clase de utilidades no instanciable");
    }

    /**
     * Restringe el campo a dígitos: descarta cualquier otra tecla al escribirla.
     *
     * @param campo JTextField al que se le aplicará la restricción
     */
    public static void soloNumeros(JTextField campo) {
        campo.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                char c = e.getKeyChar();
                if (!Character.isDigit(c) && c != KeyEvent.VK_BACK_SPACE) {
                    e.consume(); // Ignora la tecla si no es número
                }
            }
        });
    }

    /**
     * Restringe el campo a letras (con acentos y ñ) y espacios.
     *
     * @param campo JTextField al que se le aplicará la restricción
     */
    public static void soloLetrasYEspacios(JTextField campo) {
        campo.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                char c = e.getKeyChar();
                // Permitir: letras, espacios, y backspace
                if (!Character.isLetter(c) && c != ' ' && c != KeyEvent.VK_BACK_SPACE) {
                    e.consume(); // Ignora la tecla si no es letra ni espacio
                }
            }
        });
    }
}
