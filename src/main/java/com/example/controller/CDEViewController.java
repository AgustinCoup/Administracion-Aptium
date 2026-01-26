package com.example.controller;

import java.util.List;

import com.example.model.AppModel;
import com.example.model.Equipo;
import com.example.model.EquipoDAO;
import com.example.view.PanelVerCDEv2;

public class CDEViewController {
    private PanelVerCDEv2 panel;
    private EquipoDAO equipoDAO;
    
    public CDEViewController(PanelVerCDEv2 panel, AppModel model) {
        this.panel = panel;
        this.equipoDAO = new EquipoDAO();
        cargarDatos();
    }
    
    public void cargarDatos() {
        List<Equipo> equipos = equipoDAO.obtenerTodosLosEquipos();
        panel.actualizarTabla(equipos);
    }
}
