package com.example.common.eliminacion;

import com.example.AbstractDAOTest;
import com.example.infrastructure.db.ConnectionPool;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Corre contra H2 porque lo que se prueba es la conversión de un {@link ResultSet} real, con los
 * tipos que devuelve el driver, y no de un mock que devolvería lo que el test quisiera.
 */
class SnapshotJsonTest extends AbstractDAOTest {

    private static final String SQL_FILAS =
        "SELECT * FROM (VALUES "
            + "(1, 'Toalla', TIMESTAMP '2026-09-29 10:15:00', CAST(NULL AS VARCHAR(10)), 2.50), "
            + "(2, 'Sábana', CAST(NULL AS TIMESTAMP),       'x',                       NULL)"
            + ") AS t(id, nombre, fecha, nulo, litros) ORDER BY id";

    @Test
    void resultSetAJsonArray_timestampEnIsoYNullComoJsonNull() throws SQLException {
        JSONArray filas;
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FILAS);
             ResultSet rs = ps.executeQuery()) {
            filas = SnapshotJson.filas(rs);
        }

        assertEquals(2, filas.length());
        JSONObject primera = filas.getJSONObject(0);
        assertEquals(1, primera.getInt("id"));
        assertEquals("Toalla", primera.getString("nombre"));
        assertEquals("2026-09-29T10:15:00", primera.getString("fecha"));
        assertTrue(primera.has("nulo"), "una columna NULL no se omite: queda como null explícito");
        assertSame(JSONObject.NULL, primera.get("nulo"));
        assertEquals(0, new java.math.BigDecimal("2.50").compareTo(primera.getBigDecimal("litros")));

        JSONObject segunda = filas.getJSONObject(1);
        assertSame(JSONObject.NULL, segunda.get("fecha"));
        assertSame(JSONObject.NULL, segunda.get("litros"));
    }

    /**
     * H2 devuelve los nombres de columna en mayúsculas y MySQL como están en el esquema: el
     * snapshot los normaliza a minúsculas para que un mismo ingreso se archive igual en los dos.
     */
    @Test
    void resultSetAJsonArray_nombresDeColumnaEnMinusculas() throws SQLException {
        JSONArray filas;
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FILAS);
             ResultSet rs = ps.executeQuery()) {
            filas = SnapshotJson.filas(rs);
        }

        assertEquals(java.util.Set.of("id", "nombre", "fecha", "nulo", "litros"),
            filas.getJSONObject(0).keySet());
    }

    /**
     * Los tipos que el snapshot recibe de las tablas reales además de los de arriba: fechas sin hora
     * ({@code fecha_lavado}), booleanos ({@code requiere_lavado}), {@code TEXT} (que H2 entrega como
     * {@code Clob}) y cualquier otro, que va como su {@code toString}.
     */
    @Test
    void filaActual_fechaBooleanoClobYOtroTipo() throws SQLException {
        String sql = "SELECT DATE '2026-09-29' AS dia, TRUE AS activo, "
            + "CAST('observación larga' AS CLOB) AS texto, "
            + "CAST('123e4567-e89b-12d3-a456-426614174000' AS UUID) AS otro";
        JSONObject fila;
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            fila = SnapshotJson.filaActual(rs);
        }

        assertEquals("2026-09-29", fila.getString("dia"));
        assertTrue(fila.getBoolean("activo"));
        assertEquals("observación larga", fila.getString("texto"));
        assertEquals("123e4567-e89b-12d3-a456-426614174000", fila.getString("otro"));
    }

    /**
     * H2 entrega {@code Timestamp} y {@code java.sql.Date}, pero Connector/J 8 puede entregar
     * {@code LocalDateTime} y {@code LocalDate}: se archivan con el mismo formato. Con un mock porque
     * H2 no produce esos tipos.
     */
    @Test
    void filaActual_tiposJavaTime_mismoFormatoQueLosDeJdbc() throws SQLException {
        java.sql.ResultSetMetaData meta = org.mockito.Mockito.mock(java.sql.ResultSetMetaData.class);
        org.mockito.Mockito.when(meta.getColumnCount()).thenReturn(2);
        org.mockito.Mockito.when(meta.getColumnLabel(1)).thenReturn("FECHA_FIN");
        org.mockito.Mockito.when(meta.getColumnLabel(2)).thenReturn("DIA");
        ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
        org.mockito.Mockito.when(rs.getMetaData()).thenReturn(meta);
        org.mockito.Mockito.when(rs.getObject(1)).thenReturn(java.time.LocalDateTime.of(2026, 9, 29, 10, 15));
        org.mockito.Mockito.when(rs.getObject(2)).thenReturn(java.time.LocalDate.of(2026, 9, 29));

        JSONObject fila = SnapshotJson.filaActual(rs);

        assertEquals("2026-09-29T10:15:00", fila.getString("fecha_fin"));
        assertEquals("2026-09-29", fila.getString("dia"));
    }

    @Test
    void resultSetVacio_arrayVacio() throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FILAS + " LIMIT 0");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(SnapshotJson.filas(rs).isEmpty());
        }
    }

    @Test
    void raiz_llevaModuloYFormato() {
        JSONObject raiz = SnapshotJson.raiz(ModuloIngreso.LAVADERO, 1);

        assertEquals("LAVADERO", raiz.getString("modulo"));
        assertEquals(1, raiz.getInt("formato"));
    }
}
