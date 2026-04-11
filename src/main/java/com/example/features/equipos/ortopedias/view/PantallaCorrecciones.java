package com.example.features.equipos.ortopedias.view;

import com.example.common.constants.Constantes;
import com.example.common.model.EquipoRegistrableInterface;
import com.example.common.util.Validador;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.Material;
import com.example.features.equipos.ortopedias.view.helpers.AgregarMaterialDialog;
import com.example.features.equipos.ortopedias.view.helpers.PanelEquipoMaterial;
import com.example.ui.common.Estilos;
import com.example.ui.common.PanelHeader;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Pantalla para correcciones de equipos en estado "Nuevo".
 *
 * Solo opera sobre equipos de ortopedia ({@link Equipo}).
 * Si el equipo seleccionado es de tipo "Otros", las operaciones de
 * modificación quedan deshabilitadas y se muestra un mensaje al usuario.
 *
 * El uso de {@link IEquipoRegistrable} en los métodos que interactúan con
 * {@link PanelEquipoMaterial} es necesario porque dicho panel ahora trabaja
 * con la interfaz; los casts internos son seguros gracias al guard instanceof.
 */
public class PantallaCorrecciones extends JPanel {

    private PanelEquipoMaterial panelTablas;

    // ── Formulario de modificación ────────────────────────────────────────────
    private JComboBox<String> cmbOperacion;
    private JSpinner          spinCantidad;
    private JTextField        txtCodigoNuevo;
    private JTextField        txtDescripcionNueva;
    private JTextArea         txtMotivo;

    // ── Botones ───────────────────────────────────────────────────────────────
    private JButton btnGuardarCambios;
    private JButton btnAgregarMaterial;
    private JButton btnEliminarMaterial;
    private JButton btnEliminarEquipo;
    private JButton btnVerAuditoria;

    // ── Estado ────────────────────────────────────────────────────────────────
    private JLabel       lblEstado;
    private JProgressBar progreso;

    // ── Labels condicionales ──────────────────────────────────────────────────
    private JLabel lblCantidad;
    private JLabel lblCodigoNuevo;
    private JLabel lblDescripcionNueva;

    // ── Callbacks ─────────────────────────────────────────────────────────────
    private QuartaConsumer<Integer, Integer, Integer, String> onModificarCantidad;
    private QuartaConsumer<Integer, Integer, Integer, String> onModificarCodigo;
    private QuartaConsumer<Integer, Integer, Integer, String> onAgregarMaterial;
    private BiConsumer<Integer, String>                       onEliminarEquipo;
    private TripleConsumer<Integer, Integer, String>          onEliminarMaterial;
    private Runnable                                          onVerAuditoria;
    private java.util.function.BiConsumer<Integer, JTextField> onCodigoNuevoChanged;
    private Runnable                                          onPantallaVisible;

    // ── Constructor ───────────────────────────────────────────────────────────

