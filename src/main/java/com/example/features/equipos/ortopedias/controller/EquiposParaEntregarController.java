package com.example.features.equipos.ortopedias.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.ui.events.OnEstadosActualizadosListener;
import com.example.common.constants.Constantes;
import com.example.common.model.EntregaDestinoKey;
import com.example.common.model.EntregaDestinoKey.TipoDestino;
import com.example.app.AppModel;
import com.example.features.equipos.ortopedias.controller.helpers.InstitucionAcumulador;
import com.example.features.equipos.ortopedias.controller.helpers.MaterialAgrupado;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.model.Material;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.MaterialOtros;
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
    private final AppModel model;
    private final Map<EntregaDestinoKey, List<MaterialEntregaItem>> materialesPorDestino  = new HashMap<>();
    private final Map<EntregaDestinoKey, Integer>                   volumenPorDestino     = new HashMap<>();
    private OnEstadosActualizadosListener onEstadosActualizadosListener;

    public EquiposParaEntregarController(PantallaEquiposParaEntregar panel, AppModel model,
                                         OnEstadosActualizadosListener onEstadosActualizadosListener) {
        this.panel = panel;
        this.model = model;
        this.onEstadosActualizadosListener = onEstadosActualizadosListener;
        inicializarEventos();
        cargarDatos();
        panel.addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent e) { cargarDatos(); }
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

    public void cargarDatos() {
        materialesPorDestino.clear();
        volumenPorDestino.clear();
        Map<EntregaDestinoKey, InstitucionAcumulador> destinos = new LinkedHashMap<>();

        // ── Ortopedia: agrupa por institución ──────────────────────────────────
        for (Equipo equipo : model.obtenerTodosLosEquipos()) {
            if (!equipoEsEntregable(equipo)) continue;

            List<MaterialEntregaItem> materiales = construirMateriales(equipo);
            if (materiales.isEmpty()) continue;

            int institucionId = equipo.getNroInstitucion() != null ? equipo.getNroInstitucion() : -1;
            String institucionNombre = equipo.getInstitucionNombre();
            final String nombreFinal = (institucionNombre == null || institucionNombre.isBlank())
                ? Constantes.Textos.SIN_INSTITUCION : institucionNombre;

            EntregaDestinoKey key = new EntregaDestinoKey(TipoDestino.INSTITUCION, institucionId);
            destinos.computeIfAbsent(key, k -> new InstitucionAcumulador(k, nombreFinal))
                    .agregarEquipo(equipo.getId());
            materialesPorDestino.computeIfAbsent(key, k -> new ArrayList<>()).addAll(materiales);
        }

        // ── EquipoOtros: agrupa por cliente ────────────────────────────────────
        for (EquipoOtros equipo : model.obtenerTodosLosEquiposOtros()) {
            if (equipo.calcularEstado().getOrden() < EstadoEquipo.ESTERILIZADO.getOrden()) continue;

            List<MaterialEntregaItem> materiales = construirMaterialesOtros(equipo);
            if (materiales.isEmpty()) continue;

            EntregaDestinoKey key = new EntregaDestinoKey(TipoDestino.CLIENTE, equipo.getNroCliente());
            String nombreCliente = equipo.getClienteNombre() != null ? equipo.getClienteNombre() : "Sin cliente";
            destinos.computeIfAbsent(key, k -> new InstitucionAcumulador(k, nombreCliente))
                    .agregarEquipo(equipo.getId());
            materialesPorDestino.computeIfAbsent(key, k -> new ArrayList<>()).addAll(materiales);
            volumenPorDestino.merge(key, equipo.getVolumenEquipo(), Integer::sum);
        }

        List<InstitucionEntregaItem> filasInstituciones = destinos.values().stream()
            .sorted(Comparator.comparing(InstitucionAcumulador::getNombre, String.CASE_INSENSITIVE_ORDER))
            .map(ac -> new InstitucionEntregaItem(ac.getKey(), ac.getNombre(), ac.getEquiposCount()))
            .toList();

        panel.actualizarInstituciones(filasInstituciones);
        panel.limpiarMateriales();
    }

    private List<MaterialEntregaItem> construirMaterialesOtros(EquipoOtros equipo) {
        List<MaterialEntregaItem> resultado = new ArrayList<>();
        List<MaterialOtros> mats = equipo.getMateriales();

        if (mats.isEmpty()) {
            // REMITO sin filas reales: una sola fila "Elementos"
            if (equipo.getRemitoCantidad() != null && equipo.getRemitoCantidad() > 0) {
                resultado.add(new MaterialEntregaItem("Elementos", equipo.getRemitoCantidad(), false));
            }
            return resultado;
        }

        // DETALLES o REMITO con filas reales: agrupar por descripción
        Map<String, int[]> agrupados = new LinkedHashMap<>();
        for (MaterialOtros m : mats) {
            if (m.getEstado().getOrden() < EstadoEquipo.ESTERILIZADO.getOrden()) continue;
            int[] contadores = agrupados.computeIfAbsent(m.getDescripcion(), k -> new int[2]);
            contadores[0] += m.getCantidad();
            if (m.getEstado() == EstadoEquipo.ENTREGADO) contadores[1] += m.getCantidad();
        }
        for (Map.Entry<String, int[]> e : agrupados.entrySet()) {
            int pendiente = e.getValue()[0] - e.getValue()[1];
            if (pendiente > 0) resultado.add(new MaterialEntregaItem(e.getKey(), pendiente, false));
        }
        return resultado;
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
        
        // Convertir a items para la tabla, omitiendo los que ya fueron entregados en su totalidad
        for (MaterialAgrupado agrupado : agrupados.values()) {
            if (agrupado.todosEntregados()) {
                continue;
            }
            MaterialEntregaItem item = new MaterialEntregaItem(
                agrupado.getDescripcion(),
                agrupado.getCantidadTotal() - agrupado.getCantidadEntregada(),
                false
            );
            materiales.add(item);
        }
        
        return materiales;
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

        List<String> exitosas = new ArrayList<>();
        List<String> errores = new ArrayList<>();

        for (Map.Entry<InstitucionEntregaItem, List<MaterialEntregaItem>> entry : materialesPorInstitucion.entrySet()) {
            InstitucionEntregaItem institucion = entry.getKey();
            EntregaDestinoKey key = institucion.getKey();
            boolean exitoso = key.getTipo() == TipoDestino.CLIENTE
                ? model.entregarClienteOtrosCompleto(key.getId())
                : model.entregarInstitucionCompleta(key.getId());
            if (exitoso) exitosas.add(institucion.getNombre());
            else errores.add(institucion.getNombre());
        }

        if (!exitosas.isEmpty()) {
            log.info("{} institución(es) entregada(s) exitosamente", exitosas.size());
            cargarDatos();
            if (onEstadosActualizadosListener != null) {
                onEstadosActualizadosListener.onEstadosActualizados();
            } else {
                log.warn("onEstadosActualizadosListener es null, otras pantallas NO se refrescarán");
            }
            String texto = exitosas.size() == 1
                ? "\"" + exitosas.get(0) + "\" entregada correctamente."
                : exitosas.size() + " instituciones entregadas correctamente.";
            panel.mostrarInfo(texto);
        }
        if (!errores.isEmpty()) {
            panel.mostrarError("No se pudieron entregar: " + String.join(", ", errores));
        }
    }

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