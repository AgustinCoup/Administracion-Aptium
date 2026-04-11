package com.example.features.lotes.model;

public class LoteMovimiento {
    private final int materialId;
    private final int equipoId;
    private final int cantidad;
    private final boolean esOtros;
    /** Litros declarados por el usuario para este grupo (solo para esOtros=true). */
    private final Integer volumenOtros;

    /** Constructor para materiales de ortopedia. */
    public LoteMovimiento(int materialId, int equipoId, int cantidad) {
        this(materialId, equipoId, cantidad, false, null);
    }

    /** Constructor para ortopedia/otros sin volumen personalizado. */
    public LoteMovimiento(int materialId, int equipoId, int cantidad, boolean esOtros) {
        this(materialId, equipoId, cantidad, esOtros, null);
    }

    public LoteMovimiento(int materialId, int equipoId, int cantidad, boolean esOtros, Integer volumenOtros) {
        this.materialId   = materialId;
        this.equipoId     = equipoId;
        this.cantidad     = cantidad;
        this.esOtros      = esOtros;
        this.volumenOtros = volumenOtros;
    }

    public int getMaterialId()      { return materialId; }
    public int getEquipoId()        { return equipoId; }
    public int getCantidad()        { return cantidad; }
    public boolean isEsOtros()      { return esOtros; }
    public Integer getVolumenOtros(){ return volumenOtros; }
}


