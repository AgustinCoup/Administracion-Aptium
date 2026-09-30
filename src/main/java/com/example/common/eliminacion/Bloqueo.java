package com.example.common.eliminacion;

import java.util.List;
import java.util.Objects;

/**
 * Por qué un ingreso <b>no se puede eliminar todavía</b>: algo en curso lo está usando.
 *
 * <p>No es un conflicto de concurrencia. Un lote o un ciclo en curso no es "otro se te adelantó",
 * es un estado que el operador tiene que resolver (finalizar ese lote o ese ciclo) antes de volver
 * a intentar. Por eso viaja en {@link EliminacionBloqueadaException}, que es una
 * {@code BusinessException} y no una {@code ConflictoConcurrenciaException}.</p>
 *
 * <p><b>Los mismos bloqueos se calculan dos veces</b>: en el resumen previo (para avisar antes de
 * pedir la password) y dentro de la transacción del borrado (que es la que manda). Los dos lados
 * producen estos mismos tipos, y el texto sale de un solo lugar ({@link TextoBloqueos}): el
 * operador lee lo mismo si el bloqueo se vio antes o si apareció por una carrera.</p>
 *
 * <p>El tipo es cerrado desde el principio: los dos últimos casos son de Lavadero, pero se declaran
 * acá para que {@link TextoBloqueos} y quien los consuma no tengan que cambiar al sumarlos.</p>
 *
 * <p>No confundir con {@code BloqueoEquipoOtros}, que son los <em>locks</em> de base que toma una
 * transacción de borrado, no una razón para rechazarlo.</p>
 */
public sealed interface Bloqueo {

    /** Un material del ingreso está en un lote con {@code fecha_fin IS NULL}. */
    record LoteEnCurso(String loteIdNegocio) implements Bloqueo {
        public LoteEnCurso {
            Objects.requireNonNull(loteIdNegocio, "loteIdNegocio");
        }
    }

    /** Ropa del ingreso de Lavadero está en un ciclo sin finalizar de ese lavarropas. */
    record CicloEnCurso(int lavarropasNumero) implements Bloqueo {}

    /**
     * El {@code equipo_otros} que creó la derivación también tiene ropa de otros ingresos de
     * Lavadero: borrarlo se llevaría ropa ajena, y no hay forma de descontar sólo la parte propia.
     */
    record DerivadoCompartido(int equipoOtrosId, List<Integer> otrosIngresosLavadero) implements Bloqueo {
        public DerivadoCompartido {
            otrosIngresosLavadero = List.copyOf(otrosIngresosLavadero);
        }
    }

    /** El {@code equipo_otros} que creó la derivación tiene materiales en un lote en curso. */
    record DerivadoEnLoteEnCurso(int equipoOtrosId, String loteIdNegocio) implements Bloqueo {
        public DerivadoEnLoteEnCurso {
            Objects.requireNonNull(loteIdNegocio, "loteIdNegocio");
        }
    }
}
