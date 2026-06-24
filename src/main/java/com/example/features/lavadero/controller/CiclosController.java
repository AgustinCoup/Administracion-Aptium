package com.example.features.lavadero.controller;

import com.example.app.AppModel;
import com.example.common.constants.Constantes;
import com.example.features.lavadero.controller.helpers.ElementoCicloTransferable;
import com.example.features.lavadero.model.CicloLavadero;
import com.example.features.lavadero.model.ElementoCicloItem;
import com.example.features.lavadero.model.ElementoCicloMovimiento;
import com.example.features.lavadero.model.Lavarropas;
import com.example.features.lavadero.model.TipoJabon;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CiclosController {

    private static final Logger log = LoggerFactory.getLogger(CiclosController.class);

    private final PantallaCiclos pantalla;
    private final AppModel       model;

    private final Map<Integer, List<ElementoCicloItem>> pendientesPorLavarropas = new HashMap<>();
    private Map<Integer, CicloLavadero> ciclosActivos        = new HashMap<>();
    private List<ElementoCicloItem>     elementosDisponibles  = new ArrayList<>();
    private LavarropasItem              lavarropasSeleccionado;

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
        inicializarEventos();
        cargarDatos();
    }

    private void inicializarEventos() {
        pantalla.setOnLavarropasSeleccionado(this::onLavarropasSeleccionado);
        pantalla.setOnLanzar(e -> lanzarCiclo());
        pantalla.setOnFinalizar(e -> finalizarCiclo());
        pantalla.setOnQuitar(e -> quitarElemento());
        pantalla.setOnLitrosJabonChanged(this::actualizarBotonLanzar);

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
        Integer numeroPreseleccionado = lavarropasSeleccionado != null
            ? lavarropasSeleccionado.getNumero() : null;

        ciclosActivos = model.obtenerCiclosActivosPorLavarropas();

        List<ElementoCicloItem> dbDisponibles = model.obtenerElementosDisponiblesParaCiclo();
        elementosDisponibles = aplicarPendientesEnDisponibles(dbDisponibles);

        List<Lavarropas> lavarropasLista = model.obtenerLavarropas();
        List<LavarropasItem> items = new ArrayList<>();
        for (Lavarropas lv : lavarropasLista) {
            CicloLavadero activo = ciclosActivos.get(lv.getNumero());
            boolean ocupado = activo != null;
            Integer cicloId = ocupado ? activo.getId() : null;
            items.add(new LavarropasItem(lv.getNumero(), lv.getCapacidadLitros(), ocupado, cicloId));
        }

        pantalla.setLavarropas(items);
        if (numeroPreseleccionado != null) pantalla.seleccionarLavarropas(numeroPreseleccionado);
        pantalla.setElementosDisponibles(elementosDisponibles);
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

    private void onLavarropasSeleccionado(LavarropasItem item) {
        lavarropasSeleccionado = item;

        if (item == null) {
            pantalla.setElementosCiclo(Collections.emptyList());
            pantalla.setConfigEnabled(false);
            pantalla.setLanzarEnabled(false);
            pantalla.setFinalizarEnabled(false);
            pantalla.setQuitarEnabled(false);
            return;
        }

        if (item.isOcupado()) {
            List<ElementoCicloItem> enCiclo = model.obtenerElementosDeCiclo(item.getCicloId());
            pantalla.setElementosCiclo(enCiclo);
            pantalla.setConfigEnabled(false);
            pantalla.setLanzarEnabled(false);
            pantalla.setFinalizarEnabled(true);
            pantalla.setQuitarEnabled(false);
        } else {
            List<ElementoCicloItem> pendientes = pendientesPorLavarropas
                .getOrDefault(item.getNumero(), Collections.emptyList());
            pantalla.setElementosCiclo(pendientes);
            pantalla.setConfigEnabled(true);
            pantalla.setQuitarEnabled(!pendientes.isEmpty());
            pantalla.setFinalizarEnabled(false);
            actualizarBotonLanzar();
        }
    }

    private void actualizarBotonLanzar() {
        if (lavarropasSeleccionado == null || lavarropasSeleccionado.isOcupado()) {
            pantalla.setLanzarEnabled(false);
            return;
        }
        List<ElementoCicloItem> pendientes = pendientesPorLavarropas
            .getOrDefault(lavarropasSeleccionado.getNumero(), Collections.emptyList());
        pantalla.setLanzarEnabled(!pendientes.isEmpty() && pantalla.getLitrosJabon() != null);
    }

    private void configurarDnD() {
        JTable tablaDisponibles = pantalla.getTablaDisponibles();
        JTable tablaCiclo = pantalla.getTablaCiclo();
        if (tablaDisponibles == null || tablaCiclo == null || ELEMENTO_CICLO_FLAVOR == null) return;

        tablaDisponibles.setDragEnabled(true);
        tablaDisponibles.setDropMode(DropMode.ON);
        tablaDisponibles.setFillsViewportHeight(true);
        tablaDisponibles.setTransferHandler(new DisponiblesTransferHandler());

        tablaCiclo.setDragEnabled(false);
        tablaCiclo.setDropMode(DropMode.ON);
        tablaCiclo.setFillsViewportHeight(true);
        tablaCiclo.setTransferHandler(new CicloTransferHandler());
    }

    // ── DisponiblesTransferHandler (origen del drag) ────────────────────────────

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

    // ── CicloTransferHandler (destino del drop) ─────────────────────────────────

    private class CicloTransferHandler extends TransferHandler {
        @Override public int getSourceActions(JComponent c) { return NONE; }

        @Override
        public boolean canImport(TransferSupport support) {
            if (!support.isDrop()) return false;
            if (!support.isDataFlavorSupported(ELEMENTO_CICLO_FLAVOR)) return false;
            if (lavarropasSeleccionado == null || lavarropasSeleccionado.isOcupado()) return false;
            support.setShowDropLocation(true);
            return true;
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            try {
                ElementoCicloItem item = (ElementoCicloItem) support.getTransferable()
                    .getTransferData(ELEMENTO_CICLO_FLAVOR);
                SwingUtilities.invokeLater(() -> agregarElemento(item));
                return true;
            } catch (Exception e) {
                log.error("Error al procesar drop en tabla ciclo", e);
                SwingUtilities.invokeLater(() -> pantalla.mostrarAdvertencia("Error: " + e.getMessage()));
                return false;
            }
        }
    }

    private void agregarElemento(ElementoCicloItem item) {
        if (item == null || lavarropasSeleccionado == null || lavarropasSeleccionado.isOcupado()) return;

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

        int cantidad = (Integer) spCantidad.getValue();
        agregarPendiente(lavarropasSeleccionado.getNumero(), item, cantidad);
        cargarDatos();
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
            origen.getClienteNombre()
        );
        nuevo.setCantidadEnCiclo(cantidad);
        pendientes.add(nuevo);
    }

    private void quitarElemento() {
        if (lavarropasSeleccionado == null || lavarropasSeleccionado.isOcupado()) return;
        ElementoCicloItem seleccionado = pantalla.getElementoCicloSeleccionado();
        if (seleccionado == null) {
            pantalla.mostrarAdvertencia("Seleccione un elemento para quitar.");
            return;
        }
        List<ElementoCicloItem> pendientes = pendientesPorLavarropas
            .getOrDefault(lavarropasSeleccionado.getNumero(), new ArrayList<>());
        pendientes.removeIf(e -> e.getElementoClasificacionId() == seleccionado.getElementoClasificacionId());
        pendientesPorLavarropas.put(lavarropasSeleccionado.getNumero(), pendientes);
        cargarDatos();
    }

    private void lanzarCiclo() {
        if (lavarropasSeleccionado == null || lavarropasSeleccionado.isOcupado()) return;

        List<ElementoCicloItem> pendientes = pendientesPorLavarropas
            .getOrDefault(lavarropasSeleccionado.getNumero(), Collections.emptyList());
        if (pendientes.isEmpty()) {
            pantalla.mostrarAdvertencia("Debe agregar al menos un elemento al ciclo.");
            return;
        }

        TipoJabon tipoJabon    = pantalla.getTipoJabonSeleccionado();
        BigDecimal litrosJabon = pantalla.getLitrosJabon();
        if (litrosJabon == null) {
            pantalla.mostrarError("Los litros de jabón deben ser un número mayor a cero.");
            return;
        }

        boolean    suavizante  = pantalla.isSuavizante();
        BigDecimal litrosTotales = pantalla.getLitrosTotales();

        if (!pantalla.confirmar(Constantes.Mensajes.CONFIRMAR_LANZAR_CICLO,
                Constantes.Mensajes.TITULO_LANZAR_LOTE)) return;

        List<ElementoCicloMovimiento> movimientos = new ArrayList<>();
        for (ElementoCicloItem item : pendientes) {
            movimientos.add(new ElementoCicloMovimiento(
                item.getElementoClasificacionId(), item.getCantidadEnCiclo()));
        }

        try {
            model.lanzarCiclo(lavarropasSeleccionado.getNumero(), tipoJabon,
                litrosJabon, suavizante, litrosTotales, movimientos);
        } catch (Exception e) {
            log.error("Error al lanzar ciclo en lavarropas {}", lavarropasSeleccionado.getNumero(), e);
            pantalla.mostrarError(Constantes.Mensajes.ERROR_LANZAR_CICLO);
            return;
        }

        pendientesPorLavarropas.remove(lavarropasSeleccionado.getNumero());
        cargarDatos();
    }

    private void finalizarCiclo() {
        if (lavarropasSeleccionado == null || !lavarropasSeleccionado.isOcupado()) return;

        if (!pantalla.confirmar(Constantes.Mensajes.CONFIRMAR_FINALIZAR_CICLO,
                Constantes.Mensajes.TITULO_FINALIZAR_LOTE)) return;

        try {
            model.finalizarCiclo(lavarropasSeleccionado.getCicloId());
        } catch (Exception e) {
            log.error("Error al finalizar ciclo {}", lavarropasSeleccionado.getCicloId(), e);
            pantalla.mostrarError(Constantes.Mensajes.ERROR_FINALIZAR_CICLO);
            return;
        }

        cargarDatos();
    }

    public boolean tienePendientes() {
        return pendientesPorLavarropas.values().stream().anyMatch(l -> !l.isEmpty());
    }

    public void descartarPendientes() {
        pendientesPorLavarropas.clear();
        cargarDatos();
    }
}
