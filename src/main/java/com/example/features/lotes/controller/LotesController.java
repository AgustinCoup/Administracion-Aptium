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

    /**
     * Mapa equipoId → clienteNombre, construido en cargarDatos().
     * Se usa para poblar la columna "Cliente" en ambas tablas de materiales.
     */
    private Map<Integer, String> clientesPorEquipo = new HashMap<>();

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

        // Bloquear navegación si hay materiales cargados en algún autoclave sin lanzar
        pantallaLotes.setGuardVolver(
            this::tieneCambiosPendientes,
            "Tenés materiales cargados en un equipo de esterilización sin lanzar.\nSi volvés ahora, esos cambios se perderán.\n¿Querés salir de todas formas?"
        );
    }

    /**
     * Constructor para PanelLotesContenido embebido con contexto de equipo.
     *
     * @param panel          Panel reusable para gestión de lotes
     * @param model          Modelo de datos
     * @param equipoContexto Equipo específico (null = todos los equipos del sistema)
     * @param listener       Listener para notificaciones
     */
    public LotesController(PanelLotesContenido panel, AppModel model, Equipo equipoContexto,
                           OnEstadosActualizadosListener listener) {
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

        // Actualizar el estado del botón Lanzar en tiempo real cuando el usuario
        // modifica el campo de volumen manual.
        panel.setOnVolumenManualChanged(this::actualizarBotonLanzarPorVolumen);

        // Configurar DnD después de que el componente esté visible y con tamaño
        panel.addComponentListener(new java.awt.event.ComponentAdapter() {
            private boolean dndConfigurado = false;

            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (!dndConfigurado) {
                    SwingUtilities.invokeLater(() -> { configurarDnD(); dndConfigurado = true; });
                }
            }

            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                if (!dndConfigurado) {
                    SwingUtilities.invokeLater(() -> { configurarDnD(); dndConfigurado = true; });
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

        // Construir mapa equipoId → clienteNombre a partir de todos los equipos cargados.
        // NOTA: se asume que Equipo expone getClienteNombre(). Si el método tiene otro
        //       nombre en tu modelo (ej. getCliente()), cambiá solo esa línea.
        clientesPorEquipo.clear();
        if (equipoContexto != null) {
            clientesPorEquipo.put(equipoContexto.getId(), equipoContexto.getClienteNombre());
        } else {
            for (Equipo eq : model.obtenerTodosLosEquipos()) {
                clientesPorEquipo.put(eq.getId(), eq.getClienteNombre());
            }
        }

        materialesDisponibles = construirMaterialesDisponibles();
        aplicarPendientesEnDisponibles();

        List<AutoclaveItem> items = new ArrayList<>();
        for (Autoclave autoclave : autoclaves) {
            Lote loteActivo = lotesActivos.get(autoclave.getNombre());
            boolean ocupado = loteActivo != null;
            int capacidadUsada = ocupado
                    ? loteActivo.getCapacidadUsada()
                    : calcularCapacidadPendiente(autoclave.getNombre());
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
            if (a.isOcupado() != b.isOcupado())
                return Boolean.compare(a.isOcupado(), b.isOcupado());
            return a.getNombre().compareTo(b.getNombre());
        });

        panel.setAutoclaves(items);
        if (autoclaveSeleccion != null) panel.seleccionarAutoclave(autoclaveSeleccion);
        panel.setMaterialesDisponibles(materialesDisponibles);
    }

    private List<MaterialLoteItem> construirMaterialesDisponibles() {
        List<MaterialLoteItem> disponibles = new ArrayList<>();

        if (equipoContexto != null) {
            if (equipoContexto.getMateriales() == null) return disponibles;
            String clienteNombre = clientesPorEquipo.getOrDefault(equipoContexto.getId(), "");
            for (Material material : equipoContexto.getMateriales()) {
                EstadoEquipo siguiente = equipoContexto.getSiguienteEstado(material.getEstado());
                if (siguiente != EstadoEquipo.ESTERILIZANDO) continue;
                Integer volumen = volumenesCatalogo.get(material.getCodigo());
                int volumenUnitario = volumen != null ? volumen : 1;
                disponibles.add(new MaterialLoteItem(
                        material.getId(),
                        equipoContexto.getId(),
                        material.getDescripcion(),
                        material.getCantidad(),
                        volumenUnitario,
                        clienteNombre
                ));
            }
            return disponibles;
        }

        List<Equipo> equipos = model.obtenerTodosLosEquipos();
        for (Equipo equipo : equipos) {
            if (equipo.getMateriales() == null) continue;
            String clienteNombre = clientesPorEquipo.getOrDefault(equipo.getId(), "");
            for (Material material : equipo.getMateriales()) {
                EstadoEquipo siguiente = equipo.getSiguienteEstado(material.getEstado());
                if (siguiente != EstadoEquipo.ESTERILIZANDO) continue;
                Integer volumen = volumenesCatalogo.get(material.getCodigo());
                int volumenUnitario = volumen != null ? volumen : 1;
                disponibles.add(new MaterialLoteItem(
                        material.getId(),
                        equipo.getId(),
                        material.getDescripcion(),
                        material.getCantidad(),
                        volumenUnitario,
                        clienteNombre
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
                if (disponible == null) continue;
                int restante = disponible.getCantidad() - pendiente.getCantidad();
                if (restante <= 0) disponiblesPorId.remove(pendiente.getMaterialId());
                else disponible.setCantidad(restante);
            }
        }

        materialesDisponibles = new ArrayList<>(disponiblesPorId.values());
    }

    private void onAutoclaveSeleccionado(AutoclaveItem autoclave) {
        autoclaveSeleccionado = autoclave;
        if (autoclave == null) {
            panel.setMaterialesAutoclave(List.of());
            panel.setCapacidadTexto(Constantes.Textos.CAPACIDAD_AUTOCLAVE);
            panel.setVolumenManualEnabled(false);
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
                String clienteNombre = clientesPorEquipo.getOrDefault(info.getEquipoId(), "");
                items.add(new MaterialLoteItem(
                        info.getMaterialId(),
                        info.getEquipoId(),
                        info.getDescripcion(),
                        info.getCantidad(),
                        info.getVolumen(),
                        clienteNombre
                ));
            }
            panel.setMaterialesAutoclave(items);
            panel.setCapacidad(autoclave.getCapacidadUsada(), autoclave.getCapacidad());
            panel.setVolumenManualEnabled(false);
            panel.setLanzarEnabled(false);
            panel.setFinalizarEnabled(true);
            panel.setQuitarEnabled(false);
        } else {
            List<MaterialLoteItem> pendientes = pendientesPorAutoclave.getOrDefault(autoclave.getNombre(), List.of());
            panel.setMaterialesAutoclave(pendientes);
            int usada = calcularCapacidad(pendientes);
            panel.setCapacidadTexto(String.format("Capacidad: %d/%d", usada, autoclave.getCapacidad()));

            panel.setVolumenCalculado(usada);
            panel.setVolumenManualEnabled(!pendientes.isEmpty());

            boolean hayPendientes = !pendientes.isEmpty();
            panel.setLanzarEnabled(hayPendientes && volumenManualDentroDeCapacidad(autoclave));
            panel.setFinalizarEnabled(false);
            panel.setQuitarEnabled(hayPendientes);
        }
    }

    /**
     * Actualiza el estado del botón Lanzar según el volumen manual ingresado.
     * Se invoca en tiempo real via el listener del campo de texto.
     */
    private void actualizarBotonLanzarPorVolumen() {
        if (autoclaveSeleccionado == null || autoclaveSeleccionado.isOcupado()) return;
        List<MaterialLoteItem> pendientes = pendientesPorAutoclave.getOrDefault(
                autoclaveSeleccionado.getNombre(), List.of());
        boolean hayPendientes = !pendientes.isEmpty();
        panel.setLanzarEnabled(hayPendientes && volumenManualDentroDeCapacidad(autoclaveSeleccionado));
    }

    /**
     * Retorna true si el volumen manual es válido (>= 0 y <= capacidad del autoclave).
     */
    private boolean volumenManualDentroDeCapacidad(AutoclaveItem autoclave) {
        int volumenManual = panel.getVolumenManual();
        if (volumenManual < 0) return false;
        return volumenManual <= autoclave.getCapacidad();
    }

    private void configurarDnD() {
        JTable tablaDisponibles = panel.getTablaDisponibles();
        JTable tablaAutoclave   = panel.getTablaAutoclave();

        if (tablaDisponibles == null || tablaAutoclave == null || MATERIAL_LOTE_FLAVOR == null) return;

        // Tabla Disponibles: ORIGEN para drag
        TransferHandler handlerDisponibles = new TransferHandler() {
            @Override public int getSourceActions(JComponent c) { return COPY; }

            @Override
            protected Transferable createTransferable(JComponent c) {
                MaterialLoteItem item = panel.getMaterialDisponibleSeleccionado();
                return item == null ? null : new MaterialLoteTransferable(item, MATERIAL_LOTE_FLAVOR);
            }

            @Override
            public boolean canImport(TransferSupport support) {
                if (!support.isDrop()) return false;
                boolean ok = support.isDataFlavorSupported(MATERIAL_LOTE_FLAVOR);
                support.setShowDropLocation(ok);
                return ok;
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                try {
                    MaterialLoteItem item = (MaterialLoteItem) support.getTransferable().getTransferData(MATERIAL_LOTE_FLAVOR);
                    quitarMaterialDePendientes(item);
                    cargarDatos();
                    return true;
                } catch (Exception e) { e.printStackTrace(); return false; }
            }
        };

        tablaDisponibles.setDragEnabled(true);
        tablaDisponibles.setDropMode(DropMode.ON);
        tablaDisponibles.setFillsViewportHeight(true);
        tablaDisponibles.setTransferHandler(handlerDisponibles);

        // Tabla Autoclave: DESTINO para drop
        TransferHandler handlerAutoclave = new TransferHandler() {
            @Override public int getSourceActions(JComponent c) { return MOVE; }

            @Override
            protected Transferable createTransferable(JComponent c) {
                MaterialLoteItem item = panel.getMaterialAutoclaveSeleccionado();
                return item == null ? null : new MaterialLoteTransferable(item, MATERIAL_LOTE_FLAVOR);
            }

            @Override
            protected void exportDone(JComponent source, Transferable data, int action) {
                if (action == MOVE) SwingUtilities.invokeLater(() -> cargarDatos());
            }

            @Override
            public boolean canImport(TransferSupport support) {
                if (!support.isDrop()) return false;
                if (!support.isDataFlavorSupported(MATERIAL_LOTE_FLAVOR)) return false;
                if (autoclaveSeleccionado == null || autoclaveSeleccionado.isOcupado()) return false;
                support.setShowDropLocation(true);
                return true;
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                if (autoclaveSeleccionado == null) {
                    SwingUtilities.invokeLater(() -> panel.mostrarAdvertencia("Debe seleccionar un autoclave primero."));
                    return false;
                }
                if (autoclaveSeleccionado.isOcupado()) {
                    SwingUtilities.invokeLater(() -> panel.mostrarAdvertencia("Este autoclave ya tiene un lote en progreso."));
                    return false;
                }
                try {
                    MaterialLoteItem item = (MaterialLoteItem) support.getTransferable().getTransferData(MATERIAL_LOTE_FLAVOR);
                    SwingUtilities.invokeLater(() -> agregarMaterial(item));
                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                    SwingUtilities.invokeLater(() -> panel.mostrarAdvertencia("Error: " + e.getMessage()));
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
        if (item == null || autoclaveSeleccionado == null) return;

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

        if (cantidadElegida == null) return;

        int volumenNecesario = cantidadElegida * item.getVolumen();
        int capacidadUsada   = calcularCapacidadPendiente(autoclaveSeleccionado.getNombre());
        if (capacidadUsada + volumenNecesario > autoclaveSeleccionado.getCapacidad()) {
            panel.mostrarAdvertencia(
                    "El volumen calculado supera la capacidad del autoclave.\n" +
                    "Puede ajustar el volumen final en el campo \"Volumen final\" antes de lanzar.");
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
        if (encontrado == null) return;
        int restante = encontrado.getCantidad() - cantidad;
        if (restante <= 0) materialesDisponibles.remove(encontrado);
        else encontrado.setCantidad(restante);
    }

    private void agregarPendiente(String autoclaveNombre, MaterialLoteItem item, int cantidad) {
        List<MaterialLoteItem> pendientes = pendientesPorAutoclave
                .computeIfAbsent(autoclaveNombre, k -> new ArrayList<>());
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
                item.getVolumen(),
                item.getClienteNombre()
        ));
    }

    private void quitarMaterial() {
        if (autoclaveSeleccionado == null || autoclaveSeleccionado.isOcupado()) return;
        MaterialLoteItem seleccionado = panel.getMaterialAutoclaveSeleccionado();
        if (seleccionado == null) {
            panel.mostrarAdvertencia("Seleccione un material para quitar.");
            return;
        }
        quitarMaterialDePendientes(seleccionado);
    }

    private void quitarMaterialDePendientes(MaterialLoteItem seleccionado) {
        if (autoclaveSeleccionado == null) return;

        List<MaterialLoteItem> pendientes = pendientesPorAutoclave
                .getOrDefault(autoclaveSeleccionado.getNombre(), new ArrayList<>());
        pendientes.removeIf(item -> item.getMaterialId() == seleccionado.getMaterialId());
        pendientesPorAutoclave.put(autoclaveSeleccionado.getNombre(), pendientes);

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
                    seleccionado.getVolumen(),
                    seleccionado.getClienteNombre()
            ));
        }

        cargarDatos();
    }

    private void lanzarLote() {
        if (autoclaveSeleccionado == null || autoclaveSeleccionado.isOcupado()) return;

        List<MaterialLoteItem> pendientes = pendientesPorAutoclave
                .getOrDefault(autoclaveSeleccionado.getNombre(), List.of());
        if (pendientes.isEmpty()) {
            panel.mostrarAdvertencia("Debe cargar materiales antes de lanzar el lote.");
            return;
        }

        int volumenManual   = panel.getVolumenManual();
        int capacidadTotal  = autoclaveSeleccionado.getCapacidad();

        if (volumenManual < 0) {
            panel.mostrarError("El campo \"Volumen final\" contiene un valor inválido.\n" +
                    "Ingrese un número entero mayor a 0.");
            return;
        }

        if (volumenManual > capacidadTotal) {
            panel.mostrarError(String.format(
                    "El volumen final (%d) supera la capacidad del autoclave (%d).\n" +
                    "Ajuste el valor en el campo \"Volumen final\" antes de lanzar.",
                    volumenManual, capacidadTotal));
            return;
        }

        int volumenCalculado = calcularCapacidad(pendientes);
        double porcentaje    = capacidadTotal == 0 ? 0 : (double) volumenManual / capacidadTotal;

        StringBuilder mensaje = new StringBuilder();
        mensaje.append("Se lanzará el lote con los siguientes materiales:\n\n");
        for (MaterialLoteItem item : pendientes) {
            mensaje.append(String.format("• %s (x%d)\n", item.getDescripcion(), item.getCantidad()));
        }
        mensaje.append(String.format("\nVolumen calculado (catálogo): %d\n", volumenCalculado));
        mensaje.append(String.format("Volumen final confirmado:     %d/%d (%.0f%%)\n",
                volumenManual, capacidadTotal, porcentaje * 100));

        if (volumenManual != volumenCalculado)
            mensaje.append("\n⚠ El volumen fue ajustado manualmente respecto al catálogo.");
        if (porcentaje < 0.8)
            mensaje.append("\n⚠ El autoclave tiene menos del 80% de capacidad.");

        mensaje.append("\n\n¿Desea continuar?");

        if (!panel.confirmar(mensaje.toString(), "Confirmar Lanzamiento de Lote")) return;

        List<LoteMovimiento> movimientos = new ArrayList<>();
        for (MaterialLoteItem item : pendientes) {
            movimientos.add(new LoteMovimiento(item.getMaterialId(), item.getEquipoId(), item.getCantidad()));
        }

        Lote lote = model.lanzarLote(autoclaveSeleccionado.getNombre(),
                capacidadTotal, volumenManual, movimientos);

        if (lote == null) {
            panel.mostrarError("Error al lanzar el lote.");
            return;
        }

        pendientesPorAutoclave.remove(autoclaveSeleccionado.getNombre());
        cargarDatos();

        if (onEstadosActualizadosListener != null) onEstadosActualizadosListener.onEstadosActualizados();
    }

    private void finalizarLote() {
        if (autoclaveSeleccionado == null || !autoclaveSeleccionado.isOcupado()) return;

        if (!panel.confirmar("¿Confirmar finalización del lote?",
                Constantes.Mensajes.TITULO_CONFIRMAR_CAMBIOS)) return;

        boolean exitoso = model.finalizarLote(autoclaveSeleccionado.getLoteId());
        if (!exitoso) {
            panel.mostrarError("Error al finalizar el lote.");
            return;
        }

        cargarDatos();
        if (onEstadosActualizadosListener != null) onEstadosActualizadosListener.onEstadosActualizados();
    }

    private int calcularCapacidad(List<MaterialLoteItem> materiales) {
        int total = 0;
        for (MaterialLoteItem item : materiales) total += item.getVolumenTotal();
        return total;
    }

    private int calcularCapacidadPendiente(String autoclaveNombre) {
        return calcularCapacidad(pendientesPorAutoclave.getOrDefault(autoclaveNombre, List.of()));
    }

    /**
     * Retorna true si hay materiales cargados en al menos un autoclave que todavía
     * no fueron lanzados como lote. Se usa como guard del botón Volver.
     */
    public boolean tieneCambiosPendientes() {
        return pendientesPorAutoclave.values().stream().anyMatch(lista -> !lista.isEmpty());
    }
}