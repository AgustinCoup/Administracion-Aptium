package com.example.features.lavadero.dao;

import com.example.features.lavadero.model.JabonCatalogo;
import com.example.infrastructure.db.ConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

public class CatalogoJabonesDAO {

    private static final Logger log = LoggerFactory.getLogger(CatalogoJabonesDAO.class);

    public List<JabonCatalogo> findAll() {
        String sql = "SELECT id, nombre FROM catalogo_jabones ORDER BY nombre";
        List<JabonCatalogo> result = new ArrayList<>();
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new JabonCatalogo(rs.getInt("id"), rs.getString("nombre")));
            }
        } catch (SQLException e) {
            log.error("Error al cargar catálogo de jabones", e);
        }
        return Collections.unmodifiableList(result);
    }
}
