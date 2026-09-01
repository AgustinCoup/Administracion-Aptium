package com.example.features.lavadero.view;

import com.example.features.lavadero.model.JabonCatalogo;
import com.example.features.lavadero.model.TipoLavado;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.lang.reflect.Field;
import java.util.List;

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

    @Test
    void resetConfiguracionVuelveLosCamposASuEstadoInicial() throws Exception {
        LavarropasCard card = new LavarropasCard(1);
        card.setJabones(List.of(SKIP));

        field(card, "cmbTipoLavado", JComboBox.class).setSelectedItem(TipoLavado.values()[0]);
        field(card, "cmbJabon", JComboBox.class).setSelectedItem(SKIP);
        field(card, "txtLitrosJabon", JTextField.class).setText("500");
        field(card, "chkSuavizante", JCheckBox.class).setSelected(true);
        field(card, "chkPotenciador", JCheckBox.class).setSelected(true);
        field(card, "txtLitrosTotales", JTextField.class).setText("20");

        card.resetConfiguracion();

        assertNull(card.getTipoLavado());
        assertNull(card.getJabon());
        assertNull(card.getLitrosJabon());
        assertFalse(card.isSuavizante());
        assertFalse(card.isPotenciador());
        assertNull(card.getLitrosTotales());
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
    void configuracionConTipoJabonYMililitrosEstaCompleta() throws Exception {
        LavarropasCard card = new LavarropasCard(1);
        card.setJabones(List.of(SKIP));
        field(card, "cmbTipoLavado", JComboBox.class).setSelectedItem(TipoLavado.values()[0]);
        field(card, "cmbJabon", JComboBox.class).setSelectedItem(SKIP);
        field(card, "txtLitrosJabon", JTextField.class).setText("500");

        assertTrue(card.tieneConfiguracionCompleta());
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(LavarropasCard card, String name, Class<T> type) throws Exception {
        Field f = LavarropasCard.class.getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(card);
    }
}
