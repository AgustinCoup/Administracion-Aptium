package com.example.features.equipos.otros.controller;

import com.example.app.AppModel;
import com.example.common.constants.Constantes;
import com.example.common.exception.ValidationException;
import com.example.features.equipos.common.controller.EquipoInputControllerBase;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.MaterialOtros;
import com.example.features.equipos.otros.model.TipoIngresoOtros;
import com.example.features.equipos.otros.view.PantallaIngresoOtros;
import com.example.features.equipos.otros.view.helpers.PanelMaterialesOtros;
import com.example.ui.events.OnEquipoGuardadoListener;

import java.awt.CardLayout;
import java.util.List;
import javax.swing.JPanel;

public class OtrosInputController extends EquipoInputControllerBase<PantallaIngresoOtros> {

    public OtrosInputController(PantallaIngresoOtros panel, AppModel model,
                                CardLayout navegador, JPanel contenedor,
                                OnEquipoGuardadoListener onEquipoGuardadoListener) {
        super(panel, model, navegador, contenedor, onEquipoGuardadoListener);
        inicializarEventosComunes();
        inicializarEventosEspecificos();
    }

    private void inicializarEventosEspecificos() {
        panel.getPanelMateriales().setOnDescripcionChangedListener((texto, consumerSugerencias) -> {
            if (texto == null || texto.trim().isEmpty()) {
                consumerSugerencias.accept(List.of());
                return;
            }
            consumerSugerencias.accept(model.buscarMaterialesOtrosPorDescripcion(texto.trim()));
        });
        panel.getPanelMateriales().setOnVerificarDescripcion(desc -> model.existeMaterialOtros(desc));
    }

    @Override
    protected void guardar() {
        if (panel.getTxtCliente().getText().trim().isEmpty()) {
            panel.mostrarAdvertencia(Constantes.Mensajes.CAMPO_CLIENTE_OBLIGATORIO);
            return;
        }
        if (panel.getSelectedClienteId() == -1) {
            panel.mostrarAdvertencia(Constantes.Mensajes.CLIENTE_NO_SELECCIONADO);
            return;
        }

        EquipoOtros equipo = new EquipoOtros();
        equipo.setNroCliente(panel.getSelectedClienteId());
        equipo.setClienteNombre(panel.getTxtCliente().getText().trim());
        equipo.setRequiereLavado(panel.isRequiereLavado());
        equipo.setRequiereEmpaque(panel.isRequiereEmpaque());

        if (panel.isRemito()) {
            construirRemito(equipo);
        } else {
            if (!construirDetalles(equipo)) return;
        }

        persistir(equipo);
    }

    private void construirRemito(EquipoOtros equipo) {
        equipo.setTipoIngreso(TipoIngresoOtros.REMITO);
        equipo.setRemitoCantidad(panel.getPanelRemito().getCantidad());
        equipo.setRemitoObservaciones(panel.getPanelRemito().getObservaciones());
    }

    private boolean construirDetalles(EquipoOtros equipo) {
        equipo.setTipoIngreso(TipoIngresoOtros.DETALLES);

        List<PanelMaterialesOtros.OtrosMaterialRow> filas = panel.getMaterialFilas();
        boolean tieneMaterial = filas.stream()
            .anyMatch(f -> !f.txtDescripcion.getText().trim().isEmpty());

        if (!tieneMaterial) {
            panel.mostrarAdvertencia(Constantes.Mensajes.DEBE_AGREGAR_MATERIAL);
            return false;
        }

        for (PanelMaterialesOtros.OtrosMaterialRow fila : filas) {
            String desc = fila.txtDescripcion.getText().trim();
            if (desc.isEmpty()) continue;
            Object val   = fila.spCantidad.getValue();
            int cantidad = (val instanceof Number) ? ((Number) val).intValue() : 1;
            equipo.agregarMaterial(new MaterialOtros(desc, cantidad));
        }
        return true;
    }

    private void persistir(EquipoOtros equipo) {
        boolean exito;
        try {
            exito = model.guardarEquipoOtros(equipo);
        } catch (ValidationException ex) {
            String msg = ex.getValidationErrors().isEmpty()
                ? "Error de validación."
                : String.join("\n", ex.getValidationErrors());
            panel.mostrarAdvertencia(msg);
            return;
        }

        String mensajeExito = equipo.getTipoIngreso() == TipoIngresoOtros.REMITO
            ? String.format(Constantes.Mensajes.REMITO_GUARDADO_OK, equipo.getRemitoId())
            : Constantes.Mensajes.DATOS_GUARDADOS;

        manejarResultadoGuardado(exito, mensajeExito,
            Constantes.Pantallas.INGRESO_OTROS, equipo.getTipoIngreso());
    }
}
