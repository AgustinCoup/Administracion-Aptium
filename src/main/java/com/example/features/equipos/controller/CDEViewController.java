package com.example.features.equipos.controller;

import java.util.List;

import com.example.app.AppModel;
import com.example.common.util.AbstractFilterController;
import com.example.common.util.FilterStrategy;
import com.example.features.equipos.controller.helpers.CdeFilterCriteria;
import com.example.features.equipos.controller.helpers.CdeFilterStrategy;
import com.example.features.equipos.model.Equipo;
import com.example.features.equipos.view.PantallaVerCDEv2;

/**
 * Controlador para PantallaVerCDEv2.
 * Responsabilidad: Cargar datos del Modelo y actualizar la Vista.
 * Respeta MVC: Vista ← Controller ← Modelo
 */
public class CDEViewController extends AbstractFilterController<Equipo> {
    private PantallaVerCDEv2 panel;
    private AppModel model;
    private final FilterStrategy<Equipo, CdeFilterCriteria> filterStrategy;
    
    public CDEViewController(PantallaVerCDEv2 panel, AppModel model) {
        this.panel = panel;
        this.model = model;
        this.filterStrategy = new CdeFilterStrategy();

        this.panel.setOnFiltrosChanged(this::aplicarFiltros);
        cargarDatos();
    }
    
    /**
     * Carga los equipos desde el Modelo y actualiza la Vista.
     */
    public void cargarDatos() {
        recargarCache(model.obtenerTodosLosEquipos());
    }

    @Override
    protected void aplicarFiltros() {
        CdeFilterCriteria criteria = new CdeFilterCriteria(
            panel.getFiltroCliente(),
            panel.getFiltroInstitucion(),
            panel.getFiltroEstado()
        );

        List<Equipo> filtrados = filterStrategy.filter(getCache(), criteria);

        panel.actualizarTabla(filtrados);
    }
}


