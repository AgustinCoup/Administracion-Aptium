package com.example.features.equipos.controller;

import com.example.app.AppModel;
import com.example.common.exception.DatabaseException;
import com.example.common.exception.ValidationException;
import com.example.common.constants.Constantes;
import com.example.features.equipos.dao.EquipoDAO;
import com.example.features.equipos.dao.MaterialDAO;
import com.example.features.equipos.dao.AuditoriaDAO;
import com.example.features.catalogo.dao.CatalogoDAO;
import com.example.features.equipos.model.Equipo;
import com.example.features.equipos.service.EquipoCorreccionService;
import com.example.features.equipos.view.PantallaCorrecciones;
import com.example.features.equipos.view.PantallaAuditoria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.List;

/**
 * Controlador para PantallaCorrecciones.
 * Responsabilidad: Gestionar la lógica de correcciones de equipos en estado NUEVO.
 * 
 * Acciones soportadas:
 * - Cargar equipos nuevos para edición
 * - Modificar cantidad de material
 * - Modificar código de catálogo
 * - Eliminar equipo completo
 * - Ver auditoría de cambios
 * 
 * DEPENDENCY INJECTION:
 * - Recibe DAOs por constructor (separación de responsabilidades)
 * - EquipoDAO: operaciones de equipo
 * - MaterialDAO: operaciones de material
 * - AuditoriaDAO: registro de auditoría
 * - CatalogoDAO: búsquedas en catálogo
 */
public class CorreccionsController {
    private static final Logger log = LoggerFactory.getLogger(CorreccionsController.class);

    private PantallaCorrecciones panel;
    private EquipoCorreccionService correccionService;
    private Runnable onCambiosAplicados;

    public CorreccionsController(PantallaCorrecciones panel, AppModel model) {
        this.panel = panel;
        
        // Inyectar DAOs en el servicio
        EquipoDAO equipoDAO = new EquipoDAO();
        MaterialDAO materialDAO = new MaterialDAO();
        AuditoriaDAO auditoriaDAO = new AuditoriaDAO();
        CatalogoDAO catalogoDAO = new CatalogoDAO();
        
        this.correccionService = new EquipoCorreccionService(equipoDAO, materialDAO, auditoriaDAO, catalogoDAO);

        // Registrar listeners de la vista
        this.panel.setOnModificarCantidad((equipoId, materialId, cantidad, motivo) -> 
            modificarCantidadMaterial(equipoId, materialId, cantidad, motivo));
        this.panel.setOnModificarCodigo((equipoId, materialId, codigo, motivo) -> 
            modificarCodigoMaterial(equipoId, materialId, codigo, motivo));
        this.panel.setOnEliminarEquipo(this::eliminarEquipo);
        this.panel.setOnEliminarMaterial(this::eliminarMaterial);
        this.panel.setOnVerAuditoria(this::abrirPantallaAuditoria);
        
        // Registrar listener para recargar cuando se hace visible la pantalla
        this.panel.setOnPantallaVisible(this::cargarEquiposNuevos);
        
        // Registrar listener para búsqueda de descripción en tiempo real
        this.panel.setOnCodigoNuevoChanged((codigo, campoDescripcion) -> {
            String descripcion = correccionService.obtenerDescripcionMaterial(codigo);
            campoDescripcion.setText(descripcion != null ? descripcion : Constantes.Mensajes.AUTOCOMPLETE_DESCONOCIDO);
        });

        // Cargar datos iniciales
        cargarEquiposNuevos();
        
        // Limpiar pantalla al inicializar
        panel.limpiarPantalla();
    }

    /**
     * Carga todos los equipos en estado NUEVO desde la base de datos.
     */
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

