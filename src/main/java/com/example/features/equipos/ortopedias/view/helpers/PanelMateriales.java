package com.example.features.equipos.ortopedias.view.helpers;

import com.example.ui.common.Estilos;
import com.example.ui.common.Hotkeys;

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.example.common.constants.Constantes;
import com.example.common.util.Validador;
import com.example.ui.common.DuplicadoHighlighter;

import java.util.function.BiConsumer;

/**
 * Panel especializado para gestionar materiales de forma dinámica.
 * Responsabilidad única: UI de tabla de materiales (agregar, eliminar filas, calcular totales).
 *
 * Regla de negocio aplicada en la vista:
 *   No se permite tener dos filas con el mismo código de catálogo.
 *   Cuando una fila entra en conflicto su campo número se pinta en rojo y se
 *   muestra un tooltip explicativo. El método {@link #tieneDuplicados()} permite
 *   al Controller bloquear el guardado.
 *
 * Separado de PantallaIngresoOrtopedia para evitar que esa clase sea demasiado grande.
 */
public class PanelMateriales extends JPanel {

    private List<MaterialRow> materialRows = new ArrayList<>();
    private JLabel            lblTotalElementos;
    private Font              inputFont;
    private int               inputHeight;
    private JPanel            listaMaterialesPanel;

    // Callback para cuando se cambia el número de material
    // BiConsumer<Integer, JTextField>: recibe el código y el textfield donde actualizar la descripción
    private BiConsumer<Integer, JTextField> onNumeroChangedListener;

    // Color de fondo normal de los JTextField (se captura la primera vez que se crea uno)
    private Color colorNormal = null;

