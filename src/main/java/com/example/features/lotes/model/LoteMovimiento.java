package com.example.features.lotes.model;

import com.example.features.equipos.ortopedias.model.EstadoEquipo;

/**
 * Un material (o un REMITO de "otros") que se va a mover a un lote.
 *
 * <p><b>{@code estadoOrigenEsperado}</b> es el estado que la pantalla de Lotes mostraba cuando el
 * operador arrastró el ítem al autoclave. Viaja con el movimiento para que
 * {@code LoteDAO.aplicarMovimientoLote} pueda guardar la escritura contra él (CAS sobre
 * {@code equipo_materiales.estado} / {@code equipo_otros_materiales.estado} / el estado del
 * encabezado del REMITO): si la fila ya no está en ese estado, otro operador la avanzó — o la
 * lanzó en otro lote — mientras este armaba el suyo, y el movimiento se rechaza con
 * {@link com.example.common.exception.ConflictoConcurrenciaException}, que revierte el lote entero.
 *
 * <p>No hay constructor sin {@code estadoOrigenEsperado}: un movimiento sin estado esperado es el
 * bug que la guarda cierra, y dejar la puerta abierta invita a reintroducirlo.
 */
public class LoteMovimiento {
    private final int materialId;
    private final int equipoId;
    private final int cantidad;
    private final boolean esOtros;
    private final EstadoEquipo estadoOrigenEsperado;

    /** Constructor para materiales de ortopedia. */
    public LoteMovimiento(int materialId, int equipoId, int cantidad,
                          EstadoEquipo estadoOrigenEsperado) {
        this(materialId, equipoId, cantidad, false, estadoOrigenEsperado);
    }

    public LoteMovimiento(int materialId, int equipoId, int cantidad, boolean esOtros,
                          EstadoEquipo estadoOrigenEsperado) {
        this.materialId = materialId;
        this.equipoId   = equipoId;
        this.cantidad   = cantidad;
        this.esOtros    = esOtros;
        this.estadoOrigenEsperado = estadoOrigenEsperado;
    }

    public int getMaterialId() { return materialId; }
    public int getEquipoId()   { return equipoId; }
    public int getCantidad()   { return cantidad; }
    public boolean isEsOtros() { return esOtros; }

    /** Estado que la pantalla mostraba al armar el lote; la guarda de concurrencia del DAO. */
    public EstadoEquipo getEstadoOrigenEsperado() { return estadoOrigenEsperado; }
}
