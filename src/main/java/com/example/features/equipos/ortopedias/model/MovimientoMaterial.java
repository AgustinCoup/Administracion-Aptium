package com.example.features.equipos.ortopedias.model;

/**
 * Representa el movimiento de una subcantidad de un material a otro estado.
 * Se usa para aplicar cambios en lote dentro de una transaccion.
 */
public class MovimientoMaterial {
    private final int materialId;
    private final int cantidad;
    private final EstadoEquipo estadoDestino;

    public MovimientoMaterial(int materialId, int cantidad, EstadoEquipo estadoDestino) {
        this.materialId = materialId;
        this.cantidad = cantidad;
        this.estadoDestino = estadoDestino;
    }

    public int getMaterialId() {
        return materialId;
    }

    public int getCantidad() {
        return cantidad;
    }

    public EstadoEquipo getEstadoDestino() {
        return estadoDestino;
    }
}


