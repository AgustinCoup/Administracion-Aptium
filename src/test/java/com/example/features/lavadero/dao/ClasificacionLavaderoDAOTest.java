package com.example.features.lavadero.dao;

import com.example.AbstractDAOTest;
import com.example.common.exception.BusinessException;
import com.example.common.exception.ConflictoConcurrenciaException;
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

        clasificacionDAO.guardar(ingresoId, elementos);

        assertEquals(2, contarFilas("elementos_clasificacion_lavadero"));
        assertEquals("CLASIFICADO", estadoDelIngreso(ingresoId));
    }

    /**
     * Un ingreso que no existe no está PENDIENTE, así que la guarda no matchea. El mensaje habla
     * de "otro usuario ya lo clasificó" y no de "no existe" a propósito: por la pantalla sólo se
     * llega a un ingreso que el combo listó, y desde ahí la única forma de que deje de estar
     * PENDIENTE es que alguien más lo haya tocado.
     */
    @Test
    void guardar_ingresoInexistente_lanzaConflicto() throws SQLException {
        List<ElementoClasificacion> elementos = List.of(new ElementoClasificacion(elementoId(1), 2));

        assertThrows(ConflictoConcurrenciaException.class,
            () -> clasificacionDAO.guardar(999999, elementos));
        assertEquals(0, contarFilas("elementos_clasificacion_lavadero"));
    }

    // ── guarda de concurrencia ────────────────────────────────────────────────

    /**
     * Dos operadores clasificando el mismo ingreso: sin la guarda se insertaban los dos juegos de
     * líneas y el ingreso quedaba con el doble de ropa de la que entró.
     */
    @Test
    void guardar_ingresoYaClasificadoPorOtro_lanzaYNoInsertaNingunaLinea() throws SQLException {
        clasificacionDAO.guardar(ingresoId, List.of(new ElementoClasificacion(elementoId(1), 3)));

        List<ElementoClasificacion> segunda = List.of(
            new ElementoClasificacion(elementoId(2), 7),
            new ElementoClasificacion(elementoId(3), 2));

        assertThrows(ConflictoConcurrenciaException.class,
            () -> clasificacionDAO.guardar(ingresoId, segunda));

        assertEquals(1, contarFilas("elementos_clasificacion_lavadero"),
            "la segunda clasificación no dejó ninguna línea: sólo quedan las de la primera");
        assertEquals(3, escalar("SELECT cantidad FROM elementos_clasificacion_lavadero"));
    }

    /** El conflicto es una BusinessException: los controllers ya la rutean como aviso al usuario. */
    @Test
    void guardar_conflicto_esUnaBusinessException() {
        clasificacionDAO.guardar(ingresoId, List.of(new ElementoClasificacion(elementoId(1), 1)));

        assertThrows(BusinessException.class, () -> clasificacionDAO.guardar(
            ingresoId, List.of(new ElementoClasificacion(elementoId(1), 1))));
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
        return escalar("SELECT COUNT(*) FROM " + tabla);
    }

    private int escalar(String sql) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private String estadoDelIngreso(int id) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT estado FROM ingresos_lavadero WHERE id = " + id)) {
            return rs.next() ? rs.getString(1) : null;
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
