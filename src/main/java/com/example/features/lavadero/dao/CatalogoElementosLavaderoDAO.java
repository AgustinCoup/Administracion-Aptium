package com.example.features.lavadero.dao;

import com.example.common.exception.DatabaseException;
import com.example.features.lavadero.model.CategoriaElementoLavadero;
import com.example.features.lavadero.model.ElementoCatalogo;
import com.example.infrastructure.db.ConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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

    /** Busca por nombre sin distinguir mayúsculas; sirve para rechazar altas duplicadas. */
    public Optional<ElementoCatalogo> buscarPorNombre(String nombre) {
        String sql = "SELECT id, nombre FROM catalogo_elementos_lavadero WHERE LOWER(nombre) = LOWER(?)";
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nombre);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new ElementoCatalogo(rs.getInt("id"), rs.getString("nombre")));
                }
            }
        } catch (SQLException e) {
            log.error("Error al buscar elemento de catálogo lavadero por nombre '{}'", nombre, e);
            throw new DatabaseException("Error al buscar elemento de catálogo por nombre", e);
        }
        return Optional.empty();
    }

    /** Inserta un elemento nuevo y lo devuelve ya con su id asignado. */
    public ElementoCatalogo agregar(String nombre, CategoriaElementoLavadero categoria) {
        String sql = "INSERT INTO catalogo_elementos_lavadero (nombre, categoria) VALUES (?, ?)";
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nombre);
            ps.setString(2, categoria.name());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return new ElementoCatalogo(rs.getInt(1), nombre);
                }
            }
            throw new DatabaseException("El alta del elemento de catálogo no devolvió id: " + nombre);
        } catch (SQLException e) {
            log.error("Error al agregar elemento de catálogo lavadero '{}'", nombre, e);
            throw new DatabaseException("Error al agregar elemento de catálogo", e);
        }
    }
}
