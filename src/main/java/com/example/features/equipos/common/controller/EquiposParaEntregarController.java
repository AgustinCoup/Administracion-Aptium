package com.example.features.equipos.common.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.example.app.ui.DatosOperativos;
import com.example.common.constants.Constantes;
import com.example.ui.common.TareaUI;
import com.example.ui.events.OnEstadosActualizadosListener;
import com.example.common.model.EntregaDestinoKey;
import com.example.common.model.EntregaDestinoKey.TipoDestino;
import com.example.features.equipos.common.controller.helpers.AplicadorPorPartes;
import com.example.features.equipos.ortopedias.controller.helpers.AgrupadorEntregas;
import com.example.features.equipos.ortopedias.controller.helpers.PlanificadorEntrega;
import com.example.features.equipos.ortopedias.controller.helpers.PlanificadorEntrega.Plan;
import com.example.features.equipos.ortopedias.controller.helpers.PlanificadorEntrega.Rechazo;
import com.example.features.equipos.ortopedias.controller.helpers.PlanificadorEntrega.ResultadoPlan;
import com.example.features.equipos.ortopedias.controller.helpers.PlanificadorEntrega.ResultadoTexto;
import com.example.features.equipos.ortopedias.controller.helpers.PlanificadorEntrega.SolicitudEntrega;
import com.example.features.equipos.ortopedias.service.IEstadoValidator;
import com.example.features.equipos.ortopedias.service.MaterialService;
import com.example.features.equipos.otros.service.EquipoOtrosService;
import com.example.features.equipos.ortopedias.view.PantallaEquiposParaEntregar;
import com.example.features.equipos.ortopedias.view.helpers.MaterialEntregaItem;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EquiposParaEntregarController {
    private static final Logger log = LoggerFactory.getLogger(EquiposParaEntregarController.class);

    private final PantallaEquiposParaEntregar panel;
    private final EquipoOtrosService  equipoOtrosService;
    private final MaterialService     materialService;
    private final AgrupadorEntregas   agrupador;
    private final Runnable            solicitarRefresco;
    private final Map<EntregaDestinoKey, List<MaterialEntregaItem>> materialesPorDestino  = new HashMap<>();
    private final Map<EntregaDestinoKey, Integer>                   volumenPorDestino     = new HashMap<>();
    private OnEstadosActualizadosListener onEstadosActualizadosListener;

    /**
     * Alcance: la regla de "qué estado ya es entregable" y la entrega por id, todo o una
     * selección. Los equipos llegan desde el refresco global, no los lee acá.
     */
    public EquiposParaEntregarController(PantallaEquiposParaEntregar panel,
                                         EquipoOtrosService equipoOtrosService,
                                         MaterialService materialService,
                                         IEstadoValidator estadoValidator,
                                         OnEstadosActualizadosListener onEstadosActualizadosListener,
                                         Runnable solicitarRefresco) {
        this.panel              = panel;
        this.equipoOtrosService = equipoOtrosService;
        this.materialService    = materialService;
        this.agrupador          = new AgrupadorEntregas(estadoValidator);
        this.solicitarRefresco  = Objects.requireNonNull(solicitarRefresco, "solicitarRefresco");
        this.onEstadosActualizadosListener = onEstadosActualizadosListener;
        inicializarEventos();
        // Botón "Actualizar": reusa el mismo disparador del componentShown. Sin guard:
        // no acumula estado en memoria entre lecturas.
        panel.setAccionRefrescar(solicitarRefresco);
        panel.addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent e) { solicitarRefresco.run(); }
        });
    }

    /**
     * Permite asignar el callback después de la construcción del objeto.
     * Útil para evitar referencias circulares en la construcción.
     */
    public void setOnEstadosActualizados(OnEstadosActualizadosListener listener) {
        this.onEstadosActualizadosListener = listener;
    }

    private void inicializarEventos() {
        panel.setOnInstitucionSeleccionada(institucion -> {
            if (institucion == null) {
                panel.limpiarMateriales();
                panel.ocultarVolumen();
                return;
            }
            List<MaterialEntregaItem> materiales =
                materialesPorDestino.getOrDefault(institucion.getKey(), List.of());
            panel.actualizarMateriales(materiales);
            if (institucion.getKey().getTipo() == TipoDestino.CLIENTE) {
                int litros = volumenPorDestino.getOrDefault(institucion.getKey(), 0);
                panel.mostrarVolumenCliente(litros);
            } else {
                panel.ocultarVolumen();
            }
        });

        panel.setOnEntregarInstitucion(e -> entregarInstitucion());
    }

    /**
     * Vuelca el snapshot a la pantalla. Sin I/O: la agrupación es lógica pura.
     *
     * <p>El snapshot es la cola activa, y de eso depende la pantalla: un equipo
     * entregado por completo no llega hasta acá. {@link AgrupadorEntregas} descuenta
     * lo ya entregado <i>dentro</i> de un equipo que todavía tiene pendientes, pero
     * no filtra al equipo entero — ver {@code AgrupadorEntregasTest}.
     */
    public void pintar(DatosOperativos datos) {
        AgrupadorEntregas.Resultado agrupado =
            agrupador.agrupar(datos.equipos(), datos.equiposOtros());

        materialesPorDestino.clear();
        materialesPorDestino.putAll(agrupado.materialesPorDestino());
        volumenPorDestino.clear();
        volumenPorDestino.putAll(agrupado.volumenPorDestino());

        panel.actualizarInstituciones(agrupado.filas());
        panel.limpiarMateriales();
        panel.marcarActualizado();
    }

    /**
     * Planifica, confirma y entrega. El controller no calcula: {@link PlanificadorEntrega} decide
     * qué se entrega y arma el texto de la confirmación a partir de la selección de la pantalla.
     */
    private void entregarInstitucion() {
        ResultadoPlan resultado = PlanificadorEntrega.planificar(
            panel.getInstitucionesSeleccionadas(), materialesPorDestino, panel.getMaterialesSeleccionados());

        if (resultado instanceof Rechazo rechazo) {
            panel.mostrarAdvertencia(rechazo.mensaje());
            return;
        }
        Plan plan = (Plan) resultado;

        if (!panel.confirmarConDetalle(plan.textoConfirmacion(), Constantes.Mensajes.TITULO_CONFIRMAR_ENTREGA)) {
            return;
        }

        TareaUI.<AplicadorPorPartes.Resultado<EntregaDestinoKey>>nueva()
            .nombre("entregar-instituciones")
            .leer(() -> AplicadorPorPartes.aplicarTodos(plan.solicitudes(), this::entregar))
            .pintar(resultadoAplicado -> finalizarEntregas(resultadoAplicado, plan.solicitudes()))
            .siFalla(e -> panel.mostrarError("No se pudo completar la entrega: " + e.getMessage()))
            .antes(()  -> panel.setEntregarInstitucionEnabled(false))
            .despues(() -> panel.setEntregarInstitucionEnabled(true))
            .lanzar();
    }

    /** Despacho al service según el tipo de destino. Corre en el hilo de fondo. */
    private boolean entregar(EntregaDestinoKey destino, SolicitudEntrega solicitud) {
        return destino.getTipo() == TipoDestino.CLIENTE
            ? equipoOtrosService.entregar(solicitud.filas(), solicitud.remitos())
            : materialService.entregarMateriales(solicitud.filas());
    }

    /**
     * Hilo de UI: mensajes del resultado, refresco global y notificación al resto de las
     * pantallas. El refresco se pide <b>siempre</b>, también si todo falló o chocó: un conflicto
     * es justamente que la pantalla quedó vieja.
     */
    private void finalizarEntregas(AplicadorPorPartes.Resultado<EntregaDestinoKey> resultado,
                                   Map<EntregaDestinoKey, SolicitudEntrega> solicitudes) {
        ResultadoTexto texto = PlanificadorEntrega.describirResultado(resultado, solicitudes);
        if (texto.info() != null) {
            panel.mostrarInfo(texto.info());
        }
        if (texto.error() != null) {
            panel.mostrarError(texto.error());
        }

        solicitarRefresco.run();

        if (resultado.algunaExitosa()) {
            if (onEstadosActualizadosListener != null) {
                onEstadosActualizadosListener.onEstadosActualizados();
            } else {
                log.warn("onEstadosActualizadosListener es null, otras pantallas NO se refrescarán");
            }
        }
    }
}
