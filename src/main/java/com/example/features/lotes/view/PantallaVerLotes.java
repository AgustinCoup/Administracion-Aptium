package com.example.features.lotes.view;

import com.example.common.constants.Constantes;
import com.example.common.util.DateTimeDisplayUtils;
import com.example.features.lotes.model.Lote;
import com.example.features.lotes.view.helpers.EstadoCellRenderer;
import com.example.ui.common.CheckableComboBox;
import com.example.ui.common.Estilos;
import com.example.ui.common.FilterUiHelper;
import com.example.ui.common.PanelHeader;
import com.example.ui.common.TableStyler;
import com.toedter.calendar.JDateChooser;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionListener;
import java.time.LocalDate;
import java.util.List;

/**
 * Pantalla de consulta de lotes de esterilización.
 *
 * Filtros disponibles:
 * - ID de lote: texto libre (coincidencia parcial)
 * - Autoclave: CheckableComboBox multi-selección (vacío = todos)
 * - Estado: CheckableComboBox multi-selección (ACTIVO / EXITOSO / FALLIDO)
 * - Fecha inicio Desde / Hasta: JDateChooser con calendario
 */
public class PantallaVerLotes extends JPanel {

    private final DefaultTableModel modeloTabla;
    private final JTable            tablaLotes;

    // ── Controles de filtro ───────────────────────────────────────────────────
    private JTextField                  txtFiltroId;
    private CheckableComboBox<String>   cmbFiltroAutoclave;
    private CheckableComboBox<String>   cmbFiltroEstado;
    private JDateChooser                dateChooserDesde;
    private JDateChooser                dateChooserHasta;
    private JButton                     btnLimpiarFiltros;
    private JButton                     btnImprimir;

    private Runnable onFiltrosChanged;

