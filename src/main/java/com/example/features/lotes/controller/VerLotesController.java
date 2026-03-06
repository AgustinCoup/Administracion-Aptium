package com.example.features.lotes.controller;

import com.example.app.AppModel;
import com.example.common.util.AbstractFilterController;
import com.example.common.util.FilterStrategy;
import com.example.features.autoclaves.model.Autoclave;
import com.example.features.lotes.controller.helpers.LotesFilterCriteria;
import com.example.features.lotes.controller.helpers.LotesFilterStrategy;
import com.example.features.lotes.model.Lote;
import com.example.features.lotes.view.PantallaVerLotes;

import java.util.List;
import java.util.stream.Collectors;

public class VerLotesController extends AbstractFilterController<Lote> {

    private final PantallaVerLotes panel;
    private final AppModel model;
    private final FilterStrategy<Lote, LotesFilterCriteria> filterStrategy;

    public VerLotesController(PantallaVerLotes panel, AppModel model) {
        this.panel = panel;
        this.model = model;
        this.filterStrategy = new LotesFilterStrategy();

        this.panel.setOnFiltrosChanged(this::aplicarFiltros);
        cargarDatos();
    }

    public void cargarDatos() {
        List<String> autoclaves = model.obtenerAutoclaves().stream()
            .map(Autoclave::getNombre)
            .sorted(String::compareToIgnoreCase)
            .collect(Collectors.toList());
        panel.setEquiposFiltro(autoclaves);

        recargarCache(model.obtenerTodosLosLotes());
    }

    @Override
    protected void aplicarFiltros() {
        LotesFilterCriteria criteria = new LotesFilterCriteria(
            panel.getFiltroId(),
            panel.getFiltroAutoclaves(),   // antes: getFiltroEquipo() → String
            panel.getFiltroEstados(),      // antes: getFiltroEstado() → String
            panel.getFiltroFechaDesde(),   // antes: getFiltroFechaInicio() → String
            panel.getFiltroFechaHasta()    // nuevo
        );

        List<Lote> filtrados = filterStrategy.filter(getCache(), criteria);
        panel.actualizarLotes(filtrados);
    }
}