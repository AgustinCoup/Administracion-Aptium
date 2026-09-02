package com.example.features.lavadero.view.helpers;

import com.example.common.util.DateTimeDisplayUtils;
import com.example.features.lavadero.model.IngresoHistorial;
import com.example.features.lavadero.model.LineaHistorial;
import com.example.ui.common.Estilos;
import com.example.ui.common.TableStyler;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Diálogo modal con la trazabilidad completa de un ingreso de lavadero: panel de datos arriba,
 * tabla de líneas (elemento → lavarropas → fecha de lavado → fecha listo → destino) al centro.
 *
 * <p><b>Cero I/O</b>: recibe el ingreso y sus líneas ya leídas (el controller las trae por
 * {@code TareaUI}). Copia la estructura de {@code DetalleOtrosDialog}.</p>
 */
public class DetalleHistorialDialog extends JDialog {

    private static final String[] COLUMNAS =
        {"Elemento", "Cantidad", "Lavarropas", "F. lavado", "F. listo", "Destino"};

    private static final String SIN_DATO = "—";

    public DetalleHistorialDialog(Window parent, IngresoHistorial ingreso, List<LineaHistorial> lineas) {
        super(parent, "Historial del ingreso #" + ingreso.id(), ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout(10, 10));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        add(crearPanelDatos(ingreso), BorderLayout.NORTH);
        add(lineas == null || lineas.isEmpty() ? crearMensajeVacio() : crearPanelLineas(lineas),
            BorderLayout.CENTER);
        add(crearPanelBoton(), BorderLayout.SOUTH);

        setSize(720, 480);
        setLocationRelativeTo(parent);
        setResizable(true);
    }

    private JPanel crearPanelDatos(IngresoHistorial ingreso) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Datos del ingreso"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill   = GridBagConstraints.HORIZONTAL;

        int row = 0;
        agregarFila(panel, gbc, row++, "Cliente:", val(ingreso.clienteNombre()));
        agregarFila(panel, gbc, row++, "Fecha de ingreso:",
            DateTimeDisplayUtils.formatForUi(ingreso.fechaIngreso()));
        agregarFila(panel, gbc, row++, "Peso:",
            ingreso.pesoTotalKg() != null ? ingreso.pesoTotalKg() + " kg" : SIN_DATO);
        agregarFila(panel, gbc, row++, "Bolsas:", String.valueOf(ingreso.cantBolsas()));
        agregarFila(panel, gbc, row, "Estado:", ingreso.estado().name());
        return panel;
    }

    private JPanel crearPanelLineas(List<LineaHistorial> lineas) {
        DefaultTableModel model = new DefaultTableModel(COLUMNAS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (LineaHistorial l : lineas) {
            model.addRow(new Object[]{
                val(l.elementoNombre()),
                l.cantidad(),
                textoLavarropas(l),
                DateTimeDisplayUtils.formatForUi(l.fechaLavado()),
                DateTimeDisplayUtils.formatForUi(l.fechaListo()),
                l.destino() != null ? l.destino().getNombre() : "Sin destino"
            });
        }
        JTable tabla = new JTable(model);
        TableStyler.applyStandard(tabla);
        TableStyler.centerColumns(tabla, 1, 2, 3, 4, 5);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Trazabilidad (" + lineas.size() + ")"));
        panel.add(new JScrollPane(tabla), BorderLayout.CENTER);
        return panel;
    }

    private JPanel crearMensajeVacio() {
        JPanel panel = new JPanel(new GridBagLayout());
        JLabel lbl = new JLabel("Este ingreso todavía no fue clasificado.");
        lbl.setFont(Estilos.Fuentes.LABEL);
        lbl.setForeground(Estilos.Colores.TEXTO_AYUDA);
        panel.add(lbl);
        return panel;
    }

    private String textoLavarropas(LineaHistorial l) {
        if (l.lavarropas() == null) return SIN_DATO;
        return l.esFraccionDeEquipo()
            ? l.lavarropas() + " (" + l.totalPartes() + " partes)"
            : l.lavarropas();
    }

    private JPanel crearPanelBoton() {
        JButton btnCerrar = new JButton("Cerrar");
        btnCerrar.setFont(Estilos.Fuentes.BOTON_PEQUENO);
        btnCerrar.addActionListener(e -> dispose());
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.add(btnCerrar);
        return panel;
    }

    private void agregarFila(JPanel panel, GridBagConstraints gbc, int row, String label, String value) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3;
        JLabel lbl = new JLabel(label);
        lbl.setFont(Estilos.Fuentes.LABEL);
        panel.add(lbl, gbc);

        gbc.gridx = 1; gbc.weightx = 0.7;
        JLabel val = new JLabel(value);
        val.setFont(Estilos.Fuentes.INPUT);
        panel.add(val, gbc);
    }

    private String val(String s) { return s != null && !s.isBlank() ? s : SIN_DATO; }
}
