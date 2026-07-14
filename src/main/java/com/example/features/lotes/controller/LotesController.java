package com.example.features.lotes.controller;

import com.example.common.constants.Constantes;
import com.example.features.lotes.controller.helpers.MaterialLoteTransferable;
import com.example.ui.events.OnEstadosActualizadosListener;
import com.example.app.AppModel;
import com.example.features.autoclaves.model.Autoclave;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.model.Material;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.MaterialOtros;
import com.example.features.equipos.otros.model.TipoIngresoOtros;
import com.example.features.lotes.model.Lote;
import com.example.features.lotes.model.LoteMaterialInfo;
import com.example.features.lotes.model.LoteMovimiento;
import com.example.features.lotes.view.PantallaLotes;
import com.example.ui.dialogs.CantidadDialogHelper;
import com.example.features.lotes.view.helpers.AutoclaveItem;
import com.example.features.lotes.view.helpers.MaterialLoteItem;
import com.example.features.lotes.view.helpers.PanelLotesContenido;

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LotesController {

    private static final Logger log = LoggerFactory.getLogger(LotesController.class);

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
     * Mapa equipoId → clienteNombre para ortopedia y otros respectivamente.
     * Se usan mapas separados para evitar colisiones de ID entre tablas distintas.
     */
    private Map<Integer, String> clientesPorEquipo      = new HashMap<>();
    private Map<Integer, String> clientesPorEquipoOtros = new HashMap<>();

    // DataFlavor personalizado para transferir MaterialLoteItem en la misma JVM
    public static final DataFlavor MATERIAL_LOTE_FLAVOR;

    static {
        DataFlavor flavor = null;
        try {
            flavor = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType +
                    ";class=\"" + MaterialLoteItem.class.getName() + "\"");
        } catch (ClassNotFoundException e) {
            log.error("No se pudo registrar el DataFlavor para drag-and-drop", e);
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
            Constantes.Mensajes.GUARD_LOTES_CAMBIOS,
            this::descartarCambiosPendientes
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
        panel.setOnMarcarFallo(e -> marcarLoteFallo());
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
        clientesPorEquipoOtros.clear();
        if (equipoContexto != null) {
            clientesPorEquipo.put(equipoContexto.getId(), equipoContexto.getClienteNombre());
        } else {
            for (Equipo eq : model.obtenerTodosLosEquipos()) {
                clientesPorEquipo.put(eq.getId(), eq.getClienteNombre());
            }
            for (EquipoOtros eq : model.obtenerTodosLosEquiposOtros()) {
                clientesPorEquipoOtros.put(eq.getId(), eq.getClienteNombre());
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

        // EquipoOtros: REMITO y DETALLES
        for (EquipoOtros equipo : model.obtenerTodosLosEquiposOtros()) {
            String clienteNombre = clientesPorEquipoOtros.getOrDefault(equipo.getId(), "");
            List<MaterialOtros> mats = equipo.getMateriales();
            boolean remitoSinFilas = equipo.getTipoIngreso() == TipoIngresoOtros.REMITO
                                     && (mats == null || mats.isEmpty());
            if (remitoSinFilas) {
                EstadoEquipo siguiente = equipo.getSiguienteEstado(equipo.getEstado());
                if (siguiente != EstadoEquipo.ESTERILIZANDO) continue;
                int cantidad = equipo.getRemitoCantidad() != null ? equipo.getRemitoCantidad() : 1;
                // materialId negativo = -equipoId, señal única de REMITO para el DAO
                disponibles.add(new MaterialLoteItem(
                        -equipo.getId(), equipo.getId(), "Elementos", cantidad, 1, clienteNombre, true));
            } else {
                if (mats == null) continue;
                for (MaterialOtros material : mats) {
                    EstadoEquipo siguiente = equipo.getSiguienteEstado(material.getEstado());
                    if (siguiente != EstadoEquipo.ESTERILIZANDO) continue;
                    if (material.getId() == null) continue;
                    disponibles.add(new MaterialLoteItem(
                            material.getId(), equipo.getId(), material.getDescripcion(),
                            material.getCantidad(), 1, clienteNombre, true));
                }
            }
        }

        return disponibles;
    }

    /** Clave compuesta para evitar colisiones entre IDs de ortopedias y otros (tablas distintas). */
    private String claveItem(MaterialLoteItem item) {
        return (item.isEsOtros() ? "O" : "E") + item.getMaterialId();
    }

    private void aplicarPendientesEnDisponibles() {
        Map<String, MaterialLoteItem> disponiblesPorId = new LinkedHashMap<>();
        for (MaterialLoteItem item : materialesDisponibles) {
            disponiblesPorId.put(claveItem(item), item);
        }

        for (List<MaterialLoteItem> pendientes : pendientesPorAutoclave.values()) {
            for (MaterialLoteItem pendiente : pendientes) {
                String clave = claveItem(pendiente);
                MaterialLoteItem disponible = disponiblesPorId.get(clave);
                if (disponible == null) continue;
                int restante = disponible.getCantidad() - pendiente.getCantidad();
                if (restante <= 0) disponiblesPorId.remove(clave);
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
            panel.setMarcarFalloEnabled(false);
            panel.setQuitarEnabled(false);
            return;
        }

        if (autoclave.isOcupado()) {
            Lote lote = lotesActivos.get(autoclave.getNombre());
            List<LoteMaterialInfo> materialesLote = lote != null ? lote.getMateriales() : new ArrayList<>();
            List<MaterialLoteItem> items = new ArrayList<>();
            for (LoteMaterialInfo info : materialesLote) {
                // codigoCatalogo == 0 indica material de equipo_otros_materiales
                String clienteNombre = info.getCodigoCatalogo() == 0
                        ? clientesPorEquipoOtros.getOrDefault(info.getEquipoId(), "")
                        : clientesPorEquipo.getOrDefault(info.getEquipoId(), "");
                // codigoCatalogo == 0 discrimina materiales de equipo_otros_materiales.
                // Para "otros": volumen en DB es el total declarado → setVolumenOtros.
                // Para ortopedia: volumen es unitario (del catálogo) → volumenTotal = cantidad × volumen.
                boolean esOtros = info.getCodigoCatalogo() == 0;
                MaterialLoteItem nuevoItem;
                if (esOtros) {
                    nuevoItem = new MaterialLoteItem(
                            info.getMaterialId(), info.getEquipoId(), info.getDescripcion(),
                            info.getCantidad(), 1, clienteNombre, true);
                    // Sin setVolumenOtros: el volumen pertenece al ingreso
                    // (lote_otros_volumenes); la columna muestra "-".
                } else {
                    nuevoItem = new MaterialLoteItem(
                            info.getMaterialId(), info.getEquipoId(), info.getDescripcion(),
                            info.getCantidad(), info.getVolumen(), clienteNombre);
                }
                items.add(nuevoItem);
            }
            panel.setMaterialesAutoclave(items);
            panel.setCapacidad(autoclave.getCapacidadUsada(), autoclave.getCapacidad());
            panel.setVolumenManualEnabled(false);
            panel.setLanzarEnabled(false);
            panel.setFinalizarEnabled(true);
            panel.setMarcarFalloEnabled(true);
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
            panel.setMarcarFalloEnabled(false);
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

        tablaDisponibles.setDragEnabled(true);
        tablaDisponibles.setDropMode(DropMode.ON);
        tablaDisponibles.setFillsViewportHeight(true);
        tablaDisponibles.setTransferHandler(new DisponiblesTransferHandler());

        tablaAutoclave.setDragEnabled(true);
        tablaAutoclave.setDropMode(DropMode.ON);
        tablaAutoclave.setFillsViewportHeight(true);
        tablaAutoclave.setTransferHandler(new AutoclaveTransferHandler());
    }

    // ── Tabla Disponibles: ORIGEN para drag, DESTINO para devolver ────────────

    private class DisponiblesTransferHandler extends TransferHandler {
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
                MaterialLoteItem item = (MaterialLoteItem) support.getTransferable()
                        .getTransferData(MATERIAL_LOTE_FLAVOR);
                quitarMaterialDePendientes(item);
                cargarDatos();
                return true;
            } catch (Exception e) {
                log.error("Error al procesar drop en tabla disponibles", e);
                return false;
            }
        }
    }

    // ── Tabla Autoclave: DESTINO para drop, ORIGEN para devolver ─────────────

    private class AutoclaveTransferHandler extends TransferHandler {
        private boolean draggingFromSelf = false;

        @Override public int getSourceActions(JComponent c) { return MOVE; }

        @Override
        protected Transferable createTransferable(JComponent c) {
            MaterialLoteItem item = panel.getMaterialAutoclaveSeleccionado();
            if (item == null) return null;
            draggingFromSelf = true;
            return new MaterialLoteTransferable(item, MATERIAL_LOTE_FLAVOR);
        }

        @Override
        protected void exportDone(JComponent source, Transferable data, int action) {
            draggingFromSelf = false;
            if (action == MOVE) SwingUtilities.invokeLater(() -> cargarDatos());
        }

        @Override
        public boolean canImport(TransferSupport support) {
            if (!support.isDrop()) return false;
            if (!support.isDataFlavorSupported(MATERIAL_LOTE_FLAVOR)) return false;
            if (autoclaveSeleccionado == null || autoclaveSeleccionado.isOcupado()) return false;
            if (draggingFromSelf) return false;
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
                MaterialLoteItem item = (MaterialLoteItem) support.getTransferable()
                        .getTransferData(MATERIAL_LOTE_FLAVOR);
                SwingUtilities.invokeLater(() -> agregarMaterial(item));
                return true;
            } catch (Exception e) {
                log.error("Error al procesar drop en tabla autoclave", e);
                SwingUtilities.invokeLater(() -> panel.mostrarAdvertencia("Error: " + e.getMessage()));
                return false;
            }
        }
    }

    private void agregarMaterial(MaterialLoteItem item) {
        if (item == null || autoclaveSeleccionado == null) return;

        Integer cantidadElegida;
        Integer volumenOtros = null;

        if (item.isEsOtros()) {
            int[] result = pedirCantidadYVolumen(item.getDescripcion(), item.getCantidad());
            if (result == null) return;
            cantidadElegida = result[0];
            volumenOtros    = result[1];
        } else {
            cantidadElegida = CantidadDialogHelper.pedirCantidad(
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
        }

        int volumenNecesario = item.isEsOtros()
                ? volumenOtros
                : cantidadElegida * item.getVolumen();
        int capacidadUsada = calcularCapacidadPendiente(autoclaveSeleccionado.getNombre());
        if (capacidadUsada + volumenNecesario > autoclaveSeleccionado.getCapacidad()) {
            panel.mostrarAdvertencia(
                    "El volumen calculado supera la capacidad del autoclave.\n" +
                    "Puede ajustar el volumen final en el campo \"Volumen final\" antes de lanzar.");
        }

        item.setVolumenOtros(volumenOtros);
        ajustarDisponibles(item, cantidadElegida);
        agregarPendiente(autoclaveSeleccionado.getNombre(), item, cantidadElegida);
        cargarDatos();
    }

    /**
     * Diálogo unificado para equipo_otros: pide cantidad y litros en un solo paso.
     * Retorna int[]{cantidad, litros} o null si el usuario cancela.
     */
    private int[] pedirCantidadYVolumen(String descripcion, int cantidadMax) {
        JSpinner spCantidad = new JSpinner(new SpinnerNumberModel(cantidadMax, 1, cantidadMax, 1));
        spCantidad.setEditor(new JSpinner.NumberEditor(spCantidad, "0"));
        JCheckBox chkTodos = new JCheckBox("Todos", true);
        spCantidad.setEnabled(false);
        chkTodos.addActionListener(e -> {
            if (chkTodos.isSelected()) {
                spCantidad.setValue(cantidadMax);
                spCantidad.setEnabled(false);
            } else {
                spCantidad.setEnabled(true);
            }
        });

        JSpinner spLitros = new JSpinner(new SpinnerNumberModel(1, 1, 10000, 1));
        spLitros.setEditor(new JSpinner.NumberEditor(spLitros, "0"));

        JPanel dlgPanel = new JPanel(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
        gbc.insets = new java.awt.Insets(4, 5, 4, 5);
        gbc.anchor = java.awt.GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        dlgPanel.add(new JLabel("<html><b>" + descripcion + "</b></html>"), gbc);

        gbc.gridy = 1; gbc.gridwidth = 1;
        dlgPanel.add(new JLabel("Cantidad:"), gbc);
        gbc.gridx = 1;
        dlgPanel.add(spCantidad, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        dlgPanel.add(chkTodos, gbc);

        gbc.gridy = 3;
        dlgPanel.add(new JLabel("Volumen (litros):"), gbc);
        gbc.gridx = 1;
        dlgPanel.add(spLitros, gbc);

        int res = JOptionPane.showConfirmDialog(panel, dlgPanel,
                "Agregar al autoclave", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (res != JOptionPane.OK_OPTION) return null;
        return new int[]{ (Integer) spCantidad.getValue(), (Integer) spLitros.getValue() };
    }

    private void ajustarDisponibles(MaterialLoteItem item, int cantidad) {
        MaterialLoteItem encontrado = null;
        for (MaterialLoteItem disponible : materialesDisponibles) {
            if (claveItem(disponible).equals(claveItem(item))) {
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
            if (claveItem(existente).equals(claveItem(item))) {
                existente.setCantidad(existente.getCantidad() + cantidad);
                return;
            }
        }
        MaterialLoteItem nuevo = new MaterialLoteItem(
                item.getMaterialId(),
                item.getEquipoId(),
                item.getDescripcion(),
                cantidad,
                item.getVolumen(),
                item.getClienteNombre(),
                item.isEsOtros());
        nuevo.setVolumenOtros(item.getVolumenOtros());
        pendientes.add(nuevo);
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
        pendientes.removeIf(item -> claveItem(item).equals(claveItem(seleccionado)));
        pendientesPorAutoclave.put(autoclaveSeleccionado.getNombre(), pendientes);

        boolean encontrado = false;
        for (MaterialLoteItem disponible : materialesDisponibles) {
            if (claveItem(disponible).equals(claveItem(seleccionado))) {
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

        // PUENTE TEMPORAL (se elimina en el paso 3 del plan): los litros siguen
        // llegando por material desde el diálogo viejo; acá se agrupan por ingreso.
        Map<Integer, Integer> volumenesPorIngreso = new HashMap<>();
        List<LoteMovimiento> movimientos = new ArrayList<>();
        for (MaterialLoteItem item : pendientes) {
            movimientos.add(new LoteMovimiento(
                    item.getMaterialId(), item.getEquipoId(), item.getCantidad(),
                    item.isEsOtros()));
            if (item.isEsOtros() && item.getVolumenOtros() != null) {
                volumenesPorIngreso.merge(item.getEquipoId(), item.getVolumenOtros(), Integer::sum);
            }
        }

        Lote lote = model.lanzarLote(autoclaveSeleccionado.getNombre(),
                capacidadTotal, volumenManual, movimientos, volumenesPorIngreso);

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

        if (!panel.confirmar(Constantes.Mensajes.CONFIRMAR_FINALIZAR_LOTE,
                Constantes.Mensajes.TITULO_CONFIRMAR_CAMBIOS)) return;

        boolean exitoso = model.finalizarLote(autoclaveSeleccionado.getLoteId());
        if (!exitoso) {
            panel.mostrarError(Constantes.Mensajes.ERROR_FINALIZAR_LOTE);
            return;
        }

        cargarDatos();
        if (onEstadosActualizadosListener != null) onEstadosActualizadosListener.onEstadosActualizados();
    }

    private void marcarLoteFallo() {
        if (autoclaveSeleccionado == null || !autoclaveSeleccionado.isOcupado()) return;

        if (!panel.confirmar(Constantes.Mensajes.CONFIRMAR_MARCAR_LOTE_FALLO,
                Constantes.Mensajes.TITULO_CONFIRMAR_CAMBIOS)) return;

        boolean exitoso = model.marcarLoteFallo(autoclaveSeleccionado.getLoteId());
        if (!exitoso) {
            panel.mostrarError(Constantes.Mensajes.ERROR_MARCAR_LOTE_FALLO);
            return;
        }

        panel.mostrarInfo(Constantes.Mensajes.LOTE_FALLO_OK);
        cargarDatos();
        if (onEstadosActualizadosListener != null) onEstadosActualizadosListener.onEstadosActualizados();
    }

    private int calcularCapacidad(List<MaterialLoteItem> materiales) {
        int total = 0;
        for (MaterialLoteItem item : materiales) {
            if (item.isEsOtros() && item.getVolumenOtros() != null) {
                total += item.getVolumenOtros();
            } else {
                total += item.getVolumenTotal();
            }
        }
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

    public void descartarCambiosPendientes() {
        if (!tieneCambiosPendientes()) {
            return;
        }

        pendientesPorAutoclave.clear();
        cargarDatos();
    }
}