    public PanelMateriales(Font inputFont, int inputHeight) {
        this.inputFont   = inputFont;
        this.inputHeight = inputHeight;

        setLayout(new BorderLayout(5, 5));

        listaMaterialesPanel = new JPanel(new GridBagLayout());

        JScrollPane scrollPane = new JScrollPane(listaMaterialesPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);

        lblTotalElementos = new JLabel(String.format(Constantes.Textos.TOTAL_ELEMENTOS, 0));
        lblTotalElementos.setFont(Estilos.Fuentes.LABEL);
        JPanel panelTotal = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 5));
        panelTotal.add(lblTotalElementos);

        JButton btnAgregarMaterial = new JButton(Constantes.Botones.AGREGAR);
        btnAgregarMaterial.setFont(Estilos.Fuentes.BOTON_PEQUENO);
        JButton btnEliminarMaterial = new JButton(Constantes.Botones.ELIMINAR);
        btnEliminarMaterial.setFont(Estilos.Fuentes.BOTON_PEQUENO);
        JPanel panelAgregar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        panelAgregar.add(btnEliminarMaterial);
        panelAgregar.add(btnAgregarMaterial);

        JPanel panelInferior = new JPanel(new BorderLayout());
        panelInferior.add(panelTotal,   BorderLayout.WEST);
        panelInferior.add(panelAgregar, BorderLayout.EAST);
        add(panelInferior, BorderLayout.SOUTH);

        agregarFilaMaterial();

        btnAgregarMaterial.addActionListener(e -> {
            agregarFilaMaterial();
            listaMaterialesPanel.revalidate();
            listaMaterialesPanel.repaint();
        });

        btnEliminarMaterial.addActionListener(e -> eliminarUltimaFilaMaterial());

        Hotkeys.registrarMateriales(this,
            () -> { agregarFilaMaterial(); listaMaterialesPanel.revalidate(); listaMaterialesPanel.repaint(); },
            () -> eliminarUltimaFilaMaterial()
        );
    }

    /**
     * Permite al Controller registrar un listener para cuando cambie el número de material.
     */
    public void setOnNumeroChangedListener(BiConsumer<Integer, JTextField> listener) {
        this.onNumeroChangedListener = listener;
    }

    // ── Validación de duplicados ─────────────────────────────────────────────

    /**
     * Verifica si existe algún código de catálogo repetido entre las filas actuales.
     *
     * También actualiza el color de fondo de cada campo número:
     *   - Fondo rojo claro + tooltip si el código aparece más de una vez.
     *   - Fondo normal en caso contrario.
     *
     * Llamar desde el Controller antes de intentar guardar el equipo.
     *
     * @return true si hay al menos un código duplicado (debe bloquearse el guardado)
     */
    public boolean tieneDuplicados() {
        List<JTextField> campos = new ArrayList<>();
        for (MaterialRow row : materialRows) campos.add(row.numero);

        return DuplicadoHighlighter.marcar(
            campos,
            cod -> Validador.soloNumeros(cod.trim()) ? cod.trim() : "",
            colorNormal,
            "Código duplicado: unifique estas filas antes de guardar");
    }

    // ── Construcción de filas ────────────────────────────────────────────────

    void agregarFilaMaterial() {
        GridBagConstraints gbcRow = new GridBagConstraints();
        gbcRow.insets = new Insets(5, 0, 5, 10);
        gbcRow.fill   = GridBagConstraints.HORIZONTAL;

        int rowIndex = materialRows.size();

        JTextField txtNumero = new JTextField();
        Validador.aplicarSoloNumeros(txtNumero);
        txtNumero.setFont(this.inputFont);
        txtNumero.setColumns(5);
        txtNumero.setMargin(Estilos.Espaciados.INSETS_INPUT);
        int numeroWidth = Estilos.Dimensiones.calcularAnchoNumero(5);
        txtNumero.setPreferredSize(new Dimension(numeroWidth, this.inputHeight));
        txtNumero.setMinimumSize(new Dimension(numeroWidth, this.inputHeight));

        // Capturar el color normal la primera vez
        if (colorNormal == null) {
            colorNormal = txtNumero.getBackground();
        }

        JTextField txtDesc = new JTextField();
        txtDesc.setFont(this.inputFont);
        txtDesc.setColumns(40);
        txtDesc.setMargin(Estilos.Espaciados.INSETS_INPUT);
        txtDesc.setPreferredSize(new Dimension(200, this.inputHeight));
        txtDesc.setMinimumSize(new Dimension(100, this.inputHeight));
        txtDesc.setEditable(false);
        txtDesc.setBackground(Color.LIGHT_GRAY);

        txtNumero.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { onCambioNumero(txtNumero, txtDesc); }
            @Override public void removeUpdate(DocumentEvent e)  { onCambioNumero(txtNumero, txtDesc); }
            @Override public void changedUpdate(DocumentEvent e) { onCambioNumero(txtNumero, txtDesc); }
        });

        SpinnerNumberModel model = new SpinnerNumberModel(1.0, 1.0, null, 1.0);
        JSpinner spElementos = new JSpinner(model);
        spElementos.setFont(this.inputFont);
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(
            spElementos, Constantes.Formatos.FORMATO_SPINNER_CANTIDAD);
        spElementos.setEditor(editor);
        editor.getTextField().setColumns(4);
        spElementos.addChangeListener(e -> actualizarTotalElementos());

        JButton btnEliminarFila = new JButton(Constantes.Botones.ELIMINAR_FILA);
        btnEliminarFila.setFont(Estilos.Fuentes.BOTON_PEQUENO);
        btnEliminarFila.setPreferredSize(new Dimension(45, this.inputHeight));
        btnEliminarFila.setMargin(new Insets(0, 0, 0, 0));
        btnEliminarFila.setToolTipText(Constantes.Textos.TOOLTIP_ELIMINAR_FILA);

        MaterialRow materialRow = new MaterialRow(txtNumero, txtDesc, spElementos, btnEliminarFila);

        btnEliminarFila.addActionListener(e -> {
            eliminarFilaMaterial(materialRow);
            // Re-evaluar duplicados por si la fila eliminada resolvía un conflicto
            tieneDuplicados();
        });

        gbcRow.gridx = 0; gbcRow.gridy = rowIndex; gbcRow.weightx = 0;
        gbcRow.fill = GridBagConstraints.NONE; gbcRow.anchor = GridBagConstraints.WEST;
        listaMaterialesPanel.add(txtNumero, gbcRow);

        gbcRow.gridx = 1; gbcRow.gridy = rowIndex; gbcRow.weightx = 1;
        gbcRow.fill = GridBagConstraints.HORIZONTAL; gbcRow.anchor = GridBagConstraints.WEST;
        listaMaterialesPanel.add(txtDesc, gbcRow);

        gbcRow.gridx = 2; gbcRow.gridy = rowIndex; gbcRow.weightx = 0;
        gbcRow.fill = GridBagConstraints.NONE; gbcRow.anchor = GridBagConstraints.EAST;
        listaMaterialesPanel.add(spElementos, gbcRow);

        gbcRow.gridx = 3; gbcRow.gridy = rowIndex; gbcRow.weightx = 0;
        gbcRow.fill = GridBagConstraints.NONE; gbcRow.anchor = GridBagConstraints.EAST;
        gbcRow.insets = new Insets(5, 5, 5, 0);
        listaMaterialesPanel.add(btnEliminarFila, gbcRow);

        materialRows.add(materialRow);
        actualizarTotalElementos();
    }

    /**
     * Llamado cada vez que cambia el texto de un campo número.
     * Notifica al Controller para autocompletar la descripción y
     * re-evalúa el estado de duplicados en todas las filas.
     */
    private void onCambioNumero(JTextField txtNumero, JTextField txtDesc) {
        notificarCambioNumero(txtNumero, txtDesc);
        tieneDuplicados();   // actualiza colores en tiempo real
    }

    private void actualizarTotalElementos() {
        double total = 0.0;
        for (MaterialRow row : materialRows) {
            Object v = row.Elementos.getValue();
            if (v instanceof Number) total += ((Number) v).doubleValue();
        }
        lblTotalElementos.setText(String.format(Constantes.Textos.TOTAL_ELEMENTOS, (int) total));
    }

    private void eliminarUltimaFilaMaterial() {
        if (materialRows.isEmpty()) return;
        eliminarFilaMaterial(materialRows.get(materialRows.size() - 1));
    }

    private void eliminarFilaMaterial(MaterialRow row) {
        if (materialRows.remove(row)) {
            listaMaterialesPanel.remove(row.numero);
            listaMaterialesPanel.remove(row.descripcion);
            listaMaterialesPanel.remove(row.Elementos);
            listaMaterialesPanel.remove(row.btnEliminar);
            listaMaterialesPanel.revalidate();
            listaMaterialesPanel.repaint();
            actualizarTotalElementos();
        }
    }

    private void notificarCambioNumero(JTextField txtNumero, JTextField txtDesc) {
        String numeroStr = txtNumero.getText().trim();
        if (numeroStr.isEmpty()) {
            txtDesc.setText("");
            return;
        }
        if (!Validador.soloNumeros(numeroStr)) {
            txtDesc.setText(Constantes.Textos.CODIGO_INVALIDO);
            return;
        }
        if (onNumeroChangedListener != null) {
            try {
                int codigo = Integer.parseInt(numeroStr);
                onNumeroChangedListener.accept(codigo, txtDesc);
            } catch (NumberFormatException e) {
                txtDesc.setText(Constantes.Textos.CODIGO_INVALIDO);
            }
        }
    }

    // ── API pública ──────────────────────────────────────────────────────────

    public void limpiar() {
        while (!materialRows.isEmpty()) {
            MaterialRow last = materialRows.remove(materialRows.size() - 1);
            listaMaterialesPanel.remove(last.numero);
            listaMaterialesPanel.remove(last.descripcion);
            listaMaterialesPanel.remove(last.Elementos);
            listaMaterialesPanel.remove(last.btnEliminar);
        }
        agregarFilaMaterial();
        listaMaterialesPanel.revalidate();
        listaMaterialesPanel.repaint();
        actualizarTotalElementos();
    }

    public List<MaterialRow> getMaterialRows() {
        return materialRows;
    }

    // ── Clase de fila ────────────────────────────────────────────────────────

    public static class MaterialRow {
        public final JTextField numero;
        public final JTextField descripcion;
        public final JSpinner   Elementos;
        public final JButton    btnEliminar;

        public MaterialRow(JTextField numero, JTextField descripcion,
                           JSpinner Elementos, JButton btnEliminar) {
            this.numero      = numero;
            this.descripcion = descripcion;
            this.Elementos   = Elementos;
            this.btnEliminar = btnEliminar;
        }
    }
}