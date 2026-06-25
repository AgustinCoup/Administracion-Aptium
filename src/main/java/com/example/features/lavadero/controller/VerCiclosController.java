package com.example.features.lavadero.controller;

import com.example.app.AppModel;
import com.example.features.lavadero.model.CicloLavadero;
import com.example.features.lavadero.view.PantallaVerCiclos;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

public class VerCiclosController {

    private final PantallaVerCiclos pantalla;
    private final AppModel          model;

    private List<CicloLavadero> cache = List.of();

    public VerCiclosController(PantallaVerCiclos pantalla, AppModel model) {
        this.pantalla = pantalla;
        this.model    = model;

        pantalla.setOnFiltrosChanged(this::aplicarFiltros);
        pantalla.setOnLimpiar(pantalla::limpiarFiltros);

        cargarDatos();

        pantalla.addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent e) { cargarDatos(); }
        });
    }

    public void cargarDatos() {
        cache = model.obtenerCiclosFinalizados();
        aplicarFiltros();
    }

    private void aplicarFiltros() {
        Integer   numero = pantalla.getFiltroNumero();
        LocalDate desde  = pantalla.getFiltroFechaDesde();
        LocalDate hasta  = pantalla.getFiltroFechaHasta();
        pantalla.actualizarCiclos(filtrar(cache, numero, desde, hasta));
    }

    // Paquete-privado para test unitario directo sin Swing.
    static List<CicloLavadero> filtrar(List<CicloLavadero> todos,
                                        Integer numeroLavarropas,
                                        LocalDate desde,
                                        LocalDate hasta) {
        return todos.stream()
            .filter(c -> numeroLavarropas == null
                || c.getLavarropasNumero() == numeroLavarropas)
            .filter(c -> desde == null
                || !c.getFechaFin().toLocalDate().isBefore(desde))
            .filter(c -> hasta == null
                || !c.getFechaFin().toLocalDate().isAfter(hasta))
            .collect(Collectors.toList());
    }
}
