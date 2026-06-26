package com.example.features.lavadero.controller;

import com.example.app.AppModel;
import com.example.common.constants.Constantes;
import com.example.features.lavadero.controller.helpers.ElementoCicloTransferable;
import com.example.features.lavadero.model.CicloLavadero;
import com.example.features.lavadero.model.ElementoCicloItem;
import com.example.features.lavadero.model.ElementoCicloMovimiento;
import com.example.features.lavadero.model.Lavarropas;
import com.example.features.lavadero.model.TipoJabon;
import com.example.features.lavadero.view.EquipoSubdivisionDialog;
import com.example.features.lavadero.view.LavarropasCard;
import com.example.features.lavadero.view.PantallaCiclos;
import com.example.features.lavadero.view.helpers.LavarropasItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.math.BigDecimal;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class CiclosController {

    private static final Logger log = LoggerFactory.getLogger(CiclosController.class);

    private final PantallaCiclos pantalla;
    private final AppModel       model;
    private final Map<Integer, LavarropasCard> cards;

    private final Map<Integer, List<ElementoCicloItem>> pendientesPorLavarropas = new HashMap<>();
    private final AtomicInteger nextInstanciaId = new AtomicInteger(1);
    private Map<Integer, CicloLavadero> ciclosActivos        = new HashMap<>();
    private List<ElementoCicloItem>     elementosDisponibles  = new ArrayList<>();
    private List<LavarropasItem>        lavarropasItems       = new ArrayList<>();

    public static final DataFlavor ELEMENTO_CICLO_FLAVOR;

    static {
        DataFlavor flavor = null;
        try {
            flavor = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType
                + ";class=\"" + ElementoCicloItem.class.getName() + "\"");
        } catch (ClassNotFoundException e) {
            LoggerFactory.getLogger(CiclosController.class)
                .error("No se pudo registrar DataFlavor para DnD de ciclos", e);
        }
        ELEMENTO_CICLO_FLAVOR = flavor;
    }

    public CiclosController(PantallaCiclos pantalla, AppModel model) {
        this.pantalla = pantalla;
        this.model    = model;
        this.cards    = pantalla.getAllCards();
        inicializarEventos();
        cargarDatos();
    }

    private void inicializarEventos() {
        for (Map.Entry<Integer, LavarropasCard> entry : cards.entrySet()) {
            int num = entry.getKey();
            LavarropasCard card = entry.getValue();
            card.getTabla().setDropMode(DropMode.ON);
            card.getTabla().setFillsViewportHeight(true);
            card.getTabla().setTransferHandler(new CicloTransferHandler(num));
            card.setOnAccion(() -> {
                if (card.estaActivo()) finalizarCiclo(num);
                else lanzarCiclo(num);
            });
            card.setOnLitrosJabonChanged(() -> card.actualizarBtnAccion());
        }

        pantalla.getBtnLanzarTodos().addActionListener(e -> lanzarTodos());
        pantalla.getBtnFinalizarTodos().addActionListener(e -> finalizarTodos());
        pantalla.getBtnDescartarTodos().addActionListener(e -> {
            if (tienePendientes() && pantalla.confirmar(
                    Constantes.Mensajes.GUARD_CICLOS_CAMBIOS,
                    Constantes.Mensajes.TITULO_CAMBIOS_SIN_CONFIRMAR)) {
                descartarPendientes();
            }
        });

        pantalla.setGuardVolver(
            this::tienePendientes,
            Constantes.Mensajes.GUARD_CICLOS_CAMBIOS,
            this::descartarPendientes
        );

        pantalla.addComponentListener(new ComponentAdapter() {
            private boolean dndConfigurado = false;

            @Override
            public void componentResized(ComponentEvent e) {
                if (!dndConfigurado) {
                    SwingUtilities.invokeLater(() -> { configurarDnD(); dndConfigurado = true; });
                }
            }

            @Override
            public void componentShown(ComponentEvent e) {
                if (!dndConfigurado) {
                    SwingUtilities.invokeLater(() -> { configurarDnD(); dndConfigurado = true; });
                }
                cards.values().forEach(LavarropasCard::colapsarSiPuede);
            }
        });
    }

    public void cargarDatos() {
        ciclosActivos = model.obtenerCiclosActivosPorLavarropas();

        List<ElementoCicloItem> dbDisponibles = model.obtenerElementosDisponiblesParaCiclo();
        elementosDisponibles = aplicarPendientesEnDisponibles(dbDisponibles);

        List<Lavarropas> lavarropasLista = model.obtenerLavarropas();
        lavarropasItems = new ArrayList<>();
        for (Lavarropas lv : lavarropasLista) {
            CicloLavadero activo = ciclosActivos.get(lv.getNumero());
            boolean ocupado = activo != null;
            Integer cicloId = ocupado ? activo.getId() : null;
            lavarropasItems.add(new LavarropasItem(lv.getNumero(), lv.getCapacidadLitros(), ocupado, cicloId));
        }

        pantalla.setElementosDisponibles(elementosDisponibles);
        actualizarTodasLasCards();
    }

    private void actualizarTodasLasCards() {
        Map<Integer, Integer> fracciones = computarFracciones();
        for (Map.Entry<Integer, LavarropasCard> entry : cards.entrySet()) {
            int num = entry.getKey();
            LavarropasCard card = entry.getValue();
            if (ciclosActivos.containsKey(num)) {
                List<ElementoCicloItem> items =
                    model.obtenerElementosDeCiclo(ciclosActivos.get(num).getId());
                card.setModoActivo(ciclosActivos.get(num).getId());
                card.setItems(items, Collections.emptyMap());
            } else {
                List<ElementoCicloItem> pending =
                    pendientesPorLavarropas.getOrDefault(num, Collections.emptyList());
                card.setModoStaging();
                card.setItems(pending, fracciones);
            }
            card.actualizarBtnAccion();
        }
        boolean hayPendientes = tienePendientes();
        pantalla.getBtnLanzarTodos().setEnabled(hayPendientes);
        pantalla.getBtnDescartarTodos().setEnabled(hayPendientes);
        pantalla.getBtnFinalizarTodos().setEnabled(!ciclosActivos.isEmpty());
    }

    private Map<Integer, Integer> computarFracciones() {
        Map<Integer, Integer> count = new HashMap<>();
        for (List<ElementoCicloItem> items : pendientesPorLavarropas.values()) {
            for (ElementoCicloItem item : items) {
                if (item.isEquipo() && item.getInstanciaId() != null) {
                    count.merge(item.getInstanciaId(), 1, Integer::sum);
                }
            }
        }
        return count;
    }

    private List<ElementoCicloItem> aplicarPendientesEnDisponibles(List<ElementoCicloItem> dbDisponibles) {
        Map<Integer, ElementoCicloItem> mapa = new LinkedHashMap<>();
        for (ElementoCicloItem item : dbDisponibles) {
            mapa.put(item.getElementoClasificacionId(), item);
        }

        Map<Integer, Integer>      regularStaged    = new HashMap<>();
        Map<Integer, Set<Integer>> equipoInstancias = new HashMap<>();

        for (List<ElementoCicloItem> pendientes : pendientesPorLavarropas.values()) {
            for (ElementoCicloItem p : pendientes) {
                int id = p.getElementoClasificacionId();
                if (p.isEquipo() && p.getInstanciaId() != null) {
                    equipoInstancias.computeIfAbsent(id, k -> new HashSet<>()).add(p.getInstanciaId());
                } else {
                    regularStaged.merge(id, p.getCantidadEnCiclo(), Integer::sum);
                }
            }
        }

        regularStaged.forEach((id, staged) -> {
            ElementoCicloItem d = mapa.get(id);
            if (d == null) return;
            d.setCantidadEnCiclo(staged);
            if (staged >= d.getCantidadDisponible()) mapa.remove(id);
        });

        equipoInstancias.forEach((id, instancias) -> {
            int staged = instancias.size();
            ElementoCicloItem d = mapa.get(id);
            if (d == null) return;
            d.setCantidadEnCiclo(staged);
            if (staged >= d.getCantidadDisponible()) mapa.remove(id);
        });

        return new ArrayList<>(mapa.values());
    }

    private void configurarDnD() {
        JTable tablaDisponibles = pantalla.getTablaDisponibles();
        if (tablaDisponibles == null || ELEMENTO_CICLO_FLAVOR == null) return;
        tablaDisponibles.setDragEnabled(true);
        tablaDisponibles.setDropMode(DropMode.ON);
        tablaDisponibles.setFillsViewportHeight(true);
        tablaDisponibles.setTransferHandler(new DisponiblesTransferHandler());
    }

    // ── DisponiblesTransferHandler ────────────────────────────────────────────

    private class DisponiblesTransferHandler extends TransferHandler {
        @Override public int getSourceActions(JComponent c) { return COPY; }

        @Override
        protected Transferable createTransferable(JComponent c) {
            ElementoCicloItem item = pantalla.getElementoDisponibleSeleccionado();
            if (item == null) return null;
            return new ElementoCicloTransferable(item, ELEMENTO_CICLO_FLAVOR);
        }

        @Override public boolean canImport(TransferSupport support) { return false; }
    }

    // ── CicloTransferHandler (uno por lavarropas) ─────────────────────────────

    private class CicloTransferHandler extends TransferHandler {
        private final int lavarropasNum;

        CicloTransferHandler(int num) { this.lavarropasNum = num; }

        @Override public int getSourceActions(JComponent c) { return NONE; }

        @Override
        public boolean canImport(TransferSupport support) {
            if (!support.isDrop()) return false;
            if (!support.isDataFlavorSupported(ELEMENTO_CICLO_FLAVOR)) return false;
            if (ciclosActivos.containsKey(lavarropasNum)) return false;
            support.setShowDropLocation(true);
            return true;
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            try {
                ElementoCicloItem item = (ElementoCicloItem) support.getTransferable()
                    .getTransferData(ELEMENTO_CICLO_FLAVOR);
                if (item.isEquipo()) {
                    SwingUtilities.invokeLater(() -> procesarDropEquipo(item, lavarropasNum));
                } else {
                    SwingUtilities.invokeLater(() -> procesarDropRegular(item, lavarropasNum));
                }
                return true;
            } catch (Exception e) {
                log.error("Error al procesar drop en lavarropas {}", lavarropasNum, e);
                SwingUtilities.invokeLater(() ->
                    pantalla.mostrarAdvertencia("Error al procesar el elemento: " + e.getMessage()));
                return false;
            }
        }
    }

    // ── Agregar elementos ─────────────────────────────────────────────────────

    private void procesarDropRegular(ElementoCicloItem item, int lavarropasNum) {
        int max = item.getCantidadDisponible() - item.getCantidadEnCiclo();
        if (max <= 0) return;
        int k = (max == 1) ? 1 : seleccionarSubcantidad(item);
        if (k <= 0) return;
        agregarPendiente(lavarropasNum, item, k);
        refrescarDisponiblesYCards();
    }

    private void procesarDropEquipo(ElementoCicloItem item, int lavarropasNum) {
        int max = item.getCantidadDisponible() - item.getCantidadEnCiclo();
        if (max <= 0) return;
        int k = (max == 1) ? 1 : seleccionarSubcantidad(item);
        if (k <= 0) return;
        for (int unidad = 1; unidad <= k; unidad++) {
            abrirDialogoSubdivisionUnidad(item, lavarropasNum, unidad, k);
        }
        refrescarDisponiblesYCards();
    }

    private int seleccionarSubcantidad(ElementoCicloItem item) {
        int max = item.getCantidadDisponible() - item.getCantidadEnCiclo();
        JSpinner sp = new JSpinner(new SpinnerNumberModel(1, 1, max, 1));
        sp.setEditor(new JSpinner.NumberEditor(sp, "0"));
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 5, 4, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        panel.add(new JLabel("<html><b>" + item.getElementoNombre()
            + "</b> — " + item.getClienteNombre() + " (disponibles: " + max + ")</html>"), gbc);
        gbc.gridy = 1; gbc.gridwidth = 1;
        panel.add(new JLabel("Unidades:"), gbc);
        gbc.gridx = 1;
        panel.add(sp, gbc);
        int res = JOptionPane.showConfirmDialog(pantalla, panel,
            "¿Cuántas unidades distribuís ahora?", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        return res == JOptionPane.OK_OPTION ? (Integer) sp.getValue() : 0;
    }

    private void abrirDialogoSubdivisionUnidad(ElementoCicloItem equipo, int preSelected,
                                                int unidad, int totalUnidades) {
        List<LavarropasItem> candidatos = lavarropasItems.stream()
            .filter(lv -> !ciclosActivos.containsKey(lv.getNumero()))
            .collect(Collectors.toList());
        if (candidatos.isEmpty()) {
            pantalla.mostrarAdvertencia("No hay lavarropas libres para subdividir el equipo.");
            return;
        }
        Frame frame = (Frame) SwingUtilities.getWindowAncestor(pantalla);
        EquipoSubdivisionDialog dlg =
            new EquipoSubdivisionDialog(frame, equipo, candidatos, preSelected, unidad, totalUnidades);
        dlg.setVisible(true);
        List<Integer> seleccionados = dlg.getSeleccionados();
        if (seleccionados.isEmpty()) return;
        int instanciaId = nextInstanciaId.getAndIncrement();
        for (int num : seleccionados) {
            ElementoCicloItem copia = new ElementoCicloItem(
                equipo.getElementoClasificacionId(), equipo.getIngresoId(),
                equipo.getElementoNombre(), equipo.getCantidadTotal(),
                equipo.getCantidadYaProcesada(), equipo.getClienteNombre(),
                ElementoCicloItem.CATEGORIA_EQUIPO
            );
            copia.setInstanciaId(instanciaId);
            copia.setCantidadEnCiclo(1);
            agregarPendienteEquipo(num, copia);
        }
    }

    private void agregarPendienteEquipo(int lavarropasNumero, ElementoCicloItem item) {
        pendientesPorLavarropas
            .computeIfAbsent(lavarropasNumero, k -> new ArrayList<>())
            .add(item);
    }

    private void agregarPendiente(int lavarropasNumero, ElementoCicloItem origen, int cantidad) {
        List<ElementoCicloItem> pendientes = pendientesPorLavarropas
            .computeIfAbsent(lavarropasNumero, k -> new ArrayList<>());
        for (ElementoCicloItem existente : pendientes) {
            if (existente.getElementoClasificacionId() == origen.getElementoClasificacionId()
                    && !existente.isEquipo()) {
                existente.setCantidadEnCiclo(existente.getCantidadEnCiclo() + cantidad);
                return;
            }
        }
        ElementoCicloItem nuevo = new ElementoCicloItem(
            origen.getElementoClasificacionId(),
            origen.getIngresoId(),
            origen.getElementoNombre(),
            origen.getCantidadTotal(),
            origen.getCantidadYaProcesada(),
            origen.getClienteNombre(),
            origen.getCategoria()
        );
        nuevo.setCantidadEnCiclo(cantidad);
        pendientes.add(nuevo);
    }

    private void refrescarDisponiblesYCards() {
        List<ElementoCicloItem> dbDisponibles = model.obtenerElementosDisponiblesParaCiclo();
        elementosDisponibles = aplicarPendientesEnDisponibles(dbDisponibles);
        pantalla.setElementosDisponibles(elementosDisponibles);
        actualizarTodasLasCards();
    }

    // ── Lanzar / Finalizar ────────────────────────────────────────────────────

    private void lanzarCiclo(int num) {
        if (!pantalla.confirmar(Constantes.Mensajes.CONFIRMAR_LANZAR_CICLO,
                Constantes.Mensajes.TITULO_LANZAR_LOTE)) return;
        ejecutarLanzamiento(num);
        cargarDatos();
    }

    private void ejecutarLanzamiento(int num) {
        LavarropasCard card = cards.get(num);
        List<ElementoCicloItem> pendientes =
            pendientesPorLavarropas.getOrDefault(num, Collections.emptyList());
        if (pendientes.isEmpty()) return;

        TipoJabon  tipoJabon    = card.getTipoJabon();
        BigDecimal litrosJabon  = card.getLitrosJabon();
        if (litrosJabon == null) {
            pantalla.mostrarError("Lavarropas #" + num + ": ingrese los litros de jabón.");
            return;
        }
        boolean    suavizante    = card.isSuavizante();
        BigDecimal litrosTotales = card.getLitrosTotales();

        List<ElementoCicloMovimiento> movimientos = new ArrayList<>();
        for (ElementoCicloItem item : pendientes) {
            movimientos.add(new ElementoCicloMovimiento(
                item.getElementoClasificacionId(), item.getCantidadEnCiclo()));
        }
        try {
            model.lanzarCiclo(num, tipoJabon, litrosJabon, suavizante, litrosTotales, movimientos);
            pendientesPorLavarropas.remove(num);
        } catch (Exception e) {
            log.error("Error al lanzar ciclo en lavarropas {}", num, e);
            pantalla.mostrarError(Constantes.Mensajes.ERROR_LANZAR_CICLO);
        }
    }

    private void lanzarTodos() {
        List<Integer> conPendientes = pendientesPorLavarropas.entrySet().stream()
            .filter(e -> !e.getValue().isEmpty())
            .map(Map.Entry::getKey)
            .sorted()
            .collect(Collectors.toList());
        if (conPendientes.isEmpty()) return;
        if (!pantalla.confirmar(
                "¿Lanzar " + conPendientes.size() + " ciclo(s) de lavado?",
                Constantes.Mensajes.TITULO_LANZAR_LOTE)) return;
        for (int num : conPendientes) {
            ejecutarLanzamiento(num);
        }
        cargarDatos();
    }

    private void finalizarCiclo(int num) {
        if (ciclosActivos.get(num) == null) return;
        if (!pantalla.confirmar(Constantes.Mensajes.CONFIRMAR_FINALIZAR_CICLO,
                Constantes.Mensajes.TITULO_FINALIZAR_LOTE)) return;
        ejecutarFinalizacion(num);
        cargarDatos();
    }

    private void finalizarTodos() {
        List<Integer> conActivos = ciclosActivos.keySet().stream()
            .sorted().collect(Collectors.toList());
        if (conActivos.isEmpty()) return;
        if (!pantalla.confirmar(
                "¿Finalizar " + conActivos.size() + " ciclo(s) activo(s)?",
                Constantes.Mensajes.TITULO_FINALIZAR_LOTE)) return;
        for (int num : conActivos) {
            ejecutarFinalizacion(num);
        }
        cargarDatos();
    }

    private void ejecutarFinalizacion(int num) {
        CicloLavadero ciclo = ciclosActivos.get(num);
        if (ciclo == null) return;
        try {
            model.finalizarCiclo(ciclo.getId());
        } catch (Exception e) {
            log.error("Error al finalizar ciclo {}", ciclo.getId(), e);
            pantalla.mostrarError(Constantes.Mensajes.ERROR_FINALIZAR_CICLO);
        }
    }

    // ── Pendientes ────────────────────────────────────────────────────────────

    public boolean tienePendientes() {
        return pendientesPorLavarropas.values().stream().anyMatch(l -> !l.isEmpty());
    }

    public void descartarPendientes() {
        pendientesPorLavarropas.clear();
        cargarDatos();
    }
}
