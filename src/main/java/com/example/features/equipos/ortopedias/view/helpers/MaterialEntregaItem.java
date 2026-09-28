package com.example.features.equipos.ortopedias.view.helpers;

import com.example.common.model.FilaAEntregar;
import com.example.common.model.RemitoAEntregar;
import java.util.List;

/**
 * Una fila de la tabla de Para Entregar, y <b>qué</b> se entrega si se la elige.
 *
 * <p>Lleva exactamente una de las dos listas no vacía:
 * <ul>
 *   <li>{@code filas}: todas las filas {@code ESTERILIZADO} del grupo, cada una con su cantidad.
 *       Pueden ser varias del mismo código si vienen de lotes distintos; es lo que compara la
 *       guarda de la entrega, así que se entrega la fila entera, tal como se vio;</li>
 *   <li>{@code remitos}: un REMITO sin filas reales (datos viejos).</li>
 * </ul>
 *
 * @param ingreso  texto de la columna Ingreso (paciente/fecha o ID de remito, y la marca de incompleto)
 * @param cantidad la suma de las filas, o la cantidad del remito sin filas
 */
public record MaterialEntregaItem(String ingreso, String material, int cantidad,
                                  List<FilaAEntregar> filas, List<RemitoAEntregar> remitos) {

    public MaterialEntregaItem {
        filas = List.copyOf(filas);
        remitos = List.copyOf(remitos);
        if (filas.isEmpty() == remitos.isEmpty()) {
            throw new IllegalArgumentException(
                "Un ítem de entrega lleva filas o un remito, no ambos ni ninguno: " + material);
        }
    }
}
