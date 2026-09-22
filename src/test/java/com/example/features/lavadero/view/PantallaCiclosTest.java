package com.example.features.lavadero.view;

import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import java.awt.CardLayout;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lo único de {@link PantallaCiclos} que corre headless y que vale la pena fijar: el contrato de
 * {@link PantallaCiclos#reconstruirGrilla}.
 *
 * <p>Que la card se <b>reuse</b> es lo que hace que dar de baja un lavarropas no le borre al
 * operador la configuración que está tipeando en otro — una {@code LavarropasCard} guarda esa
 * configuración en sus propios combos, así que conservar la instancia <i>es</i> conservar la
 * configuración. Es el punto 5 del smoke del Paso 3, y el único que se puede sostener sin clickear
 * la pantalla.</p>
 */
class PantallaCiclosTest {

    private static PantallaCiclos nuevaPantalla() {
        JPanel contenedor = new JPanel(new CardLayout());
        return new PantallaCiclos((CardLayout) contenedor.getLayout(), contenedor);
    }

    @Test
    void recienConstruida_noTieneNingunaCard() {
        // Qué lavarropas hay es un dato de la base: la grilla la puebla el primer pintar().
        assertTrue(nuevaPantalla().getAllCards().isEmpty());
    }

    @Test
    void reconstruirGrilla_creaUnaCardPorNumero() {
        PantallaCiclos pantalla = nuevaPantalla();

        pantalla.reconstruirGrilla(List.of(1, 2, 3));

        assertEquals(List.of(1, 2, 3), List.copyOf(pantalla.getAllCards().keySet()));
        assertNotNull(pantalla.getCard(2));
    }

    @Test
    void reconstruirGrilla_reusaLasCardsQueSobreviven() {
        PantallaCiclos pantalla = nuevaPantalla();
        pantalla.reconstruirGrilla(List.of(1, 2, 3));
        LavarropasCard card1 = pantalla.getCard(1);
        LavarropasCard card2 = pantalla.getCard(2);

        pantalla.reconstruirGrilla(List.of(1, 2));   // se dio de baja el #3

        assertSame(card1, pantalla.getCard(1),
            "recrear la card le borraría al operador la configuración que está tipeando");
        assertSame(card2, pantalla.getCard(2));
    }

    @Test
    void reconstruirGrilla_descartaLasQueYaNoEstan() {
        PantallaCiclos pantalla = nuevaPantalla();
        pantalla.reconstruirGrilla(List.of(1, 2, 3));

        pantalla.reconstruirGrilla(List.of(1, 2));

        assertNull(pantalla.getCard(3));
        assertEquals(2, pantalla.getAllCards().size());
    }

    /**
     * El mapa se repuebla desde cero en el orden recibido, no con {@code put} al final del mapa
     * viejo: {@code getAllCards()} es un {@code LinkedHashMap} y su orden de inserción es lo que
     * decide cómo se dibujan las columnas.
     */
    @Test
    void reconstruirGrilla_reponeElOrdenAunqueUnNumeroVuelvaDespues() {
        PantallaCiclos pantalla = nuevaPantalla();
        pantalla.reconstruirGrilla(List.of(1, 2, 3));
        pantalla.reconstruirGrilla(List.of(1, 3));   // se da de baja el #2
        LavarropasCard card1 = pantalla.getCard(1);

        pantalla.reconstruirGrilla(List.of(1, 2, 3, 4));   // se reactiva el #2 y se agrega el #4

        assertEquals(List.of(1, 2, 3, 4), List.copyOf(pantalla.getAllCards().keySet()));
        assertSame(card1, pantalla.getCard(1), "las que nunca se fueron se siguen reusando");
    }
}
