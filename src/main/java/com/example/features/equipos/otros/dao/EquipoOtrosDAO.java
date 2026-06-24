package com.example.features.equipos.otros.dao;

import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.MaterialOtros;
import com.example.features.equipos.otros.model.TipoIngresoOtros;
import com.example.features.catalogo.dao.CatalogoOtrosDAO;
import com.example.infrastructure.db.ConnectionPool;
import com.example.features.equipos.ortopedias.model.MovimientoMaterial;
import com.example.features.equipos.ortopedias.model.Equipo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para {@code equipo_otros} y {@code equipo_otros_materiales}.
 *
 * Refleja la estructura de {@link com.example.features.equipos.ortopedias.dao.EquipoDAO}
 * adaptada a las tablas de "otros".
 *
 * Modalidades de guardado:
 * <ul>
 *   <li>{@link TipoIngresoOtros#REMITO}: persiste encabezado con cantidad y observaciones;
 *       genera {@code remito_id} = ddmmaaaa-{id} tras obtener el PK autogenerado.
 *       No inserta filas en {@code equipo_otros_materiales}.</li>
 *   <li>{@link TipoIngresoOtros#DETALLES}: comportamiento original; inserta materiales
 *       y sus movimientos iniciales.</li>
 * </ul>
 */
public class EquipoOtrosDAO {

    private static final Logger log = LoggerFactory.getLogger(EquipoOtrosDAO.class);

    /** Formato del identificador de remito: día-mes-año. */
    private static final DateTimeFormatter FMT_REMITO = DateTimeFormatter.ofPattern("ddMMyyyy");

    private final CatalogoOtrosDAO catalogoOtrosDAO;

    public EquipoOtrosDAO(CatalogoOtrosDAO catalogoOtrosDAO) {
        this.catalogoOtrosDAO = catalogoOtrosDAO;
    }

    // ── Guardar ───────────────────────────────────────────────────────────────

    /**
     * Persiste un equipo "otros" en una única transacción.
     *
     * Para {@link TipoIngresoOtros#REMITO}:
     *   1. Inserta encabezado con cantidad y observaciones.
     *   2. Genera {@code remito_id} = ddmmaaaa-{equipoId} y actualiza el registro.
     *
     * Para {@link TipoIngresoOtros#DETALLES}:
     *   1. Inserta encabezado.
     *   2. Por cada material: busca o crea entrada en {@code catalogo_otros},
     *      inserta en {@code equipo_otros_materiales} y registra el movimiento inicial.
     */
    public boolean guardar(EquipoOtros equipo) {
        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();
            conn.setAutoCommit(false);

            // 1. Insertar encabezado
            String sqlEquipo =
                "INSERT INTO equipo_otros " +
                "(nro_cliente, estado, requiere_lavado, requiere_empaque, " +
                " tipo_ingreso, remito_cantidad, remito_observaciones) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

            int equipoId;
            try (PreparedStatement ps = conn.prepareStatement(sqlEquipo, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt   (1, equipo.getNroCliente());
                ps.setString(2, equipo.getEstado().getNombre());
                ps.setBoolean(3, equipo.isRequiereLavado());
                ps.setBoolean(4, equipo.isRequiereEmpaque());
                ps.setString(5, equipo.getTipoIngreso().getNombre());

                if (equipo.getTipoIngreso() == TipoIngresoOtros.REMITO) {
                    ps.setInt(6, equipo.getRemitoCantidad());
                    String obs = equipo.getRemitoObservaciones();
                    if (obs != null && !obs.isBlank()) ps.setString(7, obs);
                    else                               ps.setNull(7, Types.VARCHAR);
                } else {
                    ps.setNull(6, Types.INTEGER);
                    ps.setNull(7, Types.VARCHAR);
                }

                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        equipoId = rs.getInt(1);
                        equipo.setId(equipoId);
                    } else {
                        throw new SQLException("No se generó ID para equipo_otros");
                    }
                }
            }

            // 2a. Modo REMITO: generar y persistir remito_id
            if (equipo.getTipoIngreso() == TipoIngresoOtros.REMITO) {
                String fechaHoy = LocalDate.now().format(FMT_REMITO);
                int secuencial;
                try (PreparedStatement psCount = conn.prepareStatement(
                        "SELECT COUNT(*) FROM equipo_otros WHERE remito_id LIKE ?")) {
                    psCount.setString(1, fechaHoy + "-%");
                    try (ResultSet rsCount = psCount.executeQuery()) {
                        secuencial = rsCount.next() ? rsCount.getInt(1) + 1 : 1;
                    }
                }
                String remitoId = fechaHoy + "-" + secuencial;
                equipo.setRemitoId(remitoId);

                String sqlRem = "UPDATE equipo_otros SET remito_id = ? WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sqlRem)) {
                    ps.setString(1, remitoId);
                    ps.setInt   (2, equipoId);
                    ps.executeUpdate();
                }
                log.info("Remito generado: {}", remitoId);

            // 2b. Modo DETALLES: insertar materiales con sus movimientos
            } else {
                String sqlMat =
                    "INSERT INTO equipo_otros_materiales " +
                    "(equipo_otros_id, catalogo_otros_id, descripcion, cantidad, estado) " +
                    "VALUES (?, ?, ?, ?, ?)";
                String sqlMov =
                    "INSERT INTO otros_material_movimientos " +
                    "(material_id, equipo_otros_id, cantidad, estado_origen, estado_destino) " +
                    "VALUES (?, ?, ?, ?, ?)";

                try (PreparedStatement psMat = conn.prepareStatement(sqlMat, Statement.RETURN_GENERATED_KEYS);
                     PreparedStatement psMov = conn.prepareStatement(sqlMov)) {

                    for (MaterialOtros mat : equipo.getMateriales()) {
                        int catalogoId = catalogoOtrosDAO.obtenerOCrear(conn, mat.getDescripcion());
                        mat.setCatalogoOtrosId(catalogoId);

                        psMat.setInt   (1, equipoId);
                        psMat.setInt   (2, catalogoId);
                        psMat.setString(3, mat.getDescripcion());
                        psMat.setInt   (4, mat.getCantidad());
                        psMat.setString(5, mat.getEstado().getNombre());
                        psMat.executeUpdate();

                        int materialId;
                        try (ResultSet rsMat = psMat.getGeneratedKeys()) {
                            if (rsMat.next()) {
                                materialId = rsMat.getInt(1);
                                mat.setId(materialId);
                            } else {
                                throw new SQLException("No se generó ID para equipo_otros_materiales");
                            }
                        }

                        psMov.setInt   (1, materialId);
                        psMov.setInt   (2, equipoId);
                        psMov.setInt   (3, mat.getCantidad());
                        psMov.setNull  (4, Types.VARCHAR);
                        psMov.setString(5, mat.getEstado().getNombre());
                        psMov.executeUpdate();
                    }
                }
            }

            conn.commit();
            log.info("EquipoOtros guardado: ID={}, tipo={}", equipoId, equipo.getTipoIngreso());
            return true;

        } catch (SQLException e) {
            rollback(conn, e);
            return false;
        } finally {
            close(conn);
        }
    }

    // ── Lectura ───────────────────────────────────────────────────────────────

    /**
     * Retorna todos los equipos "otros" activos (no ENTREGADO), con sus materiales.
     */
    public List<EquipoOtros> obtenerTodos() {
        List<EquipoOtros> lista = new ArrayList<>();
        String sql =
            "SELECT eo.id, eo.nro_cliente, c.nombre AS cliente_nombre, " +
            "eo.estado, eo.requiere_lavado, eo.requiere_empaque, " +
            "eo.tipo_ingreso, eo.remito_id, eo.remito_cantidad, eo.remito_observaciones, " +
            "eo.volumen_equipo, eo.fecha_ingreso " +
            "FROM equipo_otros eo " +
            "JOIN clientes c ON eo.nro_cliente = c.id " +
            "ORDER BY eo.estado, eo.id DESC";

        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt  = conn.createStatement();
             ResultSet rs    = stmt.executeQuery(sql)) {

            while (rs.next()) {
                EquipoOtros eq = mapearEquipo(rs);
                cargarMateriales(conn, eq);
                lista.add(eq);
            }
        } catch (SQLException e) {
            log.error("Error al obtener todos los EquipoOtros", e);
        }
        return lista;
    }

    /** Retorna los equipos "otros" en estado "Nuevo" (editables para correcciones). */
    public List<EquipoOtros> obtenerEquiposNuevos() {
        List<EquipoOtros> lista = new ArrayList<>();
        String sql =
            "SELECT eo.id, eo.nro_cliente, c.nombre AS cliente_nombre, " +
            "eo.estado, eo.requiere_lavado, eo.requiere_empaque, " +
            "eo.tipo_ingreso, eo.remito_id, eo.remito_cantidad, eo.remito_observaciones, " +
            "eo.volumen_equipo, eo.fecha_ingreso " +
            "FROM equipo_otros eo " +
            "JOIN clientes c ON eo.nro_cliente = c.id " +
            "WHERE eo.estado = 'Nuevo' " +
            "ORDER BY eo.id DESC";

        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt  = conn.createStatement();
             ResultSet rs    = stmt.executeQuery(sql)) {

            while (rs.next()) {
                EquipoOtros eq = mapearEquipo(rs);
                cargarMateriales(conn, eq);
                lista.add(eq);
            }
        } catch (SQLException e) {
            log.error("Error al obtener EquipoOtros nuevos", e);
        }
        return lista;
    }

    /** Retorna un equipo_otros por id, con sus materiales cargados, o null si no existe. */
    public EquipoOtros obtenerPorId(int id) {
        String sql =
            "SELECT eo.id, eo.nro_cliente, c.nombre AS cliente_nombre, " +
            "eo.estado, eo.requiere_lavado, eo.requiere_empaque, " +
            "eo.tipo_ingreso, eo.remito_id, eo.remito_cantidad, eo.remito_observaciones, " +
            "eo.volumen_equipo, eo.fecha_ingreso " +
            "FROM equipo_otros eo " +
            "JOIN clientes c ON eo.nro_cliente = c.id " +
            "WHERE eo.id = ?";

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    EquipoOtros eq = mapearEquipo(rs);
                    cargarMateriales(conn, eq);
                    return eq;
                }
            }
        } catch (SQLException e) {
            log.error("Error al obtener EquipoOtros id={}", id, e);
        }
        return null;
    }

    // ── Entrega ───────────────────────────────────────────────────────────────

    /**
     * Marca como ENTREGADO todos los materiales esterilizados de equipos_otros
     * cuyo nro_cliente coincide. Para REMITO sin filas reales, actualiza
     * el estado del equipo directamente.
     */
    public boolean entregarClienteCompleto(int nroCliente) {
        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();
            conn.setAutoCommit(false);

            // IDs de equipos del cliente
            List<Integer> equipoIds = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM equipo_otros WHERE nro_cliente = ?")) {
                ps.setInt(1, nroCliente);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) equipoIds.add(rs.getInt("id"));
                }
            }

            String sqlMov =
                "INSERT INTO otros_material_movimientos " +
                "(material_id, equipo_otros_id, cantidad, estado_origen, estado_destino) " +
                "VALUES (?, ?, ?, ?, ?)";

            for (int equipoId : equipoIds) {
                // Materiales reales esterilizados
                List<int[]> mats = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, cantidad, estado FROM equipo_otros_materiales " +
                        "WHERE equipo_otros_id = ? AND estado = ? FOR UPDATE")) {
                    ps.setInt(1, equipoId);
                    ps.setString(2, EstadoEquipo.ESTERILIZADO.getNombre());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            mats.add(new int[]{rs.getInt("id"), rs.getInt("cantidad")});
                        }
                    }
                }
                for (int[] mat : mats) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE equipo_otros_materiales SET estado = ? WHERE id = ?")) {
                        ps.setString(1, EstadoEquipo.ENTREGADO.getNombre());
                        ps.setInt(2, mat[0]);
                        ps.executeUpdate();
                    }
                    registrarMovimiento(conn, sqlMov, mat[0], equipoId, mat[1],
                        EstadoEquipo.ESTERILIZADO.getNombre(), EstadoEquipo.ENTREGADO);
                }

                // REMITO sin filas reales: actualizar estado del equipo si está esterilizado
                if (mats.isEmpty()) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE equipo_otros SET estado = ? " +
                            "WHERE id = ? AND estado = ?")) {
                        ps.setString(1, EstadoEquipo.ENTREGADO.getNombre());
                        ps.setInt(2, equipoId);
                        ps.setString(3, EstadoEquipo.ESTERILIZADO.getNombre());
                        ps.executeUpdate();
                    }
                } else {
                    recalcularEstadoEquipo(conn, equipoId);
                }
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            rollback(conn, e);
            return false;
        } finally {
            close(conn);
        }
    }

    // ── Aplicar movimientos (split/merge) ─────────────────────────────────────

    /**
     * Persiste una lista de movimientos sobre materiales de un equipo "otros".
     * Replica la lógica de
     * {@link com.example.features.equipos.ortopedias.dao.MaterialDAO#aplicarMovimientos}
     * pero sobre las tablas de "otros".
     */
    public boolean aplicarMovimientos(int equipoId,
                                      List<MovimientoMaterial> movimientos) {
        if (movimientos == null || movimientos.isEmpty()) return true;

        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();
            conn.setAutoCommit(false);

            boolean requiereLavado  = true;
            boolean requiereEmpaque = true;
            String sqlCfg = "SELECT requiere_lavado, requiere_empaque FROM equipo_otros WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlCfg)) {
                ps.setInt(1, equipoId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        requiereLavado  = rs.getBoolean("requiere_lavado");
                        requiereEmpaque = rs.getBoolean("requiere_empaque");
                    }
                }
            }

            String sqlSelect =
                "SELECT catalogo_otros_id, descripcion, cantidad, estado " +
                "FROM equipo_otros_materiales WHERE id = ? AND equipo_otros_id = ? FOR UPDATE";
            String sqlUpdCant = "UPDATE equipo_otros_materiales SET cantidad = ? WHERE id = ? AND equipo_otros_id = ?";
            String sqlUpdEst  = "UPDATE equipo_otros_materiales SET estado = ?   WHERE id = ? AND equipo_otros_id = ?";
            String sqlInsert  =
                "INSERT INTO equipo_otros_materiales " +
                "(equipo_otros_id, catalogo_otros_id, descripcion, cantidad, estado) " +
                "VALUES (?, ?, ?, ?, ?)";
            String sqlMov =
                "INSERT INTO otros_material_movimientos " +
                "(material_id, equipo_otros_id, cantidad, estado_origen, estado_destino) " +
                "VALUES (?, ?, ?, ?, ?)";

            boolean anyDetalles = false;  // Bug 1: saber si hay movimientos sobre materiales reales

            for (var mov : movimientos) {
                int matId         = mov.getMaterialId();
                int cantidadMover = mov.getCantidad();
                EstadoEquipo dest = mov.getEstadoDestino();

                // REMITO (matId==0): crear filas reales en equipo_otros_materiales
                if (matId == 0) {
                    String estadoActual;
                    int remitoCant;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT estado, remito_cantidad, requiere_lavado, requiere_empaque " +
                            "FROM equipo_otros WHERE id = ?")) {
                        ps.setInt(1, equipoId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) throw new SQLException("equipo_otros no encontrado: " + equipoId);
                            estadoActual = rs.getString("estado");
                            remitoCant   = rs.getInt("remito_cantidad");
                            if (dest == null) {
                                dest = Equipo.calcularSiguienteEstado(
                                    EstadoEquipo.desdeBD(estadoActual),
                                    rs.getBoolean("requiere_lavado"),
                                    rs.getBoolean("requiere_empaque"));
                            }
                        }
                    }
                    if (dest == null) throw new SQLException("Estado final para REMITO: " + equipoId);

                    int catalogoId = catalogoOtrosDAO.obtenerOCrear(conn, "Elementos");

                    // Tras el primer split existen filas reales; usar su cantidad como fuente.
                    int disponibleMaterializado;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT COALESCE(SUM(cantidad), 0) FROM equipo_otros_materiales " +
                            "WHERE equipo_otros_id = ? AND estado = ? AND lote_id IS NULL")) {
                        ps.setInt(1, equipoId);
                        ps.setString(2, estadoActual);
                        try (ResultSet rs = ps.executeQuery()) {
                            disponibleMaterializado = rs.next() ? rs.getInt(1) : 0;
                        }
                    }

                    if (disponibleMaterializado > 0) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "DELETE FROM equipo_otros_materiales " +
                                "WHERE equipo_otros_id = ? AND estado = ? AND lote_id IS NULL")) {
                            ps.setInt(1, equipoId);
                            ps.setString(2, estadoActual);
                            ps.executeUpdate();
                        }
                        EquipoOtrosMaterialHelper.materializarRemitoSplit(
                                conn, equipoId, catalogoId, disponibleMaterializado,
                                estadoActual, cantidadMover, dest.getNombre(), null, null);
                    } else {
                        EquipoOtrosMaterialHelper.materializarRemitoSplit(
                                conn, equipoId, catalogoId, remitoCant,
                                estadoActual, cantidadMover, dest.getNombre(), null, null);
                    }
                    anyDetalles = true;
                    continue;
                }

                int    catalogoId;
                String descripcion;
                int    cantidadActual;
                String estadoActual;

                try (PreparedStatement ps = conn.prepareStatement(sqlSelect)) {
                    ps.setInt(1, matId);
                    ps.setInt(2, equipoId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Material no encontrado: " + matId);
                        catalogoId     = rs.getInt("catalogo_otros_id");
                        descripcion    = rs.getString("descripcion");
                        cantidadActual = rs.getInt("cantidad");
                        estadoActual   = rs.getString("estado");
                    }
                }

                anyDetalles = true;

                if (cantidadMover <= 0 || cantidadMover > cantidadActual)
                    throw new SQLException("Cantidad inválida para mover: " + matId);

                if (dest == null) {
                    dest = Equipo.calcularSiguienteEstado(
                            EstadoEquipo.desdeBD(estadoActual), requiereLavado, requiereEmpaque);
                }
                if (dest == null) throw new SQLException("Material en estado final: " + matId);

                if (cantidadMover == cantidadActual) {
                    try (PreparedStatement ps = conn.prepareStatement(sqlUpdEst)) {
                        ps.setString(1, dest.getNombre());
                        ps.setInt   (2, matId);
                        ps.setInt   (3, equipoId);
                        ps.executeUpdate();
                    }
                    registrarMovimiento(conn, sqlMov, matId, equipoId, cantidadMover, estadoActual, dest);
                } else {
                    try (PreparedStatement ps = conn.prepareStatement(sqlUpdCant)) {
                        ps.setInt(1, cantidadActual - cantidadMover);
                        ps.setInt(2, matId);
                        ps.setInt(3, equipoId);
                        ps.executeUpdate();
                    }
                    int nuevoMatId;
                    try (PreparedStatement ps = conn.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS)) {
                        ps.setInt   (1, equipoId);
                        ps.setInt   (2, catalogoId);
                        ps.setString(3, descripcion);
                        ps.setInt   (4, cantidadMover);
                        ps.setString(5, dest.getNombre());
                        ps.executeUpdate();
                        try (ResultSet rs = ps.getGeneratedKeys()) {
                            if (!rs.next()) throw new SQLException("No se generó ID para nuevo material");
                            nuevoMatId = rs.getInt(1);
                        }
                    }
                    registrarMovimiento(conn, sqlMov, nuevoMatId, equipoId, cantidadMover, estadoActual, dest);
                }
            }

            if (anyDetalles) {
                unificarMaterialesDuplicados(conn, equipoId);  // Bug 2
                recalcularEstadoEquipo(conn, equipoId);        // Bug 1: solo si hay materiales reales
            }
            conn.commit();
            return true;

        } catch (SQLException e) {
            rollback(conn, e);
            return false;
        } finally {
            close(conn);
        }
    }

    // ── Mapeo ─────────────────────────────────────────────────────────────────

    private EquipoOtros mapearEquipo(ResultSet rs) throws SQLException {
        EquipoOtros eq = new EquipoOtros();
        eq.setId(rs.getInt("id"));
        eq.setNroCliente(rs.getInt("nro_cliente"));
        eq.setClienteNombre(rs.getString("cliente_nombre"));
        eq.setEstado(EstadoEquipo.desdeBD(rs.getString("estado")));
        eq.setRequiereLavado(rs.getBoolean("requiere_lavado"));
        eq.setRequiereEmpaque(rs.getBoolean("requiere_empaque"));
        eq.setTipoIngreso(TipoIngresoOtros.desdeBD(rs.getString("tipo_ingreso")));
        eq.setRemitoId(rs.getString("remito_id"));
        eq.setRemitoCantidad(rs.getObject("remito_cantidad") != null ? rs.getInt("remito_cantidad") : null);
        eq.setRemitoObservaciones(rs.getString("remito_observaciones"));
        eq.setVolumenEquipo(rs.getInt("volumen_equipo"));
        Timestamp fi = rs.getTimestamp("fecha_ingreso");
        eq.setFechaIngreso(fi != null ? fi.toLocalDateTime() : null);
        return eq;
    }

    private void cargarMateriales(Connection conn, EquipoOtros equipo) throws SQLException {
        String sql =
            "SELECT m.id, m.catalogo_otros_id, m.descripcion, m.cantidad, m.estado, " +
            "       mv.fecha AS ultimo_movimiento, l.id_negocio AS lote_id_negocio " +
            "FROM equipo_otros_materiales m " +
            "LEFT JOIN ( " +
            "    SELECT material_id, MAX(fecha) AS fecha " +
            "    FROM otros_material_movimientos GROUP BY material_id " +
            ") mv ON mv.material_id = m.id " +
            "LEFT JOIN lotes l ON m.lote_id = l.id " +
            "WHERE m.equipo_otros_id = ? " +
            "ORDER BY m.id";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, equipo.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("ultimo_movimiento");
                    MaterialOtros mat = new MaterialOtros(
                        rs.getInt("id"),
                        rs.getObject("catalogo_otros_id") != null ? rs.getInt("catalogo_otros_id") : null,
                        rs.getString("descripcion"),
                        rs.getInt("cantidad"),
                        EstadoEquipo.desdeBD(rs.getString("estado")),
                        ts != null ? ts.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime() : null
                    );
                    mat.setLoteIdNegocio(rs.getString("lote_id_negocio"));
                    equipo.agregarMaterial(mat);
                }
            }
        }
    }

    private void recalcularEstadoEquipo(Connection conn, int equipoId) throws SQLException {
        String sqlCalc =
            "SELECT MIN(CASE " +
            "  WHEN estado='Nuevo'         THEN 1 " +
            "  WHEN estado='Lavando'       THEN 2 " +
            "  WHEN estado='Lavado'        THEN 3 " +
            "  WHEN estado='Empaquetado'   THEN 4 " +
            "  WHEN estado='Esterilizando' THEN 5 " +
            "  WHEN estado='Esterilizado'  THEN 6 " +
            "  WHEN estado='Entregado'     THEN 7 " +
            "  ELSE 1 END) AS orden_minimo " +
            "FROM equipo_otros_materiales WHERE equipo_otros_id = ?";

        EstadoEquipo nuevoEstado = EstadoEquipo.NUEVO;
        try (PreparedStatement ps = conn.prepareStatement(sqlCalc)) {
            ps.setInt(1, equipoId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int orden = rs.getInt("orden_minimo");
                    for (EstadoEquipo e : EstadoEquipo.values()) {
                        if (e.getOrden() == orden) { nuevoEstado = e; break; }
                    }
                }
            }
        }

        String sqlUpd = "UPDATE equipo_otros SET estado = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sqlUpd)) {
            ps.setString(1, nuevoEstado.getNombre());
            ps.setInt   (2, equipoId);
            ps.executeUpdate();
        }
    }

    /**
     * Unifica filas de {@code equipo_otros_materiales} con la misma
     * {@code (equipo_otros_id, descripcion, estado)}.
     * Delega en {@link EquipoOtrosMaterialHelper#unificarMaterialesDuplicados}.
     */
    private void unificarMaterialesDuplicados(Connection conn, int equipoId) throws SQLException {
        EquipoOtrosMaterialHelper.unificarMaterialesDuplicados(conn, equipoId);
    }

    private void registrarMovimiento(Connection conn, String sql, int materialId, int equipoId,
                                     int cantidad, String estadoOrigen, EstadoEquipo destino)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt   (1, materialId);
            ps.setInt   (2, equipoId);
            ps.setInt   (3, cantidad);
            ps.setString(4, estadoOrigen);
            ps.setString(5, destino.getNombre());
            ps.executeUpdate();
        }
    }

    private void rollback(Connection conn, Exception e) {
        log.error("Error en EquipoOtrosDAO — haciendo rollback", e);
        if (conn != null) {
            try { conn.rollback(); } catch (SQLException ex) {
                log.error("Error en rollback", ex);
            }
        }
    }

    /** Retorna todos los equipos "otros" con fecha_ingreso dentro del rango [desde, hasta], todos los estados. */
    public List<EquipoOtros> obtenerEntreFechas(LocalDate desde, LocalDate hasta, Integer clienteId) {
        List<EquipoOtros> lista = new ArrayList<>();
        String where = "WHERE eo.fecha_ingreso >= ? AND eo.fecha_ingreso <= ?";
        if (clienteId != null) where += " AND eo.nro_cliente = ?";
        String sql =
            "SELECT eo.id, eo.nro_cliente, c.nombre AS cliente_nombre, " +
            "eo.estado, eo.requiere_lavado, eo.requiere_empaque, " +
            "eo.tipo_ingreso, eo.remito_id, eo.remito_cantidad, eo.remito_observaciones, " +
            "eo.volumen_equipo, eo.fecha_ingreso " +
            "FROM equipo_otros eo " +
            "JOIN clientes c ON eo.nro_cliente = c.id " +
            where + " ORDER BY eo.fecha_ingreso, eo.id";

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(desde.atStartOfDay()));
            ps.setTimestamp(2, Timestamp.valueOf(hasta.atTime(23, 59, 59)));
            if (clienteId != null) ps.setInt(3, clienteId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    EquipoOtros eq = mapearEquipo(rs);
                    cargarMateriales(conn, eq);
                    lista.add(eq);
                }
            }
        } catch (SQLException e) {
            log.error("Error al obtener EquipoOtros entre fechas", e);
        }
        return lista;
    }

    private void close(Connection conn) {
        if (conn != null) {
            try { conn.close(); } catch (SQLException e) {
                log.error("Error al cerrar conexión", e);
            }
        }
    }
}