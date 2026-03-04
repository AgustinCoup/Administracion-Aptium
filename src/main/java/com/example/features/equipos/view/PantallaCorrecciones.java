package com.example.features.equipos.view;

import com.example.common.constants.Constantes;
import com.example.common.util.Validador;
import com.example.features.equipos.model.Equipo;
import com.example.features.equipos.model.Material;
import com.example.features.equipos.view.helpers.PanelEquipoMaterial;
import com.example.ui.common.PanelHeader;
import com.example.ui.common.Estilos;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;

/**
 * Pantalla para correcciones de equipos en estado "Nuevo".
 * 
 * Estructura:
 * - Arriba: PanelEquipoMaterial (tabla equipos + tabla materiales)
 * - Abajo: Formulario para modificar cantidad/código o eliminar equipo
 * 
 * Solo permite editar equipos en estado "Nuevo".
 */
public class PantallaCorrecciones extends JPanel {
    private PanelEquipoMaterial panelTablas;
    
    // Componentes del formulario
    private JComboBox<String> cmbOperacion;
    private JSpinner spinCantidad;
    private JTextField txtCodigoNuevo;
    private JTextField txtDescripcionNueva;
    private JTextArea txtMotivo;
    private JButton btnGuardarCambios;
    private JButton btnEliminarEquipo;
    private JButton btnEliminarMaterial;
    private JButton btnVerAuditoria;
    private JLabel lblEstado;
    private JProgressBar progreso;
    
    // Labels del formulario para controlar visibilidad
    private JLabel lblCantidad;
    private JLabel lblCodigoNuevo;
    private JLabel lblDescripcionNueva;

    // Callbacks
    private QuartaConsumer<Integer, Integer, Integer, String> onModificarCantidad;
    private QuartaConsumer<Integer, Integer, Integer, String> onModificarCodigo;
    private BiConsumer<Integer, String> onEliminarEquipo;
    private TripleConsumer<Integer, Integer, String> onEliminarMaterial;
    private Runnable onVerAuditoria;
    private BiConsumer<Integer, JTextField> onCodigoNuevoChanged;
    private Runnable onPantallaVisible;

