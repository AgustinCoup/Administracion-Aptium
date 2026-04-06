package com.example.features.equipos.controller;

import com.example.app.AppModel;
import com.example.common.exception.DatabaseException;
import com.example.common.exception.ValidationException;
import com.example.common.constants.Constantes;
import com.example.features.equipos.dao.AuditoriaDAO;
import com.example.features.catalogo.dao.CatalogoDAO;
import com.example.features.equipos.dao.EquipoDAO;
import com.example.features.equipos.dao.MaterialDAO;
import com.example.features.equipos.model.Equipo;
import com.example.features.equipos.service.EquipoCorreccionService;
import com.example.features.equipos.view.PantallaAuditoria;
import com.example.features.equipos.view.PantallaCorrecciones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.List;

/**
 * Controlador para PantallaCorrecciones.
 *
 * Responsabilidades:
 * - Cargar equipos en estado NUEVO para edición.
 * - Modificar cantidad de material.
 * - Modificar código de catálogo de material.
 * - Agregar un material nuevo a un equipo.
 * - Eliminar equipo completo.
 * - Eliminar material individual de un equipo.
 *
 * La navegación a la pantalla de auditoría se delega al UiCoordinator mediante
 * {@link #setOnVerAuditoria(Runnable)}, manteniendo este controlador desacoplado
 * del CardLayout concreto.
 */
public class CorreccionsController {

    private static final Logger log = LoggerFactory.getLogger(CorreccionsController.class);

    private final PantallaCorrecciones    panel;
    private final EquipoCorreccionService correccionService;
    private Runnable                      onCambiosAplicados;

    public CorreccionsController(PantallaCorrecciones panel, AppModel model) {
        this.panel = panel;

        this.correccionService = new EquipoCorreccionService(
            new EquipoDAO(),
            new MaterialDAO(),
            new AuditoriaDAO(),
            new CatalogoDAO()
        );

        // ── Cablear callbacks de la vista ─────────────────────────────────────
        panel.setOnModificarCantidad((equipoId, materialId, cantidad, motivo) ->
            modificarCantidadMaterial(equipoId, materialId, cantidad, motivo));

        panel.setOnModificarCodigo((equipoId, materialId, codigo, motivo) ->
            modificarCodigoMaterial(equipoId, materialId, codigo, motivo));

        panel.setOnAgregarMaterial((equipoId, codigoCatalogo, cantidad, motivo) ->
            agregarMaterial(equipoId, codigoCatalogo, cantidad, motivo));

        panel.setOnEliminarEquipo(this::eliminarEquipo);
        panel.setOnEliminarMaterial(this::eliminarMaterial);

        // onVerAuditoria lo registra UiCoordinator con setOnVerAuditoria()
        // porque él conoce el CardLayout y la pantalla destino.

        panel.setOnPantallaVisible(this::cargarEquiposNuevos);

        panel.setOnCodigoNuevoChanged((codigo, campoDescripcion) -> {
            String descripcion = correccionService.obtenerDescripcionMaterial(codigo);
            campoDescripcion.setText(descripcion != null
                ? descripcion : Constantes.Mensajes.AUTOCOMPLETE_DESCONOCIDO);
        });

        cargarEquiposNuevos();
        panel.limpiarPantalla();
    }

    // ── API pública ──────────────────────────────────────────────────────────

    /**
     * Permite que UiCoordinator registre la acción de navegación a la pantalla de auditoría,
     * desacoplando este controlador de la implementación concreta del CardLayout.
     */
    public void setOnVerAuditoria(Runnable callback) {
        panel.setOnVerAuditoria(callback);
    }

    public void setOnCambiosAplicados(Runnable callback) {
        this.onCambiosAplicados = callback;
    }

    /**
     * Inyecta el servicio de corrección en la pantalla de auditoría.
     *
     * <p>UiCoordinator llama a este método una única vez durante el arranque,
     * evitando que tenga que conocer los servicios internos de este controlador.
     *
     * @param pantalla pantalla de auditoría a inicializar
     */
    public void inicializarPantallaAuditoria(PantallaAuditoria pantalla) {
        pantalla.inicializar(correccionService);
    }

    // ── Operaciones ──────────────────────────────────────────────────────────

