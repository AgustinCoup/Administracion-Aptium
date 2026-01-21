package com.example.view;

import javax.swing.*;
import java.awt.*;

public class PanelVerCDEv1 extends JPanel {

    public PanelVerCDEv1(CardLayout navegador, JPanel contenedor) {
        setLayout(new BorderLayout());

        JPanel panelNorte = new JPanel();
        panelNorte.setLayout(new BoxLayout(panelNorte, BoxLayout.Y_AXIS));

        JPanel panelTitulo = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        JLabel titulo = new JLabel("ESTADO DE PROCESOS EN TIEMPO REAL");
        titulo.setFont(new Font("Arial", Font.BOLD, 26));
        panelTitulo.add(titulo);

        JButton btnVolver = new JButton("<- Volver");
        JPanel panelBotonVolver = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        panelBotonVolver.add(btnVolver);

        panelNorte.add(panelBotonVolver);
        panelNorte.add(panelTitulo);

        add(panelNorte, BorderLayout.NORTH);

        btnVolver.addActionListener(e -> navegador.show(contenedor, "ESTERILIZACION"));

        // 2. Contenedor de Columnas (6 columnas para tus 6 estados)
        JPanel panelColumnas = new JPanel(new GridLayout(6, 1, 10, 0));
        panelColumnas.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Añadimos cada columna
        panelColumnas.add(crearColumna("Nuevo", Color.LIGHT_GRAY));
        panelColumnas.add(crearColumna("Lavando", Color.CYAN));
        panelColumnas.add(crearColumna("Lavado", new Color(135, 206, 250)));
        panelColumnas.add(crearColumna("Empaquetado", Color.ORANGE));
        panelColumnas.add(crearColumna("Esterilizando", Color.PINK));
        panelColumnas.add(crearColumna("Esterilizado", Color.GREEN));

        // Usamos un JScrollPane por si hay muchos elementos y hay que hacer scroll horizontal
        JScrollPane scroll = new JScrollPane(panelColumnas);
        add(scroll, BorderLayout.CENTER);
    }

    private JPanel crearColumna(String nombreEstado, Color colorFondo) {
        JPanel col = new JPanel(new BorderLayout());
        col.setBorder(BorderFactory.createLineBorder(Color.GRAY));

        // Encabezado de la columna
        JLabel lblNombre = new JLabel(nombreEstado.toUpperCase(), SwingConstants.CENTER);
        lblNombre.setOpaque(true);
        lblNombre.setBackground(colorFondo);
        lblNombre.setFont(new Font("Arial", Font.BOLD, 12));
        col.add(lblNombre, BorderLayout.NORTH);

        // Aquí es donde iría la lista de elementos (por ahora un área de texto vacía)
        DefaultListModel<String> modeloLista = new DefaultListModel<>();
        // Ejemplo de cómo se vería con datos:
        if(nombreEstado.equals("Nuevo")) modeloLista.addElement("Caja de Instrumental #102");
        if(nombreEstado.equals("Lavando")) modeloLista.addElement("Set de Cirugía Menor #05");

        JList<String> lista = new JList<>(modeloLista);
        col.add(new JScrollPane(lista), BorderLayout.CENTER);

        return col;
    }
}