    public PantallaCorrecciones(CardLayout navegador, JPanel contenedor) {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent e) {
                if (onPantallaVisible != null) onPantallaVisible.run();
            }
        });

        PanelHeader header = new PanelHeader(
            "Correcciones de Equipos", navegador, contenedor,
            Constantes.Pantallas.ESTERILIZACION);
        add(header, BorderLayout.NORTH);

        panelTablas = new PanelEquipoMaterial(
            Constantes.Textos.TABLA_EQUIPOS_TITULO,
            Constantes.Textos.TABLA_MATERIALES_TITULO,
            true
        );
        panelTablas.setOnMaterialSelectionChanged(this::onMaterialSelectionChanged);
        panelTablas.setOnEquipoSeleccionado(this::onEquipoSeleccionado);
        add(panelTablas, BorderLayout.CENTER);

        add(crearPanelInferior(), BorderLayout.SOUTH);
    }

    // ── Construcción de la UI ─────────────────────────────────────────────────

    private JPanel crearPanelInferior() {
        JPanel panelPrincipal = new JPanel(new BorderLayout(5, 5));
        panelPrincipal.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        panelPrincipal.add(crearPanelFormulario(), BorderLayout.CENTER);
        panelPrincipal.add(crearPanelBotones(),    BorderLayout.SOUTH);

        JPanel panelEstado = new JPanel(new BorderLayout(10, 5));
        panelEstado.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        lblEstado = new JLabel("Listo");
        lblEstado.setFont(Estilos.Fuentes.LABEL);
        panelEstado.add(lblEstado, BorderLayout.WEST);
        progreso = new JProgressBar();
        progreso.setIndeterminate(false);
        progreso.setVisible(false);
        panelEstado.add(progreso, BorderLayout.CENTER);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(panelPrincipal, BorderLayout.CENTER);
        wrapper.add(panelEstado,    BorderLayout.SOUTH);
        return wrapper;
    }

    private JPanel crearPanelFormulario() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Modificar Material Seleccionado"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill   = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.3;
        JLabel lblOp = new JLabel("Tipo de cambio:");
        lblOp.setFont(Estilos.Fuentes.LABEL);
        panel.add(lblOp, gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        cmbOperacion = new JComboBox<>(new String[]{"Modificar Cantidad", "Modificar Código"});
        cmbOperacion.setFont(Estilos.Fuentes.INPUT);
        cmbOperacion.addActionListener(e -> actualizarCamposEdicion());
        panel.add(cmbOperacion, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.3;
        lblCantidad = new JLabel("Nueva cantidad:");
        lblCantidad.setFont(Estilos.Fuentes.LABEL);
        panel.add(lblCantidad, gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        spinCantidad = new JSpinner(new SpinnerNumberModel(1, 1, 1000, 1));
        spinCantidad.setFont(Estilos.Fuentes.INPUT);
        panel.add(spinCantidad, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.3;
        lblCodigoNuevo = new JLabel("Nuevo código:");
        lblCodigoNuevo.setFont(Estilos.Fuentes.LABEL);
        lblCodigoNuevo.setVisible(false);
        panel.add(lblCodigoNuevo, gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        txtCodigoNuevo = new JTextField();
        txtCodigoNuevo.setFont(Estilos.Fuentes.INPUT);
        txtCodigoNuevo.setVisible(false);
        Validador.aplicarSoloNumeros(txtCodigoNuevo);
        txtCodigoNuevo.getDocument().addDocumentListener(simpleListener(this::notificarCambioCodigo));
        panel.add(txtCodigoNuevo, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.3;
        lblDescripcionNueva = new JLabel("Descripción:");
        lblDescripcionNueva.setFont(Estilos.Fuentes.LABEL);
        lblDescripcionNueva.setVisible(false);
        panel.add(lblDescripcionNueva, gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        txtDescripcionNueva = new JTextField();
        txtDescripcionNueva.setFont(Estilos.Fuentes.INPUT);
        txtDescripcionNueva.setEditable(false);
        txtDescripcionNueva.setBackground(new Color(240, 240, 240));
        txtDescripcionNueva.setVisible(false);
        panel.add(txtDescripcionNueva, gbc);

        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0.3;
        JLabel lblMotivo = new JLabel("Motivo del cambio:");
        lblMotivo.setFont(Estilos.Fuentes.LABEL);
        panel.add(lblMotivo, gbc);
        gbc.gridx = 1; gbc.weightx = 0.7; gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        txtMotivo = new JTextArea(3, 30);
        txtMotivo.setFont(Estilos.Fuentes.INPUT);
        txtMotivo.setLineWrap(true);
        txtMotivo.setWrapStyleWord(true);
        panel.add(new JScrollPane(txtMotivo), gbc);

        return panel;
    }

    private JPanel crearPanelBotones() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));

        btnGuardarCambios = new JButton("Guardar Cambios");
        btnGuardarCambios.setFont(Estilos.Fuentes.BOTON);
        btnGuardarCambios.addActionListener(e -> guardarCambios());
        btnGuardarCambios.setEnabled(false);
        panel.add(btnGuardarCambios);

        btnAgregarMaterial = new JButton("Agregar Material");
        btnAgregarMaterial.setFont(Estilos.Fuentes.BOTON);
        btnAgregarMaterial.setForeground(new Color(0, 120, 0));
        btnAgregarMaterial.addActionListener(e -> abrirDialogoAgregarMaterial());
        btnAgregarMaterial.setEnabled(false);
        panel.add(btnAgregarMaterial);

        btnEliminarMaterial = new JButton("Eliminar Material");
        btnEliminarMaterial.setForeground(new Color(200, 100, 0));
        btnEliminarMaterial.setFont(Estilos.Fuentes.BOTON);
        btnEliminarMaterial.addActionListener(e -> solicitarEliminacionMaterial());
        btnEliminarMaterial.setEnabled(false);
        panel.add(btnEliminarMaterial);

        btnEliminarEquipo = new JButton("Eliminar Equipo");
        btnEliminarEquipo.setForeground(Color.RED);
        btnEliminarEquipo.setFont(Estilos.Fuentes.BOTON);
        btnEliminarEquipo.addActionListener(e -> solicitarEliminacion());
        btnEliminarEquipo.setEnabled(false);
        panel.add(btnEliminarEquipo);

        btnVerAuditoria = new JButton("Ver Auditoría");
        btnVerAuditoria.setFont(Estilos.Fuentes.BOTON);
        btnVerAuditoria.addActionListener(e -> abrirAuditoria());
        panel.add(btnVerAuditoria);

        return panel;
    }

    // ── Diálogo "Agregar Material" ────────────────────────────────────────────

    private void abrirDialogoAgregarMaterial() {
        Equipo equipo = obtenerEquipoOrtopedia("Agregar Material");
        if (equipo == null) return;

        AgregarMaterialDialog dialogo = new AgregarMaterialDialog(onCodigoNuevoChanged);

        int resultado = JOptionPane.showConfirmDialog(
            this, dialogo.panel, "Agregar Material al Equipo",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (resultado != JOptionPane.OK_OPTION) return;

        String codigoStr = dialogo.getCodigo();
        String motivo    = dialogo.getMotivo();
        if (codigoStr.isEmpty())               { mostrarError("Debe ingresar un código de catálogo"); return; }
        if (!Validador.soloNumeros(codigoStr)) { mostrarError("El código debe ser un número entero"); return; }
        if (motivo.isEmpty())                  { mostrarError("El motivo es obligatorio"); return; }

        Integer codigo   = Integer.parseInt(codigoStr);
        Integer cantidad = dialogo.getCantidad();

        boolean codigoYaExiste = equipo.getMateriales().stream()
            .anyMatch(m -> m.getCodigo() == codigo);
        if (codigoYaExiste) {
            mostrarError(
                "El equipo ya tiene un material con el código " + codigo + ".\n" +
                "Use 'Modificar Cantidad' si desea cambiar la cantidad de ese material.");
            return;
        }

        String descFinal = dialogo.getDescripcion();
        int confirmar = JOptionPane.showConfirmDialog(this,
            String.format("¿Agregar al equipo de %s?\n\nMaterial: %s - %s\nCantidad: %d",
                equipo.getClienteNombre(), codigo,
                descFinal.isEmpty() ? "(desconocido)" : descFinal, cantidad),
            "Confirmar adición", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (confirmar == JOptionPane.YES_OPTION && onAgregarMaterial != null) {
            onAgregarMaterial.accept(equipo.getId(), codigo, cantidad, motivo);
        }
    }

    // ── Lógica de la UI ───────────────────────────────────────────────────────

    private void actualizarCamposEdicion() {
        boolean esModificarCantidad =
            "Modificar Cantidad".equals(cmbOperacion.getSelectedItem().toString());
        lblCantidad.setVisible(esModificarCantidad);
        spinCantidad.setVisible(esModificarCantidad);
        lblCodigoNuevo.setVisible(!esModificarCantidad);
        txtCodigoNuevo.setVisible(!esModificarCantidad);
        lblDescripcionNueva.setVisible(!esModificarCantidad);
        txtDescripcionNueva.setVisible(!esModificarCantidad);
    }

    /**
     * Callback del panel cuando se selecciona un equipo.
     * Habilita botones SOLO si es un equipo de ortopedia.
     */
    private void onEquipoSeleccionado(EquipoRegistrableInterface equipo) {
        boolean esOrtopedia = equipo instanceof Equipo;
        btnEliminarEquipo.setEnabled(esOrtopedia);
        btnAgregarMaterial.setEnabled(esOrtopedia);
        if (equipo != null && !esOrtopedia) {
            mostrarMensaje("Correcciones solo disponibles para equipos de ortopedia.");
        }
    }

    private void onMaterialSelectionChanged() {
        boolean sel          = panelTablas.getMaterialSeleccionadoIndex() >= 0;
        boolean esOrtopedia  = panelTablas.getEquipoSeleccionado() instanceof Equipo;
        btnGuardarCambios.setEnabled(sel && esOrtopedia);
        btnEliminarMaterial.setEnabled(sel && esOrtopedia);
    }

    private void notificarCambioCodigo() {
        String codigoStr = txtCodigoNuevo.getText().trim();
        if (codigoStr.isEmpty()) { txtDescripcionNueva.setText(""); return; }
        if (!Validador.soloNumeros(codigoStr)) { txtDescripcionNueva.setText("Código inválido"); return; }
        if (onCodigoNuevoChanged != null) {
            try { onCodigoNuevoChanged.accept(Integer.parseInt(codigoStr), txtDescripcionNueva); }
            catch (NumberFormatException e) { txtDescripcionNueva.setText("Código inválido"); }
        }
    }

    private void guardarCambios() {
        Equipo equipo = obtenerEquipoOrtopedia("Guardar cambios");
        if (equipo == null) return;

        int matIdx = panelTablas.getMaterialSeleccionadoIndex();
        if (matIdx < 0) { mostrarError("Debe seleccionar un material"); return; }

        Material material = equipo.getMateriales().get(matIdx);
        String motivo = txtMotivo.getText().trim();
        if (motivo.isEmpty()) { mostrarError("El motivo del cambio es obligatorio"); return; }

        boolean esModificarCantidad =
            "Modificar Cantidad".equals(cmbOperacion.getSelectedItem().toString());

        try {
            if (esModificarCantidad) {
                int cantidadNueva = (Integer) spinCantidad.getValue();
                if (confirmar(construirMensajeConfirmacion(true, material, cantidadNueva, null))) {
                    if (onModificarCantidad != null)
                        onModificarCantidad.accept(equipo.getId(), material.getId(), cantidadNueva, motivo);
                    limpiarFormulario();
                }
            } else {
                String codigoStr = txtCodigoNuevo.getText().trim();
                if (codigoStr.isEmpty()) { mostrarError("Ingrese el nuevo código de catálogo"); return; }
                Integer codigoNuevo = Integer.parseInt(codigoStr);
                if (confirmar(construirMensajeConfirmacion(false, material, null, codigoNuevo))) {
                    if (onModificarCodigo != null)
                        onModificarCodigo.accept(equipo.getId(), material.getId(), codigoNuevo, motivo);
                    limpiarFormulario();
                }
            }
        } catch (NumberFormatException e) {
            mostrarError("Código inválido. Debe ser un número");
        }
    }

    private String construirMensajeConfirmacion(boolean esModificarCantidad, Material material,
                                                Integer cantidadNueva, Integer codigoNuevo) {
        StringBuilder sb = new StringBuilder();
        sb.append("Material: ").append(material.getDescripcion()).append("\n");
        sb.append("Código actual: ").append(material.getCodigo()).append("\n\n");
        if (esModificarCantidad) {
            sb.append("CAMBIO: Modificar cantidad\n");
            sb.append("Cantidad actual: ").append(material.getCantidad()).append("\n");
            sb.append("Cantidad nueva: ").append(cantidadNueva).append("\n");
        } else {
            sb.append("CAMBIO: Modificar código de catálogo\n");
            sb.append("Código actual: ").append(material.getCodigo()).append("\n");
            sb.append("Código nuevo: ").append(codigoNuevo).append("\n");
        }
        return sb.toString();
    }

    private boolean confirmar(String mensaje) {
        return JOptionPane.showConfirmDialog(this, mensaje,
            "¿Está seguro de que quiere hacer estos cambios?",
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    private void limpiarFormulario() {
        spinCantidad.setValue(1);
        txtCodigoNuevo.setText("");
        txtDescripcionNueva.setText("");
        txtMotivo.setText("");
        cmbOperacion.setSelectedIndex(0);
        actualizarCamposEdicion();
        panelTablas.limpiarSeleccion();
        btnGuardarCambios.setEnabled(false);
        btnEliminarMaterial.setEnabled(false);
        btnEliminarEquipo.setEnabled(false);
        btnAgregarMaterial.setEnabled(false);
    }

    private void solicitarEliminacion() {
        Equipo equipo = obtenerEquipoOrtopedia("Eliminar equipo");
        if (equipo == null) return;
        String motivo = JOptionPane.showInputDialog(
            this, "Ingrese el motivo de la eliminación:", "Eliminar Equipo", JOptionPane.WARNING_MESSAGE);
        if (motivo != null && !motivo.trim().isEmpty() && onEliminarEquipo != null)
            onEliminarEquipo.accept(equipo.getId(), motivo.trim());
    }

    private void solicitarEliminacionMaterial() {
        Equipo equipo = obtenerEquipoOrtopedia("Eliminar material");
        if (equipo == null) return;
        int matIdx = panelTablas.getMaterialSeleccionadoIndex();
        if (matIdx < 0) { mostrarError("Debe seleccionar un material"); return; }

        Material material = equipo.getMateriales().get(matIdx);
        int resp = JOptionPane.showConfirmDialog(this,
            String.format("¿Está seguro de que desea eliminar TODOS los materiales con código %d (%s) del equipo?\n\nEsta acción no se puede deshacer.",
                material.getCodigo(), material.getDescripcion()),
            "Confirmar Eliminación de Material", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (resp == JOptionPane.YES_OPTION) {
            String motivo = JOptionPane.showInputDialog(
                this, "Ingrese el motivo de la eliminación:", "Eliminar Material", JOptionPane.WARNING_MESSAGE);
            if (motivo != null && !motivo.trim().isEmpty() && onEliminarMaterial != null)
                onEliminarMaterial.accept(equipo.getId(), material.getCodigo(), motivo.trim());
        }
    }

    private void abrirAuditoria() {
        if (onVerAuditoria != null) onVerAuditoria.run();
    }

    /**
     * Obtiene el equipo seleccionado casteado a {@link Equipo} (ortopedia).
     * Si no hay selección o el equipo es de tipo "Otros", muestra un mensaje
     * apropiado y retorna null.
     *
     * @param accion Nombre de la acción que intentó el usuario (para el mensaje)
     */
    private Equipo obtenerEquipoOrtopedia(String accion) {
        EquipoRegistrableInterface sel = panelTablas.getEquipoSeleccionado();
        if (sel == null) {
            mostrarError("Debe seleccionar un equipo");
            return null;
        }
        if (!(sel instanceof Equipo)) {
            mostrarError("La acción \"" + accion + "\" solo está disponible para equipos de ortopedia.");
            return null;
        }
        return (Equipo) sel;
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private static DocumentListener simpleListener(Runnable accion) {
        return new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { accion.run(); }
            @Override public void removeUpdate(DocumentEvent e)  { accion.run(); }
            @Override public void changedUpdate(DocumentEvent e) { accion.run(); }
        };
    }

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Llamado por {@link com.example.features.equipos.controller.CorreccionsController}.
     * Acepta List<Equipo> (solo ortopedia) y hace el upcasting internamente.
     */
    public void actualizarListaEquipos(List<Equipo> equipos) {
        List<EquipoRegistrableInterface> lista = new ArrayList<>(equipos);
        panelTablas.actualizarEquipos(lista);
    }

    public void recargarMateriales()  { panelTablas.recargarMateriales(); }

    public void limpiarPantalla() {
        limpiarFormulario();
        lblEstado.setText("Listo");
        lblEstado.setForeground(Color.BLACK);
    }

    public void mostrarMensaje(String mensaje) {
        lblEstado.setText("✓ " + mensaje);
        lblEstado.setForeground(new Color(0, 153, 0));
    }

    public void mostrarError(String mensaje) {
        lblEstado.setText("✗ " + mensaje);
        lblEstado.setForeground(Color.RED);
        JOptionPane.showMessageDialog(this, mensaje, "Error", JOptionPane.ERROR_MESSAGE);
    }

    public void mostrarCargando(boolean cargando) {
        progreso.setVisible(cargando);
        panelTablas.setEnabled(!cargando);
        boolean matSel      = panelTablas.getMaterialSeleccionadoIndex() >= 0;
        boolean eqOrtopedia = panelTablas.getEquipoSeleccionado() instanceof Equipo;
        btnGuardarCambios.setEnabled(!cargando && matSel && eqOrtopedia);
        btnEliminarMaterial.setEnabled(!cargando && matSel && eqOrtopedia);
        btnEliminarEquipo.setEnabled(!cargando && eqOrtopedia);
        btnAgregarMaterial.setEnabled(!cargando && eqOrtopedia);
    }

    // ── Setters de callbacks ──────────────────────────────────────────────────

    public void setOnModificarCantidad(QuartaConsumer<Integer, Integer, Integer, String> cb) { onModificarCantidad  = cb; }
    public void setOnModificarCodigo  (QuartaConsumer<Integer, Integer, Integer, String> cb) { onModificarCodigo    = cb; }
    public void setOnAgregarMaterial  (QuartaConsumer<Integer, Integer, Integer, String> cb) { onAgregarMaterial    = cb; }
    public void setOnEliminarEquipo   (BiConsumer<Integer, String> cb)                       { onEliminarEquipo     = cb; }
    public void setOnEliminarMaterial (TripleConsumer<Integer, Integer, String> cb)          { onEliminarMaterial   = cb; }
    public void setOnVerAuditoria     (Runnable cb)                                          { onVerAuditoria       = cb; }
    public void setOnCodigoNuevoChanged(java.util.function.BiConsumer<Integer, JTextField> cb) { onCodigoNuevoChanged = cb; }
    public void setOnPantallaVisible  (Runnable cb)                                          { onPantallaVisible    = cb; }

    // ── Interfaces funcionales ────────────────────────────────────────────────

    @FunctionalInterface public interface QuartaConsumer<A, B, C, D> { void accept(A a, B b, C c, D d); }
    @FunctionalInterface public interface TripleConsumer<A, B, C>    { void accept(A a, B b, C c); }
    @FunctionalInterface public interface BiConsumer<A, B>           { void accept(A a, B b); }
    @FunctionalInterface public interface Consumer<A>                 { void accept(A a); }
}