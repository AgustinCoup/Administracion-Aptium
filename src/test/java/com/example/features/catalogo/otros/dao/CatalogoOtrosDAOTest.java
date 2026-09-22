package com.example.features.catalogo.otros.dao;

import com.example.AbstractDAOTest;
import com.example.common.exception.BusinessException;
import com.example.common.exception.ConflictoConcurrenciaException;
import com.example.features.catalogo.dao.CatalogoOtrosDAO;
import com.example.features.catalogo.model.ItemCatalogo;
import com.example.infrastructure.db.ConnectionPool;
import org.junit.jupiter.api.DisplayName;
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

    @Test
    @DisplayName("obtenerOCrear rechaza una descripción dada de baja y no la re-crea")
    void obtenerOCrear_descripcionDeBaja_rechazaYNoLaReactiva() throws Exception {
        ejecutarSQL("INSERT INTO catalogo_otros (descripcion, activo) VALUES ('TestOtros DeBaja', FALSE)");

        try (Connection conn = ConnectionPool.getConnection()) {
            conn.setAutoCommit(false);
            BusinessException e = assertThrows(BusinessException.class,
                () -> dao.obtenerOCrear(conn, "TestOtros DeBaja"));
            conn.rollback();

            assertTrue(e.getMessage().contains("TestOtros DeBaja"));
            assertTrue(e.getMessage().contains("dado de baja") || e.getMessage().contains("dada de baja"));
        }
        assertFalse(dao.obtenerTodosConEstado().stream()
            .filter(i -> "TestOtros DeBaja".equals(i.descripcion()))
            .findFirst().orElseThrow().vigente());
    }

    // ── buscarPorDescripcionParcial no ofrece las de baja ────────────────────

    @Test
    void buscar_noOfreceLasDadasDeBaja() throws SQLException {
        ejecutarSQL("INSERT INTO catalogo_otros (descripcion, activo) VALUES ('TestOtros Descartada', FALSE)");

        assertTrue(dao.buscarPorDescripcionParcial("TestOtros Descar").isEmpty());
    }

    // ── obtenerTodosConEstado / darDeBaja / reactivar ────────────────────────

    @Test
    void obtenerTodosConEstado_traeActivosYDeBaja() throws SQLException {
        ejecutarSQL("INSERT INTO catalogo_otros (descripcion) VALUES ('TestOtros Activa')");
        ejecutarSQL("INSERT INTO catalogo_otros (descripcion, activo) VALUES ('TestOtros Inactiva', FALSE)");

        List<ItemCatalogo> items = dao.obtenerTodosConEstado();

        assertTrue(items.stream().anyMatch(i -> "TestOtros Activa".equals(i.descripcion()) && i.vigente()));
        assertTrue(items.stream().anyMatch(i -> "TestOtros Inactiva".equals(i.descripcion()) && !i.vigente()));
    }

    @Test
    void darDeBaja_loSacaDelAutocompletado() throws SQLException {
        ejecutarSQL("INSERT INTO catalogo_otros (descripcion) VALUES ('TestOtros NuevaParaBaja')");
        int idCreado = dao.obtenerIdPorDescripcion("TestOtros NuevaParaBaja");

        dao.darDeBaja(idCreado);

        assertTrue(dao.buscarPorDescripcionParcial("TestOtros NuevaParaBaja").isEmpty());
    }

    @Test
    @DisplayName("Segunda baja: el CAS no matchea y sale como conflicto")
    void darDeBaja_dosVeces_laSegundaEsConflicto() throws SQLException {
        ejecutarSQL("INSERT INTO catalogo_otros (descripcion) VALUES ('TestOtros Baja2')");
        int id = dao.obtenerIdPorDescripcion("TestOtros Baja2");
        dao.darDeBaja(id);

        assertThrows(ConflictoConcurrenciaException.class, () -> dao.darDeBaja(id));
    }

    @Test
    void reactivar_unaDeBaja_laVuelveAOfrecerEnElAutocompletado() throws SQLException {
        ejecutarSQL("INSERT INTO catalogo_otros (descripcion) VALUES ('TestOtros Reactivar')");
        int id = dao.obtenerIdPorDescripcion("TestOtros Reactivar");
        dao.darDeBaja(id);

        dao.reactivar(id);

        assertFalse(dao.buscarPorDescripcionParcial("TestOtros Reactivar").isEmpty());
    }
}
