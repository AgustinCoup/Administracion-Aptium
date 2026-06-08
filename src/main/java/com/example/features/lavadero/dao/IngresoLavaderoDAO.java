package com.example.features.lavadero.dao;

import com.example.features.lavadero.model.IngresoLavadero;
import com.example.infrastructure.db.TransactionalConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

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

    private int insertarIngreso(Connection conn, IngresoLavadero ingreso) throws SQLException {
        String sql = "INSERT INTO ingresos_lavadero (cliente_id) VALUES (?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, ingreso.getClienteId());
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
