package com.example.common.eliminacion;

import com.example.common.constants.Constantes.Mensajes;
import com.example.common.exception.BusinessException;

import java.util.List;

/**
 * La transacción de borrado encontró algo en curso que usa el ingreso, y no borró nada.
 *
 * <p><b>Extiende {@link BusinessException}, no {@code ConflictoConcurrenciaException}, a
 * propósito.</b> El conflicto dice "otro se te adelantó, mirá la versión nueva y volvé a
 * intentar"; esto dice "hay un lote o un ciclo que tenés que finalizar primero". Volver a intentar
 * sin hacer nada daría el mismo rechazo. El conflicto de la eliminación
 * ({@code CONFLICTO_ELIMINACION}) queda para "el ingreso cambió desde que lo viste" y para la
 * contención de locks.</p>
 *
 * <p>Lleva <b>todos</b> los bloqueos encontrados, no sólo el primero: si hay dos lotes en curso,
 * el operador tiene que enterarse de los dos antes de ir a finalizar uno.</p>
 */
public class EliminacionBloqueadaException extends BusinessException {

    private final transient List<Bloqueo> bloqueos;

    public EliminacionBloqueadaException(List<Bloqueo> bloqueos) {
        super(Mensajes.ELIMINACION_BLOQUEADA + "\n" + TextoBloqueos.describir(bloqueos));
        if (bloqueos.isEmpty()) {
            throw new IllegalArgumentException("Una eliminación bloqueada lleva al menos un bloqueo");
        }
        this.bloqueos = List.copyOf(bloqueos);
    }

    public List<Bloqueo> getBloqueos() {
        return bloqueos;
    }
}
