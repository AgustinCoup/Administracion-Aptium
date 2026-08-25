package com.example.features.lavadero.view;

import com.example.features.lavadero.model.TipoLavado;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * LavarropasCard construye bien en headless (es un JPanel, no un JDialog). No expone
 * setters de configuración -no hay otro consumidor que el operador tipeando-, así que
 * este test usa reflection solo para dejarla en un estado no-default antes de resetear,
 * sin agregar API de producción para tres setters.
 */
class LavarropasCardTest {

    @Test
    void resetConfiguracionVuelveLosCamposASuEstadoInicial() throws Exception {
        LavarropasCard card = new LavarropasCard(1);

        field(card, "cmbTipoLavado", JComboBox.class).setSelectedItem(TipoLavado.values()[0]);
        field(card, "txtLitrosJabon", JTextField.class).setText("500");
        field(card, "chkSuavizante", JCheckBox.class).setSelected(true);
        field(card, "chkPotenciador", JCheckBox.class).setSelected(true);
        field(card, "txtLitrosTotales", JTextField.class).setText("20");

        card.resetConfiguracion();

        assertNull(card.getTipoLavado());
        assertNull(card.getLitrosJabon());
        assertFalse(card.isSuavizante());
        assertFalse(card.isPotenciador());
        assertNull(card.getLitrosTotales());
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(LavarropasCard card, String name, Class<T> type) throws Exception {
        Field f = LavarropasCard.class.getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(card);
    }
}
