package com.example.features.lotes.view.helpers;

import com.example.ui.common.Estilos;
import com.example.ui.common.RowTooltipTable;
import com.example.ui.common.TableStyler;
import com.example.ui.common.LabelFactory;
import com.example.ui.common.dnd.TableSelectionSupport;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.example.common.constants.Constantes;
import com.example.features.lotes.model.OcupacionAutoclave;

/**
 * Panel reutilizable con la lógica de gestión de lotes.
 * SOLO el contenido (sin header de navegación ni footer).
 */
public class PanelLotesContenido extends JPanel {

    private JTable tablaAutoclaves;
    private AutoclaveTableModel modeloAutoclaves;
    private MaterialLoteTableModel modeloDisponibles;
    private MaterialLoteTableModel modeloAutoclave;
    private RowTooltipTable tablaDisponibles;
    private RowTooltipTable tablaAutoclave;
    private JLabel lblCapacidad;
    private JProgressBar barraCapacidad;
    private JTextField txtVolumenManual;
    private JLabel lblVolumenManualPorcentaje;
    private JButton btnLanzar;
    private JButton btnFinalizar;
    private JButton btnMarcarFallo;
    private JButton btnQuitar;
    private Consumer<AutoclaveItem> onAutoclaveSeleccionado;

    private Runnable onVolumenManualChanged;

    private static final Color COLOR_ROJO  = new Color(220, 50,  50);
    private static final Color COLOR_VERDE = new Color(76,  175, 80);

    // Anchos preferidos para las columnas de MaterialLoteTableModel:
    // Material(0), Cantidad(1), Cliente(2)
    // Los minWidth se calculan dinámicamente a partir del texto del encabezado,
    // garantizando que el título siempre sea completamente legible.
    private static final int[] COL_PREF_WIDTHS    = { 150,  65,  800 };
    private static final int[] COL_MAX_WIDTHS     = { 400, 9999, 9999 };
    private static final int   COL_HEADER_PADDING = 16; // px de margen a cada lado del texto del header

