package com.example.features.ajustes.view;

import com.example.common.constants.Constantes;
import com.example.features.catalogo.model.ItemCatalogo;
import com.example.features.lavadero.model.ElementoCatalogo;
import com.example.ui.common.PanelHeader;

import javax.swing.*;
import java.awt.*;

/**
 * Cuatro pestañas: Clientes (con la barra de actualizaciones, que no pertenece a ninguna, al
 * sur), Catálogos (con un {@link JTabbedPane} anidado Lavadero/Ortopedias/Otros), Lavarropas y
 * Jabones e insumos.
 *
 * <p><b>La carga es por pestaña, no por pantalla.</b> Cada controller se suscribe con
 * {@link #setOnPestanaSeleccionada} (o, para las sub-pestañas de Catálogos,
 * {@link #setOnSubPestanaCatalogoSeleccionada}) y decide cuándo leer. Este panel no dispara
 * ninguna carga por su cuenta — ni siquiera de la pestaña inicial — porque {@link JTabbedPane}
 * no emite un {@code ChangeEvent} por la selección con la que ya nace. Es el controller de cada
 * pestaña el que decide si necesita ese primer disparo (Clientes lo tiene desde antes, por el
 * {@code componentShown} de toda la pantalla; las demás lo piden explícitamente donde hace falta,
 * como {@code CatalogosAjustesController} al entrar a "Catálogos").</p>
 */
public class PantallaAjustes extends JPanel {

    public static final int TAB_CLIENTES          = 0;
    public static final int TAB_CATALOGOS         = 1;
    public static final int TAB_LAVARROPAS        = 2;
    public static final int TAB_JABONES_INSUMOS   = 3;

    public static final int SUBTAB_CATALOGO_LAVADERO    = 0;
    public static final int SUBTAB_CATALOGO_ORTOPEDIAS   = 1;
    public static final int SUBTAB_CATALOGO_OTROS        = 2;

    private final PanelGestionClientes  panelClientes      = new PanelGestionClientes();
    private final PanelCatalogoSimple<ElementoCatalogo> panelCatalogoLavadero = new PanelCatalogoSimple<>(
        ElementoCatalogo::getNombre, ElementoCatalogo::isActivo, true);
    private final PanelCatalogoOrtopedias panelCatalogoOrtopedias = new PanelCatalogoOrtopedias();
    private final PanelCatalogoSimple<ItemCatalogo> panelCatalogoOtros = new PanelCatalogoSimple<>(
        ItemCatalogo::descripcion, ItemCatalogo::vigente, false);
    private final PanelGestionLavarropas panelLavarropas     = new PanelGestionLavarropas();
    private final PanelJabonesEInsumos   panelJabonesInsumos = new PanelJabonesEInsumos();

    private final JTabbedPane tabsCatalogos = new JTabbedPane();
    private final JTabbedPane tabs          = new JTabbedPane();

    private final JButton   btnBuscarActualizaciones = new JButton(Constantes.Botones.BUSCAR_ACTUALIZACIONES);
    private       Runnable  onBuscarActualizaciones;

    public PantallaAjustes(CardLayout navegador, JPanel contenedor) {
        setLayout(new BorderLayout());

        PanelHeader header = new PanelHeader(
            Constantes.Titulos.AJUSTES,
            navegador,
            contenedor,
            Constantes.Pantallas.MENU_PRINCIPAL
        );
        add(header, BorderLayout.NORTH);

        JPanel panelClientesConActualizaciones = new JPanel(new BorderLayout());
        panelClientesConActualizaciones.add(panelClientes, BorderLayout.CENTER);
        panelClientesConActualizaciones.add(crearBarraActualizaciones(), BorderLayout.SOUTH);

        tabsCatalogos.addTab("Lavadero", panelCatalogoLavadero);
        tabsCatalogos.addTab("Ortopedias", panelCatalogoOrtopedias);
        tabsCatalogos.addTab("Otros", panelCatalogoOtros);

        tabs.addTab("Clientes", panelClientesConActualizaciones);
        tabs.addTab("Catálogos", tabsCatalogos);
        tabs.addTab("Lavarropas", panelLavarropas);
        tabs.addTab("Jabones e insumos", panelJabonesInsumos);
        add(tabs, BorderLayout.CENTER);

        // Listener cableado una sola vez (igual que PanelGestionClientes): setOnBuscarActualizaciones
        // solo reasigna el Runnable que se lee al click. Con addActionListener directo ahí, llamar
        // el setter más de una vez apilaba un listener nuevo por llamada y un click terminaba
        // disparando todos los Runnable acumulados, no solo el último.
        btnBuscarActualizaciones.addActionListener(e -> {
            if (onBuscarActualizaciones != null) onBuscarActualizaciones.run();
        });
    }

    private JPanel crearBarraActualizaciones() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        panel.add(btnBuscarActualizaciones);
        return panel;
    }

    public PanelGestionClientes getPanelClientes() { return panelClientes; }

    public PanelCatalogoSimple<ElementoCatalogo> getPanelCatalogoLavadero() {
        return panelCatalogoLavadero;
    }

    public PanelCatalogoOrtopedias getPanelCatalogoOrtopedias() { return panelCatalogoOrtopedias; }

    public PanelCatalogoSimple<ItemCatalogo> getPanelCatalogoOtros() {
        return panelCatalogoOtros;
    }

    public PanelGestionLavarropas getPanelLavarropas() { return panelLavarropas; }

    public PanelJabonesEInsumos getPanelJabonesInsumos() { return panelJabonesInsumos; }

    /** Índice de la sub-pestaña de Catálogos actualmente seleccionada. */
    public int getSubPestanaCatalogoSeleccionada() { return tabsCatalogos.getSelectedIndex(); }

    /**
     * Corre {@code r} cada vez que se selecciona la pestaña de nivel superior {@code indice}
     * (uno de los {@code TAB_*} de esta clase). No corre por la selección con la que el
     * {@link JTabbedPane} ya nace: sólo dispara con un cambio real.
     */
    public void setOnPestanaSeleccionada(int indice, Runnable r) {
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedIndex() == indice) r.run();
        });
    }

    /**
     * Corre {@code r} cada vez que se selecciona la sub-pestaña {@code indice} de Catálogos (uno
     * de los {@code SUBTAB_CATALOGO_*}). Entrar a "Catálogos" sin cambiar de sub-pestaña no dispara
     * esto — ver el javadoc de la clase — por eso {@code CatalogosAjustesController} también se
     * suscribe a {@code TAB_CATALOGOS} para cargar la sub-pestaña ya seleccionada.
     */
    public void setOnSubPestanaCatalogoSeleccionada(int indice, Runnable r) {
        tabsCatalogos.addChangeListener(e -> {
            if (tabsCatalogos.getSelectedIndex() == indice) r.run();
        });
    }

    public void setOnBuscarActualizaciones(Runnable r) {
        onBuscarActualizaciones = r;
    }

    /** Evita clicks re-entrantes mientras el flujo de chequeo/descarga/instalación está en curso. */
    public void setBuscarActualizacionesHabilitado(boolean habilitado) {
        btnBuscarActualizaciones.setEnabled(habilitado);
    }
}
