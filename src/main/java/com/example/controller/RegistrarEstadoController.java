package com.example.controller;

import com.example.constants.Constantes;
import com.example.model.AppModel;
import com.example.model.Equipo;
import com.example.model.EstadoEquipo;
import com.example.model.Material;
import com.example.model.MovimientoMaterial;
import com.example.view.PantallaRegistrarEstado;
import com.example.controller.listeners.OnEstadosActualizadosListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controlador para PantallaRegistrarEstado.
 * Responsabilidad: Orquestar entre Vista y Modelo (sin lógica de BD en la Vista).
 */
public class RegistrarEstadoController {

    private final PantallaRegistrarEstado panel;
    private final AppModel model;
    private OnEstadosActualizadosListener onEstadosActualizadosListener;

    // Buffer de cambios pendientes: Map<EquipoId, Map<MaterialId, Movimiento>>
    private final Map<Integer, Map<Integer, MovimientoMaterial>> cambiosPendientes;
    private Integer equipoEntregaPendienteId;

    public RegistrarEstadoController(PantallaRegistrarEstado panel, AppModel model,
                                     OnEstadosActualizadosListener onEstadosActualizadosListener) {
        this.panel = panel;
        this.model = model;
        this.onEstadosActualizadosListener = onEstadosActualizadosListener;
        this.cambiosPendientes = new HashMap<>();
        this.equipoEntregaPendienteId = null;

        inicializarEventos();
        cargarEquipos();
    }

    /**
     * Permite asignar el callback después de la construcción del objeto.
     * Útil para evitar referencias circulares en la construcción.
     */
    public void setOnEstadosActualizados(OnEstadosActualizadosListener listener) {
        this.onEstadosActualizadosListener = listener;
    }

