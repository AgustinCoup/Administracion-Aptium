package com.example.common.model;

import java.util.Comparator;

/**
 * Una fila de material que el operador vio lista para entregar, tal como la vio.
 *
 * <p>Viaja de la pantalla a la escritura para que ésta entregue <b>exactamente</b> lo que se
 * confirmó y nada más: la escritura nunca relee "lo que haya esterilizado". {@code cantidadVista}
 * es parte de la guarda de concurrencia (ver {@code MaterialDAO.entregarMateriales}).
 *
 * <p>Sirve a los dos tipos de equipo: en ortopedias {@code equipoId} es {@code equipos.id}; en
 * otros, {@code equipo_otros.id}.
 */
public record FilaAEntregar(int equipoId, int materialId, int cantidadVista) {

    /**
     * El orden en que se escriben las filas de una entrega. Dos entregas concurrentes con filas en
     * común bloquean en el mismo orden y no se cruzan en un deadlock — que H2 no reproduce.
     */
    public static final Comparator<FilaAEntregar> ORDEN_DE_ESCRITURA =
        Comparator.comparingInt(FilaAEntregar::equipoId).thenComparingInt(FilaAEntregar::materialId);

    public FilaAEntregar {
        if (cantidadVista <= 0) {
            throw new IllegalArgumentException(
                "La cantidad vista debe ser mayor a cero (material " + materialId + ")");
        }
    }
}
