package com.example.features.lotes.view.helpers;

import java.io.Serializable;

public class MaterialLoteItem implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int materialId;
    private final int equipoId;
    private final String descripcion;
    private int cantidad;
    private final int volumen;
    private final String clienteNombre;
    private final boolean esOtros;

    /** Constructor legacy — clienteNombre vacío, esOtros=false. */
    public MaterialLoteItem(int materialId, int equipoId, String descripcion, int cantidad, int volumen) {
        this(materialId, equipoId, descripcion, cantidad, volumen, "", false);
    }

    /** Constructor legacy — esOtros=false. */
    public MaterialLoteItem(int materialId, int equipoId, String descripcion,
                            int cantidad, int volumen, String clienteNombre) {
        this(materialId, equipoId, descripcion, cantidad, volumen, clienteNombre, false);
    }

    public MaterialLoteItem(int materialId, int equipoId, String descripcion,
                            int cantidad, int volumen, String clienteNombre, boolean esOtros) {
        this.materialId = materialId;
        this.equipoId = equipoId;
        this.descripcion = descripcion;
        this.cantidad = cantidad;
        this.volumen = volumen;
        this.clienteNombre = clienteNombre != null ? clienteNombre : "";
        this.esOtros = esOtros;
    }

    public int getMaterialId()           { return materialId; }
    public int getEquipoId()             { return equipoId; }
    public String getDescripcion()       { return descripcion; }
    public int getCantidad()             { return cantidad; }
    public void setCantidad(int c)       { this.cantidad = c; }
    public int getVolumen()              { return volumen; }
    /** Solo significativo para ortopedias; el volumen de los "otros" se asigna por ingreso al lanzar. */
    public int getVolumenTotal()         { return cantidad * volumen; }
    public String getClienteNombre()     { return clienteNombre; }
    public boolean isEsOtros()           { return esOtros; }
}
