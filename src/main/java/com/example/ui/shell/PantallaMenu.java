package com.example.ui.shell;

import javax.swing.*;
import java.awt.*;

import com.example.common.constants.Constantes;
import com.example.ui.common.Estilos;

public class PantallaMenu extends JPanel {

    public PantallaMenu(CardLayout navegador, JPanel contenedor) {
        setLayout(new BorderLayout()); // Para poner un título arriba y botones al centro

        // Título de bienvenida
        JLabel lblBienvenida = new JLabel(Constantes.Titulos.MENU_PRINCIPAL, SwingConstants.CENTER);
        lblBienvenida.setFont(Estilos.Fuentes.TITULO_SECUNDARIO);
        lblBienvenida.setBorder(Estilos.Espaciados.BORDE_TITULO);
        add(lblBienvenida, BorderLayout.NORTH);

        // Contenedor para los 4 botones
        JPanel panelBotones = new JPanel(new GridLayout(2, 2, 15, 15));
        panelBotones.setBorder(Estilos.Espaciados.BORDE_PRINCIPAL);

        JButton btnEsterilizacion = new JButton(Constantes.Botones.CENTRO_ESTERILIZACION);
        btnEsterilizacion.setFont(Estilos.Fuentes.BOTON);

        JButton btnLavadero = new JButton(Constantes.Botones.LAVADERO);
        btnLavadero.setFont(Estilos.Fuentes.BOTON);

        JButton btnDesinfectadora = new JButton(Constantes.Botones.DESINFECTADORA);
        btnDesinfectadora.setFont(Estilos.Fuentes.BOTON);

        JButton btnDistribuidora = new JButton(Constantes.Botones.DISTRIBUIDORA);
        btnDistribuidora.setFont(Estilos.Fuentes.BOTON);

        // Acción para cambiar a la pantalla de Esterilización
        btnEsterilizacion.addActionListener(e -> navegador.show(contenedor, Constantes.Pantallas.ESTERILIZACION));

        panelBotones.add(btnEsterilizacion);
        panelBotones.add(btnLavadero);
        panelBotones.add(btnDesinfectadora);
        panelBotones.add(btnDistribuidora);

        add(panelBotones, BorderLayout.CENTER);
    }
}


