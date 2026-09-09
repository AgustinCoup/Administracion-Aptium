package com.example.ui.common;

import org.junit.jupiter.api.Test;

import javax.swing.JTextField;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import static org.junit.jupiter.api.Assertions.*;

class RestriccionesCampoTest {

    @Test
    void digitoPermitido_noSeConsume() {
        JTextField campo = new JTextField();
        RestriccionesCampo.soloNumerosDecimales(campo);

        KeyEvent evento = evento(campo, '5');
        listener(campo).keyTyped(evento);

        assertFalse(evento.isConsumed());
    }

    @Test
    void primerSeparadorDecimal_campoVacio_sePermite() {
        JTextField campo = new JTextField();
        RestriccionesCampo.soloNumerosDecimales(campo);

        KeyEvent evento = evento(campo, ',');
        listener(campo).keyTyped(evento);

        assertFalse(evento.isConsumed());
    }

    @Test
    void segundoSeparadorDecimal_seConsume() {
        JTextField campo = new JTextField();
        RestriccionesCampo.soloNumerosDecimales(campo);
        campo.setText("1,");

        KeyEvent evento = evento(campo, '.');
        listener(campo).keyTyped(evento);

        assertTrue(evento.isConsumed());
    }

    @Test
    void letra_seConsume() {
        JTextField campo = new JTextField();
        RestriccionesCampo.soloNumerosDecimales(campo);

        KeyEvent evento = evento(campo, 'a');
        listener(campo).keyTyped(evento);

        assertTrue(evento.isConsumed());
    }

    @Test
    void backspace_noSeConsume() {
        JTextField campo = new JTextField();
        RestriccionesCampo.soloNumerosDecimales(campo);

        KeyEvent evento = evento(campo, (char) KeyEvent.VK_BACK_SPACE);
        listener(campo).keyTyped(evento);

        assertFalse(evento.isConsumed());
    }

    private static KeyListener listener(JTextField campo) {
        return campo.getKeyListeners()[0];
    }

    private static KeyEvent evento(JTextField campo, char c) {
        return new KeyEvent(campo, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0,
            KeyEvent.VK_UNDEFINED, c);
    }
}
