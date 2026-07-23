package com.example.features.ajustes.view;

import com.example.common.constants.Constantes;
import com.example.ui.common.PanelHeader;

import javax.swing.*;
import java.awt.*;

public class PantallaAjustes extends JPanel {

    private final PanelGestionClientes panelClientes = new PanelGestionClientes();
    private final JButton btnBuscarActualizaciones = new JButton(Constantes.Botones.BUSCAR_ACTUALIZACIONES);

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
    }

    private JPanel crearBarraActualizaciones() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        panel.add(btnBuscarActualizaciones);
        return panel;
    }

    public PanelGestionClientes getPanelClientes() { return panelClientes; }

    public void setOnBuscarActualizaciones(Runnable r) {
        btnBuscarActualizaciones.addActionListener(e -> r.run());
    }
}
