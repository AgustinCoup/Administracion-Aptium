package com.example.features.ajustes.controller;

import com.example.common.exception.BusinessException;
import com.example.features.ajustes.view.NuevoElementoLavaderoDialog;
import com.example.features.ajustes.view.PanelCatalogoOrtopedias;
import com.example.features.ajustes.view.PanelCatalogoSimple;
import com.example.features.ajustes.view.PantallaAjustes;
import com.example.features.catalogo.model.ItemCatalogo;
import com.example.features.catalogo.service.CatalogoOtrosService;
import com.example.features.catalogo.service.CatalogoService;
import com.example.features.lavadero.model.ElementoCatalogo;
import com.example.features.lavadero.service.ClasificacionLavaderoService;
import com.example.ui.common.TareaUI;

import javax.swing.*;
import java.util.List;

/**
 * Pestaña Catálogos de Ajustes: baja lógica de los tres catálogos que la comparten —
 * elementos de Lavadero (con alta), Ortopedias y Otros (sin alta: ver el javadoc de cada panel).
 * Un solo controller porque las tres sub-pestañas comparten la misma mecánica de mutación
 * (dar de baja / reactivar por {@code TareaUI}); separarlas en tres clases hubiera triplicado
 * ese cableado sin ganar nada en alcance — cada método ya declara con qué catálogo trabaja.
 */
public class CatalogosAjustesController {

    private final PantallaAjustes                      vista;
    private final PanelCatalogoSimple<ElementoCatalogo> panelLavadero;
    private final PanelCatalogoOrtopedias               panelOrtopedias;
    private final PanelCatalogoSimple<ItemCatalogo>     panelOtros;
    private final ClasificacionLavaderoService          clasificacionLavaderoService;
    private final CatalogoService                       catalogoService;
    private final CatalogoOtrosService                  catalogoOtrosService;
    private       Runnable                              onMutacion;

    public CatalogosAjustesController(
        PantallaAjustes vista,
        PanelCatalogoSimple<ElementoCatalogo> panelLavadero,
        PanelCatalogoOrtopedias panelOrtopedias,
        PanelCatalogoSimple<ItemCatalogo> panelOtros,
        ClasificacionLavaderoService clasificacionLavaderoService,
        CatalogoService catalogoService,
        CatalogoOtrosService catalogoOtrosService,
        Runnable onMutacion
    ) {
        this.vista                        = vista;
        this.panelLavadero                = panelLavadero;
        this.panelOrtopedias              = panelOrtopedias;
        this.panelOtros                   = panelOtros;
        this.clasificacionLavaderoService = clasificacionLavaderoService;
        this.catalogoService              = catalogoService;
        this.catalogoOtrosService         = catalogoOtrosService;
        this.onMutacion                   = onMutacion;

        panelLavadero.setOnAgregar(this::agregarElementoLavadero);
        panelLavadero.setOnDarDeBaja(this::darDeBajaLavadero);
        panelLavadero.setOnReactivar(this::reactivarLavadero);

        panelOrtopedias.setOnDarDeBaja(this::darDeBajaOrtopedias);
        panelOrtopedias.setOnReactivar(this::reactivarOrtopedias);

        panelOtros.setOnDarDeBaja(this::darDeBajaOtros);
        panelOtros.setOnReactivar(this::reactivarOtros);

        // Al entrar a "Catálogos" carga sólo la sub-pestaña ya seleccionada (Lavadero, la
        // primera): el ChangeListener del tabbedPane anidado no dispara solo por eso, porque su
        // selección no cambió. Las otras dos sub-pestañas cargan al seleccionarse.
        vista.setOnPestanaSeleccionada(PantallaAjustes.TAB_CATALOGOS, this::cargarSubPestanaActual);
        vista.setOnSubPestanaCatalogoSeleccionada(PantallaAjustes.SUBTAB_CATALOGO_LAVADERO, this::cargarLavadero);
        vista.setOnSubPestanaCatalogoSeleccionada(PantallaAjustes.SUBTAB_CATALOGO_ORTOPEDIAS, this::cargarOrtopedias);
        vista.setOnSubPestanaCatalogoSeleccionada(PantallaAjustes.SUBTAB_CATALOGO_OTROS, this::cargarOtros);
    }

    public void setOnMutacion(Runnable r) { this.onMutacion = r; }

    private void cargarSubPestanaActual() {
        switch (vista.getSubPestanaCatalogoSeleccionada()) {
            case PantallaAjustes.SUBTAB_CATALOGO_ORTOPEDIAS -> cargarOrtopedias();
            case PantallaAjustes.SUBTAB_CATALOGO_OTROS       -> cargarOtros();
            default                                          -> cargarLavadero();
        }
    }

    private void cargarLavadero() {
        TareaUI.<List<ElementoCatalogo>>nueva()
            .nombre("ajustes-catalogo-lavadero-cargar")
            .leer(clasificacionLavaderoService::obtenerCatalogoCompleto)
            .pintar(panelLavadero::setDatos)
            .siFalla(e -> mostrarError("Error al cargar el catálogo de Lavadero."))
            .lanzar();
    }

