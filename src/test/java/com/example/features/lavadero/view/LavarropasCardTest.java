package com.example.features.lavadero.view;

import com.example.features.lavadero.model.ConfiguracionCopiada;
import com.example.features.lavadero.model.InsumoCatalogo;
import com.example.features.lavadero.model.JabonCatalogo;
import com.example.features.lavadero.model.OrigenJabon;
import com.example.features.lavadero.model.TipoLavado;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LavarropasCard construye bien en headless (es un JPanel, no un JDialog). No expone
 * setters de configuración -no hay otro consumidor que el operador tipeando-, así que
 * este test usa reflection solo para dejarla en un estado no-default antes de resetear,
 * sin agregar API de producción para tres setters.
 */
class LavarropasCardTest {

    private static final JabonCatalogo SKIP  = new JabonCatalogo(1, "Skip");
    private static final JabonCatalogo LIDER = new JabonCatalogo(2, "Lider");
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

    // ── Jabón: catálogo, origen y carga automática ───────────────────────────

    /**
     * {@code addItem} autoselecciona el primero: sin limpiar la selección después de llenar un
     * combo que estaba vacío, el operador se llevaría puesto un jabón que nunca eligió.
     */
    @Test
    void setJabonesSobreUnaCardNuevaDejaElComboSinSeleccion() {
        LavarropasCard card = new LavarropasCard(1);

        card.setJabones(List.of(SKIP, LIDER));

        assertNull(card.getJabon());
        assertEquals(OrigenJabon.AUTO, card.getOrigenJabon());
    }

    /**
     * El catálogo se relee en <b>cada</b> carga de la pantalla, así que si {@code setJabones}
     * vaciara la selección, cada F5 le borraría el jabón a una card a medio configurar.
     *
     * <p>El catálogo nuevo trae <b>otra instancia</b> con el mismo id a propósito: es lo que
     * devuelve una segunda lectura de la base, y {@code JabonCatalogo} no tiene {@code equals}.
     * Comparando por referencia este test fallaría, que es exactamente el bug.</p>
     */
    @Test
    void setJabonesConservaElJabonElegidoSiSuIdSigueEnElCatalogo() throws Exception {
        LavarropasCard card = new LavarropasCard(1);
        card.setJabones(List.of(SKIP, LIDER));
        elegirJabonAMano(card, SKIP);

        card.setJabones(List.of(new JabonCatalogo(SKIP.getId(), "Skip"), new JabonCatalogo(2, "Lider")));

        assertNotNull(card.getJabon());
        assertEquals(SKIP.getId(), card.getJabon().getId());
        assertEquals(OrigenJabon.MANUAL, card.getOrigenJabon(),
            "conservar la selección no puede cambiar quién la puso");
    }

    @Test
    void setJabonesLimpiaElJabonQueYaNoEstaEnElCatalogoYVuelveAAuto() throws Exception {
        LavarropasCard card = new LavarropasCard(1);
        card.setJabones(List.of(SKIP, LIDER));
        elegirJabonAMano(card, SKIP);

        card.setJabones(List.of(LIDER));

        assertNull(card.getJabon());
        assertEquals(OrigenJabon.AUTO, card.getOrigenJabon(),
            "sin jabón elegido no hay elección a mano que respetar: la carga automática vuelve a mandar");
    }

    @Test
    void elegirUnJabonEnElComboDejaElOrigenEnManual() throws Exception {
        LavarropasCard card = new LavarropasCard(1);
        card.setJabones(List.of(SKIP, LIDER));

        elegirJabonAMano(card, LIDER);

        assertEquals(OrigenJabon.MANUAL, card.getOrigenJabon());
    }