    public PantallaVerLotes(CardLayout navegador, JPanel contenedor) {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        PanelHeader header = new PanelHeader(
            Constantes.Titulos.VER_LOTES,
            navegador,
            contenedor,
            Constantes.Pantallas.VER_EQUIPOS
        );

        JPanel panelNorte = new JPanel(new BorderLayout());
        panelNorte.add(header,              BorderLayout.NORTH);
        panelNorte.add(crearPanelFiltros(), BorderLayout.SOUTH);
        add(panelNorte, BorderLayout.NORTH);

        modeloTabla = new DefaultTableModel(
            new Object[]{
                Constantes.Textos.COLUMNA_LOTE_ID,
                Constantes.Textos.COLUMNA_LOTE_EQUIPO,
                Constantes.Textos.COLUMNA_LOTE_CAPACIDAD_USADA,
                Constantes.Textos.COLUMNA_LOTE_INICIO,
                Constantes.Textos.COLUMNA_LOTE_FIN,
                Constantes.Textos.COLUMNA_LOTE_ESTADO
            },
            0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        tablaLotes = new JTable(modeloTabla);
        TableStyler.applyStandard(tablaLotes);
        TableStyler.centerColumns(tablaLotes, 2);
        tablaLotes.setRowSelectionAllowed(false);
        tablaLotes.setFillsViewportHeight(true);
        tablaLotes.getColumnModel().getColumn(5).setCellRenderer(new EstadoCellRenderer());

        add(new JScrollPane(tablaLotes), BorderLayout.CENTER);
    }

    // ── Construcción del panel de filtros ─────────────────────────────────────

    private JPanel crearPanelFiltros() {
        // ── Componentes ──────────────────────────────────────────────────────
        JLabel lblId = new JLabel(Constantes.Textos.FILTRO_ID);
        lblId.setFont(Estilos.Fuentes.LABEL);
        txtFiltroId = new JTextField(6);
        txtFiltroId.setFont(Estilos.Fuentes.INPUT);

        JLabel lblAutoclave = new JLabel(Constantes.Textos.FILTRO_EQUIPO);
        lblAutoclave.setFont(Estilos.Fuentes.LABEL);
        cmbFiltroAutoclave = new CheckableComboBox<>(new String[0]);
        cmbFiltroAutoclave.setFont(Estilos.Fuentes.INPUT);
        cmbFiltroAutoclave.setPreferredSize(new Dimension(150, 25));

        JLabel lblEstado = new JLabel(Constantes.Textos.FILTRO_ESTADO);
        lblEstado.setFont(Estilos.Fuentes.LABEL);
        cmbFiltroEstado = new CheckableComboBox<>(new String[]{"ACTIVO", "EXITOSO", "FALLIDO"});
        cmbFiltroEstado.setFont(Estilos.Fuentes.INPUT);
        cmbFiltroEstado.setPreferredSize(new Dimension(130, 25));

        JLabel lblDesde = new JLabel("Desde:");
        lblDesde.setFont(Estilos.Fuentes.LABEL);
        dateChooserDesde = new JDateChooser();
        dateChooserDesde.setPreferredSize(new Dimension(110, 25));
        dateChooserDesde.setDateFormatString("dd/MM/yyyy");

        JLabel lblHasta = new JLabel("Hasta:");
        lblHasta.setFont(Estilos.Fuentes.LABEL);
        dateChooserHasta = new JDateChooser();
        dateChooserHasta.setPreferredSize(new Dimension(110, 25));
        dateChooserHasta.setDateFormatString("dd/MM/yyyy");

        btnLimpiarFiltros = new JButton(Constantes.Botones.LIMPIAR_FILTROS);
        btnLimpiarFiltros.setFont(Estilos.Fuentes.INPUT);
        btnLimpiarFiltros.addActionListener(e -> limpiarFiltros());

        btnImprimir = new JButton(Constantes.Botones.IMPRIMIR);
        btnImprimir.setFont(Estilos.Fuentes.INPUT);

        // ── Dos filas para que no se corten en pantallas pequeñas ────────────
        JPanel fila1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        fila1.add(lblId);       fila1.add(txtFiltroId);
        fila1.add(lblAutoclave); fila1.add(cmbFiltroAutoclave);
        fila1.add(lblEstado);   fila1.add(cmbFiltroEstado);

        JPanel fila2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        fila2.add(lblDesde); fila2.add(dateChooserDesde);
        fila2.add(lblHasta); fila2.add(dateChooserHasta);
        fila2.add(btnLimpiarFiltros);
        fila2.add(btnImprimir);

        JPanel panelFiltros = new JPanel(new BorderLayout());
        panelFiltros.setBorder(BorderFactory.createTitledBorder("Filtros"));
        JPanel filas = new JPanel();
        filas.setLayout(new BoxLayout(filas, BoxLayout.Y_AXIS));
        filas.add(fila1);
        filas.add(fila2);
        panelFiltros.add(filas, BorderLayout.CENTER);

        // ── Vínculos de cambio ───────────────────────────────────────────────
        FilterUiHelper.bindOnTextChange(this::notificarCambioFiltros, txtFiltroId);
        FilterUiHelper.bindOnDateChange(this::notificarCambioFiltros, dateChooserDesde, dateChooserHasta);
        cmbFiltroAutoclave.setOnSelectionChange(this::notificarCambioFiltros);
        cmbFiltroEstado.setOnSelectionChange(this::notificarCambioFiltros);

        return panelFiltros;
    }

    // ── Operaciones públicas ─────────────────────────────────────────────────

    public void limpiarFiltros() {
        txtFiltroId.setText("");
        cmbFiltroAutoclave.clearSelection();
        cmbFiltroEstado.clearSelection();
        dateChooserDesde.setDate(null);
        dateChooserHasta.setDate(null);
        notificarCambioFiltros();
    }

    private void notificarCambioFiltros() {
        if (onFiltrosChanged != null) onFiltrosChanged.run();
    }

    public void actualizarLotes(List<Lote> lotes) {
        modeloTabla.setRowCount(0);
        for (Lote lote : lotes) {
            String fechaInicio = DateTimeDisplayUtils.formatForUi(lote.getFechaInicio());
            String fechaFin    = DateTimeDisplayUtils.formatForUi(lote.getFechaFin());
            String estado      = lote.getEstado() != null ? lote.getEstado() : "ACTIVO";

            modeloTabla.addRow(new Object[]{
                lote.getIdNegocio(),
                lote.getAutoclaveNombre(),
                lote.getCapacidadUsada(),
                fechaInicio,
                fechaFin,
                estado
            });
        }
    }

    /**
     * Rellena las opciones del CheckableComboBox de autoclaves.
     * Preserva la selección actual si los items coinciden.
     */
    public void setEquiposFiltro(List<String> autoclaves) {
        // Guardar selección actual para restaurarla si es posible
        java.util.List<String> seleccionActual = cmbFiltroAutoclave.getSelectedItems();

        DefaultComboBoxModel<String> modelo = new DefaultComboBoxModel<>();
        if (autoclaves != null) {
            for (String a : autoclaves) {
                if (a != null && !a.trim().isEmpty()) modelo.addElement(a);
            }
        }
        cmbFiltroAutoclave.setModel(modelo);

        // Restaurar selección
        if (!seleccionActual.isEmpty()) {
            cmbFiltroAutoclave.setSelectedItems(seleccionActual);
        }
    }

    // ── Getters de criterios (usados por VerLotesController) ─────────────────

    public String getFiltroId() {
        return txtFiltroId.getText().trim();
    }

    /** Lista de autoclaves seleccionadas; vacía significa "sin filtro". */
    public List<String> getFiltroAutoclaves() {
        return cmbFiltroAutoclave.getSelectedItems();
    }

    /** Lista de estados seleccionados; vacía significa "sin filtro". */
    public List<String> getFiltroEstados() {
        return cmbFiltroEstado.getSelectedItems();
    }

    /** Límite inferior de la fecha de inicio del lote; null = sin límite. */
    public LocalDate getFiltroFechaDesde() {
        if (dateChooserDesde.getDate() == null) return null;
        return dateChooserDesde.getDate().toInstant()
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
    }

    /** Límite superior de la fecha de inicio del lote; null = sin límite. */
    public LocalDate getFiltroFechaHasta() {
        if (dateChooserHasta.getDate() == null) return null;
        return dateChooserHasta.getDate().toInstant()
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
    }

    public void setOnFiltrosChanged(Runnable listener) {
        this.onFiltrosChanged = listener;
    }

    /** Registra la acción que se ejecuta al presionar "Imprimir". */
    public void setOnImprimir(Runnable listener) {
        // Limpiar listeners anteriores para evitar acumulación si se llama más de una vez
        for (ActionListener al : btnImprimir.getActionListeners()) {
            btnImprimir.removeActionListener(al);
        }
        btnImprimir.addActionListener(e -> listener.run());
    }
}