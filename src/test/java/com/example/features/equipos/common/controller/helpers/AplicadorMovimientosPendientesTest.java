package com.example.features.equipos.common.controller.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.common.model.EquipoKey;
import com.example.common.model.EquipoRegistrableInterface.TipoEquipo;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.model.MovimientoMaterial;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AplicadorMovimientosPendientesTest {

    private final EquipoKey ortopedia1 = new EquipoKey(TipoEquipo.ORTOPEDIA, 1);
    private final EquipoKey otros7      = new EquipoKey(TipoEquipo.OTROS, 7);
    private final EquipoKey ortopedia3  = new EquipoKey(TipoEquipo.ORTOPEDIA, 3);

    @Test
    @DisplayName("un buffer vacío es todos exitosos y no llama a la operación")
    void bufferVacio() {
        List<EquipoKey> llamadas = new ArrayList<>();

        var resultado = AplicadorMovimientosPendientes.aplicarTodos(Map.of(), (k, m) -> {
            llamadas.add(k);
            return true;
        });

        assertTrue(resultado.todosExitosos());
        assertTrue(resultado.idsConError().isEmpty());
        assertTrue(llamadas.isEmpty());
    }

    @Test
    @DisplayName("con todas las operaciones OK no hay ids con error")
    void todasExitosas() {
        var resultado = AplicadorMovimientosPendientes.aplicarTodos(buffer(), (k, m) -> true);

        assertTrue(resultado.todosExitosos());
        assertTrue(resultado.idsConError().isEmpty());
    }

    @Test
    @DisplayName("un loop mixto acumula solo los ids que fallaron, en orden de intento")
    void loopMixto() {
        var resultado = AplicadorMovimientosPendientes.aplicarTodos(
            buffer(), (k, m) -> k.getTipo() != TipoEquipo.OTROS);

        assertFalse(resultado.todosExitosos());
        assertEquals(List.of(7), resultado.idsConError());
    }

    @Test
    @DisplayName("no corta ante el primer fallo: intenta todos los equipos")
    void noCortaAnteElPrimerFallo() {
        List<Integer> intentados = new ArrayList<>();

        var resultado = AplicadorMovimientosPendientes.aplicarTodos(buffer(), (k, m) -> {
            intentados.add(k.getId());
            return false;
        });

        assertEquals(List.of(1, 7, 3), intentados);
        assertEquals(List.of(1, 7, 3), resultado.idsConError());
    }

    @Test
    @DisplayName("cada equipo recibe sus propios movimientos")
    void pasaLosMovimientosDeCadaEquipo() {
        Map<EquipoKey, List<MovimientoMaterial>> recibidos = new LinkedHashMap<>();

        AplicadorMovimientosPendientes.aplicarTodos(buffer(), (k, m) -> {
            recibidos.put(k, m);
            return true;
        });

        assertEquals(1, recibidos.get(ortopedia1).size());
        assertEquals(2, recibidos.get(otros7).size());
        assertEquals(10, recibidos.get(ortopedia1).get(0).getMaterialId());
    }

    private Map<EquipoKey, List<MovimientoMaterial>> buffer() {
        Map<EquipoKey, List<MovimientoMaterial>> buffer = new LinkedHashMap<>();
        buffer.put(ortopedia1, List.of(mov(10)));
        buffer.put(otros7, List.of(mov(20), mov(21)));
        buffer.put(ortopedia3, List.of(mov(30)));
        return buffer;
    }

    private static MovimientoMaterial mov(int materialId) {
        return new MovimientoMaterial(materialId, 1, EstadoEquipo.LAVANDO);
    }
}
