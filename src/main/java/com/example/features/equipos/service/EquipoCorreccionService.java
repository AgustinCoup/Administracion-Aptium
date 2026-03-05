package com.example.features.equipos.service;

import com.example.common.exception.DatabaseException;
import com.example.common.exception.ValidationException;
import com.example.features.equipos.dao.AuditoriaDAO;
import com.example.features.equipos.dao.EquipoDAO;
import com.example.features.equipos.dao.MaterialDAO;
import com.example.features.catalogo.dao.CatalogoDAO;
import com.example.features.equipos.model.EquipoAuditoria;
import com.example.features.equipos.model.Equipo;
import com.example.features.equipos.model.EstadoEquipo;
import com.example.features.equipos.model.Material;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Servicio de negocio para correcciones y auditoría de equipos.
 * Gestiona todas las operaciones de modificación de equipos en estado "Nuevo".
 *
 * RESTRICCIONES:
 * - Solo permite modificar equipos en estado "Nuevo"
 * - Registra todos los cambios en la tabla de auditoría
 * - Incluye motivo obligatorio para cada cambio
 *
 * DEPENDENCY INJECTION:
 * - Recibe DAOs por constructor (permite testing con mocks)
 */
public class EquipoCorreccionService {

    private static final Logger log = LoggerFactory.getLogger(EquipoCorreccionService.class);

    private final EquipoDAO    equipoDAO;
    private final MaterialDAO  materialDAO;
    private final AuditoriaDAO auditoriaDAO;
    private final CatalogoDAO  catalogoDAO;

    public EquipoCorreccionService(EquipoDAO equipoDAO, MaterialDAO materialDAO,
                                   AuditoriaDAO auditoriaDAO, CatalogoDAO catalogoDAO) {
        if (equipoDAO   == null) throw new IllegalArgumentException("EquipoDAO no puede ser nulo");
        if (materialDAO == null) throw new IllegalArgumentException("MaterialDAO no puede ser nulo");
        if (auditoriaDAO == null) throw new IllegalArgumentException("AuditoriaDAO no puede ser nulo");
        if (catalogoDAO  == null) throw new IllegalArgumentException("CatalogoDAO no puede ser nulo");
        this.equipoDAO    = equipoDAO;
        this.materialDAO  = materialDAO;
        this.auditoriaDAO = auditoriaDAO;
        this.catalogoDAO  = catalogoDAO;
    }

    // ── Modificaciones ───────────────────────────────────────────────────────

