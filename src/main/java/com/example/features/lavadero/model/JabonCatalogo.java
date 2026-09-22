package com.example.features.lavadero.model;

/**
 * Sin {@code equals}/{@code hashCode} a propósito: sus objetos se comparan por referencia en el
 * combo de la card. Agregarle {@code activo} no cambia eso, pero el jabón automático (Paso 7)
 * compara jabones <b>por {@link #getId()}</b>, nunca por referencia.
 */
public class JabonCatalogo {

    private final int     id;
    private final String  nombre;
    private final boolean activo;

    /** Para los caminos que sólo conocen jabones activos (findActivos, altas nuevas, joins históricos). */
    public JabonCatalogo(int id, String nombre) {
        this(id, nombre, true);
    }

    /** @param activo estado de baja lógica; lo necesita Ajustes para mostrarlo en la lista. */
    public JabonCatalogo(int id, String nombre, boolean activo) {
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
