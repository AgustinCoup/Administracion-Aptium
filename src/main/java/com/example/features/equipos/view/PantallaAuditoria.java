package com.example.features.equipos.view;

import com.example.features.equipos.model.EquipoAuditoria;
import com.example.features.equipos.service.EquipoCorreccionService;
import com.example.ui.common.CheckableComboBox;
import com.example.ui.common.Estilos;
import com.example.ui.common.FilterUiHelper;
import com.toedter.calendar.JDateChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Pantalla para visualizar el historial de auditoría de TODOS los cambios realizados.
 * Muestra todos los cambios en el sistema: modificaciones de cantidad/código y eliminaciones de equipos.
 */
public class PantallaAuditoria extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(PantallaAuditoria.class);
    private static final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    private EquipoCorreccionService correccionService;
    private JTable tablaAuditoria;
    private JLabel lblTotalRegistros;
    private JDateChooser dateChooserDesde;
    private JDateChooser dateChooserHasta;
    private CheckableComboBox<String> cmbTiposCambio;
    private List<EquipoAuditoria> auditoriasCargadas;

    public PantallaAuditoria(EquipoCorreccionService correccionService) {
        this.correccionService = correccionService;
        this.auditoriasCargadas = new ArrayList<>();

        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Panel superior (encabezado + filtros)
        add(crearPanelNorte(), BorderLayout.NORTH);

        // Tabla de auditoría
        add(crearPanelTabla(), BorderLayout.CENTER);

        // Footer
        add(crearPanelInferior(), BorderLayout.SOUTH);

        // Cargar datos
        cargarAuditoria();
    }

    /**
     * Panel norte con encabezado y filtros combinados.
     */
    private JPanel crearPanelNorte() {
        JPanel panelNorte = new JPanel(new BorderLayout(5, 5));
        panelNorte.add(crearPanelEncabezado(), BorderLayout.NORTH);
        panelNorte.add(crearPanelFiltros(), BorderLayout.SOUTH);
        return panelNorte;
    }

    /**
     * Panel de encabezado con título e información.
     */
    private JPanel crearPanelEncabezado() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Historial Completo de Cambios"));

        JLabel lblTitulo = new JLabel("Todos los Registros de Auditoría");
        lblTitulo.setFont(Estilos.Fuentes.TITULO);

        lblTotalRegistros = new JLabel("Cargando...");
        lblTotalRegistros.setFont(Estilos.Fuentes.LABEL);

        panel.add(lblTitulo);
        panel.add(new JSeparator(JSeparator.VERTICAL));
        panel.add(lblTotalRegistros);

        return panel;
    }

    /**
     * Panel de filtros con fecha rango y tipos de cambio.
     */
    private JPanel crearPanelFiltros() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Filtros"));

        // Filtro Fecha Desde
        JLabel lblDesde = new JLabel("Desde:");
        lblDesde.setFont(Estilos.Fuentes.LABEL);
        dateChooserDesde = new JDateChooser();
        dateChooserDesde.setPreferredSize(new Dimension(120, 25));
        dateChooserDesde.setDateFormatString("dd/MM/yyyy");

        // Filtro Fecha Hasta
        JLabel lblHasta = new JLabel("Hasta:");
        lblHasta.setFont(Estilos.Fuentes.LABEL);
        dateChooserHasta = new JDateChooser();
        dateChooserHasta.setPreferredSize(new Dimension(120, 25));
        dateChooserHasta.setDateFormatString("dd/MM/yyyy");

        // Filtro Tipos de Cambio
        JLabel lblTipo = new JLabel("Tipo de Cambio:");
        lblTipo.setFont(Estilos.Fuentes.LABEL);
        cmbTiposCambio = new CheckableComboBox<>(new String[]{
            "Modificación de Cantidad",
            "Modificación de Código",
            "Eliminación de Equipo",
            "Eliminación de Material"
        });
        cmbTiposCambio.setFont(Estilos.Fuentes.INPUT);
        cmbTiposCambio.setPreferredSize(new Dimension(200, 25));

        // Botón Limpiar Filtros
        JButton btnLimpiarFiltros = new JButton("Limpiar Filtros");
        btnLimpiarFiltros.setFont(Estilos.Fuentes.BOTON);
        btnLimpiarFiltros.addActionListener(e -> limpiarFiltros());

        // Vincular cambios de filtros
        FilterUiHelper.bindOnDateChange(this::aplicarFiltros, dateChooserDesde, dateChooserHasta);
        cmbTiposCambio.setOnSelectionChange(this::aplicarFiltros);

        panel.add(lblDesde);
        panel.add(dateChooserDesde);
        panel.add(lblHasta);
        panel.add(dateChooserHasta);
        panel.add(lblTipo);
        panel.add(cmbTiposCambio);
        panel.add(btnLimpiarFiltros);

        return panel;
    }

    /**
     * Panel central con la tabla de auditoría.
     */
    private JPanel crearPanelTabla() {
        JPanel panel = new JPanel(new BorderLayout());

        String[] columnasAuditoria = {"Fecha", "Tipo de Cambio", "Valor Anterior", "Valor Nuevo", "Motivo"};
        DefaultTableModel modelAuditoria = new DefaultTableModel(columnasAuditoria, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tablaAuditoria = new JTable(modelAuditoria);
        tablaAuditoria.setFont(Estilos.Fuentes.TABLA_CONTENIDO);
        tablaAuditoria.setRowHeight(30);
        tablaAuditoria.getColumnModel().getColumn(0).setPreferredWidth(150);
        tablaAuditoria.getColumnModel().getColumn(1).setPreferredWidth(150);
        tablaAuditoria.getColumnModel().getColumn(2).setPreferredWidth(150);
        tablaAuditoria.getColumnModel().getColumn(3).setPreferredWidth(150);
        tablaAuditoria.getColumnModel().getColumn(4).setPreferredWidth(250);

        // Permitir seleccionar filas
        tablaAuditoria.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scrollPane = new JScrollPane(tablaAuditoria);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Panel inferior con botones.
     */
    private JPanel crearPanelInferior() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));

        JButton btnCerrar = new JButton("Cerrar");
        btnCerrar.setFont(Estilos.Fuentes.BOTON);
        btnCerrar.addActionListener(e -> cerrarVentana());

        panel.add(btnCerrar);

        return panel;
    }

    /**
     * Carga los datos de auditoría desde el servicio (TODOS los registros).
     */
    private void cargarAuditoria() {
        new Thread(() -> {
            try {
                List<EquipoAuditoria> auditorias = correccionService.obtenerTodasAuditorias();
                auditoriasCargadas = auditorias;

                SwingUtilities.invokeLater(() -> {
                    cmbTiposCambio.selectAll(); // Por defecto mostrar todos
                    aplicarFiltros();
                    log.info("Cargados {} registros de auditoría", auditorias.size());
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    lblTotalRegistros.setText("✗ Error al cargar auditoría: " + e.getMessage());
                    lblTotalRegistros.setForeground(Color.RED);
                    log.error("Error al cargar auditoría", e);
                });
            }
        }).start();
    }

    /**
     * Aplica los filtros actuales a la tabla.
     */
    private void aplicarFiltros() {
        // Filtrar por fecha y tipo
        List<EquipoAuditoria> filtradas = auditoriasCargadas.stream()
            .filter(this::cumpleFechas)
            .filter(this::cumpleTipo)
            .collect(Collectors.toList());

        actualizarTabla(filtradas);
        lblTotalRegistros.setText("Mostrando: " + filtradas.size() + " de " + auditoriasCargadas.size() + " registros");
    }

    /**
     * Verifica si una auditoría cumple con el rango de fechas.
     */
    private boolean cumpleFechas(EquipoAuditoria auditoria) {
        if (auditoria.getFechaCambio() == null) return true;

        java.time.LocalDateTime fechaAuditoria = auditoria.getFechaCambio();

        if (dateChooserDesde.getDate() != null) {
            java.time.LocalDate fechaDesde = dateChooserDesde.getDate().toInstant()
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            if (fechaAuditoria.toLocalDate().isBefore(fechaDesde)) {
                return false;
            }
        }

        if (dateChooserHasta.getDate() != null) {
            java.time.LocalDate fechaHasta = dateChooserHasta.getDate().toInstant()
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            if (fechaAuditoria.toLocalDate().isAfter(fechaHasta)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Verifica si una auditoría cumple con los tipos de cambio seleccionados.
     */
    private boolean cumpleTipo(EquipoAuditoria auditoria) {
        List<String> tiposSeleccionados = cmbTiposCambio.getSelectedItems();
        if (tiposSeleccionados.isEmpty()) {
            return true; // Si no hay tipos seleccionados, mostrar todos
        }

        String tipoDeAuditoria = traduciaTipoCambio(auditoria.getTipoCambio());
        return tiposSeleccionados.contains(tipoDeAuditoria);
    }

    /**
     * Actualiza la tabla con los registros de auditoría filtrados.
     */
    private void actualizarTabla(List<EquipoAuditoria> auditorias) {
        DefaultTableModel model = (DefaultTableModel) tablaAuditoria.getModel();
        model.setRowCount(0);

        for (EquipoAuditoria auditoria : auditorias) {
            String fechaFormato = auditoria.getFechaCambio() != null ?
                sdf.format(java.sql.Timestamp.valueOf(auditoria.getFechaCambio())) : "N/A";

            String tipoCambio = traduciaTipoCambio(auditoria.getTipoCambio());

            model.addRow(new Object[]{
                fechaFormato,
                tipoCambio,
                auditoria.getValorAnterior() != null ? auditoria.getValorAnterior() : "-",
                auditoria.getValorNuevo() != null ? auditoria.getValorNuevo() : "-",
                auditoria.getMotivo() != null ? auditoria.getMotivo() : "-"
            });
        }

        // Colorear filas según tipo de cambio
        for (int i = 0; i < model.getRowCount(); i++) {
            String tipo = (String) model.getValueAt(i, 1);
            if ("Eliminación de Equipo".equals(tipo)) {
                tablaAuditoria.setSelectionBackground(new Color(255, 200, 200));
            }
        }
    }

    /**
     * Traduce el tipo de cambio a texto legible.
     */
    private String traduciaTipoCambio(String tipoCambio) {
        if (tipoCambio == null) return "Desconocido";
        switch (tipoCambio) {
            case "MODIFICACION_CANTIDAD":
                return "Modificación de Cantidad";
            case "MODIFICACION_CODIGO":
                return "Modificación de Código";
            case "ELIMINACION_EQUIPO":
                return "Eliminación de Equipo";
            case "ELIMINACION_MATERIAL":
                return "Eliminación de Material";
            default:
                return tipoCambio;
        }
    }

    /**
     * Limpia todos los filtros.
     */
    private void limpiarFiltros() {
        dateChooserDesde.setDate(null);
        dateChooserHasta.setDate(null);
        cmbTiposCambio.selectAll();
        aplicarFiltros();
    }

    /**
     * Cierra la ventana.
     */
    private void cerrarVentana() {
        SwingUtilities.getWindowAncestor(this).dispose();
    }
}
