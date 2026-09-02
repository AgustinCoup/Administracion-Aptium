package com.example.features.lavadero.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Fila maestra de la pantalla de Historial: un ingreso de lavadero con lo que se muestra en la
 * tabla y lo que hace falta para filtrarlo. Sólo lectura — el historial no muta nada.
 *
 * <p>{@link #elementos()} y {@link #lavarropas()} <b>no se muestran</b> en ninguna columna: son
 * agregados para que la estrategia de filtrado resuelva en memoria los filtros de elemento y de
 * lavarropas, como hace toda la app sobre su snapshot. Salen de la misma consulta maestra, así
 * que no cuestan un viaje extra a la base.</p>
 *
 * <p>Los dos conjuntos vienen vacíos cuando el ingreso todavía no llegó a esa etapa: un ingreso
 * {@code PENDIENTE} no tiene elementos clasificados, y uno {@code CLASIFICADO} sin lanzar no
 * tiene lavarropas.</p>
 */
public record IngresoHistorial(
        int id,
        String clienteNombre,
        LocalDateTime fechaIngreso,
        BigDecimal pesoTotalKg,
        int cantBolsas,
        EstadoIngresoLavadero estado,
        Set<String> elementos,
        Set<Integer> lavarropas) {

    public IngresoHistorial {
        elementos  = Set.copyOf(elementos);
        lavarropas = Set.copyOf(lavarropas);
    }
}
