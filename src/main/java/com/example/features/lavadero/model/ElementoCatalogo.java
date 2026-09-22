package com.example.features.lavadero.model;

public class ElementoCatalogo {

    private final int     id;
    private final String  nombre;
    private final boolean activo;

    /** Para los caminos que sólo conocen elementos activos (findActivos, altas nuevas). */
    public ElementoCatalogo(int id, String nombre) {
        this(id, nombre, true);
    }

    /** @param activo estado de baja lógica; lo necesita Ajustes para mostrarlo en la lista. */
    public ElementoCatalogo(int id, String nombre, boolean activo) {
        this.id     = id;
        this.nombre = nombre;
        this.activo = activo;
    }

    public int     getId()     { return id; }
    public String  getNombre() { return nombre; }
    public boolean isActivo()  { return activo; }

    @Override
    public String toString() { return nombre; }
}
