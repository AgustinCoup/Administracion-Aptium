package com.example.features.catalogo.dao;

import com.example.AbstractDAOTest;
import com.example.common.exception.ConflictoConcurrenciaException;
import com.example.features.catalogo.model.ItemCatalogo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    // ── obtenerDescripcionVigente ─────────────────────────────────────────────

    @Test
    void obtenerDescripcionVigente_codigoVigente_retornaDescripcion() {
        assertEquals("TORNILLERA", dao.obtenerDescripcionVigente(400));
    }

    @Test
    void obtenerDescripcionVigente_codigoDadoDeBaja_retornaNull() {
        // el 414 quedó fuera del listado oficial: no debe poder cargarse
        assertNull(dao.obtenerDescripcionVigente(414));
    }

    @Test
    void obtenerDescripcion_codigoDadoDeBaja_sigueResolviendoElHistorico() {
        // la fila sobrevive para que los materiales ya guardados no queden huérfanos
        assertNotNull(dao.obtenerDescripcion(414));
    }

    @ParameterizedTest
    @ValueSource(ints = {406, 409, 418, 420})
    void obtenerDescripcionVigente_codigoCompuestoRetirado_retornaNull(int codigo) {
        // los compuestos se desdoblaron en códigos nuevos: no se asignan más
        assertNull(dao.obtenerDescripcionVigente(codigo));
    }

    @ParameterizedTest
    @ValueSource(ints = {406, 409, 418, 420})
    void obtenerDescripcion_codigoCompuestoRetirado_conservaElTextoOriginal(int codigo) {
        // el historial no se re-etiqueta: la descripción vieja sigue intacta bajo (LEGACY)
        assertTrue(dao.obtenerDescripcion(codigo).startsWith("(LEGACY) "));
    }

    @Test
    void obtenerDescripcionVigente_reemplazosDeLosCompuestos_sonAsignables() {
        assertEquals("MAKITA", dao.obtenerDescripcionVigente(431));
        assertEquals("INSTRUMENTAL PEQUEÑO", dao.obtenerDescripcionVigente(432));
        assertEquals("CLAVOS", dao.obtenerDescripcionVigente(433));
        assertEquals("TORNILLO", dao.obtenerDescripcionVigente(434));
    }

    // ── obtenerVolumen ────────────────────────────────────────────────────────

    @Test
    void obtenerVolumen_codigoExistente_retornaVolumen() {
        // código 400 "TORNILLERA" tiene volumen 15 (seed)
        assertEquals(15, dao.obtenerVolumen(400));
    }

    @Test
    void obtenerVolumen_codigoInexistente_retornaNull() {
        assertNull(dao.obtenerVolumen(99999));
    }

    /**
     * El 414 está de baja desde la V16 (fuera del listado oficial). Un material ya cargado con
     * ese código sigue necesitando su volumen para armar un lote: obtenerVolumen es histórico y
     * no filtra por vigente.
     */
    @Test
    void obtenerVolumen_codigoDadoDeBaja_sigueDevolviendoElVolumen() {
        assertNotNull(dao.obtenerVolumen(414));
    }

    // ── darDeBaja / reactivar ─────────────────────────────────────────────────

    @Test
    void darDeBaja_loSacaDeVigentes() {
        dao.guardarDescripcion(9020, "TestBaja");

        dao.darDeBaja(9020);

        assertNull(dao.obtenerDescripcionVigente(9020));
        assertNotNull(dao.obtenerDescripcion(9020), "sigue existiendo, sólo dejó de estar vigente");
    }

    @Test
    @DisplayName("Segunda baja: el CAS no matchea y sale como conflicto")
    void darDeBaja_dosVeces_laSegundaEsConflicto() {
        dao.guardarDescripcion(9021, "TestBaja2");
        dao.darDeBaja(9021);

        assertThrows(ConflictoConcurrenciaException.class, () -> dao.darDeBaja(9021));
    }

    @Test
    void reactivar_unoDeBaja_loVuelveAVigentes() {
        dao.guardarDescripcion(9022, "TestReactivar");
        dao.darDeBaja(9022);

        dao.reactivar(9022);

        assertEquals("TestReactivar", dao.obtenerDescripcionVigente(9022));
    }

    @Test
    void reactivar_unoYaVigente_esConflicto() {
        dao.guardarDescripcion(9023, "TestYaVigente");

        assertThrows(ConflictoConcurrenciaException.class, () -> dao.reactivar(9023));
    }

    // ── obtenerTodosConEstado ─────────────────────────────────────────────────

    @Test
    void obtenerTodosConEstado_traeVigentesYDeBaja() {
        dao.guardarDescripcion(9024, "TestEstadoVigente");
        dao.guardarDescripcion(9025, "TestEstadoDeBaja");
        dao.darDeBaja(9025);

        List<ItemCatalogo> items = dao.obtenerTodosConEstado();

        assertTrue(items.stream().anyMatch(i -> i.codigo() == 9024 && i.vigente()));
        assertTrue(items.stream().anyMatch(i -> i.codigo() == 9025 && !i.vigente()));
    }

    // ── obtenerTodasLasDescripciones ──────────────────────────────────────────

    @Test
    void obtenerTodasLasDescripciones_retornaMapaNonEmpty() {
        Map<Integer, String> mapa = dao.obtenerTodasLasDescripciones();
        assertFalse(mapa.isEmpty());
        assertEquals("TORNILLERA", mapa.get(400));
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
