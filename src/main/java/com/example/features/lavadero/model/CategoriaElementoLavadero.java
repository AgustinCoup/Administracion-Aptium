package com.example.features.lavadero.model;

/**
 * Categoría de un elemento del catálogo de lavadero.
 *
 * <p>Se persiste con {@link #name()} en la columna {@code catalogo_elementos_lavadero.categoria}
 * (ver migraciones {@code V9}/{@code V11}). Los elementos {@link #EQUIPO} siguen en Ciclos el
 * flujo de fracciones/subdivisión; los {@link #REGULAR} no.
 */
public enum CategoriaElementoLavadero {

    REGULAR("Regular"),
    EQUIPO("Equipo");

    private final String etiqueta;

    CategoriaElementoLavadero(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
