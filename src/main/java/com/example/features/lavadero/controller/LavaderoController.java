package com.example.features.lavadero.controller;

import com.example.common.constants.Constantes;
import com.example.common.exception.ValidationException;
import com.example.features.clientes.service.ClienteService;
import com.example.features.lavadero.model.BolsaLavadero;
import com.example.features.lavadero.model.IngresoLavadero;
import com.example.features.lavadero.service.LavaderoService;
import com.example.features.lavadero.view.PanelBolsas;
import com.example.features.lavadero.view.PantallaIngresoLavadero;
import com.example.ui.common.AutocompleteListener;
import com.example.ui.common.TareaUI;
import com.example.ui.events.OnEquipoGuardadoListener;

import java.awt.CardLayout;
import java.math.BigDecimal;
import java.util.List;
import javax.swing.JPanel;

/**
 * Cablea el alta de un ingreso de lavadero.
 *
 * <p>El guardado va por {@link TareaUI}: validación y armado del {@code IngresoLavadero} en el
 * hilo de la interfaz, la escritura en fondo, y el éxito —mensaje, limpieza, navegación y
 * refresco— en {@code pintar}.
 *
 * <p><b>Excepción conocida:</b> el autocompletado de clientes sí consulta la base desde el hilo
 * de la interfaz ({@link #cablearAutocompleteCliente()}). Es la misma excepción aceptada que los
 * otros cuatro autocompletados por tecla de la app: se dispara en cada pulsación y moverlo a
 * fondo pide cancelación y orden de resultados, que es un cambio aparte.
 */
public class LavaderoController {

    private final PantallaIngresoLavadero  panel;
    private final ClienteService           clienteService;
    private final LavaderoService          lavaderoService;
    private final CardLayout               navegador;
    private final JPanel                   contenedor;
    private final OnEquipoGuardadoListener onGuardado;

    public LavaderoController(PantallaIngresoLavadero panel, ClienteService clienteService,
                               LavaderoService lavaderoService,
                               CardLayout navegador, JPanel contenedor,
                               OnEquipoGuardadoListener onGuardado) {
        this.panel      = panel;
        this.clienteService  = clienteService;
        this.lavaderoService = lavaderoService;
        this.navegador  = navegador;
        this.contenedor = contenedor;
        this.onGuardado = onGuardado;

        cablearAutocompleteCliente();
        cablearBotones();
    }

    private void cablearAutocompleteCliente() {
        new AutocompleteListener<>(
            panel.getTxtCliente(),
            text   -> clienteService.buscarClientes(text),
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

        // El ingreso ya está armado y nadie más lo toca: se puede entregar al hilo de fondo.
        TareaUI.<Boolean>nueva()
            .nombre("guardar-ingreso-lavadero")
            .antes(() -> panel.getBtnGuardar().setEnabled(false))
            .despues(() -> panel.getBtnGuardar().setEnabled(true))
            .leer(() -> lavaderoService.registrarIngreso(ingreso))
            .pintar(this::finalizarGuardado)
            .siFalla(this::mostrarFallo)
            .lanzar();
    }

    private void finalizarGuardado(boolean guardado) {
        if (!guardado) {
            panel.mostrarError(Constantes.Mensajes.ERROR_GUARDAR_DATOS);
            return;
        }
        panel.mostrarInfo(Constantes.Mensajes.DATOS_GUARDADOS);
        panel.limpiarFormulario();
        navegador.show(contenedor, Constantes.Pantallas.LAVADERO);
        onGuardado.onEquipoGuardado();
    }

    private void mostrarFallo(Throwable causa) {
        if (causa instanceof ValidationException validacion) {
            panel.mostrarAdvertencia(validacion.getValidationErrors().isEmpty()
                ? "Error de validación."
                : String.join("\n", validacion.getValidationErrors()));
            return;
        }
        panel.mostrarError(Constantes.Mensajes.ERROR_GUARDAR_DATOS);
    }
}
