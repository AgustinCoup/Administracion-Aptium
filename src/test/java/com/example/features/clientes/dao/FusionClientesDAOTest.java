package com.example.features.clientes.dao;

import com.example.AbstractDAOTest;
import com.example.common.exception.ConflictoConcurrenciaException;
import com.example.features.clientes.model.Cliente;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class FusionClientesDAOTest extends AbstractDAOTest {

    private final ClienteDAO clienteDAO = new ClienteDAO();
    private final FusionClientesDAO dao = new FusionClientesDAO();

    @Override
    protected void limpiarTablas() throws SQLException {
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

    private long contarEquiposConCliente(int clienteId) throws SQLException {
        try (Connection conn = com.example.infrastructure.db.ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM equipos WHERE nro_cliente = ?")) {
            ps.setInt(1, clienteId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private long contarEquiposOtrosConCliente(int clienteId) throws SQLException {
        try (Connection conn = com.example.infrastructure.db.ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM equipo_otros WHERE nro_cliente = ?")) {
            ps.setInt(1, clienteId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }
}
