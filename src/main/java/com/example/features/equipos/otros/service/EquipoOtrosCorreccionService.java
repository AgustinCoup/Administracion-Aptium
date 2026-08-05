package com.example.features.equipos.otros.service;

import com.example.common.exception.DatabaseException;
import com.example.common.exception.ValidationException;
import com.example.features.equipos.ortopedias.dao.AuditoriaDAO;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.otros.dao.EquipoOtrosDAO;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.MaterialOtros;
import com.example.features.equipos.otros.model.TipoIngresoOtros;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Servicio de correcciones para equipos de tipo "Otros".
 *
 * Refleja la estructura de {@link com.example.features.equipos.ortopedias.service.EquipoCorreccionService}
 * adaptada a las particularidades de REMITO y DETALLES.
 *
 * RESTRICCIONES:
 * - Solo permite modificar equipos en estado NUEVO.
 * - Para REMITO: "corregir cantidad" solo está disponible cuando no hay filas reales
 *   en equipo_otros_materiales (el equipo no tuvo movimientos aún).
 * - Todas las operaciones registran en las tablas de auditoría compartidas con ortopedias
 *   usando tipo_equipo = 'OTROS'.
 *
 * La auditoría se registra FUERA de la transacción del dato, después de que este
 * quedó confirmado: un fallo de auditoría no debe revertir la corrección.
 */
public class EquipoOtrosCorreccionService {

    private static final Logger log = LoggerFactory.getLogger(EquipoOtrosCorreccionService.class);
    private static final String TIPO = "OTROS";

    private final EquipoOtrosDAO equipoOtrosDAO;
    private final AuditoriaDAO   auditoriaDAO;

    public EquipoOtrosCorreccionService(EquipoOtrosDAO equipoOtrosDAO,
                                        AuditoriaDAO auditoriaDAO) {
        if (equipoOtrosDAO == null) throw new IllegalArgumentException("EquipoOtrosDAO no puede ser nulo");
        if (auditoriaDAO   == null) throw new IllegalArgumentException("AuditoriaDAO no puede ser nulo");
        this.equipoOtrosDAO = equipoOtrosDAO;
        this.auditoriaDAO   = auditoriaDAO;
    }

    // ── Consultas ────────────────────────────────────────────────────────────

    public List<EquipoOtros> obtenerEquiposOtrosNuevos() {
        return equipoOtrosDAO.obtenerEquiposNuevos();
    }

    // ── REMITO: modificar cantidad global ────────────────────────────────────

    /**
     * Modifica remito_cantidad de un REMITO que todavía no tuvo movimientos.
     * Si ya hay filas en equipo_otros_materiales la operación es bloqueada.
     */
    public boolean modificarCantidadRemito(int equipoId, int cantidadNueva, String motivo) {
        ValidationException.Builder v = ValidationException.builder();
        v.addErrorIf(cantidadNueva <= 0, "La cantidad debe ser mayor a 0")
         .addErrorIf(motivo == null || motivo.trim().isEmpty(), "El motivo es obligatorio");
        v.throwIfHasErrors();

        EquipoOtros equipo = cargarYValidarNuevo(equipoId);
        if (equipo.getTipoIngreso() != TipoIngresoOtros.REMITO) {
            throw new ValidationException("Esta operación es solo para equipos de tipo REMITO");
        }

        if (equipoOtrosDAO.tieneMateriales(equipoId)) {
            throw new ValidationException(
                "El equipo ya tuvo movimientos de estado. No se puede modificar la cantidad del remito.");
        }

        int cantidadAnterior = equipo.getRemitoCantidad() != null ? equipo.getRemitoCantidad() : 0;

        equipoOtrosDAO.actualizarCantidadRemito(equipoId, cantidadNueva);

        auditoriaDAO.registrarCambio(equipoId, null, "MODIFICACION_CANTIDAD",
            "remito_cantidad",
            String.valueOf(cantidadAnterior),
            String.valueOf(cantidadNueva),
            motivo.trim(), TIPO);

        log.info("Cantidad remito equipo={} modificada {} → {} — motivo: {}",
            equipoId, cantidadAnterior, cantidadNueva, motivo);
        return true;
    }

    // ── DETALLES: modificar cantidad de material ─────────────────────────────

    public boolean modificarCantidadMaterial(int equipoId, int materialId,
                                              int cantidadNueva, String motivo) {
        ValidationException.Builder v = ValidationException.builder();
        v.addErrorIf(cantidadNueva <= 0, "La cantidad debe ser mayor a 0")
         .addErrorIf(motivo == null || motivo.trim().isEmpty(), "El motivo es obligatorio");
        v.throwIfHasErrors();

        cargarYValidarNuevo(equipoId);

        Integer cantidadAnterior = equipoOtrosDAO.obtenerCantidadMaterial(materialId, equipoId);
        if (cantidadAnterior == null) {
            throw new ValidationException("El material no existe en el equipo");
        }

        int rows = equipoOtrosDAO.actualizarCantidadMaterial(equipoId, materialId, cantidadNueva);
        if (rows == 0) throw new ValidationException("Material no encontrado en el equipo");

        auditoriaDAO.registrarCambio(equipoId, materialId, "MODIFICACION_CANTIDAD",
            "cantidad",
            String.valueOf(cantidadAnterior),
            String.valueOf(cantidadNueva),
            motivo.trim(), TIPO);

        log.info("Cantidad material={} equipo={} modificada {} → {} — motivo: {}",
            materialId, equipoId, cantidadAnterior, cantidadNueva, motivo);
        return true;
    }

