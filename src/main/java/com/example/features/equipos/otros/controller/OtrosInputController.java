package com.example.features.equipos.otros.controller;

import com.example.app.AppModel;
import com.example.common.constants.Constantes;
import com.example.features.clientes.model.Cliente;
import com.example.features.equipos.ortopedias.controller.helpers.GestorNuevasEntidades;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.MaterialOtros;
import com.example.features.equipos.otros.view.PantallaIngresoOtros;
import com.example.features.equipos.otros.view.helpers.PanelMaterialesOtros;
import com.example.ui.common.AutocompleteListener;
import com.example.ui.events.OnEquipoGuardadoListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.CardLayout;
import java.util.List;
import java.util.Objects;

/**
 * Controlador para {@link PantallaIngresoOtros}.
 *
 * Espeja la estructura de {@link com.example.features.equipos.controller.OrthopediaInputController}
 * pero sin profesional, paciente ni institución.
 *
 * Responsabilidades:
 * - Configurar el autocompletado de clientes.
 * - Configurar el autocomplete de descripciones de materiales (1 carácter mínimo).
 * - Validar el formulario.
 * - Construir y persistir el {@link EquipoOtros}.
 */
public class OtrosInputController {

    private static final Logger log = LoggerFactory.getLogger(OtrosInputController.class);

    private final PantallaIngresoOtros       panel;
    private final AppModel                   model;
    private final CardLayout                 navegador;
    private final JPanel                     contenedor;
    private final OnEquipoGuardadoListener   onEquipoGuardadoListener;

    private AutocompleteListener<Cliente>    autocompleteClienteListener;
    private GestorNuevasEntidades<Cliente>   gestorNuevosClientes;

    public OtrosInputController(PantallaIngresoOtros panel,
                                AppModel model,
                                CardLayout navegador,
                                JPanel contenedor,
                                OnEquipoGuardadoListener onEquipoGuardadoListener) {
        this.panel                   = panel;
        this.model                   = model;
        this.navegador               = navegador;
        this.contenedor              = contenedor;
        this.onEquipoGuardadoListener = onEquipoGuardadoListener;
        inicializarEventos();
    }

    // ── Inicialización ────────────────────────────────────────────────────────

    private void inicializarEventos() {

        // Botón guardar
        panel.getBtnGuardar().addActionListener(e -> guardar());

        // Lógica lavado ↔ empaque (idéntica a ortopedia)
        panel.setOnRequiereLavadoChanged(e -> {
            boolean lavado = panel.isRequiereLavado();
            if (lavado) {
                panel.setRequiereEmpaqueSelected(true);
                panel.setRequiereEmpaqueEnabled(false);
            } else {
                panel.setRequiereEmpaqueEnabled(true);
            }
        });

        // Autocomplete de clientes
        autocompleteClienteListener = new AutocompleteListener<>(
            panel.getTxtCliente(),
            texto -> model.buscarClientes(texto),
            cliente -> panel.setSelectedClienteId(cliente.getId()),
            nombre  -> gestorNuevosClientes.manejarEntidadNoExistente(nombre)
        );
        panel.getTxtCliente().getDocument().addDocumentListener(autocompleteClienteListener);

        gestorNuevosClientes = new GestorNuevasEntidades<>(
            obtenerVentanaParente(),
            Constantes.Textos.ENTIDAD_CLIENTE,
            nombre -> panel.getTxtCliente().setText(nombre),
            id     -> panel.setSelectedClienteId(id),
            autocompleteClienteListener,
            cliente -> model.guardarCliente(cliente),
            Cliente::new
        );

        // Autocomplete de materiales "otros" (1 carácter mínimo)
        panel.getPanelMateriales().setOnDescripcionChangedListener((texto, consumerSugerencias) -> {
            if (texto == null || texto.trim().isEmpty()) {
                consumerSugerencias.accept(List.of());
                return;
            }
            List<String> sugerencias = model.getCatalogoOtrosService()
                                            .buscarPorDescripcionParcial(texto.trim());
            consumerSugerencias.accept(sugerencias);
        });
    }

    // ── Guardar ───────────────────────────────────────────────────────────────

    private void guardar() {
        // 1. Validar cliente
        if (panel.getTxtCliente().getText().trim().isEmpty()) {
            panel.mostrarAdvertencia(Constantes.Mensajes.CAMPO_CLIENTE_OBLIGATORIO);
            return;
        }
        if (panel.getSelectedClienteId() == -1) {
            panel.mostrarAdvertencia(Constantes.Mensajes.CLIENTE_NO_SELECCIONADO);
            return;
        }

        // 2. Validar que haya al menos un material con descripción no vacía
        List<PanelMaterialesOtros.OtrosMaterialRow> filas = panel.getMaterialFilas();
        boolean tieneMaterial = filas.stream()
            .anyMatch(f -> !f.txtDescripcion.getText().trim().isEmpty());
        if (!tieneMaterial) {
            panel.mostrarAdvertencia(Constantes.Mensajes.DEBE_AGREGAR_MATERIAL);
            return;
        }

        // 3. Construir EquipoOtros
        EquipoOtros equipo = new EquipoOtros();
        equipo.setNroCliente(panel.getSelectedClienteId());
        equipo.setClienteNombre(panel.getTxtCliente().getText().trim());
        equipo.setRequiereLavado(panel.isRequiereLavado());
        equipo.setRequiereEmpaque(panel.isRequiereEmpaque());

        for (PanelMaterialesOtros.OtrosMaterialRow fila : filas) {
            String desc = fila.txtDescripcion.getText().trim();
            if (desc.isEmpty()) continue;
            Object val = fila.spCantidad.getValue();
            int cantidad = (val instanceof Number) ? ((Number) val).intValue() : 1;
            equipo.agregarMaterial(new MaterialOtros(desc, cantidad));
        }

        // 4. Persistir
        boolean exito;
        try {
            exito = model.getEquipoOtrosService().guardarEquipo(equipo);
        } catch (com.example.common.exception.ValidationException ex) {
            String msg = ex.getValidationErrors().isEmpty()
                ? "Error de validación."
                : String.join("\n", ex.getValidationErrors());
            panel.mostrarAdvertencia(msg);
            return;
        }

        if (exito) {
            panel.mostrarInfo(Constantes.Mensajes.DATOS_GUARDADOS);
            panel.limpiarFormulario();
            if (onEquipoGuardadoListener != null) {
                onEquipoGuardadoListener.onEquipoGuardado();
            }
            navegador.show(contenedor, Constantes.Pantallas.INGRESO_OTROS);
            log.info("EquipoOtros guardado exitosamente");
        } else {
            panel.mostrarError(Constantes.Mensajes.ERROR_GUARDAR_EQUIPO);
            log.error("Falló guardar EquipoOtros");
        }
    }

    private Frame obtenerVentanaParente() {
        return (Frame) SwingUtilities.getWindowAncestor(panel);
    }
}