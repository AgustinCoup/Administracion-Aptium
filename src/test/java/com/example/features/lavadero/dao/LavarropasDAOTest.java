package com.example.features.lavadero.dao;

import com.example.AbstractDAOTest;
import com.example.features.lavadero.model.Lavarropas;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LavarropasDAOTest extends AbstractDAOTest {

    private final LavarropasDAO dao = new LavarropasDAO();

    @Override
    protected void limpiarTablas() throws SQLException {
        // Los lavarropas son datos de referencia (seed); no se borran entre tests.
    }

    @Test
    void obtenerTodos_retornaLosSeeds() {
        List<Lavarropas> lista = dao.obtenerTodos();

        assertEquals(13, lista.size(), "Deben existir los 13 lavarropas del seed");
    }

    @Test
    void obtenerTodos_ordenadosPorNumero() {
        List<Lavarropas> lista = dao.obtenerTodos();

        for (int i = 0; i < lista.size() - 1; i++) {
            assertTrue(lista.get(i).getNumero() < lista.get(i + 1).getNumero(),
                "Los lavarropas deben estar ordenados por número");
        }
    }

    @Test
    void obtenerTodos_capacidadEsCorrecta() {
        List<Lavarropas> lista = dao.obtenerTodos();

        lista.forEach(lv -> assertEquals(13, lv.getCapacidadLitros(),
            "Todos los lavarropas del seed tienen 13 litros de capacidad"));
    }
}
