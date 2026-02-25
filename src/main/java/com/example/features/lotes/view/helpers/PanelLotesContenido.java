package com.example.features.lotes.view.helpers;




import com.example.ui.common.Estilos;
import com.example.ui.common.TableStyler;
import com.example.ui.common.LabelFactory;
import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

import com.example.common.constants.Constantes;

/**
 * Panel reutilizable con la lógica de gestión de lotes.
 * SOLO el contenido (sin header de navegación ni footer).
 * 
 * Responsabilidad: Mostrar tablas de materiales, lista de autoclaves,
 * barra de capacidad y botones de acción.
 * 
 * Uso:
 * - Como pantalla standalone (en PantallaLotes)
 * - Como sub-panel en PantallaRegistrarEstado
 * 
 * SRP: Solo gestiona UI del subsistema de lotes.
 */
public class PanelLotesContenido extends JPanel {
    
    private JTable tablaAutoclaves;
    private AutoclaveTableModel modeloAutoclaves;
    private MaterialLoteTableModel modeloDisponibles;
    private MaterialLoteTableModel modeloAutoclave;
    private JTable tablaDisponibles;
    private JTable tablaAutoclave;
    private JLabel lblCapacidad;
    private JProgressBar barraCapacidad;
    private JButton btnLanzar;
    private JButton btnFinalizar;
    private JButton btnQuitar;
    private Consumer<AutoclaveItem> onAutoclaveSeleccionado;

    /**
     * Constructor sin dependencias de navegación.
     * El panel es completamente autónomo.
     */
    public PanelLotesContenido() {
        setLayout(new BorderLayout());
        setBorder(Estilos.Espaciados.BORDE_PRINCIPAL);

        // Panel central: tablas de materiales
        JPanel centro = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;

        JLabel lblDisponibles = LabelFactory.createSectionLabel(
            Constantes.Textos.TABLA_MATERIALES_DISPONIBLES);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weighty = 0;
        centro.add(lblDisponibles, gbc);

        modeloDisponibles = new MaterialLoteTableModel();
        tablaDisponibles = new JTable(modeloDisponibles);
        TableStyler.applyStandard(tablaDisponibles);
        TableStyler.centerColumns(tablaDisponibles, 1, 2, 3, 4);
        tablaDisponibles.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scrollDisponibles = new JScrollPane(tablaDisponibles);
        gbc.gridy = 1;
        gbc.weighty = 0.5;
        centro.add(scrollDisponibles, gbc);

        JLabel lblAutoclave = LabelFactory.createSectionLabel(
            Constantes.Textos.TABLA_MATERIALES_AUTOCLAVE);
        gbc.gridy = 2;
        gbc.weighty = 0;
        centro.add(lblAutoclave, gbc);

        modeloAutoclave = new MaterialLoteTableModel();
        tablaAutoclave = new JTable(modeloAutoclave);
        TableStyler.applyStandard(tablaAutoclave);
        TableStyler.centerColumns(tablaAutoclave, 1, 2, 3, 4);
        tablaAutoclave.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scrollAutoclave = new JScrollPane(tablaAutoclave);
        gbc.gridy = 3;
        gbc.weighty = 0.5;
        centro.add(scrollAutoclave, gbc);

        add(centro, BorderLayout.CENTER);

        // Panel lateral: autoclaves y capacidad
        JPanel lateral = new JPanel(new BorderLayout());
        lateral.setPreferredSize(new Dimension(240, 0));

        JLabel lblAutoclaves = LabelFactory.createSectionLabel(
            Constantes.Textos.LISTA_AUTOCLAVES_TITULO);
        lateral.add(lblAutoclaves, BorderLayout.NORTH);

        modeloAutoclaves = new AutoclaveTableModel();
        tablaAutoclaves = new JTable(modeloAutoclaves);
        tablaAutoclaves.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        TableStyler.applyStandard(tablaAutoclaves);
        TableStyler.centerColumns(tablaAutoclaves, 1, 2);

        JScrollPane scrollAutoclaves = new JScrollPane(tablaAutoclaves);
        lateral.add(scrollAutoclaves, BorderLayout.CENTER);

        JPanel panelCapacidad = crearPanelCapacidad();
        lateral.add(panelCapacidad, BorderLayout.SOUTH);

        add(lateral, BorderLayout.EAST);

        // Panel de botones
        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        panelBotones.setBorder(Estilos.Espaciados.BORDE_PRINCIPAL);

        btnQuitar = new JButton(Constantes.Botones.QUITAR);
        btnQuitar.setFont(Estilos.Fuentes.BOTON);

        btnFinalizar = new JButton(Constantes.Botones.FINALIZAR_LOTE);
        btnFinalizar.setFont(Estilos.Fuentes.BOTON);

        btnLanzar = new JButton(Constantes.Botones.LANZAR_LOTE);
        btnLanzar.setFont(Estilos.Fuentes.BOTON);

        panelBotones.add(btnQuitar);
        panelBotones.add(btnFinalizar);
        panelBotones.add(btnLanzar);

        add(panelBotones, BorderLayout.SOUTH);

        tablaAutoclaves.getSelectionModel().addListSelectionListener(crearListenerAutoclave());
    }

    private JPanel crearPanelCapacidad() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        lblCapacidad = new JLabel(Constantes.Textos.CAPACIDAD_AUTOCLAVE);
        lblCapacidad.setFont(Estilos.Fuentes.LABEL);
        panel.add(lblCapacidad, BorderLayout.NORTH);

