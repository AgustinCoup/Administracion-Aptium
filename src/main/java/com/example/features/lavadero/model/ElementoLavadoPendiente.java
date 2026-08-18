package com.example.features.lavadero.model;

import java.time.LocalDateTime;

/**
 * Una tanda lavada (fila de {@code elementos_ciclo_lavadero} cuyo ciclo ya finalizó) que
 * todavía tiene cantidad sin marcar como Listo.
 *
 * <p>Arrastra cliente, lavarropas y fecha de fin de ciclo a propósito: el nombre del elemento
 * no identifica nada por sí solo — puede haber "Batas" de tres clientes lavadas en tres
 * lavarropas distintos el mismo día. El operador necesita esos tres datos para saber qué está
 * tocando cuando marca lo que terminó de doblar.</p>
 */
public record ElementoLavadoPendiente(
        int elementoCicloId,
        int cicloId,
        int lavarropasNumero,
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
}
