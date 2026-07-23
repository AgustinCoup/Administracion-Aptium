package com.example.features.equipos.ortopedias.controller;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.example.app.ui.DatosRefresco;
import com.example.common.model.EquipoRegistrableInterface;
import com.example.common.util.AbstractFilterController;
import com.example.common.util.FilterStrategy;
import com.example.features.equipos.ortopedias.controller.helpers.CdeFilterCriteria;
import com.example.features.equipos.ortopedias.controller.helpers.CdeFilterStrategy;
import com.example.features.equipos.ortopedias.view.PantallaVerCDEv2;

/**
 * Controlador para {@link PantallaVerCDEv2}.
 *
 * Muestra equipos de ortopedia Y equipos "otros" en una única tabla. El filtro
 * por institución muestra cadena vacía para "otros", lo que los hace aparecer
 * cuando el campo institución está en blanco.
 *
 * <p>No lee de la base: recibe el snapshot ya leído en
 * {@link #pintar(DatosRefresco)} y solo transforma y vuelca a la vista.
 */
public class CDEViewController extends AbstractFilterController<EquipoRegistrableInterface> {

    private final PantallaVerCDEv2     panel;
    private final FilterStrategy<EquipoRegistrableInterface, CdeFilterCriteria> filterStrategy;

    /** Alcance: solo pintar la tabla unificada; los datos llegan desde el refresco global. */
    public CDEViewController(PantallaVerCDEv2 panel, Runnable solicitarRefresco) {
        this.panel          = panel;
        this.filterStrategy = new CdeFilterStrategy();
        Objects.requireNonNull(solicitarRefresco, "solicitarRefresco");

        this.panel.setOnFiltrosChanged(this::aplicarFiltros);
        this.panel.addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent e) { solicitarRefresco.run(); }
        });
    }

    /** Vuelca el snapshot a la tabla. Corre en el hilo de UI, sin I/O. */
    public void pintar(DatosRefresco datos) {
        List<EquipoRegistrableInterface> todos = new ArrayList<>();
        todos.addAll(datos.equipos());
        todos.addAll(datos.equiposOtros());
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
