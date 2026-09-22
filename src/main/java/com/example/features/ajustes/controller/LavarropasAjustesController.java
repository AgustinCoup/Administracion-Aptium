package com.example.features.ajustes.controller;

import com.example.common.exception.BusinessException;
import com.example.features.ajustes.view.NuevoLavarropasDialog;
import com.example.features.ajustes.view.PanelGestionLavarropas;
import com.example.features.ajustes.view.PantallaAjustes;
import com.example.features.lavadero.model.Lavarropas;
import com.example.features.lavadero.service.LavarropasService;
import com.example.ui.common.TareaUI;

import javax.swing.*;
import java.util.List;

/** Pestaña Lavarropas de Ajustes: alta con el número que elige el operador, baja y reactivación. */
public class LavarropasAjustesController {

    private final PanelGestionLavarropas panel;
    private final LavarropasService      lavarropasService;
    private       Runnable               onMutacion;

    public LavarropasAjustesController(
        PantallaAjustes vista,
        PanelGestionLavarropas panel,
        LavarropasService lavarropasService,
        Runnable onMutacion
    ) {
        this.panel             = panel;
        this.lavarropasService = lavarropasService;
        this.onMutacion        = onMutacion;

        panel.setOnAgregar(this::agregar);
        panel.setOnDarDeBaja(this::darDeBaja);
        panel.setOnReactivar(this::reactivar);

        vista.setOnPestanaSeleccionada(PantallaAjustes.TAB_LAVARROPAS, this::cargarDatos);
    }

    public void setOnMutacion(Runnable r) { this.onMutacion = r; }

    private void cargarDatos() {
        TareaUI.<List<Lavarropas>>nueva()
            .nombre("ajustes-lavarropas-cargar")
            .leer(lavarropasService::obtenerTodos)
            .pintar(panel::setDatos)
            .siFalla(e -> mostrarError("Error al cargar los lavarropas."))
            .lanzar();
    }

    private void agregar() {
        NuevoLavarropasDialog dialog = new NuevoLavarropasDialog(SwingUtilities.getWindowAncestor(panel));
        dialog.setVisible(true);
        Integer numero = dialog.obtenerNumero();
        if (numero == null) return;

        mutar("ajustes-lavarropas-agregar", () -> lavarropasService.agregar(numero), "Error al agregar: ");
    }

    private void darDeBaja() {
        Lavarropas seleccionado = panel.getSeleccionado();
        if (!confirmarSeleccion(seleccionado)) return;
        mutar("ajustes-lavarropas-baja",
            () -> lavarropasService.darDeBaja(seleccionado.getNumero()), "Error al dar de baja: ");
    }

    private void reactivar() {
        Lavarropas seleccionado = panel.getSeleccionado();
        if (!confirmarSeleccion(seleccionado)) return;
        mutar("ajustes-lavarropas-reactivar",
            () -> lavarropasService.reactivar(seleccionado.getNumero()), "Error al reactivar: ");
    }

    private boolean confirmarSeleccion(Lavarropas seleccionado) {
        if (seleccionado != null) return true;
        JOptionPane.showMessageDialog(panel, "Seleccione un lavarropas de la lista.",
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
}
