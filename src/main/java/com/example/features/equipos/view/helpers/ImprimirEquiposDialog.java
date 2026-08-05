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

    private final CampoFiltroEntidad<Cliente>     filtroCliente;
    /** null cuando el reporte no admite filtro por institución (caso "Otros"). */
    private final CampoFiltroEntidad<Institucion> filtroInstitucion;

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

        dateDesde = crearDateChooser();
        dateHasta = crearDateChooser();

        filtroCliente = new CampoFiltroEntidad<>(
            "Filtrar por cliente (opcional)", "Cliente:", buscarClientes, Cliente::getId);
        filtroInstitucion = buscarInstituciones == null ? null : new CampoFiltroEntidad<>(
            "Filtrar por institución (opcional)", "Institución:", buscarInstituciones, Institucion::getId);

        // ── Panel fechas ──────────────────────────────────────────────────────
        JPanel panelFechas = new JPanel(new GridBagLayout());
        panelFechas.setBorder(BorderFactory.createTitledBorder("Filtrar por fecha de ingreso"));
        panelFechas.setBackground(Color.WHITE);

        GridBagConstraints gbc = new GridBagConstraints();
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
        panelCentral.add(filtroCliente.getPanel());
        if (filtroInstitucion != null) panelCentral.add(filtroInstitucion.getPanel());
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
            dispose();
            onConfirmar.onConfirmar(desde, hasta,
                filtroCliente.idConfirmado(),
                filtroInstitucion != null ? filtroInstitucion.idConfirmado() : null);
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

    private static JDateChooser crearDateChooser() {
        JDateChooser chooser = new JDateChooser();
        chooser.setDateFormatString("dd/MM/yyyy");
        chooser.setPreferredSize(new Dimension(140, 26));
        chooser.setFont(Estilos.Fuentes.INPUT);
        return chooser;
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

    /**
     * Devuelve el id de {@code seleccionada} solo si {@code textoVisible} sigue
     * coincidiendo con ella; null en cualquier otro caso.
     *
     * <p>Es la regla que evita filtrar por una entidad que el usuario ya editó:
     * {@code AutocompleteListener} solo avisa de un texto sin correspondencia al
     * perder el foco y con al menos 3 caracteres, así que un residuo de 1-2
     * ("Ho" tras borrar "Hospital A") dejaba viva la selección anterior.
     * Derivar el id del texto visible al confirmar no depende de ningún evento.
     */
    static <T> Integer idSiCoincide(T seleccionada, String textoVisible, Function<T, Integer> extraerId) {
        if (seleccionada == null || textoVisible == null) return null;
        return seleccionada.toString().equals(textoVisible.trim())
             ? extraerId.apply(seleccionada)
             : null;
    }

    /**
     * Campo de filtro por entidad con autocompletado: agrupa el panel con borde,
     * la etiqueta, el campo de texto y el listener que rastrea la selección.
     *
     * <p>Comparte forma entre cliente e institución para que la regla de
     * {@link #idSiCoincide} viva en un solo lugar.
     */
    static final class CampoFiltroEntidad<T> {

        private final JPanel                  panel;
        private final JTextField              campo;
        private final AutocompleteListener<T> autocomplete;
        private final Function<T, Integer>    extraerId;

        CampoFiltroEntidad(String tituloBorde, String etiqueta,
                           Function<String, List<T>> buscar,
                           Function<T, Integer> extraerId) {
            this.extraerId = extraerId;

            campo = new JTextField();
            campo.setPreferredSize(new Dimension(220, 26));
            campo.setFont(Estilos.Fuentes.INPUT);
            // El id se deriva del texto visible al confirmar, no de callbacks de selección.
            autocomplete = new AutocompleteListener<>(campo, buscar, seleccionada -> { });

            panel = new JPanel(new GridBagLayout());
            panel.setBorder(BorderFactory.createTitledBorder(tituloBorde));
            panel.setBackground(Color.WHITE);

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(10, 12, 10, 12);
            gbc.anchor = GridBagConstraints.WEST;

            JLabel label = new JLabel(etiqueta);
            label.setFont(Estilos.Fuentes.LABEL);
            gbc.gridx = 0; gbc.gridy = 0;
            panel.add(label, gbc);
            gbc.gridx = 1;
            panel.add(campo, gbc);
        }

        JPanel     getPanel() { return panel; }
        JTextField getCampo() { return campo; }

        /** Id de la entidad elegida, o null si el texto del campo ya no coincide con ella. */
        Integer idConfirmado() {
            return idSiCoincide(autocomplete.getSelectedItem(), campo.getText(), extraerId);
        }
    }
}
