package com.example.model;

import com.example.database.ConnectionPool;
import com.example.exception.DatabaseException;
import com.example.exception.ResourceNotFoundException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para gestionar operaciones sobre la tabla clientes.
 * Proporciona métodos para buscar clientes en la base de datos.
 * 
 * MANEJO DE ERRORES:
 * - Lanza DatabaseException en caso de error SQL
 * - Lanza ResourceNotFoundException cuando no encuentra un cliente específico
 * - Búsquedas que retornan listas vacías NO lanzan excepción (comportamiento esperado)
 */
public class ClienteDAO implements DAO<Cliente, Integer> {

    /**
     * Busca clientes cuyo nombre contenga el substring proporcionado.
     * 
     * La búsqueda es case-insensitive y busca en cualquier parte del nombre.
     * Retorna resultados ordenados alfabéticamente.
     * 
     * @param substring Texto a buscar en los nombres de clientes
     * @return Lista de clientes que coinciden con la búsqueda (vacía si no hay resultados)
     * @throws DatabaseException si hay error en la consulta
     */
    public List<Cliente> buscarPorNombre(String substring) {
        List<Cliente> resultados = new ArrayList<>();
        
        String sql = "SELECT id, nombre FROM clientes WHERE LOWER(nombre) LIKE LOWER(?) ORDER BY nombre";
        
        try (Connection conn = ConnectionPool.getConnection();
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
            throw new DatabaseException("Error al buscar clientes por nombre: " + substring, e);
        }
        
        return resultados;
    }

    /**
     * Obtiene un cliente específico por su identificador.
     * 
     * @param id Identificador único del cliente
     * @return Cliente encontrado
     * @throws ResourceNotFoundException si el cliente no existe
     * @throws DatabaseException si hay error en la consulta
     */
    @Override
    public Cliente obtenerPorId(Integer id) {
        String sql = "SELECT id, nombre FROM clientes WHERE id = ?";
        
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                Cliente cliente = new Cliente();
                cliente.setId(rs.getInt("id"));
                cliente.setNombre(rs.getString("nombre"));
                return cliente;
            } else {
                throw new ResourceNotFoundException("Cliente", id);
            }
        } catch (SQLException e) {
            throw new DatabaseException("obtener", "Cliente", id, e);
        }
    }

    /**
     * Obtiene todos los clientes de la base de datos.
     * 
     * @return Lista completa de clientes ordenados alfabéticamente
     * @throws DatabaseException si hay error en la consulta
     */
    @Override
    public List<Cliente> obtenerTodos() {
        List<Cliente> clientes = new ArrayList<>();
        String sql = "SELECT id, nombre FROM clientes ORDER BY nombre";
        
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                Cliente cliente = new Cliente();
                cliente.setId(rs.getInt("id"));
                cliente.setNombre(rs.getString("nombre"));
                clientes.add(cliente);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error al obtener todos los clientes", e);
        }
        
        return clientes;
    }

    /**
     * Guarda un nuevo cliente en la base de datos.
     * 
     * @param entidad Cliente a guardar (id se ignora, se genera automáticamente)
     * @return true si se guardó exitosamente, false en caso contrario
     * @throws DatabaseException si hay error en la inserción
     */
    @Override
    public boolean guardar(Cliente entidad) {
        String sql = "INSERT INTO clientes (nombre) VALUES (?)";
        
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            pstmt.setString(1, entidad.getNombre());
            int rowsAffected = pstmt.executeUpdate();
            
            if (rowsAffected > 0) {
                // Obtener el ID generado automáticamente
                ResultSet generatedKeys = pstmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    entidad.setId(generatedKeys.getInt(1));
                }
                return true;
            }
            return false;
        } catch (SQLException e) {
            throw new DatabaseException("Error al guardar cliente: " + entidad.getNombre(), e);
        }
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
}
