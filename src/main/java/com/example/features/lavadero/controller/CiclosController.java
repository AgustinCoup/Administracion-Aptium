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
import java.util.stream.Collectors;

public class CiclosController {

    private static final Logger log = LoggerFactory.getLogger(CiclosController.class);

    private final PantallaCiclos pantalla;
    private final AppModel       model;
    private final Map<Integer, LavarropasCard> cards;

    private final Map<Integer, List<ElementoCicloItem>> pendientesPorLavarropas = new HashMap<>();
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
    }

    private Map<Integer, Integer> computarFracciones() {
        Map<Integer, Integer> count = new HashMap<>();
        for (List<ElementoCicloItem> items : pendientesPorLavarropas.values()) {
            for (ElementoCicloItem item : items) {
                if (item.isEquipo()) {
                    count.merge(item.getElementoClasificacionId(), 1, Integer::sum);
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
        for (List<ElementoCicloItem> pendientes : pendientesPorLavarropas.values()) {
            for (ElementoCicloItem pendiente : pendientes) {
                ElementoCicloItem disponible = mapa.get(pendiente.getElementoClasificacionId());
                if (disponible == null) continue;
                int totalStaged = disponible.getCantidadEnCiclo() + pendiente.getCantidadEnCiclo();
                disponible.setCantidadEnCiclo(totalStaged);
                if (totalStaged >= disponible.getCantidadDisponible()) {
                    mapa.remove(pendiente.getElementoClasificacionId());
                }
            }
        }
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
                    SwingUtilities.invokeLater(() -> abrirDialogoSubdivision(item, lavarropasNum));
                } else {
                    SwingUtilities.invokeLater(() -> agregarElementoACard(lavarropasNum, item));
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

    private void agregarElementoACard(int lavarropasNum, ElementoCicloItem item) {
        int maxDisponible = item.getCantidadDisponible() - item.getCantidadEnCiclo();
        if (maxDisponible <= 0) return;

        JSpinner spCantidad = new JSpinner(new SpinnerNumberModel(maxDisponible, 1, maxDisponible, 1));
        spCantidad.setEditor(new JSpinner.NumberEditor(spCantidad, "0"));

        JPanel dlgPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 5, 4, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        dlgPanel.add(new JLabel("<html><b>" + item.getElementoNombre()
            + "</b> — " + item.getClienteNombre() + "</html>"), gbc);
        gbc.gridy = 1; gbc.gridwidth = 1;
        dlgPanel.add(new JLabel("Cantidad:"), gbc);
        gbc.gridx = 1;
        dlgPanel.add(spCantidad, gbc);

        int res = JOptionPane.showConfirmDialog(pantalla, dlgPanel,
            "Agregar al ciclo", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (res != JOptionPane.OK_OPTION) return;

        agregarPendiente(lavarropasNum, item, (Integer) spCantidad.getValue());
        refrescarDisponiblesYCards();
    }

    private void abrirDialogoSubdivision(ElementoCicloItem equipo, Integer preSelected) {
        List<LavarropasItem> candidatos = lavarropasItems.stream()
            .filter(lv -> !ciclosActivos.containsKey(lv.getNumero()))
            .collect(Collectors.toList());
        if (candidatos.isEmpty()) {
            pantalla.mostrarAdvertencia("No hay lavarropas libres para subdividir el equipo.");
            return;
        }
        Frame frame = (Frame) SwingUtilities.getWindowAncestor(pantalla);
        EquipoSubdivisionDialog dlg =
            new EquipoSubdivisionDialog(frame, equipo, candidatos, preSelected);
        dlg.setVisible(true);
        List<Integer> seleccionados = dlg.getSeleccionados();
        if (seleccionados.isEmpty()) return;
        for (int num : seleccionados) {
            ElementoCicloItem copia = new ElementoCicloItem(
                equipo.getElementoClasificacionId(),
                equipo.getIngresoId(),
                equipo.getElementoNombre(),
                equipo.getCantidadTotal(),
                equipo.getCantidadYaProcesada(),
                equipo.getClienteNombre(),
                ElementoCicloItem.CATEGORIA_EQUIPO
            );
            copia.setCantidadEnCiclo(1);
            agregarPendiente(num, copia, 1);
        }
        refrescarDisponiblesYCards();
    }

    private void agregarPendiente(int lavarropasNumero, ElementoCicloItem origen, int cantidad) {
        List<ElementoCicloItem> pendientes = pendientesPorLavarropas
            .computeIfAbsent(lavarropasNumero, k -> new ArrayList<>());
        for (ElementoCicloItem existente : pendientes) {
            if (existente.getElementoClasificacionId() == origen.getElementoClasificacionId()) {
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
        CicloLavadero ciclo = ciclosActivos.get(num);
        if (ciclo == null) return;
        if (!pantalla.confirmar(Constantes.Mensajes.CONFIRMAR_FINALIZAR_CICLO,
                Constantes.Mensajes.TITULO_FINALIZAR_LOTE)) return;
        try {
            model.finalizarCiclo(ciclo.getId());
        } catch (Exception e) {
            log.error("Error al finalizar ciclo {}", ciclo.getId(), e);
            pantalla.mostrarError(Constantes.Mensajes.ERROR_FINALIZAR_CICLO);
            return;
        }
        cargarDatos();
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
