package com.example.features.lotes.controller.helpers;

import com.example.features.lotes.view.helpers.MaterialLoteItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Lógica pura de reconciliación disponibles ↔ pendientes del staging de lotes.
 * No depende de Swing: recibe un {@link EstadoStaging} y devuelve uno nuevo,
 * sin mutar las listas ni los ítems de entrada (crea copias con la cantidad
 * ajustada). Es el patrón del repo para lógica de negocio embebida en la UI
 * ({@code AgrupadorIngresosLote}, {@code SincronizadorVolumenFinal}).
 */
public class ReconciliadorPendientes {

    /**
     * Clave compuesta que discrimina ortopedias ("E") de otros ("O") para evitar
     * colisiones entre IDs de tablas distintas.
     */
    public static String claveItem(MaterialLoteItem item) {
        return (item.isEsOtros() ? "O" : "E") + item.getMaterialId();
    }

    /**
     * Alta de un ítem con una cantidad: descuenta esa cantidad de disponibles
     * (elimina la fila si llega a 0) y la acumula en pendientes (suma si la clave
     * ya existe; agrega una fila nueva si no).
     */
    public EstadoStaging alta(EstadoStaging estado, MaterialLoteItem item, int cantidad) {
        return new EstadoStaging(
                descontarDisponible(estado.getDisponibles(), item, cantidad),
                acumularPendiente(estado.getPendientes(), item, cantidad));
    }

    /**
     * Baja de varios ítems: los quita de pendientes y devuelve sus cantidades a
     * disponibles (suma si la fila existe; la recrea si no).
     */
    public EstadoStaging baja(EstadoStaging estado, List<MaterialLoteItem> aQuitar) {
        List<MaterialLoteItem> disponibles = new ArrayList<>(estado.getDisponibles());
        List<MaterialLoteItem> pendientes = new ArrayList<>(estado.getPendientes());
        for (MaterialLoteItem item : aQuitar) {
            String clave = claveItem(item);
            pendientes.removeIf(p -> claveItem(p).equals(clave));
            disponibles = devolverDisponible(disponibles, item);
        }
        return new EstadoStaging(disponibles, pendientes);
    }

    /** Capacidad usada por los pendientes; los "otros" no suman (litros por ingreso). */
    public int capacidadUsada(List<MaterialLoteItem> pendientes) {
        int total = 0;
        for (MaterialLoteItem item : pendientes) {
            if (!item.isEsOtros()) total += item.getVolumenTotal();
        }
        return total;
    }

    private List<MaterialLoteItem> descontarDisponible(List<MaterialLoteItem> disponibles,
                                                       MaterialLoteItem item, int cantidad) {
        String clave = claveItem(item);
        List<MaterialLoteItem> resultado = new ArrayList<>();
        for (MaterialLoteItem disponible : disponibles) {
            if (!claveItem(disponible).equals(clave)) {
                resultado.add(disponible);
                continue;
            }
            int restante = disponible.getCantidad() - cantidad;
            if (restante > 0) resultado.add(conCantidad(disponible, restante));
            // restante <= 0 → la fila desaparece de disponibles
        }
        return resultado;
    }

    private List<MaterialLoteItem> acumularPendiente(List<MaterialLoteItem> pendientes,
                                                     MaterialLoteItem item, int cantidad) {
        String clave = claveItem(item);
        List<MaterialLoteItem> resultado = new ArrayList<>();
        boolean acumulado = false;
        for (MaterialLoteItem pendiente : pendientes) {
            if (!acumulado && claveItem(pendiente).equals(clave)) {
                resultado.add(conCantidad(pendiente, pendiente.getCantidad() + cantidad));
                acumulado = true;
            } else {
                resultado.add(pendiente);
            }
        }
        if (!acumulado) resultado.add(conCantidad(item, cantidad));
        return resultado;
    }

    private List<MaterialLoteItem> devolverDisponible(List<MaterialLoteItem> disponibles,
                                                      MaterialLoteItem item) {
        String clave = claveItem(item);
        List<MaterialLoteItem> resultado = new ArrayList<>();
        boolean devuelto = false;
        for (MaterialLoteItem disponible : disponibles) {
            if (!devuelto && claveItem(disponible).equals(clave)) {
                resultado.add(conCantidad(disponible, disponible.getCantidad() + item.getCantidad()));
                devuelto = true;
            } else {
                resultado.add(disponible);
            }
        }
        if (!devuelto) resultado.add(conCantidad(item, item.getCantidad()));
        return resultado;
    }

    /** Copia el ítem con otra cantidad, preservando el resto de sus atributos. */
    private static MaterialLoteItem conCantidad(MaterialLoteItem item, int cantidad) {
        return new MaterialLoteItem(
                item.getMaterialId(), item.getEquipoId(), item.getDescripcion(),
                cantidad, item.getVolumen(), item.getClienteNombre(), item.isEsOtros());
    }
}
