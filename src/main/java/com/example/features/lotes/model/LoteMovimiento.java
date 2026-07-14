package com.example.features.lotes.model;

public class LoteMovimiento {
    private final int materialId;
    private final int equipoId;
    private final int cantidad;
    private final boolean esOtros;

    /** Constructor para materiales de ortopedia. */
    public LoteMovimiento(int materialId, int equipoId, int cantidad) {
        this(materialId, equipoId, cantidad, false);
    }

    public LoteMovimiento(int materialId, int equipoId, int cantidad, boolean esOtros) {
        this.materialId = materialId;
        this.equipoId   = equipoId;
        this.cantidad   = cantidad;
        this.esOtros    = esOtros;
    }

    public int getMaterialId() { return materialId; }
    public int getEquipoId()   { return equipoId; }
    public int getCantidad()   { return cantidad; }
    public boolean isEsOtros() { return esOtros; }
}
