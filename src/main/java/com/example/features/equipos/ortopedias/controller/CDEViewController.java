package com.example.features.equipos.ortopedias.controller;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;

import com.example.common.model.EquipoRegistrableInterface;
import com.example.common.util.AbstractFilterController;
import com.example.common.util.FilterStrategy;
import com.example.features.equipos.ortopedias.controller.helpers.CdeFilterCriteria;
import com.example.features.equipos.ortopedias.controller.helpers.CdeFilterStrategy;
import com.example.features.equipos.ortopedias.service.EquipoService;
import com.example.features.equipos.ortopedias.view.PantallaVerCDEv2;
import com.example.features.equipos.otros.service.EquipoOtrosService;

/**
 * Controlador para {@link PantallaVerCDEv2}.
 *
 * Carga equipos de ortopedia Y equipos "otros" para mostrarlos en una
 * única tabla. El filtro por institución muestra cadena vacía para "otros",
 * lo que los hace aparecer cuando el campo institución está en blanco.
 */
public class CDEViewController extends AbstractFilterController<EquipoRegistrableInterface> {

    private final PantallaVerCDEv2     panel;
    private final EquipoService        equipoService;
    private final EquipoOtrosService   equipoOtrosService;
    private final FilterStrategy<EquipoRegistrableInterface, CdeFilterCriteria> filterStrategy;

    /** Alcance: lectura de equipos de ortopedia y "otros" para la tabla unificada. */
    public CDEViewController(PantallaVerCDEv2 panel,
                             EquipoService equipoService,
                             EquipoOtrosService equipoOtrosService) {
        this.panel              = panel;
        this.equipoService      = equipoService;
        this.equipoOtrosService = equipoOtrosService;
        this.filterStrategy     = new CdeFilterStrategy();

        this.panel.setOnFiltrosChanged(this::aplicarFiltros);
        cargarDatos();
        this.panel.addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent e) { cargarDatos(); }
        });
    }

    /** Carga todos los equipos activos (ortopedia + otros) y recarga el cache. */
    public void cargarDatos() {
        List<EquipoRegistrableInterface> todos = new ArrayList<>();
        todos.addAll(equipoService.obtenerTodos());
        todos.addAll(equipoOtrosService.obtenerTodos());
        recargarCache(todos);
    }

    @Override
    protected void aplicarFiltros() {
        CdeFilterCriteria criteria = new CdeFilterCriteria(
            panel.getFiltroCliente(),
            panel.getFiltroInstitucion(),
            panel.getFiltroEstados()
        );
        List<EquipoRegistrableInterface> filtrados = filterStrategy.filter(getCache(), criteria);
        panel.actualizarTabla(filtrados);
    }
}