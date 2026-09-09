package com.example.features.lavadero.dao;

import com.example.features.lavadero.model.IngresoLavadero;
import com.example.features.lavadero.model.IngresoLavaderoResumen;
import com.example.infrastructure.db.ConnectionPool;
import com.example.infrastructure.db.TransactionalConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class IngresoLavaderoDAO {

    private static final Logger log = LoggerFactory.getLogger(IngresoLavaderoDAO.class);

    private final BolsaLavaderoDAO bolsaDAO;

    public IngresoLavaderoDAO(BolsaLavaderoDAO bolsaDAO) {
        if (bolsaDAO == null) throw new IllegalArgumentException("bolsaDAO no puede ser nulo");
        this.bolsaDAO = bolsaDAO;
    }

    public boolean guardar(IngresoLavadero ingreso) {
        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            Connection conn = tx.get();

            int ingresoId = insertarIngreso(conn, ingreso);
            bolsaDAO.insertarBolsas(conn, ingresoId, ingreso.getBolsas());

            tx.commit();
            return true;
        } catch (SQLException e) {
            log.error("Error al guardar IngresoLavadero", e);
            return false;
        }
    }

    public List<IngresoLavaderoResumen> findSinClasificar() {
        String sql =
            "SELECT il.id, c.nombre, il.fecha_ingreso, il.peso_total_kg, COUNT(b.id) AS cant_bolsas " +
            "FROM ingresos_lavadero il " +
            "JOIN clientes c ON il.cliente_id = c.id " +
            "LEFT JOIN bolsas_lavadero b ON b.ingreso_id = il.id " +
            "WHERE NOT EXISTS " +
            "  (SELECT 1 FROM elementos_clasificacion_lavadero e WHERE e.ingreso_id = il.id) " +
            "GROUP BY il.id, c.nombre, il.fecha_ingreso, il.peso_total_kg " +
            "ORDER BY il.fecha_ingreso DESC";

        List<IngresoLavaderoResumen> result = new ArrayList<>();
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Timestamp ts = rs.getTimestamp("fecha_ingreso");
                LocalDateTime fecha = ts != null ? ts.toLocalDateTime() : null;
                BigDecimal peso = rs.getBigDecimal("peso_total_kg");
                result.add(new IngresoLavaderoResumen(
                    rs.getInt("id"),
                    rs.getString("nombre"),
                    fecha,
                    peso,
                    rs.getInt("cant_bolsas")
                ));
            }
        } catch (SQLException e) {
            log.error("Error al cargar ingresos sin clasificar", e);
        }
        return Collections.unmodifiableList(result);
    }

    private int insertarIngreso(Connection conn, IngresoLavadero ingreso) throws SQLException {
        String sql = "INSERT INTO ingresos_lavadero (cliente_id, peso_total_kg) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, ingreso.getClienteId());
            ps.setBigDecimal(2, ingreso.getPesoTotal());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    ingreso.setId(id);
                    return id;
                }
                throw new SQLException("No se generó ID para ingresos_lavadero");
            }
        }
    }
}
