package com.example.features.lotes.controller.helpers;

import com.example.features.lotes.view.helpers.MaterialLoteItem;

import java.util.List;

/**
 * Estado inmutable del staging de un autoclave: los materiales todavía
 * disponibles y los que ya fueron cargados como pendientes de ese autoclave.
 *
 * <p>Value type sin dependencias de Swing. Las transformaciones (alta/baja) las
 * realiza {@link ReconciliadorPendientes}, que recibe un estado y devuelve uno
 * nuevo sin mutar el original.
 */
public final class EstadoStaging {

    private final List<MaterialLoteItem> disponibles;
    private final List<MaterialLoteItem> pendientes;

    public EstadoStaging(List<MaterialLoteItem> disponibles, List<MaterialLoteItem> pendientes) {
        this.disponibles = List.copyOf(disponibles);
        this.pendientes = List.copyOf(pendientes);
    }

    public List<MaterialLoteItem> getDisponibles() { return disponibles; }
    public List<MaterialLoteItem> getPendientes()  { return pendientes; }
}
