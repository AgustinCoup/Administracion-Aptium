package com.example.features.lavadero.controller.helpers;

import com.example.features.lavadero.model.ElementoCicloItem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
     * Baja de una selección devuelta a disponibles desde un lavarropas.
     *
     * <p>Cada ítem se enruta según su naturaleza: una fracción de equipo deshace la
     * subdivisión <b>entera</b> (regla de negocio: ver {@link #quitarInstanciaEquipo});
     * un regular se descuenta por su {@code cantidadEnCiclo} completa del lavarropas
     * de origen. Un ítem que ya no está en el staging es un no-op.
     */
    public void quitar(Collection<ElementoCicloItem> seleccionados, int lavarropasOrigen) {
        if (seleccionados == null) return;
        for (ElementoCicloItem item : seleccionados) {
            if (item == null) continue;
            if (esFraccionDeEquipo(item)) quitarInstanciaEquipo(item.getInstanciaId());
            else quitarRegular(lavarropasOrigen, item, item.getCantidadEnCiclo());
        }
    }

    /**
     * Descuenta {@code cantidad} unidades de la fila regular de ese lavarropas y la
     * elimina si queda sin unidades. Simétrico de {@link #agregarRegular}: identifica
     * la fila por {@code elementoClasificacionId}, no por identidad de objeto.
     */
    public void quitarRegular(int lavarropasNumero, ElementoCicloItem item, int cantidad) {
        List<ElementoCicloItem> pendientes = pendientesPorLavarropas.get(lavarropasNumero);
        if (pendientes == null) return;
        for (Iterator<ElementoCicloItem> it = pendientes.iterator(); it.hasNext(); ) {
            ElementoCicloItem existente = it.next();
            if (esFraccionDeEquipo(existente)
                    || existente.getElementoClasificacionId() != item.getElementoClasificacionId()) {
                continue;
            }
            existente.setCantidadEnCiclo(existente.getCantidadEnCiclo() - cantidad);
            if (existente.getCantidadEnCiclo() <= 0) it.remove();
            break;
        }
        descartarSiQuedoVacio(lavarropasNumero);
    }

    /**
     * Deshace una subdivisión completa: elimina <b>todas</b> las fracciones con ese
     * {@code instanciaId}, en todos los lavarropas.
     *
     * <p>Es la regla de negocio decidida para la devolución: dejar 2 de 3 fracciones
     * haría que las cards restantes mostraran {@code 1/3} (un dato falso) o exigiría
     * renumerar las fracciones en cascada. Deshacer la subdivisión entera es la única
     * semántica que no deja estados inconsistentes.
     */
    public void quitarInstanciaEquipo(Integer instanciaId) {
        if (instanciaId == null) return;
        Iterator<Map.Entry<Integer, List<ElementoCicloItem>>> it =
            pendientesPorLavarropas.entrySet().iterator();
        while (it.hasNext()) {
            List<ElementoCicloItem> pendientes = it.next().getValue();
            pendientes.removeIf(p -> esFraccionDeEquipo(p) && instanciaId.equals(p.getInstanciaId()));
            if (pendientes.isEmpty()) it.remove();
        }
    }

    /**
     * En cuántos lavarropas está repartida esa instancia de equipo. Lo consume el
     * diálogo de confirmación de la devolución, que tiene que avisar el alcance real
     * de la baja antes de aplicarla.
     */
    public int lavarropasDeInstancia(Integer instanciaId) {
        if (instanciaId == null) return 0;
        return fraccionesPorInstancia().getOrDefault(instanciaId, 0);
    }

    private void descartarSiQuedoVacio(int lavarropasNumero) {
        List<ElementoCicloItem> pendientes = pendientesPorLavarropas.get(lavarropasNumero);
        if (pendientes != null && pendientes.isEmpty()) pendientesPorLavarropas.remove(lavarropasNumero);
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

        regularStagedPorElemento().forEach((id, staged) -> descontar(mapa, id, staged));
        instanciasEquipoPorElemento().forEach((id, inst) -> descontar(mapa, id, inst.size()));

        return new ArrayList<>(mapa.values());
    }

    private static void descontar(Map<Integer, ElementoCicloItem> mapa, int id, int staged) {
        ElementoCicloItem disponible = mapa.get(id);
        if (disponible == null) return;
        disponible.setCantidadEnCiclo(staged);
        if (staged >= disponible.getCantidadDisponible()) mapa.remove(id);
    }

    /**
     * Cuántas unidades de este elemento ya están repartidas en el staging, con la
     * misma aritmética que {@link #aplicarSobreDisponibles}: los equipos cuentan por
     * instancias, los regulares por suma de cantidades.
     *
     * <p>Es la fuente de verdad para saber cuánto queda por repartir: el
     * {@code cantidadEnCiclo} del ítem arrastrado sólo se actualiza en el refresco,
     * así que dentro de una tanda de drops múltiples ya está desactualizado.
     */
    public int cantidadStaged(ElementoCicloItem item) {
        int id = item.getElementoClasificacionId();
        return item.isEquipo()
            ? instanciasEquipoPorElemento().getOrDefault(id, Set.of()).size()
            : regularStagedPorElemento().getOrDefault(id, 0);
    }

    /** {@code elementoClasificacionId → suma de cantidades staged} de los no-equipos. */
    private Map<Integer, Integer> regularStagedPorElemento() {
        Map<Integer, Integer> staged = new HashMap<>();
        for (List<ElementoCicloItem> pendientes : pendientesPorLavarropas.values()) {
            for (ElementoCicloItem p : pendientes) {
                if (!esFraccionDeEquipo(p)) {
                    staged.merge(p.getElementoClasificacionId(), p.getCantidadEnCiclo(), Integer::sum);
                }
            }
        }
        return staged;
    }

    /** {@code elementoClasificacionId → instanciaIds} de las fracciones de equipo staged. */
    private Map<Integer, Set<Integer>> instanciasEquipoPorElemento() {
        Map<Integer, Set<Integer>> instancias = new HashMap<>();
        for (List<ElementoCicloItem> pendientes : pendientesPorLavarropas.values()) {
            for (ElementoCicloItem p : pendientes) {
                if (esFraccionDeEquipo(p)) {
                    instancias.computeIfAbsent(p.getElementoClasificacionId(), k -> new HashSet<>())
                        .add(p.getInstanciaId());
                }
            }
        }
        return instancias;
    }

    /** Un equipo sin {@code instanciaId} no se repartió por subdivisión: cuenta como regular. */
    private static boolean esFraccionDeEquipo(ElementoCicloItem item) {
        return item.isEquipo() && item.getInstanciaId() != null;
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
