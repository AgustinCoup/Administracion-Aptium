package com.example.features.equipos.otros.service;

import com.example.common.exception.ValidationException;
import com.example.common.paginacion.CriteriosPagina;
import com.example.common.paginacion.Pagina;
import com.example.features.equipos.model.FiltroEquipos;
import com.example.features.equipos.ortopedias.model.MovimientoMaterial;
import com.example.features.equipos.otros.dao.EquipoOtrosDAO;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.TipoIngresoOtros;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Servicio de negocio para equipos "otros".
 * Delega persistencia en {@link EquipoOtrosDAO}.
 *
 * Validaciones por modalidad:
 * <ul>
 *   <li>{@link TipoIngresoOtros#REMITO}   – requiere {@code remitoCantidad} > 0.
 *       No exige materiales detallados.</li>
 *   <li>{@link TipoIngresoOtros#DETALLES} – requiere al menos un material en la lista.</li>
 * </ul>
 */
public class EquipoOtrosService {

    private final EquipoOtrosDAO dao;

    public EquipoOtrosService(EquipoOtrosDAO dao) {
        if (dao == null) throw new IllegalArgumentException("EquipoOtrosDAO no puede ser nulo");
        this.dao = dao;
    }

    /**
     * Valida y persiste un equipo "otros".
     *
     * @throws ValidationException si faltan campos obligatorios según la modalidad
     */
    public boolean guardarEquipo(EquipoOtros equipo) {
        List<String> errores = new ArrayList<>();

        if (equipo.getNroCliente() <= 0) {
            errores.add("El campo Cliente es obligatorio.");
        }

        if (equipo.getTipoIngreso() == TipoIngresoOtros.REMITO) {
            if (equipo.getRemitoCantidad() == null || equipo.getRemitoCantidad() <= 0) {
                errores.add("La cantidad del remito debe ser un número mayor a cero.");
            }
        } else {
            // DETALLES
            if (equipo.getMateriales().isEmpty()) {
                errores.add("Debe agregar al menos un material.");
            }
        }

        if (!errores.isEmpty()) {
            throw new ValidationException(errores);
        }

        return dao.guardar(equipo);
    }

    /**
     * Retorna todos los equipos "otros" (sin filtrar por estado).
     */
    public List<EquipoOtros> obtenerTodos() {
        return dao.obtenerTodos();
    }

    /**
     * Retorna la cola activa: los equipos "otros" con algo sin entregar.
     *
     * <p>Contraparte de {@link #obtenerTodos()} para las pantallas operativas,
     * que se refrescan en cada guardado y no necesitan el histórico.
     */
    public List<EquipoOtros> obtenerActivos() {
        return dao.obtenerActivos();
    }

    /**
     * Una página de equipos "otros" con sus materiales, con los filtros y el orden resueltos en
     * SQL. La consume la grilla de "otros" de Ver Equipos.
     *
     * <p>Valida y delega: cero JDBC acá (regla del repo). Ver
     * {@link EquipoOtrosDAO#obtenerPagina(FiltroEquipos, CriteriosPagina)} para por qué son dos
     * viajes, y {@link FiltroEquipos} para qué campos del filtro <b>no</b> aplican acá y por qué no
     * se "arregla".
     */
    public Pagina<EquipoOtros> obtenerPagina(FiltroEquipos filtro, CriteriosPagina criterios) {
        exigirCriterios(filtro, criterios);
        return dao.obtenerPagina(filtro, criterios);
    }

    /**
     * La misma página con el total ya sabido, para cuando sólo cambió el número de página y no los
     * filtros: así no se repite el {@code COUNT(*)}.
     */
    public Pagina<EquipoOtros> obtenerPagina(FiltroEquipos filtro, CriteriosPagina criterios,
                                             long totalConocido) {
        exigirCriterios(filtro, criterios);
        return dao.obtenerPagina(filtro, criterios, totalConocido);
    }

    /** Cuántos equipos "otros" matchean el filtro. Se pide sólo cuando cambian los filtros. */
    public long contar(FiltroEquipos filtro) {
        Objects.requireNonNull(filtro, "filtro");
        return dao.contar(filtro);
    }

    private static void exigirCriterios(FiltroEquipos filtro, CriteriosPagina criterios) {
        Objects.requireNonNull(filtro, "filtro");
        Objects.requireNonNull(criterios, "criterios");
    }

    public EquipoOtros obtenerPorId(int id) {
        return dao.obtenerPorId(id);
    }

    /**
     * Marca como entregados todos los materiales esterilizados de equipos_otros
     * cuyo nro_cliente coincide.
     */
    public boolean entregarClienteCompleto(int nroCliente) {
        return dao.entregarClienteCompleto(nroCliente);
    }

    /**
     * Aplica movimientos de subcantidades sobre los materiales de un equipo.
     * Llamado por {@link com.example.features.equipos.common.controller.RegistrarEstadoController}
     * al confirmar cambios pendientes de tipo OTROS.
     */
    public boolean aplicarMovimientos(int equipoId, List<MovimientoMaterial> movimientos) {
        return dao.aplicarMovimientos(equipoId, movimientos);
    }

    public List<EquipoOtros> obtenerEntreFechas(LocalDate desde, LocalDate hasta, Integer clienteId) {
        return dao.obtenerEntreFechas(desde, hasta, clienteId);
    }
}