    private void cargarEquiposNuevos() {
        new Thread(() -> {
            try {
                panel.mostrarCargando(true);
                List<Equipo> equipos = correccionService.obtenerEquiposNuevos();
                SwingUtilities.invokeLater(() -> {
                    panel.actualizarListaEquipos(equipos);
                    panel.limpiarPantalla();
                    panel.mostrarMensaje("Se cargaron " + equipos.size() + " equipos en estado NUEVO");
                    panel.mostrarCargando(false);
                });
                log.info("Se cargaron {} equipos nuevos", equipos.size());
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    panel.mostrarError("Error al cargar equipos: " + e.getMessage());
                    panel.mostrarCargando(false);
                });
                log.error("Error al cargar equipos nuevos", e);
            }
        }).start();
    }

    private void modificarCantidadMaterial(Integer equipoId, Integer materialId,
                                           Integer cantidadNueva, String motivo) {
        new Thread(() -> {
            try {
                panel.mostrarCargando(true);
                boolean ok = correccionService.modificarCantidadMaterial(
                    equipoId, materialId, cantidadNueva, motivo);
                SwingUtilities.invokeLater(() -> {
                    if (ok) {
                        panel.mostrarMensaje("Cantidad modificada correctamente");
                        cargarEquiposNuevos();
                        notificarCambiosAplicados();
                    } else {
                        panel.mostrarError("No se pudo modificar la cantidad");
                    }
                    panel.mostrarCargando(false);
                });
                log.info("Cantidad modificada: equipo={}, material={}, cantidad={}",
                    equipoId, materialId, cantidadNueva);
            } catch (ValidationException e) {
                SwingUtilities.invokeLater(() -> {
                    panel.mostrarError("Error de validación: " + e.getMessage());
                    panel.mostrarCargando(false);
                });
            } catch (DatabaseException e) {
                SwingUtilities.invokeLater(() -> {
                    panel.mostrarError("Error en la base de datos: " + e.getMessage());
                    panel.mostrarCargando(false);
                });
                log.error("Error de base de datos", e);
            }
        }).start();
    }

    private void modificarCodigoMaterial(Integer equipoId, Integer materialId,
                                         Integer codigoNuevo, String motivo) {
        new Thread(() -> {
            try {
                panel.mostrarCargando(true);
                boolean ok = correccionService.modificarCodigoMaterial(
                    equipoId, materialId, codigoNuevo, motivo);
                SwingUtilities.invokeLater(() -> {
                    if (ok) {
                        panel.mostrarMensaje("Código modificado correctamente");
                        cargarEquiposNuevos();
                        notificarCambiosAplicados();
                    } else {
                        panel.mostrarError("No se pudo modificar el código");
                    }
                    panel.mostrarCargando(false);
                });
                log.info("Código modificado: equipo={}, material={}, código={}",
                    equipoId, materialId, codigoNuevo);
            } catch (ValidationException e) {
                SwingUtilities.invokeLater(() -> {
                    panel.mostrarError("Error de validación: " + e.getMessage());
                    panel.mostrarCargando(false);
                });
            } catch (DatabaseException e) {
                SwingUtilities.invokeLater(() -> {
                    panel.mostrarError("Error en la base de datos: " + e.getMessage());
                    panel.mostrarCargando(false);
                });
                log.error("Error de base de datos", e);
            }
        }).start();
    }

    private void agregarMaterial(Integer equipoId, Integer codigoCatalogo,
                                 Integer cantidad, String motivo) {
        new Thread(() -> {
            try {
                panel.mostrarCargando(true);
                boolean ok = correccionService.agregarMaterialAEquipo(
                    equipoId, codigoCatalogo, cantidad, motivo);
                SwingUtilities.invokeLater(() -> {
                    if (ok) {
                        panel.mostrarMensaje("Material agregado correctamente");
                        cargarEquiposNuevos();
                        notificarCambiosAplicados();
                    } else {
                        panel.mostrarError("No se pudo agregar el material");
                    }
                    panel.mostrarCargando(false);
                });
                log.info("Material código={} (cantidad={}) agregado al equipo {}",
                    codigoCatalogo, cantidad, equipoId);
            } catch (ValidationException e) {
                SwingUtilities.invokeLater(() -> {
                    panel.mostrarError("Error de validación: " + e.getMessage());
                    panel.mostrarCargando(false);
                });
            } catch (DatabaseException e) {
                SwingUtilities.invokeLater(() -> {
                    panel.mostrarError("Error en la base de datos: " + e.getMessage());
                    panel.mostrarCargando(false);
                });
                log.error("Error de base de datos al agregar material", e);
            }
        }).start();
    }

    private void eliminarEquipo(Integer equipoId, String motivo) {
        int respuesta = JOptionPane.showConfirmDialog(
            panel,
            "¿Está seguro de que desea eliminar este equipo?\n\nEsta acción no se puede deshacer.",
            "Confirmar eliminación",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );

        if (respuesta != JOptionPane.YES_OPTION) return;

        new Thread(() -> {
            try {
                panel.mostrarCargando(true);
                boolean ok = correccionService.eliminarEquipo(equipoId, motivo);
                SwingUtilities.invokeLater(() -> {
                    if (ok) {
                        panel.mostrarMensaje("Equipo eliminado correctamente");
                        cargarEquiposNuevos();
                        notificarCambiosAplicados();
                    } else {
                        panel.mostrarError("No se pudo eliminar el equipo");
                    }
                    panel.mostrarCargando(false);
                });
                log.info("Equipo {} eliminado", equipoId);
            } catch (ValidationException e) {
                SwingUtilities.invokeLater(() -> {
                    panel.mostrarError("Error de validación: " + e.getMessage());
                    panel.mostrarCargando(false);
                });
            } catch (DatabaseException e) {
                SwingUtilities.invokeLater(() -> {
                    panel.mostrarError("Error en la base de datos: " + e.getMessage());
                    panel.mostrarCargando(false);
                });
                log.error("Error de base de datos", e);
            }
        }).start();
    }

    private void eliminarMaterial(Integer equipoId, Integer codigoCatalogo, String motivo) {
        new Thread(() -> {
            try {
                panel.mostrarCargando(true);
                boolean ok = correccionService.eliminarMaterial(equipoId, codigoCatalogo, motivo);
                SwingUtilities.invokeLater(() -> {
                    if (ok) {
                        panel.mostrarMensaje("Material eliminado correctamente");
                        cargarEquiposNuevos();
                        notificarCambiosAplicados();
                    } else {
                        panel.mostrarError("No se pudo eliminar el material");
                    }
                    panel.mostrarCargando(false);
                });
                log.info("Material con código {} eliminado del equipo {}", codigoCatalogo, equipoId);
            } catch (ValidationException e) {
                SwingUtilities.invokeLater(() -> {
                    panel.mostrarError("Error de validación: " + e.getMessage());
                    panel.mostrarCargando(false);
                });
            } catch (DatabaseException e) {
                SwingUtilities.invokeLater(() -> {
                    panel.mostrarError("Error en la base de datos: " + e.getMessage());
                    panel.mostrarCargando(false);
                });
                log.error("Error de base de datos", e);
            }
        }).start();
    }

    private void notificarCambiosAplicados() {
        if (onCambiosAplicados != null) onCambiosAplicados.run();
    }
}