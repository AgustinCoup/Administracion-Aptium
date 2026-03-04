package com.example.features.equipos.service;

import com.example.common.exception.DatabaseException;
import com.example.common.exception.ValidationException;
import com.example.features.equipos.dao.EquipoDAO;
import com.example.features.equipos.model.Material;
import com.example.features.equipos.dao.MaterialDAO;
import com.example.features.equipos.dao.AuditoriaDAO;
import com.example.features.catalogo.dao.CatalogoDAO;
import com.example.features.equipos.model.EquipoAuditoria;
import com.example.features.equipos.model.Equipo;
import com.example.features.equipos.model.EstadoEquipo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

/**
 * Servicio de negocio para correcciones y auditoría de equipos.
 * Gestiona todas las operaciones de modificación de equipos que están en estado "Nuevo".
 * 
 * RESTRICCIONES:
 * - Solo permite modificar equipos en estado "Nuevo"
 * - Registra todos los cambios en la tabla de auditoría
 * - Incluye motivo obligatorio para cada cambio
 * 
 * SEPARATION OF CONCERNS:
 * - EquipoDAO: operaciones a nivel de entidad Equipo
 * - MaterialDAO: operaciones sobre materiales (cantidad, código)
 * - AuditoriaDAO: registro de auditoría
 * - CatalogoDAO: búsquedas en catálogo
 * 
 * DEPENDENCY INJECTION:
 * - Recibe DAOs por constructor (permite testing con mocks)
 */
public class EquipoCorreccionService {

    private static final Logger log = LoggerFactory.getLogger(EquipoCorreccionService.class);

    private final EquipoDAO equipoDAO;
    private final MaterialDAO materialDAO;
    private final AuditoriaDAO auditoriaDAO;
    private final CatalogoDAO catalogoDAO;

    public EquipoCorreccionService(EquipoDAO equipoDAO, MaterialDAO materialDAO, 
                                   AuditoriaDAO auditoriaDAO, CatalogoDAO catalogoDAO) {
        if (equipoDAO == null) {
            throw new IllegalArgumentException("EquipoDAO no puede ser nulo");
        }
        if (materialDAO == null) {
            throw new IllegalArgumentException("MaterialDAO no puede ser nulo");
        }
        if (auditoriaDAO == null) {
            throw new IllegalArgumentException("AuditoriaDAO no puede ser nulo");
        }
        if (catalogoDAO == null) {
            throw new IllegalArgumentException("CatalogoDAO no puede ser nulo");
        }
        this.equipoDAO = equipoDAO;
        this.materialDAO = materialDAO;
        this.auditoriaDAO = auditoriaDAO;
        this.catalogoDAO = catalogoDAO;
    }

    /**
     * Modifica la cantidad de un material de un equipo.
     * VALIDACIONES:
     * - El equipo debe estar en estado "Nuevo"
     * - La cantidad nueva debe ser positiva
     * - El motivo es obligatorio y no puede estar vacío
     * 
     * TRANSACCIÓN:
     * - Actualiza cantidad en MaterialDAO
     * - Registra cambio en AuditoriaDAO
     * 
     * @param equipoId ID del equipo
     * @param materialId ID del material a modificar
     * @param cantidadNueva Nueva cantidad
     * @param motivo Motivo de la corrección
     * @return true si se actualizó correctamente
     * @throws ValidationException si los datos son inválidos
     * @throws DatabaseException si hay error en BD
     */
    public boolean modificarCantidadMaterial(Integer equipoId, Integer materialId, 
                                            Integer cantidadNueva, String motivo) {
        // Validaciones
        ValidationException.Builder validationBuilder = ValidationException.builder();
        
        validationBuilder
            .addErrorIf(equipoId == null, "El ID del equipo es obligatorio")
            .addErrorIf(materialId == null, "El ID del material es obligatorio")
            .addErrorIf(cantidadNueva == null, "La cantidad nueva es obligatoria")
            .addErrorIf(cantidadNueva != null && cantidadNueva <= 0, "La cantidad debe ser mayor a 0")
            .addErrorIf(motivo == null || motivo.trim().isEmpty(), "El motivo de la corrección es obligatorio");
        
        validationBuilder.throwIfHasErrors();

        // Validar que el equipo está en estado "Nuevo"
        Equipo equipo = equipoDAO.obtenerPorId(equipoId.toString());
        if (!equipo.getEstado().equals(EstadoEquipo.NUEVO)) {
            throw new ValidationException("El equipo no está en estado 'Nuevo' y no puede ser modificado");
        }

        try {
            // Obtener cantidad anterior para auditoría
            Integer cantidadAnterior = materialDAO.obtenerCantidad(materialId);
            if (cantidadAnterior == null) {
                throw new ValidationException("El material no existe");
            }

            // Actualizar cantidad
            materialDAO.actualizarCantidad(materialId, cantidadNueva);

            // Registrar auditoría
            auditoriaDAO.registrarCambio(equipoId, materialId, "MODIFICACION_CANTIDAD", 
                                        "cantidad", String.valueOf(cantidadAnterior), String.valueOf(cantidadNueva), 
                                        motivo.trim());

            log.info("Cantidad de material {} del equipo {} modificada de {} a {} - Motivo: {}", 
                    materialId, equipoId, cantidadAnterior, cantidadNueva, motivo);
            return true;

        } catch (ValidationException e) {
            throw e;
        } catch (DatabaseException e) {
            log.error("Error al modificar cantidad de material {} del equipo {}", materialId, equipoId, e);
            throw e;
        }
    }

