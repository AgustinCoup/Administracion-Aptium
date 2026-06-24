package com.example.features.lavadero.dao;

import com.example.features.lavadero.model.Lavarropas;
import com.example.infrastructure.db.ConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class LavarropasDAO {

    private static final Logger log = LoggerFactory.getLogger(LavarropasDAO.class);

    public List<Lavarropas> obtenerTodos() {
        String sql = "SELECT numero, capacidad_litros FROM lavarropas ORDER BY numero";
        List<Lavarropas> resultado = new ArrayList<>();
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                resultado.add(new Lavarropas(rs.getInt("numero"), rs.getInt("capacidad_litros")));
            }
        } catch (SQLException e) {
            log.error("Error al obtener lavarropas", e);
        }
        return resultado;
    }
}
