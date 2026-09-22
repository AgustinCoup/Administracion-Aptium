package com.example.features.lavadero.dao;

import com.example.AbstractDAOTest;
import com.example.common.exception.BusinessException;
import com.example.common.exception.ConflictoConcurrenciaException;
import com.example.common.exception.DatabaseException;
import com.example.features.lavadero.model.InsumoCatalogo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CatalogoInsumosDAOTest extends AbstractDAOTest {

    private final CatalogoInsumosDAO dao = new CatalogoInsumosDAO();

    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM catalogo_insumos WHERE nombre LIKE 'TestInsumo%'");
        ejecutarSQL("UPDATE catalogo_insumos SET activo = TRUE");
    }

    @Test
    void findAll_traeLosDosInsumosDelSeedDeV24_ordenadosPorNombreYActivos() {
        List<InsumoCatalogo> insumos = dao.findAll();

        assertEquals(List.of("Potenciador", "Suavizante"),
            insumos.stream().map(InsumoCatalogo::nombre).toList());
        assertTrue(insumos.stream().allMatch(InsumoCatalogo::activo));
    }

    @Test
    void findAll_devuelveUnaListaInmodificable() {
        List<InsumoCatalogo> insumos = dao.findAll();

        assertThrows(UnsupportedOperationException.class,
            () -> insumos.add(new InsumoCatalogo(99, "x", true)));
    }

    @Test
    void agregar_devuelveInsumoConIdYApareceEnFindAll() {
        InsumoCatalogo creado = dao.agregar("TestInsumo Nuevo");

        assertTrue(creado.id() > 0);
        assertTrue(creado.activo());
        assertTrue(dao.findAll().stream().anyMatch(i -> i.id() == creado.id()));
    }

    @Test
    void findActivos_noTraeLosDeBaja() {
        InsumoCatalogo creado = dao.agregar("TestInsumo DeBaja");
        dao.darDeBaja(creado.id());

        assertTrue(dao.findActivos().stream().noneMatch(i -> i.id() == creado.id()));
        assertTrue(dao.findAll().stream().anyMatch(i -> i.id() == creado.id()));
    }

    @Test
    @DisplayName("Segunda baja: el CAS no matchea y sale como conflicto")
    void darDeBaja_dosVeces_laSegundaEsConflicto() {
        InsumoCatalogo creado = dao.agregar("TestInsumo Baja2");
        dao.darDeBaja(creado.id());

        assertThrows(ConflictoConcurrenciaException.class, () -> dao.darDeBaja(creado.id()));
    }

    @Test
    void reactivar_unoDeBaja_loVuelveAActivos() {
        InsumoCatalogo creado = dao.agregar("TestInsumo Reactivar");
        dao.darDeBaja(creado.id());

        dao.reactivar(creado.id());

        assertTrue(dao.findActivos().stream().anyMatch(i -> i.id() == creado.id()));
    }

    @Test
    void reactivar_unoYaActivo_esConflicto() {
        InsumoCatalogo creado = dao.agregar("TestInsumo YaActivo");

        assertThrows(ConflictoConcurrenciaException.class, () -> dao.reactivar(creado.id()));
    }

    @Test
    @DisplayName("Alta con un nombre ya activo: el cartel dice sólo que ya existe")
    void agregar_nombreYaExisteActivo_rechazaConElMensajeSimple() {
        dao.agregar("TestInsumo Repetido");

        BusinessException e = assertThrows(BusinessException.class, () -> dao.agregar("TestInsumo Repetido"));

        assertTrue(e.getMessage().contains("ya existe"));
        assertFalse(e.getMessage().contains("dado de baja"));
    }

    @Test
    @DisplayName("Alta con un nombre ya existente y dado de baja: el cartel manda a reactivarlo")
    void agregar_nombreYaExisteDeBaja_rechazaConElMensajeQueMandaAReactivar() {
        InsumoCatalogo creado = dao.agregar("TestInsumo RepetidoDeBaja");
        dao.darDeBaja(creado.id());

        BusinessException e = assertThrows(BusinessException.class,
            () -> dao.agregar("TestInsumo RepetidoDeBaja"));

        assertTrue(e.getMessage().contains("dado de baja"));
        assertTrue(e.getMessage().contains("Reactivalo"));
    }

    @Test
    @DisplayName("Un fallo de SQL sale como DatabaseException, no como lista vacía")
    void findAll_conLaTablaRota_lanzaEnVezDeDevolverVacio() throws SQLException {
        ejecutarSQL("ALTER TABLE catalogo_insumos RENAME TO ci_tmp");
        try {
            assertThrows(DatabaseException.class, dao::findAll);
            assertThrows(DatabaseException.class, dao::findActivos);
        } finally {
            ejecutarSQL("ALTER TABLE ci_tmp RENAME TO catalogo_insumos");
        }
    }
}
