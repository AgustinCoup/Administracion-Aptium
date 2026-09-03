package com.example.features.equipos.common.controller.helpers;

import com.example.common.exception.ConflictoConcurrenciaException;
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

    /**
     * Escritura de los movimientos de un equipo; {@code false} = el service no aplicó el cambio.
     *
     * <p>Puede propagar {@link ConflictoConcurrenciaException}: la atrapa {@link #aplicarTodos} y
     * la contabiliza aparte de los fallos técnicos (un choque no es un error del operador, es que
     * otro se le adelantó).
     */
    @FunctionalInterface
    public interface Operacion {
        boolean aplicar(EquipoKey equipo, List<MovimientoMaterial> movimientos);
    }

    /**
     * Ids de equipo cuya escritura no se aplicó, separados por causa y en el orden en que se
     * intentaron: {@code idsConError} son fallos técnicos, {@code idsConConflicto} son choques de
     * concurrencia. Se distinguen porque el operador tiene que rehacer su trabajo sobre los
     * equipos que chocaron con datos frescos, no reintentar a ciegas.
     */
    public record Resultado(List<Integer> idsConError, List<Integer> idsConConflicto) {
        public Resultado {
            idsConError = List.copyOf(idsConError);
            idsConConflicto = List.copyOf(idsConConflicto);
        }

        public boolean todosExitosos() {
            return idsConError.isEmpty() && idsConConflicto.isEmpty();
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
        List<Integer> idsConConflicto = new ArrayList<>();
        for (Map.Entry<EquipoKey, List<MovimientoMaterial>> entrada : movimientosPorEquipo.entrySet()) {
            try {
                if (!operacion.aplicar(entrada.getKey(), entrada.getValue())) {
                    idsConError.add(entrada.getKey().getId());
                }
            } catch (ConflictoConcurrenciaException e) {
                idsConConflicto.add(entrada.getKey().getId());
            }
        }
        return new Resultado(idsConError, idsConConflicto);
    }
}
