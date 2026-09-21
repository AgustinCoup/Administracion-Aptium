package com.example.features.lavadero.model;

/**
 * Insumo extra que puede llevar un ciclo de lavado (suavizante, potenciador, …), tal como está en
 * {@code catalogo_insumos}. A diferencia del jabón, no lleva cantidad: un ciclo lo lleva o no.
 *
 * <p>Es {@code record}, y {@link JabonCatalogo} no, porque la card rechaza insumos duplicados en su
 * lista y eso es comparar por valor: el {@code record} trae {@code equals}/{@code hashCode} gratis.
 * Aun así la deduplicación se hace por {@link #id()}: dos lecturas del catálogo pueden diferir en
 * {@code nombre} o {@code activo} y seguir siendo el mismo insumo.</p>
 *
 * @param id     id en {@code catalogo_insumos}
 * @param nombre nombre visible
 * @param activo existe para el plan de Ajustes, que da de baja insumos; hoy ninguna escritura lo
 *               pone en {@code false}, así que todas las lecturas lo devuelven en {@code true}
 */
public record InsumoCatalogo(int id, String nombre, boolean activo) {

    @Override
    public String toString() { return nombre; }
}
