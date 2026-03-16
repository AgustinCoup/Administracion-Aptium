package com.example.features.equipos.otros.dao;

import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.MaterialOtros;
import com.example.features.catalogo.dao.CatalogoOtrosDAO;
import com.example.infrastructure.db.ConnectionPool;
import com.example.features.equipos.ortopedias.model.MovimientoMaterial;
import com.example.features.equipos.ortopedias.model.Equipo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para {@code equipo_otros} y {@code equipo_otros_materiales}.
 *
 * Refleja la estructura de {@link com.example.features.equipos.dao.EquipoDAO}
 * adaptada a las tablas de "otros".
 */
public class EquipoOtrosDAO {

    private static final Logger log = LoggerFactory.getLogger(EquipoOtrosDAO.class);

    private final CatalogoOtrosDAO catalogoOtrosDAO;

    public EquipoOtrosDAO(CatalogoOtrosDAO catalogoOtrosDAO) {
        this.catalogoOtrosDAO = catalogoOtrosDAO;
    }

    // ── Guardar ───────────────────────────────────────────────────────────────

    /**
     * Persiste un equipo con todos sus materiales en una única transacción.
     * Por cada material:
     * - Busca o crea la entrada en {@code catalogo_otros}.
     * - Inserta en {@code equipo_otros_materiales}.
     * - Registra el movimiento inicial en {@code otros_material_movimientos}.
     */
    public boolean guardar(EquipoOtros equipo) {
        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();
            conn.setAutoCommit(false);

            // 1. Insertar encabezado
            String sqlEquipo =
                "INSERT INTO equipo_otros (nro_cliente, estado, requiere_lavado, requiere_empaque) " +
                "VALUES (?, ?, ?, ?)";
            int equipoId;
            try (PreparedStatement ps = conn.prepareStatement(sqlEquipo, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, equipo.getNroCliente());
                ps.setString(2, equipo.getEstado().getNombre());
                ps.setBoolean(3, equipo.isRequiereLavado());
                ps.setBoolean(4, equipo.isRequiereEmpaque());
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

            // 2. Insertar materiales
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
                    // Obtener o crear entrada en catálogo
                    int catalogoId = catalogoOtrosDAO.obtenerOCrear(conn, mat.getDescripcion());
                    mat.setCatalogoOtrosId(catalogoId);

                    psMat.setInt(1, equipoId);
                    psMat.setInt(2, catalogoId);
                    psMat.setString(3, mat.getDescripcion());
                    psMat.setInt(4, mat.getCantidad());
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

                    psMov.setInt(1, materialId);
                    psMov.setInt(2, equipoId);
                    psMov.setInt(3, mat.getCantidad());
                    psMov.setNull(4, Types.VARCHAR);
                    psMov.setString(5, mat.getEstado().getNombre());
                    psMov.executeUpdate();
                }
            }

            conn.commit();
            log.info("EquipoOtros guardado: ID={}", equipoId);
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
            "eo.estado, eo.requiere_lavado, eo.requiere_empaque " +
            "FROM equipo_otros eo " +
            "JOIN clientes c ON eo.nro_cliente = c.id " +
            "ORDER BY eo.estado, eo.id DESC";

        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

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

    // ── Aplicar movimientos (split/merge) ─────────────────────────────────────

    /**
     * Persiste una lista de movimientos sobre materiales de un equipo "otros".
     * Replica la lógica de
     * {@link com.example.features.equipos.dao.MaterialDAO#aplicarMovimientos}
     * pero sobre las tablas de "otros".
     */
    public boolean aplicarMovimientos(int equipoId,
                                      List<MovimientoMaterial> movimientos) {
        if (movimientos == null || movimientos.isEmpty()) return true;

        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();
            conn.setAutoCommit(false);

            // Leer configuración del equipo
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
            String sqlUpdCant  = "UPDATE equipo_otros_materiales SET cantidad = ? WHERE id = ? AND equipo_otros_id = ?";
            String sqlUpdEst   = "UPDATE equipo_otros_materiales SET estado = ?   WHERE id = ? AND equipo_otros_id = ?";
            String sqlInsert   =
                "INSERT INTO equipo_otros_materiales " +
                "(equipo_otros_id, catalogo_otros_id, descripcion, cantidad, estado) " +
                "VALUES (?, ?, ?, ?, ?)";
            String sqlMov      =
                "INSERT INTO otros_material_movimientos " +
                "(material_id, equipo_otros_id, cantidad, estado_origen, estado_destino) " +
                "VALUES (?, ?, ?, ?, ?)";

            for (var mov : movimientos) {
                int matId          = mov.getMaterialId();
                int cantidadMover  = mov.getCantidad();
                EstadoEquipo dest  = mov.getEstadoDestino();

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
                        ps.setInt(2, matId);
                        ps.setInt(3, equipoId);
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
                        ps.setInt(1, equipoId);
                        ps.setInt(2, catalogoId);
                        ps.setString(3, descripcion);
                        ps.setInt(4, cantidadMover);
                        ps.setString(5, dest.getNombre());
                        ps.executeUpdate();
                        try (ResultSet rs = ps.getGeneratedKeys()) {
                            if (!rs.next()) throw new SQLException("Sin ID para nuevo material");
                            nuevoMatId = rs.getInt(1);
                        }
                    }
                    registrarMovimiento(conn, sqlMov, nuevoMatId, equipoId, cantidadMover, estadoActual, dest);
                }
            }

            // Recalcular y actualizar estado del equipo
            recalcularEstadoEquipo(conn, equipoId);

            conn.commit();
            return true;

        } catch (SQLException e) {
            rollback(conn, e);
            return false;
        } finally {
            close(conn);
        }
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    private EquipoOtros mapearEquipo(ResultSet rs) throws SQLException {
        EquipoOtros eq = new EquipoOtros();
        eq.setId(rs.getInt("id"));
        eq.setNroCliente(rs.getInt("nro_cliente"));
        eq.setClienteNombre(rs.getString("cliente_nombre"));
        eq.setEstado(EstadoEquipo.desdeBD(rs.getString("estado")));
        eq.setRequiereLavado(rs.getBoolean("requiere_lavado"));
        eq.setRequiereEmpaque(rs.getBoolean("requiere_empaque"));
        return eq;
    }

    private void cargarMateriales(Connection conn, EquipoOtros equipo) throws SQLException {
        String sql =
            "SELECT m.id, m.catalogo_otros_id, m.descripcion, m.cantidad, m.estado, " +
            "       mv.fecha AS ultimo_movimiento " +
            "FROM equipo_otros_materiales m " +
            "LEFT JOIN ( " +
            "    SELECT material_id, MAX(fecha) AS fecha " +
            "    FROM otros_material_movimientos GROUP BY material_id " +
            ") mv ON mv.material_id = m.id " +
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
            ps.setInt(2, equipoId);
            ps.executeUpdate();
        }
    }

    private void registrarMovimiento(Connection conn, String sql, int materialId, int equipoId,
                                     int cantidad, String estadoOrigen, EstadoEquipo destino)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, materialId);
            ps.setInt(2, equipoId);
            ps.setInt(3, cantidad);
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

    private void close(Connection conn) {
        if (conn != null) {
            try { conn.close(); } catch (SQLException e) {
                log.error("Error al cerrar conexión", e);
            }
        }
    }
}