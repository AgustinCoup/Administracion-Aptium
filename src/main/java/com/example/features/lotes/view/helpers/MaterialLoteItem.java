package com.example.features.lotes.view.helpers;

import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import java.io.Serializable;

public class MaterialLoteItem implements Serializable {
    private static final long serialVersionUID = 2L;

    private final int materialId;
    private final int equipoId;
    private final String descripcion;
    private int cantidad;
    private final int volumen;
    private final String clienteNombre;
    private final boolean esOtros;

    /**
     * Estado que la pantalla mostraba para este ítem, propagado al {@code LoteMovimiento} como
     * guarda de concurrencia al lanzar. {@code null} en los ítems que nunca se lanzan (los de un
     * lote ya activo, que se muestran de sólo lectura) y en los constructores legacy de test.
     */
    private final EstadoEquipo estadoOrigen;

    /** Constructor legacy — clienteNombre vacío, esOtros=false, sin estado. */
    public MaterialLoteItem(int materialId, int equipoId, String descripcion, int cantidad, int volumen) {
        this(materialId, equipoId, descripcion, cantidad, volumen, "", false, null);
    }

    /** Constructor legacy — esOtros=false, sin estado. */
    public MaterialLoteItem(int materialId, int equipoId, String descripcion,
                            int cantidad, int volumen, String clienteNombre) {
        this(materialId, equipoId, descripcion, cantidad, volumen, clienteNombre, false, null);
    }

    /** Constructor legacy — sin estado (ítems de sólo lectura de un lote ya activo). */
    public MaterialLoteItem(int materialId, int equipoId, String descripcion,
                            int cantidad, int volumen, String clienteNombre, boolean esOtros) {
        this(materialId, equipoId, descripcion, cantidad, volumen, clienteNombre, esOtros, null);
    }

    public MaterialLoteItem(int materialId, int equipoId, String descripcion,
                            int cantidad, int volumen, String clienteNombre, boolean esOtros,
                            EstadoEquipo estadoOrigen) {
        this.materialId = materialId;
        this.equipoId = equipoId;
        this.descripcion = descripcion;
        this.cantidad = cantidad;
        this.volumen = volumen;
        this.clienteNombre = clienteNombre != null ? clienteNombre : "";
        this.esOtros = esOtros;
        this.estadoOrigen = estadoOrigen;
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
    /** Estado que vio el operador; guarda de concurrencia al lanzar. Puede ser {@code null}. */
    public EstadoEquipo getEstadoOrigen() { return estadoOrigen; }
}
