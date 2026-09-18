package com.example.features.equipos.view;

import com.example.common.constants.Constantes;
import com.example.common.paginacion.Pagina;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.TipoIngresoOtros;
import com.example.ui.common.CheckableComboBox;
import com.example.ui.common.Estilos;
import com.example.ui.common.FilterUiHelper;
import com.example.ui.common.PanelHeader;
import com.example.ui.common.PanelPaginacion;
import com.example.ui.common.TableStyler;
import com.toedter.calendar.JDateChooser;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

public class PantallaVerEquipos extends JPanel {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final PanelHeader header;

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
    private       Runnable                   onCambioRef;
    // true mientras se resetean los controles de filtro programáticamente
    // (aplicarFiltroInicial/limpiarFiltros), para no contar como cambio del
    // usuario el aviso que igual disparan JDateChooser/CheckableComboBox al
    // limpiarse (p.ej. JDateChooser.setDate(null) avisa aunque ya fuera null).
    private       boolean                    silenciandoCallback;
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

    // ── Paginación ────────────────────────────────────────────────────────────
    // Una barra por grilla: las dos tablas paginan independientemente, así que pasar a la página 3
    // de ortopedias no mueve la de "otros". Comparten el panel de filtros, no la página.
    private final PanelPaginacion paginacionOrtopedias = new PanelPaginacion();
    private final PanelPaginacion paginacionOtros      = new PanelPaginacion();

