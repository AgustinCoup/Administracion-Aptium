package com.example.features.lavadero.dao;

import com.example.features.lavadero.model.ElementoCatalogo;
import com.example.infrastructure.db.ConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CatalogoElementosLavaderoDAO {

    private static final Logger log = LoggerFactory.getLogger(CatalogoElementosLavaderoDAO.class);

    public List<ElementoCatalogo> findAll() {
        String sql = "SELECT id, nombre FROM catalogo_elementos_lavadero ORDER BY nombre";
        List<ElementoCatalogo> result = new ArrayList<>();
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new ElementoCatalogo(rs.getInt("id"), rs.getString("nombre")));
            }
        } catch (SQLException e) {
            log.error("Error al cargar catálogo de elementos lavadero", e);
        }
        return Collections.unmodifiableList(result);
    }
}
