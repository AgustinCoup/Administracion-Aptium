package com.example.ui.shell;

import javax.swing.*;
import java.awt.*;

import com.example.common.constants.Constantes;
import com.example.ui.common.Estilos;
import com.example.ui.common.PanelHeader;

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

        JPanel botones = new JPanel(new GridLayout(2, 2, 10, 10));
        botones.setBorder(Estilos.Espaciados.BORDE_PRINCIPAL);

        JButton btnVer = new JButton(Constantes.Botones.VER);
        btnVer.setFont(Estilos.Fuentes.BOTON);

        JButton btnRegistrar = new JButton(Constantes.Botones.REGISTRAR);
        btnRegistrar.setFont(Estilos.Fuentes.BOTON);

        JButton btnIngresar = new JButton(Constantes.Botones.INGRESAR);
        btnIngresar.setFont(Estilos.Fuentes.BOTON);

        JButton btnParaEntregar = new JButton(Constantes.Botones.PARA_ENTREGAR);
        btnParaEntregar.setFont(Estilos.Fuentes.BOTON);

        btnIngresar.addActionListener(e -> navegador.show(contenedor, Constantes.Pantallas.ES_ORTOPEDIA));
        btnVer.addActionListener(e -> navegador.show(contenedor, Constantes.Pantallas.VER_EQUIPOS));

        btnRegistrar.addActionListener(e -> navegador.show(contenedor, Constantes.Pantallas.REGISTRAR_ESTADO));
        btnParaEntregar.addActionListener(e -> navegador.show(contenedor, Constantes.Pantallas.EQUIPOS_PARA_ENTREGAR));
        
        botones.add(btnIngresar);
        botones.add(btnRegistrar);
        botones.add(btnParaEntregar);
        botones.add(btnVer);
        add(botones, BorderLayout.CENTER);
    }
}


