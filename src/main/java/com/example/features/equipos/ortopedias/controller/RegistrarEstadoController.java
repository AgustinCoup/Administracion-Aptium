package com.example.features.equipos.ortopedias.controller;

import com.example.common.constants.Constantes;
import com.example.common.model.EquipoRegistrableInterface;
import com.example.common.model.MaterialRegistrableInterface;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.model.MovimientoMaterial;
import com.example.features.equipos.ortopedias.view.PantallaRegistrarEstado;
import com.example.app.AppModel;
import com.example.ui.events.OnEstadosActualizadosListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controlador para {@link PantallaRegistrarEstado}.
 *
 * Refactorizado para operar sobre {@link EquipoRegistrableInterface} e
 * {@link MaterialRegistrableInterface}, de modo que gestiona tanto equipos de
 * ortopedia como equipos "otros" sin lógica duplicada.
 *
 * Al confirmar cambios, despacha al servicio correcto según
 * {@link EquipoRegistrableInterface.TipoEquipo}.
 */
public class RegistrarEstadoController {

    private final PantallaRegistrarEstado     panel;
    private final AppModel                    model;
    private OnEstadosActualizadosListener     onEstadosActualizadosListener;

    // Buffer de cambios pendientes: Map<EquipoId, Map<MaterialId, Movimiento>>
    // La clave de equipo es el ID de la tabla correspondiente (equipos o equipo_otros).
    // Para distinguir el tipo guardamos el equipo completo en equiposPendientes.
    private final Map<Integer, Map<Integer, MovimientoMaterial>> cambiosPendientes = new HashMap<>();
    private final Map<Integer, EquipoRegistrableInterface>               equiposPendientes = new HashMap<>();

    public RegistrarEstadoController(PantallaRegistrarEstado panel, AppModel model,
                                     OnEstadosActualizadosListener onEstadosActualizadosListener) {
        this.panel = panel;
        this.model = model;
        this.onEstadosActualizadosListener = onEstadosActualizadosListener;

        inicializarEventos();
        cargarEquipos();
    }

    public void setOnEstadosActualizados(OnEstadosActualizadosListener listener) {
        this.onEstadosActualizadosListener = listener;
    }

    private void inicializarEventos() {
        panel.setOnEquipoSeleccionado(this::actualizarEstadoBotones);
        panel.setOnMaterialSeleccionado(e -> {
            if (e.getValueIsAdjusting()) return;
            actualizarTextoAvanzar();
        });
        panel.setOnAvanzar(e -> avanzarMaterialSeleccionado());
        panel.setOnCancelar(e -> cancelarCambios());
        panel.setOnConfirmar(e -> confirmarCambios());
        panel.setOnGestionarLotes(e -> panel.navegarALotes());
        panel.setOnCorrecciones(e -> panel.navegarACorrecciones());

        panel.setGuardVolver(
            () -> !cambiosPendientes.isEmpty(),
            Constantes.Mensajes.GUARD_REGISTRAR_ESTADO_CAMBIOS,
            this::descartarCambiosPendientes
        );
    }

    // ── Carga de datos ────────────────────────────────────────────────────────

    /**
     * Carga todos los equipos activos (ortopedia + otros) que no estén ENTREGADO.
     */
    public void cargarEquipos() {
        // Equipos de ortopedia
        List<EquipoRegistrableInterface> ortopedia = model.obtenerTodosLosEquipos()
            .stream()
            .filter(eq -> eq.calcularEstado() != EstadoEquipo.ENTREGADO)
            .collect(Collectors.toList());

        // Equipos "otros"
        List<EquipoRegistrableInterface> otros = model.getEquipoOtrosService().obtenerTodos()
            .stream()
            .filter(eq -> eq.calcularEstado() != EstadoEquipo.ENTREGADO)
            .collect(Collectors.toList());

        List<EquipoRegistrableInterface> todos = new java.util.ArrayList<>();
        todos.addAll(ortopedia);
        todos.addAll(otros);

        panel.actualizarEquipos(todos);
        actualizarTextoAvanzar();
    }

    // ── Lógica de avanzar ─────────────────────────────────────────────────────

    private void actualizarEstadoBotones(EquipoRegistrableInterface equipoSeleccionado) {
        actualizarTextoAvanzar();
    }

