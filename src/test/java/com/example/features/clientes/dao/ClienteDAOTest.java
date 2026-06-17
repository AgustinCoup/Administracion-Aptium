package com.example.features.clientes.dao;

import com.example.AbstractDAOTest;
import com.example.common.exception.DatabaseException;
import com.example.common.exception.ResourceNotFoundException;
import com.example.features.clientes.model.Cliente;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClienteDAOTest extends AbstractDAOTest {

    private final ClienteDAO dao = new ClienteDAO();

    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM equipo_materiales WHERE equipo_id IN (SELECT id FROM equipos WHERE nro_cliente IN (SELECT id FROM clientes WHERE nombre LIKE 'TestCliente%'))");
        ejecutarSQL("DELETE FROM equipos WHERE nro_cliente IN (SELECT id FROM clientes WHERE nombre LIKE 'TestCliente%')");
        ejecutarSQL("DELETE FROM clientes WHERE nombre LIKE 'TestCliente%'");
    }

    // ── guardar ──────────────────────────────────────────────────────────────

    @Test
    void guardar_clienteNuevo_asignaIdYRetornaTrue() {
        Cliente c = new Cliente(0, "TestCliente Alpha");
        assertTrue(dao.guardar(c));
        assertTrue(c.getId() > 0, "El id debe ser asignado por la BD");
    }

    // ── obtenerPorId ─────────────────────────────────────────────────────────

    @Test
    void obtenerPorId_existente_retornaCliente() {
        Cliente c = new Cliente(0, "TestCliente Beta");
        dao.guardar(c);
        Cliente obtenido = dao.obtenerPorId(c.getId());
        assertEquals("TestCliente Beta", obtenido.getNombre());
        assertEquals(c.getId(), obtenido.getId());
    }

    @Test
    void obtenerPorId_noExistente_lanzaResourceNotFoundException() {
        assertThrows(ResourceNotFoundException.class, () -> dao.obtenerPorId(999999));
    }

    // ── obtenerTodos ─────────────────────────────────────────────────────────

    @Test
    void obtenerTodos_retornaListaConElementosSeed() {
        List<Cliente> lista = dao.obtenerTodos();
        assertNotNull(lista);
        assertFalse(lista.isEmpty());
    }

    // ── buscarPorNombre ───────────────────────────────────────────────────────

    @Test
    void buscarPorNombre_sinCoincidencias_retornaListaVacia() {
        List<Cliente> resultado = dao.buscarPorNombre("ZZZNOMBREIMPOSIBLE99999");
        assertTrue(resultado.isEmpty());
    }

    @Test
    void buscarPorNombre_coincidenciaParcial_encuentraElemento() {
        dao.guardar(new Cliente(0, "TestCliente Gamma"));
        List<Cliente> resultado = dao.buscarPorNombre("TestCliente");
        assertFalse(resultado.isEmpty());
        assertTrue(resultado.stream().anyMatch(c -> c.getNombre().equals("TestCliente Gamma")));
    }

    @Test
    void buscarPorNombre_caseInsensitive_encuentraElemento() {
        dao.guardar(new Cliente(0, "TestCliente Delta"));
        assertFalse(dao.buscarPorNombre("testcliente delta").isEmpty());
    }

    // ── contar ────────────────────────────────────────────────────────────────

    @Test
    void contar_retornaValorPositivo() {
        assertTrue(dao.contar() > 0);
    }

    @Test
    void contar_incrementaDespuesDeGuardar() {
        long antes = dao.contar();
        dao.guardar(new Cliente(0, "TestCliente Epsilon"));
        assertEquals(antes + 1, dao.contar());
    }

    // ── existe ────────────────────────────────────────────────────────────────

    @Test
    void existe_idExistente_retornaTrue() {
        Cliente c = new Cliente(0, "TestCliente Zeta");
        dao.guardar(c);
        assertTrue(dao.existe(c.getId()));
    }

    @Test
    void existe_idInexistente_retornaFalse() {
        assertFalse(dao.existe(999999));
    }

    // ── actualizar no soportado ───────────────────────────────────────────────

    @Test
    void actualizar_lanzaUnsupportedOperation() {
        assertThrows(UnsupportedOperationException.class,
            () -> dao.actualizar(new Cliente(1, "nombre")));
    }

    // ── eliminar ──────────────────────────────────────────────────────────────

    @Test
    void eliminar_sinReferencias_eliminaCliente() {
        Cliente c = new Cliente(0, "TestCliente Eliminar");
        dao.guardar(c);

        assertTrue(dao.eliminar(c.getId()));
        assertFalse(dao.existe(c.getId()));
    }

    @Test
    void eliminar_conReferencias_lanzaDatabaseException() throws SQLException {
        Cliente c = new Cliente(0, "TestCliente ConFK");
        dao.guardar(c);
        ejecutarSQL("INSERT INTO equipos (nro_cliente, nro_institucion, estado, requiere_lavado, requiere_empaque) VALUES (" + c.getId() + ", 1, 'Nuevo', 1, 1)");

        assertThrows(DatabaseException.class, () -> dao.eliminar(c.getId()));
    }
}
