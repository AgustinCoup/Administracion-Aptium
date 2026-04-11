package com.example.common.model;

import java.util.Objects;

/**
 * Clave compuesta que identifica unívocamente un destino de entrega.
 * <p>
 * Necesaria porque instituciones y clientes tienen tablas propias con
 * auto-increment independientes, por lo que el ID numérico solo no es suficiente.
 */
public final class EntregaDestinoKey {

    public enum TipoDestino { INSTITUCION, CLIENTE }

    private final TipoDestino tipo;
    private final int id;

    public EntregaDestinoKey(TipoDestino tipo, int id) {
        this.tipo = tipo;
        this.id   = id;
    }

    public TipoDestino getTipo() { return tipo; }
    public int getId()           { return id;   }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EntregaDestinoKey)) return false;
        EntregaDestinoKey other = (EntregaDestinoKey) o;
        return id == other.id && tipo == other.tipo;
    }

    @Override
    public int hashCode() {
        return Objects.hash(tipo, id);
    }

    @Override
    public String toString() {
        return tipo + "#" + id;
    }
}
