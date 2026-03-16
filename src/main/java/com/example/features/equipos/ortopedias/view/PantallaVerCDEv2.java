package com.example.features.equipos.ortopedias.view;

import javax.swing.*;
import com.example.common.constants.Constantes;
import com.example.common.model.EquipoRegistrableInterface;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.view.helpers.PanelEquipoMaterial;
import com.example.ui.common.PanelHeader;
import com.example.ui.common.Estilos;
import com.example.ui.common.FilterUiHelper;
import com.example.ui.common.CheckableComboBox;
import java.awt.*;
import java.util.List;

/**
 * Pantalla para visualizar el estado de equipos y materiales en tiempo real.
 * Actualizada para trabajar con {@link EquipoRegistrableInterface}: muestra
 * tanto equipos de ortopedia como "otros".
 */
public class PantallaVerCDEv2 extends JPanel {

    private PanelEquipoMaterial        panelTablas;
    private JButton                    btnVerLotes;
    private JTextField                 txtFiltroCliente;
    private JTextField                 txtFiltroInstitucion;
    private CheckableComboBox<String>  cmbFiltroEstado;
    private JButton                    btnLimpiarFiltros;
    private Runnable                   onFiltrosChanged;

    public PantallaVerCDEv2(CardLayout navegador, JPanel contenedor) {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        PanelHeader header = new PanelHeader(
            Constantes.Titulos.ESTADO_PROCESOS,
            navegador, contenedor,
            Constantes.Pantallas.ESTERILIZACION
        );

        JPanel panelNorte = new JPanel(new BorderLayout());
        panelNorte.add(header,            BorderLayout.NORTH);
        panelNorte.add(crearPanelFiltros(), BorderLayout.SOUTH);
        add(panelNorte, BorderLayout.NORTH);

        panelTablas = new PanelEquipoMaterial(
            Constantes.Textos.TABLA_EQUIPOS_TITULO,
            Constantes.Textos.TABLA_MATERIALES_SELECCIONADO_TITULO,
            false
        );
        add(panelTablas, BorderLayout.CENTER);
        add(crearPanelSur(navegador, contenedor), BorderLayout.SOUTH);
    }

    private JPanel crearPanelSur(CardLayout navegador, JPanel contenedor) {
        JPanel panelSur = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        btnVerLotes = new JButton(Constantes.Botones.VER_LOTES);
        btnVerLotes.setFont(Estilos.Fuentes.BOTON);
        btnVerLotes.addActionListener(e -> navegador.show(contenedor, Constantes.Pantallas.VER_LOTES));
        panelSur.add(btnVerLotes);
        return panelSur;
    }

    private JPanel crearPanelFiltros() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

        JLabel lblCliente = new JLabel(Constantes.Textos.FILTRO_CLIENTE);
        lblCliente.setFont(Estilos.Fuentes.LABEL);
        txtFiltroCliente = new JTextField(12);
        txtFiltroCliente.setFont(Estilos.Fuentes.INPUT);

        JLabel lblInstitucion = new JLabel(Constantes.Textos.FILTRO_INSTITUCION);
        lblInstitucion.setFont(Estilos.Fuentes.LABEL);
        txtFiltroInstitucion = new JTextField(12);
        txtFiltroInstitucion.setFont(Estilos.Fuentes.INPUT);

        JLabel lblEstado = new JLabel(Constantes.Textos.FILTRO_ESTADO);
        lblEstado.setFont(Estilos.Fuentes.LABEL);

        String[] estados = new String[EstadoEquipo.values().length];
        for (int i = 0; i < EstadoEquipo.values().length; i++) {
            estados[i] = EstadoEquipo.values()[i].getNombre();
        }
        cmbFiltroEstado = new CheckableComboBox<>(estados);
        cmbFiltroEstado.setFont(Estilos.Fuentes.INPUT);
        cmbFiltroEstado.setOnSelectionChange(this::notificarCambioFiltros);

        FilterUiHelper.bindOnTextChange(this::notificarCambioFiltros, txtFiltroCliente, txtFiltroInstitucion);

        btnLimpiarFiltros = new JButton(Constantes.Botones.LIMPIAR_FILTROS);
        btnLimpiarFiltros.setFont(Estilos.Fuentes.INPUT);
        btnLimpiarFiltros.addActionListener(e -> limpiarFiltros());

        panel.add(lblCliente);      panel.add(txtFiltroCliente);
        panel.add(lblInstitucion);  panel.add(txtFiltroInstitucion);
        panel.add(lblEstado);       panel.add(cmbFiltroEstado);
        panel.add(btnLimpiarFiltros);
        return panel;
    }

    // ── API pública ───────────────────────────────────────────────────────────

    /** Actualiza la tabla con la lista filtrada de equipos (ortopedia + otros). */
    public void actualizarTabla(List<EquipoRegistrableInterface> equipos) {
        panelTablas.actualizarEquipos(equipos);
    }

    public void limpiarFiltros() {
        txtFiltroCliente.setText("");
        txtFiltroInstitucion.setText("");
        cmbFiltroEstado.clearSelection();
        notificarCambioFiltros();
    }

    private void notificarCambioFiltros() {
        if (onFiltrosChanged != null) onFiltrosChanged.run();
    }

    public void setOnFiltrosChanged(Runnable listener) { this.onFiltrosChanged = listener; }
    public String       getFiltroCliente()    { return txtFiltroCliente.getText().trim(); }
    public String       getFiltroInstitucion(){ return txtFiltroInstitucion.getText().trim(); }
    public List<String> getFiltroEstados()    { return cmbFiltroEstado.getSelectedItems(); }
}