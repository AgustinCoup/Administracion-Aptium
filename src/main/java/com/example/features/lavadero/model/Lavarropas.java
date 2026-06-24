package com.example.features.lavadero.model;

public class Lavarropas {

    private final int numero;
    private final int capacidadLitros;

    public Lavarropas(int numero, int capacidadLitros) {
        this.numero = numero;
        this.capacidadLitros = capacidadLitros;
    }

    public int getNumero()          { return numero; }
    public int getCapacidadLitros() { return capacidadLitros; }

    @Override
    public String toString() {
        return "Lavarropas #" + numero;
    }
}
