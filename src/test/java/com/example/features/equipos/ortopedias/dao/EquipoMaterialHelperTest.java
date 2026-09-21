package com.example.features.equipos.ortopedias.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.AbstractDAOTest;
import com.example.infrastructure.db.ConnectionPool;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Qué fila sobrevive en {@link EquipoMaterialHelper#unificarMaterialesDuplicados}: la de movimiento
 * más reciente; si empatan, la de id mayor; sin movimientos cuenta como la más vieja.
 */
class EquipoMaterialHelperTest extends AbstractDAOTest {

    private static final int CODIGO = 400;

    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM material_movimientos");
        ejecutarSQL("DELETE FROM equipo_materiales");
        ejecutarSQL("DELETE FROM equipos");
    }

    @Test
    void sobreviveElDeMovimientoMasReciente_aunqueTengaIdMenor() throws Exception {
        int equipoId = insertarEquipo();
        int viejo  = insertarMaterial(equipoId, 2);
        int nuevo  = insertarMaterial(equipoId, 3);
        insertarMovimiento(viejo,  equipoId, "2026-09-10 12:00:00");
        insertarMovimiento(nuevo,  equipoId, "2026-09-01 12:00:00");

        unificar(equipoId);

        assertEquals(List.of(viejo), materiales(equipoId));
        assertEquals(5, cantidad(viejo));
    }

    @Test
    void conMovimientosEmpatados_sobreviveElDeIdMayor() throws Exception {
        int equipoId = insertarEquipo();
        int menor = insertarMaterial(equipoId, 2);
        int mayor = insertarMaterial(equipoId, 3);
        insertarMovimiento(menor, equipoId, "2026-09-10 12:00:00");
        insertarMovimiento(mayor, equipoId, "2026-09-10 12:00:00");

        unificar(equipoId);

        assertEquals(List.of(mayor), materiales(equipoId));
        assertEquals(5, cantidad(mayor));
    }

    @Test
    void sinMovimientos_pierdeContraCualquierMovimiento() throws Exception {
        // NULL va último en un ORDER BY … DESC, en MySQL y en H2: el id mayor no alcanza.
        int equipoId = insertarEquipo();
        int conMovimiento = insertarMaterial(equipoId, 2);
        insertarMaterial(equipoId, 3);   // id mayor, sin movimientos
        insertarMovimiento(conMovimiento, equipoId, "2026-01-01 00:00:00");

        unificar(equipoId);

        assertEquals(List.of(conMovimiento), materiales(equipoId));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static void unificar(int equipoId) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection()) {
            conn.setAutoCommit(false);
            EquipoMaterialHelper.unificarMaterialesDuplicados(conn, equipoId);
            conn.commit();
        }
    }

    private int insertarEquipo() throws SQLException {
        return insertar("INSERT INTO equipos (nro_cliente, nro_institucion, estado, requiere_lavado, "
            + "requiere_empaque) VALUES (1, 1, 'Nuevo', 1, 1)");
    }

    private int insertarMaterial(int equipoId, int cantidad) throws SQLException {
        return insertar("INSERT INTO equipo_materiales (equipo_id, codigo_catalogo, cantidad, estado) "
            + "VALUES (" + equipoId + ", " + CODIGO + ", " + cantidad + ", 'Lavado')");
    }

    private static void insertarMovimiento(int materialId, int equipoId, String fecha) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO material_movimientos (material_id, equipo_id, cantidad, estado_origen, "
                 + "estado_destino, fecha) VALUES (?, ?, 1, 'Lavando', 'Lavado', ?)")) {
            ps.setInt(1, materialId);
            ps.setInt(2, equipoId);
            ps.setTimestamp(3, Timestamp.valueOf(fecha));
            ps.executeUpdate();
        }
    }

    private static int insertar(String sql) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static List<Integer> materiales(int equipoId) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id FROM equipo_materiales WHERE equipo_id = ? ORDER BY id")) {
            ps.setInt(1, equipoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getInt(1));
            }
        }
        return ids;
    }

    private static int cantidad(int materialId) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT cantidad FROM equipo_materiales WHERE id = ?")) {
            ps.setInt(1, materialId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
