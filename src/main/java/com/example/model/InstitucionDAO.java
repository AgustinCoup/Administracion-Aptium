package com.example.model;

import com.example.database.Conexion;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para acceso a datos de instituciones.
 * Implementa operaciones CRUD y búsqueda para autocompletado.
 */
public class InstitucionDAO implements DAO<Institucion, Integer> {
    
    /**
     * Busca instituciones cuyo nombre contenga el texto especificado.
     * Búsqueda case-insensitive.
     * 
     * @param nombre Texto a buscar en el nombre de la institución
     * @return Lista de instituciones que coinciden con la búsqueda
     */
    public List<Institucion> buscarPorNombre(String nombre) {
        List<Institucion> instituciones = new ArrayList<>();
        String sql = "SELECT id, nombre FROM instituciones WHERE LOWER(nombre) LIKE LOWER(?) ORDER BY nombre";
        
        try (Connection conn = Conexion.conectar();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, "%" + nombre + "%");
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Institucion institucion = new Institucion();
                    institucion.setId(rs.getInt("id"));
                    institucion.setNombre(rs.getString("nombre"));
                    instituciones.add(institucion);
                }
            }
        } catch (SQLException e) {
            System.out.println("Error al buscar instituciones: " + e.getMessage());
        }
        
        return instituciones;
    }
    
    @Override
    public Institucion obtenerPorId(Integer id) {
        String sql = "SELECT id, nombre FROM instituciones WHERE id = ?";
        
        try (Connection conn = Conexion.conectar();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, id);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Institucion institucion = new Institucion();
                    institucion.setId(rs.getInt("id"));
                    institucion.setNombre(rs.getString("nombre"));
                    return institucion;
                }
            }
        } catch (SQLException e) {
            System.out.println("Error al obtener institución por ID: " + e.getMessage());
        }
        
        return null;
    }
    
    @Override
    public List<Institucion> obtenerTodos() {
        List<Institucion> instituciones = new ArrayList<>();
        String sql = "SELECT id, nombre FROM instituciones ORDER BY nombre";
        
        try (Connection conn = Conexion.conectar();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                Institucion institucion = new Institucion();
                institucion.setId(rs.getInt("id"));
                institucion.setNombre(rs.getString("nombre"));
                instituciones.add(institucion);
            }
        } catch (SQLException e) {
            System.out.println("Error al obtener todas las instituciones: " + e.getMessage());
        }
        
        return instituciones;
    }
    
    @Override
    public boolean guardar(Institucion institucion) {
        String sql = "INSERT INTO instituciones (nombre) VALUES (?)";
        
        try (Connection conn = Conexion.conectar();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            pstmt.setString(1, institucion.getNombre());
            pstmt.executeUpdate();
            
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    institucion.setId(rs.getInt(1));
                }
            }
            return true;
        } catch (SQLException e) {
            System.out.println("Error al insertar institución: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public boolean actualizar(Institucion institucion) {
        String sql = "UPDATE instituciones SET nombre = ? WHERE id = ?";
        
        try (Connection conn = Conexion.conectar();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, institucion.getNombre());
            pstmt.setInt(2, institucion.getId());
            int filasActualizadas = pstmt.executeUpdate();
            return filasActualizadas > 0;
        } catch (SQLException e) {
            System.out.println("Error al actualizar institución: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public boolean eliminar(Integer id) {
        String sql = "DELETE FROM instituciones WHERE id = ?";
        
        try (Connection conn = Conexion.conectar();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, id);
            int filasEliminadas = pstmt.executeUpdate();
            return filasEliminadas > 0;
        } catch (SQLException e) {
            System.out.println("Error al eliminar institución: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Verifica si existe una institución con el ID especificado.
     * 
     * @param id ID de la institución
     * @return true si existe, false en caso contrario
     */
    @Override
    public boolean existe(Integer id) {
        return obtenerPorId(id) != null;
    }
    
    /**
     * Cuenta el total de instituciones en la base de datos.
     * 
     * @return Cantidad total de instituciones
     */
    @Override
    public long contar() {
        String sql = "SELECT COUNT(*) FROM instituciones";
        
        try (Connection conn = Conexion.conectar();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            System.out.println("Error al contar instituciones: " + e.getMessage());
        }
        
        return 0;
    }
}
