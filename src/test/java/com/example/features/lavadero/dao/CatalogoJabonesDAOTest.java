package com.example.features.lavadero.dao;

import com.example.AbstractDAOTest;
import com.example.common.exception.BusinessException;
import com.example.common.exception.ConflictoConcurrenciaException;
import com.example.common.exception.DatabaseException;
import com.example.features.lavadero.model.JabonCatalogo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class CatalogoJabonesDAOTest extends AbstractDAOTest {

    private CatalogoJabonesDAO dao;

    @BeforeEach
    void setUp() {
        dao = new CatalogoJabonesDAO();
    }

    @Override
    protected void limpiarTablas() throws SQLException {
        // Los seeds de la V12 (Skip, Lider) son datos de referencia y no se borran; sí hay que
        // devolverlos a activo = TRUE para no contaminar otros tests.
        ejecutarSQL("DELETE FROM catalogo_jabones WHERE nombre LIKE 'TestJabon%'");
        ejecutarSQL("UPDATE catalogo_jabones SET activo = TRUE");
    }

    @Test
    void findAll_traeLosSeedsDeV12YActivos() {
        assertTrue(dao.findAll().stream().anyMatch(j -> "Skip".equals(j.getNombre())));
        assertTrue(dao.findAll().stream().allMatch(JabonCatalogo::isActivo));
    }

    @Test
    void agregar_devuelveJabonConIdYApareceEnFindAll() {
        JabonCatalogo creado = dao.agregar("TestJabon Nuevo");

        assertTrue(creado.getId() > 0);
        assertTrue(creado.isActivo());
        assertTrue(dao.findAll().stream().anyMatch(j -> j.getId() == creado.getId()));
    }

    @Test
    void findActivos_noTraeLosDeBaja() {
        JabonCatalogo creado = dao.agregar("TestJabon DeBaja");
        dao.darDeBaja(creado.getId());

        assertTrue(dao.findActivos().stream().noneMatch(j -> j.getId() == creado.getId()));
        assertTrue(dao.findAll().stream().anyMatch(j -> j.getId() == creado.getId()));
    }

    @Test
    @DisplayName("Segunda baja: el CAS no matchea y sale como conflicto")
    void darDeBaja_dosVeces_laSegundaEsConflicto() {
        JabonCatalogo creado = dao.agregar("TestJabon Baja2");
        dao.darDeBaja(creado.getId());

        assertThrows(ConflictoConcurrenciaException.class, () -> dao.darDeBaja(creado.getId()));
    }

    @Test
    void reactivar_unoDeBaja_loVuelveAActivos() {
        JabonCatalogo creado = dao.agregar("TestJabon Reactivar");
        dao.darDeBaja(creado.getId());

        dao.reactivar(creado.getId());

        assertTrue(dao.findActivos().stream().anyMatch(j -> j.getId() == creado.getId()));
    }

    @Test
    void reactivar_unoYaActivo_esConflicto() {
        JabonCatalogo creado = dao.agregar("TestJabon YaActivo");

        assertThrows(ConflictoConcurrenciaException.class, () -> dao.reactivar(creado.getId()));
    }

    @Test
    @DisplayName("Alta con un nombre ya activo: el cartel dice sólo que ya existe")
    void agregar_nombreYaExisteActivo_rechazaConElMensajeSimple() {
        dao.agregar("TestJabon Repetido");

        BusinessException e = assertThrows(BusinessException.class, () -> dao.agregar("TestJabon Repetido"));

        assertTrue(e.getMessage().contains("ya existe"));
        assertFalse(e.getMessage().contains("dado de baja"));
    }

    @Test
    @DisplayName("Alta con un nombre ya existente y dado de baja: el cartel manda a reactivarlo")
    void agregar_nombreYaExisteDeBaja_rechazaConElMensajeQueMandaAReactivar() {
        JabonCatalogo creado = dao.agregar("TestJabon RepetidoDeBaja");
        dao.darDeBaja(creado.getId());

        BusinessException e = assertThrows(BusinessException.class,
            () -> dao.agregar("TestJabon RepetidoDeBaja"));

        assertTrue(e.getMessage().contains("dado de baja"));
        assertTrue(e.getMessage().contains("Reactivalo"));
    }

    @Test
    @DisplayName("Un fallo de SQL sale como DatabaseException, no como lista vacía")
    void findAll_conLaTablaRota_lanzaEnVezDeDevolverVacio() throws SQLException {
        ejecutarSQL("ALTER TABLE catalogo_jabones RENAME TO cj_tmp");
        try {
            assertThrows(DatabaseException.class, dao::findAll);
            assertThrows(DatabaseException.class, dao::findActivos);
        } finally {
            ejecutarSQL("ALTER TABLE cj_tmp RENAME TO catalogo_jabones");
        }
    }
}
