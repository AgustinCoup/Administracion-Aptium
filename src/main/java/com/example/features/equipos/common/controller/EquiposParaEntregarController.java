package com.example.features.equipos.common.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.example.app.ui.DatosOperativos;
import com.example.ui.common.TareaUI;
import com.example.ui.events.OnEstadosActualizadosListener;
import com.example.common.model.EntregaDestinoKey;
import com.example.common.model.EntregaDestinoKey.TipoDestino;
import com.example.features.equipos.ortopedias.controller.helpers.AgrupadorEntregas;
import com.example.features.equipos.ortopedias.service.IEstadoValidator;
import com.example.features.equipos.ortopedias.service.MaterialService;
import com.example.features.equipos.otros.service.EquipoOtrosService;
import com.example.features.equipos.ortopedias.view.PantallaEquiposParaEntregar;
import com.example.features.equipos.ortopedias.view.helpers.InstitucionEntregaItem;
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
     * Alcance: la regla de "qué estado ya es entregable" y las dos entregas
     * masivas. Los equipos llegan desde el refresco global, no los lee acá.
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
    }

    private void entregarInstitucion() {
        List<InstitucionEntregaItem> seleccionadas = panel.getInstitucionesSeleccionadas();

        if (seleccionadas.isEmpty()) {
            panel.mostrarAdvertencia("Debe seleccionar al menos una institución antes de entregar.");
            return;
        }

        Map<InstitucionEntregaItem, List<MaterialEntregaItem>> materialesPorInstitucion = new LinkedHashMap<>();
        for (InstitucionEntregaItem institucion : seleccionadas) {
            List<MaterialEntregaItem> pendientes = materialesPorDestino
                .getOrDefault(institucion.getKey(), List.of())
                .stream()
                .filter(m -> !m.isEntregado())
                .toList();
            if (!pendientes.isEmpty()) {
                materialesPorInstitucion.put(institucion, pendientes);
            }
        }

        if (materialesPorInstitucion.isEmpty()) {
            panel.mostrarAdvertencia("Las instituciones seleccionadas no tienen materiales pendientes de entrega.");
            return;
        }

        if (!panel.confirmar(construirMensajeConfirmacion(materialesPorInstitucion), "Confirmar Entrega")) return;

        TareaUI.<ResultadoEntregas>nueva()
            .nombre("entregar-instituciones")
            .leer(() -> ejecutarEntregas(materialesPorInstitucion))
            .pintar(this::finalizarEntregas)
            .siFalla(e -> panel.mostrarError("No se pudo completar la entrega: " + e.getMessage()))
            .antes(()  -> panel.setEntregarInstitucionEnabled(false))
            .despues(() -> panel.setEntregarInstitucionEnabled(true))
            .lanzar();
    }

    /** Entrega separada por destino, en el hilo de fondo. Solo services y colecciones. */
    private ResultadoEntregas ejecutarEntregas(
            Map<InstitucionEntregaItem, List<MaterialEntregaItem>> materialesPorInstitucion) {
        List<String> exitosas = new ArrayList<>();
        List<String> errores  = new ArrayList<>();
        for (InstitucionEntregaItem institucion : materialesPorInstitucion.keySet()) {
            EntregaDestinoKey key = institucion.getKey();
            boolean exitoso = key.getTipo() == TipoDestino.CLIENTE
                ? equipoOtrosService.entregarClienteCompleto(key.getId())
                : materialService.entregarInstitucionCompleta(key.getId());
            (exitoso ? exitosas : errores).add(institucion.getNombre());
        }
        return new ResultadoEntregas(exitosas, errores);
    }

    /** Hilo de UI: mensajes, refresco global y notificación al resto de las pantallas. */
    private void finalizarEntregas(ResultadoEntregas resultado) {
        if (!resultado.exitosas().isEmpty()) {
            log.info("{} institución(es) entregada(s) exitosamente", resultado.exitosas().size());
            solicitarRefresco.run();
            if (onEstadosActualizadosListener != null) {
                onEstadosActualizadosListener.onEstadosActualizados();
            } else {
                log.warn("onEstadosActualizadosListener es null, otras pantallas NO se refrescarán");
            }
            String texto = resultado.exitosas().size() == 1
                ? "\"" + resultado.exitosas().get(0) + "\" entregada correctamente."
                : resultado.exitosas().size() + " instituciones entregadas correctamente.";
            panel.mostrarInfo(texto);
        }
        if (!resultado.errores().isEmpty()) {
            panel.mostrarError("No se pudieron entregar: " + String.join(", ", resultado.errores()));
        }
    }

    private record ResultadoEntregas(List<String> exitosas, List<String> errores) { }

    private String construirMensajeConfirmacion(Map<InstitucionEntregaItem, List<MaterialEntregaItem>> materialesPorInstitucion) {
        StringBuilder sb = new StringBuilder();
        sb.append("¿Confirmar entrega de los siguientes materiales?\n\n");
        for (Map.Entry<InstitucionEntregaItem, List<MaterialEntregaItem>> entry : materialesPorInstitucion.entrySet()) {
            sb.append("Institución: ").append(entry.getKey().getNombre()).append("\n");
            Map<String, Integer> resumen = new LinkedHashMap<>();
            for (MaterialEntregaItem item : entry.getValue()) {
                resumen.merge(item.getMaterial(), item.getCantidad(), Integer::sum);
            }
            for (Map.Entry<String, Integer> mat : resumen.entrySet()) {
                sb.append("  • ").append(mat.getKey()).append(" x ").append(mat.getValue()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

}