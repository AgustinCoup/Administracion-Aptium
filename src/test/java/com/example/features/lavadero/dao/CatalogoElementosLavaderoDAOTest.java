package com.example.features.lavadero.dao;

import com.example.AbstractDAOTest;
import com.example.common.exception.BusinessException;
import com.example.common.exception.ConflictoConcurrenciaException;
import com.example.features.lavadero.model.CategoriaElementoLavadero;
import com.example.features.lavadero.model.ElementoCatalogo;
import com.example.infrastructure.db.ConnectionPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CatalogoElementosLavaderoDAOTest extends AbstractDAOTest {

    private CatalogoElementosLavaderoDAO dao;

    @BeforeEach
    void setUp() {
        dao = new CatalogoElementosLavaderoDAO();
    }

    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM catalogo_elementos_lavadero WHERE nombre LIKE 'TestCat%'");
    }

    @Test
    void agregar_devuelveElementoConIdYApareceEnFindAll() {
        ElementoCatalogo creado = dao.agregar("TestCat Sabana", CategoriaElementoLavadero.REGULAR);

        assertTrue(creado.getId() > 0);
        assertEquals("TestCat Sabana", creado.getNombre());
        assertTrue(dao.findAll().stream().anyMatch(e -> e.getId() == creado.getId()));
    }

    @Test
    void agregar_persisteLaCategoria() throws SQLException {
        ElementoCatalogo creado = dao.agregar("TestCat Equipo", CategoriaElementoLavadero.EQUIPO);
        assertEquals("EQUIPO", leerCategoria(creado.getId()));
    }

    @Test
    void buscarPorNombre_ignoraMayusculas() {
        ElementoCatalogo creado = dao.agregar("TestCat Poncho", CategoriaElementoLavadero.REGULAR);

        Optional<ElementoCatalogo> encontrado = dao.buscarPorNombre("testcat poncho");
        assertTrue(encontrado.isPresent());
        assertEquals(creado.getId(), encontrado.get().getId());
    }

    @Test
    void buscarPorNombre_inexistente_devuelveVacio() {
        assertTrue(dao.buscarPorNombre("TestCat NoExiste").isEmpty());
    }

    // ── findActivos / findAll ────────────────────────────────────────────────

    @Test
    void findActivos_noTraeLosDeBaja() {
        ElementoCatalogo creado = dao.agregar("TestCat DeBaja", CategoriaElementoLavadero.REGULAR);
        dao.darDeBaja(creado.getId());

        assertTrue(dao.findActivos().stream().noneMatch(e -> e.getId() == creado.getId()));
        assertTrue(dao.findAll().stream().anyMatch(e -> e.getId() == creado.getId()),
            "findAll es para Ajustes: no filtra");
    }

    @Test
    void buscarPorNombre_encuentraUnoDadoDeBaja() {
        ElementoCatalogo creado = dao.agregar("TestCat Ponchera", CategoriaElementoLavadero.REGULAR);
        dao.darDeBaja(creado.getId());

        Optional<ElementoCatalogo> encontrado = dao.buscarPorNombre("TestCat Ponchera");
        assertTrue(encontrado.isPresent(), "el WHERE activo puesto de más rompería el alta rechazada");
        assertFalse(encontrado.get().isActivo());
    }

    // ── baja / reactivación ──────────────────────────────────────────────────

    @Test
    void darDeBaja_loSacaDeActivos() {
        ElementoCatalogo creado = dao.agregar("TestCat Sabana2", CategoriaElementoLavadero.REGULAR);

        dao.darDeBaja(creado.getId());

        assertTrue(dao.findActivos().stream().noneMatch(e -> e.getId() == creado.getId()));
    }

    @Test
    @DisplayName("Segunda baja: el CAS no matchea y sale como conflicto")
    void darDeBaja_dosVeces_laSegundaEsConflicto() {
        ElementoCatalogo creado = dao.agregar("TestCat Sabana3", CategoriaElementoLavadero.REGULAR);
        dao.darDeBaja(creado.getId());

        assertThrows(ConflictoConcurrenciaException.class, () -> dao.darDeBaja(creado.getId()));
    }

    @Test
    void reactivar_unoDeBaja_loVuelveAActivos() {
        ElementoCatalogo creado = dao.agregar("TestCat Sabana4", CategoriaElementoLavadero.REGULAR);
        dao.darDeBaja(creado.getId());

        dao.reactivar(creado.getId());

        assertTrue(dao.findActivos().stream().anyMatch(e -> e.getId() == creado.getId()));
    }

    @Test
    void reactivar_unoYaActivo_esConflicto() {
        ElementoCatalogo creado = dao.agregar("TestCat Sabana5", CategoriaElementoLavadero.REGULAR);

        assertThrows(ConflictoConcurrenciaException.class, () -> dao.reactivar(creado.getId()));
    }

    // ── alta con nombre repetido ─────────────────────────────────────────────

    @Test
    @DisplayName("Alta con un nombre ya activo: el cartel dice sólo que ya existe")
    void agregar_nombreYaExisteActivo_rechazaConElMensajeSimple() {
        dao.agregar("TestCat Repetido", CategoriaElementoLavadero.REGULAR);

        BusinessException e = assertThrows(BusinessException.class,
            () -> dao.agregar("TestCat Repetido", CategoriaElementoLavadero.REGULAR));

        assertTrue(e.getMessage().contains("ya existe"));
        assertFalse(e.getMessage().contains("dado de baja"));
    }

    @Test
    @DisplayName("Alta con un nombre ya existente y dado de baja: el cartel manda a reactivarlo")
    void agregar_nombreYaExisteDeBaja_rechazaConElMensajeQueMandaAReactivar() {
        ElementoCatalogo creado = dao.agregar("TestCat RepetidoDeBaja", CategoriaElementoLavadero.REGULAR);
        dao.darDeBaja(creado.getId());

        BusinessException e = assertThrows(BusinessException.class,
            () -> dao.agregar("TestCat RepetidoDeBaja", CategoriaElementoLavadero.REGULAR));

        assertTrue(e.getMessage().contains("dado de baja"));
        assertTrue(e.getMessage().contains("Reactivalo"));
    }

    // ── manejo de errores ────────────────────────────────────────────────────

    @Test
    @DisplayName("Un fallo de SQL en findAll sale como DatabaseException, no como lista vacía")
    void findAll_conLaTablaRota_lanzaEnVezDeDevolverVacio() throws SQLException {
        ejecutarSQL("ALTER TABLE catalogo_elementos_lavadero RENAME TO cel_tmp");
        try {
            assertThrows(com.example.common.exception.DatabaseException.class, dao::findAll);
            assertThrows(com.example.common.exception.DatabaseException.class, dao::findActivos);
        } finally {
            ejecutarSQL("ALTER TABLE cel_tmp RENAME TO catalogo_elementos_lavadero");
        }
    }

    private String leerCategoria(int id) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT categoria FROM catalogo_elementos_lavadero WHERE id = " + id)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }
}
