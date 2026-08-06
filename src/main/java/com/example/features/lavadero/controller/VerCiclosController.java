package com.example.features.lavadero.controller;

import com.example.common.util.AbstractFilterController;
import com.example.common.util.FilterStrategy;
import com.example.features.lavadero.controller.helpers.CicloFilterCriteria;
import com.example.features.lavadero.controller.helpers.CicloFilterStrategy;
import com.example.features.lavadero.model.CicloLavadero;
import com.example.features.lavadero.view.PantallaVerCiclos;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;
import java.util.Objects;

public class VerCiclosController extends AbstractFilterController<CicloLavadero> {

    private final PantallaVerCiclos pantalla;
    private final FilterStrategy<CicloLavadero, CicloFilterCriteria> filterStrategy = new CicloFilterStrategy();

    /** Alcance: pintar la grilla desde el refresco global. Sin I/O propia. */
    public VerCiclosController(PantallaVerCiclos pantalla, Runnable solicitarRefresco) {
        this.pantalla = pantalla;
        Objects.requireNonNull(solicitarRefresco, "solicitarRefresco");

        pantalla.setOnFiltrosChanged(this::aplicarFiltros);
        pantalla.setOnLimpiar(pantalla::limpiarFiltros);

        pantalla.addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent e) { solicitarRefresco.run(); }
        });
    }

    /** Vuelca el snapshot a la grilla. Sin I/O. */
    public void pintar(List<CicloLavadero> ciclos) {
        recargarCache(ciclos);
    }

    @Override
    protected void aplicarFiltros() {
        CicloFilterCriteria criteria = new CicloFilterCriteria(
            pantalla.getFiltroNumero(),
            pantalla.getFiltroEstados(),
            pantalla.getFiltroFechaDesde(),
            pantalla.getFiltroFechaHasta()
        );
        pantalla.actualizarCiclos(filterStrategy.filter(getCache(), criteria));
    }
}
