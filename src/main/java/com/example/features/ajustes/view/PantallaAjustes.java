package com.example.features.ajustes.view;

import com.example.common.constants.Constantes;
import com.example.ui.common.PanelHeader;

import javax.swing.*;
import java.awt.*;

public class PantallaAjustes extends JPanel {

    private final PanelGestionClientes panelClientes = new PanelGestionClientes();
    private final JButton btnBuscarActualizaciones = new JButton(Constantes.Botones.BUSCAR_ACTUALIZACIONES);
    private Runnable onBuscarActualizaciones;

    public PantallaAjustes(CardLayout navegador, JPanel contenedor) {
        setLayout(new BorderLayout());

        PanelHeader header = new PanelHeader(
            Constantes.Titulos.AJUSTES,
            navegador,
            contenedor,
            Constantes.Pantallas.MENU_PRINCIPAL
        );
        add(header, BorderLayout.NORTH);
        add(panelClientes, BorderLayout.CENTER);
        add(crearBarraActualizaciones(), BorderLayout.SOUTH);

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

    public void setOnBuscarActualizaciones(Runnable r) {
        onBuscarActualizaciones = r;
    }

    /** Evita clicks re-entrantes mientras el flujo de chequeo/descarga/instalación está en curso. */
    public void setBuscarActualizacionesHabilitado(boolean habilitado) {
        btnBuscarActualizaciones.setEnabled(habilitado);
    }
}
