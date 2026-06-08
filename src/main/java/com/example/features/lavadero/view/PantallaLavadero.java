package com.example.features.lavadero.view;

import com.example.common.constants.Constantes;
import com.example.ui.common.Estilos;
import com.example.ui.common.PanelHeader;

import javax.swing.*;
import java.awt.*;

public class PantallaLavadero extends JPanel {

    public PantallaLavadero(CardLayout navegador, JPanel contenedor) {
        setLayout(new BorderLayout());

        PanelHeader header = new PanelHeader(
            Constantes.Titulos.LAVADERO,
            navegador,
            contenedor,
            Constantes.Pantallas.MENU_PRINCIPAL
        );
        add(header, BorderLayout.NORTH);

        JPanel panelBotones = new JPanel(new GridLayout(1, 1, 0, 0));
        panelBotones.setBorder(Estilos.Espaciados.BORDE_PRINCIPAL);

        JButton btnIngresar = new JButton(Constantes.Botones.INGRESAR);
        btnIngresar.setFont(Estilos.Fuentes.BOTON);
        btnIngresar.addActionListener(
            e -> navegador.show(contenedor, Constantes.Pantallas.INGRESO_LAVADERO));

        panelBotones.add(btnIngresar);
        add(panelBotones, BorderLayout.CENTER);
    }
}
