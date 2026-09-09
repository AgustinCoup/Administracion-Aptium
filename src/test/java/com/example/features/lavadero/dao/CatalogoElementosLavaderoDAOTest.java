package com.example.features.lavadero.dao;

import com.example.AbstractDAOTest;
import com.example.features.lavadero.model.CategoriaElementoLavadero;
import com.example.features.lavadero.model.ElementoCatalogo;
import com.example.infrastructure.db.ConnectionPool;
import org.junit.jupiter.api.BeforeEach;
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

    private String leerCategoria(int id) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT categoria FROM catalogo_elementos_lavadero WHERE id = " + id)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }
}
