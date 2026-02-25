package com.example.features.lotes.controller;

import com.example.common.constants.Constantes;
import com.example.features.lotes.controller.helpers.MaterialLoteTransferable;
import com.example.ui.events.OnEstadosActualizadosListener;
import com.example.app.AppModel;
import com.example.features.autoclaves.model.Autoclave;
import com.example.features.equipos.model.Equipo;
import com.example.features.equipos.model.EstadoEquipo;
import com.example.features.lotes.model.Lote;
import com.example.features.lotes.model.LoteMaterialInfo;
import com.example.features.lotes.model.LoteMovimiento;
import com.example.features.equipos.model.Material;
import com.example.features.lotes.view.PantallaLotes;
import com.example.ui.dialogs.CantidadDialogHelper;
import com.example.features.lotes.view.helpers.AutoclaveItem;
import com.example.features.lotes.view.helpers.MaterialLoteItem;
import com.example.features.lotes.view.helpers.PanelLotesContenido;

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LotesController {

    private final PanelLotesContenido panel;
    private final AppModel model;
    private final Equipo equipoContexto;  // null = todos los equipos, non-null = solo este
    private OnEstadosActualizadosListener onEstadosActualizadosListener;

    private final Map<String, List<MaterialLoteItem>> pendientesPorAutoclave = new HashMap<>();
    private final Map<String, Lote> lotesActivos = new HashMap<>();
    private List<MaterialLoteItem> materialesDisponibles = new ArrayList<>();
    private Map<Integer, Integer> volumenesCatalogo = new HashMap<>();
    private AutoclaveItem autoclaveSeleccionado;
    
    // DataFlavor personalizado para transferir MaterialLoteItem en la misma JVM
    public static final DataFlavor MATERIAL_LOTE_FLAVOR;
    
    static {
        DataFlavor flavor = null;
        try {
            flavor = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + 
                ";class=\"" + MaterialLoteItem.class.getName() + "\"");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        MATERIAL_LOTE_FLAVOR = flavor;
    }

    /**
     * Constructor para PantallaLotes (pantalla completa, sin contexto de equipo).
     */
    public LotesController(PantallaLotes pantallaLotes, AppModel model, OnEstadosActualizadosListener listener) {
        this(pantallaLotes.getPanelContenido(), model, null, listener);
    }

    /**
     * Constructor para PanelLotesContenido embebido con contexto de equipo.
     * @param panel Panel reusable para gestión de lotes
     * @param model Modelo de datos
     * @param equipoContexto Equipo específico (null = todos los equipos del sistema)
     * @param listener Listener para notificaciones
     */
    public LotesController(PanelLotesContenido panel, AppModel model, Equipo equipoContexto, OnEstadosActualizadosListener listener) {
        this.panel = panel;
        this.model = model;
        this.equipoContexto = equipoContexto;
        this.onEstadosActualizadosListener = listener;

        inicializarEventos();
        cargarDatos();
    }

    public void setOnEstadosActualizados(OnEstadosActualizadosListener listener) {
        this.onEstadosActualizadosListener = listener;
    }

    private void inicializarEventos() {
        panel.setOnAutoclaveSeleccionado(this::onAutoclaveSeleccionado);
        panel.setOnLanzar(e -> lanzarLote());
        panel.setOnFinalizar(e -> finalizarLote());
        panel.setOnQuitar(e -> quitarMaterial());

        // Configurar DnD después de que el componente esté visible y con tamaño
        panel.addComponentListener(new java.awt.event.ComponentAdapter() {
            private boolean dndConfigurado = false;
            
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (!dndConfigurado) {
                    SwingUtilities.invokeLater(() -> {
                        configurarDnD();
                        dndConfigurado = true;
                    });
                }
            }
            
            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                if (!dndConfigurado) {
                    SwingUtilities.invokeLater(() -> {
                        configurarDnD();
                        dndConfigurado = true;
                    });
                }
            }
        });
    }

    public void cargarDatos() {
        String autoclaveSeleccion = autoclaveSeleccionado != null ? autoclaveSeleccionado.getNombre() : null;
        volumenesCatalogo = model.obtenerVolumenesCatalogo();
        List<Autoclave> autoclaves = model.obtenerAutoclaves();
        lotesActivos.clear();
        lotesActivos.putAll(model.obtenerLotesActivosPorAutoclave());

        materialesDisponibles = construirMaterialesDisponibles();
        aplicarPendientesEnDisponibles();

        List<AutoclaveItem> items = new ArrayList<>();
        for (Autoclave autoclave : autoclaves) {
            Lote loteActivo = lotesActivos.get(autoclave.getNombre());
            boolean ocupado = loteActivo != null;
            int capacidadUsada = ocupado ? loteActivo.getCapacidadUsada() : calcularCapacidadPendiente(autoclave.getNombre());
            Integer loteId = ocupado ? loteActivo.getId() : null;
            items.add(new AutoclaveItem(
                autoclave.getNombre(),
                autoclave.getCapacidad(),
                ocupado,
                loteId,
                capacidadUsada
            ));
        }

        // Ordenar: primero libres, luego alfabético
        items.sort((a, b) -> {
            if (a.isOcupado() != b.isOcupado()) {
                return Boolean.compare(a.isOcupado(), b.isOcupado()); // false < true (libres primero)
            }
            return a.getNombre().compareTo(b.getNombre()); // Alfabético
        });

        panel.setAutoclaves(items);
        if (autoclaveSeleccion != null) {
            panel.seleccionarAutoclave(autoclaveSeleccion);
        }
        panel.setMaterialesDisponibles(materialesDisponibles);
    }

    private List<MaterialLoteItem> construirMaterialesDisponibles() {
        List<MaterialLoteItem> disponibles = new ArrayList<>();

        // Si hay contexto de equipo, procesar solo ese equipo
        if (equipoContexto != null) {
            if (equipoContexto.getMateriales() == null) {
                return disponibles;
            }
            for (Material material : equipoContexto.getMateriales()) {
                EstadoEquipo siguiente = equipoContexto.getSiguienteEstado(material.getEstado());
                if (siguiente != EstadoEquipo.ESTERILIZANDO) {
                    continue;
                }
                Integer volumen = volumenesCatalogo.get(material.getCodigo());
                int volumenUnitario = volumen != null ? volumen : 1;
                disponibles.add(new MaterialLoteItem(
                    material.getId(),
                    equipoContexto.getId(),
                    material.getDescripcion(),
                    material.getCantidad(),
                    volumenUnitario
                ));
            }
            return disponibles;
        }

        // Sin contexto: procesar todos los equipos (comportamiento original)
        List<Equipo> equipos = model.obtenerTodosLosEquipos();

        for (Equipo equipo : equipos) {
            if (equipo.getMateriales() == null) {
                continue;
            }
            for (Material material : equipo.getMateriales()) {
                EstadoEquipo siguiente = equipo.getSiguienteEstado(material.getEstado());
                if (siguiente != EstadoEquipo.ESTERILIZANDO) {
                    continue;
                }
                Integer volumen = volumenesCatalogo.get(material.getCodigo());
                int volumenUnitario = volumen != null ? volumen : 1;
                disponibles.add(new MaterialLoteItem(
                    material.getId(),
                    equipo.getId(),
                    material.getDescripcion(),
                    material.getCantidad(),
                    volumenUnitario
                ));
            }
        }

        return disponibles;
    }

    private void aplicarPendientesEnDisponibles() {
        Map<Integer, MaterialLoteItem> disponiblesPorId = new LinkedHashMap<>();
        for (MaterialLoteItem item : materialesDisponibles) {
            disponiblesPorId.put(item.getMaterialId(), item);
        }

        for (List<MaterialLoteItem> pendientes : pendientesPorAutoclave.values()) {
            for (MaterialLoteItem pendiente : pendientes) {
                MaterialLoteItem disponible = disponiblesPorId.get(pendiente.getMaterialId());
                if (disponible == null) {
                    continue;
                }
                int restante = disponible.getCantidad() - pendiente.getCantidad();
                if (restante <= 0) {
                    disponiblesPorId.remove(pendiente.getMaterialId());
                } else {
                    disponible.setCantidad(restante);
                }
            }
        }

        materialesDisponibles = new ArrayList<>(disponiblesPorId.values());
    }

    private void onAutoclaveSeleccionado(AutoclaveItem autoclave) {
        autoclaveSeleccionado = autoclave;
        if (autoclave == null) {
            panel.setMaterialesAutoclave(List.of());
            panel.setCapacidadTexto(Constantes.Textos.CAPACIDAD_AUTOCLAVE);
            panel.setLanzarEnabled(false);
            panel.setFinalizarEnabled(false);
            panel.setQuitarEnabled(false);
            return;
        }

        if (autoclave.isOcupado()) {
            Lote lote = lotesActivos.get(autoclave.getNombre());
            List<LoteMaterialInfo> materialesLote = lote != null ? lote.getMateriales() : new ArrayList<>();
            List<MaterialLoteItem> items = new ArrayList<>();
            for (LoteMaterialInfo info : materialesLote) {
                items.add(new MaterialLoteItem(
                    info.getMaterialId(),
                    info.getEquipoId(),
                    info.getDescripcion(),
                    info.getCantidad(),
                    info.getVolumen()
                ));
            }
            panel.setMaterialesAutoclave(items);
            panel.setCapacidad(autoclave.getCapacidadUsada(), autoclave.getCapacidad());
            panel.setLanzarEnabled(false);
            panel.setFinalizarEnabled(true);
            panel.setQuitarEnabled(false);
        } else {
            List<MaterialLoteItem> pendientes = pendientesPorAutoclave.getOrDefault(autoclave.getNombre(), List.of());
            panel.setMaterialesAutoclave(pendientes);
            int usada = calcularCapacidad(pendientes);
            panel.setCapacidadTexto(String.format("Capacidad: %d/%d", usada, autoclave.getCapacidad()));
            panel.setLanzarEnabled(!pendientes.isEmpty());
            panel.setFinalizarEnabled(false);
            panel.setQuitarEnabled(!pendientes.isEmpty());
        }
    }

    private void configurarDnD() {
        JTable tablaDisponibles = panel.getTablaDisponibles();
        JTable tablaAutoclave = panel.getTablaAutoclave();

        if (tablaDisponibles == null || tablaAutoclave == null || MATERIAL_LOTE_FLAVOR == null) {
            return;
        }

        // Tabla Disponibles: ORIGEN para drag (copiar a autoclave)
        TransferHandler handlerDisponibles = new TransferHandler() {
            @Override
            public int getSourceActions(JComponent c) {
                return COPY;
            }

            @Override
            protected Transferable createTransferable(JComponent c) {
                MaterialLoteItem item = panel.getMaterialDisponibleSeleccionado();
                if (item == null) {
                    return null;
                }
                return new MaterialLoteTransferable(item, MATERIAL_LOTE_FLAVOR);
            }

            @Override
            public boolean canImport(TransferSupport support) {
                if (!support.isDrop()) {
                    return false;
                }
                boolean hasCorrectFlavor = support.isDataFlavorSupported(MATERIAL_LOTE_FLAVOR);
                support.setShowDropLocation(hasCorrectFlavor);
                return hasCorrectFlavor;
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) {
                    return false;
                }
                try {
                    MaterialLoteItem item = (MaterialLoteItem) support.getTransferable().getTransferData(MATERIAL_LOTE_FLAVOR);
                    quitarMaterialDePendientes(item);
                    cargarDatos();
                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            }
        };
        
        tablaDisponibles.setDragEnabled(true);
        tablaDisponibles.setDropMode(DropMode.ON);
        tablaDisponibles.setFillsViewportHeight(true);
        tablaDisponibles.setTransferHandler(handlerDisponibles);

        // Tabla Autoclave: DESTINO para drop (recibir de disponibles o devolver a disponibles)
        TransferHandler handlerAutoclave = new TransferHandler() {
            @Override
            public int getSourceActions(JComponent c) {
                return MOVE;
            }

            @Override
            protected Transferable createTransferable(JComponent c) {
                MaterialLoteItem item = panel.getMaterialAutoclaveSeleccionado();
                if (item == null) {
                    return null;
                }
                return new MaterialLoteTransferable(item, MATERIAL_LOTE_FLAVOR);
            }
            
            @Override
            protected void exportDone(JComponent source, Transferable data, int action) {
                if (action == MOVE) {
                    SwingUtilities.invokeLater(() -> cargarDatos());
                }
            }

            @Override
            public boolean canImport(TransferSupport support) {
                if (!support.isDrop()) {
                    return false;
                }
                
                if (!support.isDataFlavorSupported(MATERIAL_LOTE_FLAVOR)) {
                    return false;
                }
                
                if (autoclaveSeleccionado == null || autoclaveSeleccionado.isOcupado()) {
                    return false;
                }
                
                support.setShowDropLocation(true);
                return true;
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) {
                    return false;
                }

                if (autoclaveSeleccionado == null) {
                    SwingUtilities.invokeLater(() -> 
                        panel.mostrarAdvertencia("Debe seleccionar un autoclave primero."));
                    return false;
                }

                if (autoclaveSeleccionado.isOcupado()) {
                    SwingUtilities.invokeLater(() -> 
                        panel.mostrarAdvertencia("Este autoclave ya tiene un lote en progreso."));
                    return false;
                }

                try {
                    MaterialLoteItem item = (MaterialLoteItem) support.getTransferable().getTransferData(MATERIAL_LOTE_FLAVOR);
                    SwingUtilities.invokeLater(() -> agregarMaterial(item));
                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                    SwingUtilities.invokeLater(() ->
                        panel.mostrarAdvertencia("Error: " + e.getMessage()));
                    return false;
                }
            }
        };
        
        tablaAutoclave.setDragEnabled(true);
        tablaAutoclave.setDropMode(DropMode.ON);
        tablaAutoclave.setFillsViewportHeight(true);
        tablaAutoclave.setTransferHandler(handlerAutoclave);
    }

    private void agregarMaterial(MaterialLoteItem item) {
        if (item == null || autoclaveSeleccionado == null) {
            return;
        }

        Integer cantidadElegida = CantidadDialogHelper.pedirCantidad(
            panel, 
            item.getDescripcion(), 
            item.getCantidad(),
            (chkTodos, spinner) -> chkTodos.addActionListener(e -> {
                if (chkTodos.isSelected()) {
                    spinner.setValue(item.getCantidad());
                    spinner.setEnabled(false);
                } else {
                    spinner.setEnabled(true);
                }
            })
        );
        
        if (cantidadElegida == null) {
            return;
        }

        int volumenNecesario = cantidadElegida * item.getVolumen();
        int capacidadUsada = calcularCapacidadPendiente(autoclaveSeleccionado.getNombre());
        if (capacidadUsada + volumenNecesario > autoclaveSeleccionado.getCapacidad()) {
            panel.mostrarAdvertencia("No se puede superar la capacidad del autoclave.");
            return;
        }

        ajustarDisponibles(item, cantidadElegida);
        agregarPendiente(autoclaveSeleccionado.getNombre(), item, cantidadElegida);

        cargarDatos();
    }

    private void ajustarDisponibles(MaterialLoteItem item, int cantidad) {
        MaterialLoteItem encontrado = null;
        for (MaterialLoteItem disponible : materialesDisponibles) {
            if (disponible.getMaterialId() == item.getMaterialId()) {
                encontrado = disponible;
                break;
            }
        }
        if (encontrado == null) {
            return;
        }
        int restante = encontrado.getCantidad() - cantidad;
        if (restante <= 0) {
            materialesDisponibles.remove(encontrado);
        } else {
            encontrado.setCantidad(restante);
        }
    }

    private void agregarPendiente(String autoclaveNombre, MaterialLoteItem item, int cantidad) {
        List<MaterialLoteItem> pendientes = pendientesPorAutoclave.computeIfAbsent(autoclaveNombre, k -> new ArrayList<>());
        for (MaterialLoteItem existente : pendientes) {
            if (existente.getMaterialId() == item.getMaterialId()) {
                existente.setCantidad(existente.getCantidad() + cantidad);
                return;
            }
        }
        pendientes.add(new MaterialLoteItem(
            item.getMaterialId(),
            item.getEquipoId(),
            item.getDescripcion(),
            cantidad,
            item.getVolumen()
        ));
    }

    private void quitarMaterial() {
        if (autoclaveSeleccionado == null || autoclaveSeleccionado.isOcupado()) {
            return;
        }

        MaterialLoteItem seleccionado = panel.getMaterialAutoclaveSeleccionado();
        if (seleccionado == null) {
            panel.mostrarAdvertencia("Seleccione un material para quitar.");
            return;
        }
        quitarMaterialDePendientes(seleccionado);
    }

    private void quitarMaterialDePendientes(MaterialLoteItem seleccionado) {
        if (autoclaveSeleccionado == null) {
            return;
        }

        List<MaterialLoteItem> pendientes = pendientesPorAutoclave.getOrDefault(autoclaveSeleccionado.getNombre(), new ArrayList<>());
        pendientes.removeIf(item -> item.getMaterialId() == seleccionado.getMaterialId());
        pendientesPorAutoclave.put(autoclaveSeleccionado.getNombre(), pendientes);

        // Devolver a disponibles
        boolean encontrado = false;
        for (MaterialLoteItem disponible : materialesDisponibles) {
            if (disponible.getMaterialId() == seleccionado.getMaterialId()) {
                disponible.setCantidad(disponible.getCantidad() + seleccionado.getCantidad());
                encontrado = true;
                break;
            }
        }
        if (!encontrado) {
            materialesDisponibles.add(new MaterialLoteItem(
                seleccionado.getMaterialId(),
                seleccionado.getEquipoId(),
                seleccionado.getDescripcion(),
                seleccionado.getCantidad(),
                seleccionado.getVolumen()
            ));
        }

        cargarDatos();
    }

    private void lanzarLote() {
        if (autoclaveSeleccionado == null || autoclaveSeleccionado.isOcupado()) {
            return;
        }

        List<MaterialLoteItem> pendientes = pendientesPorAutoclave.getOrDefault(autoclaveSeleccionado.getNombre(), List.of());
        if (pendientes.isEmpty()) {
            panel.mostrarAdvertencia("Debe cargar materiales antes de lanzar el lote.");
            return;
        }

        int capacidadUsada = calcularCapacidad(pendientes);
        double porcentaje = autoclaveSeleccionado.getCapacidad() == 0 ? 0 : (double) capacidadUsada / autoclaveSeleccionado.getCapacidad();
        
        // Construir mensaje de confirmación con lista de materiales
        StringBuilder mensaje = new StringBuilder();
        mensaje.append("Se lanzará el lote con los siguientes materiales:\n\n");
        for (MaterialLoteItem item : pendientes) {
            mensaje.append(String.format("• %s (x%d)\n", item.getDescripcion(), item.getCantidad()));
        }
        mensaje.append(String.format("\nCapacidad utilizada: %d/%d (%.0f%%)\n", 
            capacidadUsada, autoclaveSeleccionado.getCapacidad(), porcentaje * 100));
        
        if (porcentaje < 0.8) {
            mensaje.append("\n⚠ El autoclave tiene menos del 80% de capacidad.");
        }
        
        mensaje.append("\n¿Desea continuar?");
        
        boolean confirmar = panel.confirmar(
            mensaje.toString(),
            "Confirmar Lanzamiento de Lote"
        );
        
        if (!confirmar) {
            return;
        }

        List<LoteMovimiento> movimientos = new ArrayList<>();
        for (MaterialLoteItem item : pendientes) {
            movimientos.add(new LoteMovimiento(item.getMaterialId(), item.getEquipoId(), item.getCantidad()));
        }

        Lote lote = model.lanzarLote(autoclaveSeleccionado.getNombre(),
            autoclaveSeleccionado.getCapacidad(), capacidadUsada, movimientos);

        if (lote == null) {
            panel.mostrarError("Error al lanzar el lote.");
            return;
        }

        pendientesPorAutoclave.remove(autoclaveSeleccionado.getNombre());
        cargarDatos();

        if (onEstadosActualizadosListener != null) {
            onEstadosActualizadosListener.onEstadosActualizados();
        }
    }

    private void finalizarLote() {
        if (autoclaveSeleccionado == null || !autoclaveSeleccionado.isOcupado()) {
            return;
        }

        boolean confirmar = panel.confirmar(
            "¿Confirmar finalización del lote?",
            Constantes.Mensajes.TITULO_CONFIRMAR_CAMBIOS
        );
        if (!confirmar) {
            return;
        }

        boolean exitoso = model.finalizarLote(autoclaveSeleccionado.getLoteId());
        if (!exitoso) {
            panel.mostrarError("Error al finalizar el lote.");
            return;
        }

        cargarDatos();

        if (onEstadosActualizadosListener != null) {
            onEstadosActualizadosListener.onEstadosActualizados();
        }
    }

    private int calcularCapacidad(List<MaterialLoteItem> materiales) {
        int total = 0;
        for (MaterialLoteItem item : materiales) {
            total += item.getVolumenTotal();
        }
        return total;
    }

    private int calcularCapacidadPendiente(String autoclaveNombre) {
        List<MaterialLoteItem> pendientes = pendientesPorAutoclave.getOrDefault(autoclaveNombre, List.of());
        return calcularCapacidad(pendientes);
    }

}


