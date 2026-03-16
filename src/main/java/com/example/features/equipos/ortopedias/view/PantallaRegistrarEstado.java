package com.example.features.equipos.ortopedias.view;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.example.common.constants.Constantes;
import com.example.common.model.EquipoRegistrableInterface;
import com.example.ui.dialogs.CantidadDialogHelper;
import com.example.ui.common.Estilos;
import com.example.features.equipos.ortopedias.view.helpers.PanelEquipoMaterial;
import com.example.ui.common.PanelHeader;

/**
 * Pantalla para registrar cambios de estado en materiales.
 *
 * Refactorizada para trabajar con {@link IEquipoRegistrable}: acepta
 * tanto equipos de ortopedia como equipos "otros" sin cambios de UI.
 */
public class PantallaRegistrarEstado extends JPanel {

    private final CardLayout    navegador;
    private final JPanel        contenedor;
    private PanelHeader         header;
    private PanelEquipoMaterial panelTablas;
    private JButton btnAvanzar;
    private JButton btnGestionarLotes;
    private JButton btnConfirmar;
    private JButton btnCancelar;
    private JButton btnCorrecciones;
    private JLabel  lblCambiosPendientes;

    public PantallaRegistrarEstado(CardLayout navegador, JPanel contenedor) {
        this.navegador  = navegador;
        this.contenedor = contenedor;
        setLayout(new BorderLayout());

        header = new PanelHeader(
            Constantes.Titulos.REGISTRAR_ESTADO,
            navegador,
            contenedor,
            Constantes.Pantallas.ESTERILIZACION
        );
        add(header, BorderLayout.NORTH);

        panelTablas = new PanelEquipoMaterial(
            Constantes.Textos.TABLA_EQUIPOS_TITULO,
            Constantes.Textos.TABLA_MATERIALES_TITULO,
            true
        );
        add(panelTablas, BorderLayout.CENTER);
        add(crearPanelBotones(), BorderLayout.SOUTH);
    }

