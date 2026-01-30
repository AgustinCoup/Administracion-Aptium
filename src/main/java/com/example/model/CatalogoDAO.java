package com.example.model;

import com.example.database.ConnectionPool;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * DAO para gestionar el catálogo de descripciones de materiales.
 * Implementa operaciones CRUD sobre la tabla catalogo_descripciones.
 */
public class CatalogoDAO implements DAO<String, Integer> {
    
    /**
     * Guarda una descripción en el catálogo.
     * Si el código ya existe, actualiza la descripción.
     * 
     * @param codigo Código del material
     * @param descripcion Descripción del material
     * @return true si se guardó exitosamente
     */
    public boolean guardarDescripcion(int codigo, String descripcion) {
        String sql = "INSERT INTO catalogo_descripciones (codigo, descripcion) VALUES (?, ?) " +
                     "ON DUPLICATE KEY UPDATE descripcion = ?";
        
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, codigo);
            pstmt.setString(2, descripcion);
            pstmt.setString(3, descripcion);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Obtiene la descripción de un material por su código.
     * 
     * @param codigo Código del material
     * @return Descripción del material, o null si no existe
     */
    public String obtenerDescripcion(int codigo) {
        String sql = "SELECT descripcion FROM catalogo_descripciones WHERE codigo = ?";
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, codigo);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getString("descripcion");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    /**
     * Obtiene todas las descripciones del catálogo como un mapa.
     * 
     * @return Mapa con código -> descripción
     */
    public Map<Integer, String> obtenerTodasLasDescripciones() {
        Map<Integer, String> catalogo = new HashMap<>();
        String sql = "SELECT codigo, descripcion FROM catalogo_descripciones ORDER BY codigo";
        
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                catalogo.put(rs.getInt("codigo"), rs.getString("descripcion"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return catalogo;
    }
    
    // ===== Implementación de la interfaz DAO<String, Integer> =====
    
    /**
     * Guarda una descripción (implementa interfaz DAO).
     * Para este DAO, la entidad es la descripción (String).
     */
    @Override
    public boolean guardar(String descripcion) {
        // Este método no es práctico para este DAO, se usa guardarDescripcion()
        return false;
    }
    
    /**
     * Obtiene una descripción por código (implementa interfaz DAO).
     */
    @Override
    public String obtenerPorId(Integer codigo) {
        return obtenerDescripcion(codigo);
    }
    
    /**
     * Obtiene todas las descripciones como lista (implementa interfaz DAO).
     */
    @Override
    public List<String> obtenerTodos() {
        String sql = "SELECT descripcion FROM catalogo_descripciones ORDER BY codigo";
        List<String> descripciones = new ArrayList<>();
        
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                descripciones.add(rs.getString("descripcion"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return descripciones;
    }
    
    /**
     * Actualiza una descripción (implementa interfaz DAO).
     */
    @Override
    public boolean actualizar(String descripcion) {
        // Este método no es práctico para este DAO
        return false;
    }
    
    /**
     * Elimina una descripción por código (implementa interfaz DAO).
     */
    @Override
    public boolean eliminar(Integer codigo) {
        String sql = "DELETE FROM catalogo_descripciones WHERE codigo = ?";
        
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, codigo);
            int filasEliminadas = pstmt.executeUpdate();
            return filasEliminadas > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Obtiene el total de códigos en el catálogo (implementa interfaz DAO).
     */
    @Override
    public long contar() {
        String sql = "SELECT COUNT(*) FROM catalogo_descripciones";
        
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }
    
    /**
     * Verifica si existe una descripción para el código (implementa interfaz DAO).
     */
    @Override
    public boolean existe(Integer codigo) {
        return obtenerDescripcion(codigo) != null;
    }
}