    /**
     * Modifica el código de catálogo de un material de un equipo.
     * VALIDACIONES:
     * - El equipo debe estar en estado "Nuevo"
     * - El código nuevo debe existir en catálogo
     * - El motivo es obligatorio y no puede estar vacío
     * 
     * TRANSACCIÓN:
     * - Obtiene código anterior
     * - Valida código nuevo contra CatalogoDAO
     * - Actualiza código en MaterialDAO
     * - Registra cambio en AuditoriaDAO
     * 
     * @param equipoId ID del equipo
     * @param materialId ID del material a modificar
     * @param codigoNuevo Nuevo código de catálogo (debe existir en catalogo_descripciones)
     * @param motivo Motivo de la corrección
     * @return true si se actualizó correctamente
     * @throws ValidationException si los datos son inválidos
     * @throws DatabaseException si hay error en BD
     */
    public boolean modificarCodigoMaterial(Integer equipoId, Integer materialId, 
                                          Integer codigoNuevo, String motivo) {
        // Validaciones
        ValidationException.Builder validationBuilder = ValidationException.builder();
        
        validationBuilder
            .addErrorIf(equipoId == null, "El ID del equipo es obligatorio")
            .addErrorIf(materialId == null, "El ID del material es obligatorio")
            .addErrorIf(codigoNuevo == null, "El código nuevo es obligatorio")
            .addErrorIf(codigoNuevo != null && codigoNuevo <= 0, "El código debe ser válido")
            .addErrorIf(motivo == null || motivo.trim().isEmpty(), "El motivo de la corrección es obligatorio");
        
        validationBuilder.throwIfHasErrors();

        // Validar que el equipo está en estado "Nuevo"
        Equipo equipo = equipoDAO.obtenerPorId(equipoId.toString());
        if (!equipo.getEstado().equals(EstadoEquipo.NUEVO)) {
            throw new ValidationException("El equipo no está en estado 'Nuevo' y no puede ser modificado");
        }

        try {
            // Obtener código anterior para auditoría
            Object[] materialActual = materialDAO.obtenerMaterial(materialId);
            if (materialActual == null) {
                throw new ValidationException("El material no existe");
            }

            Integer codigoAnterior = (Integer) materialActual[0];  // [codigo, equipoId, descripcion, cantidad, estado]
            String descripcionAnterior = (String) materialActual[2];

            // Validar que el código nuevo existe en el catálogo
            String descripcionNueva = catalogoDAO.obtenerDescripcion(codigoNuevo);
            if (descripcionNueva == null) {
                throw new ValidationException("El código de catálogo " + codigoNuevo + " no existe");
            }

            // Actualizar código
            materialDAO.actualizarCodigo(materialId, codigoNuevo);

            // Registrar auditoría con código y descripción
            String valoresAnteriores = codigoAnterior + " (" + (descripcionAnterior != null ? descripcionAnterior : "N/A") + ")";
            String valoresNuevos = codigoNuevo + " (" + descripcionNueva + ")";
            auditoriaDAO.registrarCambio(equipoId, materialId, "MODIFICACION_CODIGO", 
                                        "codigo_catalogo", valoresAnteriores, valoresNuevos, motivo.trim());

            log.info("Código de material {} del equipo {} modificado de {} a {} - Motivo: {}", 
                    materialId, equipoId, codigoAnterior, codigoNuevo, motivo);
            return true;

        } catch (ValidationException e) {
            throw e;
        } catch (DatabaseException e) {
            log.error("Error al modificar código de material {} del equipo {}", materialId, equipoId, e);
            throw e;
        }
    }