    private JPanel crearPanelBotones() {
        JPanel panelPrincipal = new JPanel(new BorderLayout());
        panelPrincipal.setBorder(Estilos.Espaciados.BORDE_PRINCIPAL);

        JPanel panelInfo = new JPanel(new FlowLayout(FlowLayout.LEFT));
        lblCambiosPendientes = new JLabel(String.format(Constantes.Textos.CAMBIOS_PENDIENTES, 0));
        lblCambiosPendientes.setFont(Estilos.Fuentes.LABEL);
        panelInfo.add(lblCambiosPendientes);
        panelPrincipal.add(panelInfo, BorderLayout.WEST);

        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));

        btnGestionarLotes = new JButton("Gestionar Lotes");
        btnGestionarLotes.setFont(Estilos.Fuentes.BOTON);

        btnAvanzar = new JButton(Constantes.Textos.BOTON_SELECCIONE_MATERIAL);
        btnAvanzar.setFont(Estilos.Fuentes.BOTON);
        btnAvanzar.setEnabled(false);
        btnAvanzar.setVisible(false);

        btnCancelar = new JButton(Constantes.Botones.CANCELAR);
        btnCancelar.setFont(Estilos.Fuentes.BOTON);
        btnCancelar.setEnabled(false);

        btnConfirmar = new JButton(Constantes.Botones.CONFIRMAR_GUARDAR);
        btnConfirmar.setFont(Estilos.Fuentes.BOTON);
        btnConfirmar.setEnabled(false);

        panelBotones.add(btnGestionarLotes);
        panelBotones.add(btnAvanzar);
        panelBotones.add(btnCancelar);
        panelBotones.add(btnConfirmar);
        panelPrincipal.add(panelBotones, BorderLayout.CENTER);

        JPanel panelCorrecciones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        btnCorrecciones = new JButton("Correcciones");
        btnCorrecciones.setFont(Estilos.Fuentes.BOTON);
        panelCorrecciones.add(btnCorrecciones);
        panelPrincipal.add(panelCorrecciones, BorderLayout.EAST);

        return panelPrincipal;
    }

    // ── API para el controller ────────────────────────────────────────────────

    public void actualizarEquipos(List<EquipoRegistrableInterface> equipos) {
        panelTablas.actualizarEquipos(equipos);
    }

    public EquipoRegistrableInterface getEquipoSeleccionado() {
        return panelTablas.getEquipoSeleccionado();
    }

    public int getMaterialSeleccionadoIndex() {
        return panelTablas.getMaterialSeleccionadoIndex();
    }

    public void recargarMateriales()       { panelTablas.recargarMateriales(); }
    public void refrescarEstadosEquipos()  { panelTablas.refrescarEstadosEquipos(); }

    public void setOnEquipoSeleccionado(Consumer<EquipoRegistrableInterface> listener) {
        panelTablas.setOnEquipoSeleccionado(listener);
    }

    public void setOnMaterialSeleccionado(ListSelectionListener listener) {
        panelTablas.getTablaMateriales().getSelectionModel().addListSelectionListener(listener);
    }

    public void setOnAvanzar(java.awt.event.ActionListener l)        { btnAvanzar.addActionListener(l); }
    public void setOnConfirmar(java.awt.event.ActionListener l)      { btnConfirmar.addActionListener(l); }
    public void setOnCancelar(java.awt.event.ActionListener l)       { btnCancelar.addActionListener(l); }
    public void setOnGestionarLotes(java.awt.event.ActionListener l) { btnGestionarLotes.addActionListener(l); }
    public void setOnCorrecciones(java.awt.event.ActionListener l)   { btnCorrecciones.addActionListener(l); }

    public void setCambiosPendientesCount(int total) {
        lblCambiosPendientes.setText(String.format(Constantes.Textos.CAMBIOS_PENDIENTES, total));
    }

    public void setAvanzarEnabled(boolean v)  { btnAvanzar.setEnabled(v); }
    public void setAvanzarVisible(boolean v)  { btnAvanzar.setVisible(v); }
    public void setAvanzarTexto(String t)     { btnAvanzar.setText(t); }
    public void setConfirmarEnabled(boolean v){ btnConfirmar.setEnabled(v); }
    public void setCancelarEnabled(boolean v) { btnCancelar.setEnabled(v); }

    public void mostrarAdvertencia(String m) {
        JOptionPane.showMessageDialog(this, m, Constantes.Mensajes.TITULO_ADVERTENCIA, JOptionPane.WARNING_MESSAGE);
    }
    public void mostrarInfo(String m) {
        JOptionPane.showMessageDialog(this, m, Constantes.Mensajes.TITULO_EXITO, JOptionPane.INFORMATION_MESSAGE);
    }
    public void mostrarError(String m) {
        JOptionPane.showMessageDialog(this, m, Constantes.Mensajes.TITULO_ERROR, JOptionPane.ERROR_MESSAGE);
    }
    public boolean confirmar(String m, String t) {
        return JOptionPane.showConfirmDialog(this, m, t, JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE)
               == JOptionPane.YES_OPTION;
    }

    public Integer pedirCantidadParaAvanzar(String descripcion, int disponible,
                                             BiConsumer<JCheckBox, JSpinner> cfg) {
        return CantidadDialogHelper.pedirCantidad(this, descripcion, disponible, cfg);
    }

    public void navegarALotes()       { navegador.show(contenedor, Constantes.Pantallas.LOTES); }
    public void navegarACorrecciones(){ navegador.show(contenedor, Constantes.Pantallas.CORRECCIONES); }

    public void setGuardVolver(Supplier<Boolean> hayPendientes, String mensajeBloqueo) {
        header.setGuardNavegacion(hayPendientes, mensajeBloqueo);
    }
    public void setGuardVolver(Supplier<Boolean> hayPendientes, String mensajeBloqueo,
                               Runnable onDescartarConfirmado) {
        header.setGuardNavegacion(hayPendientes, mensajeBloqueo, onDescartarConfirmado);
    }
}