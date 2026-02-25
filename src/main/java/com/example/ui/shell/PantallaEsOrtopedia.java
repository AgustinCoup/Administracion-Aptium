package com.example.ui.shell;

import javax.swing.*;
import java.awt.*;

import com.example.common.constants.Constantes;
import com.example.ui.common.Estilos;
import com.example.ui.common.PanelHeader;

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


        JButton btnEsOrtopedia = new JButton(Constantes.Botones.ORTOPEDIA);
        btnEsOrtopedia.setFont(Estilos.Fuentes.BOTON);
        btnEsOrtopedia.addActionListener(e -> navegador.show(contenedor, Constantes.Pantallas.INGRESO_ORTOPEDIA));

        JButton btnNoEsOrtopedia = new JButton(Constantes.Botones.OTROS);
        btnNoEsOrtopedia.setFont(Estilos.Fuentes.BOTON);

        botones.add(btnEsOrtopedia);
        botones.add(btnNoEsOrtopedia);
        add(botones, BorderLayout.CENTER);
    }
}


