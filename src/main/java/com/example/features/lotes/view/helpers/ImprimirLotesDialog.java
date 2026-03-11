package com.example.features.lotes.view.helpers;

import com.example.ui.common.Estilos;
import com.toedter.calendar.JDateChooser;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.function.BiConsumer;

/**
 * Diálogo modal que pide un rango "Desde / Hasta" antes de generar el reporte.
 *
 * Uso desde el controller:
 * <pre>
 *   Frame parent = (Frame) SwingUtilities.getWindowAncestor(panel);
 *   new ImprimirLotesDialog(parent, (desde, hasta) -> generarReporte(desde, hasta))
 *       .setVisible(true);
 * </pre>
 *
 * El callback {@code onConfirmar} recibe los dos {@link LocalDate} ya validados
 * (no nulos, desde ≤ hasta) solo cuando el usuario presiona "Generar Reporte".
 */
public class ImprimirLotesDialog extends JDialog {

    private final JDateChooser dateDesde;
    private final JDateChooser dateHasta;

    public ImprimirLotesDialog(Frame parent, BiConsumer<LocalDate, LocalDate> onConfirmar) {
        super(parent, "Imprimir Reporte de Lotes", true); // modal

        // ── Pickers de fecha ──────────────────────────────────────────────────
        dateDesde = new JDateChooser();
        dateDesde.setDateFormatString("dd/MM/yyyy");
        dateDesde.setPreferredSize(new Dimension(140, 26));
        dateDesde.setFont(Estilos.Fuentes.INPUT);

        dateHasta = new JDateChooser();
        dateHasta.setDateFormatString("dd/MM/yyyy");
        dateHasta.setPreferredSize(new Dimension(140, 26));
        dateHasta.setFont(Estilos.Fuentes.INPUT);

        // ── Panel de fechas ───────────────────────────────────────────────────
        JPanel panelFechas = new JPanel(new GridBagLayout());
        panelFechas.setBorder(BorderFactory.createTitledBorder(
            "Filtrar por fecha de inicio del lote"));
        panelFechas.setBackground(Color.WHITE);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets  = new Insets(10, 12, 10, 12);
        gbc.anchor  = GridBagConstraints.WEST;
        gbc.fill    = GridBagConstraints.NONE;

        JLabel lblDesde = new JLabel("Desde:");
        lblDesde.setFont(Estilos.Fuentes.LABEL);
        gbc.gridx = 0; gbc.gridy = 0;
        panelFechas.add(lblDesde, gbc);

        gbc.gridx = 1;
        panelFechas.add(dateDesde, gbc);

        JLabel lblHasta = new JLabel("Hasta:");
        lblHasta.setFont(Estilos.Fuentes.LABEL);
        gbc.gridx = 0; gbc.gridy = 1;
        panelFechas.add(lblHasta, gbc);

        gbc.gridx = 1;
        panelFechas.add(dateHasta, gbc);

        // ── Botones ───────────────────────────────────────────────────────────
        JButton btnGenerar  = new JButton("Generar Reporte");
        JButton btnCancelar = new JButton("Cancelar");
        btnGenerar.setFont(Estilos.Fuentes.INPUT);
        btnCancelar.setFont(Estilos.Fuentes.INPUT);

        btnCancelar.addActionListener(e -> dispose());

        btnGenerar.addActionListener(e -> {
            LocalDate desde = resolverFecha(dateDesde, "Desde");
            if (desde == null) return;

            LocalDate hasta = resolverFecha(dateHasta, "Hasta");
            if (hasta == null) return;

            if (desde.isAfter(hasta)) {
                JOptionPane.showMessageDialog(
                    this,
                    "La fecha 'Desde' no puede ser posterior a la fecha 'Hasta'.",
                    "Fechas inválidas",
                    JOptionPane.WARNING_MESSAGE);
                return;
            }

            dispose(); // cerrar antes de abrir el visor de reportes
            onConfirmar.accept(desde, hasta);
        });

        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        panelBotones.add(btnCancelar);
        panelBotones.add(btnGenerar);
        getRootPane().setDefaultButton(btnGenerar);

        // ── Armado final ──────────────────────────────────────────────────────
        setLayout(new BorderLayout());
        getRootPane().setBorder(BorderFactory.createEmptyBorder(6, 6, 0, 6));
        add(panelFechas,  BorderLayout.CENTER);
        add(panelBotones, BorderLayout.SOUTH);

        pack();
        setMinimumSize(getSize());
        setResizable(false);
        setLocationRelativeTo(parent);
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Obtiene un {@link LocalDate} del picker dado.
     * Muestra un aviso y retorna null si el usuario no seleccionó fecha.
     */
    private LocalDate resolverFecha(JDateChooser chooser, String nombre) {
        if (chooser.getDate() == null) {
            JOptionPane.showMessageDialog(
                this,
                "Por favor seleccioná la fecha '" + nombre + "'.",
                "Fecha requerida",
                JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return chooser.getDate().toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDate();
    }
}