    /**
     * Modifica la cantidad de un material de un equipo.
     */
    public boolean modificarCantidadMaterial(Integer equipoId, Integer materialId,
                                             Integer cantidadNueva, String motivo) {
        ValidationException.Builder v = ValidationException.builder();
        v.addErrorIf(equipoId      == null, "El ID del equipo es obligatorio")
         .addErrorIf(materialId    == null, "El ID del material es obligatorio")
         .addErrorIf(cantidadNueva == null, "La cantidad nueva es obligatoria")
         .addErrorIf(cantidadNueva != null && cantidadNueva <= 0, "La cantidad debe ser mayor a 0")
         .addErrorIf(motivo == null || motivo.trim().isEmpty(), "El motivo de la corrección es obligatorio");
        v.throwIfHasErrors();

        Equipo equipo = equipoDAO.obtenerPorId(equipoId.toString());
        if (!equipo.getEstado().equals(EstadoEquipo.NUEVO)) {
            throw new ValidationException("El equipo no está en estado 'Nuevo' y no puede ser modificado");
        }

        try {
            Integer cantidadAnterior = materialDAO.obtenerCantidad(materialId);
            if (cantidadAnterior == null) throw new ValidationException("El material no existe");

            materialDAO.actualizarCantidad(materialId, cantidadNueva);
            auditoriaDAO.registrarCambio(equipoId, materialId, "MODIFICACION_CANTIDAD",
                "cantidad", String.valueOf(cantidadAnterior), String.valueOf(cantidadNueva), motivo.trim());

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
     */
    public boolean modificarCodigoMaterial(Integer equipoId, Integer materialId,
                                           Integer codigoNuevo, String motivo) {
        ValidationException.Builder v = ValidationException.builder();
        v.addErrorIf(equipoId   == null, "El ID del equipo es obligatorio")
         .addErrorIf(materialId == null, "El ID del material es obligatorio")
         .addErrorIf(codigoNuevo == null, "El código nuevo es obligatorio")
         .addErrorIf(codigoNuevo != null && codigoNuevo <= 0, "El código debe ser válido")
         .addErrorIf(motivo == null || motivo.trim().isEmpty(), "El motivo de la corrección es obligatorio");
        v.throwIfHasErrors();

        Equipo equipo = equipoDAO.obtenerPorId(equipoId.toString());
        if (!equipo.getEstado().equals(EstadoEquipo.NUEVO)) {
            throw new ValidationException("El equipo no está en estado 'Nuevo' y no puede ser modificado");
        }

        try {
            Object[] materialActual = materialDAO.obtenerMaterial(materialId);
            if (materialActual == null) throw new ValidationException("El material no existe");

            Integer codigoAnterior      = (Integer) materialActual[0];
            String  descripcionAnterior = (String)  materialActual[2];

            String descripcionNueva = catalogoDAO.obtenerDescripcion(codigoNuevo);
            if (descripcionNueva == null) {
                throw new ValidationException("El código de catálogo " + codigoNuevo + " no existe");
            }

            materialDAO.actualizarCodigo(materialId, codigoNuevo);
            String valAnterior = codigoAnterior + " (" + (descripcionAnterior != null ? descripcionAnterior : "N/A") + ")";
            String valNuevo    = codigoNuevo    + " (" + descripcionNueva + ")";
            auditoriaDAO.registrarCambio(equipoId, materialId, "MODIFICACION_CODIGO",
                "codigo_catalogo", valAnterior, valNuevo, motivo.trim());

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

    // ── Eliminaciones ────────────────────────────────────────────────────────

    /**
     * Elimina un equipo completo (hard delete) y registra en auditoría.
     */
    public boolean eliminarEquipo(Integer equipoId, String motivo) {
        ValidationException.Builder v = ValidationException.builder();
        v.addErrorIf(equipoId == null, "El ID del equipo es obligatorio")
         .addErrorIf(motivo == null || motivo.trim().isEmpty(), "El motivo de la eliminación es obligatorio");
        v.throwIfHasErrors();

        Equipo equipo = equipoDAO.obtenerPorId(equipoId.toString());
        if (!equipo.getEstado().equals(EstadoEquipo.NUEVO)) {
            throw new ValidationException("El equipo no está en estado 'Nuevo' y no puede ser eliminado");
        }

        try {
            boolean snapEquipo = auditoriaDAO.registrarEquipoEliminado(
                equipo.getId(), equipo.getNroCliente(), equipo.getClienteNombre(),
                equipo.getNroProfesional(), equipo.getPacienteNombre(),
                equipo.getNroInstitucion(), equipo.getInstitucionNombre(),
                equipo.getEstado().getNombre(), motivo.trim());
            if (!snapEquipo) throw new DatabaseException("No se pudo registrar el snapshot de equipo eliminado");

            for (Material material : equipo.getMateriales()) {
                boolean snapMat = auditoriaDAO.registrarMaterialEliminado(
                    equipo.getId(), material.getId(), material.getCodigo(),
                    material.getDescripcion(), material.getCantidad(),
                    material.getEstado() != null ? material.getEstado().getNombre() : null,
                    motivo.trim());
                if (!snapMat) throw new DatabaseException("No se pudo registrar el snapshot de materiales eliminados");
            }

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
     */
    public boolean eliminarMaterial(Integer equipoId, Integer codigoCatalogo, String motivo) {
        ValidationException.Builder v = ValidationException.builder();
        v.addErrorIf(equipoId       == null, "El ID del equipo es obligatorio")
         .addErrorIf(codigoCatalogo == null, "El código de catálogo es obligatorio")
         .addErrorIf(motivo == null || motivo.trim().isEmpty(), "El motivo de la eliminación es obligatorio");
        v.throwIfHasErrors();

        Equipo equipo = equipoDAO.obtenerPorId(equipoId.toString());
        if (!equipo.getEstado().equals(EstadoEquipo.NUEVO)) {
            throw new ValidationException("El equipo no está en estado 'Nuevo' y no puede ser modificado");
        }

        List<Object[]> materiales = materialDAO.obtenerMaterialesPorCodigo(equipoId, codigoCatalogo);
        if (materiales.isEmpty()) {
            throw new ValidationException("No existen materiales con ese código en el equipo seleccionado");
        }

        try {
            for (Object[] material : materiales) {
                Integer materialId  = (Integer) material[0];
                Integer codigo      = (Integer) material[1];
                String  descripcion = (String)  material[2];
                Integer cantidad    = (Integer) material[3];
                String  estado      = (String)  material[4];

                boolean snap = auditoriaDAO.registrarMaterialEliminado(
                    equipoId, materialId, codigo, descripcion, cantidad, estado, motivo.trim());
                if (!snap) throw new DatabaseException("No se pudo registrar el snapshot del material eliminado");
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

    // ── Adición ──────────────────────────────────────────────────────────────

    /**
     * Agrega un material nuevo a un equipo existente en estado "Nuevo".
     *
     * VALIDACIONES:
     * - El equipo debe existir y estar en estado "Nuevo"
     * - El código de catálogo debe existir en catalogo_descripciones
     * - La cantidad debe ser positiva
     * - El motivo es obligatorio
     *
     * TRANSACCIÓN (delegada a MaterialDAO):
     * - Inserta fila en equipo_materiales con estado NUEVO
     * - Registra movimiento inicial en material_movimientos
     * - Registra auditoría con tipo ADICION_MATERIAL
     *
     * @param equipoId       ID del equipo
     * @param codigoCatalogo Código del material en el catálogo
     * @param cantidad       Cantidad a agregar
     * @param motivo         Motivo de la adición
     * @return true si se agregó correctamente
     * @throws ValidationException si los datos son inválidos
     * @throws DatabaseException   si hay error en BD
     */
    public boolean agregarMaterialAEquipo(Integer equipoId, Integer codigoCatalogo,
                                          Integer cantidad, String motivo) {
        ValidationException.Builder v = ValidationException.builder();
        v.addErrorIf(equipoId       == null, "El ID del equipo es obligatorio")
         .addErrorIf(codigoCatalogo == null, "El código de catálogo es obligatorio")
         .addErrorIf(codigoCatalogo != null && codigoCatalogo <= 0, "El código debe ser válido")
         .addErrorIf(cantidad       == null, "La cantidad es obligatoria")
         .addErrorIf(cantidad       != null && cantidad <= 0, "La cantidad debe ser mayor a 0")
         .addErrorIf(motivo == null || motivo.trim().isEmpty(), "El motivo es obligatorio");
        v.throwIfHasErrors();

        Equipo equipo = equipoDAO.obtenerPorId(equipoId.toString());
        if (!equipo.getEstado().equals(EstadoEquipo.NUEVO)) {
            throw new ValidationException("El equipo no está en estado 'Nuevo' y no puede ser modificado");
        }

        String descripcion = catalogoDAO.obtenerDescripcion(codigoCatalogo);
        if (descripcion == null) {
            throw new ValidationException("El código de catálogo " + codigoCatalogo + " no existe");
        }

        try {
            Integer nuevoMaterialId = materialDAO.agregarMaterial(equipoId, codigoCatalogo, cantidad);

            // Registrar en auditoría: valor_nuevo = "cantidad uds." para que la vista
            // muestre el dato en la columna Valor Nuevo; material_info se resuelve
            // automáticamente en vista_auditoria via JOIN a equipo_materiales.
            auditoriaDAO.registrarCambio(
                equipoId, nuevoMaterialId,
                "ADICION_MATERIAL",
                "material_nuevo",
                null,
                cantidad + " uds.",
                motivo.trim()
            );

            log.info("Material código={} (cantidad={}) agregado al equipo {} - Motivo: {}",
                codigoCatalogo, cantidad, equipoId, motivo);
            return true;
        } catch (DatabaseException e) {
            log.error("Error al agregar material código={} al equipo {}", codigoCatalogo, equipoId, e);
            throw e;
        }
    }

    // ── Consultas ────────────────────────────────────────────────────────────

    /** Obtiene todos los equipos en estado "Nuevo" (editables). */
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

    /** Obtiene el historial de auditoría para un equipo específico. */
    public List<EquipoAuditoria> obtenerAuditoriaEquipo(Integer equipoId) {
        if (equipoId == null) throw new IllegalArgumentException("El ID del equipo es obligatorio");
        try {
            List<EquipoAuditoria> auditorias = auditoriaDAO.obtenerPorEquipo(equipoId);
            log.info("Obtenido historial de auditoría para equipo {}: {} registros", equipoId, auditorias.size());
            return auditorias;
        } catch (DatabaseException e) {
            log.error("Error al obtener auditoría del equipo {}", equipoId, e);
            throw e;
        }
    }

    /** Obtiene TODOS los registros de auditoría del sistema. */
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

    /** Obtiene la descripción de un material del catálogo por su código. */
    public String obtenerDescripcionMaterial(int codigo) {
        try {
            return catalogoDAO.obtenerDescripcion(codigo);
        } catch (DatabaseException e) {
            log.error("Error al obtener descripción del material con código {}", codigo, e);
            return null;
        }
    }
}