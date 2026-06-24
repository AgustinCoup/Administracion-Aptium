package com.example.features.lavadero.model;

public enum TipoJabon {
    JABON_LIQUIDO("Jabón Líquido"),
    JABON_EN_POLVO("Jabón en Polvo"),
    DESENGRASANTE("Desengrasante");

    private final String displayName;

    TipoJabon(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
