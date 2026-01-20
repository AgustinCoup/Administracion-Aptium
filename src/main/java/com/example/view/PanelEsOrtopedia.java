package com.example.view;

import javax.swing.*;
import java.awt.*;

public class PanelEsOrtopedia extends JPanel {
    // Pasamos el navegador y el contenedor para poder cambiar de pantalla desde aquí
    public PanelEsOrtopedia(CardLayout navegador, JPanel contenedor) {
        setLayout(new BorderLayout());

        JPanel panelNorte = new JPanel();
        panelNorte.setLayout(new BoxLayout(panelNorte, BoxLayout.Y_AXIS));
        panelNorte.setPreferredSize(new Dimension(0, 80));

        JPanel panelTitulo = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        JLabel titulo = new JLabel("INGRESO");
        titulo.setFont(new Font("Arial", Font.BOLD, 26));
        panelTitulo.add(titulo);

        JButton btnVolver = new JButton("<- Volver");
        JPanel panelBotonVolver = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        panelBotonVolver.add(btnVolver);

        panelNorte.add(panelBotonVolver);
        panelNorte.add(panelTitulo);

        add(panelNorte, BorderLayout.NORTH);

        JPanel botones = new JPanel(new GridLayout(1, 2, 10, 10));
        botones.setBorder(BorderFactory.createEmptyBorder(20, 50, 50, 50));


        JButton btnEsOrtopedia = new JButton("Ortopedia");
        btnEsOrtopedia.setFont(new Font("Arial", Font.PLAIN, 24));

        JButton btnNoEsOrtopedia = new JButton("Otros");
        btnNoEsOrtopedia.setFont(new Font("Arial", Font.PLAIN, 24));

        botones.add(btnEsOrtopedia);
        botones.add(btnNoEsOrtopedia);
        add(botones, BorderLayout.CENTER);

        btnVolver.addActionListener(e -> navegador.show(contenedor, "ESTERILIZACION"));
    }
}