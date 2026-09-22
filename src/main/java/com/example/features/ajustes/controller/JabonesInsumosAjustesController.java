package com.example.features.ajustes.controller;

import com.example.common.exception.BusinessException;
import com.example.features.ajustes.view.PanelJabonesEInsumos;
import com.example.features.ajustes.view.PantallaAjustes;
import com.example.features.ajustes.view.PedirTextoDialog;
import com.example.features.lavadero.model.InsumoCatalogo;
import com.example.features.lavadero.model.JabonCatalogo;
import com.example.features.lavadero.model.TipoLavado;
import com.example.features.lavadero.service.CatalogoInsumosService;
import com.example.features.lavadero.service.CatalogoJabonesService;
import com.example.features.lavadero.service.JabonPorTipoLavadoService;
import com.example.ui.common.TareaUI;

import javax.swing.*;
import java.util.List;
import java.util.Map;

/**
 * Pestaña Jabones e insumos de Ajustes: ABM de los dos catálogos y el jabón por defecto de cada
 * tipo de lavado que la card de Ciclos usa para la carga automática (Paso 7, ya cerrado). Se
 * cablea entera, defaults incluidos: no hay nada que dejar deshabilitado.
 */
public class JabonesInsumosAjustesController {

    private final PanelJabonesEInsumos      panel;
    private final CatalogoJabonesService    jabonesService;
    private final CatalogoInsumosService    insumosService;
    private final JabonPorTipoLavadoService defaultsService;
    private       Runnable                  onMutacion;

    public JabonesInsumosAjustesController(
        PantallaAjustes vista,
        PanelJabonesEInsumos panel,
        CatalogoJabonesService jabonesService,
        CatalogoInsumosService insumosService,
        JabonPorTipoLavadoService defaultsService,
        Runnable onMutacion
    ) {
        this.panel           = panel;
        this.jabonesService  = jabonesService;
        this.insumosService  = insumosService;
        this.defaultsService = defaultsService;
        this.onMutacion      = onMutacion;

        panel.getPanelJabones().setOnAgregar(this::agregarJabon);
        panel.getPanelJabones().setOnDarDeBaja(this::darDeBajaJabon);
        panel.getPanelJabones().setOnReactivar(this::reactivarJabon);

        panel.getPanelInsumos().setOnAgregar(this::agregarInsumo);
        panel.getPanelInsumos().setOnDarDeBaja(this::darDeBajaInsumo);
        panel.getPanelInsumos().setOnReactivar(this::reactivarInsumo);

        panel.setOnGuardarDefaults(this::guardarDefaults);

        vista.setOnPestanaSeleccionada(PantallaAjustes.TAB_JABONES_INSUMOS, this::cargarDatos);
    }

    public void setOnMutacion(Runnable r) { this.onMutacion = r; }

    /**
     * Una sola lectura para las tres cosas: los combos de default necesitan los jabones activos
     * ya en mano para poblarse, así que separarla en tres TareaUI no ahorra nada y sólo agrega
     * formas de pintar a medias si una de las tres tarda más que las otras.
     */
    private void cargarDatos() {
        TareaUI.<DatosJabonesInsumos>nueva()
            .nombre("ajustes-jabones-insumos-cargar")
            .leer(() -> new DatosJabonesInsumos(
                jabonesService.obtenerTodos(),
                insumosService.obtenerTodos(),
                jabonesService.obtenerActivos(),
                defaultsService.obtenerDefaults()))
            .pintar(datos -> {
                panel.getPanelJabones().setDatos(datos.jabones());
                panel.getPanelInsumos().setDatos(datos.insumos());
                panel.setJabonesActivos(datos.jabonesActivos(), datos.defaults());
            })
            .siFalla(e -> mostrarError("Error al cargar jabones e insumos."))
            .lanzar();
    }

    // ── Jabones ──────────────────────────────────────────────────────────────

