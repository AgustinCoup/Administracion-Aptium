package com.example.features.lavadero.controller.helpers;

import com.example.features.lavadero.model.JabonCatalogo;
import com.example.features.lavadero.model.OrigenJabon;
import com.example.features.lavadero.model.TipoLavado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La tabla entera de la regla del jabón automático, un caso por fila, más los dos del pedido.
 *
 * <p>Está acá y no en {@code LavarropasCardTest} a propósito: la regla no depende de Swing, y
 * probarla contra un {@code JComboBox} la ataría a los eventos del combo — que es justo lo que el
 * repo hace con {@code ConstructorVistaCiclos} y {@code SincronizadorVolumenFinal}.</p>
 */
class SelectorJabonAutomaticoTest {

    private static final JabonCatalogo SKIP  = new JabonCatalogo(1, "Skip");
    private static final JabonCatalogo LIDER = new JabonCatalogo(2, "Lider");
    private static final JabonCatalogo DE_BAJA = new JabonCatalogo(3, "Retirado", false);

    private static final Map<TipoLavado, JabonCatalogo> DEFAULTS =
        Map.of(TipoLavado.SUCIO, SKIP, TipoLavado.LIMPIO, LIDER);

    @Test
    @DisplayName("Sin tipo elegido no se toca nada")
    void tipoNulo_noTocaNada() {
        assertTrue(SelectorJabonAutomatico
            .alCambiarTipo(null, SKIP, OrigenJabon.AUTO, DEFAULTS).isEmpty());
    }

    @Test
    @DisplayName("Una elección a mano pesa más que la automática")
    void origenManualConJabon_noTocaNada() {
        assertTrue(SelectorJabonAutomatico
            .alCambiarTipo(TipoLavado.LIMPIO, SKIP, OrigenJabon.MANUAL, DEFAULTS).isEmpty());
    }

    /**
     * MANUAL con el combo vacío no es una elección a mano que proteger: es el estado en que queda
     * una card cuyo jabón elegido desapareció del catálogo. Tiene que volver a cargar solo.
     */
    @Test
    void origenManualSinJabon_cargaElDefault() {
        assertEquals(Optional.of(SKIP), SelectorJabonAutomatico
            .alCambiarTipo(TipoLavado.SUCIO, null, OrigenJabon.MANUAL, DEFAULTS));
    }

    @Test
    void origenAutoConJabon_loReemplazaPorElDelTipoNuevo() {
        assertEquals(Optional.of(LIDER), SelectorJabonAutomatico
            .alCambiarTipo(TipoLavado.LIMPIO, SKIP, OrigenJabon.AUTO, DEFAULTS));
    }

    @Test
    @DisplayName("Sin default configurado para ese tipo, no se toca nada")
    void sinDefaultParaElTipo_noTocaNada() {
        Map<TipoLavado, JabonCatalogo> soloSucio = Map.of(TipoLavado.SUCIO, SKIP);

        assertTrue(SelectorJabonAutomatico
            .alCambiarTipo(TipoLavado.LIMPIO, null, OrigenJabon.AUTO, soloSucio).isEmpty());
    }

    /**
     * El {@code JOIN} del DAO no filtra por {@code activo} —Ajustes tiene que poder mostrar que el
     * default quedó apuntando a un jabón retirado—, así que el filtro es esta regla.
     */
    @Test
    void defaultDadoDeBaja_noTocaNada() {
        Map<TipoLavado, JabonCatalogo> conBaja = Map.of(TipoLavado.SUCIO, DE_BAJA);

        assertTrue(SelectorJabonAutomatico
            .alCambiarTipo(TipoLavado.SUCIO, null, OrigenJabon.AUTO, conBaja).isEmpty());
    }

    @Test
    void sinDefaultsNoFalla() {
        assertTrue(SelectorJabonAutomatico
            .alCambiarTipo(TipoLavado.SUCIO, null, OrigenJabon.AUTO, Map.of()).isEmpty());
    }

    // ── Los dos casos del pedido ──────────────────────────────────────────────

    @Test
    @DisplayName("Sucio → Skip automático; al pasar a Limpio queda Lider")
    void elCasoDelPedido_elAutomaticoSigueAlTipo() {
        Optional<JabonCatalogo> primero = SelectorJabonAutomatico
            .alCambiarTipo(TipoLavado.SUCIO, null, OrigenJabon.AUTO, DEFAULTS);
        assertEquals(Optional.of(SKIP), primero);

        // La card queda con el jabón cargado y el origen sigue AUTO.
        Optional<JabonCatalogo> segundo = SelectorJabonAutomatico
            .alCambiarTipo(TipoLavado.LIMPIO, primero.orElseThrow(), OrigenJabon.AUTO, DEFAULTS);
        assertEquals(Optional.of(LIDER), segundo);
    }

    @Test
    @DisplayName("Sucio → el operador elige Lider a mano; al pasar a Limpio sigue Lider")
    void elInversoDelPedido_loElegidoAManoNoSeMueve() {
        assertTrue(SelectorJabonAutomatico
            .alCambiarTipo(TipoLavado.LIMPIO, LIDER, OrigenJabon.MANUAL, DEFAULTS).isEmpty());
        assertTrue(SelectorJabonAutomatico
            .alCambiarTipo(TipoLavado.SUCIO, LIDER, OrigenJabon.MANUAL, DEFAULTS).isEmpty());
    }
}
