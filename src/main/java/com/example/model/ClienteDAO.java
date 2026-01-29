package com.example.model;

import com.example.database.Conexion;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para gestionar operaciones sobre la tabla clientes.
 * Proporciona métodos para buscar clientes en la base de datos.
 */
public class ClienteDAO implements DAO<Cliente, Integer> {

    /**
     * Busca clientes cuyo nombre contenga el substring proporcionado.
     * 
     * La búsqueda es case-insensitive y busca en cualquier parte del nombre.
     * Retorna resultados ordenados alfabéticamente.
     * 
     * @param substring Texto a buscar en los nombres de clientes
     * @return Lista de clientes que coinciden con la búsqueda
     */
    public List<Cliente> buscarPorNombre(String substring) {
        List<Cliente> resultados = new ArrayList<>();
        
        String sql = "SELECT id, nombre FROM clientes WHERE LOWER(nombre) LIKE LOWER(?) ORDER BY nombre";
        
        try (Connection conn = Conexion.conectar();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, "%" + substring + "%");
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                Cliente cliente = new Cliente();
                cliente.setId(rs.getInt("id"));
                cliente.setNombre(rs.getString("nombre"));
                resultados.add(cliente);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return resultados;
    }

    /**
     * Obtiene un cliente específico por su identificador.
     * 
     * @param id Identificador único del cliente
     * @return Cliente encontrado, o null si no existe
     */
    @Override
    public Cliente obtenerPorId(Integer id) {
        String sql = "SELECT id, nombre FROM clientes WHERE id = ?";
        
        try (Connection conn = Conexion.conectar();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                Cliente cliente = new Cliente();
                cliente.setId(rs.getInt("id"));
                cliente.setNombre(rs.getString("nombre"));
                return cliente;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return null;
    }

    /**
     * Obtiene todos los clientes de la base de datos.
     * 
     * @return Lista completa de clientes ordenados alfabéticamente
     */
    @Override
    public List<Cliente> obtenerTodos() {
        List<Cliente> clientes = new ArrayList<>();
        String sql = "SELECT id, nombre FROM clientes ORDER BY nombre";
        
        try (Connection conn = Conexion.conectar();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                Cliente cliente = new Cliente();
                cliente.setId(rs.getInt("id"));
                cliente.setNombre(rs.getString("nombre"));
                clientes.add(cliente);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return clientes;
    }

    @Override
    public boolean guardar(Cliente entidad) {
        throw new UnsupportedOperationException("Guardar cliente no implementado");
    }

    @Override
    public boolean actualizar(Cliente entidad) {
        throw new UnsupportedOperationException("Actualizar cliente no implementado");
    }

    @Override
    public boolean eliminar(Integer id) {
        throw new UnsupportedOperationException("Eliminar cliente no implementado");
    }

    @Override
    public boolean existe(Integer id) {
        return obtenerPorId(id) != null;
    }

    /**
     * Retorna la cantidad total de clientes registrados.
     * 
     * @return Número total de clientes en la base de datos
     */
    @Override
    public long contar() {
        String sql = "SELECT COUNT(*) FROM clientes";
        
        try (Connection conn = Conexion.conectar();
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
}
