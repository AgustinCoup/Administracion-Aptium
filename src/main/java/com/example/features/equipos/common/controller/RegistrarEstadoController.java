package com.example.features.equipos.common.controller;

import com.example.app.ui.DatosOperativos;
import com.example.common.constants.Constantes;
import com.example.common.model.EquipoKey;
import com.example.common.model.EquipoRegistrableInterface;
import com.example.common.model.MaterialRegistrableInterface;
import com.example.features.equipos.common.controller.helpers.AplicadorMovimientosPendientes;
import com.example.features.equipos.common.controller.helpers.PlanificadorAvanceMultiple;
import com.example.features.equipos.common.controller.helpers.PlanificadorAvanceMultiple.EntradaAvance;
import com.example.features.equipos.common.controller.helpers.PlanificadorAvanceMultiple.EvaluacionAvance;
import com.example.features.equipos.common.controller.helpers.SuperposicionPreviews;
import com.example.features.equipos.common.model.RespuestaAvanceCompleto;
import com.example.features.equipos.ortopedias.model.MovimientoMaterial;
import com.example.features.equipos.ortopedias.service.IEstadoValidator;
import com.example.features.equipos.ortopedias.service.MaterialService;
import com.example.features.equipos.ortopedias.view.PantallaRegistrarEstado;
import com.example.features.equipos.otros.service.EquipoOtrosService;
import com.example.ui.common.TareaUI;
import com.example.ui.events.OnEstadosActualizadosListener;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
    private final PlanificadorAvanceMultiple  planificador;
    private final Runnable                    solicitarRefresco;
    private OnEstadosActualizadosListener     onEstadosActualizadosListener;

    /**
     * Último snapshot recibido. Permite repintar tras descartar cambios locales
     * sin volver a la base: nada cambió ahí, solo el buffer de esta pantalla.
     */
    private DatosOperativos ultimoSnapshot = DatosOperativos.vacio();

    // Buffer de cambios pendientes indexado por EquipoKey (tipo + id).
    // Necesario porque equipos y equipo_otros tienen auto-increment independientes.
    private final Map<EquipoKey, Map<Integer, MovimientoMaterial>> cambiosPendientes = new HashMap<>();

    /**
     * Copia de preview de cada equipo con cambios: los previews se aplican acá y nunca sobre
     * {@link #ultimoSnapshot}, que es de sólo lectura y compartido con Para Entregar y Lotes.
     * Vaciarla es lo que hace que Cancelar vuelva al estado real sin releer.
     */
    private final Map<EquipoKey, EquipoRegistrableInterface>       equiposPendientes = new HashMap<>();

    /**
     * {@code true} mientras Confirmar guarda. Sin esto, cualquier cambio de selección volvía a
     * prender Avanzar en medio de la escritura, y lo que se avanzara ahí lo borraba el
     * {@code clear()} de {@link #finalizarConfirmacion} sin avisar.
     */
    private boolean escrituraEnCurso;

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
        this.planificador       = new PlanificadorAvanceMultiple(estadoValidator);
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
        panel.setOnAvanzar(e -> avanzarSeleccion());
        panel.setOnCancelar(e -> cancelarCambios());
        panel.setOnConfirmar(e -> confirmarCambios());
        panel.setOnGestionarLotes(e -> navegarConGuard(panel::navegarALotes));
        panel.setOnCorrecciones(e -> navegarConGuard(panel::navegarACorrecciones));

        panel.setGuardVolver(
            () -> !cambiosPendientes.isEmpty(),
            Constantes.Mensajes.GUARD_REGISTRAR_ESTADO_CAMBIOS,
            this::descartarCambiosPendientes
        );

        // Botón "Actualizar" / F5: "descartar + releer". Un repintado del grupo operativo
        // deja cambiosPendientes vivo pero invisible (ver "buffer zombi" en el plan); el
        // guard descarta el buffer —vía onDescartar, que sincroniza contador y botones—
        // antes de que la acción dispare la relectura.
        panel.setGuardRefresco(
            () -> !cambiosPendientes.isEmpty(),
            Constantes.Mensajes.REFRESCO_REGISTRAR_ESTADO,
            this::descartarCambiosPendientes);
        panel.setAccionRefrescar(solicitarRefresco);
    }

    // ── Carga de datos ────────────────────────────────────────────────────────

    /**
     * Vuelca al panel los equipos (ortopedia + otros) de la cola activa. Sin I/O:
     * el snapshot ya viene sin entregados, así que acá solo se concatena.
     */
    public void pintar(DatosOperativos datos) {
        this.ultimoSnapshot = datos;
        repintar();
        panel.marcarActualizado();
    }

    /**
     * Repinta desde el último snapshot, sin volver a la base, con las copias de preview encima.
     * Sin copias, es el snapshot tal cual: por eso vaciarlas alcanza para descartar.
     */
    private void repintar() {
        List<EquipoRegistrableInterface> todos = new ArrayList<>();
        todos.addAll(ultimoSnapshot.equipos());
        todos.addAll(ultimoSnapshot.equiposOtros());

        panel.actualizarEquipos(SuperposicionPreviews.superponer(todos, equiposPendientes));
        actualizarTextoAvanzar();
    }

    // ── Lógica de avanzar ─────────────────────────────────────────────────────

    private void actualizarEstadoBotones(EquipoRegistrableInterface equipoSeleccionado) {
        actualizarTextoAvanzar();
    }

    private void actualizarTextoAvanzar() {
        EvaluacionAvance evaluacion = evaluarSeleccion();
        panel.setAvanzarTexto(evaluacion.textoBoton());
        panel.setAvanzarEnabled(evaluacion.botonHabilitado());
        panel.setAvanzarVisible(evaluacion.botonVisible());
    }

    /** Lo que decide el planificador sobre la selección actual. Sólo lee estado del EDT. */
    private EvaluacionAvance evaluarSeleccion() {
        EquipoRegistrableInterface visible = panel.getEquipoSeleccionado();
        if (visible == null) {
            return planificador.evaluar(new EntradaAvance(null, null, List.of(), Set.of(), escrituraEnCurso));
        }
        EquipoKey key = new EquipoKey(visible.getTipo(), visible.getId());
        Set<Integer> idsEnBuffer = cambiosPendientes.getOrDefault(key, Map.of()).keySet();
        return planificador.evaluar(new EntradaAvance(
            originalDe(key, visible), visible, panel.getMaterialesSeleccionados(), idsEnBuffer, escrituraEnCurso));
    }

    /**
     * El equipo tal como vino en el snapshot, sin previews: contra él detecta el planificador las
     * filas que un preview infló. Lo que muestra la tabla sale siempre del snapshot (con copias
     * encima), así que está; si no estuviera, el visible es lo único que hay.
     */
    private EquipoRegistrableInterface originalDe(EquipoKey key, EquipoRegistrableInterface visible) {
        List<? extends EquipoRegistrableInterface> lista =
            key.getTipo() == EquipoRegistrableInterface.TipoEquipo.OTROS
                ? ultimoSnapshot.equiposOtros()
                : ultimoSnapshot.equipos();
        for (EquipoRegistrableInterface equipo : lista) {
            if (Objects.equals(equipo.getId(), key.getId())) return equipo;
        }
        return visible;
    }

    /**
     * Avanza los materiales seleccionados. El orden es lo que no se puede tocar: se piden todas las
     * cantidades, se arman <b>todos</b> los movimientos con los ids que el operador vio, se encola
     * <b>todo</b>, y recién después se aplican los previews, sobre la copia. Cancelar cualquier
     * diálogo aborta la operación entera: nada entra al buffer.
     */
    private void avanzarSeleccion() {
        EquipoRegistrableInterface equipo = panel.getEquipoSeleccionado();
        EvaluacionAvance evaluacion = evaluarSeleccion();
        // Defensa: con la selección bloqueada el botón ya estaba apagado u oculto.
        if (!(evaluacion instanceof EvaluacionAvance.Avanzable avance)) {
            panel.mostrarAdvertencia(evaluacion.textoBoton());
            return;
        }
        pedirCantidades(avance, ultimoSnapshot)
            .ifPresent(movimientos -> encolarConPreview(equipo, movimientos));
    }

    /**
     * La cascada de diálogos. Vacío si el operador canceló cualquiera, o si la pantalla se repintó
     * con un diálogo abierto: un modal sigue despachando el EDT, y el {@code done()} de una
     * {@code TareaUI} en vuelo (un F5 justo antes, el debounce del refresco) corre {@link #pintar}
     * igual. Los materiales capturados serían de un snapshot que ya no está en la tabla, así que se
     * aborta en ese mismo diálogo, sin hacerle tipear el resto al operador.
     */
    private Optional<List<MovimientoMaterial>> pedirCantidades(EvaluacionAvance.Avanzable avance,
                                                               DatosOperativos snapshotAntes) {
        return switch (avance.modoCantidades()) {
            case UN_MATERIAL             -> pedirCantidadPorMaterial(avance, snapshotAntes);
            case COMPLETOS_SIN_PREGUNTAR -> Optional.of(planificador.movimientosCompletos(avance));
            case PREGUNTAR_SI_COMPLETOS  -> preguntarSiCompletos(avance, snapshotAntes);
        };
    }

    /** "¿Todos completos?": Sí los pasa enteros, No abre la cascada, Cancelar aborta. */
    private Optional<List<MovimientoMaterial>> preguntarSiCompletos(EvaluacionAvance.Avanzable avance,
                                                                    DatosOperativos snapshotAntes) {
        RespuestaAvanceCompleto respuesta = panel.preguntarAvanceCompleto(
            avance.materiales().size(), avance.siguiente().getNombre());
        if (respuesta == RespuestaAvanceCompleto.CANCELAR || pantallaReleida(snapshotAntes)) {
            return Optional.empty();
        }
        return respuesta == RespuestaAvanceCompleto.TODOS_COMPLETOS
            ? Optional.of(planificador.movimientosCompletos(avance))
            : pedirCantidadPorMaterial(avance, snapshotAntes);
    }

    /** Un diálogo de cantidad por material; el primero que se cancela aborta todo. */
    private Optional<List<MovimientoMaterial>> pedirCantidadPorMaterial(EvaluacionAvance.Avanzable avance,
                                                                        DatosOperativos snapshotAntes) {
        Map<Integer, Integer> cantidades = new HashMap<>();
        for (MaterialRegistrableInterface material : avance.materiales()) {
            Integer cantidad = panel.pedirCantidadParaAvanzar(material.getDescripcion(), material.getCantidad());
            if (cantidad == null || pantallaReleida(snapshotAntes)) {
                return Optional.empty();
            }
            cantidades.put(material.getId(), cantidad);
        }
        return Optional.of(planificador.movimientosConCantidades(avance, cantidades));
    }

    /**
     * {@code true} (y avisa) si {@link #pintar} corrió desde que se abrió la cascada. Compara
     * referencias: {@code pintar} siempre recibe un {@code DatosOperativos} nuevo, y
     * {@code resetearCambios} —que repinta sin cambiarlo— no puede correr con un modal abierto.
     */
    private boolean pantallaReleida(DatosOperativos snapshotAntes) {
        if (ultimoSnapshot == snapshotAntes) return false;
        panel.mostrarAdvertencia(Constantes.Mensajes.AVANCE_PANTALLA_RELEIDA);
        return true;
    }

    /**
     * Encola todos los movimientos y recién después aplica los previews, sobre la copia. Al revés,
     * el preview del primero cambiaría la lista que el segundo todavía necesita: parte filas, agrega
     * filas sin id y saca filas al unificar. Por eso cada preview busca su material por id.
     */
    private void encolarConPreview(EquipoRegistrableInterface equipo, List<MovimientoMaterial> movimientos) {
        EquipoKey key = new EquipoKey(equipo.getTipo(), equipo.getId());
        // El primer avance sobre un equipo lo copia; los siguientes ya ven la copia en la tabla,
        // y computeIfAbsent la devuelve tal cual.
        EquipoRegistrableInterface copia = equiposPendientes.computeIfAbsent(
            key, k -> equipo.copiarParaPreview());
        Map<Integer, MovimientoMaterial> buffer = cambiosPendientes.computeIfAbsent(key, k -> new HashMap<>());

        for (MovimientoMaterial movimiento : movimientos) {
            buffer.put(movimiento.getMaterialId(), movimiento);
        }
        for (MovimientoMaterial movimiento : movimientos) {
            copia.aplicarMovimientoPreview(materialPorId(copia, movimiento.getMaterialId()),
                movimiento.getCantidad(), movimiento.getEstadoDestino());
        }

        panel.reemplazarEquipo(copia);
        panel.recargarMateriales();
        panel.refrescarEstadosEquipos();
        actualizarTextoAvanzar();
        actualizarContadorCambios();
        panel.setConfirmarEnabled(true);
        panel.setCancelarEnabled(true);
    }

    /**
     * El material de la copia con ese id. La selección puede venir del original (primer avance
     * sobre el equipo) o de la copia (los siguientes): el id es lo único que vale en las dos.
     */
    private static MaterialRegistrableInterface materialPorId(EquipoRegistrableInterface copia, Integer id) {
        return copia.getMaterialesRegistrables().stream()
            .filter(m -> Objects.equals(m.getId(), id))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "El material " + id + " no está en la copia de preview del equipo " + copia.getId()));
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
        // Solo se descartó el buffer local: la base no cambió, alcanza con repintar. Sin las
        // copias, repintar muestra el snapshot intacto.
        repintar();
        actualizarContadorCambios();
        sincronizarBotonesConBuffer();
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

        // Copia del buffer para el hilo de fondo: el del controller se limpia en el
        // hilo de UI, dentro de finalizarConfirmacion.
        Map<EquipoKey, List<MovimientoMaterial>> aAplicar = new LinkedHashMap<>();
        cambiosPendientes.forEach((key, movs) -> aAplicar.put(key, new ArrayList<>(movs.values())));

        TareaUI.<AplicadorMovimientosPendientes.Resultado>nueva()
            .nombre("registrar-estado-confirmar")
            .leer(() -> AplicadorMovimientosPendientes.aplicarTodos(aAplicar, this::aplicarMovimientos))
            .pintar(this::finalizarConfirmacion)
            .siFalla(e -> panel.mostrarError("No se pudieron guardar los cambios: " + e.getMessage()))
            .antes(() -> {
                escrituraEnCurso = true;
                panel.setConfirmarEnabled(false);
                panel.setCancelarEnabled(false);
                actualizarTextoAvanzar();
            })
            .despues(() -> {
                escrituraEnCurso = false;
                sincronizarBotonesConBuffer();
            })
            .lanzar();
    }

    /**
     * Deja los botones acordes al buffer. Va en {@code despues}, que es lo único que corre
     * tanto en éxito como en error: si la escritura falla, el buffer sigue intacto y hay que
     * volver a encender Confirmar y Cancelar —sin esto quedaban apagados con cambios adentro,
     * y el operador no podía ni reintentar ni descartar—. En el éxito el buffer ya está vacío,
     * así que la misma regla los deja apagados.
     */
    private void sincronizarBotonesConBuffer() {
        boolean hayCambios = !cambiosPendientes.isEmpty();
        panel.setConfirmarEnabled(hayCambios);
        panel.setCancelarEnabled(hayCambios);
        // Avanzar no depende del buffer sino de la selección: se recalcula, no se prende a mano.
        actualizarTextoAvanzar();
    }

    /** Despacho al service según el tipo de equipo. Corre en el hilo de fondo. */
    private boolean aplicarMovimientos(EquipoKey key, List<MovimientoMaterial> movs) {
        return key.getTipo() == EquipoRegistrableInterface.TipoEquipo.OTROS
            ? equipoOtrosService.aplicarMovimientos(key.getId(), movs)
            : materialService.aplicarMovimientos(key.getId(), movs);
    }

    /** Hilo de UI: mensaje del resultado, limpieza del buffer y refresco global. */
    private void finalizarConfirmacion(AplicadorMovimientosPendientes.Resultado resultado) {
        if (resultado.todosExitosos()) {
            panel.mostrarInfo(Constantes.Mensajes.CAMBIOS_GUARDADOS_OK);
        } else {
            // Fallos técnicos y choques de concurrencia se nombran por separado: un choque no es
            // un error del operador, es que otro se le adelantó, y su trabajo sobre ese equipo
            // hay que rehacerlo con los datos recargados.
            StringBuilder detalle = new StringBuilder();
            for (Integer id : resultado.idsConError()) {
                detalle.append(String.format(Constantes.Mensajes.ERROR_ACTUALIZAR_EQUIPO_ID, id));
            }
            for (Integer id : resultado.idsConConflicto()) {
                detalle.append(String.format(Constantes.Mensajes.CONFLICTO_ACTUALIZAR_EQUIPO_ID, id));
            }
            panel.mostrarError(String.format(Constantes.Mensajes.CAMBIOS_GUARDADOS_ERROR, detalle));
        }

        // Los botones los deja sincronizarBotonesConBuffer, en el despues de la tarea.
        cambiosPendientes.clear();
        equiposPendientes.clear();
        actualizarContadorCambios();

        // Se escribió en la base: hay que releerla. Se pide siempre, incluso si
        // alguna operación falló, porque las que sí pasaron cambiaron estado.
        solicitarRefresco.run();

        if (resultado.todosExitosos() && onEstadosActualizadosListener != null) {
            onEstadosActualizadosListener.onEstadosActualizados();
        }
    }
}