    private void cargarOrtopedias() {
        TareaUI.<List<ItemCatalogo>>nueva()
            .nombre("ajustes-catalogo-ortopedias-cargar")
            .leer(catalogoService::obtenerTodosConEstado)
            .pintar(panelOrtopedias::setDatos)
            .siFalla(e -> mostrarError("Error al cargar el catálogo de Ortopedias."))
            .lanzar();
    }

    private void cargarOtros() {
        TareaUI.<List<ItemCatalogo>>nueva()
            .nombre("ajustes-catalogo-otros-cargar")
            .leer(catalogoOtrosService::obtenerTodosConEstado)
            .pintar(panelOtros::setDatos)
            .siFalla(e -> mostrarError("Error al cargar el catálogo de Otros."))
            .lanzar();
    }

    // ── Lavadero (con alta) ─────────────────────────────────────────────────

    private void agregarElementoLavadero() {
        NuevoElementoLavaderoDialog dialog =
            new NuevoElementoLavaderoDialog(SwingUtilities.getWindowAncestor(panelLavadero));
        dialog.setVisible(true);
        String nombre = dialog.obtenerNombre();
        if (nombre == null) return;

        mutar("ajustes-catalogo-lavadero-agregar", this::cargarLavadero,
            () -> clasificacionLavaderoService.agregarElementoCatalogo(nombre, dialog.obtenerCategoria()),
            "Error al agregar el elemento: ");
    }

    private void darDeBajaLavadero() {
        ElementoCatalogo seleccionado = panelLavadero.getSeleccionado();
        if (!confirmarSeleccion(panelLavadero, seleccionado)) return;
        mutar("ajustes-catalogo-lavadero-baja", this::cargarLavadero,
            () -> clasificacionLavaderoService.darDeBajaElemento(seleccionado.getId()),
            "Error al dar de baja: ");
    }

    private void reactivarLavadero() {
        ElementoCatalogo seleccionado = panelLavadero.getSeleccionado();
        if (!confirmarSeleccion(panelLavadero, seleccionado)) return;
        mutar("ajustes-catalogo-lavadero-reactivar", this::cargarLavadero,
            () -> clasificacionLavaderoService.reactivarElemento(seleccionado.getId()),
            "Error al reactivar: ");
    }

    // ── Ortopedias (sin alta) ────────────────────────────────────────────────

    private void darDeBajaOrtopedias() {
        ItemCatalogo seleccionado = panelOrtopedias.getSeleccionado();
        if (!confirmarSeleccion(panelOrtopedias, seleccionado)) return;
        mutar("ajustes-catalogo-ortopedias-baja", this::cargarOrtopedias,
            () -> catalogoService.darDeBaja(seleccionado.codigo()),
            "Error al dar de baja: ");
    }

    private void reactivarOrtopedias() {
        ItemCatalogo seleccionado = panelOrtopedias.getSeleccionado();
        if (!confirmarSeleccion(panelOrtopedias, seleccionado)) return;
        mutar("ajustes-catalogo-ortopedias-reactivar", this::cargarOrtopedias,
            () -> catalogoService.reactivar(seleccionado.codigo()),
            "Error al reactivar: ");
    }

    // ── Otros (sin alta) ─────────────────────────────────────────────────────

    private void darDeBajaOtros() {
        ItemCatalogo seleccionado = panelOtros.getSeleccionado();
        if (!confirmarSeleccion(panelOtros, seleccionado)) return;
        mutar("ajustes-catalogo-otros-baja", this::cargarOtros,
            () -> catalogoOtrosService.darDeBaja(seleccionado.codigo()),
            "Error al dar de baja: ");
    }

    private void reactivarOtros() {
        ItemCatalogo seleccionado = panelOtros.getSeleccionado();
        if (!confirmarSeleccion(panelOtros, seleccionado)) return;
        mutar("ajustes-catalogo-otros-reactivar", this::cargarOtros,
            () -> catalogoOtrosService.reactivar(seleccionado.codigo()),
            "Error al reactivar: ");
    }

    // ── Mecánica común ───────────────────────────────────────────────────────

    private boolean confirmarSeleccion(JComponent panel, Object seleccionado) {
        if (seleccionado != null) return true;
        JOptionPane.showMessageDialog(panel, "Seleccione un elemento de la lista.",
            "Aviso", JOptionPane.WARNING_MESSAGE);
        return false;
    }

    /** Toda mutación sigue la misma forma: escribir fuera del EDT, recargar la lista que cambió y avisar. */
    private void mutar(String nombre, Runnable recargar, Runnable operacion, String prefijoError) {
        TareaUI.<Void>nueva()
            .nombre(nombre)
            .leer(() -> { operacion.run(); return null; })
            .pintar(sinResultado -> {
                recargar.run();
                notificarMutacion();
            })
            .siFalla(e -> mostrarError(
                e instanceof BusinessException ? e.getMessage() : prefijoError + e.getMessage()))
            .lanzar();
    }

    private void notificarMutacion() {
        if (onMutacion != null) onMutacion.run();
    }

    private void mostrarError(String mensaje) {
        JOptionPane.showMessageDialog(vista, mensaje, "Error", JOptionPane.ERROR_MESSAGE);
    }
}
