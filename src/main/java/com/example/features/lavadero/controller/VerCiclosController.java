package com.example.features.lavadero.controller;

import com.example.common.paginacion.CriteriosPagina;
import com.example.common.paginacion.Pagina;
import com.example.common.paginacion.PaginadorEnMemoria;
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

    /**
     * Paginación en memoria (anti-patrón A3: no alivia la base, sólo el pintado — ver
     * {@link PaginadorEnMemoria}). Se resetea a la página 1 sólo cuando cambia un filtro
     * ({@link #alCambiarFiltros()}), nunca dentro de {@link #aplicarFiltros()}: ese método lo llama
     * también {@code recargarCache} en cada refresco (anti-patrón A15).
     */
    private CriteriosPagina criteriosPagina = CriteriosPagina.primera();

    /** Alcance: pintar la grilla desde el refresco global. Sin I/O propia. */
    public VerCiclosController(PantallaVerCiclos pantalla, Runnable solicitarRefresco) {
        this.pantalla = pantalla;
        Objects.requireNonNull(solicitarRefresco, "solicitarRefresco");

        pantalla.setOnFiltrosChanged(this::alCambiarFiltros);
        pantalla.setAlCambiarPagina(this::alCambiarPagina);
        pantalla.setOnLimpiar(pantalla::limpiarFiltros);

        // El botón "Actualizar" reusa el mismo disparador del componentShown.
        pantalla.setAccionRefrescar(solicitarRefresco);

        pantalla.addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent e) { solicitarRefresco.run(); }
        });
    }

    /** Vuelca el snapshot a la grilla. Sin I/O. */
    public void pintar(List<CicloLavadero> ciclos) {
        recargarCache(ciclos);
        pantalla.marcarActualizado();
    }

    @Override
    protected void aplicarFiltros() {
        CicloFilterCriteria criteria = new CicloFilterCriteria(
            pantalla.getFiltroNumero(),
            pantalla.getFiltroEstados(),
            pantalla.getFiltroFechaDesde(),
            pantalla.getFiltroFechaHasta()
        );
        List<CicloLavadero> filtrados = filterStrategy.filter(getCache(), criteria);
        Pagina<CicloLavadero> pagina = PaginadorEnMemoria.paginar(filtrados, criteriosPagina);
        pantalla.actualizarCiclos(pagina.contenido());
        pantalla.mostrarPaginacion(pagina);
    }

    /** Filtro nuevo ⇒ página 1, igual que en las pantallas paginadas por SQL. */
    private void alCambiarFiltros() {
        criteriosPagina = CriteriosPagina.primera();
        aplicarFiltros();
    }

    /** Otra página del mismo filtro. */
    private void alCambiarPagina(int numeroPagina) {
        criteriosPagina = criteriosPagina.conPagina(numeroPagina);
        aplicarFiltros();
    }
}