    private void actualizarTextoAvanzar() {
        EquipoRegistrableInterface equipo   = panel.getEquipoSeleccionado();
        int materialIndex           = panel.getMaterialSeleccionadoIndex();

        if (equipo == null || materialIndex < 0) {
            panel.setAvanzarTexto(Constantes.Textos.BOTON_SELECCIONE_MATERIAL);
            panel.setAvanzarEnabled(false);
            panel.setAvanzarVisible(false);
            return;
        }

        MaterialRegistrableInterface material = equipo.getMaterialesRegistrables().get(materialIndex);
        EstadoEquipo siguienteEstado  = equipo.getSiguienteEstado(material.getEstado());

        if (material.getEstado() == EstadoEquipo.ESTERILIZANDO ||
            siguienteEstado == EstadoEquipo.ESTERILIZANDO ||
            siguienteEstado == EstadoEquipo.ESTERILIZADO  ||
            material.getEstado() == EstadoEquipo.ESTERILIZADO) {
            panel.setAvanzarEnabled(false);
            panel.setAvanzarVisible(false);
            return;
        }

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

    private void avanzarMaterialSeleccionado() {
        EquipoRegistrableInterface equipo = panel.getEquipoSeleccionado();
        int materialIndex         = panel.getMaterialSeleccionadoIndex();

        if (equipo == null || materialIndex < 0) {
            panel.mostrarAdvertencia(Constantes.Mensajes.SELECCIONE_MATERIAL_AVANZAR);
            return;
        }

        MaterialRegistrableInterface material = equipo.getMaterialesRegistrables().get(materialIndex);
        if (!material.esPersistido()) {
            panel.mostrarAdvertencia(Constantes.Mensajes.MATERIAL_CAMBIOS_PENDIENTES);
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
        if (cantidad == null) return;

        int equipoId = equipo.getId();

        if (cambiosPendientes.containsKey(equipoId) &&
            cambiosPendientes.get(equipoId).containsKey(material.getId())) {
            panel.mostrarAdvertencia(Constantes.Mensajes.MATERIAL_CAMBIO_PENDIENTE_DUP);
            return;
        }

        EstadoEquipo siguienteEstado = equipo.getSiguienteEstado(material.getEstado());
        if (siguienteEstado == null) {
            panel.mostrarAdvertencia(
                String.format(Constantes.Mensajes.MATERIAL_ESTADO_FINAL, material.getEstado().getNombre()));
            return;
        }

        cambiosPendientes.putIfAbsent(equipoId, new HashMap<>());
        equiposPendientes.put(equipoId, equipo);

        MovimientoMaterial movimiento = new MovimientoMaterial(material.getId(), cantidad, siguienteEstado);
        cambiosPendientes.get(equipoId).put(material.getId(), movimiento);

        equipo.aplicarMovimientoPreview(material, cantidad, siguienteEstado);
        panel.recargarMateriales();
        panel.refrescarEstadosEquipos();
        actualizarTextoAvanzar();
        actualizarContadorCambios();
        panel.setConfirmarEnabled(true);
        panel.setCancelarEnabled(true);
    }

    // ── Confirmar / Cancelar ──────────────────────────────────────────────────

    private void actualizarContadorCambios() {
        int total = cambiosPendientes.values().stream().mapToInt(Map::size).sum();
        panel.setCambiosPendientesCount(total);
    }

    private void cancelarCambios() {
        if (cambiosPendientes.isEmpty()) return;
        boolean conf = panel.confirmar(
            Constantes.Mensajes.CONFIRMAR_CANCELACION,
            Constantes.Mensajes.TITULO_CONFIRMAR_CANCELACION);
        if (conf) resetearCambios();
    }

    public void descartarCambiosPendientes() {
        if (!cambiosPendientes.isEmpty()) resetearCambios();
    }

    private void resetearCambios() {
        cambiosPendientes.clear();
        equiposPendientes.clear();
        cargarEquipos();
        actualizarTextoAvanzar();
        actualizarContadorCambios();
        panel.setConfirmarEnabled(false);
        panel.setCancelarEnabled(false);
    }

    private void confirmarCambios() {
        if (cambiosPendientes.isEmpty()) return;

        boolean conf = panel.confirmar(
            Constantes.Mensajes.CONFIRMAR_CAMBIOS,
            Constantes.Mensajes.TITULO_CONFIRMAR_CAMBIOS);
        if (!conf) return;

        boolean todosExitosos = true;
        StringBuilder errores = new StringBuilder();

        for (Map.Entry<Integer, Map<Integer, MovimientoMaterial>> entry : cambiosPendientes.entrySet()) {
            Integer equipoId                         = entry.getKey();
            List<MovimientoMaterial> movs            = new java.util.ArrayList<>(entry.getValue().values());
            EquipoRegistrableInterface equipo                = equiposPendientes.get(equipoId);
            EquipoRegistrableInterface.TipoEquipo tipo       = equipo != null
                ? equipo.getTipo()
                : EquipoRegistrableInterface.TipoEquipo.ORTOPEDIA; // fallback seguro

            boolean exitoso;
            if (tipo == EquipoRegistrableInterface.TipoEquipo.OTROS) {
                exitoso = model.getEquipoOtrosService().aplicarMovimientos(equipoId, movs);
            } else {
                exitoso = model.getMaterialService().aplicarMovimientos(equipoId, movs);
            }

            if (!exitoso) {
                todosExitosos = false;
                errores.append(String.format(Constantes.Mensajes.ERROR_ACTUALIZAR_EQUIPO_ID, equipoId));
            }
        }

        if (todosExitosos) {
            panel.mostrarInfo(Constantes.Mensajes.CAMBIOS_GUARDADOS_OK);
        } else {
            panel.mostrarError(String.format(Constantes.Mensajes.CAMBIOS_GUARDADOS_ERROR, errores));
        }

        cambiosPendientes.clear();
        equiposPendientes.clear();
        cargarEquipos();
        actualizarTextoAvanzar();
        actualizarContadorCambios();
        panel.setConfirmarEnabled(false);
        panel.setCancelarEnabled(false);

        if (todosExitosos && onEstadosActualizadosListener != null) {
            onEstadosActualizadosListener.onEstadosActualizados();
        }
    }
}