    /**
     * Elimina un equipo completo (hard delete) y registra en auditoría.
     * VALIDACIONES:
     * - El equipo debe estar en estado "Nuevo"
     * - El motivo es obligatorio y no puede estar vacío
     * 
     * @param equipoId ID del equipo a eliminar
     * @param motivo Motivo de la eliminación
     * @return true si se eliminó correctamente
     * @throws ValidationException si los datos son inválidos
     * @throws DatabaseException si hay error en BD
     */
    public boolean eliminarEquipo(Integer equipoId, String motivo) {
        // Validaciones
        ValidationException.Builder validationBuilder = ValidationException.builder();
        
        validationBuilder
            .addErrorIf(equipoId == null, "El ID del equipo es obligatorio")
            .addErrorIf(motivo == null || motivo.trim().isEmpty(), "El motivo de la eliminación es obligatorio");
        
        validationBuilder.throwIfHasErrors();

        // Validar que el equipo está en estado "Nuevo"
        Equipo equipo = equipoDAO.obtenerPorId(equipoId.toString());
        if (!equipo.getEstado().equals(EstadoEquipo.NUEVO)) {
            throw new ValidationException("El equipo no está en estado 'Nuevo' y no puede ser eliminado");
        }

        try {
            // Registrar snapshot persistente del equipo eliminado
            boolean equipoEliminadoRegistrado = auditoriaDAO.registrarEquipoEliminado(
                equipo.getId(),
                equipo.getNroCliente(),
                equipo.getClienteNombre(),
                equipo.getNroProfesional(),
                equipo.getPacienteNombre(),
                equipo.getNroInstitucion(),
                equipo.getInstitucionNombre(),
                equipo.getEstado().getNombre(),
                motivo.trim()
            );
            if (!equipoEliminadoRegistrado) {
                throw new DatabaseException("No se pudo registrar el snapshot de equipo eliminado");
            }

            // Registrar snapshot persistente de cada material del equipo
            for (Material material : equipo.getMateriales()) {
                boolean materialEliminadoRegistrado = auditoriaDAO.registrarMaterialEliminado(
                    equipo.getId(),
                    material.getId(),
                    material.getCodigo(),
                    material.getDescripcion(),
                    material.getCantidad(),
                    material.getEstado() != null ? material.getEstado().getNombre() : null,
                    motivo.trim()
                );
                if (!materialEliminadoRegistrado) {
                    throw new DatabaseException("No se pudo registrar el snapshot de materiales eliminados");
                }
            }

            // Luego eliminar el equipo
            equipoDAO.eliminar(equipoId.toString());

            log.info("Equipo {} eliminado exitosamente - Motivo: {}", equipoId, motivo);
            return true;

        } catch (ValidationException e) {
            throw e;
        } catch (DatabaseException e) {
            log.error("Error al eliminar equipo {}", equipoId, e);
            throw e;
        }
    }

