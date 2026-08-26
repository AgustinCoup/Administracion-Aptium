package com.example.features.lavadero.model;

import java.time.LocalDateTime;

/**
 * Una tanda lavada todavía con cantidad sin marcar como Listo: o bien una fila de
 * {@code elementos_ciclo_lavadero} cuyo ciclo ya finalizó (elemento regular), o bien un
 * {@code Equipo*} repartido en varios lavarropas cuyas N fracciones terminaron todas
 * ({@link #instanciaEquipoId()} no nulo) — un equipo repartido consume una sola unidad y
 * genera un solo pendiente, no N (ver {@code plans/fracciones-de-equipo-persistidas.md}).
 *
 * <p>Exactamente uno de {@link #elementoCicloId()} / {@link #instanciaEquipoId()} es no nulo.
 * Arrastra cliente, lavarropas y fecha de fin de ciclo a propósito: el nombre del elemento no
 * identifica nada por sí solo — puede haber "Batas" de tres clientes lavadas en tres
 * lavarropas distintos el mismo día. El operador necesita esos datos para saber qué está
 * tocando cuando marca lo que terminó de doblar.</p>
 */
public record ElementoLavadoPendiente(
        Integer elementoCicloId,
        Integer instanciaEquipoId,
        String lavarropas,
        int ingresoId,
        int clienteId,
        String clienteNombre,
        String elementoNombre,
        int cantidadLavada,
        int cantidadYaLista,
        LocalDateTime fechaFinCiclo) {

    /** Cantidad todavía sin marcar como Listo. */
    public int cantidadPendiente() {
        return cantidadLavada - cantidadYaLista;
    }

    public boolean esInstanciaDeEquipo() {
        return instanciaEquipoId != null;
    }
}
