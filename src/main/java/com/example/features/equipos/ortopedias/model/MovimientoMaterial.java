package com.example.features.equipos.ortopedias.model;

/**
 * Representa el movimiento de una subcantidad de un material a otro estado.
 * Se usa para aplicar cambios en lote dentro de una transaccion.
 *
 * <p><b>{@code estadoOrigenEsperado}</b> es el estado que la pantalla mostraba cuando el operador
 * tildó este movimiento. Viaja con el movimiento para que el DAO pueda guardar la escritura contra
 * él (CAS sobre {@code equipo_materiales.estado}): si la fila ya no está en ese estado, otro
 * operador la avanzó mientras este pensaba y el movimiento se rechaza con
 * {@link com.example.common.exception.ConflictoConcurrenciaException}.
 */
public class MovimientoMaterial {
    private final int materialId;
    private final int cantidad;
    private final EstadoEquipo estadoOrigenEsperado;
    private final EstadoEquipo estadoDestino;

    public MovimientoMaterial(int materialId, int cantidad,
                              EstadoEquipo estadoOrigenEsperado, EstadoEquipo estadoDestino) {
        this.materialId = materialId;
        this.cantidad = cantidad;
        this.estadoOrigenEsperado = estadoOrigenEsperado;
        this.estadoDestino = estadoDestino;
    }

    public int getMaterialId() {
        return materialId;
    }

    public int getCantidad() {
        return cantidad;
    }

    /** Estado que la pantalla mostraba al tildar el movimiento; la guarda de concurrencia del DAO. */
    public EstadoEquipo getEstadoOrigenEsperado() {
        return estadoOrigenEsperado;
    }

    public EstadoEquipo getEstadoDestino() {
        return estadoDestino;
    }
}
