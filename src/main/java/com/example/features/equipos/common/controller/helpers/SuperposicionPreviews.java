package com.example.features.equipos.common.controller.helpers;

import com.example.common.model.EquipoKey;
import com.example.common.model.EquipoRegistrableInterface;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lo que pinta Registrar Estado: el snapshot, con las copias de preview encima.
 *
 * <p>Los previews se aplican sobre copias ({@link EquipoRegistrableInterface#copiarParaPreview()})
 * porque el snapshot es de sólo lectura y lo comparten tres pantallas. Esta clase es la otra
 * mitad: vuelve a juntar las dos cosas para pintar, sin tocar ninguna.
 *
 * <p>La clave es {@link EquipoKey} (tipo + id) y no el id: ortopedias y "otros" tienen
 * autoincrementales independientes, así que el mismo número puede ser dos equipos distintos.
 */
public final class SuperposicionPreviews {

    private SuperposicionPreviews() {}

    /**
     * Conserva el orden del snapshot y reemplaza por clave los equipos que tienen copia. Una copia
     * cuyo equipo ya no está en el snapshot (se entregó, o lo corrigieron) no se pinta: no hay
     * dónde. Su movimiento sigue en el buffer, y lo decide la guarda de estado al confirmar.
     */
    public static List<EquipoRegistrableInterface> superponer(
            List<? extends EquipoRegistrableInterface> snapshot,
            Map<EquipoKey, ? extends EquipoRegistrableInterface> copias) {
        List<EquipoRegistrableInterface> resultado = new ArrayList<>(snapshot.size());
        for (EquipoRegistrableInterface equipo : snapshot) {
            EquipoRegistrableInterface copia = copias.get(new EquipoKey(equipo.getTipo(), equipo.getId()));
            resultado.add(copia != null ? copia : equipo);
        }
        return resultado;
    }
}
