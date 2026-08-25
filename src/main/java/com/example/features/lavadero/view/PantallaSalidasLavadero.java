package com.example.features.lavadero.view;

import com.example.common.constants.Constantes;
import com.example.features.lavadero.model.AccionSalida;
import com.example.features.lavadero.model.ElementoLavadoPendiente;
import com.example.features.lavadero.model.SalidaLista;
import com.example.features.lavadero.view.helpers.ElementoLavadoTableModel;
import com.example.features.lavadero.view.helpers.SalidaListaTableModel;
import com.example.ui.common.Estilos;
import com.example.ui.common.LabelFactory;
import com.example.ui.common.PanelHeader;
import com.example.ui.common.TableStyler;
import com.example.ui.common.dnd.TableSelectionSupport;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * Vista pura: widgets, getters, {@code refrescar(...)}, {@code mostrarError}/{@code mostrarInfo}.
 * Cero lógica, cero listeners — los cablea {@code SalidasLavaderoController}. Molde:
 * {@link PantallaClasificacionLavadero}.
 */
public class PantallaSalidasLavadero extends JPanel {

    private final ElementoLavadoTableModel modeloLavados = new ElementoLavadoTableModel();
    private final SalidaListaTableModel    modeloListos  = new SalidaListaTableModel();

    private final JTable tablaLavados;
    private final JTable tablaListos;

    private final JButton  btnMarcarListo    = new JButton(Constantes.Botones.MARCAR_LISTO);
    private final JButton  btnVolverALavado  = new JButton(Constantes.Botones.VOLVER_A_LAVADO);
    private final JButton  btnSaleDelFlujo   = new JButton(Constantes.Botones.SALE_DEL_FLUJO);
    private final JButton  btnIngresarACde   = new JButton(Constantes.Botones.INGRESAR_A_CDE);

