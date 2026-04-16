package com.example.features.equipos.ortopedias.dao;

import com.example.AbstractDAOTest;
import com.example.features.equipos.ortopedias.model.EquipoAuditoria;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de integración para AuditoriaDAO.
 * Las tablas de auditoría no tienen FK hacia equipos, por lo que los tests
 * no requieren equipos pre-existentes.
 */
class AuditoriaDAOTest extends AbstractDAOTest {

    private final AuditoriaDAO dao = new AuditoriaDAO();

    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM materiales_eliminados");
        ejecutarSQL("DELETE FROM equipos_eliminados");
        ejecutarSQL("DELETE FROM equipos_auditoria");
    }

    // ── registrarCambio ───────────────────────────────────────────────────────

    @Test
    void registrarCambio_conMaterialId_retornaTrue() {
        assertTrue(dao.registrarCambio(10, 5, "MODIFICACION_CANTIDAD",
            "cantidad", "3", "5", "corrección test"));
    }

    @Test
    void registrarCambio_sinMaterialId_retornaTrue() {
        assertTrue(dao.registrarCambio(10, null, "MODIFICACION_CODIGO",
            "codigo_catalogo", "100", "200", "reasignación test"));
    }

    // ── registrarEquipoEliminado ───────────────────────────────────────────────

    @Test
    void registrarEquipoEliminado_todosLosCampos_retornaTrue() {
        assertTrue(dao.registrarEquipoEliminado(
            42, 1, "Cliente A", 2, "Paciente X",
            3, "Inst B", "Nuevo", "baja por error test"));
    }

    @Test
    void registrarEquipoEliminado_camposOpcionalesNull_retornaTrue() {
        // nroProfesional, paciente, nroInstitucion pueden ser null
        assertTrue(dao.registrarEquipoEliminado(
            43, 1, "Cliente B", null, null,
            null, null, "Nuevo", "baja test sin profesional"));
    }

    // ── registrarMaterialEliminado ────────────────────────────────────────────

    @Test
    void registrarMaterialEliminado_conMaterialId_retornaTrue() {
        assertTrue(dao.registrarMaterialEliminado(
            10, 5, 400, "Tornillera", 2, "Nuevo", "baja test"));
    }

    @Test
    void registrarMaterialEliminado_materialIdNull_retornaTrue() {
        assertTrue(dao.registrarMaterialEliminado(
            10, null, 400, "Tornillera", 2, "Nuevo", "baja sin id test"));
    }

    @Test
    void registrarMaterialEliminado_cantidadNull_retornaTrue() {
        assertTrue(dao.registrarMaterialEliminado(
            10, 5, 400, "Tornillera", null, "Nuevo", "baja sin cantidad test"));
    }

    // ── obtenerPorEquipo ──────────────────────────────────────────────────────

    @Test
    void obtenerPorEquipo_sinRegistros_retornaListaVacia() {
        assertTrue(dao.obtenerPorEquipo(9999).isEmpty());
    }

    @Test
    void obtenerPorEquipo_conRegistros_retornaLista() {
        dao.registrarCambio(55, 1, "MODIFICACION_CANTIDAD", "cantidad", "2", "4", "motivo");
        dao.registrarCambio(55, 2, "MODIFICACION_CODIGO", "codigo_catalogo", "100", "200", "motivo");

        List<EquipoAuditoria> lista = dao.obtenerPorEquipo(55);
        assertEquals(2, lista.size());
        assertTrue(lista.stream().allMatch(a -> a.getEquipoId() == 55));
    }

    @Test
    void obtenerPorEquipo_soloDevuelveRegistrosDelEquipoConsultado() {
        dao.registrarCambio(60, 1, "MODIFICACION_CANTIDAD", "cantidad", "1", "2", "m1");
        dao.registrarCambio(61, 2, "MODIFICACION_CANTIDAD", "cantidad", "1", "2", "m2");

        List<EquipoAuditoria> lista = dao.obtenerPorEquipo(60);
        assertEquals(1, lista.size());
        assertEquals(60, lista.get(0).getEquipoId());
    }

    // ── obtenerTodos (via vista_auditoria) ────────────────────────────────────

    @Test
    void obtenerTodos_sinRegistros_retornaListaVacia() {
        assertTrue(dao.obtenerTodos().isEmpty());
    }

    @Test
    void obtenerTodos_conRegistros_retornaListaNonEmpty() {
        dao.registrarCambio(70, 1, "MODIFICACION_CANTIDAD", "cantidad", "3", "5", "test vista");
        List<EquipoAuditoria> todos = dao.obtenerTodos();
        assertFalse(todos.isEmpty());
    }

    @Test
    void obtenerTodos_incluyeEliminaciones() {
        dao.registrarEquipoEliminado(80, 1, "Cliente Test", null, null, null, null, "Nuevo", "motivo");
        List<EquipoAuditoria> todos = dao.obtenerTodos();
        assertTrue(todos.stream().anyMatch(a -> "ELIMINACION_EQUIPO".equals(a.getTipoCambio())));
    }
}
