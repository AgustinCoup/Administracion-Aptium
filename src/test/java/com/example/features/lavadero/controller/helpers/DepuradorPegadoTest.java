package com.example.features.lavadero.controller.helpers;

import com.example.features.lavadero.model.ConfiguracionCopiada;
import com.example.features.lavadero.model.InsumoCatalogo;
import com.example.features.lavadero.model.JabonCatalogo;
import com.example.features.lavadero.model.TipoLavado;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepuradorPegadoTest {

    private static final JabonCatalogo SKIP  = new JabonCatalogo(1, "Skip");
    private static final JabonCatalogo LIDER = new JabonCatalogo(2, "Lider");
    private static final InsumoCatalogo SUAVIZANTE  = new InsumoCatalogo(1, "Suavizante", true);
    private static final InsumoCatalogo POTENCIADOR = new InsumoCatalogo(2, "Potenciador", true);

    @Test
    void conCatalogosCompletosNoOmiteNada() {
        ConfiguracionCopiada original = new ConfiguracionCopiada(
            TipoLavado.SUCIO, SKIP, new BigDecimal("100"), List.of(SUAVIZANTE, POTENCIADOR));

        var resultado = DepuradorPegado.depurar(original, List.of(SKIP, LIDER), List.of(SUAVIZANTE, POTENCIADOR));

        assertTrue(resultado.omitidos().isEmpty());
        assertEquals(original, resultado.depurada());
    }

    @Test
    void unJabonQueYaNoEstaEntreLosActivosSeOmiteYSeAvisa() {
        ConfiguracionCopiada original =
            new ConfiguracionCopiada(TipoLavado.SUCIO, SKIP, new BigDecimal("100"), List.of());

        var resultado = DepuradorPegado.depurar(original, List.of(LIDER), List.of());

        assertNull(resultado.depurada().jabon());
        assertEquals(List.of("Skip"), resultado.omitidos());
        // el resto de la config viaja intacto
        assertEquals(TipoLavado.SUCIO, resultado.depurada().tipo());
        assertEquals(new BigDecimal("100"), resultado.depurada().litrosJabon());
    }

    @Test
    void unInsumoDeBajaSeOmiteYSePeganLosDemas() {
        ConfiguracionCopiada original = new ConfiguracionCopiada(
            TipoLavado.LIMPIO, LIDER, null, List.of(SUAVIZANTE, POTENCIADOR));

        var resultado = DepuradorPegado.depurar(original, List.of(LIDER), List.of(SUAVIZANTE));

        assertEquals(List.of(SUAVIZANTE), resultado.depurada().insumos());
        assertEquals(List.of("Potenciador"), resultado.omitidos());
    }

    @Test
    void jabonEInsumoDeBajaALaVezOmitenLosDos() {
        ConfiguracionCopiada original = new ConfiguracionCopiada(
            TipoLavado.SUCIO, SKIP, new BigDecimal("50"), List.of(SUAVIZANTE));

        var resultado = DepuradorPegado.depurar(original, List.of(LIDER), List.of());

        assertNull(resultado.depurada().jabon());
        assertTrue(resultado.depurada().insumos().isEmpty());
        assertEquals(List.of("Skip", "Suavizante"), resultado.omitidos());
    }

    @Test
    void sinJabonNiInsumosNoHayNadaQueOmitir() {
        ConfiguracionCopiada original =
            new ConfiguracionCopiada(TipoLavado.SUCIO, null, null, List.of());

        var resultado = DepuradorPegado.depurar(original, List.of(SKIP), List.of(SUAVIZANTE));

        assertTrue(resultado.omitidos().isEmpty());
        assertNull(resultado.depurada().jabon());
    }

    /**
     * La comparación es por id, no por referencia: la lectura del catálogo que llega a
     * {@code depurar} puede traer otra instancia para el mismo jabón.
     */
    @Test
    void comparaPorIdYNoPorReferencia() {
        ConfiguracionCopiada original =
            new ConfiguracionCopiada(TipoLavado.SUCIO, new JabonCatalogo(1, "Skip"), null, List.of());

        var resultado = DepuradorPegado.depurar(
            original, List.of(new JabonCatalogo(1, "Skip")), List.of());

        assertTrue(resultado.omitidos().isEmpty());
        assertEquals(1, resultado.depurada().jabon().getId());
    }
}