    private void agregarJabon() {
        PedirTextoDialog dialog = new PedirTextoDialog(
            SwingUtilities.getWindowAncestor(panel), "Nuevo jabón", "Nombre:");
        dialog.setVisible(true);
        String nombre = dialog.obtenerValor();
        if (nombre == null) return;
        mutar("ajustes-jabon-agregar", () -> jabonesService.agregar(nombre), "Error al agregar: ");
    }

    private void darDeBajaJabon() {
        JabonCatalogo seleccionado = panel.getPanelJabones().getSeleccionado();
        if (!confirmarSeleccion(seleccionado)) return;
        mutar("ajustes-jabon-baja", () -> jabonesService.darDeBaja(seleccionado.getId()), "Error al dar de baja: ");
    }

    private void reactivarJabon() {
        JabonCatalogo seleccionado = panel.getPanelJabones().getSeleccionado();
        if (!confirmarSeleccion(seleccionado)) return;
        mutar("ajustes-jabon-reactivar", () -> jabonesService.reactivar(seleccionado.getId()), "Error al reactivar: ");
    }

    // ── Insumos ──────────────────────────────────────────────────────────────

    private void agregarInsumo() {
        PedirTextoDialog dialog = new PedirTextoDialog(
            SwingUtilities.getWindowAncestor(panel), "Nuevo insumo", "Nombre:");
        dialog.setVisible(true);
        String nombre = dialog.obtenerValor();
        if (nombre == null) return;
        mutar("ajustes-insumo-agregar", () -> insumosService.agregar(nombre), "Error al agregar: ");
    }

    private void darDeBajaInsumo() {
        InsumoCatalogo seleccionado = panel.getPanelInsumos().getSeleccionado();
        if (!confirmarSeleccion(seleccionado)) return;
        mutar("ajustes-insumo-baja", () -> insumosService.darDeBaja(seleccionado.id()), "Error al dar de baja: ");
    }

    private void reactivarInsumo() {
        InsumoCatalogo seleccionado = panel.getPanelInsumos().getSeleccionado();
        if (!confirmarSeleccion(seleccionado)) return;
        mutar("ajustes-insumo-reactivar", () -> insumosService.reactivar(seleccionado.id()), "Error al reactivar: ");
    }

    // ── Defaults ─────────────────────────────────────────────────────────────

    private void guardarDefaults() {
        TareaUI.<Void>nueva()
            .nombre("ajustes-jabon-defaults-guardar")
            .leer(() -> {
                for (TipoLavado tipo : TipoLavado.values()) {
                    JabonCatalogo elegido = panel.getDefaultSeleccionado(tipo);
                    if (elegido == null) defaultsService.borrar(tipo);
                    else defaultsService.guardar(tipo, elegido.getId());
                }
                return null;
            })
            .pintar(sinResultado -> {
                cargarDatos();
                notificarMutacion();
            })
            .siFalla(e -> mostrarError(e instanceof BusinessException
                ? e.getMessage() : "Error al guardar los defaults: " + e.getMessage()))
            .lanzar();
    }

    // ── Mecánica común ───────────────────────────────────────────────────────

    private boolean confirmarSeleccion(Object seleccionado) {
        if (seleccionado != null) return true;
        JOptionPane.showMessageDialog(panel, "Seleccione un elemento de la lista.",
            "Aviso", JOptionPane.WARNING_MESSAGE);
        return false;
    }

    private void mutar(String nombre, Runnable operacion, String prefijoError) {
        TareaUI.<Void>nueva()
            .nombre(nombre)
            .leer(() -> { operacion.run(); return null; })
            .pintar(sinResultado -> {
                cargarDatos();
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
        JOptionPane.showMessageDialog(panel, mensaje, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private record DatosJabonesInsumos(
        List<JabonCatalogo> jabones,
        List<InsumoCatalogo> insumos,
        List<JabonCatalogo> jabonesActivos,
        Map<TipoLavado, JabonCatalogo> defaults
    ) {}
}
