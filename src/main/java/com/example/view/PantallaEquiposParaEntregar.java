package com.example.view;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.function.Consumer;

import com.example.constants.Constantes;
import com.example.view.helpers.Estilos;
import com.example.view.helpers.InstitucionEntregaItem;
import com.example.view.helpers.InstitucionEntregaTableModel;
import com.example.view.helpers.LabelFactory;
import com.example.view.helpers.MaterialEntregaItem;
import com.example.view.helpers.MaterialEntregaTableModel;
import com.example.view.helpers.PanelHeader;
import com.example.view.helpers.TableStyler;

public class PantallaEquiposParaEntregar extends JPanel {
    private InstitucionEntregaTableModel modeloInstituciones;
    private MaterialEntregaTableModel modeloMateriales;
    private JTable tablaInstituciones;
    private JTable tablaMateriales;
    private JButton btnEntregarInstitucion;
    private Consumer<InstitucionEntregaItem> onInstitucionSeleccionada;
    public PantallaEquiposParaEntregar(CardLayout navegador, JPanel contenedor) {
        setLayout(new BorderLayout());

        // Header reutilizable con título y botón de navegación
        PanelHeader header = new PanelHeader(
            Constantes.Titulos.EQUIPOS_PARA_ENTREGAR, 
            navegador, 
            contenedor, 
            Constantes.Pantallas.ESTERILIZACION
        );
        add(header, BorderLayout.NORTH);

        JPanel contenido = new JPanel(new GridBagLayout());
        contenido.setBorder(Estilos.Espaciados.BORDE_PRINCIPAL);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;

        JLabel lblInstituciones = LabelFactory.createSectionLabel(Constantes.Textos.TABLA_INSTITUCIONES_TITULO);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weighty = 0;
        contenido.add(lblInstituciones, gbc);

        modeloInstituciones = new InstitucionEntregaTableModel();
        tablaInstituciones = new JTable(modeloInstituciones);
        TableStyler.applyStandard(tablaInstituciones);
        tablaInstituciones.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scrollInstituciones = new JScrollPane(tablaInstituciones);
        gbc.gridy = 1;
        gbc.weighty = 0.45;
        contenido.add(scrollInstituciones, gbc);

        JLabel lblMateriales = LabelFactory.createSectionLabel(Constantes.Textos.TABLA_MATERIALES_PARA_ENTREGAR_TITULO);
        gbc.gridy = 2;
        gbc.weighty = 0;
        contenido.add(lblMateriales, gbc);

        modeloMateriales = new MaterialEntregaTableModel();
        tablaMateriales = new JTable(modeloMateriales);
        TableStyler.applyStandard(tablaMateriales);
        tablaMateriales.getColumnModel().getColumn(2).setCellRenderer(TableStyler.createEntregadoRenderer());
        TableStyler.centerColumns(tablaMateriales, 1);

        tablaMateriales.setEnabled(false);

        JScrollPane scrollMateriales = new JScrollPane(tablaMateriales);
        gbc.gridy = 3;
        gbc.weighty = 0.55;
        contenido.add(scrollMateriales, gbc);

        tablaInstituciones.getSelectionModel().addListSelectionListener(crearListenerSeleccionInstitucion());

        add(contenido, BorderLayout.CENTER);

        // Panel de botones al sur
        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panelBotones.setBorder(Estilos.Espaciados.BORDE_PRINCIPAL);
        
        btnEntregarInstitucion = new JButton(Constantes.Botones.ENTREGAR_INSTITUCION);
        btnEntregarInstitucion.setFont(Estilos.Fuentes.BOTON);
        
        panelBotones.add(btnEntregarInstitucion);
        add(panelBotones, BorderLayout.SOUTH);
    }

    private ListSelectionListener crearListenerSeleccionInstitucion() {
        return e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            int selectedRow = tablaInstituciones.getSelectedRow();
            if (selectedRow >= 0) {
                InstitucionEntregaItem institucion = modeloInstituciones.getInstitucionAt(selectedRow);
                if (onInstitucionSeleccionada != null) {
                    onInstitucionSeleccionada.accept(institucion);
                }
            } else if (onInstitucionSeleccionada != null) {
                onInstitucionSeleccionada.accept(null);
            }
        };
    }

    public void actualizarInstituciones(List<InstitucionEntregaItem> instituciones) {
        modeloInstituciones.actualizarDatos(instituciones);
        tablaInstituciones.clearSelection();
    }

    public void actualizarMateriales(List<MaterialEntregaItem> materiales) {
        modeloMateriales.actualizarDatos(materiales);
    }

    public void limpiarMateriales() {
        modeloMateriales.limpiar();
    }

    public void setOnInstitucionSeleccionada(Consumer<InstitucionEntregaItem> listener) {
        this.onInstitucionSeleccionada = listener;
    }

    public void setOnEntregarInstitucion(ActionListener listener) {
        btnEntregarInstitucion.addActionListener(listener);
    }

    public InstitucionEntregaItem getInstitucionSeleccionada() {
        int selectedRow = tablaInstituciones.getSelectedRow();
        if (selectedRow >= 0) {
            return modeloInstituciones.getInstitucionAt(selectedRow);
        }
        return null;
    }

    public void mostrarAdvertencia(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje,
            Constantes.Mensajes.TITULO_ADVERTENCIA, JOptionPane.WARNING_MESSAGE);
    }

    public void mostrarInfo(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje,
            Constantes.Mensajes.TITULO_EXITO, JOptionPane.INFORMATION_MESSAGE);
    }

    public void mostrarError(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje,
            Constantes.Mensajes.TITULO_ERROR, JOptionPane.ERROR_MESSAGE);
    }

    public boolean confirmar(String mensaje, String titulo) {
        int respuesta = JOptionPane.showConfirmDialog(this, mensaje, titulo,
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        return respuesta == JOptionPane.YES_OPTION;
    }
}
