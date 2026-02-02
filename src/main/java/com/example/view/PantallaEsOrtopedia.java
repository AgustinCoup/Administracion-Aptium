package com.example.view;

import javax.swing.*;
import java.awt.*;

import com.example.constants.Constantes;
import com.example.view.helpers.Estilos;
import com.example.view.helpers.PanelHeader;

public class PantallaEsOrtopedia extends JPanel {
    // Pasamos el navegador y el contenedor para poder cambiar de pantalla desde aquí
    public PantallaEsOrtopedia(CardLayout navegador, JPanel contenedor) {
        setLayout(new BorderLayout());

        // Header reutilizable con título y botón de navegación
        PanelHeader header = new PanelHeader(
            Constantes.Titulos.INGRESO, 
            navegador, 
            contenedor, 
            Constantes.Pantallas.ESTERILIZACION
        );
        add(header, BorderLayout.NORTH);

        JPanel botones = new JPanel(new GridLayout(1, 2, 10, 10));
        botones.setBorder(Estilos.Espaciados.BORDE_PRINCIPAL);


        JButton btnEsOrtopedia = new JButton("Ortopedia");
        btnEsOrtopedia.setFont(Estilos.Fuentes.BOTON);
        btnEsOrtopedia.addActionListener(e -> navegador.show(contenedor, Constantes.Pantallas.INGRESO_ORTOPEDIA));

        JButton btnNoEsOrtopedia = new JButton("Otros");
        btnNoEsOrtopedia.setFont(Estilos.Fuentes.BOTON);

        botones.add(btnEsOrtopedia);
        botones.add(btnNoEsOrtopedia);
        add(botones, BorderLayout.CENTER);
    }
}