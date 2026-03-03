package com.example.features.lotes.view;

import com.example.common.constants.Constantes;
import com.example.common.util.DateTimeDisplayUtils;
import com.example.features.lotes.model.Lote;
import com.example.features.lotes.view.helpers.EstadoCellRenderer;
import com.example.ui.common.PanelHeader;
import com.example.ui.common.Estilos;
import com.example.ui.common.FilterUiHelper;
import com.example.ui.common.TableStyler;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class PantallaVerLotes extends JPanel {

    private final DefaultTableModel modeloTabla;
    private final JTable tablaLotes;
    private JTextField txtFiltroId;
    private JComboBox<String> cmbFiltroEquipo;
    private JComboBox<String> cmbFiltroEstado;
    private JTextField txtFiltroFechaInicio;
    private JButton btnLimpiarFiltros;
    private Runnable onFiltrosChanged;

    public PantallaVerLotes(CardLayout navegador, JPanel contenedor) {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        PanelHeader header = new PanelHeader(
            Constantes.Titulos.VER_LOTES,
            navegador,
            contenedor,
            Constantes.Pantallas.VER_CDE_V2
        );

        JPanel panelNorte = new JPanel(new BorderLayout());
        panelNorte.add(header, BorderLayout.NORTH);
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
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tablaLotes = new JTable(modeloTabla);
        TableStyler.applyStandard(tablaLotes);
        TableStyler.centerColumns(tablaLotes, 2);
        tablaLotes.setRowSelectionAllowed(false);
        tablaLotes.setFillsViewportHeight(true);
        
        // Aplicar renderer de colores para la columna Estado
        tablaLotes.getColumnModel().getColumn(5).setCellRenderer(new EstadoCellRenderer());

        JScrollPane scrollPane = new JScrollPane(tablaLotes);
        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel crearPanelFiltros() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

        JLabel lblId = new JLabel(Constantes.Textos.FILTRO_ID);
        lblId.setFont(Estilos.Fuentes.LABEL);
        txtFiltroId = new JTextField(10);
        txtFiltroId.setFont(Estilos.Fuentes.INPUT);

        JLabel lblEquipo = new JLabel(Constantes.Textos.FILTRO_EQUIPO);
        lblEquipo.setFont(Estilos.Fuentes.LABEL);
        cmbFiltroEquipo = new JComboBox<>();
        cmbFiltroEquipo.setFont(Estilos.Fuentes.INPUT);
        cmbFiltroEquipo.addItem(Constantes.Textos.FILTRO_TODOS);
        cmbFiltroEquipo.addActionListener(e -> notificarCambioFiltros());

        JLabel lblEstado = new JLabel(Constantes.Textos.FILTRO_ESTADO);
        lblEstado.setFont(Estilos.Fuentes.LABEL);
        cmbFiltroEstado = new JComboBox<>();
        cmbFiltroEstado.setFont(Estilos.Fuentes.INPUT);
        cmbFiltroEstado.addItem(Constantes.Textos.FILTRO_TODOS);
        cmbFiltroEstado.addItem("ACTIVO");
        cmbFiltroEstado.addItem("EXITOSO");
        cmbFiltroEstado.addItem("FALLIDO");
        cmbFiltroEstado.addActionListener(e -> notificarCambioFiltros());

        JLabel lblFechaInicio = new JLabel(Constantes.Textos.FILTRO_FECHA_INICIO);
        lblFechaInicio.setFont(Estilos.Fuentes.LABEL);
        txtFiltroFechaInicio = new JTextField(14);
        txtFiltroFechaInicio.setFont(Estilos.Fuentes.INPUT);

        FilterUiHelper.bindOnTextChange(this::notificarCambioFiltros,
            txtFiltroId,
            txtFiltroFechaInicio);

        btnLimpiarFiltros = new JButton(Constantes.Botones.LIMPIAR_FILTROS);
        btnLimpiarFiltros.setFont(Estilos.Fuentes.INPUT);
        btnLimpiarFiltros.addActionListener(e -> limpiarFiltros());

        panel.add(lblId);
        panel.add(txtFiltroId);
        panel.add(lblEquipo);
        panel.add(cmbFiltroEquipo);
        panel.add(lblEstado);
        panel.add(cmbFiltroEstado);
        panel.add(lblFechaInicio);
        panel.add(txtFiltroFechaInicio);
        panel.add(btnLimpiarFiltros);

        return panel;
    }

    public void limpiarFiltros() {
        txtFiltroId.setText("");
        cmbFiltroEquipo.setSelectedItem(Constantes.Textos.FILTRO_TODOS);
        cmbFiltroEstado.setSelectedItem(Constantes.Textos.FILTRO_TODOS);
        txtFiltroFechaInicio.setText("");
        notificarCambioFiltros();
    }

    private void notificarCambioFiltros() {
        if (onFiltrosChanged != null) {
            onFiltrosChanged.run();
        }
    }

    public void actualizarLotes(List<Lote> lotes) {
        modeloTabla.setRowCount(0);

        for (Lote lote : lotes) {
            String fechaInicio = DateTimeDisplayUtils.formatForUi(lote.getFechaInicio());
            String fechaFin = DateTimeDisplayUtils.formatForUi(lote.getFechaFin());
            String estado = lote.getEstado() != null ? lote.getEstado() : "ACTIVO";

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

    public void setOnFiltrosChanged(Runnable listener) {
        this.onFiltrosChanged = listener;
    }

    public String getFiltroId() {
        return txtFiltroId.getText().trim();
    }

    public String getFiltroEquipo() {
        Object selected = cmbFiltroEquipo.getSelectedItem();
        if (selected == null) {
            return "";
        }

        String valor = selected.toString().trim();
        if (Constantes.Textos.FILTRO_TODOS.equalsIgnoreCase(valor)) {
            return "";
        }

        return valor;
    }

    public String getFiltroFechaInicio() {
        return txtFiltroFechaInicio.getText().trim();
    }

    public String getFiltroEstado() {
        Object selected = cmbFiltroEstado.getSelectedItem();
        if (selected == null) {
            return "";
        }

        String valor = selected.toString().trim();
        if (Constantes.Textos.FILTRO_TODOS.equalsIgnoreCase(valor)) {
            return "";
        }

        return valor;
    }

    public void setEquiposFiltro(List<String> equipos) {
        String seleccionActual = (String) cmbFiltroEquipo.getSelectedItem();

        cmbFiltroEquipo.removeAllItems();
        cmbFiltroEquipo.addItem(Constantes.Textos.FILTRO_TODOS);

        if (equipos != null) {
            for (String equipo : equipos) {
                if (equipo != null && !equipo.trim().isEmpty()) {
                    cmbFiltroEquipo.addItem(equipo);
                }
            }
        }

        if (seleccionActual != null) {
            cmbFiltroEquipo.setSelectedItem(seleccionActual);
        }
    }
}