    /**
     * Modifica la cantidad de un material.
     * @param equipoId ID del equipo
     * @param materialId ID del material
     * @param cantidadNueva Nueva cantidad
     * @param motivo Motivo del cambio
     */
    private void modificarCantidadMaterial(Integer equipoId, Integer materialId, 
                                          Integer cantidadNueva, String motivo) {
        new Thread(() -> {
            try {
                panel.mostrarCargando(true);
                boolean resultado = correccionService.modificarCantidadMaterial(
                    equipoId, materialId, cantidadNueva, motivo);
                
                SwingUtilities.invokeLater(() -> {
                    if (resultado) {
                        panel.mostrarMensaje("Cantidad modificada correctamente");
                        cargarEquiposNuevos(); // Recargar lista (esto llamará a limpiarPantalla())
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
                log.warn("Error de validación: {}", e.getMessage());
            } catch (DatabaseException e) {
                SwingUtilities.invokeLater(() -> {
                    panel.mostrarError("Error en la base de datos: " + e.getMessage());
                    panel.mostrarCargando(false);
                });
                log.error("Error de base de datos", e);
            }
        }).start();
    }

    /**
     * Modifica el código de catálogo de un material.
     * @param equipoId ID del equipo
     * @param materialId ID del material
     * @param codigoNuevo Nuevo código de catálogo
     * @param motivo Motivo del cambio
     */
    private void modificarCodigoMaterial(Integer equipoId, Integer materialId, 
                                        Integer codigoNuevo, String motivo) {
        new Thread(() -> {
            try {
                panel.mostrarCargando(true);
                boolean resultado = correccionService.modificarCodigoMaterial(
                    equipoId, materialId, codigoNuevo, motivo);
                
                SwingUtilities.invokeLater(() -> {
                    if (resultado) {
                        panel.mostrarMensaje("Código modificado correctamente");
                        cargarEquiposNuevos(); // Recargar lista (esto llamará a limpiarPantalla())
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
                log.warn("Error de validación: {}", e.getMessage());
            } catch (DatabaseException e) {
                SwingUtilities.invokeLater(() -> {
                    panel.mostrarError("Error en la base de datos: " + e.getMessage());
                    panel.mostrarCargando(false);
                });
                log.error("Error de base de datos", e);
            }
        }).start();
    }

    /**
     * Elimina un equipo completo previa confirmación.
     * @param equipoId ID del equipo a eliminar
     * @param motivo Motivo de la eliminación
     */
    private void eliminarEquipo(Integer equipoId, String motivo) {
        int respuesta = JOptionPane.showConfirmDialog(
            panel,
            "¿Está seguro de que desea eliminar este equipo?\n\nEsta acción no se puede deshacer.",
            "Confirmar eliminación",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );

        if (respuesta == JOptionPane.YES_OPTION) {
            new Thread(() -> {
                try {
                    panel.mostrarCargando(true);
                    boolean resultado = correccionService.eliminarEquipo(equipoId, motivo);
                    
                    SwingUtilities.invokeLater(() -> {
                        if (resultado) {
                            panel.mostrarMensaje("Equipo eliminado correctamente");
                            cargarEquiposNuevos(); // Recargar lista
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
                    log.warn("Error de validación: {}", e.getMessage());
                } catch (DatabaseException e) {
                    SwingUtilities.invokeLater(() -> {
                        panel.mostrarError("Error en la base de datos: " + e.getMessage());
                        panel.mostrarCargando(false);
                    });
                    log.error("Error de base de datos", e);
                }
            }).start();
        }
    }

    /**
     * Elimina todos los materiales de un código específico dentro de un equipo.
     * @param equipoId ID del equipo
     * @param codigoCatalogo Código del material a eliminar
     * @param motivo Motivo de la eliminación
     */
    private void eliminarMaterial(Integer equipoId, Integer codigoCatalogo, String motivo) {
        new Thread(() -> {
            try {
                panel.mostrarCargando(true);
                boolean resultado = correccionService.eliminarMaterial(equipoId, codigoCatalogo, motivo);

                SwingUtilities.invokeLater(() -> {
                    if (resultado) {
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
                log.warn("Error de validación: {}", e.getMessage());
            } catch (DatabaseException e) {
                SwingUtilities.invokeLater(() -> {
                    panel.mostrarError("Error en la base de datos: " + e.getMessage());
                    panel.mostrarCargando(false);
                });
                log.error("Error de base de datos", e);
            }
        }).start();
    }

    /**
     * Abre la pantalla de auditoría para ver todos los registros.
     * Muestra TODA la tabla de auditoría de la base de datos, sin filtros por equipo.
     */
    private void abrirPantallaAuditoria() {
        try {
            panel.mostrarCargando(true);
            JFrame frameAuditoria = new JFrame("Auditoría - Todos los Registros");
            frameAuditoria.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frameAuditoria.setSize(900, 600);
            frameAuditoria.setLocationRelativeTo(null);
            
            // Crear y agregar la pantalla de auditoría (sin filtro de equipo)
            PantallaAuditoria pantallaAuditoria = 
                new PantallaAuditoria(correccionService);
            
            frameAuditoria.add(pantallaAuditoria);
            frameAuditoria.setVisible(true);
            panel.mostrarCargando(false);
            
            log.info("Abierta pantalla de auditoría con todos los registros");
        } catch (Exception e) {
            panel.mostrarError("Error al abrir auditoría: " + e.getMessage());
            panel.mostrarCargando(false);
            log.error("Error al abrir pantalla de auditoría", e);
        }
    }

    /**
     * Obtiene el servicio de correcciones (útil para testing).
     */
    public EquipoCorreccionService getCorreccionService() {
        return correccionService;
    }

    public void setOnCambiosAplicados(Runnable onCambiosAplicados) {
        this.onCambiosAplicados = onCambiosAplicados;
    }

    private void notificarCambiosAplicados() {
        if (onCambiosAplicados != null) {
            onCambiosAplicados.run();
        }
    }
}
