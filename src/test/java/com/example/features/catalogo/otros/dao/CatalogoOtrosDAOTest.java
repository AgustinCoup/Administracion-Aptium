package com.example.features.catalogo.otros.dao;

import com.example.AbstractDAOTest;
import com.example.features.catalogo.dao.CatalogoOtrosDAO;
import com.example.infrastructure.db.ConnectionPool;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CatalogoOtrosDAOTest extends AbstractDAOTest {

    private final CatalogoOtrosDAO dao = new CatalogoOtrosDAO();

    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM catalogo_otros WHERE descripcion LIKE 'TestOtros%'");
    }

    // ── buscarPorDescripcionParcial ───────────────────────────────────────────

    @Test
    void buscar_textoNulo_retornaVacio() {
        assertTrue(dao.buscarPorDescripcionParcial(null).isEmpty());
    }

    @Test
    void buscar_textoVacio_retornaVacio() {
        assertTrue(dao.buscarPorDescripcionParcial("").isEmpty());
    }

    @Test
    void buscar_textoQueCoincide_retornaResultados() throws SQLException {
        ejecutarSQL("INSERT INTO catalogo_otros (descripcion) VALUES ('TestOtros Guante'), ('TestOtros Gasa')");

        List<String> resultado = dao.buscarPorDescripcionParcial("TestOtros G");
        assertEquals(2, resultado.size());
    }

    @Test
    void buscar_textoSinCoincidencias_retornaVacio() {
        assertTrue(dao.buscarPorDescripcionParcial("xyzNoExiste").isEmpty());
    }

    // ── obtenerIdPorDescripcion ───────────────────────────────────────────────

    @Test
    void obtenerIdPorDescripcion_nulo_retornaMinusUno() {
        assertEquals(-1, dao.obtenerIdPorDescripcion(null));
    }

    @Test
    void obtenerIdPorDescripcion_vacio_retornaMinusUno() {
        assertEquals(-1, dao.obtenerIdPorDescripcion(""));
    }

    @Test
    void obtenerIdPorDescripcion_noExistente_retornaMinusUno() {
        assertEquals(-1, dao.obtenerIdPorDescripcion("TestOtros NoExiste"));
    }

    @Test
    void obtenerIdPorDescripcion_existente_retornaIdPositivo() throws SQLException {
        ejecutarSQL("INSERT INTO catalogo_otros (descripcion) VALUES ('TestOtros Existente')");

        int id = dao.obtenerIdPorDescripcion("TestOtros Existente");
        assertTrue(id > 0);
    }

    // ── obtenerOCrear ─────────────────────────────────────────────────────────

    @Test
    void obtenerOCrear_nuevaDescripcion_insertaYRetornaId() throws Exception {
        try (Connection conn = ConnectionPool.getConnection()) {
            conn.setAutoCommit(false);
            int id = dao.obtenerOCrear(conn, "TestOtros NuevaDesc");
            conn.commit();
            assertTrue(id > 0);
        }
    }

    @Test
    void obtenerOCrear_descripcionExistente_retornaMismoId() throws Exception {
        ejecutarSQL("INSERT INTO catalogo_otros (descripcion) VALUES ('TestOtros Repetida')");
        int idOriginal = dao.obtenerIdPorDescripcion("TestOtros Repetida");

        try (Connection conn = ConnectionPool.getConnection()) {
            conn.setAutoCommit(false);
            int idSegundo = dao.obtenerOCrear(conn, "TestOtros Repetida");
            conn.commit();
            assertEquals(idOriginal, idSegundo);
        }
    }
}
