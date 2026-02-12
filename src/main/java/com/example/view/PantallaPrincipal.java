package com.example.view;

import javax.swing.*;
import java.awt.*;
import com.example.constants.Constantes;

public class PantallaPrincipal extends JFrame {
    private CardLayout navegador = new CardLayout();
    private JPanel contenedor = new JPanel(navegador);
    private PantallaIngresoOrtopedia ingresoOrtopedia;
    private PantallaVerCDEv2 verCDEv2;
    private PantallaRegistrarEstado registrarEstado;
    private PantallaEquiposParaEntregar equiposParaEntregar;
    private PantallaLotes pantallaLotes;

    public PantallaPrincipal() {
        setTitle(Constantes.Titulos.APP);
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
        registrarEstado = new PantallaRegistrarEstado(navegador, contenedor);
        equiposParaEntregar = new PantallaEquiposParaEntregar(navegador, contenedor);
        pantallaLotes = new PantallaLotes(navegador, contenedor);

        // Los registramos en el mazo de cartas con un nombre único
        contenedor.add(menu, Constantes.Pantallas.MENU_PRINCIPAL);
        contenedor.add(esterilizacion, Constantes.Pantallas.ESTERILIZACION);
        contenedor.add(esOrtopedia, Constantes.Pantallas.ES_ORTOPEDIA);
        contenedor.add(verCDE, Constantes.Pantallas.VER_CDE);
        contenedor.add(verCDEv2, Constantes.Pantallas.VER_CDE_V2);
        contenedor.add(ingresoOrtopedia, Constantes.Pantallas.INGRESO_ORTOPEDIA);
        contenedor.add(registrarEstado, Constantes.Pantallas.REGISTRAR_ESTADO);
        contenedor.add(equiposParaEntregar, Constantes.Pantallas.EQUIPOS_PARA_ENTREGAR);
        contenedor.add(pantallaLotes, Constantes.Pantallas.LOTES);

        add(contenedor);
    }
    
    public PantallaIngresoOrtopedia getPanelIngresoOrtopedia() {
        return ingresoOrtopedia;
    }
    
    public PantallaVerCDEv2 getPantallaVerCDEv2() {
        return verCDEv2;
    }

    public PantallaRegistrarEstado getPantallaRegistrarEstado() {
        return registrarEstado;
    }

    public PantallaEquiposParaEntregar getPantallaEquiposParaEntregar() {
        return equiposParaEntregar;
    }

    public PantallaLotes getPantallaLotes() {
        return pantallaLotes;
    }
    
    public CardLayout getNavegador() {
        return navegador;
    }
    
    public JPanel getContenedor() {
        return contenedor;
    }
}