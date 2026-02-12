package com.example.model;

import com.example.database.ConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class AutoclaveDAO {

    private static final Logger log = LoggerFactory.getLogger(AutoclaveDAO.class);

    public List<Autoclave> obtenerTodos() {
        List<Autoclave> autoclaves = new ArrayList<>();
        String sql = "SELECT nombre, capacidad FROM autoclaves ORDER BY nombre";

        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                autoclaves.add(new Autoclave(
                    rs.getString("nombre"),
                    rs.getInt("capacidad")
                ));
            }
        } catch (SQLException e) {
            log.error("Error al obtener autoclaves", e);
        }

        return autoclaves;
    }
}