    public PanelLotesContenido() {
        setLayout(new BorderLayout());
        setBorder(Estilos.Espaciados.BORDE_PRINCIPAL);

        // ── Panel central: tablas de materiales ──────────────────────────────
        JPanel centro = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;

        // Tabla: materiales disponibles
        JLabel lblDisponibles = LabelFactory.createSectionLabel(
                Constantes.Textos.TABLA_MATERIALES_DISPONIBLES);
        gbc.gridx = 0; gbc.gridy = 0; gbc.weighty = 0;
        centro.add(lblDisponibles, gbc);

        modeloDisponibles = new MaterialLoteTableModel();
        tablaDisponibles = new RowTooltipTable(modeloDisponibles);
        TableStyler.applyStandard(tablaDisponibles);
        TableStyler.centerColumns(tablaDisponibles, 1);
        TableSelectionSupport.enableMultiSelection(tablaDisponibles);
        tablaDisponibles.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        ajustarColumnasMateriales(tablaDisponibles);

        JScrollPane scrollDisponibles = new JScrollPane(tablaDisponibles,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        gbc.gridy = 1; gbc.weighty = 0.5;
        centro.add(scrollDisponibles, gbc);

        // Tabla: materiales cargados en el equipo
        JLabel lblAutoclave = LabelFactory.createSectionLabel("Materiales cargados en el equipo");
        gbc.gridy = 2; gbc.weighty = 0;
        centro.add(lblAutoclave, gbc);

        modeloAutoclave = new MaterialLoteTableModel();
        tablaAutoclave = new RowTooltipTable(modeloAutoclave);
        TableStyler.applyStandard(tablaAutoclave);
        TableStyler.centerColumns(tablaAutoclave, 1);
        TableSelectionSupport.enableMultiSelection(tablaAutoclave);
        tablaAutoclave.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        ajustarColumnasMateriales(tablaAutoclave);

        JScrollPane scrollAutoclave = new JScrollPane(tablaAutoclave,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        gbc.gridy = 3; gbc.weighty = 0.5;
        centro.add(scrollAutoclave, gbc);

        add(centro, BorderLayout.CENTER);

        // ── Panel lateral: autoclaves y capacidad ────────────────────────────
        JPanel lateral = new JPanel(new BorderLayout());

        JLabel lblAutoclaves = LabelFactory.createSectionLabel("Equipos de Esterilización");
        lateral.add(lblAutoclaves, BorderLayout.NORTH);

        modeloAutoclaves = new AutoclaveTableModel();
        tablaAutoclaves = new JTable(modeloAutoclaves);
        tablaAutoclaves.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        TableStyler.applyStandard(tablaAutoclaves);
        TableStyler.centerColumns(tablaAutoclaves, 1, 2);
        tablaAutoclaves.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        ajustarColumnasAutoclaves(tablaAutoclaves);

        // Forzar el ancho mínimo del panel lateral igual a la suma de los minWidth
        // de las columnas + margen del scroll, para evitar la scrollbar horizontal.
        int anchoMinimoTabla = sumarMinWidthColumnas(tablaAutoclaves);
        int anchoLateral = anchoMinimoTabla + 4; // +4 por el borde del JScrollPane
        lateral.setPreferredSize(new Dimension(anchoLateral, 0));
        lateral.setMinimumSize(new Dimension(anchoLateral, 0));

        JScrollPane scrollAutoclaves = new JScrollPane(tablaAutoclaves,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        lateral.add(scrollAutoclaves, BorderLayout.CENTER);

        JPanel panelCapacidad = crearPanelCapacidad();
        lateral.add(panelCapacidad, BorderLayout.SOUTH);

        add(lateral, BorderLayout.EAST);

        // ── Botones ──────────────────────────────────────────────────────────
        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        panelBotones.setBorder(Estilos.Espaciados.BORDE_PRINCIPAL);

        btnQuitar    = new JButton(Constantes.Botones.QUITAR);
        btnFinalizar = new JButton(Constantes.Botones.MARCAR_LOTE_FINALIZADO);
        btnMarcarFallo = new JButton(Constantes.Botones.MARCAR_LOTE_FALLO);
        btnLanzar    = new JButton(Constantes.Botones.LANZAR_LOTE);

        btnQuitar.setFont(Estilos.Fuentes.BOTON);
        btnFinalizar.setFont(Estilos.Fuentes.BOTON);
        btnMarcarFallo.setFont(Estilos.Fuentes.BOTON);
        btnLanzar.setFont(Estilos.Fuentes.BOTON);

        panelBotones.add(btnQuitar);
        panelBotones.add(btnFinalizar);
        panelBotones.add(btnMarcarFallo);
        panelBotones.add(btnLanzar);

        add(panelBotones, BorderLayout.SOUTH);

        tablaAutoclaves.getSelectionModel().addListSelectionListener(crearListenerAutoclave());
    }

    /**
     * Aplica anchos a las columnas de una tabla de materiales.
     * El minWidth de cada columna se mide dinámicamente a partir del texto del encabezado,
     * así el título siempre es completamente legible sin importar el tamaño de la ventana.
     * Material y Cliente crecen ilimitadamente; Cantidad y Volumen Total también pueden
     * crecer pero su mínimo queda garantizado por el header.
     */
    private static void ajustarColumnasMateriales(JTable tabla) {
        TableColumnModel cm = tabla.getColumnModel();
        for (int i = 0; i < cm.getColumnCount() && i < COL_PREF_WIDTHS.length; i++) {
            TableColumn col = cm.getColumn(i);
            int minWidth = calcularMinWidthHeader(tabla, col);
            col.setMinWidth(minWidth);
            col.setPreferredWidth(Math.max(COL_PREF_WIDTHS[i], minWidth));
            col.setMaxWidth(COL_MAX_WIDTHS[i]);
        }
    }

    /**
     * Aplica anchos a las columnas de la tabla de autoclaves.
     * El minWidth de cada columna se mide dinámicamente a partir del texto del encabezado.
     * Todas las columnas son elásticas para aprovechar el panel lateral.
     */
    private static void ajustarColumnasAutoclaves(JTable tabla) {
        TableColumnModel cm = tabla.getColumnModel();
        for (int i = 0; i < cm.getColumnCount(); i++) {
            TableColumn col = cm.getColumn(i);
            int minWidth = calcularMinWidthHeader(tabla, col);
            col.setMinWidth(minWidth);
            col.setPreferredWidth(Math.max(col.getPreferredWidth(), minWidth));
        }
    }

    /**
     * Calcula el ancho mínimo para una columna basándose en el texto de su encabezado.
     * Usa la fuente real del header renderer para máxima precisión.
     *
     * @param tabla La tabla que contiene la columna
     * @param col   La columna a medir
     * @return Ancho en píxeles = ancho del texto + COL_HEADER_PADDING * 2
     */
    private static int calcularMinWidthHeader(JTable tabla, TableColumn col) {
        Object headerValue = col.getHeaderValue();
        if (headerValue == null) return COL_HEADER_PADDING * 2;

        // Obtener la fuente real del header renderer
        Font font = tabla.getTableHeader() != null
                ? tabla.getTableHeader().getFont()
                : tabla.getFont();

        FontMetrics fm = tabla.getFontMetrics(font);
        int textWidth = fm.stringWidth(headerValue.toString());
        return textWidth + COL_HEADER_PADDING * 2;
    }

    /**
     * Suma los minWidth de todas las columnas de una tabla.
     * Usado para calcular el ancho mínimo del panel contenedor.
     */
    private static int sumarMinWidthColumnas(JTable tabla) {
        TableColumnModel cm = tabla.getColumnModel();
        int total = 0;
        for (int i = 0; i < cm.getColumnCount(); i++) {
            total += cm.getColumn(i).getMinWidth();
        }
        return total;
    }

    // ── Panel de capacidad ───────────────────────────────────────────────────

    private JPanel crearPanelCapacidad() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 0, 2, 0);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;

        lblCapacidad = new JLabel(Constantes.Textos.CAPACIDAD_AUTOCLAVE);
        lblCapacidad.setFont(Estilos.Fuentes.LABEL);
        gbc.gridy = 0;
        panel.add(lblCapacidad, gbc);

        barraCapacidad = new JProgressBar(0, 100);
        barraCapacidad.setValue(0);
        barraCapacidad.setStringPainted(true);
        barraCapacidad.setForeground(COLOR_ROJO);
        gbc.gridy = 1;
        panel.add(barraCapacidad, gbc);

        JLabel lblEtiquetaManual = new JLabel("Volumen final:");
        lblEtiquetaManual.setFont(Estilos.Fuentes.LABEL);
        gbc.gridy = 2;
        gbc.insets = new Insets(6, 0, 2, 0);
        panel.add(lblEtiquetaManual, gbc);

        JPanel filaVolumen = new JPanel(new BorderLayout(4, 0));
        txtVolumenManual = new JTextField(6);
        txtVolumenManual.setFont(Estilos.Fuentes.LABEL);
        txtVolumenManual.setEnabled(false);
        txtVolumenManual.setToolTipText(
                "Modifique el volumen final real del lote. Los valores del catálogo son orientativos.");

        lblVolumenManualPorcentaje = new JLabel("  ");
        lblVolumenManualPorcentaje.setFont(Estilos.Fuentes.LABEL);

        filaVolumen.add(txtVolumenManual, BorderLayout.CENTER);
        filaVolumen.add(lblVolumenManualPorcentaje, BorderLayout.EAST);

        gbc.gridy = 3;
        gbc.insets = new Insets(2, 0, 2, 0);
        panel.add(filaVolumen, gbc);

        txtVolumenManual.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { onTextoCambiado(); }
            @Override public void removeUpdate(DocumentEvent e)  { onTextoCambiado(); }
            @Override public void changedUpdate(DocumentEvent e) { onTextoCambiado(); }
        });

