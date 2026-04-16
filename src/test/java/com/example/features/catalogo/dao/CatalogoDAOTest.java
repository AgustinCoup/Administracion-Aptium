package com.example.features.catalogo.dao;

import com.example.AbstractDAOTest;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CatalogoDAOTest extends AbstractDAOTest {

    private final CatalogoDAO dao = new CatalogoDAO();

    // Rango de códigos reservado para tests — nunca colisiona con seeds (400–499)
    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM catalogo_descripciones WHERE codigo >= 9000");
    }

    // ── guardarDescripcion ────────────────────────────────────────────────────

    @Test
    void guardarDescripcion_codigoNuevo_retornaTrue() {
        assertTrue(dao.guardarDescripcion(9001, "TestMat Nuevo"));
    }

    @Test
    void guardarDescripcion_codigoDuplicado_actualizaDescripcion() {
        dao.guardarDescripcion(9002, "Original");
        dao.guardarDescripcion(9002, "Actualizada");
        assertEquals("Actualizada", dao.obtenerDescripcion(9002));
    }

    // ── obtenerDescripcion ────────────────────────────────────────────────────

    @Test
    void obtenerDescripcion_codigoExistente_retornaDescripcion() {
        dao.guardarDescripcion(9003, "TestDesc");
        assertEquals("TestDesc", dao.obtenerDescripcion(9003));
    }

    @Test
    void obtenerDescripcion_codigoInexistente_retornaNull() {
        assertNull(dao.obtenerDescripcion(99999));
    }

    // ── obtenerVolumen ────────────────────────────────────────────────────────

    @Test
    void obtenerVolumen_codigoExistente_retornaVolumen() {
        // código 400 "Tornillera" tiene volumen 15 (seed)
        assertEquals(15, dao.obtenerVolumen(400));
    }

    @Test
    void obtenerVolumen_codigoInexistente_retornaNull() {
        assertNull(dao.obtenerVolumen(99999));
    }

    // ── obtenerTodasLasDescripciones ──────────────────────────────────────────

    @Test
    void obtenerTodasLasDescripciones_retornaMapaNonEmpty() {
        Map<Integer, String> mapa = dao.obtenerTodasLasDescripciones();
        assertFalse(mapa.isEmpty());
        assertEquals("Tornillera", mapa.get(400));
    }

    // ── obtenerTodosLosVolumenes ──────────────────────────────────────────────

    @Test
    void obtenerTodosLosVolumenes_retornaMapaNonEmpty() {
        Map<Integer, Integer> mapa = dao.obtenerTodosLosVolumenes();
        assertFalse(mapa.isEmpty());
        assertEquals(15, mapa.get(400));
    }

    // ── eliminar ──────────────────────────────────────────────────────────────

    @Test
    void eliminar_codigoExistente_retornaTrue() {
        dao.guardarDescripcion(9010, "ParaBorrar");
        assertTrue(dao.eliminar(9010));
        assertNull(dao.obtenerDescripcion(9010));
    }

    @Test
    void eliminar_codigoInexistente_retornaFalse() {
        assertFalse(dao.eliminar(99999));
    }

    // ── contar ────────────────────────────────────────────────────────────────

    @Test
    void contar_retornaValorPositivo() {
        assertTrue(dao.contar() > 0);
    }

    // ── existe ────────────────────────────────────────────────────────────────

    @Test
    void existe_codigoExistente_retornaTrue() {
        assertTrue(dao.existe(400));
    }

    @Test
    void existe_codigoInexistente_retornaFalse() {
        assertFalse(dao.existe(99999));
    }

    // ── obtenerTodos / obtenerPorId (interfaz DAO<String,Integer>) ────────────

    @Test
    void obtenerTodos_retornaListaNonEmpty() {
        List<String> lista = dao.obtenerTodos();
        assertFalse(lista.isEmpty());
    }

    @Test
    void obtenerPorId_delegaAObtenerDescripcion() {
        assertEquals(dao.obtenerDescripcion(400), dao.obtenerPorId(400));
    }
}
