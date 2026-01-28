package com.example.view;

import javax.swing.*;
import java.awt.*;

public class PantallaPrincipal extends JFrame {
    private CardLayout navegador = new CardLayout();
    private JPanel contenedor = new JPanel(navegador);
    private PantallaIngresoOrtopedia ingresoOrtopedia;
    private PantallaVerCDEv2 verCDEv2;

    public PantallaPrincipal() {
        setTitle("Sistema Empresa - v1.0");
        setSize(1280, 720);
        setMinimumSize(new Dimension(1280, 720));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Instanciamos los paneles pasando el navegador y el contenedor
        PantallaMenu menu = new PantallaMenu(navegador, contenedor);
        PantallaEsterilizacion esterilizacion = new PantallaEsterilizacion(navegador, contenedor);
        PantallaEsOrtopedia esOrtopedia = new PantallaEsOrtopedia(navegador, contenedor);
        PantallaVerCDEv1 verCDE = new PantallaVerCDEv1(navegador, contenedor);
        verCDEv2 = new PantallaVerCDEv2(navegador, contenedor);
        ingresoOrtopedia = new PantallaIngresoOrtopedia(navegador, contenedor);

        // Los registramos en el mazo de cartas con un nombre único
        contenedor.add(menu, "MENU_PRINCIPAL");
        contenedor.add(esterilizacion, "ESTERILIZACION");
        contenedor.add(esOrtopedia, "ES_ORTOPEDIA");
        contenedor.add(verCDE, "VER_CDE");
        contenedor.add(verCDEv2, "VER_CDE_V2");
        contenedor.add(ingresoOrtopedia, "INGRESO_ORTOPEDIA");

        add(contenedor);
    }
    
    public PantallaIngresoOrtopedia getPanelIngresoOrtopedia() {
        return ingresoOrtopedia;
    }
    
    public PantallaVerCDEv2 getPantallaVerCDEv2() {
        return verCDEv2;
    }
    
    public CardLayout getNavegador() {
        return navegador;
    }
    
    public JPanel getContenedor() {
        return contenedor;
    }
}