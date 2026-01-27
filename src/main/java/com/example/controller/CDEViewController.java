package com.example.controller;

import java.util.List;

import com.example.model.AppModel;
import com.example.model.Equipo;
import com.example.view.PantallaVerCDEv2;

/**
 * Controlador para PantallaVerCDEv2.
 * Responsabilidad: Cargar datos del Modelo y actualizar la Vista.
 * Respeta MVC: Vista ← Controller ← Modelo
 */
public class CDEViewController {
    private PantallaVerCDEv2 panel;
    private AppModel model;
    
    public CDEViewController(PantallaVerCDEv2 panel, AppModel model) {
        this.panel = panel;
        this.model = model;
        cargarDatos();
    }
    
    /**
     * Carga los equipos desde el Modelo y actualiza la Vista.
     */
    public void cargarDatos() {
        List<Equipo> equipos = model.obtenerTodosLosEquipos();
        panel.actualizarTabla(equipos);
    }
}
