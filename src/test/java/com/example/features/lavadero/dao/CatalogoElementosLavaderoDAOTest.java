package com.example.features.lavadero.dao;

import com.example.AbstractDAOTest;
import com.example.features.lavadero.model.ElementoCatalogo;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CatalogoElementosLavaderoDAOTest extends AbstractDAOTest {

    private final CatalogoElementosLavaderoDAO dao = new CatalogoElementosLavaderoDAO();

    @Override
    protected void limpiarTablas() throws SQLException {
        // Datos de referencia (seed); no se borran entre tests.
    }

    @Test
    void findAll_retornaElementosSeed() {
        List<ElementoCatalogo> lista = dao.findAll();

        assertEquals(25, lista.size(), "Deben cargarse los 25 elementos del seed");
    }

    @Test
    void findAll_ordenadosPorNombre() {
        List<ElementoCatalogo> lista = dao.findAll();

        for (int i = 0; i < lista.size() - 1; i++) {
            assertTrue(
                lista.get(i).getNombre().compareToIgnoreCase(lista.get(i + 1).getNombre()) <= 0,
                "Los elementos deben estar ordenados alfabéticamente por nombre"
            );
        }
    }

    @Test
    void findAll_retornaListaInmutable() {
        List<ElementoCatalogo> lista = dao.findAll();

        assertThrows(UnsupportedOperationException.class,
            () -> lista.add(new ElementoCatalogo(999, "test")));
    }

    @Test
    void findAll_elementosTienenIdYNombreValidos() {
        List<ElementoCatalogo> lista = dao.findAll();

        for (ElementoCatalogo e : lista) {
            assertTrue(e.getId() > 0, "El id debe ser positivo");
            assertNotNull(e.getNombre());
            assertFalse(e.getNombre().isBlank(), "El nombre no debe estar vacío");
        }
    }
}
