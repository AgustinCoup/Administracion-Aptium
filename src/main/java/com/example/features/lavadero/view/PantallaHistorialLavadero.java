package com.example.features.lavadero.view;

import com.example.common.constants.Constantes;
import com.example.common.util.DateTimeDisplayUtils;
import com.example.features.lavadero.model.EstadoIngresoLavadero;
import com.example.features.lavadero.model.IngresoHistorial;
import com.example.ui.common.CheckableComboBox;
import com.example.ui.common.Estilos;
import com.example.ui.common.FilterUiHelper;
import com.example.ui.common.PanelHeader;
import com.example.ui.common.RestriccionesCampo;
import com.example.ui.common.TableStyler;
import com.toedter.calendar.JDateChooser;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * Pantalla de consulta del Historial de Lavadero: una tabla maestra de ingresos con filtros.
 * Doble clic sobre una fila abre {@code DetalleHistorialDialog} con la trazabilidad de ese
 * ingreso (el controller lee ese detalle bajo demanda).
 *
 * <p>Sólo lectura: no importa ningún service ni DAO. Recibe la lista ya filtrada vía
 * {@link #actualizarIngresos(List)} y expone getters de filtros + setters de callbacks; el
 * controller es quien filtra y repinta.</p>
 */
public class PantallaHistorialLavadero extends JPanel {

    private static final String[] COLUMNAS = {
        "ID", "Cliente", "Fecha ingreso", "Peso (kg)", "Bolsas", "Estado"
    };

    /** Estados marcados al entrar: todo menos {@code FINALIZADO} (lo que ya salió del flujo). */
    private static final List<String> ESTADOS_POR_DEFECTO = List.of(
        EstadoIngresoLavadero.PENDIENTE.name(),
        EstadoIngresoLavadero.CLASIFICADO.name(),
        EstadoIngresoLavadero.LAVADO.name());

    private final DefaultTableModel modeloTabla;
    private final JTable            tablaIngresos;

    /** La misma lista que se pintó, para resolver el doble clic a un ingreso. */
    private List<IngresoHistorial> filasVisibles = List.of();

    private JTextField                txtCliente;
    private CheckableComboBox<String> cmbEstado;
    private JDateChooser              dateDesde;
    private JDateChooser              dateHasta;
    private JTextField                txtElemento;
    private JTextField                txtLavarropas;
    private JButton                   btnLimpiar;

    private Runnable onFiltrosChanged;

    /**
     * {@code true} mientras se resetean los filtros programáticamente
     * ({@link #restablecerFiltrosPorDefecto()} / {@link #limpiarFiltros()}): evita contar como
     * cambio del usuario el aviso que igual disparan {@code JDateChooser}/{@code JTextField} al
     * limpiarse (p.ej. {@code setDate(null)} avisa aunque ya fuera {@code null}).
     */
    private boolean silenciandoCallback;

    public PantallaHistorialLavadero(CardLayout navegador, JPanel contenedor) {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        PanelHeader header = new PanelHeader(
            Constantes.Titulos.HISTORIAL_LAVADERO,
            navegador,
            contenedor,
            Constantes.Pantallas.LAVADERO
        );

        JPanel panelNorte = new JPanel(new BorderLayout());
        panelNorte.add(header,              BorderLayout.NORTH);
        panelNorte.add(crearPanelFiltros(), BorderLayout.SOUTH);
        add(panelNorte, BorderLayout.NORTH);

        modeloTabla = new DefaultTableModel(COLUMNAS, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };

        tablaIngresos = new JTable(modeloTabla);
        TableStyler.applyStandard(tablaIngresos);
        TableStyler.centerColumns(tablaIngresos, 0, 2, 3, 4, 5);
        tablaIngresos.setRowSelectionAllowed(true);
        tablaIngresos.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tablaIngresos.setFillsViewportHeight(true);

        add(new JScrollPane(tablaIngresos), BorderLayout.CENTER);

        JLabel lblHint = new JLabel("Doble clic para ver el detalle del ingreso");
        lblHint.setFont(Estilos.Fuentes.LABEL);
        lblHint.setForeground(Estilos.Colores.TEXTO_AYUDA);
        JPanel panelSur = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        panelSur.add(lblHint);
        add(panelSur, BorderLayout.SOUTH);
    }

    private JPanel crearPanelFiltros() {
        JLabel lblCliente = new JLabel("Cliente:");
        lblCliente.setFont(Estilos.Fuentes.LABEL);
        txtCliente = new JTextField(10);
        txtCliente.setFont(Estilos.Fuentes.INPUT);

        JLabel lblEstado = new JLabel(Constantes.Textos.FILTRO_ESTADO);
        lblEstado.setFont(Estilos.Fuentes.LABEL);
        String[] estados = Arrays.stream(EstadoIngresoLavadero.values())
            .map(Enum::name).toArray(String[]::new);
        cmbEstado = new CheckableComboBox<>(estados);
        cmbEstado.setFont(Estilos.Fuentes.INPUT);
        cmbEstado.setPreferredSize(new Dimension(140, 25));

        JLabel lblDesde = new JLabel("Desde:");
        lblDesde.setFont(Estilos.Fuentes.LABEL);
        dateDesde = new JDateChooser();
        dateDesde.setPreferredSize(new Dimension(110, 25));
        dateDesde.setDateFormatString("dd/MM/yyyy");

        JLabel lblHasta = new JLabel("Hasta:");
        lblHasta.setFont(Estilos.Fuentes.LABEL);
        dateHasta = new JDateChooser();
        dateHasta.setPreferredSize(new Dimension(110, 25));
        dateHasta.setDateFormatString("dd/MM/yyyy");

        JLabel lblElemento = new JLabel("Elemento:");
        lblElemento.setFont(Estilos.Fuentes.LABEL);
        txtElemento = new JTextField(8);
        txtElemento.setFont(Estilos.Fuentes.INPUT);

        JLabel lblLavarropas = new JLabel("Lavarropas #:");
        lblLavarropas.setFont(Estilos.Fuentes.LABEL);
        txtLavarropas = new JTextField(4);
        txtLavarropas.setFont(Estilos.Fuentes.INPUT);
        RestriccionesCampo.soloNumeros(txtLavarropas);

        btnLimpiar = new JButton(Constantes.Botones.LIMPIAR_FILTROS);
        btnLimpiar.setFont(Estilos.Fuentes.INPUT);
        btnLimpiar.addActionListener(e -> limpiarFiltros());

        JPanel fila = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        fila.add(lblCliente);    fila.add(txtCliente);
        fila.add(lblEstado);     fila.add(cmbEstado);
        fila.add(lblDesde);      fila.add(dateDesde);
        fila.add(lblHasta);      fila.add(dateHasta);
        fila.add(lblElemento);   fila.add(txtElemento);
        fila.add(lblLavarropas); fila.add(txtLavarropas);
        fila.add(btnLimpiar);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Filtros"));
        panel.add(fila, BorderLayout.CENTER);

        FilterUiHelper.bindOnTextChange(this::notificarCambio, txtCliente, txtElemento, txtLavarropas);
        FilterUiHelper.bindOnDateChange(this::notificarCambio, dateDesde, dateHasta);
        cmbEstado.setOnSelectionChange(this::notificarCambio);

        cmbEstado.setSelectedItems(ESTADOS_POR_DEFECTO);
        return panel;
    }

    // ── API pública ────────────────────────────────────────────────────────────

    /** Repuebla la tabla con la lista ya filtrada y la guarda para el doble clic. */
    public void actualizarIngresos(List<IngresoHistorial> ingresos) {
        filasVisibles = ingresos != null ? List.copyOf(ingresos) : List.of();
        modeloTabla.setRowCount(0);
        for (IngresoHistorial i : filasVisibles) {
            modeloTabla.addRow(new Object[]{
                i.id(),
                i.clienteNombre(),
                DateTimeDisplayUtils.formatForUi(i.fechaIngreso()),
                i.pesoTotalKg() != null ? i.pesoTotalKg() : "—",
                i.cantBolsas(),
                i.estado().name()
            });
        }
    }

    /** @return el ingreso pintado en esa fila del modelo, o {@code null} si el índice no es válido. */
    public IngresoHistorial getIngresoAt(int modelRow) {
        return modelRow >= 0 && modelRow < filasVisibles.size() ? filasVisibles.get(modelRow) : null;
    }

    /**
     * Deja los filtros como al entrar a la pantalla (los tres estados activos, todo lo demás vacío).
     * <b>No notifica</b>: el controller llama a esto y después a {@code solicitarRefresco}, y
     * {@code pintar()} es el único que filtra y repinta, para no mostrar un flash con datos viejos.
     */
    public void restablecerFiltrosPorDefecto() {
        silenciandoCallback = true;
        try {
            txtCliente.setText("");
            txtElemento.setText("");
            txtLavarropas.setText("");
            dateDesde.setDate(null);
            dateHasta.setDate(null);
            cmbEstado.setSelectedItems(ESTADOS_POR_DEFECTO);
        } finally {
            silenciandoCallback = false;
        }
    }

    /** El botón "Limpiar": mismo default que {@link #restablecerFiltrosPorDefecto()}, pero notifica. */
    public void limpiarFiltros() {
        restablecerFiltrosPorDefecto();
        notificarCambio();
    }

    private void notificarCambio() {
        if (silenciandoCallback) return;
        if (onFiltrosChanged != null) onFiltrosChanged.run();
    }

    public void setOnFiltrosChanged(Runnable listener) { this.onFiltrosChanged = listener; }

    public JTable getTablaIngresos() { return tablaIngresos; }

    public String getFiltroCliente()   { return txtCliente.getText().trim(); }
    public String getFiltroElemento()  { return txtElemento.getText().trim(); }

    public List<String> getFiltroEstados() { return cmbEstado.getSelectedItems(); }

    public LocalDate getFiltroDesde() { return toLocalDate(dateDesde); }
    public LocalDate getFiltroHasta() { return toLocalDate(dateHasta); }

    public Integer getFiltroLavarropas() {
        String t = txtLavarropas.getText().trim();
        if (t.isEmpty()) return null;
        try { return Integer.parseInt(t); } catch (NumberFormatException e) { return null; }
    }

    private static LocalDate toLocalDate(JDateChooser chooser) {
        if (chooser.getDate() == null) return null;
        return chooser.getDate().toInstant()
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
    }
}
