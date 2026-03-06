package com.example.features.equipos.view;

import com.example.common.constants.Constantes;
import com.example.features.equipos.model.EquipoAuditoria;
import com.example.features.equipos.service.EquipoCorreccionService;
import com.example.ui.common.CheckableComboBox;
import com.example.ui.common.Estilos;
import com.example.ui.common.FilterUiHelper;
import com.example.ui.common.PanelHeader;
import com.toedter.calendar.JDateChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Pantalla que muestra el historial completo de auditoría de todos los cambios del sistema.
 *
 * Integrada en el CardLayout principal; se accede desde PantallaCorrecciones mediante
 * el botón "Ver Auditoría". El header estándar incluye el botón Volver que regresa
 * siempre a {@link Constantes.Pantallas#CORRECCIONES}.
 *
 * El servicio se inyecta de forma diferida mediante {@link #inicializar(EquipoCorreccionService)}
 * para que UiCoordinator pueda construir la pantalla antes de tener el servicio disponible.
 *
 * Filtro de tipos de cambio:
 *   - Por defecto ningún tipo está seleccionado → se muestran todos los registros
 *     (la lógica de {@link #cumpleTipo} ya trata la selección vacía como "sin filtro").
 *   - "Limpiar Filtros" restaura también la selección vacía.
 *
 * Convención de celdas:
 *   - valorAnterior / valorNuevo null → cadena vacía
 *   - clienteNombre / materialInfo null → "-"
 */
public class PantallaAuditoria extends JPanel {

    private static final Logger log = LoggerFactory.getLogger(PantallaAuditoria.class);
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

    // ── Estado ───────────────────────────────────────────────────────────────
    private EquipoCorreccionService correccionService;
    private List<EquipoAuditoria>   auditoriasCargadas = new ArrayList<>();

    // ── Componentes UI ───────────────────────────────────────────────────────
    private JTable                    tablaAuditoria;
    private JLabel                    lblTotalRegistros;
    private JDateChooser              dateChooserDesde;
    private JDateChooser              dateChooserHasta;
    private CheckableComboBox<String> cmbTiposCambio;

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
                if (correccionService != null) cargarAuditoria();
            }
        });
    }

    // ── API pública ──────────────────────────────────────────────────────────

    public void inicializar(EquipoCorreccionService servicio) {
        this.correccionService = servicio;
        cargarAuditoria();
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

        panelControles.add(lblDesde);
        panelControles.add(dateChooserDesde);
        panelControles.add(lblHasta);
        panelControles.add(dateChooserHasta);
        panelControles.add(lblTipo);
        panelControles.add(cmbTiposCambio);

        JPanel panelBoton = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        JButton btnLimpiar = new JButton("Limpiar Filtros");
        btnLimpiar.setFont(Estilos.Fuentes.BOTON);
        btnLimpiar.addActionListener(e -> limpiarFiltros());
        panelBoton.add(btnLimpiar);

        panelFiltros.add(panelControles, BorderLayout.CENTER);
        panelFiltros.add(panelBoton,     BorderLayout.EAST);

        FilterUiHelper.bindOnDateChange(this::aplicarFiltros, dateChooserDesde, dateChooserHasta);
        cmbTiposCambio.setOnSelectionChange(this::aplicarFiltros);

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

    // ── Carga y filtrado ─────────────────────────────────────────────────────

    private void cargarAuditoria() {
        lblTotalRegistros.setText("Cargando...");
        lblTotalRegistros.setForeground(Color.BLACK);

        new Thread(() -> {
            try {
                List<EquipoAuditoria> auditorias = correccionService.obtenerTodasAuditorias();
                SwingUtilities.invokeLater(() -> {
                    auditoriasCargadas = auditorias;
                    // No se preselecciona ningún tipo: clearSelection ya es el estado inicial
                    // del CheckableComboBox, así que no es necesario llamar a nada.
                    aplicarFiltros();
                    log.info("Cargados {} registros de auditoría", auditorias.size());
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    lblTotalRegistros.setText("✗ Error al cargar: " + e.getMessage());
                    lblTotalRegistros.setForeground(Color.RED);
                    log.error("Error al cargar auditoría", e);
                });
            }
        }).start();
    }

    private void aplicarFiltros() {
        List<EquipoAuditoria> filtradas = auditoriasCargadas.stream()
            .filter(this::cumpleFechas)
            .filter(this::cumpleTipo)
            .collect(Collectors.toList());

        actualizarTabla(filtradas);
        lblTotalRegistros.setForeground(Color.BLACK);
        lblTotalRegistros.setText(
            "Mostrando " + filtradas.size() + " de " + auditoriasCargadas.size() + " registros");
    }

    private boolean cumpleFechas(EquipoAuditoria a) {
        if (a.getFechaCambio() == null) return true;
        java.time.LocalDate fecha = a.getFechaCambio().toLocalDate();

        if (dateChooserDesde.getDate() != null) {
            java.time.LocalDate desde = dateChooserDesde.getDate().toInstant()
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            if (fecha.isBefore(desde)) return false;
        }
        if (dateChooserHasta.getDate() != null) {
            java.time.LocalDate hasta = dateChooserHasta.getDate().toInstant()
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            if (fecha.isAfter(hasta)) return false;
        }
        return true;
    }

    /**
     * Filtra por tipo de cambio.
     * Si ningún tipo está seleccionado (lista vacía) se interpreta como "sin filtro"
     * y se muestran todos los registros.
     */
    private boolean cumpleTipo(EquipoAuditoria a) {
        List<String> seleccionados = cmbTiposCambio.getSelectedItems();
        if (seleccionados.isEmpty()) return true;
        return seleccionados.contains(traducirTipoCambio(a.getTipoCambio()));
    }

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
                traducirTipoCambio(a.getTipoCambio()),
                valorAnterior,
                valorNuevo,
                a.getMotivo()        != null ? a.getMotivo()        : "-"
            });
        }
    }

    /**
     * Convierte el tipo de cambio interno (constante en mayúsculas) a la etiqueta
     * legible que se muestra en la tabla y en el filtro CheckableComboBox.
     *
     * Agregar aquí cualquier tipo nuevo garantiza que la traducción sea consistente
     * entre la columna de la tabla y las opciones del filtro.
     */
    private String traducirTipoCambio(String tipoCambio) {
        if (tipoCambio == null) return "Desconocido";
        switch (tipoCambio) {
            case "MODIFICACION_CANTIDAD": return "Modificación de Cantidad";
            case "MODIFICACION_CODIGO":   return "Modificación de Código";
            case "ADICION_MATERIAL":      return "Adición de Material";
            case "ELIMINACION_EQUIPO":    return "Eliminación de Equipo";
            case "ELIMINACION_MATERIAL":  return "Eliminación de Material";
            default:                      return tipoCambio;
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
        aplicarFiltros();
    }
}