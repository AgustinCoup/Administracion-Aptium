package com.example.model;

import com.example.database.ConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para acceso a datos de profesionales.
 * Implementa operaciones CRUD y búsqueda para autocompletado.
 */
public class ProfesionalDAO implements DAO<Profesional, Integer> {

    private static final Logger log = LoggerFactory.getLogger(ProfesionalDAO.class);
    
    /**
     * Busca profesionales cuyo nombre contenga el texto especificado.
     * Búsqueda case-insensitive.
     * 
     * @param nombre Texto a buscar en el nombre del profesional
     * @return Lista de profesionales que coinciden con la búsqueda
     */
    public List<Profesional> buscarPorNombre(String nombre) {
        List<Profesional> profesionales = new ArrayList<>();
        String sql = "SELECT id, nombre FROM profesionales WHERE LOWER(nombre) LIKE LOWER(?) ORDER BY nombre";
        
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, "%" + nombre + "%");
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Profesional profesional = new Profesional();
                    profesional.setId(rs.getInt("id"));
                    profesional.setNombre(rs.getString("nombre"));
                    profesionales.add(profesional);
                }
            }
        } catch (SQLException e) {
            log.error("Error al buscar profesionales", e);
        }
        
        return profesionales;
    }
    
    @Override
    public Profesional obtenerPorId(Integer id) {
        String sql = "SELECT id, nombre FROM profesionales WHERE id = ?";
        
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, id);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Profesional profesional = new Profesional();
                    profesional.setId(rs.getInt("id"));
                    profesional.setNombre(rs.getString("nombre"));
                    return profesional;
                }
            }
        } catch (SQLException e) {
            log.error("Error al obtener profesional por ID: {}", id, e);
        }
        
        return null;
    }
    
    @Override
    public List<Profesional> obtenerTodos() {
        List<Profesional> profesionales = new ArrayList<>();
        String sql = "SELECT id, nombre FROM profesionales ORDER BY nombre";
        
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                Profesional profesional = new Profesional();
                profesional.setId(rs.getInt("id"));
                profesional.setNombre(rs.getString("nombre"));
                profesionales.add(profesional);
            }
        } catch (SQLException e) {
            log.error("Error al obtener todos los profesionales", e);
        }
        
        return profesionales;
    }
    
    @Override
    public boolean guardar(Profesional profesional) {
        String sql = "INSERT INTO profesionales (nombre) VALUES (?)";
        
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            pstmt.setString(1, profesional.getNombre());
            pstmt.executeUpdate();
            
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    profesional.setId(rs.getInt(1));
                }
            }
            return true;
        } catch (SQLException e) {
            log.error("Error al insertar profesional", e);
            return false;
        }
    }
    
    @Override
    public boolean actualizar(Profesional profesional) {
        String sql = "UPDATE profesionales SET nombre = ? WHERE id = ?";
        
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, profesional.getNombre());
            pstmt.setInt(2, profesional.getId());
            int filasActualizadas = pstmt.executeUpdate();
            return filasActualizadas > 0;
        } catch (SQLException e) {
            log.error("Error al actualizar profesional", e);
            return false;
        }
    }
    
    @Override
    public boolean eliminar(Integer id) {
        String sql = "DELETE FROM profesionales WHERE id = ?";
        
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, id);
            int filasEliminadas = pstmt.executeUpdate();
            return filasEliminadas > 0;
        } catch (SQLException e) {
            log.error("Error al eliminar profesional", e);
            return false;
        }
    }
    
    /**
     * Verifica si existe un profesional con el ID especificado.
     * 
     * @param id ID del profesional
     * @return true si existe, false en caso contrario
     */
    @Override
    public boolean existe(Integer id) {
        return obtenerPorId(id) != null;
    }
    
    /**
     * Cuenta el total de profesionales en la base de datos.
     * 
     * @return Cantidad total de profesionales
     */
    @Override
    public long contar() {
        String sql = "SELECT COUNT(*) FROM profesionales";
        
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            log.error("Error al contar profesionales", e);
        }
        
        return 0;
    }
}
