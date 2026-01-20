package com.example.view;

import javax.swing.*;
import java.awt.*;

public class PanelEsterilizacion extends JPanel {
    // Pasamos el navegador y el contenedor para poder cambiar de pantalla desde aquí
    public PanelEsterilizacion(CardLayout navegador, JPanel contenedor) {
        setLayout(new BorderLayout());

        JPanel panelNorte = new JPanel();
        panelNorte.setLayout(new BoxLayout(panelNorte, BoxLayout.Y_AXIS));;

        JPanel panelTitulo = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        JLabel titulo = new JLabel("CENTRO DE ESTERILIZACIÓN");
        titulo.setFont(new Font("Arial", Font.BOLD, 26));
        panelTitulo.add(titulo);

        JButton btnVolver = new JButton("<- Volver");
        JPanel panelBotonVolver = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        panelBotonVolver.add(btnVolver);
        
        panelNorte.add(panelBotonVolver);
        panelNorte.add(panelTitulo);

        add(panelNorte, BorderLayout.NORTH);

        JPanel botones = new JPanel(new GridLayout(3, 1, 10, 10));
        botones.setBorder(BorderFactory.createEmptyBorder(20, 50, 50, 50));

        JButton btnVer = new JButton("Ver");
        btnVer.setFont(new Font("Arial", Font.PLAIN, 24));

        JButton btnRegistrar = new JButton("Registrar");
        btnRegistrar.setFont(new Font("Arial", Font.PLAIN, 24));

        JButton btnIngresar = new JButton("Ingresar");
        btnIngresar.setFont(new Font("Arial", Font.PLAIN, 24));

        btnIngresar.addActionListener(e -> navegador.show(contenedor, "ES_ORTOPEDIA"));

        botones.add(btnIngresar);
        botones.add(btnRegistrar);
        botones.add(btnVer);
        add(botones, BorderLayout.CENTER);

        btnVolver.addActionListener(e -> navegador.show(contenedor, "MENU_PRINCIPAL"));
    }
}