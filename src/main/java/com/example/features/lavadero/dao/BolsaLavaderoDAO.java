package com.example.features.lavadero.dao;

import com.example.features.lavadero.model.BolsaLavadero;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public class BolsaLavaderoDAO {

    public void insertarBolsas(Connection conn, int ingresoId, List<BolsaLavadero> bolsas)
            throws SQLException {
        String sql = "INSERT INTO bolsas_lavadero (ingreso_id, peso_kg) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (BolsaLavadero bolsa : bolsas) {
                ps.setInt(1, ingresoId);
                ps.setBigDecimal(2, bolsa.getPesoKg());
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        bolsa.setId(rs.getInt(1));
                    }
                }
            }
        }
    }
}