    public PantallaCorrecciones(CardLayout navegador, JPanel contenedor) {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Listener para detectar cuando se hace visible la pantalla
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                if (onPantallaVisible != null) {
                    onPantallaVisible.run();
                }
            }
        });

        // Header
        PanelHeader header = new PanelHeader(
            "Correcciones de Equipos",
            navegador,
            contenedor,
            Constantes.Pantallas.ESTERILIZACION
        );
        add(header, BorderLayout.NORTH);

        // Contenido principal: Panel con tablas (equipos arriba, materiales abajo)
        panelTablas = new PanelEquipoMaterial(
            Constantes.Textos.TABLA_EQUIPOS_TITULO,
            Constantes.Textos.TABLA_MATERIALES_TITULO,
            true  // Los materiales son seleccionables
        );
        panelTablas.setOnMaterialSelectionChanged(this::onMaterialSelectionChanged);
        panelTablas.setOnEquipoSeleccionado(this::onEquipoSeleccionado);
        add(panelTablas, BorderLayout.CENTER);

        // Panel inferior: Formulario + botones + estado
        add(crearPanelInferior(), BorderLayout.SOUTH);
    }





    /**
     * Panel del formulario para editar cantidad o código de un material.
     */
    private JPanel crearPanelFormulario() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Modificar Material Seleccionado"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Operación a realizar
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.3;
        JLabel lblOperacion = new JLabel("Tipo de cambio:");
        lblOperacion.setFont(Estilos.Fuentes.LABEL);
        panel.add(lblOperacion, gbc);

        gbc.gridx = 1; gbc.weightx = 0.7;
        cmbOperacion = new JComboBox<>(new String[]{"Modificar Cantidad", "Modificar Código"});
        cmbOperacion.setFont(Estilos.Fuentes.INPUT);
        cmbOperacion.addActionListener(e -> actualizarCamposEdicion());
        panel.add(cmbOperacion, gbc);

        // Cantidad
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.3;
        lblCantidad = new JLabel("Nueva cantidad:");
        lblCantidad.setFont(Estilos.Fuentes.LABEL);
        panel.add(lblCantidad, gbc);

        gbc.gridx = 1; gbc.weightx = 0.7;
        spinCantidad = new JSpinner(new SpinnerNumberModel(1, 1, 1000, 1));
        spinCantidad.setFont(Estilos.Fuentes.INPUT);
        panel.add(spinCantidad, gbc);

        // Código nuevo
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
        
        // Listener para buscar descripción en tiempo real
        txtCodigoNuevo.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                notificarCambioCodigo();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                notificarCambioCodigo();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                notificarCambioCodigo();
            }
        });
        
        panel.add(txtCodigoNuevo, gbc);

        // Descripción nueva (AUTOMÁTICA - solo lectura)
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

        // Motivo
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

    /**
     * Panel inferior con formulario, botones y barra de estado.
     */
    private JPanel crearPanelInferior() {
        JPanel panelPrincipal = new JPanel(new BorderLayout(5, 5));
        panelPrincipal.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Formulario
        JPanel panelFormulario = crearPanelFormulario();
        panelPrincipal.add(panelFormulario, BorderLayout.CENTER);

        // Panel con botones
        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        
        btnGuardarCambios = new JButton("Guardar Cambios");
        btnGuardarCambios.setFont(Estilos.Fuentes.BOTON);
        btnGuardarCambios.addActionListener(e -> guardarCambios());
        btnGuardarCambios.setEnabled(false);
        panelBotones.add(btnGuardarCambios);
        btnEliminarMaterial = new JButton("Eliminar Material");
        btnEliminarMaterial.setForeground(new Color(200, 100, 0));
        btnEliminarMaterial.setFont(Estilos.Fuentes.BOTON);
        btnEliminarMaterial.addActionListener(e -> solicitarEliminacionMaterial());
        btnEliminarMaterial.setEnabled(false);
        panelBotones.add(btnEliminarMaterial);


        btnEliminarEquipo = new JButton("Eliminar Equipo");
        btnEliminarEquipo.setForeground(Color.RED);
        btnEliminarEquipo.setFont(Estilos.Fuentes.BOTON);
        btnEliminarEquipo.addActionListener(e -> solicitarEliminacion());
        btnEliminarEquipo.setEnabled(false);
        panelBotones.add(btnEliminarEquipo);

        btnVerAuditoria = new JButton("Ver Auditoría");
        btnVerAuditoria.setFont(Estilos.Fuentes.BOTON);
        btnVerAuditoria.addActionListener(e -> abrirAuditoria());
        btnVerAuditoria.setEnabled(true);  // Siempre habilitado para ver todas las auditorías
        panelBotones.add(btnVerAuditoria);

        panelPrincipal.add(panelBotones, BorderLayout.SOUTH);

        // Estado y progreso
        JPanel panelEstado = new JPanel(new BorderLayout(10, 5));
        panelEstado.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        lblEstado = new JLabel("Listo");
        lblEstado.setFont(Estilos.Fuentes.LABEL);
        panelEstado.add(lblEstado, BorderLayout.WEST);

        progreso = new JProgressBar();
        progreso.setIndeterminate(false);
        progreso.setVisible(false);
        panelEstado.add(progreso, BorderLayout.CENTER);

        JPanel panelSurSur = new JPanel(new BorderLayout());
        panelSurSur.add(panelPrincipal, BorderLayout.CENTER);
        panelSurSur.add(panelEstado, BorderLayout.SOUTH);

        return panelSurSur;
    }

    /**
     * Actualiza visibilidad de campos de edición según operación seleccionada.
     */
    private void actualizarCamposEdicion() {
        boolean esModificarCantidad = "Modificar Cantidad".equals(
            cmbOperacion.getSelectedItem().toString());

        lblCantidad.setVisible(esModificarCantidad);
        spinCantidad.setVisible(esModificarCantidad);

        lblCodigoNuevo.setVisible(!esModificarCantidad);
        txtCodigoNuevo.setVisible(!esModificarCantidad);
        lblDescripcionNueva.setVisible(!esModificarCantidad);
        txtDescripcionNueva.setVisible(!esModificarCantidad);
    }

    /**
     * Se ejecuta cuando se selecciona un equipo.
     */
    private void onEquipoSeleccionado(Equipo equipo) {
        btnEliminarEquipo.setEnabled(equipo != null);
    }

    /**
     * Se ejecuta cuando cambia la selección de material.
     */
    private void onMaterialSelectionChanged() {
        boolean materialSeleccionado = panelTablas.getMaterialSeleccionadoIndex() >= 0;
        btnGuardarCambios.setEnabled(materialSeleccionado);
        btnEliminarMaterial.setEnabled(materialSeleccionado);
    }

    /**
     * Notifica al Controller cuando cambia el código nuevo.
     */
    private void notificarCambioCodigo() {
        String codigoStr = txtCodigoNuevo.getText().trim();

        if (codigoStr.isEmpty()) {
            txtDescripcionNueva.setText("");
            return;
        }

        if (!Validador.soloNumeros(codigoStr)) {
            txtDescripcionNueva.setText("Código inválido");
            return;
        }

        if (onCodigoNuevoChanged != null) {
            try {
                int codigo = Integer.parseInt(codigoStr);
                onCodigoNuevoChanged.accept(codigo, txtDescripcionNueva);
            } catch (NumberFormatException e) {
                txtDescripcionNueva.setText("Código inválido");
            }
        }
    }

    private void guardarCambios() {
        Equipo equipoSeleccionado = panelTablas.getEquipoSeleccionado();
        if (equipoSeleccionado == null) {
            mostrarError("Debe seleccionar un equipo");
            return;
        }

        int materialIdx = panelTablas.getMaterialSeleccionadoIndex();
        if (materialIdx < 0) {
            mostrarError("Debe seleccionar un material");
            return;
        }

        Material materialSeleccionado = equipoSeleccionado.getMateriales().get(materialIdx);
        String motivo = txtMotivo.getText().trim();
        if (motivo.isEmpty()) {
            mostrarError("El motivo del cambio es obligatorio");
            return;
        }

        boolean esModificarCantidad = "Modificar Cantidad".equals(cmbOperacion.getSelectedItem().toString());

        try {
            if (esModificarCantidad) {
                int cantidadNueva = (Integer) spinCantidad.getValue();
                String mensaje = construirMensajeConfirmacion(true, materialSeleccionado, cantidadNueva, null);

                int respuesta = JOptionPane.showConfirmDialog(
                    this,
                    mensaje,
                    "¿Está seguro de que quiere hacer estos cambios?",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
                );

                if (respuesta == JOptionPane.YES_OPTION) {
                    if (onModificarCantidad != null) {
                        onModificarCantidad.accept(
                            equipoSeleccionado.getId(),
                            materialSeleccionado.getId(),
                            cantidadNueva,
                            motivo
                        );
                    }
                    limpiarFormulario();
                }
            } else {
                String codigoStr = txtCodigoNuevo.getText().trim();
                if (codigoStr.isEmpty()) {
                    mostrarError("Ingrese el nuevo código de catálogo");
                    return;
                }

                Integer codigoNuevo = Integer.parseInt(codigoStr);
                String mensaje = construirMensajeConfirmacion(false, materialSeleccionado, null, codigoNuevo);

                int respuesta = JOptionPane.showConfirmDialog(
                    this,
                    mensaje,
                    "¿Está seguro de que quiere hacer estos cambios?",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
                );

                if (respuesta == JOptionPane.YES_OPTION) {
                    if (onModificarCodigo != null) {
                        onModificarCodigo.accept(
                            equipoSeleccionado.getId(),
                            materialSeleccionado.getId(),
                            codigoNuevo,
                            motivo
                        );
                    }
                    limpiarFormulario();
                }
            }
        } catch (NumberFormatException e) {
            mostrarError("Código inválido. Debe ser un número");
        }
    }

    private String construirMensajeConfirmacion(boolean esModificarCantidad,
                                                Material material,
                                                Integer cantidadNueva,
                                                Integer codigoNuevo) {
        StringBuilder mensaje = new StringBuilder();
        mensaje.append("Material: ").append(material.getDescripcion()).append("\n");
        mensaje.append("Código actual: ").append(material.getCodigo()).append("\n\n");

        if (esModificarCantidad) {
            mensaje.append("CAMBIO: Modificar cantidad\n");
            mensaje.append("Cantidad actual: ").append(material.getCantidad()).append("\n");
            mensaje.append("Cantidad nueva: ").append(cantidadNueva).append("\n");
        } else {
            mensaje.append("CAMBIO: Modificar código de catálogo\n");
            mensaje.append("Código actual: ").append(material.getCodigo()).append("\n");
            mensaje.append("Código nuevo: ").append(codigoNuevo).append("\n");
        }

        return mensaje.toString();
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
    }

    private void solicitarEliminacion() {
        Equipo equipoSeleccionado = panelTablas.getEquipoSeleccionado();
        if (equipoSeleccionado == null) {
            mostrarError("Debe seleccionar un equipo");
            return;
        }

        String motivo = JOptionPane.showInputDialog(
            this,
            "Ingrese el motivo de la eliminación:",
            "Eliminar Equipo"
        );

        if (motivo != null && !motivo.trim().isEmpty()) {
            if (onEliminarEquipo != null) {
                onEliminarEquipo.accept(equipoSeleccionado.getId(), motivo.trim());
            }
        }
    }

    private void solicitarEliminacionMaterial() {
        Equipo equipoSeleccionado = panelTablas.getEquipoSeleccionado();
        if (equipoSeleccionado == null) {
            mostrarError("Debe seleccionar un equipo");
            return;
        }

        int materialIdx = panelTablas.getMaterialSeleccionadoIndex();
        if (materialIdx < 0) {
            mostrarError("Debe seleccionar un material");
            return;
        }

        Material materialSeleccionado = equipoSeleccionado.getMateriales().get(materialIdx);
        int respuesta = JOptionPane.showConfirmDialog(
            this,
            String.format(
                "¿Está seguro de que desea eliminar TODOS los materiales con código %d (%s) del equipo?\n\nEsta acción no se puede deshacer.",
                materialSeleccionado.getCodigo(),
                materialSeleccionado.getDescripcion()
            ),
            "Confirmar Eliminación de Material",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );

        if (respuesta == JOptionPane.YES_OPTION) {
            String motivo = JOptionPane.showInputDialog(
                this,
                "Ingrese el motivo de la eliminación:",
                "Eliminar Material"
            );

            if (motivo != null && !motivo.trim().isEmpty()) {
                if (onEliminarMaterial != null) {
                    onEliminarMaterial.accept(
                        equipoSeleccionado.getId(),
                        materialSeleccionado.getCodigo(),
                        motivo.trim()
                    );
                }
            }
        }
    }

    private void abrirAuditoria() {
        if (onVerAuditoria != null) {
            onVerAuditoria.run();
        }
    }

    public void actualizarListaEquipos(List<Equipo> equipos) {
        panelTablas.actualizarEquipos(equipos);
    }

    public void recargarMateriales() {
        panelTablas.recargarMateriales();
    }

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
        btnGuardarCambios.setEnabled(!cargando && panelTablas.getMaterialSeleccionadoIndex() >= 0);
        btnEliminarMaterial.setEnabled(!cargando && panelTablas.getMaterialSeleccionadoIndex() >= 0);
        btnEliminarEquipo.setEnabled(!cargando && panelTablas.getEquipoSeleccionado() != null);
    }

    public void setOnModificarCantidad(QuartaConsumer<Integer, Integer, Integer, String> callback) {
        this.onModificarCantidad = callback;
    }

    public void setOnModificarCodigo(QuartaConsumer<Integer, Integer, Integer, String> callback) {
        this.onModificarCodigo = callback;
    }

    public void setOnEliminarEquipo(BiConsumer<Integer, String> callback) {
        this.onEliminarEquipo = callback;
    }

    public void setOnEliminarMaterial(TripleConsumer<Integer, Integer, String> callback) {
        this.onEliminarMaterial = callback;
    }

    public void setOnVerAuditoria(Runnable callback) {
        this.onVerAuditoria = callback;
    }

    public void setOnCodigoNuevoChanged(BiConsumer<Integer, JTextField> callback) {
        this.onCodigoNuevoChanged = callback;
    }

    public void setOnPantallaVisible(Runnable callback) {
        this.onPantallaVisible = callback;
    }

    @FunctionalInterface
    public interface QuartaConsumer<A, B, C, D> {
        void accept(A a, B b, C c, D d);
    }

    @FunctionalInterface
    public interface TripleConsumer<A, B, C> {
        void accept(A a, B b, C c);
    }

    @FunctionalInterface
    public interface QuintaConsumer<A, B, C, D, E> {
        void accept(A a, B b, C c, D d, E e);
    }

    @FunctionalInterface
    public interface BiConsumer<A, B> {
        void accept(A a, B b);
    }

    @FunctionalInterface
    public interface Consumer<A> {
        void accept(A a);
    }
}
