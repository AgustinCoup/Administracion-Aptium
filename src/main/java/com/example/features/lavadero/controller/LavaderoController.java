package com.example.features.lavadero.controller;

import com.example.app.AppModel;
import com.example.common.constants.Constantes;
import com.example.common.exception.ValidationException;
import com.example.features.lavadero.model.BolsaLavadero;
import com.example.features.lavadero.model.IngresoLavadero;
import com.example.features.lavadero.view.PanelBolsas;
import com.example.features.lavadero.view.PantallaIngresoLavadero;
import com.example.ui.common.AutocompleteListener;
import com.example.ui.events.OnEquipoGuardadoListener;

import java.awt.CardLayout;
import java.math.BigDecimal;
import java.util.List;
import javax.swing.JPanel;

public class LavaderoController {

    private final PantallaIngresoLavadero  panel;
    private final AppModel                 model;
    private final CardLayout               navegador;
    private final JPanel                   contenedor;
    private final OnEquipoGuardadoListener onGuardado;

    public LavaderoController(PantallaIngresoLavadero panel, AppModel model,
                               CardLayout navegador, JPanel contenedor,
                               OnEquipoGuardadoListener onGuardado) {
        this.panel      = panel;
        this.model      = model;
        this.navegador  = navegador;
        this.contenedor = contenedor;
        this.onGuardado = onGuardado;

        cablearAutocompleteCliente();
        cablearBotones();
    }

    private void cablearAutocompleteCliente() {
        new AutocompleteListener<>(
            panel.getTxtCliente(),
            text   -> model.buscarClientesLavadero(text),
            cliente -> panel.setSelectedClienteId(cliente.getId())
        );
    }

    private void cablearBotones() {
        panel.getBtnGuardar().addActionListener(e -> guardar());
    }

    private void guardar() {
        if (panel.getTxtCliente().getText().trim().isEmpty()) {
            panel.mostrarAdvertencia(Constantes.Mensajes.CAMPO_CLIENTE_OBLIGATORIO);
            return;
        }
        if (panel.getSelectedClienteId() == -1) {
            panel.mostrarAdvertencia(Constantes.Mensajes.CLIENTE_NO_SELECCIONADO);
            return;
        }

        List<PanelBolsas.BolsaRow> filas = panel.getPanelBolsas().getFilas();
        IngresoLavadero ingreso = new IngresoLavadero();
        ingreso.setClienteId(panel.getSelectedClienteId());

        for (PanelBolsas.BolsaRow fila : filas) {
            BigDecimal peso = new BigDecimal(fila.spnPeso.getValue().toString());
            ingreso.agregarBolsa(new BolsaLavadero(peso));
        }

        boolean exito;
        try {
            exito = model.registrarIngresoLavadero(ingreso);
        } catch (ValidationException ex) {
            String msg = ex.getValidationErrors().isEmpty()
                ? "Error de validación."
                : String.join("\n", ex.getValidationErrors());
            panel.mostrarAdvertencia(msg);
            return;
        }

        if (exito) {
            panel.mostrarInfo(Constantes.Mensajes.DATOS_GUARDADOS);
            panel.limpiarFormulario();
            navegador.show(contenedor, Constantes.Pantallas.LAVADERO);
            onGuardado.onEquipoGuardado();
        } else {
            panel.mostrarError(Constantes.Mensajes.ERROR_GUARDAR_DATOS);
        }
    }
}
