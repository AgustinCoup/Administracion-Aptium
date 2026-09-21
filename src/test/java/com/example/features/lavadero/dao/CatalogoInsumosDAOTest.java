package com.example.features.lavadero.dao;

import com.example.AbstractDAOTest;
import com.example.features.lavadero.model.InsumoCatalogo;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CatalogoInsumosDAOTest extends AbstractDAOTest {

    private final CatalogoInsumosDAO dao = new CatalogoInsumosDAO();

    @Override
    protected void limpiarTablas() throws SQLException {
        // Sólo lee el seed de la V24: no hay nada que limpiar.
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
}
