package com.example.controller;

import com.example.constants.Constantes;
import com.example.model.AppModel;
import com.example.model.Equipo;
import com.example.model.EstadoEquipo;
import com.example.model.Material;
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
    private final OnEstadosActualizadosListener onEstadosActualizadosListener;

    // Buffer de cambios pendientes: Map<EquipoId, Map<CodigoMaterial, NuevoEstado>>
    private final Map<Integer, Map<Integer, EstadoEquipo>> cambiosPendientes;

    public RegistrarEstadoController(PantallaRegistrarEstado panel, AppModel model,
                                     OnEstadosActualizadosListener onEstadosActualizadosListener) {
        this.panel = panel;
        this.model = model;
        this.onEstadosActualizadosListener = onEstadosActualizadosListener;
        this.cambiosPendientes = new HashMap<>();

        inicializarEventos();
        cargarEquipos();
    }

    private void inicializarEventos() {
        panel.setOnEquipoSeleccionado(this::actualizarEstadoBotones);
        panel.setOnAvanzar(e -> avanzarMaterialSeleccionado());
        panel.setOnCancelar(e -> cancelarCambios());
        panel.setOnConfirmar(e -> confirmarCambios());
    }

    /**
     * Carga los equipos que no están en estado ENTREGADO.
     */
    private void cargarEquipos() {
        List<Equipo> todosEquipos = model.obtenerTodosLosEquipos();
        List<Equipo> equiposActivos = todosEquipos.stream()
            .filter(eq -> eq.calcularEstado() != EstadoEquipo.ENTREGADO)
            .collect(Collectors.toList());
        panel.actualizarEquipos(equiposActivos);
    }

    /**
     * Actualiza el estado de botones según selección.
     */
    private void actualizarEstadoBotones(Equipo equipoSeleccionado) {
        panel.setAvanzarEnabled(equipoSeleccionado != null);
    }

    /**
     * Avanza el material seleccionado al siguiente estado (en buffer).
     */
    private void avanzarMaterialSeleccionado() {
        Equipo equipoSeleccionado = panel.getEquipoSeleccionado();
        int materialIndex = panel.getMaterialSeleccionadoIndex();

        if (equipoSeleccionado == null || materialIndex < 0) {
            panel.mostrarAdvertencia(
                "Por favor, seleccione un material para avanzar.");
            return;
        }

        Material material = equipoSeleccionado.getMateriales().get(materialIndex);
        EstadoEquipo estadoActual = material.getEstado();

        if (cambiosPendientes.containsKey(equipoSeleccionado.getId()) &&
            cambiosPendientes.get(equipoSeleccionado.getId()).containsKey(material.getCodigo())) {
            estadoActual = cambiosPendientes.get(equipoSeleccionado.getId()).get(material.getCodigo());
        }

        EstadoEquipo siguienteEstado = estadoActual.getSiguiente();
        if (siguienteEstado == null) {
            panel.mostrarAdvertencia(
                "El material ya está en el estado final: " + estadoActual.getNombre());
            return;
        }

        cambiosPendientes.putIfAbsent(equipoSeleccionado.getId(), new HashMap<>());
        cambiosPendientes.get(equipoSeleccionado.getId()).put(material.getCodigo(), siguienteEstado);

        // Actualizar visualmente (en memoria)
        material.setEstado(siguienteEstado);
        panel.recargarMateriales();

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
            return;
        }

        boolean confirmar = panel.confirmar(
            "¿Está seguro de que desea cancelar todos los cambios pendientes?",
            "Confirmar Cancelación");

        if (confirmar) {
            cambiosPendientes.clear();
            cargarEquipos();
            actualizarContadorCambios();
            panel.setConfirmarEnabled(false);
            panel.setCancelarEnabled(false);
        }
    }

    private void confirmarCambios() {
        if (cambiosPendientes.isEmpty()) {
            return;
        }

        int totalCambios = cambiosPendientes.values().stream().mapToInt(Map::size).sum();
        boolean confirmar = panel.confirmar(
            "¿Confirmar " + totalCambios + " cambio(s) de estado?\n" +
            "Esta operación actualizará la base de datos.",
            "Confirmar Cambios");

        if (!confirmar) {
            return;
        }

        boolean todosExitosos = true;
        StringBuilder errores = new StringBuilder();

        for (Map.Entry<Integer, Map<Integer, EstadoEquipo>> entry : cambiosPendientes.entrySet()) {
            Integer equipoId = entry.getKey();
            Map<Integer, EstadoEquipo> cambiosEquipo = entry.getValue();

            boolean exitoso = model.getMaterialService().actualizarMultiplesMateriales(equipoId, cambiosEquipo);
            if (!exitoso) {
                todosExitosos = false;
                errores.append("- Error al actualizar equipo ID: ").append(equipoId).append("\n");
            }
        }

        if (todosExitosos) {
            panel.mostrarInfo("Todos los cambios se guardaron correctamente.");
            cambiosPendientes.clear();
            cargarEquipos();
            actualizarContadorCambios();
            panel.setConfirmarEnabled(false);
            panel.setCancelarEnabled(false);

            if (onEstadosActualizadosListener != null) {
                onEstadosActualizadosListener.onEstadosActualizados();
            }
        } else {
            panel.mostrarError("Algunos cambios no se pudieron guardar:\n" + errores.toString());
            cambiosPendientes.clear();
            cargarEquipos();
            actualizarContadorCambios();
        }
    }
}
