package com.example.features.ajustes.view;

import com.example.features.lavadero.model.InsumoCatalogo;
import com.example.features.lavadero.model.JabonCatalogo;
import com.example.features.lavadero.model.TipoLavado;

import javax.swing.*;
import java.awt.*;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Pestaña de Ajustes con los catálogos de jabones e insumos, lado a lado, y el jabón por
 * defecto de cada tipo de lavado debajo. Ese default es lo que
 * {@code JabonPorTipoLavadoService}/la card de Ciclos usan para la carga automática (Paso 7,
 * ya cerrado): acá sólo se lee y se escribe.
 *
 * <p>Los combos de default ofrecen los jabones <b>activos</b> más "(sin default)", que borra la
 * fila de {@code jabon_por_tipo_lavado} para ese tipo. Se distinguen por {@code getId()}, nunca
 * por referencia (dos lecturas del catálogo dan objetos distintos para el mismo jabón — ver el
 * javadoc de {@code JabonCatalogo}).</p>
 */
public class PanelJabonesEInsumos extends JPanel {

    /** Sentinel de "sin default"; id no positivo para no confundirse con un jabón real. */
    private static final JabonCatalogo SIN_DEFAULT = new JabonCatalogo(-1, "(sin default)");

    private final PanelCatalogoSimple<JabonCatalogo> panelJabones =
        new PanelCatalogoSimple<>(JabonCatalogo::getNombre, JabonCatalogo::isActivo, true);
    private final PanelCatalogoSimple<InsumoCatalogo> panelInsumos =
        new PanelCatalogoSimple<>(InsumoCatalogo::nombre, InsumoCatalogo::activo, true);

    private final Map<TipoLavado, JComboBox<JabonCatalogo>> combosDefault = new EnumMap<>(TipoLavado.class);
    private final JButton btnGuardarDefaults = new JButton("Guardar");

    private Runnable onGuardarDefaults;

    public PanelJabonesEInsumos() {
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel panelCatalogos = new JPanel(new GridLayout(1, 2, 8, 0));
        panelCatalogos.add(envolverConTitulo(panelJabones, "Jabones"));
        panelCatalogos.add(envolverConTitulo(panelInsumos, "Insumos"));

        add(panelCatalogos,       BorderLayout.CENTER);
        add(crearPanelDefaults(), BorderLayout.SOUTH);
    }

    private JPanel envolverConTitulo(JComponent panel, String titulo) {
        JPanel contenedor = new JPanel(new BorderLayout());
        contenedor.setBorder(BorderFactory.createTitledBorder(titulo));
        contenedor.add(panel, BorderLayout.CENTER);
        return contenedor;
    }

    private JPanel crearPanelDefaults() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        panel.setBorder(BorderFactory.createTitledBorder("Jabón por defecto de cada tipo de lavado"));

        for (TipoLavado tipo : TipoLavado.values()) {
            JComboBox<JabonCatalogo> combo = new JComboBox<>();
            combosDefault.put(tipo, combo);
            panel.add(new JLabel(tipo.getNombre() + ":"));
            panel.add(combo);
        }

        btnGuardarDefaults.addActionListener(e -> { if (onGuardarDefaults != null) onGuardarDefaults.run(); });
        panel.add(btnGuardarDefaults);
        return panel;
    }

    public PanelCatalogoSimple<JabonCatalogo>  getPanelJabones() { return panelJabones; }
    public PanelCatalogoSimple<InsumoCatalogo> getPanelInsumos() { return panelInsumos; }

    /**
     * Puebla los combos de default con los jabones activos más "(sin default)", y preselecciona
     * el que ya está guardado para cada tipo.
     */
    public void setJabonesActivos(List<JabonCatalogo> activos, Map<TipoLavado, JabonCatalogo> defaults) {
        for (TipoLavado tipo : TipoLavado.values()) {
            JComboBox<JabonCatalogo> combo = combosDefault.get(tipo);
            combo.removeAllItems();
            combo.addItem(SIN_DEFAULT);
            for (JabonCatalogo jabon : activos) combo.addItem(jabon);

            JabonCatalogo actual = defaults.get(tipo);
            if (actual == null) {
                combo.setSelectedItem(SIN_DEFAULT);
            } else {
                seleccionarPorId(combo, actual.getId());
            }
        }
    }

    /**
     * Si el default guardado ya no está activo (se dio de baja) no aparece en el combo, y queda
     * "(sin default)" seleccionado — es el estado correcto para mostrarle al operador.
     */
    private void seleccionarPorId(JComboBox<JabonCatalogo> combo, int id) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (combo.getItemAt(i).getId() == id) {
                combo.setSelectedIndex(i);
                return;
            }
        }
        combo.setSelectedItem(SIN_DEFAULT);
    }

    /** @return el jabón elegido para ese tipo, o {@code null} si quedó en "(sin default)". */
    public JabonCatalogo getDefaultSeleccionado(TipoLavado tipo) {
        JabonCatalogo elegido = (JabonCatalogo) combosDefault.get(tipo).getSelectedItem();
        return (elegido == null || elegido.getId() <= 0) ? null : elegido;
    }

    public void setOnGuardarDefaults(Runnable r) { onGuardarDefaults = r; }
}