    /**
     * <b>El test del flag anti-callback.</b> {@code setSelectedItem} dispara el mismo
     * {@code ActionListener} que un click del operador: sin distinguirlos, el jabón que puso la
     * carga automática se marcaría {@code MANUAL} él solo y a partir de ahí el tipo de lavado
     * dejaría de arrastrarlo — "a veces anda".
     */
    @Test
    void setJabonAutomaticoDejaElOrigenEnAutoYNoLoMarcaComoManual() {
        LavarropasCard card = new LavarropasCard(1);
        card.setJabones(List.of(SKIP, LIDER));

        card.setJabonAutomatico(SKIP);

        assertEquals(SKIP, card.getJabon());
        assertEquals(OrigenJabon.AUTO, card.getOrigenJabon());
    }

    /**
     * El jabón por defecto viene de otra consulta que el catálogo del combo, o sea de otra
     * instancia. Un {@code JComboBox} no editable <b>rechaza en silencio</b> un
     * {@code setSelectedItem} con un objeto que no sea {@code equals} a alguno de sus ítems, así
     * que sin resolverlo por id la carga automática no seleccionaría nada y sin un solo error.
     */
    @Test
    void setJabonAutomaticoAceptaOtraInstanciaConElMismoId() {
        LavarropasCard card = new LavarropasCard(1);
        card.setJabones(List.of(SKIP, LIDER));

        card.setJabonAutomatico(new JabonCatalogo(SKIP.getId(), "Skip"));

        assertNotNull(card.getJabon());
        assertEquals(SKIP.getId(), card.getJabon().getId());
    }

    /** Un default que no está entre los activos no se fuerza: es lo mismo que no tener default. */
    @Test
    void setJabonAutomaticoConUnJabonFueraDelCatalogoNoTocaNada() throws Exception {
        LavarropasCard card = new LavarropasCard(1);
        card.setJabones(List.of(LIDER));
        elegirJabonAMano(card, LIDER);

        card.setJabonAutomatico(new JabonCatalogo(99, "Inexistente"));

        assertEquals(LIDER, card.getJabon());
        assertEquals(OrigenJabon.MANUAL, card.getOrigenJabon());
    }

    /** Pegar es una elección a mano: el tipo de lavado no lo pisa después. */
    @Test
    void setJabonManualDejaElOrigenEnManual() {
        LavarropasCard card = new LavarropasCard(1);
        card.setJabones(List.of(SKIP, LIDER));

        card.setJabonManual(LIDER);

        assertEquals(LIDER, card.getJabon());
        assertEquals(OrigenJabon.MANUAL, card.getOrigenJabon());
    }

    @Test
    void resetConfiguracionVuelveElOrigenAAuto() throws Exception {
        LavarropasCard card = new LavarropasCard(1);
        card.setJabones(List.of(SKIP, LIDER));
        elegirJabonAMano(card, SKIP);

        card.resetConfiguracion();

        assertEquals(OrigenJabon.AUTO, card.getOrigenJabon());
    }

