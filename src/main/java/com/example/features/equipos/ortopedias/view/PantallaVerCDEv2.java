package com.example.features.equipos.ortopedias.view;

import javax.swing.*;
import com.example.common.constants.Constantes;
import com.example.common.model.EquipoRegistrableInterface;
import com.example.common.paginacion.Pagina;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.view.helpers.PanelEquipoMaterial;
import com.example.ui.common.PanelHeader;
import com.example.ui.common.Estilos;
import com.example.ui.common.FilterUiHelper;
import com.example.ui.common.CheckableComboBox;
import com.example.ui.common.PanelPaginacion;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Pantalla para visualizar el estado de equipos y materiales en tiempo real.
 * Actualizada para trabajar con {@link EquipoRegistrableInterface}: muestra
 * tanto equipos de ortopedia como "otros".
 */
public class PantallaVerCDEv2 extends JPanel {

    private PanelHeader                header;
    private PanelEquipoMaterial        panelTablas;
    private JButton                    btnVerLotes;
    private JTextField                 txtFiltroCliente;
    private JTextField                 txtFiltroInstitucion;
    private CheckableComboBox<String>  cmbFiltroEstado;
    private JButton                    btnLimpiarFiltros;
    private Runnable                   onFiltrosChanged;
    private final PanelPaginacion      panelPaginacion = new PanelPaginacion();

    public PantallaVerCDEv2(CardLayout navegador, JPanel contenedor) {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        header = new PanelHeader(
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

    /** Cablea el botón "Actualizar" (y F5) del header a la relectura de la pantalla. */
    public void setAccionRefrescar(Runnable accion) {
        header.setAccionRefrescar(accion);
    }

    /** Muestra la hora del último pintado en el header. */
    public void marcarActualizado() {
        header.marcarActualizado();
    }

    private JPanel crearPanelSur(CardLayout navegador, JPanel contenedor) {
        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        btnVerLotes = new JButton(Constantes.Botones.VER_LOTES);
        btnVerLotes.setFont(Estilos.Fuentes.BOTON);
        btnVerLotes.addActionListener(e -> navegador.show(contenedor, Constantes.Pantallas.VER_LOTES));
        panelBotones.add(btnVerLotes);

        JPanel panelSur = new JPanel(new BorderLayout());
        panelSur.add(panelPaginacion, BorderLayout.NORTH);
        panelSur.add(panelBotones,    BorderLayout.SOUTH);
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

    /**
     * Vuelca la página de equipos (ortopedia + otros) a la tabla, <b>en el orden en que viene</b>.
     *
     * <p>El orden lo fija la base, que es el único lugar que ve todas las filas; reordenar acá
     * ordenaría dentro de la página. Por eso va a {@code actualizarEquiposEnOrden} y no a
     * {@code actualizarEquipos}.
     */
    public void actualizarTabla(List<EquipoRegistrableInterface> equipos) {
        panelTablas.actualizarEquiposEnOrden(equipos);
    }

    /** Qué hacer cuando el operador pide otra página. Recibe el número pedido, base 1. */
    public void setAlCambiarPagina(IntConsumer accion) {
        panelPaginacion.setAlCambiarPagina(accion);
    }

    /**
     * Actualiza la barra de paginación con la página que se acaba de pintar. Va junto con
     * {@link #actualizarTabla(List)}: la barra describe lo que la tabla muestra.
     */
    public void mostrarPaginacion(Pagina<?> pagina) {
        panelPaginacion.mostrar(pagina);
    }

    /**
     * Limpia todo: deja ver también los equipos entregados. Es la contraparte
     * explícita de {@link #aplicarFiltroInicial()}.
     */
    public void limpiarFiltros() {
        txtFiltroCliente.setText("");
        txtFiltroInstitucion.setText("");
        cmbFiltroEstado.clearSelection();
        notificarCambioFiltros();
    }

    /**
     * Aplica el filtro por defecto de la pantalla: oculta los equipos ya entregados. Esta pantalla
     * muestra el estado de los procesos en curso, y un equipo entregado ya no está en curso.
     *
     * <h2>⚠️ Es un filtro de la CONSULTA. Antes no lo era, y el javadoc decía lo contrario</h2>
     * Hasta la paginación, esto era un default de la <em>vista</em>: los datos llegaban completos y
     * el combo filtraba en memoria. El javadoc de entonces advertía que meterlo en la consulta
     * dejaría "esa opción del combo vacía para siempre". <b>Ese argumento ya no aplica, por dos
     * razones verificables en este archivo:</b>
     *
     * <ul>
     *   <li>el combo <b>no se puebla de los datos</b>, se puebla de {@code EstadoEquipo.values()}
     *       (ver {@link #crearPanelFiltros()}), así que ninguna opción puede quedar vacía por lo que
     *       traiga la consulta;</li>
     *   <li>destildar ENTREGADO —o "Limpiar filtros"— <b>dispara una consulta nueva</b>, que los
     *       trae. El riesgo que el javadoc viejo describía era el de un combo poblado del snapshot;
     *       no es éste.</li>
     * </ul>
     *
     * <p><b>Y como filtro de vista ahora sería un bug:</b> aplicado sobre una página de 50 mostraría
     * 8 filas y el operador creería que hay 8. Filtrar en memoria lo que la base ya paginó da 8 de
     * 50, no los 8 primeros de los que matchean.
     *
     * <p>No dispara el callback de filtros: quien navega a esta pantalla pide la página justo
     * después (ver {@code EstadoProcesosController}), y repintar acá mostraría un instante la
     * página de la visita anterior.
     */
    public void aplicarFiltroInicial() {
        cmbFiltroEstado.setSelectedItems(estadosVisiblesPorDefecto());
    }

    private static List<String> estadosVisiblesPorDefecto() {
        return Arrays.stream(EstadoEquipo.values())
            .filter(estado -> estado != EstadoEquipo.ENTREGADO)
            .map(EstadoEquipo::getNombre)
            .toList();
    }

    private void notificarCambioFiltros() {
        if (onFiltrosChanged != null) onFiltrosChanged.run();
    }

    public void setOnFiltrosChanged(Runnable listener) { this.onFiltrosChanged = listener; }
    public String       getFiltroCliente()    { return txtFiltroCliente.getText().trim(); }
    public String       getFiltroInstitucion(){ return txtFiltroInstitucion.getText().trim(); }
    public List<String> getFiltroEstados()    { return cmbFiltroEstado.getSelectedItems(); }
}