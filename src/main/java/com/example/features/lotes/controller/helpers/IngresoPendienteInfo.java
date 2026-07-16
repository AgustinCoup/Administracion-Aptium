package com.example.features.lotes.controller.helpers;

/**
 * Fila de asignación de litros en el lanzamiento de un lote: un ingreso
 * (equipo_otros) con al menos un elemento pendiente en el autoclave.
 */
public final class IngresoPendienteInfo {

    private final int    equipoOtrosId;
    private final String clienteNombre;
    private final String etiquetaIngreso;
    private final int    cantidadTotal;

    public IngresoPendienteInfo(int equipoOtrosId, String clienteNombre,
                                String etiquetaIngreso, int cantidadTotal) {
        this.equipoOtrosId   = equipoOtrosId;
        this.clienteNombre   = clienteNombre != null ? clienteNombre : "";
        this.etiquetaIngreso = etiquetaIngreso != null ? etiquetaIngreso : "";
        this.cantidadTotal   = cantidadTotal;
    }

    public int    getEquipoOtrosId()   { return equipoOtrosId; }
    public String getClienteNombre()   { return clienteNombre; }
    public String getEtiquetaIngreso() { return etiquetaIngreso; }
    public int    getCantidadTotal()   { return cantidadTotal; }
}
