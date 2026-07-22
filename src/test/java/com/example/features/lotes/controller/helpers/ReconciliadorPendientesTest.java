package com.example.features.lotes.controller.helpers;

import com.example.features.lotes.view.helpers.MaterialLoteItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de la lógica pura de reconciliación disponibles ↔ pendientes (paso 3 del
 * plan {@code plans/lotes-multiseleccion-dnd-materiales.md}). Sin Swing ni H2:
 * cubre la ruta múltiple del drag-and-drop en aislamiento.
 */
class ReconciliadorPendientesTest {

    private final ReconciliadorPendientes reconciliador = new ReconciliadorPendientes();

    private static MaterialLoteItem orto(int materialId, String desc, int cantidad, int volumen) {
        return new MaterialLoteItem(materialId, 100 + materialId, desc, cantidad, volumen, "Cliente", false);
    }

    private static MaterialLoteItem otros(int materialId, String desc, int cantidad) {
        return new MaterialLoteItem(materialId, 100 + materialId, desc, cantidad, 1, "Cliente", true);
    }

    private static MaterialLoteItem buscar(List<MaterialLoteItem> lista, int materialId) {
        return lista.stream().filter(i -> i.getMaterialId() == materialId).findFirst().orElse(null);
    }

    @Test
    void alta_deTresItemsDistintos_generaTresPendientes() {
        EstadoStaging estado = new EstadoStaging(
                List.of(orto(1, "A", 5, 2), orto(2, "B", 5, 2), orto(3, "C", 5, 2)), List.of());

        estado = reconciliador.alta(estado, orto(1, "A", 5, 2), 1);
        estado = reconciliador.alta(estado, orto(2, "B", 5, 2), 1);
        estado = reconciliador.alta(estado, orto(3, "C", 5, 2), 1);

        assertEquals(3, estado.getPendientes().size());
    }

    @Test
    void alta_conClaveRepetida_acumulaCantidadEnUnaSolaFila() {
        EstadoStaging estado = new EstadoStaging(List.of(orto(1, "A", 10, 2)), List.of());

        estado = reconciliador.alta(estado, orto(1, "A", 10, 2), 3);
        estado = reconciliador.alta(estado, orto(1, "A", 10, 2), 4);

        assertEquals(1, estado.getPendientes().size());
        assertEquals(7, estado.getPendientes().get(0).getCantidad());
    }

    @Test
    void alta_descuentaDeDisponibles_yEliminaLaFilaAlLlegarACero() {
        EstadoStaging conRestante = reconciliador.alta(
                new EstadoStaging(List.of(orto(1, "A", 5, 2)), List.of()), orto(1, "A", 5, 2), 2);
        assertEquals(3, buscar(conRestante.getDisponibles(), 1).getCantidad());

        EstadoStaging agotado = reconciliador.alta(
                new EstadoStaging(List.of(orto(1, "A", 5, 2)), List.of()), orto(1, "A", 5, 2), 5);
        assertTrue(agotado.getDisponibles().isEmpty(), "Al llegar a 0 la fila desaparece de disponibles");
    }

    @Test
    void baja_deMultiples_devuelveCantidadesADisponibles() {
        EstadoStaging estado = new EstadoStaging(
                List.of(orto(1, "A", 8, 2)),                      // quedaban 8 disponibles de A
                List.of(orto(1, "A", 2, 2), orto(2, "B", 3, 2))); // 2 de A y 3 de B pendientes

        estado = reconciliador.baja(estado, List.of(orto(1, "A", 2, 2), orto(2, "B", 3, 2)));

        assertTrue(estado.getPendientes().isEmpty());
        assertEquals(10, buscar(estado.getDisponibles(), 1).getCantidad()); // 8 + 2
        assertEquals(3, buscar(estado.getDisponibles(), 2).getCantidad());  // recreada
    }

    @Test
    void baja_recreaLaFilaEnDisponiblesSiNoExistia() {
        EstadoStaging estado = new EstadoStaging(List.of(), List.of(orto(9, "Z", 4, 2)));

        estado = reconciliador.baja(estado, List.of(orto(9, "Z", 4, 2)));

        assertEquals(1, estado.getDisponibles().size());
        assertEquals(4, buscar(estado.getDisponibles(), 9).getCantidad());
    }

    @Test
    void capacidadUsada_ignoraLosOtros() {
        List<MaterialLoteItem> pendientes = List.of(orto(1, "A", 3, 2), otros(2, "Caja", 10));

        // Solo la ortopedia suma: 3 × 2 = 6; los "otros" no aportan volumen.
        assertEquals(6, reconciliador.capacidadUsada(pendientes));
    }
}
