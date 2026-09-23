package com.example.features.lavadero.view;

import com.example.common.constants.Constantes;
import com.example.features.lavadero.model.ConfiguracionCopiada;
import com.example.features.lavadero.model.ElementoCicloItem;
import com.example.features.lavadero.model.InsumoCatalogo;
import com.example.features.lavadero.model.JabonCatalogo;
import com.example.features.lavadero.model.OrigenJabon;
import com.example.features.lavadero.model.TipoLavado;
import com.example.features.lavadero.view.helpers.LavarropasCardTableModel;
import com.example.features.lavadero.view.helpers.PanelInsumosCard;
import com.example.ui.common.RestriccionesCampo;
import com.example.ui.common.TableStyler;
import com.example.ui.common.dnd.TableSelectionSupport;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class LavarropasCard extends JPanel {

    private static final Font  FONT_CARD    = new Font(Font.SANS_SERIF, Font.BOLD, 11);
    private static final Font  FONT_CONFIG  = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    private static final Color COLOR_OCUPADO = new Color(0xC8C8C8);

    private final int numero;
    private final LavarropasCardTableModel tableModel = new LavarropasCardTableModel();
    private final JTable tabla;

    private final JPanel panelNorth = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
    private final JLabel lblToggle  = new JLabel("▼ ");
    private final JLabel lblTitulo;
    private final JLabel lblEstado  = new JLabel("[LIBRE]");

    private final JComboBox<TipoLavado>    cmbTipoLavado    = new JComboBox<>(TipoLavado.values());
    private final JComboBox<JabonCatalogo> cmbJabon         = new JComboBox<>();
    private final JTextField              txtLitrosJabon   = new JTextField(4);
    private final PanelInsumosCard        panelInsumos     = new PanelInsumosCard();
    private final JButton              btnCopiar        = new JButton(Constantes.Botones.COPIAR);
    private final JButton              btnPegar         = new JButton(Constantes.Botones.PEGAR);
    private final JButton              btnAccion        = new JButton("Lanzar");
    private final JPanel               panelConfig;

    // Promovidos a campo para poder mostrar/ocultar desde toggleColapso()
    private final JScrollPane scrollTabla;
    private final JPanel      panelSouth;

    private boolean activo      = false;
    private Integer cicloActivo = null;
    private boolean collapsed   = false;

    /**
     * Quién puso el jabón que muestra el combo. Arranca en {@link OrigenJabon#AUTO} <b>con el
     * combo vacío</b>, para que la primera elección de tipo de lavado lo complete sola.
     */
    private OrigenJabon origenJabon = OrigenJabon.AUTO;

    /**
     * Estamos tocando el combo de jabón desde el código, no el operador.
     *
     * <p>Hace falta porque {@code setSelectedItem} dispara <b>el mismo</b> {@code ActionListener}
     * que un click, y los dos no significan lo mismo: sin este flag, el jabón que puso la carga
     * automática se marcaría {@link OrigenJabon#MANUAL} él solo y a partir de ahí el tipo de lavado
     * dejaría de arrastrarlo — el operador vería que "a veces anda". Lo mismo vale para el
     * {@code removeAllItems} + {@code addItem} de {@link #setJabones}, que también avisa.</p>
     *
     * <p>Es el patrón {@code silenciandoCallback} de {@code PantallaHistorialLavadero}.</p>
     */
    private boolean aplicandoCambioProgramatico = false;

    private Runnable onAccion;
    private Runnable onConfiguracionChanged;
    private Runnable onTipoLavadoChanged;
    private Runnable onCopiar;
    private Runnable onPegar;

    public LavarropasCard(int numero) {
        this.numero    = numero;
        this.lblTitulo = new JLabel("Lavarropas #" + numero);

        setLayout(new BorderLayout(0, 2));
        setBorder(BorderFactory.createLineBorder(Color.GRAY));
        setBackground(Color.WHITE);

        // North: toggle + título + estado
        lblToggle.setFont(FONT_CARD);
        lblTitulo.setFont(FONT_CARD);
        lblEstado.setFont(FONT_CONFIG);
        panelNorth.setOpaque(true);
        panelNorth.add(lblToggle);
        panelNorth.add(lblTitulo);
        panelNorth.add(lblEstado);
        panelNorth.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { toggleColapso(); }
        });
        add(panelNorth, BorderLayout.NORTH);

        // Center: table
        tabla = new JTable(tableModel);
        TableSelectionSupport.enableMultiSelection(tabla);
        TableStyler.applyStandard(tabla);
        TableStyler.centerColumns(tabla, 1);
        tabla.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        tabla.setFillsViewportHeight(true);
        scrollTabla = new JScrollPane(tabla,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollTabla.setMinimumSize(new Dimension(0, 60));
        scrollTabla.setPreferredSize(new Dimension(0, 90));
        add(scrollTabla, BorderLayout.CENTER);

        // South: config + button
        panelConfig = buildConfigPanel();
        panelSouth = new JPanel(new BorderLayout());
        panelSouth.add(panelConfig, BorderLayout.CENTER);
        btnAccion.setFont(FONT_CARD);
        btnAccion.setEnabled(false);
        btnAccion.addActionListener(e -> { if (onAccion != null) onAccion.run(); });
        panelSouth.add(btnAccion, BorderLayout.SOUTH);
        add(panelSouth, BorderLayout.SOUTH);

        actualizarToggle();

        // Colapsar por defecto: todos los cards arrancan libres y vacíos
        collapsed = true;
        aplicarColapso();
    }

    /**
     * Se estira a lo ancho de su columna pero nunca a lo alto: sin este límite, el
     * {@code BoxLayout} de {@code construirGrillaDeCards()} reparte el espacio sobrante de la
     * columna entre todas las cards (no solo en el glue final), dejando un hueco en blanco
     * debajo incluso con la card contraída.
     */
    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    private JPanel buildConfigPanel() {
        JPanel config = new JPanel();
        config.setLayout(new BoxLayout(config, BoxLayout.Y_AXIS));
        config.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        for (JComponent c : new JComponent[]{cmbTipoLavado, cmbJabon, txtLitrosJabon}) {
            c.setFont(FONT_CONFIG);
        }
        RestriccionesCampo.soloNumerosDecimales(txtLitrosJabon);

        // El tipo de lavado arranca vacío: es una elección explícita del operador, y además es lo
        // que dispara la carga automática del jabón.
        cmbTipoLavado.setSelectedItem(null);
        cmbTipoLavado.addActionListener(e -> {
            notificarConfiguracionChanged();
            notificarTipoLavadoChanged();
        });
        // Un cambio del combo de jabón cuenta como elección a mano SALVO que lo esté haciendo el
        // código (carga automática, pegado, repoblado del catálogo): ver aplicandoCambioProgramatico.
        cmbJabon.addActionListener(e -> {
            if (!aplicandoCambioProgramatico) origenJabon = OrigenJabon.MANUAL;
            notificarConfiguracionChanged();
        });
        panelInsumos.setOnCambio(this::notificarConfiguracionChanged);

        config.add(rowPanel("Tipo:", cmbTipoLavado));
        config.add(rowPanel("Jabón:", cmbJabon));
        config.add(rowPanel("mL Jabón:", txtLitrosJabon));
        config.add(panelInsumos);

        // Adentro de panelConfig a propósito: setModoActivo lo oculta entero, así que "una card
        // ocupada no se copia ni se pega" sale gratis, sin una condición acá ni en el controller.
        btnCopiar.setFont(FONT_CONFIG);
        btnPegar.setFont(FONT_CONFIG);
        btnCopiar.addActionListener(e -> { if (onCopiar != null) onCopiar.run(); });
        btnPegar.setEnabled(false);
        btnPegar.addActionListener(e -> { if (onPegar != null) onPegar.run(); });
        JPanel filaBotonesCopiarPegar = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 1));
        filaBotonesCopiarPegar.add(btnCopiar);
        filaBotonesCopiarPegar.add(btnPegar);
        config.add(filaBotonesCopiarPegar);

        DocumentListener notificador = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { notificarConfiguracionChanged(); }
            @Override public void removeUpdate(DocumentEvent e)  { notificarConfiguracionChanged(); }
            @Override public void changedUpdate(DocumentEvent e) { notificarConfiguracionChanged(); }
        };
        txtLitrosJabon.getDocument().addDocumentListener(notificador);

        return config;
    }

    /** Un solo canal para todos los campos de la config: tipo, jabón, mL de jabón e insumos extra. */
    private void notificarConfiguracionChanged() {
        if (onConfiguracionChanged != null) SwingUtilities.invokeLater(onConfiguracionChanged);
    }

    /**
     * Canal <b>aparte</b> del de configuración, y no un caso particular de aquél: el controller lo
     * usa para correr {@code SelectorJabonAutomatico}, que escribe en el combo de jabón. Va
     * sincrónico —sin {@code invokeLater}— para que el jabón quede puesto antes de que
     * {@code actualizarBtnAccion()} lo mire: diferido, el botón "Lanzar" se encendería un evento
     * más tarde y la card se vería a medio completar.
     */
    private void notificarTipoLavadoChanged() {
        if (onTipoLavadoChanged != null) onTipoLavadoChanged.run();
    }

    /**
     * Corre {@code accion} sin que el combo de jabón lo cuente como elección del operador.
     *
     * <p>Reentrante a propósito (guarda y restaura el valor previo): {@link #resetConfiguracion()}
     * ya corre adentro de este flag y podría terminar llamando a algo que lo vuelva a pedir.</p>
     */
    private void programaticamente(Runnable accion) {
        boolean previo = aplicandoCambioProgramatico;
        aplicandoCambioProgramatico = true;
        try {
            accion.run();
        } finally {
            aplicandoCambioProgramatico = previo;
        }
    }

    private static JPanel rowPanel(String labelText, JComponent field) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 1));
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(FONT_CONFIG);
        row.add(lbl);
        row.add(field);
        return row;
    }

    // ── Colapso ──────────────────────────────────────────────────────────────

    public boolean puedeContraerse() {
        return activo || !tieneItems();
    }

    public void toggleColapso() {
        if (!puedeContraerse()) return;
        collapsed = !collapsed;
        aplicarColapso();
    }

    public void colapsarSiPuede() {
        if (puedeContraerse() && !collapsed) {
            collapsed = true;
            aplicarColapso();
        }
    }

    private void aplicarColapso() {
        scrollTabla.setVisible(!collapsed);
        panelSouth.setVisible(!collapsed);
        lblToggle.setText(collapsed ? "▶ " : "▼ ");
        revalidate();
        repaint();
    }

    private void actualizarToggle() {
        boolean puede = puedeContraerse();
        lblToggle.setVisible(puede);
        panelNorth.setCursor(puede
            ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            : Cursor.getDefaultCursor());

        // Si acaba de recibir ítems en staging, forzar expansión
        if (!puede && collapsed) {
            collapsed = false;
            aplicarColapso();
        } else {
            lblToggle.setText(collapsed ? "▶ " : "▼ ");
        }
    }

    // ── Estado ───────────────────────────────────────────────────────────────

    public void setModoActivo(int cicloId) {
        this.activo      = true;
        this.cicloActivo = cicloId;
        lblEstado.setText("[OCUPADO]");
        panelNorth.setBackground(COLOR_OCUPADO);
        panelConfig.setVisible(false);
        btnAccion.setText("Finalizar");
        btnAccion.setEnabled(true);
        actualizarToggle();
        revalidate();
        repaint();
    }

    public void setModoStaging() {
        this.activo      = false;
        this.cicloActivo = null;
        lblEstado.setText("[LIBRE]");
        panelNorth.setBackground(UIManager.getColor("Panel.background"));
        panelConfig.setVisible(true);
        btnAccion.setText("Lanzar");
        actualizarBtnAccion();
        actualizarToggle();
        revalidate();
        repaint();
    }

    public boolean estaActivo()     { return activo; }
    public Integer getCicloActivo() { return cicloActivo; }

    /**
     * Deja la configuración (tipo de lavado, jabón, mililitros de jabón e insumos extra) en su
     * estado inicial. Se llama al abrir la pantalla, no en cada refresco: pisar esto durante un
     * lanzamiento borraría lo que el operador está tipeando en otra card.
     *
     * <p>Vuelve también a {@link OrigenJabon#AUTO}: una card reseteada no tiene ninguna elección a
     * mano que respetar, y dejarla en {@code MANUAL} con el combo vacío la volvería inmune a la
     * carga automática para siempre.</p>
     */
    public void resetConfiguracion() {
        programaticamente(() -> {
            cmbTipoLavado.setSelectedItem(null);
            cmbJabon.setSelectedItem(null);
            txtLitrosJabon.setText("");
            panelInsumos.limpiar();
        });
        origenJabon = OrigenJabon.AUTO;
        actualizarBtnAccion();
    }

    // ── Datos ────────────────────────────────────────────────────────────────

    public void setItems(List<ElementoCicloItem> items, Map<Integer, Integer> fracciones) {
        tableModel.setItems(
            items      != null ? items      : Collections.emptyList(),
            fracciones != null ? fracciones : Collections.emptyMap()
        );
        actualizarToggle();
    }

    public boolean tieneItems() { return tableModel.getRowCount() > 0; }

    // ── Config getters ────────────────────────────────────────────────────────

    /** Puede ser {@code null}: el combo arranca sin selección y el tipo es obligatorio. */
    public TipoLavado getTipoLavado() { return (TipoLavado) cmbTipoLavado.getSelectedItem(); }

    public JabonCatalogo getJabon()  { return (JabonCatalogo) cmbJabon.getSelectedItem(); }

    /** Quién puso el jabón que muestra el combo. Lo lee {@code SelectorJabonAutomatico}. */
    public OrigenJabon getOrigenJabon() { return origenJabon; }

    /**
     * Repuebla el catálogo del combo de jabón <b>conservando lo que ya estaba elegido</b>, si ese
     * jabón sigue estando en la lista nueva; si no está, limpia la selección y vuelve a
     * {@link OrigenJabon#AUTO}.
     *
     * <p>Antes dejaba el combo sin selección siempre, porque el jabón era "una elección explícita
     * del operador, no un default que se lleva puesto sin mirar". Con el jabón automático eso
     * dejó de ser cierto —ahora sí hay un default por tipo de lavado— y además los catálogos
     * pasaron a releerse en <b>cada</b> lectura de la pantalla (es lo que hace que un cambio en
     * Ajustes se vea al volver a Ciclos): vaciando la selección, cada F5 le borraría el jabón a
     * una card a medio configurar, que es el mismo invariante que {@code recargar()} protege para
     * el resto de la config.</p>
     *
     * <p><b>La comparación es por {@link JabonCatalogo#getId()}, nunca por referencia.</b> Dos
     * lecturas del catálogo devuelven objetos distintos para el mismo jabón y {@code JabonCatalogo}
     * no tiene {@code equals}, así que buscar el elegido con {@code contains} daría siempre "no
     * está" y el efecto sería el de vaciar el combo en cada refresco.</p>
     */
    public void setJabones(java.util.List<JabonCatalogo> jabones) {
        JabonCatalogo elegido = getJabon();
        programaticamente(() -> {
            cmbJabon.removeAllItems();
            for (JabonCatalogo j : jabones) cmbJabon.addItem(j);
            JabonCatalogo reencontrado = buscarEnCombo(elegido);
            cmbJabon.setSelectedItem(reencontrado);
            if (reencontrado == null) origenJabon = OrigenJabon.AUTO;
        });
    }

    /**
     * Carga el jabón que decidió la regla automática. Deja el origen en {@link OrigenJabon#AUTO},
     * así que un cambio posterior de tipo de lavado lo vuelve a reemplazar.
     */
    public void setJabonAutomatico(JabonCatalogo jabon) {
        seleccionarJabon(jabon, OrigenJabon.AUTO);
    }

    /**
     * Carga un jabón como si lo hubiera elegido el operador: lo usa el pegado de configuración.
     * Deja el origen en {@link OrigenJabon#MANUAL}, o sea que el tipo de lavado ya no lo pisa —
     * pegar es una elección a mano, y la regla es que ésa siempre pesa más.
     */
    public void setJabonManual(JabonCatalogo jabon) {
        seleccionarJabon(jabon, OrigenJabon.MANUAL);
    }

    /**
     * <p><b>Hay que resolver la instancia del combo por id, no pasarle la que llega.</b> Un
     * {@code JComboBox} no editable <b>rechaza en silencio</b> un {@code setSelectedItem} con un
     * objeto que no sea {@code equals} a alguno de sus ítems, y {@code JabonCatalogo} compara por
     * referencia: el jabón que viene del mapa de defaults es otro objeto que el del catálogo del
     * combo, así que pasárselo tal cual no seleccionaría nada — y sin un solo error.</p>
     *
     * <p>Si el jabón no está en el catálogo del combo no se toca nada, que es lo mismo que hace la
     * regla cuando no hay default: el combo sólo ofrece jabones activos, y forzar uno que no
     * ofrece dejaría la card mostrando algo que el operador no puede volver a elegir.</p>
     */
    private void seleccionarJabon(JabonCatalogo jabon, OrigenJabon origen) {
        JabonCatalogo enElCombo = buscarEnCombo(jabon);
        if (enElCombo == null) return;
        programaticamente(() -> cmbJabon.setSelectedItem(enElCombo));
        origenJabon = origen;
    }

    /** El ítem del combo con el mismo id, o {@code null}. Ver {@link #seleccionarJabon}. */
    private JabonCatalogo buscarEnCombo(JabonCatalogo jabon) {
        if (jabon == null) return null;
        for (int i = 0; i < cmbJabon.getItemCount(); i++) {
            JabonCatalogo item = cmbJabon.getItemAt(i);
            if (item != null && item.getId() == jabon.getId()) return item;
        }
        return null;
    }

    public BigDecimal getLitrosJabon() {
        try {
            String t = txtLitrosJabon.getText().trim().replace(",", ".");
            if (t.isEmpty()) return null;
            BigDecimal v = new BigDecimal(t);
            return v.compareTo(BigDecimal.ZERO) > 0 ? v : null;
        } catch (NumberFormatException e) { return null; }
    }

    /**
     * Repuebla el catálogo del combo de insumos, pero <b>no</b> borra los que el operador ya
     * eligió — la misma regla que {@link #setJabones}, por el mismo motivo: los dos catálogos se
     * releen en cada carga de la pantalla (se editan desde Ajustes), así que pisar lo elegido
     * sería borrarle el trabajo al operador en cada F5, el mismo bug que {@code recargar()} evita
     * para el resto de la config.
     */
    public void setInsumos(java.util.List<InsumoCatalogo> catalogo) {
        panelInsumos.setCatalogo(catalogo);
    }

    public java.util.List<InsumoCatalogo> getInsumosSeleccionados() {
        return panelInsumos.getSeleccionados();
    }

    // ── DnD ──────────────────────────────────────────────────────────────────

    public JTable getTabla() { return tabla; }

    /** Ítems de las filas seleccionadas en la tabla de la card. */
    public List<ElementoCicloItem> getItemsSeleccionados() {
        return TableSelectionSupport.selectedItems(tabla, tableModel::getItemAt);
    }

    // ── Listeners ────────────────────────────────────────────────────────────

    public void setOnAccion(Runnable r)               { this.onAccion = r; }
    public void setOnConfiguracionChanged(Runnable r) { this.onConfiguracionChanged = r; }

    /**
     * Canal aparte del de configuración: avisa <b>sólo</b> cuando cambió el tipo de lavado, que es
     * lo único que dispara la carga automática del jabón. Meterlo dentro de
     * {@code onConfiguracionChanged} obligaría a ese callback a recordar el tipo anterior para
     * saber si tiene que correr la regla, y correrla de más pisaría el jabón al tipear los mL.
     */
    public void setOnTipoLavadoChanged(Runnable r)    { this.onTipoLavadoChanged = r; }

    public void setOnCopiar(Runnable r) { this.onCopiar = r; }
    public void setOnPegar(Runnable r)  { this.onPegar = r; }

    /** Enciende o apaga "Pegar". Lo maneja el controller: sabe si hay algo en el portapapeles. */
    public void setPegarHabilitado(boolean habilitado) { btnPegar.setEnabled(habilitado); }

    // ── Copiar y pegar configuración ────────────────────────────────────────

    /** No incluye los elementos cargados — eso es staging, no configuración de card. */
    public ConfiguracionCopiada copiarConfiguracion() {
        return new ConfiguracionCopiada(
            getTipoLavado(), getJabon(), getLitrosJabon(), getInsumosSeleccionados());
    }

    /**
     * Pega una configuración copiada de otra card, en este orden: el <b>tipo</b> (que puede
     * disparar la carga automática del jabón, vía {@code onTipoLavadoChanged}), el <b>jabón</b>
     * con {@link #setJabonManual}, que pisa lo que el automático acaba de poner y deja el origen
     * en {@link OrigenJabon#MANUAL}, los <b>mL</b> y por último los <b>insumos</b>, que
     * reemplazan la lista elegida y no se suman a ella.
     *
     * <p><b>El orden entre tipo y jabón no es cosmético.</b> Al revés —jabón primero, tipo
     * después— el automático que dispara el cambio de tipo pisaría el jabón recién pegado.</p>
     *
     * <p>No toca los elementos cargados ni el estado de la tabla.</p>
     */
    public void pegarConfiguracion(ConfiguracionCopiada c) {
        cmbTipoLavado.setSelectedItem(c.tipo());
        setJabonManual(c.jabon());
        txtLitrosJabon.setText(c.litrosJabon() == null ? "" : c.litrosJabon().toString());
        panelInsumos.setSeleccionados(c.insumos());
    }

    /**
     * Apaga el botón de acción mientras hay una escritura en vuelo, para que un segundo
     * click no vuelva a lanzar (o finalizar) lo mismo. No hace falta volver a encenderlo a
     * mano: el refresco posterior repinta la card y {@link #actualizarBtnAccion()} recalcula.
     */
    public void deshabilitarAccion() {
        btnAccion.setEnabled(false);
    }

    /**
     * Los tres campos obligatorios del ciclo: tipo de lavado, jabón y mililitros de jabón. Los
     * insumos extra <b>no</b> entran acá — son opcionales, y un ciclo sin ninguno es un ciclo
     * válido. Es la <b>única</b> definición de "config completa" de la pantalla: la usa esta card
     * para decidir si se puede lanzar y {@code CiclosController} para validar los grupos
     * repartidos.
     */
    public boolean tieneConfiguracionCompleta() {
        return getTipoLavado() != null && getJabon() != null && getLitrosJabon() != null;
    }

    public void actualizarBtnAccion() {
        if (activo) {
            btnAccion.setEnabled(true);
        } else {
            btnAccion.setEnabled(tieneItems() && tieneConfiguracionCompleta());
        }
    }
}
