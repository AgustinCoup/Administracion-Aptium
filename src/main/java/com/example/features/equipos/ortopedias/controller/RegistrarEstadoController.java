package com.example.features.equipos.ortopedias.controller;

import com.example.app.ui.DatosRefresco;
import com.example.common.constants.Constantes;
import com.example.common.model.EquipoKey;
import com.example.common.model.EquipoRegistrableInterface;
import com.example.common.model.MaterialRegistrableInterface;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.model.MovimientoMaterial;
import com.example.features.equipos.ortopedias.service.IEstadoValidator;
import com.example.features.equipos.ortopedias.service.MaterialService;
import com.example.features.equipos.ortopedias.view.PantallaRegistrarEstado;
import com.example.features.equipos.otros.service.EquipoOtrosService;
import com.example.ui.events.OnEstadosActualizadosListener;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    private final EquipoOtrosService          equipoOtrosService;
    private final MaterialService             materialService;
    private final IEstadoValidator            estadoValidator;
    private final Runnable                    solicitarRefresco;
    private OnEstadosActualizadosListener     onEstadosActualizadosListener;

    /**
     * Último snapshot recibido. Permite repintar tras descartar cambios locales
     * sin volver a la base: nada cambió ahí, solo el buffer de esta pantalla.
     */
    private DatosRefresco ultimoSnapshot = DatosRefresco.vacio();

    // Buffer de cambios pendientes indexado por EquipoKey (tipo + id).
    // Necesario porque equipos y equipo_otros tienen auto-increment independientes.
    private final Map<EquipoKey, Map<Integer, MovimientoMaterial>> cambiosPendientes = new HashMap<>();
    private final Map<EquipoKey, EquipoRegistrableInterface>       equiposPendientes = new HashMap<>();

    /**
     * Alcance: lectura de equipos (ortopedia + otros), avance de estado de sus
     * materiales y la regla de qué transición es manual.
     */
    public RegistrarEstadoController(PantallaRegistrarEstado panel,
                                     EquipoOtrosService equipoOtrosService,
                                     MaterialService materialService,
                                     IEstadoValidator estadoValidator,
                                     OnEstadosActualizadosListener onEstadosActualizadosListener,
                                     Runnable solicitarRefresco) {
        this.panel              = panel;
        this.equipoOtrosService = equipoOtrosService;
        this.materialService    = materialService;
        this.estadoValidator    = estadoValidator;
        this.onEstadosActualizadosListener = onEstadosActualizadosListener;
        this.solicitarRefresco  = Objects.requireNonNull(solicitarRefresco, "solicitarRefresco");

        inicializarEventos();
        panel.addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent e) {
                if (!cambiosPendientes.isEmpty()) resetearCambios();
                else solicitarRefresco.run();
            }
        });
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
        panel.setOnGestionarLotes(e -> navegarConGuard(panel::navegarALotes));
        panel.setOnCorrecciones(e -> navegarConGuard(panel::navegarACorrecciones));

        panel.setGuardVolver(
            () -> !cambiosPendientes.isEmpty(),
            Constantes.Mensajes.GUARD_REGISTRAR_ESTADO_CAMBIOS,
            this::descartarCambiosPendientes
        );
    }

    // ── Carga de datos ────────────────────────────────────────────────────────

    /**
     * Vuelca al panel los equipos (ortopedia + otros) que todavía no están
     * ENTREGADO. El filtro es sobre el snapshot: sin I/O, todo en el hilo de UI.
     */
    public void pintar(DatosRefresco datos) {
        this.ultimoSnapshot = datos;
        repintar();
    }

    /** Repinta desde el último snapshot, sin volver a la base. */
    private void repintar() {
        List<EquipoRegistrableInterface> todos = new ArrayList<>();
        agregarNoEntregados(ultimoSnapshot.equipos(), todos);
        agregarNoEntregados(ultimoSnapshot.equiposOtros(), todos);

        panel.actualizarEquipos(todos);
        actualizarTextoAvanzar();
    }

    private static void agregarNoEntregados(List<? extends EquipoRegistrableInterface> origen,
                                            List<EquipoRegistrableInterface> destino) {
        for (EquipoRegistrableInterface equipo : origen) {
            if (equipo.calcularEstado() != EstadoEquipo.ENTREGADO) destino.add(equipo);
        }
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

        if (!estadoValidator.esAvanzableManualmente(material.getEstado(), siguienteEstado)) {
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

        EquipoKey key = new EquipoKey(equipo.getTipo(), equipo.getId());

        if (cambiosPendientes.containsKey(key) &&
            cambiosPendientes.get(key).containsKey(material.getId())) {
            panel.mostrarAdvertencia(Constantes.Mensajes.MATERIAL_CAMBIO_PENDIENTE_DUP);
            return;
        }

        EstadoEquipo siguienteEstado = equipo.getSiguienteEstado(material.getEstado());
        if (siguienteEstado == null) {
            panel.mostrarAdvertencia(
                String.format(Constantes.Mensajes.MATERIAL_ESTADO_FINAL, material.getEstado().getNombre()));
            return;
        }

        cambiosPendientes.putIfAbsent(key, new HashMap<>());
        equiposPendientes.put(key, equipo);

        MovimientoMaterial movimiento = new MovimientoMaterial(material.getId(), cantidad, siguienteEstado);
        cambiosPendientes.get(key).put(material.getId(), movimiento);

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
        // Solo se descartó el buffer local: la base no cambió, alcanza con repintar.
        repintar();
        actualizarTextoAvanzar();
        actualizarContadorCambios();
        panel.setConfirmarEnabled(false);
        panel.setCancelarEnabled(false);
    }

    private void navegarConGuard(Runnable navegar) {
        if (!cambiosPendientes.isEmpty()) {
            boolean descartar = panel.confirmar(
                Constantes.Mensajes.GUARD_REGISTRAR_ESTADO_CAMBIOS,
                Constantes.Mensajes.TITULO_ADVERTENCIA);
            if (!descartar) return;
            descartarCambiosPendientes();
        }
        navegar.run();
    }

    private void confirmarCambios() {
        if (cambiosPendientes.isEmpty()) return;

        boolean conf = panel.confirmar(
            Constantes.Mensajes.CONFIRMAR_CAMBIOS,
            Constantes.Mensajes.TITULO_CONFIRMAR_CAMBIOS);
        if (!conf) return;

        boolean todosExitosos = true;
        StringBuilder errores = new StringBuilder();

        for (Map.Entry<EquipoKey, Map<Integer, MovimientoMaterial>> entry : cambiosPendientes.entrySet()) {
            EquipoKey key                            = entry.getKey();
            List<MovimientoMaterial> movs            = new ArrayList<>(entry.getValue().values());

            boolean exitoso;
            if (key.getTipo() == EquipoRegistrableInterface.TipoEquipo.OTROS) {
                exitoso = equipoOtrosService.aplicarMovimientos(key.getId(), movs);
            } else {
                exitoso = materialService.aplicarMovimientos(key.getId(), movs);
            }

            if (!exitoso) {
                todosExitosos = false;
                errores.append(String.format(Constantes.Mensajes.ERROR_ACTUALIZAR_EQUIPO_ID, key.getId()));
            }
        }

        if (todosExitosos) {
            panel.mostrarInfo(Constantes.Mensajes.CAMBIOS_GUARDADOS_OK);
        } else {
            panel.mostrarError(String.format(Constantes.Mensajes.CAMBIOS_GUARDADOS_ERROR, errores));
        }

        cambiosPendientes.clear();
        equiposPendientes.clear();
        actualizarTextoAvanzar();
        actualizarContadorCambios();
        panel.setConfirmarEnabled(false);
        panel.setCancelarEnabled(false);

        // Se escribió en la base: hay que releerla. Se pide siempre, incluso si
        // alguna operación falló, porque las que sí pasaron cambiaron estado.
        solicitarRefresco.run();

        if (todosExitosos && onEstadosActualizadosListener != null) {
            onEstadosActualizadosListener.onEstadosActualizados();
        }
    }
}