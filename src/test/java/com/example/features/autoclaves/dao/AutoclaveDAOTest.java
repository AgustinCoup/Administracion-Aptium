package com.example.features.autoclaves.dao;

import com.example.AbstractDAOTest;
import com.example.features.autoclaves.model.Autoclave;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AutoclaveDAOTest extends AbstractDAOTest {

    private final AutoclaveDAO dao = new AutoclaveDAO();

    @Override
    protected void limpiarTablas() throws SQLException {
        // Solo se usan los seeds — nada que limpiar
    }

    // ── obtenerTodos ──────────────────────────────────────────────────────────

    @Test
    void obtenerTodos_retornaSeeds_listaNoVacia() {
        List<Autoclave> lista = dao.obtenerTodos();
        assertFalse(lista.isEmpty());
    }

    @Test
    void obtenerTodos_contieneE01ConCapacidad120() {
        List<Autoclave> lista = dao.obtenerTodos();
        assertTrue(lista.stream().anyMatch(a -> "E01".equals(a.getNombre())));
        Autoclave e01 = lista.stream().filter(a -> "E01".equals(a.getNombre())).findFirst().orElseThrow();
        assertEquals(120, e01.getCapacidad());
    }

    @Test
    void obtenerTodos_retornaOrdenAlfabetico() {
        List<Autoclave> lista = dao.obtenerTodos();
        for (int i = 0; i < lista.size() - 1; i++) {
            assertTrue(lista.get(i).getNombre().compareTo(lista.get(i + 1).getNombre()) <= 0,
                "Lista no está ordenada alfabéticamente");
        }
    }

    @Test
    void obtenerTodos_cantidadIgualASeed() {
        // seed_autoclaves.sql inserta 8 filas
        assertEquals(8, dao.obtenerTodos().size());
    }
}