    /**
     * Elimina todos los materiales de un código específico dentro de un equipo.
     * VALIDACIONES:
     * - El equipo debe estar en estado "Nuevo"
     * - El código de catálogo debe existir en el equipo
     * - El motivo es obligatorio y no puede estar vacío
     *
     * @param equipoId ID del equipo
     * @param codigoCatalogo Código del material a eliminar
     * @param motivo Motivo de la eliminación
     * @return true si se eliminó correctamente
     * @throws ValidationException si los datos son inválidos
     * @throws DatabaseException si hay error en BD
     */
    public boolean eliminarMaterial(Integer equipoId, Integer codigoCatalogo, String motivo) {
        ValidationException.Builder validationBuilder = ValidationException.builder();

        validationBuilder
            .addErrorIf(equipoId == null, "El ID del equipo es obligatorio")
            .addErrorIf(codigoCatalogo == null, "El código de catálogo es obligatorio")
            .addErrorIf(motivo == null || motivo.trim().isEmpty(), "El motivo de la eliminación es obligatorio");

        validationBuilder.throwIfHasErrors();

        Equipo equipo = equipoDAO.obtenerPorId(equipoId.toString());
        if (!equipo.getEstado().equals(EstadoEquipo.NUEVO)) {
            throw new ValidationException("El equipo no está en estado 'Nuevo' y no puede ser modificado");
        }

        List<Object[]> materiales = materialDAO.obtenerMaterialesPorCodigo(equipoId, codigoCatalogo);
        if (materiales.isEmpty()) {
            throw new ValidationException("No existen materiales con ese código en el equipo seleccionado");
        }

        try {
            // Registrar snapshot persistente por cada fila eliminada
            for (Object[] material : materiales) {
                Integer materialId = (Integer) material[0];
                Integer codigo = (Integer) material[1];
                String descripcion = (String) material[2];
                Integer cantidad = (Integer) material[3];
                String estado = (String) material[4];

                boolean materialEliminadoRegistrado = auditoriaDAO.registrarMaterialEliminado(
                    equipoId,
                    materialId,
                    codigo,
                    descripcion,
                    cantidad,
                    estado,
                    motivo.trim()
                );
                if (!materialEliminadoRegistrado) {
                    throw new DatabaseException("No se pudo registrar el snapshot del material eliminado");
                }
            }

            materialDAO.eliminarMaterialesPorCodigo(equipoId, codigoCatalogo);

            log.info("Material con código {} eliminado del equipo {} - Motivo: {}", codigoCatalogo, equipoId, motivo);
            return true;

        } catch (ValidationException e) {
            throw e;
        } catch (DatabaseException e) {
            log.error("Error al eliminar material {} del equipo {}", codigoCatalogo, equipoId, e);
            throw e;
        }
    }

    /**
     * Obtiene todos los equipos en estado "Nuevo" (editables).
     * @return Lista de equipos en estado NUEVO
     */
    public List<Equipo> obtenerEquiposNuevos() {
        try {
            List<Equipo> equipos = equipoDAO.obtenerEquiposNuevos();
            log.info("Obtenidos {} equipos en estado Nuevo", equipos.size());
            return equipos;
        } catch (DatabaseException e) {
            log.error("Error al obtener equipos nuevos", e);
            throw e;
        }
    }

    /**
     * Obtiene el historial de auditoría para un equipo específico.
     * @param equipoId ID del equipo
     * @return Lista de cambios registrados para el equipo, ordenados descendentemente por fecha
     */
    public List<EquipoAuditoria> obtenerAuditoriaEquipo(Integer equipoId) {
        if (equipoId == null) {
            throw new IllegalArgumentException("El ID del equipo es obligatorio");
        }

        try {
            List<EquipoAuditoria> auditorias = auditoriaDAO.obtenerPorEquipo(equipoId);
            log.info("Obtenido historial de auditoría para equipo {}: {} registros", 
                    equipoId, auditorias.size());
            return auditorias;
        } catch (DatabaseException e) {
            log.error("Error al obtener auditoría del equipo {}", equipoId, e);
            throw e;
        }
    }

    /**
     * Obtiene TODOS los registros de auditoría de la base de datos.
     * NOTA: No filtra por equipo - devuelve el historial completo de cambios.
     * @return Lista de todos los cambios registrados, ordenados descendentemente por fecha
     */
    public List<EquipoAuditoria> obtenerTodasAuditorias() {
        try {
            List<EquipoAuditoria> auditorias = auditoriaDAO.obtenerTodos();
            log.info("Obtenidos todos los registros de auditoría: {} registros", auditorias.size());
            return auditorias;
        } catch (DatabaseException e) {
            log.error("Error al obtener todos los registros de auditoría", e);
            throw e;
        }
    }

    /**
     * Obtiene la descripción de un material del catálogo por su código.
     * @param codigo Código del material en el catálogo
     * @return Descripción del material, o null si no existe
     */
    public String obtenerDescripcionMaterial(int codigo) {
        try {
            return catalogoDAO.obtenerDescripcion(codigo);
        } catch (DatabaseException e) {
            log.error("Error al obtener descripción del material con código {}", codigo, e);
            return null;
        }
    }
}
