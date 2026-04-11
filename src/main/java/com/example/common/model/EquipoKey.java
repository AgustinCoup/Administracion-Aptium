package com.example.common.model;

import java.util.Objects;

/**
 * Clave compuesta que identifica unívocamente un equipo a través de todos los tipos.
 * <p>
 * Necesaria porque {@link EquipoRegistrableInterface.TipoEquipo#ORTOPEDIA} y
 * {@link EquipoRegistrableInterface.TipoEquipo#OTROS} tienen tablas propias con
 * auto-increment independientes, por lo que el ID numérico solo no es suficiente.
 */
public final class EquipoKey {

    private final EquipoRegistrableInterface.TipoEquipo tipo;
    private final int id;

    public EquipoKey(EquipoRegistrableInterface.TipoEquipo tipo, int id) {
        this.tipo = tipo;
        this.id   = id;
    }

    public EquipoRegistrableInterface.TipoEquipo getTipo() { return tipo; }
    public int getId()                                      { return id;   }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EquipoKey)) return false;
        EquipoKey other = (EquipoKey) o;
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
