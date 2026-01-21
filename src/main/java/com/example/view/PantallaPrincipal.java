package com.example.view;

import javax.swing.*;
import java.awt.*;

public class PantallaPrincipal extends JFrame {
    private CardLayout navegador = new CardLayout();
    private JPanel contenedor = new JPanel(navegador);

    public PantallaPrincipal() {
        setTitle("Sistema Empresa - v1.0");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Instanciamos los paneles pasando el navegador y el contenedor
        PanelMenu menu = new PanelMenu(navegador, contenedor);
        PanelEsterilizacion esterilizacion = new PanelEsterilizacion(navegador, contenedor);
        PanelEsOrtopedia esOrtopedia = new PanelEsOrtopedia(navegador, contenedor);
        PanelVerCDEv1 verCDE = new PanelVerCDEv1(navegador, contenedor);
        PanelVerCDEv2 verCDEv2 = new PanelVerCDEv2(navegador, contenedor);
        PanelIngresoOrtopedia ingresoOrtopedia = new PanelIngresoOrtopedia(navegador, contenedor);

        // Los registramos en el mazo de cartas con un nombre único
        contenedor.add(menu, "MENU_PRINCIPAL");
        contenedor.add(esterilizacion, "ESTERILIZACION");
        contenedor.add(esOrtopedia, "ES_ORTOPEDIA");
        contenedor.add(verCDE, "VER_CDE");
        contenedor.add(verCDEv2, "VER_CDE_V2");
        contenedor.add(ingresoOrtopedia, "INGRESO_ORTOPEDIA");

        add(contenedor);
    }
}