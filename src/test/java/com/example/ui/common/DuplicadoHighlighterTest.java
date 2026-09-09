package com.example.ui.common;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.function.Function;
import javax.swing.JComboBox;
import javax.swing.JTextField;

import static org.junit.jupiter.api.Assertions.*;

class DuplicadoHighlighterTest {

    private static final Color NORMAL = Color.WHITE;
    private static final String TOOLTIP = "duplicado";

    @Test
    void sinDuplicados_devuelveFalseYNoResalta() {
        JTextField a = campoCon("400");
        JTextField b = campoCon("401");

        boolean resultado = DuplicadoHighlighter.marcar(List.of(a, b), s -> s.trim(), NORMAL, TOOLTIP);

        assertFalse(resultado);
        assertEquals(NORMAL, a.getBackground());
        assertEquals(NORMAL, b.getBackground());
        assertNull(a.getToolTipText());
        assertNull(b.getToolTipText());
    }

    @Test
    void dosCamposConMismoValorNormalizado_seMarcanAmbosComoDuplicados() {
        JTextField a = campoCon("Guante");
        JTextField b = campoCon("guante ");

        boolean resultado = DuplicadoHighlighter.marcar(
            List.of(a, b), s -> s.trim().toLowerCase(), NORMAL, TOOLTIP);

        assertTrue(resultado);
        assertNotEquals(NORMAL, a.getBackground());
        assertNotEquals(NORMAL, b.getBackground());
        assertEquals(TOOLTIP, a.getToolTipText());
        assertEquals(TOOLTIP, b.getToolTipText());
    }

    @Test
    void valoresQueNormalizanAVacio_seIgnoranAlDetectarDuplicados() {
        JTextField a = campoCon("");
        JTextField b = campoCon("   ");

        boolean resultado = DuplicadoHighlighter.marcar(
            List.of(a, b), s -> s.trim(), NORMAL, TOOLTIP);

        assertFalse(resultado);
        assertEquals(NORMAL, a.getBackground());
        assertEquals(NORMAL, b.getBackground());
    }

    @Test
    void unicoDuplicadoEntreTresFilas_soloMarcaLasDosCoincidentes() {
        JTextField a = campoCon("400");
        JTextField b = campoCon("500");
        JTextField c = campoCon("400");

        boolean resultado = DuplicadoHighlighter.marcar(
            List.of(a, b, c), s -> s.trim(), NORMAL, TOOLTIP);

        assertTrue(resultado);
        assertNotEquals(NORMAL, a.getBackground());
        assertEquals(NORMAL, b.getBackground());
        assertNotEquals(NORMAL, c.getBackground());
    }

    @Test
    void conflictoResuelto_alVolverALlamarLimpiaElResaltado() {
        JTextField a = campoCon("400");
        JTextField b = campoCon("400");
        DuplicadoHighlighter.marcar(List.of(a, b), s -> s.trim(), NORMAL, TOOLTIP);

        b.setText("401");
        boolean resultado = DuplicadoHighlighter.marcar(List.of(a, b), s -> s.trim(), NORMAL, TOOLTIP);

        assertFalse(resultado);
        assertEquals(NORMAL, a.getBackground());
        assertEquals(NORMAL, b.getBackground());
        assertNull(a.getToolTipText());
    }

    // ── Overload genérico (componentes que no son JTextField) ────────────────

    @Test
    void combosConLaMismaSeleccion_seMarcanAmbosComoDuplicados() {
        JComboBox<String> a = comboCon("400");
        JComboBox<String> b = comboCon("400");

        boolean resultado = DuplicadoHighlighter.marcar(
            List.of(a, b), DuplicadoHighlighterTest::seleccionDe, Function.identity(), NORMAL, TOOLTIP);

        assertTrue(resultado);
        assertNotEquals(NORMAL, a.getBackground());
        assertNotEquals(NORMAL, b.getBackground());
        assertEquals(TOOLTIP, a.getToolTipText());
        assertEquals(TOOLTIP, b.getToolTipText());
    }

    @Test
    void combosConSeleccionDistinta_noSeMarcan() {
        JComboBox<String> a = comboCon("400");
        JComboBox<String> b = comboCon("500");

        boolean resultado = DuplicadoHighlighter.marcar(
            List.of(a, b), DuplicadoHighlighterTest::seleccionDe, Function.identity(), NORMAL, TOOLTIP);

        assertFalse(resultado);
        assertEquals(NORMAL, a.getBackground());
        assertEquals(NORMAL, b.getBackground());
        assertNull(a.getToolTipText());
        assertNull(b.getToolTipText());
    }

    @Test
    void combosSinSeleccion_seIgnoranComoVacios() {
        JComboBox<String> a = comboCon("400");
        JComboBox<String> b = comboCon("500");
        a.setSelectedIndex(-1);
        b.setSelectedIndex(-1);

        boolean resultado = DuplicadoHighlighter.marcar(
            List.of(a, b), DuplicadoHighlighterTest::seleccionDe, Function.identity(), NORMAL, TOOLTIP);

        assertFalse(resultado);
        assertEquals(NORMAL, a.getBackground());
        assertEquals(NORMAL, b.getBackground());
    }

    private static JTextField campoCon(String texto) {
        JTextField campo = new JTextField();
        campo.setBackground(NORMAL);
        campo.setText(texto);
        return campo;
    }

    private static JComboBox<String> comboCon(String item) {
        JComboBox<String> combo = new JComboBox<>(new String[]{item});
        combo.setBackground(NORMAL);
        return combo;
    }

    private static String seleccionDe(JComboBox<String> combo) {
        Object sel = combo.getSelectedItem();
        return sel != null ? sel.toString() : "";
    }
}
