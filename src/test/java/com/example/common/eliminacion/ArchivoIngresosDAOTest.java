package com.example.common.eliminacion;

import com.example.AbstractDAOTest;
import com.example.infrastructure.db.ConnectionPool;
import com.example.infrastructure.db.TransactionalConnection;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ArchivoIngresosDAOTest extends AbstractDAOTest {

    private final ArchivoIngresosDAO dao = new ArchivoIngresosDAO();

    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM ingresos_eliminados");
    }

    private static IngresoArchivado archivado(ModuloIngreso modulo, int id, Integer padre) {
        return new IngresoArchivado(modulo, id, "Clínica Test", LocalDateTime.of(2026, 9, 1, 8, 30),
            "Esterilizado", "Se cargó dos veces", "operador@PC-1", padre,
            "{\"formato\":1,\"modulo\":\"" + modulo.name() + "\"}");
    }

    @Test
    void archivar_devuelveElIdYPersisteTodasLasColumnas() throws SQLException {
        int id;
        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            id = dao.archivar(tx.get(), archivado(ModuloIngreso.ORTOPEDIA, 42, null));
            tx.commit();
        }

        assertTrue(id > 0);
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM ingresos_eliminados WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("ORTOPEDIA", rs.getString("modulo"));
                assertEquals(42, rs.getInt("ingreso_id_original"));
                assertEquals("Clínica Test", rs.getString("cliente_nombre"));
                assertEquals(LocalDateTime.of(2026, 9, 1, 8, 30),
                    rs.getTimestamp("fecha_ingreso").toLocalDateTime());
                assertEquals("Esterilizado", rs.getString("estado"));
                assertEquals("Se cargó dos veces", rs.getString("motivo"));
                assertEquals("operador@PC-1", rs.getString("puesto"));
                rs.getInt("archivo_padre_id");
                assertTrue(rs.wasNull(), "sin padre: NULL, no 0");
                assertNotNull(rs.getTimestamp("fecha_eliminacion"), "la pone la base");
                assertEquals("{\"formato\":1,\"modulo\":\"ORTOPEDIA\"}", rs.getString("snapshot"));
            }
        }
    }

    /**
     * El archivo tiene que ser atómico con el borrado: si la transacción del llamador se revierte,
     * no puede quedar una copia de un ingreso que sigue existiendo.
     */
    @Test
    void archivar_usaLaConexionDelLlamador_rollbackNoDejaFila() throws SQLException {
        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            dao.archivar(tx.get(), archivado(ModuloIngreso.OTROS, 5, null));
            // sin commit: el close() revierte
        }

        assertEquals(0, contarFilas());
    }

    /** Y no cierra la conexión ajena: el llamador sigue usándola para el borrado. */
    @Test
    void archivar_noCierraLaConexionDelLlamador() throws SQLException {
        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            dao.archivar(tx.get(), archivado(ModuloIngreso.OTROS, 5, null));
            assertFalse(tx.get().isClosed());
            assertFalse(tx.get().getAutoCommit(), "no tocó el modo de la transacción");
            tx.commit();
        }
        assertEquals(1, contarFilas());
    }

    @Test
    void archivar_padreNull_yConPadre() throws SQLException {
        int padre;
        int hijo;
        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            padre = dao.archivar(tx.get(), archivado(ModuloIngreso.LAVADERO, 10, null));
            hijo  = dao.archivar(tx.get(), archivado(ModuloIngreso.OTROS, 99, padre));
            tx.commit();
        }

        assertNull(padreDe(padre));
        assertEquals(padre, padreDe(hijo));
    }

    @Test
    void archivar_camposOpcionalesNull() throws SQLException {
        IngresoArchivado sinOpcionales = new IngresoArchivado(ModuloIngreso.LAVADERO, 3, null, null,
            null, "motivo", null, null, "{}");
        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            assertTrue(dao.archivar(tx.get(), sinOpcionales) > 0);
            tx.commit();
        }
        assertEquals(1, contarFilas());
    }

    private Integer padreDe(int id) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT archivo_padre_id FROM ingresos_eliminados WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                int valor = rs.getInt(1);
                return rs.wasNull() ? null : valor;
            }
        }
    }

    private int contarFilas() throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM ingresos_eliminados");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
