package com.example.features.lavadero.view.helpers;

public class LavarropasItem {
    private final int numero;
    private final int capacidadLitros;
    private final boolean ocupado;
    private final Integer cicloId;

    public LavarropasItem(int numero, int capacidadLitros, boolean ocupado, Integer cicloId) {
        this.numero = numero;
        this.capacidadLitros = capacidadLitros;
        this.ocupado = ocupado;
        this.cicloId = cicloId;
    }

    public int getNumero()          { return numero; }
    public int getCapacidadLitros() { return capacidadLitros; }
    public boolean isOcupado()      { return ocupado; }
    public Integer getCicloId()     { return cicloId; }
}
