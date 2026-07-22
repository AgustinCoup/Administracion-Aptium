package com.example.features.lotes.view;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.example.common.constants.Constantes;
import com.example.features.lotes.view.helpers.AutoclaveItem;
import com.example.features.lotes.view.helpers.MaterialLoteItem;
import com.example.ui.common.PanelHeader;
import com.example.features.lotes.view.helpers.PanelLotesContenido;

/**
 * Pantalla standalone para gestión de lotes.
 * 
 * Ahora es un simple wrapper que compone:
 * - PanelHeader (navegación hacia Esterilización)
 * - PanelLotesContenido (toda la lógica de tablas, autoclaves, botones)
 * 
 * Delega toda la funcionabilidad a PanelLotesContenido para máxima reutilización.
 * La misma lógica se empotra en PantallaRegistrarEstado sin duplicación.
 */
public class PantallaLotes extends JPanel {
    
    private PanelHeader header;
    private PanelLotesContenido panelContenido;

    public PantallaLotes(CardLayout navegador, JPanel contenedor) {
        setLayout(new BorderLayout());

        // Header con navegación
        header = new PanelHeader(
            Constantes.Titulos.LOTES,
            navegador,
            contenedor,
            Constantes.Pantallas.ESTERILIZACION
        );
        add(header, BorderLayout.NORTH);

        // Panel contenido reutilizable (sin header)
        panelContenido = new PanelLotesContenido();
        add(panelContenido, BorderLayout.CENTER);
    }

    // ========== DELEGACIÓN A PanelLotesContenido ==========

    public void setAutoclaves(List<AutoclaveItem> autoclaves) {
        panelContenido.setAutoclaves(autoclaves);
    }

    public void seleccionarAutoclave(String nombre) {
        panelContenido.seleccionarAutoclave(nombre);
    }

    public AutoclaveItem getAutoclaveSeleccionado() {
        return panelContenido.getAutoclaveSeleccionado();
    }

    public void setMaterialesDisponibles(List<MaterialLoteItem> materiales) {
        panelContenido.setMaterialesDisponibles(materiales);
    }

    public void setMaterialesAutoclave(List<MaterialLoteItem> materiales) {
        panelContenido.setMaterialesAutoclave(materiales);
    }

    public void setOnAutoclaveSeleccionado(Consumer<AutoclaveItem> listener) {
        panelContenido.setOnAutoclaveSeleccionado(listener);
    }

    public void setOnLanzar(java.awt.event.ActionListener listener) {
        panelContenido.setOnLanzar(listener);
    }

    public void setOnFinalizar(java.awt.event.ActionListener listener) {
        panelContenido.setOnFinalizar(listener);
    }

    public void setOnMarcarFallo(java.awt.event.ActionListener listener) {
        panelContenido.setOnMarcarFallo(listener);
    }

    public void setOnQuitar(java.awt.event.ActionListener listener) {
        panelContenido.setOnQuitar(listener);
    }

    public void setLanzarEnabled(boolean enabled) {
        panelContenido.setLanzarEnabled(enabled);
    }

    public void setFinalizarEnabled(boolean enabled) {
        panelContenido.setFinalizarEnabled(enabled);
    }

    public void setMarcarFalloEnabled(boolean enabled) {
        panelContenido.setMarcarFalloEnabled(enabled);
    }

    public void setQuitarEnabled(boolean enabled) {
        panelContenido.setQuitarEnabled(enabled);
    }

    public void setCapacidadTexto(String texto) {
        panelContenido.setCapacidadTexto(texto);
    }

    public void setCapacidad(int capacidadUsada, int capacidadTotal) {
        panelContenido.setCapacidad(capacidadUsada, capacidadTotal);
    }

    public void mostrarAdvertencia(String mensaje) {
        panelContenido.mostrarAdvertencia(mensaje);
    }

    public void mostrarInfo(String mensaje) {
        panelContenido.mostrarInfo(mensaje);
    }

    public void mostrarError(String mensaje) {
        panelContenido.mostrarError(mensaje);
    }

    public boolean confirmar(String mensaje, String titulo) {
        return panelContenido.confirmar(mensaje, titulo);
    }

    public JTable getTablaDisponibles() {
        return panelContenido.getTablaDisponibles();
    }

    public JTable getTablaAutoclave() {
        return panelContenido.getTablaAutoclave();
    }

    /**
     * Configura un guard en el botón Volver.
     * Mientras {@code hayPendientes} retorne true, el usuario verá un diálogo
     * de confirmación antes de que la navegación se ejecute.
     *
     * Debe llamarse desde el controller una vez que éste está inicializado.
     *
     * @param hayPendientes  Supplier que retorna true cuando hay materiales cargados sin lanzar
     * @param mensajeBloqueo Texto del diálogo que verá el usuario
     */
    public void setGuardVolver(Supplier<Boolean> hayPendientes, String mensajeBloqueo) {
        header.setGuardNavegacion(hayPendientes, mensajeBloqueo);
    }

    public void setGuardVolver(Supplier<Boolean> hayPendientes, String mensajeBloqueo,
                               Runnable onDescartarConfirmado) {
        header.setGuardNavegacion(hayPendientes, mensajeBloqueo, onDescartarConfirmado);
    }

    // Acceso directo al panel para operaciones avanzadas si es necesario
    public PanelLotesContenido getPanelContenido() {
        return panelContenido;
    }
}