package com.example.view;

import javax.swing.*;
import java.awt.*;

import com.example.constants.Constantes;
import com.example.view.helpers.Estilos;

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

        JButton btnEsterilizacion = new JButton("Centro de Esterilización");
        btnEsterilizacion.setFont(Estilos.Fuentes.BOTON);

        JButton btnLavadero = new JButton("Lavadero");
        btnLavadero.setFont(Estilos.Fuentes.BOTON);

        JButton btnDesinfectadora = new JButton("Desinfectadora");
        btnDesinfectadora.setFont(Estilos.Fuentes.BOTON);

        JButton btnDistribuidora = new JButton("Distribuidora");
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