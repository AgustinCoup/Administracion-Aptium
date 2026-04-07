package com.example.common.dao;

import com.example.common.exception.DatabaseException;
import com.example.common.exception.ResourceNotFoundException;
import com.example.common.model.Autocompletable;
import com.example.infrastructure.db.ConnectionPool;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO genérico para entidades simples con id y nombre.
 * Elimina duplicación entre ClienteDAO, InstitucionDAO y ProfesionalDAO.
 *
 * Las subclases solo deben implementar tres métodos:
 * - getTableName(): nombre de la tabla en la base de datos
 * - getEntityName(): nombre legible de la entidad (para mensajes de error)
 * - newInstance():  factory method que retorna una instancia vacía de T
 *
 * El método buscarPorNombre() está disponible en todas las subclases.
 */
public abstract class SimpleEntityDAO<T extends Autocompletable> implements DAO<T, Integer> {

    protected abstract String getTableName();
    protected abstract String getEntityName();
    protected abstract T newInstance();

    public List<T> buscarPorNombre(String nombre) {
        List<T> resultados = new ArrayList<>();
        String sql = "SELECT id, nombre FROM " + getTableName()
                   + " WHERE LOWER(nombre) LIKE LOWER(?) ORDER BY nombre";
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "%" + nombre + "%");
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    T entity = newInstance();
                    entity.setId(rs.getInt("id"));
                    entity.setNombre(rs.getString("nombre"));
                    resultados.add(entity);
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error al buscar " + getEntityName() + " por nombre: " + nombre, e);
        }
        return resultados;
    }

    @Override
    public T obtenerPorId(Integer id) {
        String sql = "SELECT id, nombre FROM " + getTableName() + " WHERE id = ?";
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    T entity = newInstance();
                    entity.setId(rs.getInt("id"));
                    entity.setNombre(rs.getString("nombre"));
                    return entity;
                }
                throw new ResourceNotFoundException(getEntityName(), id);
            }
        } catch (SQLException e) {
            throw new DatabaseException("obtener", getEntityName(), id, e);
        }
    }

    @Override
    public List<T> obtenerTodos() {
        List<T> resultados = new ArrayList<>();
        String sql = "SELECT id, nombre FROM " + getTableName() + " ORDER BY nombre";
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                T entity = newInstance();
                entity.setId(rs.getInt("id"));
                entity.setNombre(rs.getString("nombre"));
                resultados.add(entity);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error al obtener todos los " + getEntityName(), e);
        }
        return resultados;
    }

    @Override
    public boolean guardar(T entity) {
        String sql = "INSERT INTO " + getTableName() + " (nombre) VALUES (?)";
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, entity.getNombre());
            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    entity.setId(rs.getInt(1));
                }
            }
            return true;
        } catch (SQLException e) {
            throw new DatabaseException("Error al guardar " + getEntityName() + ": " + entity.getNombre(), e);
        }
    }

    @Override
    public boolean actualizar(T entity) {
        String sql = "UPDATE " + getTableName() + " SET nombre = ? WHERE id = ?";
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, entity.getNombre());
            pstmt.setInt(2, entity.getId());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Error al actualizar " + getEntityName() + " con ID: " + entity.getId(), e);
        }
    }

    @Override
    public boolean eliminar(Integer id) {
        String sql = "DELETE FROM " + getTableName() + " WHERE id = ?";
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Error al eliminar " + getEntityName() + " con ID: " + id, e);
        }
    }

    @Override
    public boolean existe(Integer id) {
        try {
            obtenerPorId(id);
            return true;
        } catch (ResourceNotFoundException e) {
            return false;
        }
    }

    @Override
    public long contar() {
        String sql = "SELECT COUNT(*) FROM " + getTableName();
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error al contar " + getEntityName(), e);
        }
        return 0;
    }
}
