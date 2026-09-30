package com.example.features.eliminaciones.controller.helpers;

import com.example.common.constants.Constantes.Mensajes;
import com.example.common.eliminacion.Bloqueo;
import com.example.common.eliminacion.IngresoAEliminar;
import com.example.common.eliminacion.ModuloIngreso;
import com.example.common.eliminacion.ResumenEquipo;
import com.example.common.eliminacion.ResumenIngresoLavadero;
import com.example.features.lavadero.model.EstadoIngresoLavadero;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextoEliminacionTest {

    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 9, 1, 10, 30);

    private static ResumenEquipo ortopedia(List<ResumenEquipo.LineaMaterial> materiales) {
        return new ResumenEquipo(new IngresoAEliminar(ModuloIngreso.ORTOPEDIA, 12), "Clínica Sur",
            "Sanatorio Norte", "Perez, Juan", FECHA, "ENTREGADO", 4, materiales, List.of(), List.of());
    }

    private static ResumenEquipo otros(List<Integer> origen, List<Bloqueo> bloqueos) {
        return new ResumenEquipo(new IngresoAEliminar(ModuloIngreso.OTROS, 30), "APTIUM", null, null,
            FECHA, "EMPAQUETADO", 2, List.of(), bloqueos, origen);
    }

    private static ResumenIngresoLavadero lavadero(List<ResumenIngresoLavadero.LineaElemento> elementos,
                                                   List<ResumenIngresoLavadero.DerivadoCde> derivados,
                                                   List<Bloqueo> bloqueos) {
        return new ResumenIngresoLavadero(new IngresoAEliminar(ModuloIngreso.LAVADERO, 7), "Hotel Sol", FECHA,
            EstadoIngresoLavadero.LAVADO, new BigDecimal("42.5"), elementos, derivados, bloqueos);
    }

    @Test
    void ortopedia_muestraDatosYMateriales() {
        String texto = TextoEliminacion.confirmacion(ortopedia(List.of(
            new ResumenEquipo.LineaMaterial("Tornillo", 3, "ENTREGADO", null),
            new ResumenEquipo.LineaMaterial("Placa", 1, "ESTERILIZADO", "L-15"))));

        assertTrue(texto.contains("Ortopedias #12"));
        assertTrue(texto.contains("Clínica Sur"));
        assertTrue(texto.contains("Sanatorio Norte"));
        assertTrue(texto.contains("Perez, Juan"));
        assertTrue(texto.contains("01/09/2026"));
        assertTrue(texto.contains("ENTREGADO"));
        assertTrue(texto.contains("3 × Tornillo (ENTREGADO)"));
        assertTrue(texto.contains("1 × Placa (ESTERILIZADO), lote L-15"));
    }

    @Test
    void ortopedia_sinMateriales_loDice() {
        assertTrue(TextoEliminacion.confirmacion(ortopedia(List.of()))
            .contains(Mensajes.ELIMINAR_SIN_MATERIALES));
    }

    @Test
    void ortopedia_sinPacienteNiInstitucion_omiteEsasLineas() {
        ResumenEquipo r = new ResumenEquipo(new IngresoAEliminar(ModuloIngreso.ORTOPEDIA, 1), null, null, null,
            null, "NUEVO", 1, List.of(), List.of(), List.of());

        String texto = TextoEliminacion.confirmacion(r);

        assertFalse(texto.contains("Institución"));
        assertFalse(texto.contains("Paciente"));
        assertFalse(texto.contains("Cliente"));
        assertFalse(texto.contains("Ingreso:"));
        assertFalse(texto.contains("null"));
    }

    @Test
    void otrosCargadoAMano_noMencionaLavadero() {
        String texto = TextoEliminacion.confirmacion(otros(List.of(), List.of()));

        assertTrue(texto.contains("Otros #30"));
        assertFalse(texto.contains("Lavadero"));
    }

    @Test
    void otrosDerivado_diceQueElIngresoDeLavaderoNoSeElimina() {
        String texto = TextoEliminacion.confirmacion(otros(List.of(4, 9), List.of()));

        assertTrue(texto.contains(String.format(Mensajes.ELIMINAR_VINO_DE_LAVADERO, "#4, #9")));
    }

    @Test
    void lavaderoConDerivados_nombraCadaIngresoDelCde() {
        String texto = TextoEliminacion.confirmacion(lavadero(
            List.of(new ResumenIngresoLavadero.LineaElemento("Sábana", 20)),
            List.of(new ResumenIngresoLavadero.DerivadoCde(31, "NUEVO", 12),
                    new ResumenIngresoLavadero.DerivadoCde(32, "ENTREGADO", 8)),
            List.of()));

        assertTrue(texto.contains("Lavadero #7"));
        assertTrue(texto.contains("Hotel Sol"));
        assertTrue(texto.contains("42.5 kg"));
        assertTrue(texto.contains("20 × Sábana"));
        assertTrue(texto.contains(String.format(Mensajes.ELIMINAR_DERIVADO_DEL_CDE, 31, "NUEVO", 12)));
        assertTrue(texto.contains(String.format(Mensajes.ELIMINAR_DERIVADO_DEL_CDE, 32, "ENTREGADO", 8)));
    }

    @Test
    void lavaderoSinClasificar_loDice() {
        String texto = TextoEliminacion.confirmacion(lavadero(List.of(), List.of(), List.of()));

        assertTrue(texto.contains(Mensajes.ELIMINAR_SIN_CLASIFICAR));
        assertFalse(texto.contains("CDE"));
    }

    @Test
    void siempreTerminaConNoSePuedeDeshacer() {
        List<ResumenEquipo> equipos = List.of(ortopedia(List.of()), otros(List.of(1), List.of()));
        for (ResumenEquipo r : equipos) {
            assertTrue(TextoEliminacion.confirmacion(r).endsWith(Mensajes.ELIMINAR_NO_SE_PUEDE_DESHACER));
        }
        assertTrue(TextoEliminacion.confirmacion(lavadero(List.of(), List.of(), List.of()))
            .endsWith(Mensajes.ELIMINAR_NO_SE_PUEDE_DESHACER));
    }

    @Test
    void confirmacion_avisaQueSeGuardaUnaCopia() {
        assertTrue(TextoEliminacion.confirmacion(ortopedia(List.of())).contains(Mensajes.ELIMINAR_SE_ARCHIVA));
    }

    @Test
    void bloqueos_unaLineaPorBloqueoConQueHacer() {
        String texto = TextoEliminacion.bloqueos(lavadero(List.of(), List.of(), List.of(
            new Bloqueo.CicloEnCurso(3),
            new Bloqueo.DerivadoEnLoteEnCurso(31, "L-8"))));

        assertTrue(texto.startsWith(Mensajes.ELIMINACION_BLOQUEADA));
        assertTrue(texto.contains(String.format(Mensajes.ELIMINAR_BLOQUEO_CICLO_EN_CURSO, 3)));
        assertTrue(texto.contains(String.format(Mensajes.ELIMINAR_BLOQUEO_DERIVADO_EN_LOTE, 31, "L-8")));
        assertEquals(3, texto.split("\n").length);
    }

    @Test
    void bloqueos_esElMismoTextoQueLaExcepcionDeLaTransaccion() {
        List<Bloqueo> bloqueos = List.of(new Bloqueo.LoteEnCurso("L-1"));

        String delResumen = TextoEliminacion.bloqueos(otros(List.of(), bloqueos));
        String delRechazo = new com.example.common.eliminacion.EliminacionBloqueadaException(bloqueos).getMessage();

        assertEquals(delRechazo, delResumen);
    }
}