    private void inicializarEventos() {
        panel.setOnEquipoSeleccionado(this::actualizarEstadoBotones);
        panel.setOnMaterialSeleccionado(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            actualizarTextoAvanzar();
        });
        panel.setOnAvanzar(e -> avanzarMaterialSeleccionado());
        panel.setOnEntregarEquipo(e -> entregarEquipoCompleto());
        panel.setOnCancelar(e -> cancelarCambios());
        panel.setOnConfirmar(e -> confirmarCambios());
    }

    /**
     * Carga los equipos que no están en estado ENTREGADO.
     * PÚBLICO porque es llamado como callback desde OrthopediaInputController.
     */
    public void cargarEquipos() {
        List<Equipo> todosEquipos = model.obtenerTodosLosEquipos();
        List<Equipo> equiposActivos = todosEquipos.stream()
            .filter(eq -> eq.calcularEstado() != EstadoEquipo.ENTREGADO)
            .collect(Collectors.toList());
        panel.actualizarEquipos(equiposActivos);
        actualizarTextoAvanzar();
        actualizarEstadoEntrega(panel.getEquipoSeleccionado());
    }

    /**
     * Actualiza el estado de botones según selección.
     */
    private void actualizarEstadoBotones(Equipo equipoSeleccionado) {
        actualizarTextoAvanzar();
        actualizarEstadoEntrega(equipoSeleccionado);
    }

    private void actualizarEstadoEntrega(Equipo equipoSeleccionado) {
        if (equipoSeleccionado == null || !cambiosPendientes.isEmpty() || equipoEntregaPendienteId != null) {
            panel.setEntregarEquipoEnabled(false);
            panel.setEntregarEquipoVisible(false);
            return;
        }

        boolean todosListos = equipoSeleccionado.getMateriales().stream()
            .allMatch(mat -> mat.getEstado() == EstadoEquipo.ESTERILIZADO
                || mat.getEstado() == EstadoEquipo.ENTREGADO);
        panel.setEntregarEquipoEnabled(todosListos);
        panel.setEntregarEquipoVisible(todosListos);
    }

    private void actualizarTextoAvanzar() {
        Equipo equipoSeleccionado = panel.getEquipoSeleccionado();
        int materialIndex = panel.getMaterialSeleccionadoIndex();

        if (equipoSeleccionado == null || materialIndex < 0 || equipoEntregaPendienteId != null) {
            panel.setAvanzarTexto(Constantes.Textos.BOTON_SELECCIONE_MATERIAL);
            panel.setAvanzarEnabled(false);
            panel.setAvanzarVisible(false);
            return;
        }

        Material material = equipoSeleccionado.getMateriales().get(materialIndex);
        EstadoEquipo siguienteEstado = equipoSeleccionado.getSiguienteEstado(material.getEstado());
        if (siguienteEstado == null) {
            panel.setAvanzarTexto(Constantes.Textos.BOTON_ESTADO_FINAL);
            panel.setAvanzarEnabled(false);
            panel.setAvanzarVisible(false);
            return;
        }

        panel.setAvanzarTexto(String.format(Constantes.Textos.BOTON_PASAR_A, siguienteEstado.getNombre()));
        panel.setAvanzarEnabled(true);
        panel.setAvanzarVisible(true);
    }

    /**
     * Avanza el material seleccionado al siguiente estado (en buffer).
     */
    private void avanzarMaterialSeleccionado() {
        Equipo equipoSeleccionado = panel.getEquipoSeleccionado();
        int materialIndex = panel.getMaterialSeleccionadoIndex();

        if (equipoSeleccionado == null || materialIndex < 0) {
            panel.mostrarAdvertencia(
                Constantes.Mensajes.SELECCIONE_MATERIAL_AVANZAR);
            return;
        }

        Material material = equipoSeleccionado.getMateriales().get(materialIndex);
        if (!material.esPersistido()) {
            panel.mostrarAdvertencia(
                Constantes.Mensajes.MATERIAL_CAMBIOS_PENDIENTES);
            return;
        }

        Integer cantidad = panel.pedirCantidadParaAvanzar(
            material.getDescripcion(),
            material.getCantidad(),
            (chkTodos, spinner) -> chkTodos.addActionListener(e -> {
                if (chkTodos.isSelected()) {
                    spinner.setValue(material.getCantidad());
                    spinner.setEnabled(false);
                } else {
                    spinner.setEnabled(true);
                }
            })
        );
        if (cantidad == null) {
            return;
        }
        EstadoEquipo estadoActual = material.getEstado();

        if (cambiosPendientes.containsKey(equipoSeleccionado.getId()) &&
            cambiosPendientes.get(equipoSeleccionado.getId()).containsKey(material.getId())) {
            panel.mostrarAdvertencia(
                Constantes.Mensajes.MATERIAL_CAMBIO_PENDIENTE_DUP);
            return;
        }

        EstadoEquipo siguienteEstado = equipoSeleccionado.getSiguienteEstado(estadoActual);
        if (siguienteEstado == null) {
            panel.mostrarAdvertencia(
                String.format(Constantes.Mensajes.MATERIAL_ESTADO_FINAL, estadoActual.getNombre()));
            return;
        }

        cambiosPendientes.putIfAbsent(equipoSeleccionado.getId(), new HashMap<>());
        MovimientoMaterial movimiento = new MovimientoMaterial(
            material.getId(),
            cantidad,
            siguienteEstado
        );
        cambiosPendientes.get(equipoSeleccionado.getId()).put(material.getId(), movimiento);

        // Actualizar visualmente (en memoria) para reflejar la subcantidad
        equipoSeleccionado.aplicarMovimientoPreview(material, cantidad, siguienteEstado);
        panel.recargarMateriales();
        panel.refrescarEstadosEquipos();
        actualizarTextoAvanzar();
        actualizarEstadoEntrega(equipoSeleccionado);

        actualizarContadorCambios();
        panel.setConfirmarEnabled(true);
        panel.setCancelarEnabled(true);
    }

    private void actualizarContadorCambios() {
        int totalCambios = cambiosPendientes.values().stream().mapToInt(Map::size).sum();
        panel.setCambiosPendientesCount(totalCambios);
    }

    private void cancelarCambios() {
        if (cambiosPendientes.isEmpty()) {
            if (equipoEntregaPendienteId == null) {
                return;
            }
        }

        boolean confirmar = panel.confirmar(
            Constantes.Mensajes.CONFIRMAR_CANCELACION,
            Constantes.Mensajes.TITULO_CONFIRMAR_CANCELACION);

        if (confirmar) {
            cambiosPendientes.clear();
            equipoEntregaPendienteId = null;
            cargarEquipos();
            actualizarContadorCambios();
            panel.setConfirmarEnabled(false);
            panel.setCancelarEnabled(false);
            actualizarTextoAvanzar();
            actualizarEstadoEntrega(panel.getEquipoSeleccionado());
        }
    }

    private void confirmarCambios() {
        if (cambiosPendientes.isEmpty() && equipoEntregaPendienteId == null) {
            return;
        }

        boolean confirmar = panel.confirmar(
            Constantes.Mensajes.CONFIRMAR_CAMBIOS,
            Constantes.Mensajes.TITULO_CONFIRMAR_CAMBIOS);

        if (!confirmar) {
            return;
        }

        boolean todosExitosos = true;
        StringBuilder errores = new StringBuilder();

        if (equipoEntregaPendienteId != null) {
            boolean exitosoEntrega = model.getMaterialService().entregarEquipoCompleto(equipoEntregaPendienteId);
            if (!exitosoEntrega) {
                todosExitosos = false;
            }
        }

        for (Map.Entry<Integer, Map<Integer, MovimientoMaterial>> entry : cambiosPendientes.entrySet()) {
            Integer equipoId = entry.getKey();
            Map<Integer, MovimientoMaterial> cambiosEquipo = entry.getValue();

            boolean exitoso = model.getMaterialService().aplicarMovimientos(
                equipoId,
                new java.util.ArrayList<>(cambiosEquipo.values())
            );
            if (!exitoso) {
                todosExitosos = false;
                errores.append(String.format(Constantes.Mensajes.ERROR_ACTUALIZAR_EQUIPO_ID, equipoId));
            }
        }

        if (todosExitosos) {
            panel.mostrarInfo(Constantes.Mensajes.CAMBIOS_GUARDADOS_OK);
            cambiosPendientes.clear();
            equipoEntregaPendienteId = null;
            cargarEquipos();
            actualizarContadorCambios();
            panel.setConfirmarEnabled(false);
            panel.setCancelarEnabled(false);
            actualizarTextoAvanzar();
            actualizarEstadoEntrega(panel.getEquipoSeleccionado());

            if (onEstadosActualizadosListener != null) {
                onEstadosActualizadosListener.onEstadosActualizados();
            }
        } else {
            panel.mostrarError(String.format(Constantes.Mensajes.CAMBIOS_GUARDADOS_ERROR, errores));
            cambiosPendientes.clear();
            equipoEntregaPendienteId = null;
            cargarEquipos();
            actualizarContadorCambios();
            actualizarTextoAvanzar();
            actualizarEstadoEntrega(panel.getEquipoSeleccionado());
        }
    }

    private void entregarEquipoCompleto() {
        Equipo equipoSeleccionado = panel.getEquipoSeleccionado();
        if (equipoSeleccionado == null) {
            return;
        }
        if (!cambiosPendientes.isEmpty()) {
            return;
        }

        equipoEntregaPendienteId = equipoSeleccionado.getId();

        for (Material material : equipoSeleccionado.getMateriales()) {
            material.setEstado(EstadoEquipo.ENTREGADO);
        }
        equipoSeleccionado.setEstado(EstadoEquipo.ENTREGADO);
        panel.recargarMateriales();
        panel.refrescarEstadosEquipos();

        panel.setConfirmarEnabled(true);
        panel.setCancelarEnabled(true);
        actualizarTextoAvanzar();
        actualizarEstadoEntrega(equipoSeleccionado);
    }

}
