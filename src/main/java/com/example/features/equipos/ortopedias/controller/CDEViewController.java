package com.example.features.equipos.ortopedias.controller;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.example.app.ui.HistorialEquipos;
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
 * {@link #pintar(HistorialEquipos)} y solo transforma y vuelca a la vista.
 *
 * <p>Come del histórico completo, no de la cola activa: la pantalla deja filtrar
 * por ENTREGADO. Lo que oculta los entregados en la carga por defecto es
 * {@link PantallaVerCDEv2#aplicarFiltroInicial()}, un filtro de la vista que el
 * usuario puede sacar.
 */
public class CDEViewController extends AbstractFilterController<EquipoRegistrableInterface> {

    private final PantallaVerCDEv2     panel;
    private final FilterStrategy<EquipoRegistrableInterface, CdeFilterCriteria> filterStrategy;

    /** Alcance: solo pintar la tabla unificada; los datos llegan del histórico de equipos. */
    public CDEViewController(PantallaVerCDEv2 panel, Runnable solicitarRefresco) {
        this.panel          = panel;
        this.filterStrategy = new CdeFilterStrategy();
        Objects.requireNonNull(solicitarRefresco, "solicitarRefresco");

        this.panel.setOnFiltrosChanged(this::aplicarFiltros);
        this.panel.addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent e) {
                // Sin notificar: pintar() es el único que filtra y repinta, para no
                // mostrar un flash con los datos de la visita anterior.
                panel.aplicarFiltroInicial();
                solicitarRefresco.run();
            }
        });
    }

    /** Vuelca el snapshot a la tabla. Corre en el hilo de UI, sin I/O. */
    public void pintar(HistorialEquipos datos) {
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
