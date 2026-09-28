package com.example.features.equipos.common.controller.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.common.exception.ConflictoConcurrenciaException;
import com.example.common.exception.DatabaseException;
import com.example.common.exception.ValidationException;
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

class AplicadorPorPartesTest {

    private final EquipoKey ortopedia1 = new EquipoKey(TipoEquipo.ORTOPEDIA, 1);
    private final EquipoKey otros7      = new EquipoKey(TipoEquipo.OTROS, 7);
    private final EquipoKey ortopedia3  = new EquipoKey(TipoEquipo.ORTOPEDIA, 3);

    @Test
    @DisplayName("un buffer vacío es todos exitosos y no llama a la operación")
    void bufferVacio() {
        List<EquipoKey> llamadas = new ArrayList<>();

        var resultado = AplicadorPorPartes.<EquipoKey, List<MovimientoMaterial>>aplicarTodos(Map.of(), (k, m) -> {
            llamadas.add(k);
            return true;
        });

        assertTrue(resultado.todosExitosos());
        assertFalse(resultado.algunaExitosa());
        assertTrue(resultado.conError().isEmpty());
        assertTrue(llamadas.isEmpty());
    }

    @Test
    @DisplayName("con todas las operaciones OK no hay partes con error")
    void todasExitosas() {
        var resultado = AplicadorPorPartes.aplicarTodos(buffer(), (k, m) -> true);

        assertTrue(resultado.todosExitosos());
        assertTrue(resultado.algunaExitosa());
        assertTrue(resultado.conError().isEmpty());
    }

    @Test
    @DisplayName("un loop mixto acumula solo las partes que fallaron, en orden de intento")
    void loopMixto() {
        var resultado = AplicadorPorPartes.aplicarTodos(
            buffer(), (k, m) -> k.getTipo() != TipoEquipo.OTROS);

        assertFalse(resultado.todosExitosos());
        assertEquals(List.of(otros7), resultado.conError());
    }

    @Test
    @DisplayName("no corta ante el primer fallo: intenta todas las partes")
    void noCortaAnteElPrimerFallo() {
        List<EquipoKey> intentados = new ArrayList<>();

        var resultado = AplicadorPorPartes.aplicarTodos(buffer(), (k, m) -> {
            intentados.add(k);
            return false;
        });

        assertEquals(List.of(ortopedia1, otros7, ortopedia3), intentados);
        assertEquals(List.of(ortopedia1, otros7, ortopedia3), resultado.conError());
        assertFalse(resultado.algunaExitosa());
    }

    @Test
    @DisplayName("un choque de concurrencia se contabiliza aparte del error técnico y no corta el loop")
    void choqueDeConcurrenciaSeSeparaDelError() {
        List<EquipoKey> intentados = new ArrayList<>();

        var resultado = AplicadorPorPartes.aplicarTodos(buffer(), (k, m) -> {
            intentados.add(k);
            if (k.equals(otros7)) throw new ConflictoConcurrenciaException("otro se adelantó");
            if (k.equals(ortopedia3)) return false;   // fallo técnico
            return true;
        });

        assertEquals(List.of(ortopedia1, otros7, ortopedia3), intentados, "no corta ante el choque del medio");
        assertEquals(List.of(ortopedia3), resultado.conError());
        assertEquals(List.of(otros7), resultado.conConflicto());
        assertEquals(List.of(ortopedia1), resultado.exitosas());
        assertFalse(resultado.todosExitosos());
    }

    @Test
    @DisplayName("cada parte recibe sus propios datos")
    void pasaLosDatosDeCadaParte() {
        Map<EquipoKey, List<MovimientoMaterial>> recibidos = new LinkedHashMap<>();

        AplicadorPorPartes.aplicarTodos(buffer(), (k, m) -> {
            recibidos.put(k, m);
            return true;
        });

        assertEquals(1, recibidos.get(ortopedia1).size());
        assertEquals(2, recibidos.get(otros7).size());
        assertEquals(10, recibidos.get(ortopedia1).get(0).getMaterialId());
    }

    @Test
    @DisplayName("una DatabaseException en una parte es error de esa parte y el loop sigue con las demás")
    void databaseExceptionEnUnaParte_seCuentaComoErrorYSigueConLasDemas() {
        List<EquipoKey> intentados = new ArrayList<>();

        var resultado = AplicadorPorPartes.aplicarTodos(buffer(), (k, m) -> {
            intentados.add(k);
            if (k.equals(otros7)) throw new DatabaseException("se cayó la conexión");
            return true;
        });

        assertEquals(List.of(ortopedia1, otros7, ortopedia3), intentados);
        assertEquals(List.of(otros7), resultado.conError());
        assertTrue(resultado.conConflicto().isEmpty());
        assertEquals(List.of(ortopedia1, ortopedia3), resultado.exitosas(),
            "las que ya commitearon se informan, no quedan ocultas detrás del fallo");
    }

    @Test
    @DisplayName("cualquier otra RuntimeException propaga: es un bug, no una parte que falló")
    void otraRuntimeException_propaga() {
        assertThrows(NullPointerException.class, () -> AplicadorPorPartes.aplicarTodos(buffer(), (k, m) -> {
            if (k.equals(otros7)) throw new NullPointerException();
            return true;
        }));
        assertThrows(ValidationException.class, () -> AplicadorPorPartes.aplicarTodos(buffer(), (k, m) -> {
            if (k.equals(otros7)) throw new ValidationException("dato inválido");
            return true;
        }));
    }

    @Test
    @DisplayName("las exitosas quedan en el orden en que se intentaron")
    void exitosas_enElOrdenEnQueSeIntentaron() {
        Map<EquipoKey, List<MovimientoMaterial>> invertido = new LinkedHashMap<>();
        invertido.put(ortopedia3, List.of(mov(30)));
        invertido.put(otros7, List.of(mov(20)));
        invertido.put(ortopedia1, List.of(mov(10)));

        var resultado = AplicadorPorPartes.aplicarTodos(invertido, (k, m) -> true);

        assertEquals(List.of(ortopedia3, otros7, ortopedia1), resultado.exitosas());
    }

    private Map<EquipoKey, List<MovimientoMaterial>> buffer() {
        Map<EquipoKey, List<MovimientoMaterial>> buffer = new LinkedHashMap<>();
        buffer.put(ortopedia1, List.of(mov(10)));
        buffer.put(otros7, List.of(mov(20), mov(21)));
        buffer.put(ortopedia3, List.of(mov(30)));
        return buffer;
    }

    private static MovimientoMaterial mov(int materialId) {
        return new MovimientoMaterial(materialId, 1, EstadoEquipo.NUEVO, EstadoEquipo.LAVANDO);
    }
}
