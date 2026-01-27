package com.example.view;

import javax.swing.*;
import java.awt.*;

import com.example.constants.Constantes;

public class PantallaEsterilizacion extends JPanel {
    // Pasamos el navegador y el contenedor para poder cambiar de pantalla desde aquí
    public PantallaEsterilizacion(CardLayout navegador, JPanel contenedor) {
        setLayout(new BorderLayout());

        // Header reutilizable con título y botón de navegación
        PanelHeader header = new PanelHeader(
            Constantes.Titulos.CENTRO_ESTERILIZACION, 
            navegador, 
            contenedor, 
            Constantes.Pantallas.MENU_PRINCIPAL
        );
        add(header, BorderLayout.NORTH);

        JPanel botones = new JPanel(new GridLayout(3, 1, 10, 10));
        botones.setBorder(Estilos.Espaciados.BORDE_PRINCIPAL);

        JButton btnVer = new JButton(Constantes.Botones.VER);
        btnVer.setFont(Estilos.Fuentes.BOTON);

        JButton btnRegistrar = new JButton(Constantes.Botones.REGISTRAR);
        btnRegistrar.setFont(Estilos.Fuentes.BOTON);

        JButton btnIngresar = new JButton(Constantes.Botones.INGRESAR);
        btnIngresar.setFont(Estilos.Fuentes.BOTON);

        btnIngresar.addActionListener(e -> navegador.show(contenedor, Constantes.Pantallas.ES_ORTOPEDIA));
        //btnVer.addActionListener(e -> navegador.show(contenedor, Constantes.Pantallas.VER_CDE));
        btnVer.addActionListener(e -> navegador.show(contenedor, Constantes.Pantallas.VER_CDE_V2));
        
        botones.add(btnIngresar);
        botones.add(btnRegistrar);
        botones.add(btnVer);
        add(botones, BorderLayout.CENTER);
    }
}