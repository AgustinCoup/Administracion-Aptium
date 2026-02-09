package com.example.view;

import javax.swing.*;
import com.example.constants.Constantes;
import com.example.model.Equipo;
import com.example.view.helpers.PanelEquipoMaterial;
import com.example.view.helpers.PanelHeader;
import java.awt.*;
import java.util.List;

/**
 * Pantalla para visualizar el estado de equipos y materiales en tiempo real.
 * Refactorizada para usar PanelEquipoMaterial y evitar duplicación de código.
 */
public class PantallaVerCDEv2 extends JPanel {
    
    private PanelEquipoMaterial panelTablas;

    public PantallaVerCDEv2(CardLayout navegador, JPanel contenedor) {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Header reutilizable
        PanelHeader header = new PanelHeader(
            Constantes.Titulos.ESTADO_PROCESOS, 
            navegador, 
            contenedor, 
            Constantes.Pantallas.ESTERILIZACION
        );
        add(header, BorderLayout.NORTH);

        // Panel central reutilizable (solo visualización, no editable)
        panelTablas = new PanelEquipoMaterial(
            Constantes.Textos.TABLA_EQUIPOS_TITULO,
            Constantes.Textos.TABLA_MATERIALES_SELECCIONADO_TITULO,
            false  // No editable
        );
        
        add(panelTablas, BorderLayout.CENTER);
    }

    /**
     * Actualiza la tabla con nuevos datos de equipos.
     */
    public void actualizarTabla(List<Equipo> equipos) {
        panelTablas.actualizarEquipos(equipos);
    }
}
