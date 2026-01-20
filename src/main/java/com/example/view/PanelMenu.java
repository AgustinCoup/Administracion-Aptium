package com.example.view;

import javax.swing.*;
import java.awt.*;

public class PanelMenu extends JPanel {

    public PanelMenu(CardLayout navegador, JPanel contenedor) {
        setLayout(new BorderLayout()); // Para poner un título arriba y botones al centro

        // Título de bienvenida
        JLabel lblBienvenida = new JLabel("Menú Principal de Gestión", SwingConstants.CENTER);
        lblBienvenida.setFont(new Font("Arial", Font.BOLD, 24));
        lblBienvenida.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));
        add(lblBienvenida, BorderLayout.NORTH);

        // Contenedor para los 4 botones
        JPanel panelBotones = new JPanel(new GridLayout(2, 2, 15, 15));
        panelBotones.setBorder(BorderFactory.createEmptyBorder(20, 50, 50, 50));

        JButton btnEsterilizacion = new JButton("Centro de Esterilización");
        btnEsterilizacion.setFont(new Font("Arial", Font.PLAIN, 24));

        JButton btnLavadero = new JButton("Lavadero");
        btnLavadero.setFont(new Font("Arial", Font.PLAIN, 24));

        JButton btnDesinfectadora = new JButton("Desinfectadora");
        btnDesinfectadora.setFont(new Font("Arial", Font.PLAIN, 24));

        JButton btnDistribuidora = new JButton("Distribuidora");
        btnDistribuidora.setFont(new Font("Arial", Font.PLAIN, 24));

        // Acción para cambiar a la pantalla de Esterilización
        btnEsterilizacion.addActionListener(e -> navegador.show(contenedor, "ESTERILIZACION"));

        panelBotones.add(btnEsterilizacion);
        panelBotones.add(btnLavadero);
        panelBotones.add(btnDesinfectadora);
        panelBotones.add(btnDistribuidora);

        add(panelBotones, BorderLayout.CENTER);
    }
}