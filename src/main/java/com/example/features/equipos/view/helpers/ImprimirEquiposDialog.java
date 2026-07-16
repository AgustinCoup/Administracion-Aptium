package com.example.features.equipos.view.helpers;

import com.example.features.clientes.model.Cliente;
import com.example.features.instituciones.model.Institucion;
import com.example.ui.common.AutocompleteListener;
import com.example.ui.common.Estilos;
import com.toedter.calendar.JDateChooser;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Function;

public class ImprimirEquiposDialog extends JDialog {

    @FunctionalInterface
    public interface Callback {
        void onConfirmar(LocalDate desde, LocalDate hasta, Integer clienteId, Integer institucionId);
    }

    private final JDateChooser dateDesde;
    private final JDateChooser dateHasta;
    private final JTextField   txtCliente;
    private Integer selectedClienteId = null;
    private Integer selectedInstitucionId = null;

    /** Sin filtro de institución (usado por el reporte de "Otros", que no tiene ese dato). */
    public ImprimirEquiposDialog(Frame parent, String titulo,
                                 Function<String, List<Cliente>> buscarClientes,
                                 Callback onConfirmar) {
        this(parent, titulo, buscarClientes, null, onConfirmar);
    }

    public ImprimirEquiposDialog(Frame parent, String titulo,
                                 Function<String, List<Cliente>> buscarClientes,
                                 Function<String, List<Institucion>> buscarInstituciones,
                                 Callback onConfirmar) {
        super(parent, titulo, true);

        dateDesde = new JDateChooser();
        dateDesde.setDateFormatString("dd/MM/yyyy");
        dateDesde.setPreferredSize(new Dimension(140, 26));
        dateDesde.setFont(Estilos.Fuentes.INPUT);

        dateHasta = new JDateChooser();
        dateHasta.setDateFormatString("dd/MM/yyyy");
        dateHasta.setPreferredSize(new Dimension(140, 26));
        dateHasta.setFont(Estilos.Fuentes.INPUT);

        txtCliente = new JTextField();
        txtCliente.setPreferredSize(new Dimension(220, 26));
        txtCliente.setFont(Estilos.Fuentes.INPUT);

        new AutocompleteListener<>(
            txtCliente,
            buscarClientes,
            c  -> selectedClienteId = c.getId(),
            txt -> selectedClienteId = null
        );

        // ── Panel cliente ─────────────────────────────────────────────────────
        JPanel panelCliente = new JPanel(new GridBagLayout());
        panelCliente.setBorder(BorderFactory.createTitledBorder("Filtrar por cliente (opcional)"));
        panelCliente.setBackground(Color.WHITE);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 12, 10, 12);
        gbc.anchor = GridBagConstraints.WEST;

        JLabel lblCliente = new JLabel("Cliente:");
        lblCliente.setFont(Estilos.Fuentes.LABEL);
        gbc.gridx = 0; gbc.gridy = 0;
        panelCliente.add(lblCliente, gbc);
        gbc.gridx = 1;
        panelCliente.add(txtCliente, gbc);

        // ── Panel institución (opcional; solo si el reporte la soporta) ────────
        JTextField txtInstitucion = null;
        JPanel panelInstitucion = null;
        if (buscarInstituciones != null) {
            txtInstitucion = new JTextField();
            txtInstitucion.setPreferredSize(new Dimension(220, 26));
            txtInstitucion.setFont(Estilos.Fuentes.INPUT);

            new AutocompleteListener<>(
                txtInstitucion,
                buscarInstituciones,
                i   -> selectedInstitucionId = i.getId(),
                txt -> selectedInstitucionId = null
            );

            panelInstitucion = new JPanel(new GridBagLayout());
            panelInstitucion.setBorder(BorderFactory.createTitledBorder("Filtrar por institución (opcional)"));
            panelInstitucion.setBackground(Color.WHITE);

            GridBagConstraints gbcInst = new GridBagConstraints();
            gbcInst.insets = new Insets(10, 12, 10, 12);
            gbcInst.anchor = GridBagConstraints.WEST;

            JLabel lblInstitucion = new JLabel("Institución:");
            lblInstitucion.setFont(Estilos.Fuentes.LABEL);
            gbcInst.gridx = 0; gbcInst.gridy = 0;
            panelInstitucion.add(lblInstitucion, gbcInst);
            gbcInst.gridx = 1;
            panelInstitucion.add(txtInstitucion, gbcInst);
        }
        final JTextField txtInstitucionRef = txtInstitucion;

        // ── Panel fechas ──────────────────────────────────────────────────────
        JPanel panelFechas = new JPanel(new GridBagLayout());
        panelFechas.setBorder(BorderFactory.createTitledBorder("Filtrar por fecha de ingreso"));
        panelFechas.setBackground(Color.WHITE);

        gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 12, 10, 12);
        gbc.anchor = GridBagConstraints.WEST;

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

        // ── Panel central (cliente + institución + fechas apilados) ────────────
        JPanel panelCentral = new JPanel();
        panelCentral.setLayout(new BoxLayout(panelCentral, BoxLayout.Y_AXIS));
        panelCentral.setBackground(Color.WHITE);
        panelCentral.add(panelCliente);
        if (panelInstitucion != null) panelCentral.add(panelInstitucion);
        panelCentral.add(panelFechas);

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
                JOptionPane.showMessageDialog(this,
                    "La fecha 'Desde' no puede ser posterior a la fecha 'Hasta'.",
                    "Fechas inválidas", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (txtCliente.getText().trim().isEmpty()) selectedClienteId = null;
            if (txtInstitucionRef != null && txtInstitucionRef.getText().trim().isEmpty()) {
                selectedInstitucionId = null;
            }
            dispose();
            onConfirmar.onConfirmar(desde, hasta, selectedClienteId, selectedInstitucionId);
        });

        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        panelBotones.add(btnCancelar);
        panelBotones.add(btnGenerar);
        getRootPane().setDefaultButton(btnGenerar);

        setLayout(new BorderLayout());
        getRootPane().setBorder(BorderFactory.createEmptyBorder(6, 6, 0, 6));
        add(panelCentral,  BorderLayout.CENTER);
        add(panelBotones,  BorderLayout.SOUTH);

        pack();
        setMinimumSize(getSize());
        setResizable(false);
        setLocationRelativeTo(parent);
    }

    private LocalDate resolverFecha(JDateChooser chooser, String nombre) {
        if (chooser.getDate() == null) {
            JOptionPane.showMessageDialog(this,
                "Por favor seleccioná la fecha '" + nombre + "'.",
                "Fecha requerida", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return chooser.getDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