        barraCapacidad = new JProgressBar(0, 100);
        barraCapacidad.setValue(0);
        barraCapacidad.setStringPainted(true);
        barraCapacidad.setForeground(new Color(76, 175, 80));
        panel.add(barraCapacidad, BorderLayout.CENTER);

        return panel;
    }

    private void actualizarBarraCapacidad(int capacidadUsada, int capacidadTotal) {
        if (capacidadTotal == 0) {
            barraCapacidad.setValue(0);
            barraCapacidad.setForeground(new Color(76, 175, 80));
            barraCapacidad.setString("0%");
        } else {
            int porcentaje = (capacidadUsada * 100) / capacidadTotal;
            barraCapacidad.setValue(porcentaje);
            barraCapacidad.setString(porcentaje + "%");

            if (porcentaje < 50) {
                barraCapacidad.setForeground(new Color(76, 175, 80));
            } else if (porcentaje <= 80) {
                barraCapacidad.setForeground(new Color(255, 193, 7));
            } else {
                barraCapacidad.setForeground(new Color(244, 67, 54));
            }
        }
    }

    private ListSelectionListener crearListenerAutoclave() {
        return e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            int selectedRow = tablaAutoclaves.getSelectedRow();
            if (selectedRow >= 0) {
                AutoclaveItem item = modeloAutoclaves.getAutoclaveAt(selectedRow);
                if (onAutoclaveSeleccionado != null) {
                    onAutoclaveSeleccionado.accept(item);
                }
            }
        };
    }

    // ========== GETTERS / SETTERS ==========

    public void setAutoclaves(List<AutoclaveItem> autoclaves) {
        modeloAutoclaves.setAutoclaves(autoclaves != null ? autoclaves : new java.util.ArrayList<>());
        if (autoclaves != null && !autoclaves.isEmpty()) {
            tablaAutoclaves.setRowSelectionInterval(0, 0);
        }
    }

    public void seleccionarAutoclave(String nombre) {
        if (nombre == null) {
            return;
        }
        for (int i = 0; i < modeloAutoclaves.getRowCount(); i++) {
            AutoclaveItem item = modeloAutoclaves.getAutoclaveAt(i);
            if (item != null && nombre.equalsIgnoreCase(item.getNombre())) {
                tablaAutoclaves.setRowSelectionInterval(i, i);
                return;
            }
        }
    }

    public AutoclaveItem getAutoclaveSeleccionado() {
        int selectedRow = tablaAutoclaves.getSelectedRow();
        if (selectedRow >= 0) {
            return modeloAutoclaves.getAutoclaveAt(selectedRow);
        }
        return null;
    }

    public void setMaterialesDisponibles(List<MaterialLoteItem> materiales) {
        modeloDisponibles.setItems(materiales);
    }

    public void setMaterialesAutoclave(List<MaterialLoteItem> materiales) {
        modeloAutoclave.setItems(materiales);
    }

    public MaterialLoteItem getMaterialDisponibleSeleccionado() {
        int row = tablaDisponibles.getSelectedRow();
        if (row < 0) {
            return null;
        }
        return modeloDisponibles.getItemAt(row);
    }

    public MaterialLoteItem getMaterialAutoclaveSeleccionado() {
        int row = tablaAutoclave.getSelectedRow();
        if (row < 0) {
            return null;
        }
        return modeloAutoclave.getItemAt(row);
    }

    public void setOnAutoclaveSeleccionado(Consumer<AutoclaveItem> listener) {
        this.onAutoclaveSeleccionado = listener;
    }

    public void setOnLanzar(java.awt.event.ActionListener listener) {
        btnLanzar.addActionListener(listener);
    }

    public void setOnFinalizar(java.awt.event.ActionListener listener) {
        btnFinalizar.addActionListener(listener);
    }

    public void setOnQuitar(java.awt.event.ActionListener listener) {
        btnQuitar.addActionListener(listener);
    }

    public void setLanzarEnabled(boolean enabled) {
        btnLanzar.setEnabled(enabled);
    }

    public void setFinalizarEnabled(boolean enabled) {
        btnFinalizar.setEnabled(enabled);
    }

    public void setQuitarEnabled(boolean enabled) {
        btnQuitar.setEnabled(enabled);
    }

    public JTable getTablaDisponibles() {
        return tablaDisponibles;
    }

    public JTable getTablaAutoclave() {
        return tablaAutoclave;
    }

    public void setCapacidadTexto(String texto) {
        lblCapacidad.setText(texto);
        try {
            String[] partes = texto.replace("Capacidad: ", "").split("/");
            if (partes.length == 2) {
                int usado = Integer.parseInt(partes[0].trim());
                int total = Integer.parseInt(partes[1].trim());
                actualizarBarraCapacidad(usado, total);
            }
        } catch (NumberFormatException e) {
            // Si falla, mantener estado actual
        }
    }

    public void setCapacidad(int capacidadUsada, int capacidadTotal) {
        lblCapacidad.setText(String.format("Capacidad: %d/%d", capacidadUsada, capacidadTotal));
        actualizarBarraCapacidad(capacidadUsada, capacidadTotal);
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

    private static class AutoclaveRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof AutoclaveItem) {
                AutoclaveItem item = (AutoclaveItem) value;
                if (!isSelected) {
                    label.setBackground(item.isOcupado() ? new Color(255, 200, 200) : new Color(210, 245, 210));
                }
                String texto = item.getNombre() + " (" + item.getCapacidadUsada() + "/" + item.getCapacidad() + ")";
                label.setText(texto);
            }
            return label;
        }
    }
}