    public PantallaSalidasLavadero(CardLayout navegador, JPanel contenedor) {
        setLayout(new BorderLayout());

        PanelHeader header = new PanelHeader(
            Constantes.Titulos.SALIDAS_LAVADERO,
            navegador,
            contenedor,
            Constantes.Pantallas.LAVADERO
        );
        add(header, BorderLayout.NORTH);

        tablaLavados = buildTable(modeloLavados, 1, 3);
        tablaListos  = buildTable(modeloListos, 1, 3);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            panelLavados(), panelListos());
        split.setResizeWeight(0.5);
        add(split, BorderLayout.CENTER);
    }

    private JPanel panelLavados() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(LabelFactory.createSectionLabel(Constantes.Textos.TABLA_LAVADOS_TITULO),
            BorderLayout.NORTH);
        panel.add(scroll(tablaLavados), BorderLayout.CENTER);

        btnMarcarListo.setFont(Estilos.Fuentes.BOTON);
        JLabel ayuda = new JLabel(Constantes.Textos.AYUDA_ARRASTRE_SALIDAS);
        ayuda.setFont(Estilos.Fuentes.LABEL);

        JPanel south = new JPanel(new BorderLayout());
        south.setBorder(Estilos.Espaciados.BORDE_PRINCIPAL);
        south.add(ayuda, BorderLayout.NORTH);
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        botones.add(btnMarcarListo);
        south.add(botones, BorderLayout.CENTER);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel panelListos() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(LabelFactory.createSectionLabel(Constantes.Textos.TABLA_LISTOS_TITULO),
            BorderLayout.NORTH);
        panel.add(scroll(tablaListos), BorderLayout.CENTER);

        for (JButton btn : new JButton[]{btnVolverALavado, btnSaleDelFlujo, btnIngresarACde}) {
            btn.setFont(Estilos.Fuentes.BOTON);
        }
        JLabel ayuda = new JLabel(Constantes.Textos.AYUDA_SALIDA_ENTERA);
        ayuda.setFont(Estilos.Fuentes.LABEL);

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        botones.add(btnVolverALavado);
        botones.add(btnSaleDelFlujo);
        botones.add(btnIngresarACde);

        JPanel south = new JPanel(new BorderLayout());
        south.setBorder(Estilos.Espaciados.BORDE_PRINCIPAL);
        south.add(ayuda, BorderLayout.NORTH);
        south.add(botones, BorderLayout.CENTER);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    /**
     * Las dos tablas arrastran y reciben. Qué significa soltar algo en cada una lo decide el
     * controller instalando su {@code TransferHandler}: acá sólo se declara que se puede.
     */
    private JTable buildTable(javax.swing.table.AbstractTableModel model, int... centeredCols) {
        JTable t = new JTable(model);
        TableSelectionSupport.enableMultiSelection(t);
        TableStyler.applyStandard(t);
        TableStyler.centerColumns(t, centeredCols);
        t.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        t.setDragEnabled(true);
        t.setDropMode(DropMode.ON);
        t.setFillsViewportHeight(true);
        return t;
    }

    private static JScrollPane scroll(JComponent c) {
        return new JScrollPane(c,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    }

    // ── Datos ────────────────────────────────────────────────────────────────

    public void refrescar(List<ElementoLavadoPendiente> lavados, List<SalidaLista> listos) {
        modeloLavados.setItems(lavados != null ? lavados : Collections.emptyList());
        modeloListos.setItems(listos != null ? listos : Collections.emptyList());
    }

    public JTable getTablaLavados() { return tablaLavados; }
    public JTable getTablaListos()  { return tablaListos; }

    public List<ElementoLavadoPendiente> getSeleccionLavados() {
        return TableSelectionSupport.selectedItems(tablaLavados, modeloLavados::getItemAt);
    }

    public List<SalidaLista> getSeleccionListos() {
        return TableSelectionSupport.selectedItems(tablaListos, modeloListos::getItemAt);
    }

    // ── Botones ──────────────────────────────────────────────────────────────

    public JButton getBtnMarcarListo()   { return btnMarcarListo; }
    public JButton getBtnVolverALavado() { return btnVolverALavado; }
    public JButton getBtnSaleDelFlujo()  { return btnSaleDelFlujo; }
    public JButton getBtnIngresarACde()  { return btnIngresarACde; }

    // ── Diálogos ─────────────────────────────────────────────────────────────

    /**
     * Cuántas unidades de una tanda lavada se marcan Listo.
     *
     * @return la cantidad elegida, o {@code 0} si se canceló.
     */
    public int pedirCantidadListo(ElementoLavadoPendiente item) {
        DistribucionUnidadesDialog dialogo = DistribucionUnidadesDialog.paraSalidas(
            (Frame) SwingUtilities.getWindowAncestor(this),
            item.elementoNombre(), item.clienteNombre(), item.lavarropasNumero(),
            item.cantidadPendiente());
        dialogo.setVisible(true);
        return dialogo.getCantidad();
    }

    /**
     * Pregunta y confirma en un solo paso a nombre de quién ingresan las salidas al CDE.
     *
     * @return la {@link AccionSalida} de CDE elegida, o {@code null} si se canceló.
     */
    public AccionSalida elegirAccionCde(int cantidadFilas) {
        Object[] opciones = {
            AccionSalida.CDE_CLIENTE.getNombre(),
            AccionSalida.CDE_APTIUM.getNombre(),
            Constantes.Botones.CANCELAR
        };
        int seleccion = JOptionPane.showOptionDialog(this,
            String.format(Constantes.Mensajes.ELEGIR_CLIENTE_CDE, cantidadFilas),
            Constantes.Botones.INGRESAR_A_CDE,
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            opciones,
            opciones[0]);
        if (seleccion == 0) return AccionSalida.CDE_CLIENTE;
        if (seleccion == 1) return AccionSalida.CDE_APTIUM;
        return null;
    }

    public boolean confirmar(String mensaje) {
        return JOptionPane.showConfirmDialog(this, mensaje,
            Constantes.Mensajes.TITULO_CONFIRMAR_CAMBIOS,
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    public void mostrarError(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje,
            Constantes.Mensajes.TITULO_ERROR, JOptionPane.ERROR_MESSAGE);
    }

    public void mostrarInfo(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje,
            Constantes.Mensajes.TITULO_EXITO, JOptionPane.INFORMATION_MESSAGE);
    }
}
