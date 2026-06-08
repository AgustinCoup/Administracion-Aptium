package com.example.features.lavadero.dao;

import com.example.AbstractDAOTest;
import com.example.features.clientes.dao.ClienteDAO;
import com.example.features.clientes.model.Cliente;
import com.example.features.lavadero.model.BolsaLavadero;
import com.example.features.lavadero.model.IngresoLavadero;
import com.example.infrastructure.db.ConnectionPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class IngresoLavaderoDAOTest extends AbstractDAOTest {

    private IngresoLavaderoDAO ingresoDAO;
    private int                clienteId;

    @BeforeEach
    void setUp() {
        ingresoDAO = new IngresoLavaderoDAO(new BolsaLavaderoDAO());

        Cliente cliente = new Cliente(0, "TestLavadero Cliente");
        new ClienteDAO().guardar(cliente);
        clienteId = cliente.getId();
    }

    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM bolsas_lavadero");
        ejecutarSQL("DELETE FROM ingresos_lavadero");
        ejecutarSQL("DELETE FROM clientes WHERE nombre LIKE 'TestLavadero%'");
    }

    // ── guardar — camino feliz ────────────────────────────────────────────────

    @Test
    void guardar_ingresoConUnaBolsa_creaFilasEnDB() throws SQLException {
        IngresoLavadero ingreso = new IngresoLavadero();
        ingreso.setClienteId(clienteId);
        ingreso.agregarBolsa(new BolsaLavadero(new BigDecimal("5.50")));

        assertTrue(ingresoDAO.guardar(ingreso));
        assertNotNull(ingreso.getId(), "El ingreso debe tener ID asignado");

        assertEquals(1, contarFilas("ingresos_lavadero"));
        assertEquals(1, contarFilas("bolsas_lavadero"));
    }

    @Test
    void guardar_ingresoConVariasBolsas_creaTodasLasFilas() throws SQLException {
        IngresoLavadero ingreso = new IngresoLavadero();
        ingreso.setClienteId(clienteId);
        ingreso.agregarBolsa(new BolsaLavadero(new BigDecimal("3.00")));
        ingreso.agregarBolsa(new BolsaLavadero(new BigDecimal("7.50")));
        ingreso.agregarBolsa(new BolsaLavadero(new BigDecimal("2.25")));

        ingresoDAO.guardar(ingreso);

        assertEquals(1, contarFilas("ingresos_lavadero"));
        assertEquals(3, contarFilas("bolsas_lavadero"));
    }

    @Test
    void guardar_asignaIdABolsas() {
        IngresoLavadero ingreso = new IngresoLavadero();
        ingreso.setClienteId(clienteId);
        BolsaLavadero bolsa = new BolsaLavadero(new BigDecimal("4.00"));
        ingreso.agregarBolsa(bolsa);

        ingresoDAO.guardar(ingreso);

        assertNotNull(bolsa.getId(), "La bolsa debe tener ID asignado tras guardar");
        assertTrue(bolsa.getId() > 0);
    }

    @Test
    void guardar_persistePesoCorrecto() throws SQLException {
        IngresoLavadero ingreso = new IngresoLavadero();
        ingreso.setClienteId(clienteId);
        ingreso.agregarBolsa(new BolsaLavadero(new BigDecimal("12.34")));

        ingresoDAO.guardar(ingreso);

        BigDecimal peso = leerPesoPrimeraBolsa();
        assertEquals(0, new BigDecimal("12.34").compareTo(peso),
            "El peso debe persistirse con precisión DECIMAL(6,2)");
    }

    @Test
    void guardar_clienteInexistente_retornaFalse() {
        IngresoLavadero ingreso = new IngresoLavadero();
        ingreso.setClienteId(999999);
        ingreso.agregarBolsa(new BolsaLavadero(new BigDecimal("5.00")));

        assertFalse(ingresoDAO.guardar(ingreso),
            "FK violation debe causar rollback y retornar false");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private int contarFilas(String tabla) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tabla)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private BigDecimal leerPesoPrimeraBolsa() throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT peso_kg FROM bolsas_lavadero LIMIT 1")) {
            assertTrue(rs.next());
            return rs.getBigDecimal("peso_kg");
        }
    }
}
