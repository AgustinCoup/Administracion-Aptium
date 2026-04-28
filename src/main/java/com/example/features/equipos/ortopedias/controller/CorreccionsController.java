package com.example.features.equipos.ortopedias.controller;

import com.example.app.AppModel;
import com.example.common.exception.DatabaseException;
import com.example.common.exception.ValidationException;
import com.example.common.constants.Constantes;
import com.example.common.model.EquipoRegistrableInterface;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.service.EquipoCorreccionService;
import com.example.features.equipos.ortopedias.view.PantallaAuditoria;
import com.example.features.equipos.ortopedias.view.PantallaCorrecciones;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.service.EquipoOtrosCorreccionService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class CorreccionsController {

    private static final Logger log = LoggerFactory.getLogger(CorreccionsController.class);

    private final PantallaCorrecciones        panel;
    private final EquipoCorreccionService     correccionService;
    private final EquipoOtrosCorreccionService otrosService;
    private Runnable                           onCambiosAplicados;

    public CorreccionsController(PantallaCorrecciones panel, AppModel model) {
        this.panel            = panel;
        this.correccionService = model.getEquipoCorreccionService();
        this.otrosService      = model.getEquipoOtrosCorreccionService();

        // ── Callbacks ortopedia ───────────────────────────────────────────────
        panel.setOnModificarCantidad((equipoId, materialId, cantidad, motivo) ->
            modificarCantidadMaterial(equipoId, materialId, cantidad, motivo));

        panel.setOnModificarCodigo((equipoId, materialId, codigo, motivo) ->
            modificarCodigoMaterial(equipoId, materialId, codigo, motivo));

        panel.setOnAgregarMaterial((equipoId, codigoCatalogo, cantidad, motivo) ->
            agregarMaterial(equipoId, codigoCatalogo, cantidad, motivo));

        panel.setOnEliminarEquipo(this::eliminarEquipo);
        panel.setOnEliminarMaterial(this::eliminarMaterial);

        // ── Callbacks otros ───────────────────────────────────────────────────
        panel.setOnModificarCantidadRemito((equipoId, cantidad, motivo) ->
            modificarCantidadRemito(equipoId, cantidad, motivo));

        panel.setOnModificarCantidadMaterialOtros((equipoId, materialId, cantidad, motivo) ->
            modificarCantidadMaterialOtros(equipoId, materialId, cantidad, motivo));

        panel.setOnAgregarMaterialOtros((equipoId, descripcion, cantidad, motivo) ->
            agregarMaterialOtros(equipoId, descripcion, cantidad, motivo));

        panel.setOnEliminarMaterialOtros((equipoId, descripcion, motivo) ->
            eliminarMaterialOtros(equipoId, descripcion, motivo));

        panel.setOnEliminarEquipoOtros(this::eliminarEquipoOtros);

        panel.setBuscarMaterialesOtros(texto -> model.buscarMaterialesOtrosPorDescripcion(texto));
        panel.setVerificarMaterialOtros(desc  -> model.existeMaterialOtros(desc));

        // ── General ───────────────────────────────────────────────────────────
        panel.setOnPantallaVisible(this::cargarEquiposNuevos);

        panel.setOnCodigoNuevoChanged((codigo, campoDescripcion) -> {
            new Thread(() -> {
                String descripcion = correccionService.obtenerDescripcionMaterial(codigo);
                SwingUtilities.invokeLater(() -> campoDescripcion.setText(descripcion != null
                    ? descripcion : Constantes.Mensajes.AUTOCOMPLETE_DESCONOCIDO));
            }).start();
        });

        cargarEquiposNuevos();
        panel.limpiarPantalla();
    }

    // ── API pública ──────────────────────────────────────────────────────────

    public void setOnVerAuditoria(Runnable callback) {
        panel.setOnVerAuditoria(callback);
    }

    public void setOnCambiosAplicados(Runnable callback) {
        this.onCambiosAplicados = callback;
    }

    public void inicializarPantallaAuditoria(PantallaAuditoria pantalla) {
        pantalla.inicializar(correccionService);
    }

    // ── Carga ────────────────────────────────────────────────────────────────

    private void cargarEquiposNuevos() {
        panel.mostrarCargando(true);
        new Thread(() -> {
            try {
                List<Equipo>      ortopedias = correccionService.obtenerEquiposNuevos();
                List<EquipoOtros> otros      = otrosService.obtenerEquiposOtrosNuevos();

                List<EquipoRegistrableInterface> combinada = new ArrayList<>(ortopedias);
                combinada.addAll(otros);

                SwingUtilities.invokeLater(() -> {
                    panel.actualizarListaEquiposUnificada(combinada);
                    panel.limpiarPantalla();
                    panel.mostrarMensaje("Se cargaron " + combinada.size() + " equipos en estado NUEVO");
                    panel.mostrarCargando(false);
                });
                log.info("Se cargaron {} equipos nuevos ({} ortopedia, {} otros)",
                    combinada.size(), ortopedias.size(), otros.size());
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    panel.mostrarError("Error al cargar equipos: " + e.getMessage());
                    panel.mostrarCargando(false);
                });
                log.error("Error al cargar equipos nuevos", e);
            }
        }).start();
    }

    // ── Operaciones ortopedia ────────────────────────────────────────────────

    private void modificarCantidadMaterial(Integer equipoId, Integer materialId,
                                           Integer cantidadNueva, String motivo) {
        panel.mostrarCargando(true);
        new Thread(() -> {
            try {
                boolean ok = correccionService.modificarCantidadMaterial(
                    equipoId, materialId, cantidadNueva, motivo);
                SwingUtilities.invokeLater(() -> {
                    if (ok) { panel.mostrarMensaje("Cantidad modificada correctamente"); cargarEquiposNuevos(); notificarCambiosAplicados(); }
                    else      panel.mostrarError("No se pudo modificar la cantidad");
                    panel.mostrarCargando(false);
                });
            } catch (ValidationException e) {
                SwingUtilities.invokeLater(() -> { panel.mostrarError("Error de validación: " + e.getMessage()); panel.mostrarCargando(false); });
            } catch (DatabaseException e) {
                SwingUtilities.invokeLater(() -> { panel.mostrarError("Error en la base de datos: " + e.getMessage()); panel.mostrarCargando(false); });
                log.error("Error de base de datos", e);
            }
        }).start();
    }

    private void modificarCodigoMaterial(Integer equipoId, Integer materialId,
                                         Integer codigoNuevo, String motivo) {
        panel.mostrarCargando(true);
        new Thread(() -> {
            try {
                boolean ok = correccionService.modificarCodigoMaterial(
                    equipoId, materialId, codigoNuevo, motivo);
                SwingUtilities.invokeLater(() -> {
                    if (ok) { panel.mostrarMensaje("Código modificado correctamente"); cargarEquiposNuevos(); notificarCambiosAplicados(); }
                    else      panel.mostrarError("No se pudo modificar el código");
                    panel.mostrarCargando(false);
                });
            } catch (ValidationException e) {
                SwingUtilities.invokeLater(() -> { panel.mostrarError("Error de validación: " + e.getMessage()); panel.mostrarCargando(false); });
            } catch (DatabaseException e) {
                SwingUtilities.invokeLater(() -> { panel.mostrarError("Error en la base de datos: " + e.getMessage()); panel.mostrarCargando(false); });
                log.error("Error de base de datos", e);
            }
        }).start();
    }

    private void agregarMaterial(Integer equipoId, Integer codigoCatalogo,
                                 Integer cantidad, String motivo) {
        panel.mostrarCargando(true);
        new Thread(() -> {
            try {
                boolean ok = correccionService.agregarMaterialAEquipo(
                    equipoId, codigoCatalogo, cantidad, motivo);
                SwingUtilities.invokeLater(() -> {
                    if (ok) { panel.mostrarMensaje("Material agregado correctamente"); cargarEquiposNuevos(); notificarCambiosAplicados(); }
                    else      panel.mostrarError("No se pudo agregar el material");
                    panel.mostrarCargando(false);
                });
            } catch (ValidationException e) {
                SwingUtilities.invokeLater(() -> { panel.mostrarError("Error de validación: " + e.getMessage()); panel.mostrarCargando(false); });
            } catch (DatabaseException e) {
                SwingUtilities.invokeLater(() -> { panel.mostrarError("Error en la base de datos: " + e.getMessage()); panel.mostrarCargando(false); });
                log.error("Error de base de datos al agregar material", e);
            }
        }).start();
    }

    private void eliminarEquipo(Integer equipoId, String motivo) {
        int respuesta = JOptionPane.showConfirmDialog(
            panel,
            "¿Está seguro de que desea eliminar este equipo?\n\nEsta acción no se puede deshacer.",
            "Confirmar eliminación",
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (respuesta != JOptionPane.YES_OPTION) return;

        panel.mostrarCargando(true);
        new Thread(() -> {
            try {
                boolean ok = correccionService.eliminarEquipo(equipoId, motivo);
                SwingUtilities.invokeLater(() -> {
                    if (ok) { panel.mostrarMensaje("Equipo eliminado correctamente"); cargarEquiposNuevos(); notificarCambiosAplicados(); }
                    else      panel.mostrarError("No se pudo eliminar el equipo");
                    panel.mostrarCargando(false);
                });
            } catch (ValidationException e) {
                SwingUtilities.invokeLater(() -> { panel.mostrarError("Error de validación: " + e.getMessage()); panel.mostrarCargando(false); });
            } catch (DatabaseException e) {
                SwingUtilities.invokeLater(() -> { panel.mostrarError("Error en la base de datos: " + e.getMessage()); panel.mostrarCargando(false); });
                log.error("Error de base de datos", e);
            }
        }).start();
    }

    private void eliminarMaterial(Integer equipoId, Integer codigoCatalogo, String motivo) {
        panel.mostrarCargando(true);
        new Thread(() -> {
            try {
                boolean ok = correccionService.eliminarMaterial(equipoId, codigoCatalogo, motivo);
                SwingUtilities.invokeLater(() -> {
                    if (ok) { panel.mostrarMensaje("Material eliminado correctamente"); cargarEquiposNuevos(); notificarCambiosAplicados(); }
                    else      panel.mostrarError("No se pudo eliminar el material");
                    panel.mostrarCargando(false);
                });
            } catch (ValidationException e) {
                SwingUtilities.invokeLater(() -> { panel.mostrarError("Error de validación: " + e.getMessage()); panel.mostrarCargando(false); });
            } catch (DatabaseException e) {
                SwingUtilities.invokeLater(() -> { panel.mostrarError("Error en la base de datos: " + e.getMessage()); panel.mostrarCargando(false); });
                log.error("Error de base de datos", e);
            }
        }).start();
    }

    // ── Operaciones otros ────────────────────────────────────────────────────

    private void modificarCantidadRemito(Integer equipoId, Integer cantidadNueva, String motivo) {
        panel.mostrarCargando(true);
        new Thread(() -> {
            try {
                boolean ok = otrosService.modificarCantidadRemito(equipoId, cantidadNueva, motivo);
                SwingUtilities.invokeLater(() -> {
                    if (ok) { panel.mostrarMensaje("Cantidad del remito modificada correctamente"); cargarEquiposNuevos(); notificarCambiosAplicados(); }
                    else      panel.mostrarError("No se pudo modificar la cantidad del remito");
                    panel.mostrarCargando(false);
                });
            } catch (ValidationException e) {
                SwingUtilities.invokeLater(() -> { panel.mostrarError("Error de validación: " + e.getMessage()); panel.mostrarCargando(false); });
            } catch (DatabaseException e) {
                SwingUtilities.invokeLater(() -> { panel.mostrarError("Error en la base de datos: " + e.getMessage()); panel.mostrarCargando(false); });
                log.error("Error al modificar cantidad remito equipo={}", equipoId, e);
            }
        }).start();
    }

    private void modificarCantidadMaterialOtros(Integer equipoId, Integer materialId,
                                                Integer cantidadNueva, String motivo) {
        panel.mostrarCargando(true);
        new Thread(() -> {
            try {
                boolean ok = otrosService.modificarCantidadMaterial(equipoId, materialId, cantidadNueva, motivo);
                SwingUtilities.invokeLater(() -> {
                    if (ok) { panel.mostrarMensaje("Cantidad modificada correctamente"); cargarEquiposNuevos(); notificarCambiosAplicados(); }
                    else      panel.mostrarError("No se pudo modificar la cantidad");
                    panel.mostrarCargando(false);
                });
            } catch (ValidationException e) {
                SwingUtilities.invokeLater(() -> { panel.mostrarError("Error de validación: " + e.getMessage()); panel.mostrarCargando(false); });
            } catch (DatabaseException e) {
                SwingUtilities.invokeLater(() -> { panel.mostrarError("Error en la base de datos: " + e.getMessage()); panel.mostrarCargando(false); });
                log.error("Error de base de datos", e);
            }
        }).start();
    }

    private void agregarMaterialOtros(Integer equipoId, String descripcion,
                                      Integer cantidad, String motivo) {
        panel.mostrarCargando(true);
        new Thread(() -> {
            try {
                boolean ok = otrosService.agregarMaterial(equipoId, descripcion, cantidad, motivo);
                SwingUtilities.invokeLater(() -> {
                    if (ok) { panel.mostrarMensaje("Material agregado correctamente"); cargarEquiposNuevos(); notificarCambiosAplicados(); }
                    else      panel.mostrarError("No se pudo agregar el material");
                    panel.mostrarCargando(false);
                });
            } catch (ValidationException e) {
                SwingUtilities.invokeLater(() -> { panel.mostrarError("Error de validación: " + e.getMessage()); panel.mostrarCargando(false); });
            } catch (DatabaseException e) {
                SwingUtilities.invokeLater(() -> { panel.mostrarError("Error en la base de datos: " + e.getMessage()); panel.mostrarCargando(false); });
                log.error("Error al agregar material otros equipo={}", equipoId, e);
            }
        }).start();
    }

    private void eliminarMaterialOtros(Integer equipoId, String descripcion, String motivo) {
        panel.mostrarCargando(true);
        new Thread(() -> {
            try {
                boolean ok = otrosService.eliminarMaterial(equipoId, descripcion, motivo);
                SwingUtilities.invokeLater(() -> {
                    if (ok) { panel.mostrarMensaje("Material eliminado correctamente"); cargarEquiposNuevos(); notificarCambiosAplicados(); }
                    else      panel.mostrarError("No se pudo eliminar el material");
                    panel.mostrarCargando(false);
                });
            } catch (ValidationException e) {
                SwingUtilities.invokeLater(() -> { panel.mostrarError("Error de validación: " + e.getMessage()); panel.mostrarCargando(false); });
            } catch (DatabaseException e) {
                SwingUtilities.invokeLater(() -> { panel.mostrarError("Error en la base de datos: " + e.getMessage()); panel.mostrarCargando(false); });
                log.error("Error de base de datos", e);
            }
        }).start();
    }

    private void eliminarEquipoOtros(Integer equipoId, String motivo) {
        int respuesta = JOptionPane.showConfirmDialog(
            panel,
            "¿Está seguro de que desea eliminar este equipo?\n\nEsta acción no se puede deshacer.",
            "Confirmar eliminación",
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (respuesta != JOptionPane.YES_OPTION) return;

        panel.mostrarCargando(true);
        new Thread(() -> {
            try {
                boolean ok = otrosService.eliminarEquipo(equipoId, motivo);
                SwingUtilities.invokeLater(() -> {
                    if (ok) { panel.mostrarMensaje("Equipo eliminado correctamente"); cargarEquiposNuevos(); notificarCambiosAplicados(); }
                    else      panel.mostrarError("No se pudo eliminar el equipo");
                    panel.mostrarCargando(false);
                });
            } catch (ValidationException e) {
                SwingUtilities.invokeLater(() -> { panel.mostrarError("Error de validación: " + e.getMessage()); panel.mostrarCargando(false); });
            } catch (DatabaseException e) {
                SwingUtilities.invokeLater(() -> { panel.mostrarError("Error en la base de datos: " + e.getMessage()); panel.mostrarCargando(false); });
                log.error("Error de base de datos", e);
            }
        }).start();
    }

    private void notificarCambiosAplicados() {
        if (onCambiosAplicados != null) onCambiosAplicados.run();
    }
}
