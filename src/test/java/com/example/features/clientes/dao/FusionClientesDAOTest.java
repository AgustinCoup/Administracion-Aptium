package com.example.features.clientes.dao;

import com.example.AbstractDAOTest;
import com.example.common.exception.ConflictoConcurrenciaException;
import com.example.features.clientes.model.Cliente;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FusionClientesDAOTest extends AbstractDAOTest {

    private final ClienteDAO clienteDAO = new ClienteDAO();
    private final FusionClientesDAO dao = new FusionClientesDAO();

    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM bolsas_lavadero WHERE ingreso_id IN (SELECT id FROM ingresos_lavadero WHERE cliente_id IN (SELECT id FROM clientes WHERE nombre LIKE 'TestFusion%'))");
        ejecutarSQL("DELETE FROM ingresos_lavadero WHERE cliente_id IN (SELECT id FROM clientes WHERE nombre LIKE 'TestFusion%')");
        ejecutarSQL("DELETE FROM equipo_otros_materiales WHERE equipo_otros_id IN (SELECT id FROM equipo_otros WHERE nro_cliente IN (SELECT id FROM clientes WHERE nombre LIKE 'TestFusion%'))");
        ejecutarSQL("DELETE FROM equipo_otros WHERE nro_cliente IN (SELECT id FROM clientes WHERE nombre LIKE 'TestFusion%')");
        ejecutarSQL("DELETE FROM equipo_materiales WHERE equipo_id IN (SELECT id FROM equipos WHERE nro_cliente IN (SELECT id FROM clientes WHERE nombre LIKE 'TestFusion%'))");
        ejecutarSQL("DELETE FROM equipos WHERE nro_cliente IN (SELECT id FROM clientes WHERE nombre LIKE 'TestFusion%')");
        ejecutarSQL("DELETE FROM clientes WHERE nombre LIKE 'TestFusion%'");
    }

    @Test
    void fusionar_actualizaEquiposYEliminaOrigen() throws SQLException {
        Cliente origen = new Cliente(0, "TestFusion Origen");
        Cliente destino = new Cliente(0, "TestFusion Destino");
        clienteDAO.guardar(origen);
        clienteDAO.guardar(destino);
        ejecutarSQL("INSERT INTO equipos (nro_cliente, nro_institucion, estado, requiere_lavado, requiere_empaque) VALUES (" + origen.getId() + ", 1, 'Nuevo', 1, 1)");

        dao.fusionar(origen.getId(), origen.getNombre(), destino.getId(), destino.getNombre());

        assertFalse(clienteDAO.existe(origen.getId()), "El cliente origen debe haber sido eliminado");
        assertTrue(clienteDAO.existe(destino.getId()), "El cliente destino debe seguir existiendo");
        assertEquals(1, contarEquiposConCliente(destino.getId()), "El equipo debe apuntar al cliente destino");
    }

    @Test
    void fusionar_actualizaEquipoOtrosYEliminaOrigen() throws SQLException {
        Cliente origen = new Cliente(0, "TestFusion OrigenOtros");
        Cliente destino = new Cliente(0, "TestFusion DestinoOtros");
        clienteDAO.guardar(origen);
        clienteDAO.guardar(destino);
        ejecutarSQL("INSERT INTO equipo_otros (nro_cliente, estado, requiere_lavado, requiere_empaque, tipo_ingreso, volumen_equipo) VALUES (" + origen.getId() + ", 'Nuevo', 1, 1, 'DETALLES', 0)");

        dao.fusionar(origen.getId(), origen.getNombre(), destino.getId(), destino.getNombre());

        assertFalse(clienteDAO.existe(origen.getId()));
        assertEquals(1, contarEquiposOtrosConCliente(destino.getId()), "El equipo_otros debe apuntar al cliente destino");
    }

    /**
     * La tercera tabla con FK a {@code clientes}, que llegó con la rama de Lavadero (V7). Sin
     * moverla, el {@code ON DELETE RESTRICT} hace fallar el {@code DELETE FROM clientes} y
     * fusionar cualquier cliente que alguna vez mandó ropa se vuelve imposible, con un
     * {@code DatabaseException} que el operador no puede accionar.
     */
    @Test
    void fusionar_actualizaIngresosDeLavaderoYEliminaOrigen() throws SQLException {
        Cliente origen = new Cliente(0, "TestFusion OrigenLavadero");
        Cliente destino = new Cliente(0, "TestFusion DestinoLavadero");
        clienteDAO.guardar(origen);
        clienteDAO.guardar(destino);
        ejecutarSQL("INSERT INTO ingresos_lavadero (cliente_id, fecha_ingreso, peso_total_kg, estado) "
            + "VALUES (" + origen.getId() + ", NOW(), 8.00, 'PENDIENTE')");

        dao.fusionar(origen.getId(), origen.getNombre(), destino.getId(), destino.getNombre());

        assertFalse(clienteDAO.existe(origen.getId()));
        assertEquals(1, contarConCliente("ingresos_lavadero", "cliente_id", destino.getId()),
            "el ingreso de lavadero debe apuntar al cliente destino");
    }

    @Test
    void fusionar_sinReferencias_soloEliminaOrigen() {
        Cliente origen = new Cliente(0, "TestFusion SinRefs");
        Cliente destino = new Cliente(0, "TestFusion DestinoSinRefs");
        clienteDAO.guardar(origen);
        clienteDAO.guardar(destino);

        dao.fusionar(origen.getId(), origen.getNombre(), destino.getId(), destino.getNombre());

        assertFalse(clienteDAO.existe(origen.getId()));
        assertTrue(clienteDAO.existe(destino.getId()));
    }

    @Test
    void fusionar_nombreOrigenDesactualizado_abortaYNoMueveNada() throws SQLException {
        Cliente origen = new Cliente(0, "TestFusion NombreViejo");
        Cliente destino = new Cliente(0, "TestFusion DestinoNombreViejo");
        clienteDAO.guardar(origen);
        clienteDAO.guardar(destino);
        ejecutarSQL("INSERT INTO equipos (nro_cliente, nro_institucion, estado, requiere_lavado, requiere_empaque) VALUES (" + origen.getId() + ", 1, 'Nuevo', 1, 1)");

        assertThrows(ConflictoConcurrenciaException.class,
            () -> dao.fusionar(origen.getId(), "TestFusion NombreQueYaNoEs", destino.getId(), destino.getNombre()));

        assertTrue(clienteDAO.existe(origen.getId()), "El cliente origen no debe borrarse si el conflicto abortó la fusión");
        assertEquals(1, contarEquiposConCliente(origen.getId()), "Los equipos no deben haberse movido");
    }

    @Test
    void fusionar_nombreDestinoDesactualizado_abortaYNoEliminaOrigen() {
        Cliente origen = new Cliente(0, "TestFusion NombreDestOK");
        Cliente destino = new Cliente(0, "TestFusion NombreDestViejo");
        clienteDAO.guardar(origen);
        clienteDAO.guardar(destino);

        assertThrows(ConflictoConcurrenciaException.class,
            () -> dao.fusionar(origen.getId(), origen.getNombre(), destino.getId(), "TestFusion NombreDestQueYaNoEs"));

        assertTrue(clienteDAO.existe(origen.getId()));
    }

    @Test
    void fusionar_actualizaVersionDeLosEquiposMovidos() throws SQLException {
        Cliente origen = new Cliente(0, "TestFusion VersionOrigen");
        Cliente destino = new Cliente(0, "TestFusion VersionDestino");
        clienteDAO.guardar(origen);
        clienteDAO.guardar(destino);
        ejecutarSQL("INSERT INTO equipos (nro_cliente, nro_institucion, estado, requiere_lavado, requiere_empaque) VALUES (" + origen.getId() + ", 1, 'Nuevo', 1, 1)");

        dao.fusionar(origen.getId(), origen.getNombre(), destino.getId(), destino.getNombre());

        assertEquals(1, obtenerVersionDeEquipoConCliente(destino.getId()), "La version del equipo movido debe haber subido");
    }

    private int obtenerVersionDeEquipoConCliente(int clienteId) throws SQLException {
        try (Connection conn = com.example.infrastructure.db.ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT version FROM equipos WHERE nro_cliente = ?")) {
            ps.setInt(1, clienteId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt("version");
            }
        }
    }

    /**
     * El test que existe para que esto no vuelva a pasar por omisión.
     *
     * <p>La FK de {@code ingresos_lavadero} llegó con la rama de Lavadero y {@code FusionClientesDAO}
     * no se enteró: como todas las FK a {@code clientes} son {@code ON DELETE RESTRICT}, fusionar un
     * cliente que alguna vez mandó ropa fallaba entero. El bug no fue escribir algo mal, fue
     * <b>no escribir nada</b>, y contra eso ningún test de caso sirve: hay que preguntarle al
     * esquema.</p>
     *
     * <p>Se lee del {@code INFORMATION_SCHEMA} de H2, que Flyway acaba de construir con las mismas
     * migraciones que corren en producción. Si alguien agrega una tabla con FK a {@code clientes} y
     * no la suma a {@code REFERENCIAS}, este test lo dice con el nombre de la tabla.</p>
     */
    @Test
    void todaTablaConFkAClientes_estaContempladaEnLaFusion() throws SQLException {
        Map<String, String> enElEsquema = tablasConFkAClientes();
        assertFalse(enElEsquema.isEmpty(), "la consulta al INFORMATION_SCHEMA no encontró ni una FK: "
            + "si el esquema cambió de motor o de nombres, este test dejó de proteger nada");

        Map<String, String> enLaFusion = new HashMap<>();
        FusionClientesDAO.tablasQueLaFusionMueve()
            .forEach((tabla, columna) -> enLaFusion.put(tabla.toUpperCase(), columna.toUpperCase()));

        assertEquals(enElEsquema, enLaFusion,
            "toda tabla con FK a clientes tiene que estar en FusionClientesDAO.REFERENCIAS: la FK es "
            + "ON DELETE RESTRICT, así que la que falte hace fallar la fusión entera");
    }

    /** {@code TABLA → columna de FK} de todo lo que apunta a {@code clientes}, según el esquema. */
    private Map<String, String> tablasConFkAClientes() throws SQLException {
        String sql =
            "SELECT kcu.TABLE_NAME AS tabla, kcu.COLUMN_NAME AS columna "
            + "FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS rc "
            + "JOIN INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc "
            + "  ON tc.CONSTRAINT_NAME = rc.UNIQUE_CONSTRAINT_NAME "
            + " AND tc.CONSTRAINT_SCHEMA = rc.UNIQUE_CONSTRAINT_SCHEMA "
            + "JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu "
            + "  ON kcu.CONSTRAINT_NAME = rc.CONSTRAINT_NAME "
            + " AND kcu.CONSTRAINT_SCHEMA = rc.CONSTRAINT_SCHEMA "
            + "WHERE tc.TABLE_NAME = 'CLIENTES'";
        Map<String, String> porTabla = new HashMap<>();
        try (Connection conn = com.example.infrastructure.db.ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                porTabla.put(rs.getString("tabla"), rs.getString("columna"));
            }
        }
        return porTabla;
    }

    /** Tabla y columna son literales de este test, no entrada de usuario. */
    private long contarConCliente(String tabla, String columna, int clienteId) throws SQLException {
        try (Connection conn = com.example.infrastructure.db.ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COUNT(*) FROM " + tabla + " WHERE " + columna + " = ?")) {
            ps.setInt(1, clienteId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private long contarEquiposConCliente(int clienteId) throws SQLException {
        return contarConCliente("equipos", "nro_cliente", clienteId);
    }

    private long contarEquiposOtrosConCliente(int clienteId) throws SQLException {
        return contarConCliente("equipo_otros", "nro_cliente", clienteId);
    }
}