        return panel;
    }

    // ── Lógica interna ───────────────────────────────────────────────────────

    private void onTextoCambiado() {
        int capacidadTotal = extraerCapacidadTotalDelLabel();
        int volumenManual  = getVolumenManual();
        if (volumenManual >= 0 && capacidadTotal > 0) {
            actualizarBarraCapacidad(new OcupacionAutoclave(volumenManual, capacidadTotal));
        }
        if (onVolumenManualChanged != null) {
            SwingUtilities.invokeLater(onVolumenManualChanged);
        }
    }

    private int extraerCapacidadTotalDelLabel() {
        try {
            String[] partes = lblCapacidad.getText().replace("Capacidad: ", "").split("/");
            if (partes.length == 2) return Integer.parseInt(partes[1].trim());
        } catch (NumberFormatException ignored) { }
        return 0;
    }

    /**
     * 0 % → rojo | 1–100 % → interpolación rojo→verde | sobrecarga → rojo
     */
    private void actualizarBarraCapacidad(OcupacionAutoclave ocupacion) {
        if (ocupacion.getTotal() == 0) {
            barraCapacidad.setValue(0);
            barraCapacidad.setForeground(COLOR_ROJO);
            barraCapacidad.setString("0%");
            lblVolumenManualPorcentaje.setText("  ");
            return;
        }

        int porcentaje = ocupacion.porcentaje();
        barraCapacidad.setValue(Math.min(porcentaje, 100));
        barraCapacidad.setString(porcentaje + "%");

        Color color;
        if (ocupacion.estaSobrecargado()) {
            color = COLOR_ROJO;
            lblVolumenManualPorcentaje.setForeground(COLOR_ROJO);
            lblVolumenManualPorcentaje.setText("⚠ " + porcentaje + "%");
        } else {
            color = interpolarColor(COLOR_ROJO, COLOR_VERDE, porcentaje / 100.0f);
            lblVolumenManualPorcentaje.setForeground(color);
            lblVolumenManualPorcentaje.setText(porcentaje + "%");
        }
        barraCapacidad.setForeground(color);
    }

    private static Color interpolarColor(Color desde, Color hasta, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = Math.round(desde.getRed()   + t * (hasta.getRed()   - desde.getRed()));
        int g = Math.round(desde.getGreen() + t * (hasta.getGreen() - desde.getGreen()));
        int b = Math.round(desde.getBlue()  + t * (hasta.getBlue()  - desde.getBlue()));
        return new Color(r, g, b);
    }

    private ListSelectionListener crearListenerAutoclave() {
        return e -> {
            if (e.getValueIsAdjusting()) return;
            int selectedRow = tablaAutoclaves.getSelectedRow();
            if (selectedRow >= 0) {
                AutoclaveItem item = modeloAutoclaves.getAutoclaveAt(selectedRow);
                if (onAutoclaveSeleccionado != null) onAutoclaveSeleccionado.accept(item);
            }
        };
    }

    // ── API pública ──────────────────────────────────────────────────────────

    public void setOnVolumenManualChanged(Runnable listener) { this.onVolumenManualChanged = listener; }

    public int getVolumenManual() {
        try {
            String texto = txtVolumenManual.getText().trim();
            if (texto.isEmpty()) return -1;
            return Integer.parseInt(texto);
        } catch (NumberFormatException e) { return -1; }
    }

    public void setVolumenCalculado(int volumen) {
        txtVolumenManual.getDocument().putProperty("silent", Boolean.TRUE);
        txtVolumenManual.setText(String.valueOf(volumen));
        txtVolumenManual.getDocument().putProperty("silent", null);
    }

    public void setVolumenManualEnabled(boolean enabled) {
        txtVolumenManual.setEnabled(enabled);
        if (!enabled) lblVolumenManualPorcentaje.setText("  ");
    }

    public void setAutoclaves(List<AutoclaveItem> autoclaves) {
        modeloAutoclaves.setAutoclaves(autoclaves != null ? autoclaves : new java.util.ArrayList<>());
        if (autoclaves != null && !autoclaves.isEmpty()) tablaAutoclaves.setRowSelectionInterval(0, 0);
    }

    public void seleccionarAutoclave(String nombre) {
        if (nombre == null) return;
        for (int i = 0; i < modeloAutoclaves.getRowCount(); i++) {
            AutoclaveItem item = modeloAutoclaves.getAutoclaveAt(i);
            if (item != null && nombre.equalsIgnoreCase(item.getNombre())) {
                tablaAutoclaves.setRowSelectionInterval(i, i);
                return;
            }
        }
    }

    public AutoclaveItem getAutoclaveSeleccionado() {
        int row = tablaAutoclaves.getSelectedRow();
        return row >= 0 ? modeloAutoclaves.getAutoclaveAt(row) : null;
    }

    public void setMaterialesDisponibles(List<MaterialLoteItem> materiales) { modeloDisponibles.setItems(materiales); }
    public void setMaterialesAutoclave(List<MaterialLoteItem> materiales)   { modeloAutoclave.setItems(materiales); }

    public List<MaterialLoteItem> getMaterialesDisponiblesSeleccionados() {
        return TableSelectionSupport.selectedItems(tablaDisponibles, modeloDisponibles::getItemAt);
    }

    public List<MaterialLoteItem> getMaterialesAutoclaveSeleccionados() {
        return TableSelectionSupport.selectedItems(tablaAutoclave, modeloAutoclave::getItemAt);
    }

    public void setOnAutoclaveSeleccionado(Consumer<AutoclaveItem> listener) { this.onAutoclaveSeleccionado = listener; }

    public void setOnLanzar(java.awt.event.ActionListener listener)    { btnLanzar.addActionListener(listener); }
    public void setOnFinalizar(java.awt.event.ActionListener listener) { btnFinalizar.addActionListener(listener); }
    public void setOnMarcarFallo(java.awt.event.ActionListener listener) { btnMarcarFallo.addActionListener(listener); }
    public void setOnQuitar(java.awt.event.ActionListener listener)    { btnQuitar.addActionListener(listener); }

    public void setLanzarEnabled(boolean enabled)    { btnLanzar.setEnabled(enabled); }
    public void setFinalizarEnabled(boolean enabled) { btnFinalizar.setEnabled(enabled); }
    public void setMarcarFalloEnabled(boolean enabled) { btnMarcarFallo.setEnabled(enabled); }
    public void setQuitarEnabled(boolean enabled)    { btnQuitar.setEnabled(enabled); }

    public JTable getTablaDisponibles() { return tablaDisponibles; }
    public JTable getTablaAutoclave()   { return tablaAutoclave; }

    /** Tooltip por fila de la tabla de materiales disponibles; null = sin tooltip. */
    public void setTooltipDisponibles(Function<MaterialLoteItem, String> textoPorItem) {
        tablaDisponibles.setRowTooltipProvider(row -> {
            MaterialLoteItem item = modeloDisponibles.getItemAt(row);
            return item == null ? null : textoPorItem.apply(item);
        });
    }

    /** Tooltip por fila de la tabla de materiales cargados; null = sin tooltip. */
    public void setTooltipAutoclave(Function<MaterialLoteItem, String> textoPorItem) {
        tablaAutoclave.setRowTooltipProvider(row -> {
            MaterialLoteItem item = modeloAutoclave.getItemAt(row);
            return item == null ? null : textoPorItem.apply(item);
        });
    }

    public void setCapacidadTexto(String texto) {
        lblCapacidad.setText(texto);
        try {
            String[] partes = texto.replace("Capacidad: ", "").split("/");
            if (partes.length == 2)
                actualizarBarraCapacidad(new OcupacionAutoclave(
                        Integer.parseInt(partes[0].trim()), Integer.parseInt(partes[1].trim())));
        } catch (NumberFormatException ignored) { }
    }

    public void setCapacidad(int capacidadUsada, int capacidadTotal) {
        lblCapacidad.setText(String.format("Capacidad: %d/%d", capacidadUsada, capacidadTotal));
        actualizarBarraCapacidad(new OcupacionAutoclave(capacidadUsada, capacidadTotal));
    }

    public void mostrarAdvertencia(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, Constantes.Mensajes.TITULO_ADVERTENCIA, JOptionPane.WARNING_MESSAGE);
    }

    public void mostrarInfo(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, Constantes.Mensajes.TITULO_EXITO, JOptionPane.INFORMATION_MESSAGE);
    }

    public void mostrarError(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, Constantes.Mensajes.TITULO_ERROR, JOptionPane.ERROR_MESSAGE);
    }

    public boolean confirmar(String mensaje, String titulo) {
        return JOptionPane.showConfirmDialog(this, mensaje, titulo,
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    private static class AutoclaveRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof AutoclaveItem) {
                AutoclaveItem item = (AutoclaveItem) value;
                if (!isSelected)
                    label.setBackground(item.isOcupado() ? new Color(255, 200, 200) : new Color(210, 245, 210));
                label.setText(item.getNombre() + " (" + item.getCapacidadUsada() + "/" + item.getCapacidad() + ")");
            }
            return label;
        }
    }
}