package com.example.features.equipos.ortopedias.view;

import com.example.common.constants.Constantes;
import com.example.common.model.EquipoRegistrableInterface;
import com.example.common.model.MaterialRegistrableInterface;
import com.example.common.util.Validador;
import com.example.ui.common.RestriccionesCampo;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.Material;
import com.example.features.equipos.ortopedias.view.helpers.AgregarMaterialDialog;
import com.example.features.equipos.ortopedias.view.helpers.AgregarMaterialOtrosDialog;
import com.example.features.equipos.ortopedias.view.helpers.PanelEquipoMaterial;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.MaterialOtros;
import com.example.features.equipos.otros.model.TipoIngresoOtros;
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
import java.util.function.Function;

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

    // ── Callbacks ortopedia ───────────────────────────────────────────────────
    private QuartaConsumer<Integer, Integer, Integer, String> onModificarCantidad;
    private QuartaConsumer<Integer, Integer, Integer, String> onModificarCodigo;
    private QuartaConsumer<Integer, Integer, Integer, String> onAgregarMaterial;
    private BiConsumer<Integer, String>                       onEliminarEquipo;
    private TripleConsumer<Integer, Integer, String>          onEliminarMaterial;

    // ── Callbacks otros ───────────────────────────────────────────────────────
    private TripleConsumer<Integer, Integer, String>          onModificarCantidadRemito;
    private QuartaConsumer<Integer, Integer, Integer, String> onModificarCantidadMaterialOtros;
    private QuartaConsumer<Integer, String, Integer, String>  onAgregarMaterialOtros;
    private TripleConsumer<Integer, String, String>           onEliminarMaterialOtros;
    private BiConsumer<Integer, String>                       onEliminarEquipoOtros;

    // ── Callbacks generales ───────────────────────────────────────────────────
    private Runnable                                          onVerAuditoria;
    private java.util.function.BiConsumer<Integer, JTextField> onCodigoNuevoChanged;
    private Runnable                                          onPantallaVisible;

    // ── Catálogo otros (para el diálogo "Agregar Material") ───────────────────
    private Function<String, List<String>> buscarMaterialesOtros;
    private Function<String, Boolean>      verificarMaterialOtros;

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
            Constantes.Pantallas.REGISTRAR_ESTADO);
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
        cmbOperacion = new JComboBox<>(new String[]{Constantes.Mensajes.OPERACION_MODIFICAR_CANTIDAD, Constantes.Mensajes.OPERACION_MODIFICAR_CODIGO});
        cmbOperacion.setFont(Estilos.Fuentes.INPUT);
        cmbOperacion.addActionListener(e -> actualizarCamposEdicion());
        panel.add(cmbOperacion, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.3;
        lblCantidad = new JLabel("Nueva cantidad:");
        lblCantidad.setFont(Estilos.Fuentes.LABEL);
        panel.add(lblCantidad, gbc);
        gbc.gridx = 1; gbc.weightx = 0.7; gbc.fill = GridBagConstraints.NONE; gbc.anchor = GridBagConstraints.WEST;
        spinCantidad = new JSpinner(new SpinnerNumberModel(1, 1, 1000, 1));
        spinCantidad.setFont(Estilos.Fuentes.INPUT);
        JSpinner.NumberEditor cantEditor = new JSpinner.NumberEditor(spinCantidad, "#");
        spinCantidad.setEditor(cantEditor);
        cantEditor.getTextField().setColumns(spinCantidad.getValue().toString().length());
        spinCantidad.addChangeListener(ev -> {
            int digits = spinCantidad.getValue().toString().length();
            JTextField tf = ((JSpinner.NumberEditor) spinCantidad.getEditor()).getTextField();
            if (tf.getColumns() != digits) {
                tf.setColumns(digits);
                spinCantidad.invalidate();
                if (spinCantidad.getParent() != null) {
                    spinCantidad.getParent().revalidate();
                    spinCantidad.getParent().repaint();
                }
            }
        });
        panel.add(spinCantidad, gbc);
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.anchor = GridBagConstraints.CENTER;

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.3;
        lblCodigoNuevo = new JLabel("Nuevo código:");
        lblCodigoNuevo.setFont(Estilos.Fuentes.LABEL);
        lblCodigoNuevo.setVisible(false);
        panel.add(lblCodigoNuevo, gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        txtCodigoNuevo = new JTextField();
        txtCodigoNuevo.setFont(Estilos.Fuentes.INPUT);
        txtCodigoNuevo.setVisible(false);
        RestriccionesCampo.soloNumeros(txtCodigoNuevo);
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
        EquipoRegistrableInterface sel = panelTablas.getEquipoSeleccionado();
        if (sel == null) { mostrarError(Constantes.Mensajes.DEBE_SELECCIONAR_EQUIPO); return; }

        if (sel instanceof EquipoOtros) {
            EquipoOtros equipoOtros = (EquipoOtros) sel;
            AgregarMaterialOtrosDialog dialogo = new AgregarMaterialOtrosDialog(
                buscarMaterialesOtros  != null ? buscarMaterialesOtros  : t -> List.of(),
                verificarMaterialOtros != null ? verificarMaterialOtros : t -> false);
            int resultado = JOptionPane.showConfirmDialog(
                this, dialogo.panel, "Agregar Material al Equipo",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (resultado != JOptionPane.OK_OPTION) return;
            String desc   = dialogo.getDescripcion();
            String motivo = dialogo.getMotivo();
            if (desc.isEmpty())   { mostrarError("La descripción es obligatoria"); return; }
            if (motivo.isEmpty()) { mostrarError(Constantes.Mensajes.MOTIVO_OBLIGATORIO); return; }
            int cantidad = dialogo.getCantidad();
            if (confirmar(String.format("¿Agregar al equipo de %s?\n\nMaterial: %s\nCantidad: %d",
                    equipoOtros.getClienteNombre(), desc, cantidad))) {
                if (onAgregarMaterialOtros != null)
                    onAgregarMaterialOtros.accept(equipoOtros.getId(), desc, cantidad, motivo);
            }
            return;
        }

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
        if (motivo.isEmpty())                  { mostrarError(Constantes.Mensajes.MOTIVO_OBLIGATORIO); return; }

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
        if (confirmar(String.format("¿Agregar al equipo de %s?\n\nMaterial: %s - %s\nCantidad: %d",
                equipo.getClienteNombre(), codigo,
                descFinal.isEmpty() ? "(desconocido)" : descFinal, cantidad))) {
            if (onAgregarMaterial != null)
                onAgregarMaterial.accept(equipo.getId(), codigo, cantidad, motivo);
        }
    }

    // ── Lógica de la UI ───────────────────────────────────────────────────────

    private void actualizarCamposEdicion() {
        boolean esModificarCantidad =
            Constantes.Mensajes.OPERACION_MODIFICAR_CANTIDAD.equals(cmbOperacion.getSelectedItem().toString());
        lblCantidad.setVisible(esModificarCantidad);
        spinCantidad.setVisible(esModificarCantidad);
        lblCodigoNuevo.setVisible(!esModificarCantidad);
        txtCodigoNuevo.setVisible(!esModificarCantidad);
        lblDescripcionNueva.setVisible(!esModificarCantidad);
        txtDescripcionNueva.setVisible(!esModificarCantidad);
    }

    private void onEquipoSeleccionado(EquipoRegistrableInterface equipo) {
        boolean esOrtopedia = equipo instanceof Equipo;
        boolean esRemito    = equipo instanceof EquipoOtros
            && ((EquipoOtros) equipo).getTipoIngreso() == TipoIngresoOtros.REMITO;
        boolean esDetalles  = equipo instanceof EquipoOtros
            && ((EquipoOtros) equipo).getTipoIngreso() == TipoIngresoOtros.DETALLES;

        // Constantes.Mensajes.OPERACION_MODIFICAR_CODIGO no aplica a equipos Otros (sin código numérico)
        cmbOperacion.setEnabled(esOrtopedia);
        if (!esOrtopedia) {
            cmbOperacion.setSelectedIndex(0);
            actualizarCamposEdicion();
        }

        btnEliminarEquipo.setEnabled(esOrtopedia || esRemito || esDetalles);
        btnAgregarMaterial.setEnabled(esOrtopedia || esDetalles);
        // guardar y eliminarMaterial quedan deshabilitados hasta que se seleccione un material
        btnGuardarCambios.setEnabled(false);
        btnEliminarMaterial.setEnabled(false);
    }

    private void onMaterialSelectionChanged() {
        boolean sel = panelTablas.getMaterialSeleccionadoIndex() >= 0;
        EquipoRegistrableInterface equipo = panelTablas.getEquipoSeleccionado();
        boolean esOrtopedia = equipo instanceof Equipo;
        boolean esDetalles  = equipo instanceof EquipoOtros
            && ((EquipoOtros) equipo).getTipoIngreso() == TipoIngresoOtros.DETALLES;
        boolean esRemito    = equipo instanceof EquipoOtros
            && ((EquipoOtros) equipo).getTipoIngreso() == TipoIngresoOtros.REMITO;

        btnGuardarCambios.setEnabled(sel && (esOrtopedia || esDetalles || esRemito));
        btnEliminarMaterial.setEnabled(sel && (esOrtopedia || esDetalles));
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
        EquipoRegistrableInterface sel = panelTablas.getEquipoSeleccionado();
        if (sel == null) { mostrarError(Constantes.Mensajes.DEBE_SELECCIONAR_EQUIPO); return; }

        String motivo = txtMotivo.getText().trim();
        if (motivo.isEmpty()) { mostrarError("El motivo del cambio es obligatorio"); return; }

        if (sel instanceof EquipoOtros) {
            EquipoOtros equipoOtros = (EquipoOtros) sel;
            int cantidadNueva = (Integer) spinCantidad.getValue();

            if (equipoOtros.getTipoIngreso() == TipoIngresoOtros.REMITO) {
                if (confirmar("¿Modificar cantidad del remito a " + cantidadNueva + "?")) {
                    if (onModificarCantidadRemito != null)
                        onModificarCantidadRemito.accept(equipoOtros.getId(), cantidadNueva, motivo);
                    limpiarFormulario();
                }
            } else {
                int matIdx = panelTablas.getMaterialSeleccionadoIndex();
                if (matIdx < 0) { mostrarError(Constantes.Mensajes.DEBE_SELECCIONAR_MATERIAL); return; }
                List<MaterialRegistrableInterface> mats = equipoOtros.getMaterialesRegistrables();
                MaterialOtros material = (MaterialOtros) mats.get(matIdx);
                if (confirmar("¿Modificar cantidad de \"" + material.getDescripcion() + "\" a " + cantidadNueva + "?")) {
                    if (onModificarCantidadMaterialOtros != null)
                        onModificarCantidadMaterialOtros.accept(equipoOtros.getId(), material.getId(), cantidadNueva, motivo);
                    limpiarFormulario();
                }
            }
            return;
        }

        Equipo equipo = obtenerEquipoOrtopedia("Guardar cambios");
        if (equipo == null) return;

        int matIdx = panelTablas.getMaterialSeleccionadoIndex();
        if (matIdx < 0) { mostrarError(Constantes.Mensajes.DEBE_SELECCIONAR_MATERIAL); return; }

        Material material = equipo.getMateriales().get(matIdx);
        boolean esModificarCantidad =
            Constantes.Mensajes.OPERACION_MODIFICAR_CANTIDAD.equals(cmbOperacion.getSelectedItem().toString());

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
        EquipoRegistrableInterface sel = panelTablas.getEquipoSeleccionado();
        if (sel == null) { mostrarError(Constantes.Mensajes.DEBE_SELECCIONAR_EQUIPO); return; }

        String motivo = JOptionPane.showInputDialog(
            this, Constantes.Mensajes.MOTIVO_PROMPT, "Eliminar Equipo", JOptionPane.WARNING_MESSAGE);
        if (motivo == null || motivo.trim().isEmpty()) return;

        if (sel instanceof EquipoOtros) {
            if (onEliminarEquipoOtros != null)
                onEliminarEquipoOtros.accept(sel.getId(), motivo.trim());
        } else if (sel instanceof Equipo && onEliminarEquipo != null) {
            onEliminarEquipo.accept(sel.getId(), motivo.trim());
        }
    }

    private void solicitarEliminacionMaterial() {
        EquipoRegistrableInterface sel = panelTablas.getEquipoSeleccionado();
        if (sel == null) { mostrarError(Constantes.Mensajes.DEBE_SELECCIONAR_EQUIPO); return; }
        int matIdx = panelTablas.getMaterialSeleccionadoIndex();
        if (matIdx < 0) { mostrarError(Constantes.Mensajes.DEBE_SELECCIONAR_MATERIAL); return; }

        if (sel instanceof EquipoOtros) {
            EquipoOtros equipoOtros = (EquipoOtros) sel;
            MaterialOtros material  = equipoOtros.getMateriales().get(matIdx);
            int resp = JOptionPane.showConfirmDialog(this,
                String.format("¿Está seguro de que desea eliminar todos los materiales con descripción \"%s\" del equipo?\n\nEsta acción no se puede deshacer.",
                    material.getDescripcion()),
                "Confirmar Eliminación de Material", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (resp == JOptionPane.YES_OPTION) {
                String motivo = JOptionPane.showInputDialog(
                    this, Constantes.Mensajes.MOTIVO_PROMPT, "Eliminar Material", JOptionPane.WARNING_MESSAGE);
                if (motivo != null && !motivo.trim().isEmpty() && onEliminarMaterialOtros != null)
                    onEliminarMaterialOtros.accept(equipoOtros.getId(), material.getDescripcion(), motivo.trim());
            }
            return;
        }

        Equipo equipo = obtenerEquipoOrtopedia("Eliminar material");
        if (equipo == null) return;
        Material material = equipo.getMateriales().get(matIdx);
        int resp = JOptionPane.showConfirmDialog(this,
            String.format("¿Está seguro de que desea eliminar TODOS los materiales con código %d (%s) del equipo?\n\nEsta acción no se puede deshacer.",
                material.getCodigo(), material.getDescripcion()),
            "Confirmar Eliminación de Material", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (resp == JOptionPane.YES_OPTION) {
            String motivo = JOptionPane.showInputDialog(
                this, Constantes.Mensajes.MOTIVO_PROMPT, "Eliminar Material", JOptionPane.WARNING_MESSAGE);
            if (motivo != null && !motivo.trim().isEmpty() && onEliminarMaterial != null)
                onEliminarMaterial.accept(equipo.getId(), material.getCodigo(), motivo.trim());
        }
    }

    private void abrirAuditoria() {
        if (onVerAuditoria != null) onVerAuditoria.run();
    }

    private Equipo obtenerEquipoOrtopedia(String accion) {
        EquipoRegistrableInterface sel = panelTablas.getEquipoSeleccionado();
        if (sel == null) {
            mostrarError(Constantes.Mensajes.DEBE_SELECCIONAR_EQUIPO);
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

    public void actualizarListaEquipos(List<Equipo> equipos) {
        List<EquipoRegistrableInterface> lista = new ArrayList<>(equipos);
        panelTablas.actualizarEquipos(lista);
    }

    public void actualizarListaEquiposUnificada(List<EquipoRegistrableInterface> equipos) {
        panelTablas.actualizarEquipos(equipos);
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
        EquipoRegistrableInterface sel = panelTablas.getEquipoSeleccionado();
        boolean matSel     = panelTablas.getMaterialSeleccionadoIndex() >= 0;
        boolean esOrtopedia = sel instanceof Equipo;
        boolean esDetalles  = sel instanceof EquipoOtros
            && ((EquipoOtros) sel).getTipoIngreso() == TipoIngresoOtros.DETALLES;
        boolean esRemito    = sel instanceof EquipoOtros
            && ((EquipoOtros) sel).getTipoIngreso() == TipoIngresoOtros.REMITO;

        btnGuardarCambios.setEnabled(!cargando && matSel && (esOrtopedia || esDetalles || esRemito));
        btnEliminarMaterial.setEnabled(!cargando && matSel && (esOrtopedia || esDetalles));
        btnEliminarEquipo.setEnabled(!cargando && (esOrtopedia || esDetalles || esRemito));
        btnAgregarMaterial.setEnabled(!cargando && (esOrtopedia || esDetalles));
    }

    // ── Setters de callbacks ──────────────────────────────────────────────────

    public void setOnModificarCantidad(QuartaConsumer<Integer, Integer, Integer, String> cb)         { onModificarCantidad               = cb; }
    public void setOnModificarCodigo  (QuartaConsumer<Integer, Integer, Integer, String> cb)         { onModificarCodigo                 = cb; }
    public void setOnAgregarMaterial  (QuartaConsumer<Integer, Integer, Integer, String> cb)         { onAgregarMaterial                 = cb; }
    public void setOnEliminarEquipo   (BiConsumer<Integer, String> cb)                               { onEliminarEquipo                  = cb; }
    public void setOnEliminarMaterial (TripleConsumer<Integer, Integer, String> cb)                  { onEliminarMaterial                = cb; }
    public void setOnModificarCantidadRemito        (TripleConsumer<Integer, Integer, String> cb)    { onModificarCantidadRemito         = cb; }
    public void setOnModificarCantidadMaterialOtros (QuartaConsumer<Integer, Integer, Integer, String> cb) { onModificarCantidadMaterialOtros = cb; }
    public void setOnAgregarMaterialOtros           (QuartaConsumer<Integer, String, Integer, String> cb)  { onAgregarMaterialOtros           = cb; }
    public void setOnEliminarMaterialOtros          (TripleConsumer<Integer, String, String> cb)     { onEliminarMaterialOtros           = cb; }
    public void setOnEliminarEquipoOtros            (BiConsumer<Integer, String> cb)                 { onEliminarEquipoOtros             = cb; }
    public void setBuscarMaterialesOtros            (Function<String, List<String>> fn)              { buscarMaterialesOtros             = fn; }
    public void setVerificarMaterialOtros           (Function<String, Boolean> fn)                   { verificarMaterialOtros            = fn; }
    public void setOnVerAuditoria     (Runnable cb)                                                  { onVerAuditoria                    = cb; }
    public void setOnCodigoNuevoChanged(java.util.function.BiConsumer<Integer, JTextField> cb)       { onCodigoNuevoChanged              = cb; }
    public void setOnPantallaVisible  (Runnable cb)                                                  { onPantallaVisible                 = cb; }

    // ── Interfaces funcionales ────────────────────────────────────────────────

    @FunctionalInterface public interface QuartaConsumer<A, B, C, D> { void accept(A a, B b, C c, D d); }
    @FunctionalInterface public interface TripleConsumer<A, B, C>    { void accept(A a, B b, C c); }
    @FunctionalInterface public interface BiConsumer<A, B>           { void accept(A a, B b); }
    @FunctionalInterface public interface Consumer<A>                 { void accept(A a); }
}