    /**
     * El canal del tipo de lavado es el que dispara la carga automática, y tiene que avisar
     * también cuando el tipo lo cambia el operador desde el combo.
     */
    @Test
    void cambiarElTipoDeLavadoAvisaPorSuPropioCanal() throws Exception {
        LavarropasCard card = new LavarropasCard(1);
        int[] avisos = {0};
        card.setOnTipoLavadoChanged(() -> avisos[0]++);

        field(card, "cmbTipoLavado", JComboBox.class).setSelectedItem(TipoLavado.SUCIO);

        assertEquals(1, avisos[0]);
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

    // ── Copiar y pegar ───────────────────────────────────────────────────────

    @Test
    void copiarConfiguracionNoIncluyeLosElementosCargados() throws Exception {
        LavarropasCard card = new LavarropasCard(1);
        card.setJabones(List.of(SKIP));
        card.setInsumos(List.of(SUAVIZANTE));
        field(card, "cmbTipoLavado", JComboBox.class).setSelectedItem(TipoLavado.SUCIO);
        field(card, "cmbJabon", JComboBox.class).setSelectedItem(SKIP);
        field(card, "txtLitrosJabon", JTextField.class).setText("500");
        agregarInsumo(card, SUAVIZANTE);

        ConfiguracionCopiada copiada = card.copiarConfiguracion();

        assertEquals(TipoLavado.SUCIO, copiada.tipo());
        assertEquals(SKIP, copiada.jabon());
        assertEquals(new BigDecimal("500"), copiada.litrosJabon());
        assertEquals(List.of(SUAVIZANTE), copiada.insumos());
    }

    @Test
    void pegarConfiguracionDejaLosCuatroCampos() {
        LavarropasCard origen = new LavarropasCard(1);
        origen.setJabones(List.of(SKIP, LIDER));
        origen.setInsumos(List.of(SUAVIZANTE, POTENCIADOR));
        ConfiguracionCopiada copiada = new ConfiguracionCopiada(
            TipoLavado.LIMPIO, LIDER, new BigDecimal("300"), List.of(SUAVIZANTE));

        LavarropasCard destino = new LavarropasCard(5);
        destino.setJabones(List.of(SKIP, LIDER));
        destino.setInsumos(List.of(SUAVIZANTE, POTENCIADOR));

        destino.pegarConfiguracion(copiada);

        assertEquals(TipoLavado.LIMPIO, destino.getTipoLavado());
        assertEquals(LIDER, destino.getJabon());
        assertEquals(new BigDecimal("300"), destino.getLitrosJabon());
        assertEquals(List.of(SUAVIZANTE), destino.getInsumosSeleccionados());
    }

    /** El jabón pegado queda como si el operador lo hubiera elegido a mano. */
    @Test
    void elJabonPegadoQuedaManual() {
        LavarropasCard destino = new LavarropasCard(5);
        destino.setJabones(List.of(SKIP, LIDER));

        destino.pegarConfiguracion(
            new ConfiguracionCopiada(TipoLavado.SUCIO, SKIP, null, List.of()));

        assertEquals(OrigenJabon.MANUAL, destino.getOrigenJabon());
    }

    /**
     * El test que fija la razón de ser del orden tipo-antes-que-jabón: si el jabón pegado quedara
     * {@code AUTO}, cambiar el tipo después lo volvería a pisar. Quedando {@code MANUAL}, no.
     */
    @Test
    void cambiarElTipoDespuesDePegarNoPisaElJabonPegado() throws Exception {
        LavarropasCard destino = new LavarropasCard(5);
        destino.setJabones(List.of(SKIP, LIDER));
        destino.pegarConfiguracion(
            new ConfiguracionCopiada(TipoLavado.SUCIO, SKIP, new BigDecimal("100"), List.of()));

        field(destino, "cmbTipoLavado", JComboBox.class).setSelectedItem(TipoLavado.LIMPIO);

        assertEquals(SKIP, destino.getJabon(),
            "una elección a mano (pegar cuenta como tal) siempre pesa más que la automática");
    }

    @Test
    void pegarNoTocaLosItemsDeLaTabla() {
        LavarropasCard destino = new LavarropasCard(5);
        destino.setJabones(List.of(SKIP));
        destino.setItems(List.of(), java.util.Map.of());

        destino.pegarConfiguracion(
            new ConfiguracionCopiada(TipoLavado.SUCIO, SKIP, new BigDecimal("100"), List.of()));

        assertFalse(destino.tieneItems());
    }

    @Test
    void pegarUnaConfigConLitrosJabonNuloDejaElCampoVacio() {
        LavarropasCard destino = new LavarropasCard(5);
        destino.setJabones(List.of(SKIP));

        destino.pegarConfiguracion(
            new ConfiguracionCopiada(TipoLavado.SUCIO, SKIP, null, List.of()));

        assertNull(destino.getLitrosJabon());
    }

    /**
     * Simula al operador eligiendo un jabón en el combo: es el camino que tiene que dejar el
     * origen en {@code MANUAL}. Va por el combo y no por un setter de producción justamente
     * porque lo que se está probando es que ese evento se distinga del programático.
     */
    private static void elegirJabonAMano(LavarropasCard card, JabonCatalogo jabon) throws Exception {
        field(card, "cmbJabon", JComboBox.class).setSelectedItem(jabon);
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