    // ── DETALLES: agregar material ───────────────────────────────────────────

    public boolean agregarMaterial(int equipoId, String descripcion, int cantidad, String motivo) {
        ValidationException.Builder v = ValidationException.builder();
        v.addErrorIf(descripcion == null || descripcion.trim().isEmpty(), "La descripción es obligatoria")
         .addErrorIf(cantidad <= 0, "La cantidad debe ser mayor a 0")
         .addErrorIf(motivo == null || motivo.trim().isEmpty(), "El motivo es obligatorio");
        v.throwIfHasErrors();

        cargarYValidarNuevo(equipoId);

        int nuevoMaterialId = equipoOtrosDAO.insertarMaterial(equipoId, descripcion.trim(), cantidad);

        auditoriaDAO.registrarCambio(equipoId, nuevoMaterialId, "ADICION_MATERIAL",
            "material_nuevo", null, String.valueOf(cantidad), motivo.trim(), TIPO);

        log.info("Material '{}' (cantidad={}) agregado al equipo {} — motivo: {}",
            descripcion, cantidad, equipoId, motivo);
        return true;
    }

    // ── DETALLES: eliminar material por descripción ──────────────────────────

    /** Elimina todas las filas del equipo con la descripción indicada y genera snapshots. */
    public boolean eliminarMaterial(int equipoId, String descripcion, String motivo) {
        ValidationException.Builder v = ValidationException.builder();
        v.addErrorIf(descripcion == null || descripcion.trim().isEmpty(), "La descripción es obligatoria")
         .addErrorIf(motivo == null || motivo.trim().isEmpty(), "El motivo es obligatorio");
        v.throwIfHasErrors();

        cargarYValidarNuevo(equipoId);

        List<MaterialOtros> materiales =
            equipoOtrosDAO.obtenerMaterialesPorDescripcion(equipoId, descripcion.trim());
        if (materiales.isEmpty()) {
            throw new ValidationException("No hay materiales con esa descripción en el equipo");
        }

        // Los snapshots se escriben ANTES del DELETE: después las filas ya no existen.
        for (MaterialOtros m : materiales) {
            auditoriaDAO.registrarMaterialEliminado(
                equipoId, m.getId(), null,
                m.getDescripcion(), m.getCantidad(),
                m.getEstado() != null ? m.getEstado().getNombre() : null,
                motivo.trim(), TIPO);
        }

        equipoOtrosDAO.eliminarMaterialesPorDescripcion(equipoId, descripcion.trim());

        auditoriaDAO.registrarCambio(equipoId, null, "ELIMINACION_MATERIAL",
            "material", null, null, motivo.trim(), TIPO);

        log.info("Material '{}' eliminado del equipo {} — motivo: {}", descripcion, equipoId, motivo);
        return true;
    }

    // ── Eliminar equipo ──────────────────────────────────────────────────────

    public boolean eliminarEquipo(int equipoId, String motivo) {
        ValidationException.Builder v = ValidationException.builder();
        v.addErrorIf(motivo == null || motivo.trim().isEmpty(), "El motivo es obligatorio");
        v.throwIfHasErrors();

        EquipoOtros equipo = cargarYValidarNuevo(equipoId);

        boolean snapEquipo = auditoriaDAO.registrarEquipoEliminado(
            equipo.getId(), equipo.getNroCliente(), equipo.getClienteNombre(),
            null, null, null, null,
            equipo.getEstado().getNombre(), motivo.trim(), TIPO);
        if (!snapEquipo) throw new DatabaseException("No se pudo registrar el snapshot del equipo eliminado");

        for (MaterialOtros m : equipo.getMateriales()) {
            boolean snapMat = auditoriaDAO.registrarMaterialEliminado(
                equipo.getId(), m.getId(), null,
                m.getDescripcion(), m.getCantidad(),
                m.getEstado() != null ? m.getEstado().getNombre() : null,
                motivo.trim(), TIPO);
            if (!snapMat) throw new DatabaseException("No se pudo registrar el snapshot del material eliminado");
        }

        equipoOtrosDAO.eliminarEquipo(equipoId);

        auditoriaDAO.registrarCambio(equipoId, null, "ELIMINACION_EQUIPO",
            "equipo", null, null, motivo.trim(), TIPO);

        log.info("EquipoOtros id={} eliminado — motivo: {}", equipoId, motivo);
        return true;
    }

    // ── Helpers privados ─────────────────────────────────────────────────────

    private EquipoOtros cargarYValidarNuevo(int equipoId) {
        EquipoOtros equipo = equipoOtrosDAO.obtenerPorId(equipoId);
        if (equipo == null) {
            throw new ValidationException("El equipo no existe");
        }
        if (!equipo.getEstado().equals(EstadoEquipo.NUEVO)) {
            throw new ValidationException("El equipo no está en estado 'Nuevo' y no puede ser modificado");
        }
        return equipo;
    }
}
