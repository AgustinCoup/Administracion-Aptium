package com.example.controller;

import com.example.constants.Constantes;
import com.example.controller.helpers.MaterialLoteTransferable;
import com.example.controller.listeners.OnEstadosActualizadosListener;
import com.example.model.AppModel;
import com.example.model.Autoclave;
import com.example.model.Equipo;
import com.example.model.EstadoEquipo;
import com.example.model.Lote;
import com.example.model.LoteMaterialInfo;
import com.example.model.LoteMovimiento;
import com.example.model.Material;
import com.example.view.PantallaLotes;
import com.example.view.dialogs.CantidadDialogHelper;
import com.example.view.helpers.AutoclaveItem;
import com.example.view.helpers.MaterialLoteItem;
import com.example.view.helpers.PanelLotesContenido;

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
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

    private static final DataFlavor MATERIAL_FLAVOR = new DataFlavor(
        DataFlavor.javaJVMLocalObjectMimeType + ";class=" + MaterialLoteItem.class.getName(),
        "MaterialLoteItem"
    );

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

        configurarDnD();
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

        // Tabla Disponibles: Origen para arrastrar a autoclave, Destino para returnar desde autoclave
        tablaDisponibles.setDragEnabled(true);
        tablaDisponibles.setDropMode(DropMode.ON);
        tablaDisponibles.setTransferHandler(new TransferHandler() {
            @Override
            protected Transferable createTransferable(JComponent c) {
                MaterialLoteItem item = panel.getMaterialDisponibleSeleccionado();
                if (item == null) {
                    return null;
                }
                return new MaterialLoteTransferable(item, MATERIAL_FLAVOR);
            }

            @Override
            public int getSourceActions(JComponent c) {
                return COPY;
            }

            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(MATERIAL_FLAVOR);
            }

            @Override
            public boolean importData(TransferSupport support) {
                // Esta tabla recibe materials que se están sacando de autoclass
                if (!canImport(support)) {
                    return false;
                }
                try {
                    Transferable t = support.getTransferable();
                    MaterialLoteItem item = (MaterialLoteItem) t.getTransferData(MATERIAL_FLAVOR);
                    quitarMaterialDePendientes(item);
                    return true;
                } catch (UnsupportedFlavorException | IOException e) {
                    return false;
                }
            }
        });

        // Tabla Autoclave: Destino para agregar materiales, Origen para removerlos
        tablaAutoclave.setDragEnabled(true);
        tablaAutoclave.setDropMode(DropMode.ON);
        tablaAutoclave.setTransferHandler(new TransferHandler() {
            @Override
            protected Transferable createTransferable(JComponent c) {
                MaterialLoteItem item = panel.getMaterialAutoclaveSeleccionado();
                if (item == null) {
                    return null;
                }
                return new MaterialLoteTransferable(item, MATERIAL_FLAVOR);
            }

            @Override
            public int getSourceActions(JComponent c) {
                return COPY;
            }

            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(MATERIAL_FLAVOR);
            }

            @Override
            public boolean importData(TransferSupport support) {
                // Esta tabla recibe materials nuevos de la tabla disponibles
                if (!canImport(support)) {
                    return false;
                }
                if (autoclaveSeleccionado == null || autoclaveSeleccionado.isOcupado()) {
                    panel.mostrarAdvertencia("Debe seleccionar un autoclave libre.");
                    return false;
                }
                try {
                    Transferable t = support.getTransferable();
                    MaterialLoteItem item = (MaterialLoteItem) t.getTransferData(MATERIAL_FLAVOR);
                    agregarMaterial(item);
                    return true;
                } catch (UnsupportedFlavorException | IOException e) {
                    return false;
                }
            }
        });
    }

    private void agregarMaterial(MaterialLoteItem item) {
        if (item == null || autoclaveSeleccionado == null) {
            return;
        }

        Integer cantidadElegida = CantidadDialogHelper.pedirCantidad(panel, item.getDescripcion(), item.getCantidad());
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
        if (porcentaje < 0.8) {
            boolean confirmar = panel.confirmar(
                "El autoclave tiene menos del 80% de capacidad cargada. ¿Desea continuar?",
                Constantes.Mensajes.TITULO_CONFIRMAR_CAMBIOS
            );
            if (!confirmar) {
                return;
            }
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
