package com.example.features.equipos.ortopedias.view;

import com.example.common.constants.Constantes;
import com.example.features.equipos.ortopedias.controller.helpers.FiltroAuditorias;
import com.example.features.equipos.ortopedias.model.EquipoAuditoria;
import com.example.ui.common.CheckableComboBox;
import com.example.ui.common.Estilos;
import com.example.ui.common.FilterUiHelper;
import com.example.ui.common.PanelHeader;
import com.toedter.calendar.JDateChooser;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Pantalla que muestra el historial completo de auditoría de todos los cambios del sistema.
 *
 * Integrada en el CardLayout principal; se accede desde PantallaCorrecciones mediante
 * el botón "Ver Auditoría". El header estándar incluye el botón Volver que regresa
 * siempre a {@link Constantes.Pantallas#CORRECCIONES}.
 *
 * No habla con la capa de servicios: expone callbacks que CorreccionsController
 * cablea, y este le devuelve los registros ya leídos y filtrados.
 *
 * Filtro de tipos de cambio:
 *   - Por defecto ningún tipo está seleccionado → se muestran todos los registros
 *     (FiltroAuditorias trata la selección vacía como "sin filtro").
 *   - "Limpiar Filtros" restaura también la selección vacía.
 *
 * Convención de celdas:
 *   - valorAnterior / valorNuevo null → cadena vacía
 *   - clienteNombre / materialInfo null → "-"
 */
public class PantallaAuditoria extends JPanel {

    private static final SimpleDateFormat SDF = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    // ── Índices de columna ───────────────────────────────────────────────────
    private static final int COL_FECHA    = 0;
    private static final int COL_CLIENTE  = 1;
    private static final int COL_MATERIAL = 2;
    private static final int COL_TIPO     = 3;
    private static final int COL_ANTERIOR = 4;
    private static final int COL_NUEVO    = 5;
    private static final int COL_MOTIVO   = 6;

    private static final String[] COLUMNAS = {
        "Fecha", "Cliente", "Material", "Tipo de Cambio",
        "Valor Anterior", "Valor Nuevo", "Motivo"
    };

    // ── Callbacks hacia el controller ────────────────────────────────────────
    private Runnable onRecargar      = () -> { };
    private Runnable onFiltrosChanged = () -> { };


    // ── Componentes UI ───────────────────────────────────────────────────────
    private JTable                    tablaAuditoria;
    private JLabel                    lblTotalRegistros;
    private JDateChooser              dateChooserDesde;
    private JDateChooser              dateChooserHasta;
    private CheckableComboBox<String> cmbTiposCambio;
    private CheckableComboBox<String> cmbTipoEquipo;

    // ── Constructor ──────────────────────────────────────────────────────────

    public PantallaAuditoria(CardLayout navegador, JPanel contenedor) {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel panelNorte = new JPanel();
        panelNorte.setLayout(new BoxLayout(panelNorte, BoxLayout.Y_AXIS));

        PanelHeader header = new PanelHeader(
            "Auditoría de Cambios",
            navegador,
            contenedor,
            Constantes.Pantallas.CORRECCIONES
        );
        panelNorte.add(header);
        panelNorte.add(crearPanelFiltros());

        add(panelNorte,         BorderLayout.NORTH);
        add(crearPanelTabla(),  BorderLayout.CENTER);
        add(crearPanelFooter(), BorderLayout.SOUTH);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                onRecargar.run();
            }
        });
    }

    // ── API pública ──────────────────────────────────────────────────────────

    /** Se dispara al mostrar la pantalla: el controller debe releer la auditoría. */
    public void setOnRecargar(Runnable onRecargar) {
        this.onRecargar = onRecargar;
    }

    /** Se dispara cuando el usuario toca un filtro. */
    public void setOnFiltrosChanged(Runnable onFiltrosChanged) {
        this.onFiltrosChanged = onFiltrosChanged;
    }

    /** Criterio tal como lo tiene puesto el usuario en los controles. */
    public FiltroAuditorias.Criterio getCriterio() {
        return new FiltroAuditorias.Criterio(
            aLocalDate(dateChooserDesde.getDate()),
            aLocalDate(dateChooserHasta.getDate()),
            cmbTiposCambio.getSelectedItems(),
            cmbTipoEquipo.getSelectedItems());
    }

    public void mostrarCargando() {
        lblTotalRegistros.setForeground(Color.BLACK);
        lblTotalRegistros.setText("Cargando...");
    }

    /** Vuelca los registros filtrados y actualiza el contador. */
    public void mostrarAuditorias(List<EquipoAuditoria> filtradas, int total) {
        actualizarTabla(filtradas);
        lblTotalRegistros.setForeground(Color.BLACK);
        lblTotalRegistros.setText("Mostrando " + filtradas.size() + " de " + total + " registros");
    }

    public void mostrarError(String mensaje) {
        lblTotalRegistros.setForeground(Color.RED);
        lblTotalRegistros.setText("✗ Error al cargar: " + mensaje);
    }

    private static java.time.LocalDate aLocalDate(java.util.Date fecha) {
        return fecha == null ? null
            : fecha.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
    }

    // ── Construcción de la UI ────────────────────────────────────────────────

    private JPanel crearPanelFiltros() {
        JPanel panelFiltros = new JPanel(new BorderLayout(10, 0));
        panelFiltros.setBorder(BorderFactory.createTitledBorder("Filtros"));

        JPanel panelControles = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

        JLabel lblDesde = new JLabel("Desde:");
        lblDesde.setFont(Estilos.Fuentes.LABEL);
        dateChooserDesde = new JDateChooser();
        dateChooserDesde.setPreferredSize(new Dimension(120, 25));
        dateChooserDesde.setDateFormatString("dd/MM/yyyy");

        JLabel lblHasta = new JLabel("Hasta:");
        lblHasta.setFont(Estilos.Fuentes.LABEL);
        dateChooserHasta = new JDateChooser();
        dateChooserHasta.setPreferredSize(new Dimension(120, 25));
        dateChooserHasta.setDateFormatString("dd/MM/yyyy");

        JLabel lblTipo = new JLabel("Tipo de Cambio:");
        lblTipo.setFont(Estilos.Fuentes.LABEL);

        // ── Tipos de cambio disponibles ───────────────────────────────────────
        // Incluye ADICION_MATERIAL junto con los cuatro tipos previos.
        // El orden refleja el ciclo de vida del equipo (modificaciones → adición → eliminaciones).
        cmbTiposCambio = new CheckableComboBox<>(new String[]{
            "Modificación de Cantidad",
            "Modificación de Código",
            "Adición de Material",
            "Eliminación de Equipo",
            "Eliminación de Material"
        });
        cmbTiposCambio.setFont(Estilos.Fuentes.INPUT);
        cmbTiposCambio.setPreferredSize(new Dimension(200, 25));
        // Por defecto ningún tipo seleccionado → se muestran todos los registros.
        // El usuario selecciona los que quiere filtrar de forma positiva.

        JLabel lblTipoEquipo = new JLabel("Tipo Equipo:");
        lblTipoEquipo.setFont(Estilos.Fuentes.LABEL);
        cmbTipoEquipo = new CheckableComboBox<>(new String[]{"Ortopedia", "Otros"});
        cmbTipoEquipo.setFont(Estilos.Fuentes.INPUT);
        cmbTipoEquipo.setPreferredSize(new Dimension(140, 25));

        panelControles.add(lblDesde);
        panelControles.add(dateChooserDesde);
        panelControles.add(lblHasta);
        panelControles.add(dateChooserHasta);
        panelControles.add(lblTipo);
        panelControles.add(cmbTiposCambio);
        panelControles.add(lblTipoEquipo);
        panelControles.add(cmbTipoEquipo);

        JPanel panelBoton = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        JButton btnLimpiar = new JButton("Limpiar Filtros");
        btnLimpiar.setFont(Estilos.Fuentes.BOTON);
        btnLimpiar.addActionListener(e -> limpiarFiltros());
        panelBoton.add(btnLimpiar);

        panelFiltros.add(panelControles, BorderLayout.CENTER);
        panelFiltros.add(panelBoton,     BorderLayout.EAST);

        FilterUiHelper.bindOnDateChange(() -> onFiltrosChanged.run(), dateChooserDesde, dateChooserHasta);
        cmbTiposCambio.setOnSelectionChange(() -> onFiltrosChanged.run());
        cmbTipoEquipo.setOnSelectionChange(() -> onFiltrosChanged.run());

        return panelFiltros;
    }

    private JPanel crearPanelTabla() {
        JPanel panel = new JPanel(new BorderLayout());

        DefaultTableModel modelo = new DefaultTableModel(COLUMNAS, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        tablaAuditoria = new JTable(modelo);
        tablaAuditoria.setFont(Estilos.Fuentes.TABLA_CONTENIDO);
        tablaAuditoria.setRowHeight(30);
        tablaAuditoria.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        tablaAuditoria.getColumnModel().getColumn(COL_FECHA).setPreferredWidth(140);
        tablaAuditoria.getColumnModel().getColumn(COL_CLIENTE).setPreferredWidth(180);
        tablaAuditoria.getColumnModel().getColumn(COL_MATERIAL).setPreferredWidth(250);
        tablaAuditoria.getColumnModel().getColumn(COL_TIPO).setPreferredWidth(160);
        tablaAuditoria.getColumnModel().getColumn(COL_ANTERIOR).setPreferredWidth(130);
        tablaAuditoria.getColumnModel().getColumn(COL_NUEVO).setPreferredWidth(130);
        tablaAuditoria.getColumnModel().getColumn(COL_MOTIVO).setPreferredWidth(220);

        panel.add(new JScrollPane(tablaAuditoria), BorderLayout.CENTER);
        return panel;
    }

    private JPanel crearPanelFooter() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 3));
        lblTotalRegistros = new JLabel("Cargando...");
        lblTotalRegistros.setFont(Estilos.Fuentes.LABEL);
        panel.add(lblTotalRegistros);
        return panel;
    }

    // ── Volcado a la tabla ───────────────────────────────────────────────────

    private void actualizarTabla(List<EquipoAuditoria> auditorias) {
        DefaultTableModel modelo = (DefaultTableModel) tablaAuditoria.getModel();
        modelo.setRowCount(0);

        for (EquipoAuditoria a : auditorias) {
            String fecha = a.getFechaCambio() != null
                ? SDF.format(java.sql.Timestamp.valueOf(a.getFechaCambio())) : "N/A";

            String valorAnterior = a.getValorAnterior() != null ? a.getValorAnterior() : "";
            String valorNuevo    = a.getValorNuevo()    != null ? a.getValorNuevo()    : "";

            modelo.addRow(new Object[]{
                fecha,
                a.getClienteNombre() != null ? a.getClienteNombre() : "-",
                a.getMaterialInfo()  != null ? a.getMaterialInfo()  : "-",
                FiltroAuditorias.traducirTipoCambio(a.getTipoCambio()),
                valorAnterior,
                valorNuevo,
                a.getMotivo()        != null ? a.getMotivo()        : "-"
            });
        }
    }

    /**
     * Limpia todos los filtros: fechas y selección de tipos.
     * Dejar el CheckableComboBox vacío muestra todos los registros (sin filtro de tipo).
     */
    private void limpiarFiltros() {
        dateChooserDesde.setDate(null);
        dateChooserHasta.setDate(null);
        cmbTiposCambio.clearSelection();
        cmbTipoEquipo.clearSelection();
        onFiltrosChanged.run();
    }
}