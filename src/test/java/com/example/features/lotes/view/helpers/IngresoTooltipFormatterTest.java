package com.example.features.lotes.view.helpers;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests del formateador puro del tooltip de ingreso (paso 1 del plan
 * {@code plans/lotes-tooltip-info-ingreso.md}). Sin Swing: cubre el contenido
 * exacto del tooltip en aislamiento.
 */
class IngresoTooltipFormatterTest {

    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 3, 5, 14, 30);

    private static MaterialLoteItem item(String descripcion) {
        return new MaterialLoteItem(1, 100, descripcion, 2, 5, "Cliente SA", false);
    }

    @Test
    void ortopediaCompleta_muestraTodosLosDatosDelIngreso() {
        IngresoInfo info = IngresoInfo.deOrtopedia(
                "Cliente SA", "Dr. Pérez", "Juan Gómez", "Hospital Central", FECHA);

        String html = IngresoTooltipFormatter.format(item("Caja de tornillos"), info);

        assertTrue(html.startsWith("<html><b>Caja de tornillos</b>"), html);
        assertTrue(html.contains("Cliente: Cliente SA"), html);
        assertTrue(html.contains("Profesional: Dr. Pérez"), html);
        assertTrue(html.contains("Paciente: Juan Gómez"), html);
        assertTrue(html.contains("Institución: Hospital Central"), html);
        assertTrue(html.contains("Ingreso: 05/03/2026 14:30"), html);
        assertTrue(html.endsWith("</html>"), html);
    }

    @Test
    void ortopediaConCamposVacios_muestraGuionPeroMantieneLasLineas() {
        IngresoInfo info = IngresoInfo.deOrtopedia("Cliente SA", null, "  ", null, null);

        String html = IngresoTooltipFormatter.format(item("Placa"), info);

        assertTrue(html.contains("Profesional: —"), html);
        assertTrue(html.contains("Paciente: —"), html);
        assertTrue(html.contains("Institución: —"), html);
        assertTrue(html.contains("Ingreso: —"), html);
    }

    @Test
    void otrosRemito_incluyeRemitoYOmiteDatosMedicos() {
        IngresoInfo info = IngresoInfo.deOtros("Clínica Norte", true, "05032026-7", FECHA);

        String html = IngresoTooltipFormatter.format(item("Elementos"), info);

        assertTrue(html.contains("Remito: 05032026-7"), html);
        assertTrue(html.contains("Cliente: Clínica Norte"), html);
        assertTrue(html.contains("Ingreso: 05/03/2026 14:30"), html);
        assertFalse(html.contains("Profesional"), html);
        assertFalse(html.contains("Paciente"), html);
        assertFalse(html.contains("Institución"), html);
    }

    @Test
    void otrosDetalles_noIncluyeLineaDeRemito() {
        IngresoInfo info = IngresoInfo.deOtros("Clínica Norte", false, null, FECHA);

        String html = IngresoTooltipFormatter.format(item("Pinza"), info);

        assertFalse(html.contains("Remito"), html);
        assertTrue(html.contains("Cliente: Clínica Norte"), html);
    }

    @Test
    void sinInfoDeIngreso_muestraSoloMaterialYAviso() {
        String html = IngresoTooltipFormatter.format(item("Caja"), null);

        assertEquals("<html><b>Caja</b><br>Sin datos de ingreso</html>", html);
    }

    @Test
    void descripcionConHtml_seEscapaYNoRompeElTooltip() {
        IngresoInfo info = IngresoInfo.deOrtopedia("A & B", null, null, null, FECHA);

        String html = IngresoTooltipFormatter.format(item("<b>Tornillo</b> & clavo"), info);

        assertTrue(html.contains("&lt;b&gt;Tornillo&lt;/b&gt; &amp; clavo"), html);
        assertTrue(html.contains("Cliente: A &amp; B"), html);
    }
}
