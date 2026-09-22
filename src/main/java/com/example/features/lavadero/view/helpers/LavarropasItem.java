package com.example.features.lavadero.view.helpers;

/**
 * Un lavarropas tal como lo necesita la vista de Ciclos: su número y si tiene un ciclo en curso.
 *
 * <p>Ya no lleva {@code capacidadLitros}: la columna se dropeó en la V27 y su único lector era
 * {@code LavarropasTableModel}, que no tenía un solo llamador.</p>
 */
public class LavarropasItem {
    private final int numero;
    private final boolean ocupado;
    private final Integer cicloId;

    public LavarropasItem(int numero, boolean ocupado, Integer cicloId) {
        this.numero = numero;
        this.ocupado = ocupado;
        this.cicloId = cicloId;
    }

    public int getNumero()     { return numero; }
    public boolean isOcupado() { return ocupado; }
    public Integer getCicloId() { return cicloId; }
}
