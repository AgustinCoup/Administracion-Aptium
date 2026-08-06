package com.example.features.lavadero.controller;

import com.example.common.util.FilterStrategy;
import com.example.features.lavadero.controller.helpers.CicloFilterCriteria;
import com.example.features.lavadero.controller.helpers.CicloFilterStrategy;
import com.example.features.lavadero.model.CicloLavadero;
import com.example.features.lavadero.service.CicloLavaderoService;
import com.example.features.lavadero.view.PantallaVerCiclos;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;

public class VerCiclosController {

    private final PantallaVerCiclos pantalla;
    private final CicloLavaderoService cicloLavaderoService;
    private final FilterStrategy<CicloLavadero, CicloFilterCriteria> filterStrategy = new CicloFilterStrategy();

    private List<CicloLavadero> cache = List.of();

    public VerCiclosController(PantallaVerCiclos pantalla, CicloLavaderoService cicloLavaderoService) {
        this.pantalla = pantalla;
        this.cicloLavaderoService = cicloLavaderoService;

        pantalla.setOnFiltrosChanged(this::aplicarFiltros);
        pantalla.setOnLimpiar(pantalla::limpiarFiltros);

        cargarDatos();

        pantalla.addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent e) { cargarDatos(); }
        });
    }

    public void cargarDatos() {
        cache = cicloLavaderoService.obtenerTodosLosCiclos();
        aplicarFiltros();
    }

    private void aplicarFiltros() {
        CicloFilterCriteria criteria = new CicloFilterCriteria(
            pantalla.getFiltroNumero(),
            pantalla.getFiltroEstados(),
            pantalla.getFiltroFechaDesde(),
            pantalla.getFiltroFechaHasta()
        );
        pantalla.actualizarCiclos(filterStrategy.filter(cache, criteria));
    }
}
