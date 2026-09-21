package com.example.features.lavadero.view;

import com.example.features.lavadero.model.InsumoCatalogo;
import com.example.features.lavadero.model.JabonCatalogo;
import com.example.features.lavadero.model.TipoLavado;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LavarropasCard construye bien en headless (es un JPanel, no un JDialog). No expone
 * setters de configuración -no hay otro consumidor que el operador tipeando-, así que
 * este test usa reflection solo para dejarla en un estado no-default antes de resetear,
 * sin agregar API de producción para tres setters.
 */
class LavarropasCardTest {

    private static final JabonCatalogo SKIP = new JabonCatalogo(1, "Skip");
    private static final InsumoCatalogo SUAVIZANTE  = new InsumoCatalogo(1, "Suavizante", true);
    private static final InsumoCatalogo POTENCIADOR = new InsumoCatalogo(2, "Potenciador", true);

    @Test
    void resetConfiguracionVuelveLosCamposASuEstadoInicial() throws Exception {
        LavarropasCard card = new LavarropasCard(1);
        card.setJabones(List.of(SKIP));
        card.setInsumos(List.of(SUAVIZANTE, POTENCIADOR));

        field(card, "cmbTipoLavado", JComboBox.class).setSelectedItem(TipoLavado.values()[0]);
        field(card, "cmbJabon", JComboBox.class).setSelectedItem(SKIP);
        field(card, "txtLitrosJabon", JTextField.class).setText("500");
        agregarInsumo(card, SUAVIZANTE);

        card.resetConfiguracion();

        assertNull(card.getTipoLavado());
        assertNull(card.getJabon());
        assertNull(card.getLitrosJabon());
        assertTrue(card.getInsumosSeleccionados().isEmpty());
    }

    // ── Insumos ──────────────────────────────────────────────────────────────

    @Test
    void sinAgregarInsumosLaListaEstaVacia() {
        LavarropasCard card = new LavarropasCard(1);
        card.setInsumos(List.of(SUAVIZANTE, POTENCIADOR));

        assertTrue(card.getInsumosSeleccionados().isEmpty());
    }

    @Test
    void agregarDosInsumosLosDevuelveEnElOrdenEnQueSeAgregaron() {
        LavarropasCard card = new LavarropasCard(1);
        card.setInsumos(List.of(SUAVIZANTE, POTENCIADOR));

        agregarInsumo(card, POTENCIADOR);
        agregarInsumo(card, SUAVIZANTE);

        assertEquals(List.of(POTENCIADOR, SUAVIZANTE), card.getInsumosSeleccionados());
    }

    @Test
    void agregarElMismoInsumoDosVecesDejaUno() {
        LavarropasCard card = new LavarropasCard(1);
        card.setInsumos(List.of(SUAVIZANTE));

        agregarInsumo(card, SUAVIZANTE);
        agregarInsumo(card, SUAVIZANTE);

        assertEquals(List.of(SUAVIZANTE), card.getInsumosSeleccionados());
    }

    /**
     * {@code setInsumos} repuebla el catálogo del combo, pero no puede borrar lo que el operador
     * ya eligió: es el invariante que sostiene que F5 no pise una card a medio configurar.
     */
    @Test
    void setInsumosConCatalogoNuevoNoBorraLosYaElegidos() {
        LavarropasCard card = new LavarropasCard(1);
        card.setInsumos(List.of(SUAVIZANTE, POTENCIADOR));
        agregarInsumo(card, SUAVIZANTE);

        card.setInsumos(List.of(SUAVIZANTE, POTENCIADOR));

        assertEquals(List.of(SUAVIZANTE), card.getInsumosSeleccionados());
    }

    /**
     * {@code addItem} autoselecciona el primero: sin vaciar la selección después de llenar el
     * combo, el operador se llevaría puesto un jabón que nunca eligió.
     */
    @Test
    void setJabonesDejaElComboSinSeleccion() {
        LavarropasCard card = new LavarropasCard(1);

        card.setJabones(List.of(SKIP, new JabonCatalogo(2, "Lider")));

        assertNull(card.getJabon());
    }

    @Test
    void configuracionSinJabonNoEstaCompleta() throws Exception {
        LavarropasCard card = new LavarropasCard(1);
        card.setJabones(List.of(SKIP));
        field(card, "cmbTipoLavado", JComboBox.class).setSelectedItem(TipoLavado.values()[0]);
        field(card, "txtLitrosJabon", JTextField.class).setText("500");

        assertFalse(card.tieneConfiguracionCompleta(),
            "con tipo y mL pero sin jabón el ciclo no se puede lanzar");
    }

    @Test
    void configuracionConLosTresCamposObligatoriosEstaCompleta() throws Exception {
        LavarropasCard card = new LavarropasCard(1);
        card.setJabones(List.of(SKIP));
        field(card, "cmbTipoLavado", JComboBox.class).setSelectedItem(TipoLavado.values()[0]);
        field(card, "cmbJabon", JComboBox.class).setSelectedItem(SKIP);
        field(card, "txtLitrosJabon", JTextField.class).setText("500");

        assertTrue(card.tieneConfiguracionCompleta());
    }

    /** Los insumos son opcionales: no entran en "config completa". */
    @Test
    void configuracionCompletaSinNingunInsumoElegido() throws Exception {
        LavarropasCard card = new LavarropasCard(1);
        card.setJabones(List.of(SKIP));
        card.setInsumos(List.of(SUAVIZANTE));
        field(card, "cmbTipoLavado", JComboBox.class).setSelectedItem(TipoLavado.values()[0]);
        field(card, "cmbJabon", JComboBox.class).setSelectedItem(SKIP);
        field(card, "txtLitrosJabon", JTextField.class).setText("500");

        assertTrue(card.tieneConfiguracionCompleta());
        assertTrue(card.getInsumosSeleccionados().isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(LavarropasCard card, String name, Class<T> type) throws Exception {
        Field f = LavarropasCard.class.getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(card);
    }

    /**
     * Simula el clic del operador: elegir el insumo en el combo y apretar "+". Es reflection
     * contra {@code PanelInsumosCard} en vez de una API de producción, por el mismo motivo que
     * {@link #field}: nadie más consume ese atajo.
     */
    @SuppressWarnings("unchecked")
    private static void agregarInsumo(LavarropasCard card, InsumoCatalogo insumo) {
        try {
            Field panelField = LavarropasCard.class.getDeclaredField("panelInsumos");
            panelField.setAccessible(true);
            Object panelInsumos = panelField.get(card);

            Field comboField = panelInsumos.getClass().getDeclaredField("combo");
            comboField.setAccessible(true);
            JComboBox<InsumoCatalogo> combo = (JComboBox<InsumoCatalogo>) comboField.get(panelInsumos);
            combo.setSelectedItem(insumo);

            Field btnField = panelInsumos.getClass().getDeclaredField("btnMas");
            btnField.setAccessible(true);
            JButton btnMas = (JButton) btnField.get(panelInsumos);
            for (var l : btnMas.getActionListeners()) {
                l.actionPerformed(new java.awt.event.ActionEvent(btnMas, java.awt.event.ActionEvent.ACTION_PERFORMED, ""));
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
