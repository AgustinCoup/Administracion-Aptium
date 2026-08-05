package com.example.features.lavadero.controller;

import com.example.common.constants.Constantes;
import com.example.common.exception.ValidationException;
import com.example.features.lavadero.model.ElementoClasificacion;
import com.example.features.lavadero.model.IngresoLavaderoResumen;
import com.example.features.lavadero.service.ClasificacionLavaderoService;
import com.example.features.lavadero.service.LavaderoService;
import com.example.features.lavadero.view.PanelElementosClasificacion.ElementoFila;
import com.example.features.lavadero.view.PantallaClasificacionLavadero;
import com.example.ui.events.OnEquipoGuardadoListener;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ClasificacionController {

    private final PantallaClasificacionLavadero panel;
    private final LavaderoService               lavaderoService;
    private final ClasificacionLavaderoService   clasificacionLavaderoService;
    private final CardLayout                    navegador;
    private final JPanel                        contenedor;
    private final OnEquipoGuardadoListener      onGuardado;

    public ClasificacionController(PantallaClasificacionLavadero panel,
                                   LavaderoService lavaderoService,
                                   ClasificacionLavaderoService clasificacionLavaderoService,
                                   CardLayout navegador,
                                   JPanel contenedor,
                                   OnEquipoGuardadoListener onGuardado) {
        this.panel      = panel;
        this.lavaderoService = lavaderoService;
        this.clasificacionLavaderoService = clasificacionLavaderoService;
        this.navegador  = navegador;
        this.contenedor = contenedor;
        this.onGuardado = onGuardado;

        panel.getBtnGuardar().addActionListener(e -> guardar());
        panel.getBtnCancelar().addActionListener(e -> cancelar());
    }

    public void cargarIngresosSinClasificar() {
        panel.refrescar(
            lavaderoService.obtenerIngresosSinClasificar(),
            clasificacionLavaderoService.obtenerCatalogo()
        );
    }

    private void guardar() {
        IngresoLavaderoResumen ingreso = panel.getSelectedIngreso();
        if (ingreso == null) {
            panel.mostrarError("Debe seleccionar un ingreso.");
            return;
        }

        List<ElementoFila> filas = panel.getPanelElementos().getFilas();
        if (filas.isEmpty()) {
            panel.mostrarError("Debe agregar al menos un elemento.");
            return;
        }

        if (panel.getPanelElementos().tieneDuplicados()) {
            panel.mostrarError("Hay elementos repetidos.\nUnifique las filas marcadas en rojo antes de guardar.");
            return;
        }

        List<ElementoClasificacion> elementos = new ArrayList<>();
        for (ElementoFila fila : filas) {
            int elementoId = fila.cmbElemento.getItemAt(fila.cmbElemento.getSelectedIndex()).getId();
            int cantidad   = (Integer) fila.spnCantidad.getValue();
            elementos.add(new ElementoClasificacion(elementoId, cantidad));
        }

        try {
            boolean ok = clasificacionLavaderoService.guardar(ingreso.getId(), elementos);
            if (ok) {
                panel.mostrarInfo(Constantes.Mensajes.DATOS_GUARDADOS);
                panel.limpiarFormulario();
                cargarIngresosSinClasificar();
                navegador.show(contenedor, Constantes.Pantallas.LAVADERO);
                onGuardado.onEquipoGuardado();
            } else {
                panel.mostrarError(Constantes.Mensajes.ERROR_GUARDAR_DATOS);
            }
        } catch (ValidationException ex) {
            panel.mostrarError(String.join("\n", ex.getValidationErrors()));
        }
    }

    private void cancelar() {
        panel.limpiarFormulario();
        navegador.show(contenedor, Constantes.Pantallas.LAVADERO);
    }
}
