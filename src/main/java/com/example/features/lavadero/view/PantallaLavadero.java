package com.example.features.lavadero.view;

import com.example.common.constants.Constantes;
import com.example.ui.common.Estilos;
import com.example.ui.common.PanelHeader;

import javax.swing.*;
import java.awt.*;

public class PantallaLavadero extends JPanel {

    private JButton btnClasificar;
    private JButton btnCiclos;
    private JButton btnSalidas;
    private JButton btnVerCiclos;

    public PantallaLavadero(CardLayout navegador, JPanel contenedor) {
        setLayout(new BorderLayout());

        PanelHeader header = new PanelHeader(
            Constantes.Titulos.LAVADERO,
            navegador,
            contenedor,
            Constantes.Pantallas.MENU_PRINCIPAL
        );
        add(header, BorderLayout.NORTH);

        // Menú en columna: con 5 opciones una grilla deja una fila incompleta,
        // así que todos los botones van uno debajo del otro, del mismo tamaño.
        JPanel columna = new JPanel(new GridLayout(0, 1, 0, Estilos.Espaciados.SEPARACION_MENU));

        JButton btnIngresar = crearBoton(Constantes.Botones.INGRESAR);
        btnIngresar.addActionListener(
            e -> navegador.show(contenedor, Constantes.Pantallas.INGRESO_LAVADERO));

        btnClasificar = crearBoton(Constantes.Botones.CLASIFICAR);
        btnCiclos     = crearBoton(Constantes.Botones.CICLOS);
        btnSalidas    = crearBoton(Constantes.Botones.SALIDAS);
        btnVerCiclos  = crearBoton(Constantes.Botones.VER_CICLOS);

        columna.add(btnIngresar);
        columna.add(btnClasificar);
        columna.add(btnCiclos);
        columna.add(btnSalidas);
        columna.add(btnVerCiclos);

        // Ancho fijo para que los botones no se estiren de borde a borde;
        // a lo alto ocupan todo el panel, como los botones de los otros menús.
        columna.setPreferredSize(
            new Dimension(Estilos.Dimensiones.ANCHO_MENU, columna.getPreferredSize().height));

        GridBagConstraints restricciones = new GridBagConstraints();
        restricciones.fill = GridBagConstraints.VERTICAL;
        restricciones.weighty = 1;

        JPanel panelBotones = new JPanel(new GridBagLayout());
        panelBotones.setBorder(Estilos.Espaciados.BORDE_PRINCIPAL);
        panelBotones.add(columna, restricciones);
        add(panelBotones, BorderLayout.CENTER);
    }

    private static JButton crearBoton(String texto) {
        JButton boton = new JButton(texto);
        boton.setFont(Estilos.Fuentes.BOTON);
        return boton;
    }

    public JButton getBtnClasificar() { return btnClasificar; }
    public JButton getBtnCiclos()     { return btnCiclos; }
    public JButton getBtnSalidas()    { return btnSalidas; }
    public JButton getBtnVerCiclos()  { return btnVerCiclos; }
}
