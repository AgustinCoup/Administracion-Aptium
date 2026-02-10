package com.example.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.controller.helpers.InstitucionAcumulador;
import com.example.controller.helpers.MaterialAgrupado;
import com.example.controller.listeners.OnEstadosActualizadosListener;
import com.example.constants.Constantes;
import com.example.model.AppModel;
import com.example.model.Equipo;
import com.example.model.EstadoEquipo;
import com.example.model.Material;
import com.example.view.PantallaEquiposParaEntregar;
import com.example.view.helpers.InstitucionEntregaItem;
import com.example.view.helpers.MaterialEntregaItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EquiposParaEntregarController {
    private static final Logger log = LoggerFactory.getLogger(EquiposParaEntregarController.class);
    
    private final PantallaEquiposParaEntregar panel;
    private final AppModel model;
    private final Map<Integer, List<MaterialEntregaItem>> materialesPorInstitucion = new HashMap<>();
    private OnEstadosActualizadosListener onEstadosActualizadosListener;

    public EquiposParaEntregarController(PantallaEquiposParaEntregar panel, AppModel model,
                                         OnEstadosActualizadosListener onEstadosActualizadosListener) {
        this.panel = panel;
        this.model = model;
        this.onEstadosActualizadosListener = onEstadosActualizadosListener;
        inicializarEventos();
        cargarDatos();
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
                return;
            }
            List<MaterialEntregaItem> materiales =
                materialesPorInstitucion.getOrDefault(institucion.getId(), List.of());
            panel.actualizarMateriales(materiales);
        });

        panel.setOnEntregarInstitucion(e -> entregarInstitucion());
    }

    public void cargarDatos() {
        List<Equipo> equipos = model.obtenerTodosLosEquipos();

        materialesPorInstitucion.clear();
        Map<Integer, InstitucionAcumulador> instituciones = new LinkedHashMap<>();

        for (Equipo equipo : equipos) {
            if (!equipoEsEntregable(equipo)) {
                continue;
            }

            List<MaterialEntregaItem> materiales = construirMateriales(equipo);
            if (materiales.isEmpty()) {
                continue;
            }

            int institucionId = equipo.getNroInstitucion() != null ? equipo.getNroInstitucion() : -1;
            String institucionNombre = equipo.getInstitucionNombre();
            final String nombreFinal = (institucionNombre == null || institucionNombre.isBlank())
                ? Constantes.Textos.SIN_INSTITUCION
                : institucionNombre;

            InstitucionAcumulador acumulador = instituciones.computeIfAbsent(
                institucionId,
                id -> new InstitucionAcumulador(id, nombreFinal)
            );
            acumulador.agregarEquipo(equipo.getId());

            materialesPorInstitucion
                .computeIfAbsent(institucionId, id -> new ArrayList<>())
                .addAll(materiales);
        }

        List<InstitucionEntregaItem> filasInstituciones = instituciones.values().stream()
            .sorted(Comparator.comparing(InstitucionAcumulador::getNombre, String.CASE_INSENSITIVE_ORDER))
            .map(ac -> new InstitucionEntregaItem(ac.getId(), ac.getNombre(), ac.getEquiposCount()))
            .toList();

        panel.actualizarInstituciones(filasInstituciones);
        panel.limpiarMateriales();
    }

    private boolean equipoEsEntregable(Equipo equipo) {
        if (equipo == null) {
            return false;
        }
        return equipo.calcularEstado().getOrden() >= EstadoEquipo.ESTERILIZADO.getOrden();
    }

    private boolean materialEsEntregable(Material material) {
        if (material == null) {
            return false;
        }
        return material.getEstado().getOrden() >= EstadoEquipo.ESTERILIZADO.getOrden();
    }

    private List<MaterialEntregaItem> construirMateriales(Equipo equipo) {
        List<MaterialEntregaItem> materiales = new ArrayList<>();
        if (equipo == null || equipo.getMateriales() == null) {
            return materiales;
        }

        // Agrupar materiales por código, sumando cantidades
        Map<Integer, MaterialAgrupado> agrupados = new LinkedHashMap<>();
        
        for (Material material : equipo.getMateriales()) {
            if (!materialEsEntregable(material)) {
                continue;
            }
            
            int codigo = material.getCodigo();
            boolean entregado = material.getEstado() == EstadoEquipo.ENTREGADO;
            
            MaterialAgrupado agrupado = agrupados.get(codigo);
            if (agrupado == null) {
                agrupado = new MaterialAgrupado(material.getDescripcion());
                agrupados.put(codigo, agrupado);
            }
            
            agrupado.agregar(material.getCantidad(), entregado);
        }
        
        // Convertir a items para la tabla
        for (MaterialAgrupado agrupado : agrupados.values()) {
            MaterialEntregaItem item = new MaterialEntregaItem(
                agrupado.getDescripcion(),
                agrupado.getCantidadTotal(),
                agrupado.todosEntregados()
            );
            materiales.add(item);
        }
        
        return materiales;
    }

    private void entregarInstitucion() {
        InstitucionEntregaItem institucionSeleccionada = panel.getInstitucionSeleccionada();
        
        if (institucionSeleccionada == null) {
            panel.mostrarAdvertencia("Debe seleccionar una institución antes de entregar.");
            return;
        }

        List<MaterialEntregaItem> todosMateriales = 
            materialesPorInstitucion.getOrDefault(institucionSeleccionada.getId(), List.of());

        List<MaterialEntregaItem> materialesPendientes = todosMateriales.stream()
            .filter(m -> !m.isEntregado())
            .toList();

        if (materialesPendientes.isEmpty()) {
            panel.mostrarAdvertencia(
                "La institución \"" + institucionSeleccionada.getNombre() + 
                "\" no tiene materiales pendientes de entrega.");
            return;
        }

        String mensaje = construirMensajeConfirmacion(institucionSeleccionada.getNombre(), materialesPendientes);
        
        boolean confirmado = panel.confirmar(
            mensaje, 
            "Confirmar Entrega de Institución"
        );

        if (!confirmado) {
            return;
        }

        boolean exitoso = model.getMaterialService().entregarInstitucionCompleta(institucionSeleccionada.getId());

        if (exitoso) {
            log.info("Institución {} entregada exitosamente, refrescando pantallas...", institucionSeleccionada.getNombre());
            panel.mostrarInfo(
                "Institución \"" + institucionSeleccionada.getNombre() + 
                "\" entregada correctamente."
            );
            cargarDatos();
            
            // Notificar a otras pantallas para que se actualicen
            if (onEstadosActualizadosListener != null) {
                log.info("Ejecutando listener para refrescar otras pantallas");
                onEstadosActualizadosListener.onEstadosActualizados();
            } else {
                log.warn("onEstadosActualizadosListener es null, otras pantallas NO se refrescarán");
            }
        } else {
            panel.mostrarError(
                "Error al entregar la institución \"" + institucionSeleccionada.getNombre() + 
                "\". Por favor intente nuevamente."
            );
        }
    }

    private String construirMensajeConfirmacion(String nombreInstitucion, List<MaterialEntregaItem> materiales) {
        StringBuilder sb = new StringBuilder();
        sb.append("¿Confirmar entrega de los siguientes materiales?\n\n");
        sb.append("Institución: ").append(nombreInstitucion).append("\n\n");
        
        Map<String, Integer> resumen = new LinkedHashMap<>();
        for (MaterialEntregaItem item : materiales) {
            resumen.merge(item.getMaterial(), item.getCantidad(), Integer::sum);
        }

        for (Map.Entry<String, Integer> entry : resumen.entrySet()) {
            sb.append("  • ").append(entry.getKey()).append(" x ").append(entry.getValue()).append("\n");
        }

        return sb.toString();
    }

}
