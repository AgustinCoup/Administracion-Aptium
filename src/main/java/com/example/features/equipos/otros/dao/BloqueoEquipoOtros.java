package com.example.features.equipos.otros.dao;

import java.util.List;
import java.util.Set;

/**
 * Lo que {@link EliminadorEquipoOtros#bloquear} dejó tomado {@code FOR UPDATE}, y lo que leyó al
 * tomarlo.
 *
 * <p>No es un {@link com.example.common.eliminacion.Bloqueo} (una razón para rechazar el borrado):
 * son los <em>locks</em> de base que la transacción tiene en la mano. Todo lo que trae se leyó con
 * {@code FOR UPDATE}, así que es la última versión commiteada, no la de una vista vieja.</p>
 *
 * @param equipoOtrosId la cabecera bloqueada
 * @param version       su {@code version} actual; quien llama la compara con la vista
 * @param loteIds       los {@code lote_id} no nulos de sus materiales, para
 *                      {@link EliminadorEquipoOtros#verificar}
 * @param salidaIds     <b>todas</b> las {@code salidas_lavadero} que apuntan a este equipo, de
 *                      cualquier ingreso de Lavadero. El borrado de Lavadero las usa para detectar
 *                      un derivado compartido: si sobran salidas que no son suyas, es de otro.
 */
public record BloqueoEquipoOtros(int equipoOtrosId, int version, Set<Integer> loteIds, List<Integer> salidaIds) {

    public BloqueoEquipoOtros {
        loteIds = Set.copyOf(loteIds);
        salidaIds = List.copyOf(salidaIds);
    }
}
