package com.example.features.lavadero.dao;

import com.example.AbstractDAOTest;
import com.example.features.clientes.dao.ClienteDAO;
import com.example.features.clientes.model.Cliente;
import com.example.features.lavadero.model.BolsaLavadero;
import com.example.features.lavadero.model.ElementoClasificacion;
import com.example.features.lavadero.model.IngresoLavadero;
import com.example.features.lavadero.model.IngresoLavaderoResumen;
import com.example.infrastructure.db.ConnectionPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClasificacionLavaderoDAOTest extends AbstractDAOTest {

    private ClasificacionLavaderoDAO clasificacionDAO;
    private IngresoLavaderoDAO       ingresoDAO;
    private int                      ingresoId;

    @BeforeEach
    void setUp() throws SQLException {
        clasificacionDAO = new ClasificacionLavaderoDAO();
        ingresoDAO       = new IngresoLavaderoDAO(new BolsaLavaderoDAO());

        Cliente cliente = new Cliente(0, "TestClasif Cliente");
        new ClienteDAO().guardar(cliente);

        IngresoLavadero ingreso = new IngresoLavadero();
        ingreso.setClienteId(cliente.getId());
        ingreso.agregarBolsa(new BolsaLavadero(new BigDecimal("5.00")));
        ingresoDAO.guardar(ingreso);
        ingresoId = ingreso.getId();
    }

    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM elementos_clasificacion_lavadero");
        ejecutarSQL("DELETE FROM bolsas_lavadero");
        ejecutarSQL("DELETE FROM ingresos_lavadero");
        ejecutarSQL("DELETE FROM clientes WHERE nombre LIKE 'TestClasif%'");
    }

    // ── guardar ───────────────────────────────────────────────────────────────

    @Test
    void guardar_insertaElementosCorrectamente() throws SQLException {
        List<ElementoClasificacion> elementos = List.of(
            new ElementoClasificacion(elementoId(1), 3),
            new ElementoClasificacion(elementoId(2), 1)
        );

        assertTrue(clasificacionDAO.guardar(ingresoId, elementos));
        assertEquals(2, contarFilas("elementos_clasificacion_lavadero"));
    }

    @Test
    void guardar_ingresoInexistente_retornaFalse() {
        List<ElementoClasificacion> elementos = List.of(new ElementoClasificacion(elementoId(1), 2));
        assertFalse(clasificacionDAO.guardar(999999, elementos));
    }

    // ── findSinClasificar ─────────────────────────────────────────────────────

    @Test
    void findSinClasificar_retornaIngresoSinClasificar() {
        List<IngresoLavaderoResumen> lista = ingresoDAO.findSinClasificar();
        assertTrue(lista.stream().anyMatch(r -> r.getId() == ingresoId));
    }

    @Test
    void findSinClasificar_noRetornaIngresoYaClasificado() {
        clasificacionDAO.guardar(ingresoId,
            List.of(new ElementoClasificacion(elementoId(1), 1)));

        List<IngresoLavaderoResumen> lista = ingresoDAO.findSinClasificar();
        assertTrue(lista.stream().noneMatch(r -> r.getId() == ingresoId));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private int contarFilas(String tabla) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tabla)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private int elementoId(int offset) {
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT id FROM catalogo_elementos_lavadero ORDER BY id LIMIT 1 OFFSET " + (offset - 1))) {
            if (rs.next()) return rs.getInt(1);
            throw new IllegalStateException("No hay elementos en catalogo_elementos_lavadero");
        } catch (SQLException e) {
            throw new IllegalStateException("Error leyendo catalogo", e);
        }
    }
}
