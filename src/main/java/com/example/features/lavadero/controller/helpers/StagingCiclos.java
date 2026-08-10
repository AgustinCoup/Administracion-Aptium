package com.example.features.lavadero.controller.helpers;

import com.example.features.lavadero.model.ElementoCicloItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aritmética del staging de ciclos de lavadero: qué elementos quedaron cargados
 * en cada lavarropas antes de lanzar, y cómo eso se descuenta de la lista de
 * disponibles. No depende de Swing — es el patrón del repo para lógica de negocio
 * embebida en la UI ({@code ReconciliadorPendientes}, {@code AgrupadorIngresosLote},
 * {@code SincronizadorVolumenFinal}).
 *
 * <p>A diferencia de {@code EstadoStaging} de lotes, que es inmutable y se
 * reemplaza entero en cada alta, esta clase es <b>dueña</b> del mapa
 * {@code lavarropas → pendientes} y lo muta en el lugar. La razón es que los
 * {@link ElementoCicloItem} de ciclos son objetos mutables cuya
 * {@code cantidadEnCiclo} se acumula sobre la misma fila: la tabla de cada card
 * y la de disponibles leen ese campo. Hacerlo inmutable es un cambio de
 * semántica, no de ubicación.
 *
 * <p><b>Importante:</b> {@link #aplicarSobreDisponibles(List)} <b>muta</b> los
 * ítems que recibe ({@code setCantidadEnCiclo}) y devuelve la lista filtrada.
 * De ese comportamiento dependen la tabla de disponibles y el cálculo de
 * "cuánto queda por arrastrar" del controller.
 */
public class StagingCiclos {

    private final Map<Integer, List<ElementoCicloItem>> pendientesPorLavarropas = new HashMap<>();

    /**
     * Alta de un elemento regular: si el lavarropas ya tiene una fila del mismo
     * {@code elementoClasificacionId} le acumula la cantidad; si no, agrega una
     * fila nueva copiada del origen.
     */
    public void agregarRegular(int lavarropasNumero, ElementoCicloItem origen, int cantidad) {
        List<ElementoCicloItem> pendientes = pendientesPorLavarropas
            .computeIfAbsent(lavarropasNumero, k -> new ArrayList<>());
        for (ElementoCicloItem existente : pendientes) {
            if (existente.getElementoClasificacionId() == origen.getElementoClasificacionId()
                    && !existente.isEquipo()) {
                existente.setCantidadEnCiclo(existente.getCantidadEnCiclo() + cantidad);
                return;
            }
        }
        ElementoCicloItem nuevo = new ElementoCicloItem(
            origen.getElementoClasificacionId(),
            origen.getIngresoId(),
            origen.getElementoNombre(),
            origen.getCantidadTotal(),
            origen.getCantidadYaProcesada(),
            origen.getClienteNombre(),
            origen.getCategoria()
        );
        nuevo.setCantidadEnCiclo(cantidad);
        pendientes.add(nuevo);
    }

    /**
     * Alta de una fracción de equipo: nunca se acumula: cada fracción es una fila
     * propia, identificada por su {@code instanciaId}.
     */
    public void agregarFraccionEquipo(int lavarropasNumero, ElementoCicloItem fraccion) {
        pendientesPorLavarropas
            .computeIfAbsent(lavarropasNumero, k -> new ArrayList<>())
            .add(fraccion);
    }

    /**
     * Pendientes de un lavarropas, como copia no modificable. Los ítems son los
     * mismos objetos que el staging tiene adentro (no copias defensivas): quien
     * los lee ve la {@code cantidadEnCiclo} vigente.
     */
    public List<ElementoCicloItem> pendientesDe(int lavarropasNumero) {
        return List.copyOf(pendientesPorLavarropas.getOrDefault(lavarropasNumero, List.of()));
    }

    /** Números de lavarropas con al menos un pendiente, ordenados. */
    public List<Integer> lavarropasConPendientes() {
        return pendientesPorLavarropas.entrySet().stream()
            .filter(e -> !e.getValue().isEmpty())
            .map(Map.Entry::getKey)
            .sorted()
            .toList();
    }

    /**
     * Cuántas fracciones tiene cada instancia de equipo repartida en el staging:
     * {@code instanciaId → cantidad de fracciones}. Es el denominador que la card
     * muestra en la columna {@code Fracción} ({@code 1/3}).
     */
    public Map<Integer, Integer> fraccionesPorInstancia() {
        Map<Integer, Integer> count = new HashMap<>();
        for (List<ElementoCicloItem> items : pendientesPorLavarropas.values()) {
            for (ElementoCicloItem item : items) {
                if (item.isEquipo() && item.getInstanciaId() != null) {
                    count.merge(item.getInstanciaId(), 1, Integer::sum);
                }
            }
        }
        return count;
    }

    /**
     * Descuenta lo que está en staging de la lista de disponibles que vino de la
     * BD: setea {@code cantidadEnCiclo} en cada ítem staged y saca de la lista los
     * que quedaron agotados. Los equipos cuentan por cantidad de instancias
     * repartidas; los regulares, por suma de cantidades.
     *
     * <p>Muta los ítems recibidos (ver nota de clase). Un id staged que ya no
     * figura en la BD se ignora.
     */
    public List<ElementoCicloItem> aplicarSobreDisponibles(List<ElementoCicloItem> dbDisponibles) {
        Map<Integer, ElementoCicloItem> mapa = new LinkedHashMap<>();
        for (ElementoCicloItem item : dbDisponibles) {
            mapa.put(item.getElementoClasificacionId(), item);
        }

        Map<Integer, Integer>      regularStaged    = new HashMap<>();
        Map<Integer, Set<Integer>> equipoInstancias = new HashMap<>();

        for (List<ElementoCicloItem> pendientes : pendientesPorLavarropas.values()) {
            for (ElementoCicloItem p : pendientes) {
                int id = p.getElementoClasificacionId();
                if (p.isEquipo() && p.getInstanciaId() != null) {
                    equipoInstancias.computeIfAbsent(id, k -> new HashSet<>()).add(p.getInstanciaId());
                } else {
                    regularStaged.merge(id, p.getCantidadEnCiclo(), Integer::sum);
                }
            }
        }

        regularStaged.forEach((id, staged) -> {
            ElementoCicloItem d = mapa.get(id);
            if (d == null) return;
            d.setCantidadEnCiclo(staged);
            if (staged >= d.getCantidadDisponible()) mapa.remove(id);
        });

        equipoInstancias.forEach((id, instancias) -> {
            int staged = instancias.size();
            ElementoCicloItem d = mapa.get(id);
            if (d == null) return;
            d.setCantidadEnCiclo(staged);
            if (staged >= d.getCantidadDisponible()) mapa.remove(id);
        });

        return new ArrayList<>(mapa.values());
    }

    /** {@code true} si algún lavarropas tiene elementos cargados sin lanzar. */
    public boolean hayPendientes() {
        return pendientesPorLavarropas.values().stream().anyMatch(l -> !l.isEmpty());
    }

    /** Vacía el staging completo (descartar todos). */
    public void limpiar() {
        pendientesPorLavarropas.clear();
    }

    /** Vacía el staging de un solo lavarropas (su ciclo se lanzó). */
    public void limpiarLavarropas(int lavarropasNumero) {
        pendientesPorLavarropas.remove(lavarropasNumero);
    }
}
