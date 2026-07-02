package com.example.features.lavadero.view;

import com.example.common.constants.Constantes;
import com.example.common.util.DateTimeDisplayUtils;
import com.example.features.lavadero.model.CicloLavadero;
import com.example.ui.common.Estilos;
import com.example.ui.common.FilterUiHelper;
import com.example.ui.common.PanelHeader;
import com.example.ui.common.TableStyler;
import com.toedter.calendar.JDateChooser;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.util.List;

public class PantallaVerCiclos extends JPanel {

    private final DefaultTableModel modeloTabla;
    private final JTable            tablaCiclos;

    private JTextField   txtFiltroNumero;
    private JDateChooser dateChooserDesde;
    private JDateChooser dateChooserHasta;
    private JButton      btnLimpiar;

    private Runnable onFiltrosChanged;

    public PantallaVerCiclos(CardLayout navegador, JPanel contenedor) {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        PanelHeader header = new PanelHeader(
            Constantes.Titulos.VER_CICLOS_LAVADERO,
            navegador,
            contenedor,
            Constantes.Pantallas.LAVADERO
        );

        JPanel panelNorte = new JPanel(new BorderLayout());
        panelNorte.add(header,              BorderLayout.NORTH);
        panelNorte.add(crearPanelFiltros(), BorderLayout.SOUTH);
        add(panelNorte, BorderLayout.NORTH);

        modeloTabla = new DefaultTableModel(
            new Object[]{"ID", "Lavarropas", "Jabón", "mL Jabón",
                         "Suavizante", "Potenciador", "mL Totales", "Inicio", "Fin"},
            0
        ) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };

        tablaCiclos = new JTable(modeloTabla);
        TableStyler.applyStandard(tablaCiclos);
        TableStyler.centerColumns(tablaCiclos, 0, 1, 3, 4, 5, 6);
        tablaCiclos.setRowSelectionAllowed(false);
        tablaCiclos.setFillsViewportHeight(true);

        add(new JScrollPane(tablaCiclos), BorderLayout.CENTER);
    }

    private JPanel crearPanelFiltros() {
        JLabel lblNumero = new JLabel("Lavarropas #:");
        lblNumero.setFont(Estilos.Fuentes.LABEL);
        txtFiltroNumero = new JTextField(4);
        txtFiltroNumero.setFont(Estilos.Fuentes.INPUT);

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

        btnLimpiar = new JButton(Constantes.Botones.LIMPIAR_FILTROS);
        btnLimpiar.setFont(Estilos.Fuentes.INPUT);
        btnLimpiar.addActionListener(e -> limpiarFiltros());

        JPanel fila = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        fila.add(lblNumero); fila.add(txtFiltroNumero);
        fila.add(lblDesde);  fila.add(dateChooserDesde);
        fila.add(lblHasta);  fila.add(dateChooserHasta);
        fila.add(btnLimpiar);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Filtros"));
        panel.add(fila, BorderLayout.CENTER);

        FilterUiHelper.bindOnTextChange(this::notificarCambio, txtFiltroNumero);
        FilterUiHelper.bindOnDateChange(this::notificarCambio, dateChooserDesde, dateChooserHasta);

        return panel;
    }

    // ── API pública ────────────────────────────────────────────────────────────

    public void actualizarCiclos(List<CicloLavadero> ciclos) {
        modeloTabla.setRowCount(0);
        for (CicloLavadero c : ciclos) {
            modeloTabla.addRow(new Object[]{
                c.getId(),
                c.getLavarropasNumero(),
                c.getJabon().getNombre(),
                c.getLitrosJabon(),
                c.isSuavizante()   ? "Sí" : "No",
                c.isPotenciador()  ? "Sí" : "No",
                c.getLitrosTotales() != null ? c.getLitrosTotales() : "—",
                DateTimeDisplayUtils.formatForUi(c.getFechaInicio()),
                DateTimeDisplayUtils.formatForUi(c.getFechaFin())
            });
        }
    }

    public void limpiarFiltros() {
        txtFiltroNumero.setText("");
        dateChooserDesde.setDate(null);
        dateChooserHasta.setDate(null);
        notificarCambio();
    }

    private void notificarCambio() {
        if (onFiltrosChanged != null) onFiltrosChanged.run();
    }

    public Integer getFiltroNumero() {
        String t = txtFiltroNumero.getText().trim();
        if (t.isEmpty()) return null;
        try { return Integer.parseInt(t); } catch (NumberFormatException e) { return null; }
    }

    public LocalDate getFiltroFechaDesde() {
        if (dateChooserDesde.getDate() == null) return null;
        return dateChooserDesde.getDate().toInstant()
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
    }

    public LocalDate getFiltroFechaHasta() {
        if (dateChooserHasta.getDate() == null) return null;
        return dateChooserHasta.getDate().toInstant()
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
    }

    public void setOnFiltrosChanged(Runnable listener) { this.onFiltrosChanged = listener; }

    public void setOnLimpiar(Runnable listener) {
        btnLimpiar.addActionListener(e -> listener.run());
    }
}
