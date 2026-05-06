package com.example.features.equipos.view;

import com.example.common.constants.Constantes;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.TipoIngresoOtros;
import com.example.ui.common.CheckableComboBox;
import com.example.ui.common.Estilos;
import com.example.ui.common.FilterUiHelper;
import com.example.ui.common.PanelHeader;
import com.example.ui.common.TableStyler;
import com.toedter.calendar.JDateChooser;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class PantallaVerEquipos extends JPanel {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // ── Filtros compartidos ────────────────────────────────────────────────────
    private final CheckableComboBox<String> cmbEstados;
    private final JTextField                txtCliente;
    private final JDateChooser              dateDesde;
    private final JDateChooser              dateHasta;
    private final JButton                   btnLimpiar;

    // ── Filtros exclusivos por tab ─────────────────────────────────────────────
    private final JTextField                 txtProfesional;
    private final JTextField                 txtPaciente;
    private final JTextField                 txtInstitucion;
    private final CheckableComboBox<String>  cmbTipoIngreso;
    private final JPanel                     panelFiltrosOrt;   // Prof + Pac + Inst
    private final JPanel                     panelFiltrosOtro;  // Tipo Ingreso
    private final JPanel                     panelFiltros;

    // ── Tablas ─────────────────────────────────────────────────────────────────
    private final JTable              tablaOrtopedias;
    private final DefaultTableModel   modeloOrtopedias;
    private final JTable              tablaOtros;
    private final DefaultTableModel   modeloOtros;
    private final JTabbedPane         tabs;

    // ── Listas backing (para recuperar objetos por fila) ──────────────────────
    private List<Equipo>      listaOrtopedias = List.of();
    private List<EquipoOtros> listaOtros      = List.of();

    // ── Labels de conteo ──────────────────────────────────────────────────────
    private final JLabel  lblConteoOrtopedias;
    private final JLabel  lblConteoOtros;
    private final JButton btnImprimirOrtopedias;
    private final JButton btnImprimirOtros;

    public PantallaVerEquipos(CardLayout navegador, JPanel contenedor) {
        setLayout(new BorderLayout(5, 5));

        PanelHeader header = new PanelHeader(
                "Ver Equipos", navegador, contenedor, Constantes.Pantallas.ESTERILIZACION);

        // ── Filtros compartidos ──────────────────────────────────────────────
        String[] estadoOpciones = new String[EstadoEquipo.values().length];
        for (int i = 0; i < EstadoEquipo.values().length; i++) {
            estadoOpciones[i] = EstadoEquipo.values()[i].getNombre();
        }
        cmbEstados = new CheckableComboBox<>(estadoOpciones);
        cmbEstados.setFont(Estilos.Fuentes.INPUT);
        cmbEstados.setPreferredSize(new Dimension(160, 28));

        txtCliente = new JTextField(10);
        txtCliente.setFont(Estilos.Fuentes.INPUT);

        dateDesde = new JDateChooser();
        dateDesde.setPreferredSize(new Dimension(120, 28));
        dateHasta = new JDateChooser();
        dateHasta.setPreferredSize(new Dimension(120, 28));

        btnLimpiar = new JButton(Constantes.Botones.LIMPIAR_FILTROS);
        btnLimpiar.setFont(Estilos.Fuentes.BOTON_PEQUENO);

        // ── Filtros exclusivos Ortopedias ────────────────────────────────────
        txtProfesional = new JTextField(8);
        txtProfesional.setFont(Estilos.Fuentes.INPUT);
        txtPaciente = new JTextField(8);
        txtPaciente.setFont(Estilos.Fuentes.INPUT);
        txtInstitucion = new JTextField(8);
        txtInstitucion.setFont(Estilos.Fuentes.INPUT);

        panelFiltrosOrt = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        panelFiltrosOrt.add(label("Profesional:")); panelFiltrosOrt.add(txtProfesional);
        panelFiltrosOrt.add(label("Paciente:"));    panelFiltrosOrt.add(txtPaciente);
        panelFiltrosOrt.add(label("Institución:")); panelFiltrosOrt.add(txtInstitucion);

        // ── Filtros exclusivos Otros ─────────────────────────────────────────
        String[] tipoOpciones = new String[TipoIngresoOtros.values().length];
        for (int i = 0; i < TipoIngresoOtros.values().length; i++) {
            tipoOpciones[i] = TipoIngresoOtros.values()[i].getNombre();
        }
        cmbTipoIngreso = new CheckableComboBox<>(tipoOpciones);
        cmbTipoIngreso.setFont(Estilos.Fuentes.INPUT);
        cmbTipoIngreso.setPreferredSize(new Dimension(140, 28));

        panelFiltrosOtro = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        panelFiltrosOtro.add(label("Tipo Ingreso:")); panelFiltrosOtro.add(cmbTipoIngreso);

        // ── Panel de filtros completo ────────────────────────────────────────
        panelFiltros = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        panelFiltros.add(label("Estado:"));  panelFiltros.add(cmbEstados);
        panelFiltros.add(label("Cliente:")); panelFiltros.add(txtCliente);
        panelFiltros.add(panelFiltrosOrt);
        panelFiltros.add(panelFiltrosOtro);
        panelFiltros.add(label("Desde:")); panelFiltros.add(dateDesde);
        panelFiltros.add(label("Hasta:")); panelFiltros.add(dateHasta);
        panelFiltros.add(btnLimpiar);

        JPanel norte = new JPanel(new BorderLayout());
        norte.add(header,       BorderLayout.NORTH);
        norte.add(panelFiltros, BorderLayout.SOUTH);
        add(norte, BorderLayout.NORTH);

        // ── Tab Ortopedias ───────────────────────────────────────────────────
        String[] colsOrtopedia = {"Fecha Ingreso", "Cliente", "Profesional", "Paciente", "Institución", "Estado"};
        modeloOrtopedias = new DefaultTableModel(colsOrtopedia, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tablaOrtopedias = new JTable(modeloOrtopedias);
        TableStyler.applyStandard(tablaOrtopedias);
        TableStyler.centerColumns(tablaOrtopedias, 5);
        tablaOrtopedias.getColumnModel().getColumn(5).setCellRenderer(TableStyler.createEstadoRenderer());
        tablaOrtopedias.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ajustarColumnas(tablaOrtopedias, new int[]{130, 160, 130, 130, 130, 120});
        agregarCursorMano(tablaOrtopedias);

        lblConteoOrtopedias = new JLabel("0 registros");
        lblConteoOrtopedias.setFont(Estilos.Fuentes.LABEL);
        btnImprimirOrtopedias = new JButton("Imprimir");
        btnImprimirOrtopedias.setFont(Estilos.Fuentes.BOTON_PEQUENO);

        JPanel panelTabOrtopedia = new JPanel(new BorderLayout(0, 4));
        panelTabOrtopedia.add(new JScrollPane(tablaOrtopedias), BorderLayout.CENTER);
        panelTabOrtopedia.add(crearPanelSurTab(lblConteoOrtopedias, btnImprimirOrtopedias), BorderLayout.SOUTH);

        // ── Tab Otros ────────────────────────────────────────────────────────
        String[] colsOtros = {"Fecha Ingreso", "Cliente", "Tipo Ingreso", "Estado"};
        modeloOtros = new DefaultTableModel(colsOtros, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tablaOtros = new JTable(modeloOtros);
        TableStyler.applyStandard(tablaOtros);
        TableStyler.centerColumns(tablaOtros, 2, 3);
        tablaOtros.getColumnModel().getColumn(3).setCellRenderer(TableStyler.createEstadoRenderer());
        tablaOtros.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ajustarColumnas(tablaOtros, new int[]{130, 220, 120, 120});
        agregarCursorMano(tablaOtros);

        lblConteoOtros = new JLabel("0 registros");
        lblConteoOtros.setFont(Estilos.Fuentes.LABEL);
        btnImprimirOtros = new JButton("Imprimir");
        btnImprimirOtros.setFont(Estilos.Fuentes.BOTON_PEQUENO);

        JPanel panelTabOtros = new JPanel(new BorderLayout(0, 4));
        panelTabOtros.add(new JScrollPane(tablaOtros), BorderLayout.CENTER);
        panelTabOtros.add(crearPanelSurTab(lblConteoOtros, btnImprimirOtros), BorderLayout.SOUTH);

        tabs = new JTabbedPane();
        tabs.setFont(Estilos.Fuentes.LABEL);
        tabs.addTab("Ortopedias", panelTabOrtopedia);
        tabs.addTab("Otros",      panelTabOtros);
        add(tabs, BorderLayout.CENTER);

        // ── Botón Ver Lotes ──────────────────────────────────────────────────
        JButton btnVerLotes = new JButton(Constantes.Botones.VER_LOTES);
        btnVerLotes.setFont(Estilos.Fuentes.BOTON_PEQUENO);
        btnVerLotes.addActionListener(e -> navegador.show(contenedor, Constantes.Pantallas.VER_LOTES));
        JPanel panelSur = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        panelSur.add(btnVerLotes);
        add(panelSur, BorderLayout.SOUTH);

        // Inicializar visibilidad de filtros para tab 0 (Ortopedias)
        actualizarFiltrosParaTab(0);
    }

    // ── API pública ───────────────────────────────────────────────────────────

    public void setDatosOrtopedia(List<Equipo> lista) {
        listaOrtopedias = lista;
        modeloOrtopedias.setRowCount(0);
        for (Equipo eq : lista) {
            modeloOrtopedias.addRow(new Object[]{
                eq.getFechaIngreso() != null ? eq.getFechaIngreso().format(FMT) : "—",
                eq.getClienteNombre(),
                nvl(eq.getProfesionalNombre()),
                nvl(eq.getPacienteNombre()),
                nvl(eq.getInstitucionNombre()),
                eq.getEstado().getNombre()
            });
        }
        lblConteoOrtopedias.setText(lista.size() + " registro" + (lista.size() == 1 ? "" : "s"));
    }

    public void setDatosOtros(List<EquipoOtros> lista) {
        listaOtros = lista;
        modeloOtros.setRowCount(0);
        for (EquipoOtros eq : lista) {
            modeloOtros.addRow(new Object[]{
                eq.getFechaIngreso() != null ? eq.getFechaIngreso().format(FMT) : "—",
                eq.getClienteNombre(),
                eq.getTipoIngreso().getNombre(),
                eq.getEstado().getNombre()
            });
        }
        lblConteoOtros.setText(lista.size() + " registro" + (lista.size() == 1 ? "" : "s"));
    }

    public void actualizarFiltrosParaTab(int tabIndex) {
        panelFiltrosOrt.setVisible(tabIndex == 0);
        panelFiltrosOtro.setVisible(tabIndex == 1);
        panelFiltros.revalidate();
        panelFiltros.repaint();
    }

    /** Registra los callbacks de filtro con el Runnable provisto por el controller. */
    public void configurarFiltros(Runnable onCambio) {
        cmbEstados.setOnSelectionChange(onCambio);
        FilterUiHelper.bindOnTextChange(onCambio, txtCliente, txtProfesional, txtPaciente, txtInstitucion);
        FilterUiHelper.bindOnDateChange(onCambio, dateDesde, dateHasta);
        cmbTipoIngreso.setOnSelectionChange(onCambio);
        btnLimpiar.addActionListener(e -> limpiarFiltros(onCambio));
        tabs.addChangeListener(e -> {
            actualizarFiltrosParaTab(tabs.getSelectedIndex());
            onCambio.run();
        });
    }

    public Equipo      getEquipoOrtopediaAt(int modelRow) { return modelRow >= 0 && modelRow < listaOrtopedias.size() ? listaOrtopedias.get(modelRow) : null; }
    public EquipoOtros getEquipoOtrosAt(int modelRow)     { return modelRow >= 0 && modelRow < listaOtros.size()      ? listaOtros.get(modelRow)      : null; }

    public CheckableComboBox<String>  getCmbEstados()     { return cmbEstados; }
    public JTextField                 getTxtCliente()     { return txtCliente; }
    public JTextField                 getTxtProfesional() { return txtProfesional; }
    public JTextField                 getTxtPaciente()    { return txtPaciente; }
    public JTextField                 getTxtInstitucion() { return txtInstitucion; }
    public CheckableComboBox<String>  getCmbTipoIngreso() { return cmbTipoIngreso; }
    public JDateChooser               getDateDesde()      { return dateDesde; }
    public JDateChooser               getDateHasta()      { return dateHasta; }
    public JTable                     getTablaOrtopedias(){ return tablaOrtopedias; }
    public JTable                     getTablaOtros()     { return tablaOtros; }
    public int                        getTabActivo()      { return tabs.getSelectedIndex(); }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void limpiarFiltros(Runnable onCambio) {
        cmbEstados.clearSelection();
        txtCliente.setText("");
        txtProfesional.setText("");
        txtPaciente.setText("");
        txtInstitucion.setText("");
        cmbTipoIngreso.clearSelection();
        dateDesde.setDate(null);
        dateHasta.setDate(null);
        onCambio.run();
    }

    private JPanel crearPanelSurTab(JLabel lblConteo, JButton btnImprimir) {
        JLabel lblHint = new JLabel("Doble clic para ver detalle");
        lblHint.setFont(Estilos.Fuentes.LABEL);
        lblHint.setForeground(Color.GRAY);
        JPanel panelEste = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        panelEste.add(lblHint);
        panelEste.add(btnImprimir);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(lblConteo, BorderLayout.WEST);
        panel.add(panelEste, BorderLayout.EAST);
        return panel;
    }

    public void setOnImprimirOrtopedias(Runnable r) {
        btnImprimirOrtopedias.addActionListener(e -> r.run());
    }

    public void setOnImprimirOtros(Runnable r) {
        btnImprimirOtros.addActionListener(e -> r.run());
    }

    private void agregarCursorMano(JTable tabla) {
        tabla.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                tabla.setCursor(tabla.rowAtPoint(e.getPoint()) >= 0
                    ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    : Cursor.getDefaultCursor());
            }
        });
    }

    private void ajustarColumnas(JTable tabla, int[] anchos) {
        for (int i = 0; i < anchos.length && i < tabla.getColumnCount(); i++) {
            tabla.getColumnModel().getColumn(i).setPreferredWidth(anchos[i]);
        }
    }

    private JLabel label(String texto) {
        JLabel lbl = new JLabel(texto);
        lbl.setFont(Estilos.Fuentes.LABEL);
        return lbl;
    }

    private String nvl(String s) { return s != null ? s : ""; }
}
