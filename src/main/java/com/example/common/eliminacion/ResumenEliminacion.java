package com.example.common.eliminacion;

import java.util.List;

/**
 * Qué se va a borrar, qué lo impide y con qué token se guarda el borrado: lo que el diálogo de
 * confirmación le muestra al operador.
 *
 * <p><b>Se lee de la base, no de la fila de la grilla</b>, antes de abrir el diálogo. La
 * confirmación tiene que listar todo lo que desaparece (en Lavadero, incluidos ingresos del CDE
 * que la grilla no muestra), y un bloqueo se avisa antes de pedir la password.</p>
 *
 * <p><b>Es informativo.</b> La transacción de borrado re-verifica todo con sus propios locks; lo
 * único que viaja del resumen a la transacción es el token de guarda (la {@code version} en el
 * CDE, el estado y los derivados en Lavadero), para que lo que se borre sea lo que el operador
 * confirmó.</p>
 */
public sealed interface ResumenEliminacion permits ResumenEquipo, ResumenIngresoLavadero {

    IngresoAEliminar ingreso();

    /** Vacío si se puede eliminar. */
    List<Bloqueo> bloqueos();

    default boolean estaBloqueado() {
        return !bloqueos().isEmpty();
    }
}
