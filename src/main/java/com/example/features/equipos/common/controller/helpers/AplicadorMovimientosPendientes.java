package com.example.features.equipos.common.controller.helpers;

import com.example.common.model.EquipoKey;
import com.example.features.equipos.ortopedias.model.MovimientoMaterial;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Aplica los movimientos de estado pendientes de varios equipos y acumula qué
 * equipos fallaron.
 *
 * <p>Es la parte del "confirmar cambios" de {@code RegistrarEstadoController} que
 * no habla con Swing: el loop sobre el buffer y la separación éxito/fallo. Vive
 * aparte para poder testear esa acumulación en aislamiento, sin EDT ni base.
 * El despacho al service correcto y el armado de mensajes quedan en el controller.
 */
public final class AplicadorMovimientosPendientes {

    /** Escritura de los movimientos de un equipo; {@code false} = el service no aplicó el cambio. */
    @FunctionalInterface
    public interface Operacion {
        boolean aplicar(EquipoKey equipo, List<MovimientoMaterial> movimientos);
    }

    /** Ids de equipo cuya escritura no se aplicó, en el orden en que se intentaron. */
    public record Resultado(List<Integer> idsConError) {
        public Resultado {
            idsConError = List.copyOf(idsConError);
        }

        public boolean todosExitosos() {
            return idsConError.isEmpty();
        }
    }

    private AplicadorMovimientosPendientes() {
    }

    /**
     * Corre {@code operacion} una vez por equipo. No corta ante el primer fallo:
     * los cambios que sí pasaron ya modificaron la base, así que se intentan todos.
     */
    public static Resultado aplicarTodos(Map<EquipoKey, List<MovimientoMaterial>> movimientosPorEquipo,
                                         Operacion operacion) {
        List<Integer> idsConError = new ArrayList<>();
        for (Map.Entry<EquipoKey, List<MovimientoMaterial>> entrada : movimientosPorEquipo.entrySet()) {
            if (!operacion.aplicar(entrada.getKey(), entrada.getValue())) {
                idsConError.add(entrada.getKey().getId());
            }
        }
        return new Resultado(idsConError);
    }
}
