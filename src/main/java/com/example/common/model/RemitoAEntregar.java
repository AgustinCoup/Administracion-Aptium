package com.example.common.model;

/**
 * Un REMITO de "otros" <b>sin filas en {@code equipo_otros_materiales}</b> que el operador vio
 * listo para entregar. Un remito con filas no viaja así: sus filas van como {@link FilaAEntregar}.
 *
 * <p>Con los flujos actuales no se llega a un remito esterilizado sin filas (todo movimiento lo
 * materializa); existe por datos viejos. Ver {@code EquipoOtrosDAO.entregar}.
 */
public record RemitoAEntregar(int equipoOtrosId) {
}
