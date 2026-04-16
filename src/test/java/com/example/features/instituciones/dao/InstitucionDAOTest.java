package com.example.features.instituciones.dao;

import com.example.AbstractDAOTest;
import com.example.features.instituciones.model.Institucion;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testea los métodos actualizar() y eliminar() de SimpleEntityDAO,
 * que no están disponibles en ClienteDAO (están sobreescritos para lanzar).
 */
class InstitucionDAOTest extends AbstractDAOTest {

    private final InstitucionDAO dao = new InstitucionDAO();

    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM instituciones WHERE nombre LIKE 'TestInst%'");
    }

    // ── actualizar ────────────────────────────────────────────────────────────

    @Test
    void actualizar_existente_retornaTrue() {
        Institucion inst = new Institucion(0, "TestInst Original");
        dao.guardar(inst);
        inst.setNombre("TestInst Actualizada");
        assertTrue(dao.actualizar(inst));
        assertEquals("TestInst Actualizada", dao.obtenerPorId(inst.getId()).getNombre());
    }

    @Test
    void actualizar_idInexistente_retornaFalse() {
        assertFalse(dao.actualizar(new Institucion(999999, "NoExiste")));
    }

    // ── eliminar ──────────────────────────────────────────────────────────────

    @Test
    void eliminar_existente_retornaTrue() {
        Institucion inst = new Institucion(0, "TestInst ParaBorrar");
        dao.guardar(inst);
        assertTrue(dao.eliminar(inst.getId()));
        assertFalse(dao.existe(inst.getId()));
    }

    @Test
    void eliminar_idInexistente_retornaFalse() {
        assertFalse(dao.eliminar(999999));
    }
}