    public PantallaVerEquipos(CardLayout navegador, JPanel contenedor) {
        setLayout(new BorderLayout(5, 5));

        header = new PanelHeader(
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

        // ── Panel de filtros completo (dos filas para que no se corten en pantallas pequeñas) ──
        JPanel fila1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        fila1.add(label("Estado:"));  fila1.add(cmbEstados);
        fila1.add(label("Cliente:")); fila1.add(txtCliente);
        fila1.add(label("Desde:"));   fila1.add(dateDesde);
        fila1.add(label("Hasta:"));   fila1.add(dateHasta);
        fila1.add(btnLimpiar);

        JPanel fila2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        fila2.add(panelFiltrosOrt);
        fila2.add(panelFiltrosOtro);

        panelFiltros = new JPanel();
        panelFiltros.setLayout(new BoxLayout(panelFiltros, BoxLayout.Y_AXIS));
        panelFiltros.add(fila1);
        panelFiltros.add(fila2);

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
        panelTabOrtopedia.add(
            crearPanelSurTab(lblConteoOrtopedias, btnImprimirOrtopedias, paginacionOrtopedias),
            BorderLayout.SOUTH);

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
        panelTabOtros.add(
            crearPanelSurTab(lblConteoOtros, btnImprimirOtros, paginacionOtros),
            BorderLayout.SOUTH);

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

    /** Cablea el botón "Actualizar" (y F5) del header a la relectura de la pantalla. */
    public void setAccionRefrescar(Runnable accion) {
        header.setAccionRefrescar(accion);
    }

    /** Muestra la hora del último pintado en el header. */
    public void marcarActualizado() {
        header.marcarActualizado();
    }

    /** Qué hacer cuando el operador pide otra página de ortopedias. Número pedido, base 1. */
    public void setAlCambiarPaginaOrtopedias(IntConsumer accion) {
        paginacionOrtopedias.setAlCambiarPagina(accion);
    }

    /** Qué hacer cuando el operador pide otra página de "otros". Número pedido, base 1. */
    public void setAlCambiarPaginaOtros(IntConsumer accion) {
        paginacionOtros.setAlCambiarPagina(accion);
    }

    /**
     * Vuelca la página de ortopedias: la tabla, la barra de paginación y el conteo, juntos.
     *
     * <p>El conteo muestra el <b>total del filtro</b>, no las filas de la página: decir
     * "50 registros" cuando hay 1 200 que matchean sería peor que no decir nada. Las 50 que se ven
     * las informa la barra ("Mostrando 1-50 de 1200").
     */
    public void setDatosOrtopedia(Pagina<Equipo> pagina) {
        volcarOrtopedias(pagina.contenido(), pagina.totalFilas());
        paginacionOrtopedias.mostrar(pagina);
    }

    private void volcarOrtopedias(List<Equipo> lista, long total) {
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
        lblConteoOrtopedias.setText(textoConteo(total));
    }

    /** Vuelca la página de "otros": la tabla, la barra de paginación y el conteo, juntos. */
    public void setDatosOtros(Pagina<EquipoOtros> pagina) {
        volcarOtros(pagina.contenido(), pagina.totalFilas());
        paginacionOtros.mostrar(pagina);
    }

    private void volcarOtros(List<EquipoOtros> lista, long total) {
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
        lblConteoOtros.setText(textoConteo(total));
    }

    private static String textoConteo(long total) {
        return total + " registro" + (total == 1 ? "" : "s");
    }

    public void actualizarFiltrosParaTab(int tabIndex) {
        panelFiltrosOrt.setVisible(tabIndex == 0);
        panelFiltrosOtro.setVisible(tabIndex == 1);
        panelFiltros.revalidate();
        panelFiltros.repaint();
    }

    /** Registra los callbacks de filtro con el Runnable provisto por el controller. */
    public void configurarFiltros(Runnable onCambio) {
        this.onCambioRef = onCambio;
        Runnable notificar = () -> { if (!silenciandoCallback) onCambio.run(); };
        cmbEstados.setOnSelectionChange(notificar);
        FilterUiHelper.bindOnTextChange(notificar, txtCliente, txtProfesional, txtPaciente, txtInstitucion);
        FilterUiHelper.bindOnDateChange(notificar, dateDesde, dateHasta);
        cmbTipoIngreso.setOnSelectionChange(notificar);
        btnLimpiar.addActionListener(e -> limpiarFiltros(onCambio));
        // Cambiar de pestaña sólo muestra u oculta los filtros exclusivos de cada grilla; sus
        // valores no cambian, así que el resultado de las dos consultas es el mismo que ya está en
        // pantalla. Antes esto notificaba —era gratis, porque re-filtraba un snapshot en memoria—;
        // con paginación cada notificación es una lectura, y además volvería las dos grillas a la
        // página 1: el operador perdería la página por mirar la otra pestaña.
        tabs.addChangeListener(e -> actualizarFiltrosParaTab(tabs.getSelectedIndex()));
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

    public void limpiarFiltros() { if (onCambioRef != null) limpiarFiltros(onCambioRef); }

    private void limpiarFiltros(Runnable onCambio) {
        silenciandoCallback = true;
        try {
            cmbEstados.clearSelection();
            limpiarCamposFiltro();
        } finally {
            silenciandoCallback = false;
        }
        onCambio.run();
    }

    /**
     * Aplica el filtro por defecto de la pantalla: oculta los equipos ya
     * entregados (se ven en el historial a demanda, no en la vista principal).
     * Distinto de {@link #limpiarFiltros()}, que muestra todos los estados sin
     * excepción.
     *
     * <p>Como el de la otra pantalla del CDE, <b>es un filtro de la consulta</b>: los estados
     * tildados viajan en el {@code WHERE} y el total refleja el filtro, no el universo. No queda
     * ningún filtro de vista — filtrar en memoria una página de 50 daría 8 de 50 en vez de los 8
     * primeros de los que matchean.
     *
     * <p>No dispara el callback de {@link #configurarFiltros}: quien navega a esta pantalla pide la
     * página justo después (ver {@code VerEquiposController}), y repintar acá mostraría un instante
     * la página de la visita anterior. Usar {@link #aplicarFiltroInicialYNotificar()} si hace falta
     * el repintado inmediato.
     */
    public void aplicarFiltroInicial() {
        silenciandoCallback = true;
        try {
            cmbEstados.setSelectedItems(estadosVisiblesPorDefecto());
            limpiarCamposFiltro();
        } finally {
            silenciandoCallback = false;
        }
    }

    /** Como {@link #aplicarFiltroInicial()}, pero además dispara el callback de {@link #configurarFiltros}. */
    public void aplicarFiltroInicialYNotificar() {
        aplicarFiltroInicial();
        if (onCambioRef != null) onCambioRef.run();
    }

    private void limpiarCamposFiltro() {
        txtCliente.setText("");
        txtProfesional.setText("");
        txtPaciente.setText("");
        txtInstitucion.setText("");
        cmbTipoIngreso.clearSelection();
        dateDesde.setDate(null);
        dateHasta.setDate(null);
    }

    private List<String> estadosVisiblesPorDefecto() {
        return Arrays.stream(EstadoEquipo.values())
            .filter(estado -> estado != EstadoEquipo.ENTREGADO)
            .map(EstadoEquipo::getNombre)
            .collect(Collectors.toList());
    }

    private JPanel crearPanelSurTab(JLabel lblConteo, JButton btnImprimir,
                                    PanelPaginacion paginacion) {
        JLabel lblHint = new JLabel("Doble clic para ver detalle");
        lblHint.setFont(Estilos.Fuentes.LABEL);
        lblHint.setForeground(Color.GRAY);
        JPanel panelEste = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        panelEste.add(lblHint);
        panelEste.add(btnImprimir);
        JPanel fila = new JPanel(new BorderLayout());
        fila.add(lblConteo, BorderLayout.WEST);
        fila.add(panelEste, BorderLayout.EAST);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(paginacion, BorderLayout.NORTH);
        panel.add(fila,       BorderLayout.SOUTH);
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
