package com.example.features.lavadero.model;

import java.time.LocalDateTime;

/**
 * Una línea de trazabilidad del detalle de un ingreso: qué elemento, cuántos, en qué
 * lavarropas se lavó, cuándo quedó lavado, cuándo quedó listo y a dónde fue.
 *
 * <p>Cada campo nulo significa <b>"todavía no llegó a esa etapa"</b>, nunca "falta el dato":</p>
 * <ul>
 *   <li>{@code lavarropas == null} — clasificado y todavía sin lanzar a ningún ciclo.</li>
 *   <li>{@code fechaLavado == null} — el ciclo sigue activo (o, en un equipo repartido, alguna
 *       de sus fracciones sigue en un ciclo activo: no está lavado hasta que terminan todas).</li>
 *   <li>{@code fechaListo == null} — lavado, todavía sin secar/doblar.</li>
 *   <li>{@code destino == null} — listo, sin destino asignado. Es un estado legítimo
 *       (ver {@link DestinoSalida}).</li>
 * </ul>
 *
 * <p>{@code lavarropas} es texto y no un número porque un {@code Equipo*} repartido ocupa
 * varios a la vez ({@code "3, 5"}); en ese caso {@link #totalPartes()} dice en cuántas partes
 * se repartió y {@link #cantidad()} vale 1 — un equipo repartido es un equipo, no N. Para una
 * línea regular {@code totalPartes} es {@code null}.</p>
 */
public record LineaHistorial(
        String elementoNombre,
        int cantidad,
        String lavarropas,
        Integer totalPartes,
        LocalDateTime fechaLavado,
        LocalDateTime fechaListo,
        DestinoSalida destino) {

    public boolean esFraccionDeEquipo() {
        return totalPartes != null;
    }
}
