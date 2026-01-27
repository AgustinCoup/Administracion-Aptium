package com.example.model;

import com.example.database.Conexion;
import com.example.util.Logger;
import java.sql.*;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para gestionar equipos en la base de datos.
 * Implementa todas las operaciones CRUD de la interfaz DAO<Equipo, String>.
 */
public class EquipoDAO implements DAO<Equipo, String> {

    /**
     * Guarda un equipo completo y su lista de materiales en una sola transacción.
     * Implementa el método guardar de la interfaz DAO.
     */
    @Override
    public boolean guardar(Equipo equipo) {
        return guardarEquipo(equipo);
    }

    /**
     * Guarda un equipo completo y su lista de materiales en una sola transacción.
     * Método público para compatibilidad con código existente.
     */
    public boolean guardarEquipo(Equipo equipo) {
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            conn.setAutoCommit(false); // Iniciamos transacción

            // 1. Generar el Código de Negocio (Ej: 20261)
            String nuevoCodigo = generarSiguienteCodigo(conn);
            equipo.setCodigoEquipo(nuevoCodigo);

            // 2. Insertar el encabezado del Equipo
            String sqlEquipo = "INSERT INTO equipos (codigo_equipo, nro_cliente, cliente_nombre, profesional, paciente, estado) " +
                               "VALUES (?, ?, ?, ?, ?, ?)";
            
            try (PreparedStatement psE = conn.prepareStatement(sqlEquipo)) {
                psE.setString(1, equipo.getCodigoEquipo());
                psE.setInt(2, equipo.getNroCliente());
                psE.setString(3, equipo.getClienteNombre());
                psE.setString(4, equipo.getProfesionalNombre());
                psE.setString(5, equipo.getPacienteNombre());
                psE.setString(6, equipo.getEstado().getNombre());
                psE.executeUpdate();
            }

            // 3. Insertar la lista de Materiales
            String sqlMaterial = "INSERT INTO equipo_materiales (id_relacionado, equipo_codigo, codigo_catalogo, descripcion_copia, cantidad) " +
                                 "VALUES (?, ?, ?, ?, ?)";
            
            try (PreparedStatement psM = conn.prepareStatement(sqlMaterial)) {
                for (Material mat : equipo.getMateriales()) {
                    String idRel = equipo.getCodigoEquipo() + "-" + mat.getCodigo();
                    
                    psM.setString(1, idRel);
                    psM.setString(2, equipo.getCodigoEquipo());
                    psM.setInt(3, mat.getCodigo());
                    psM.setString(4, mat.getDescripcion());
                    psM.setInt(5, mat.getCantidad());
                    psM.addBatch();
                }
                psM.executeBatch();
            }

            conn.commit();
            return true;

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { Logger.error("Error al hacer rollback", ex); }
            }
            Logger.error("Error al guardar equipo: " + equipo.getCodigoEquipo(), e);
            return false;
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException e) { Logger.error("Error al cerrar conexión", e); }
            }
        }
    }

    /**
     * Obtiene un equipo por su código único.
     * Implementa el método obtenerPorId de la interfaz DAO.
     */
    @Override
    public Equipo obtenerPorId(String codigo) {
        String sql = "SELECT codigo_equipo, cliente_nombre, estado FROM equipos WHERE codigo_equipo = ?";
        
        try (Connection conn = Conexion.conectar();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, codigo);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                Equipo eq = new Equipo();
                eq.setCodigoEquipo(rs.getString("codigo_equipo"));
                eq.setClienteNombre(rs.getString("cliente_nombre"));
                eq.setEstado(EstadoEquipo.desdeBD(rs.getString("estado")));
                return eq;
            }
        } catch (SQLException e) {
            Logger.error("Error al obtener equipo por código: " + codigo, e);
        }
        return null;
    }

    /**
     * Obtiene todos los equipos.
     * Implementa el método obtenerTodos de la interfaz DAO.
     */
    @Override
    public List<Equipo> obtenerTodos() {
        return obtenerTodosLosEquipos();
    }

    /**
     * Obtiene todos los equipos con su estado y nombre de cliente.
     * Método público para compatibilidad con código existente.
     */
    public List<Equipo> obtenerTodosLosEquipos() {
        List<Equipo> equipos = new ArrayList<>();
        String sql = "SELECT codigo_equipo, cliente_nombre, estado FROM equipos ORDER BY estado, id DESC";
        
        try (Connection conn = Conexion.conectar();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                Equipo eq = new Equipo();
                eq.setCodigoEquipo(rs.getString("codigo_equipo"));
                eq.setClienteNombre(rs.getString("cliente_nombre"));
                eq.setEstado(EstadoEquipo.desdeBD(rs.getString("estado")));
                equipos.add(eq);
            }
        } catch (SQLException e) {
            Logger.error("Error al obtener todos los equipos", e);
        }
        return equipos;
    }

    /**
     * Actualiza el estado de un equipo existente.
     * Implementa el método actualizar de la interfaz DAO.
     */
    @Override
    public boolean actualizar(Equipo equipo) {
        String sql = "UPDATE equipos SET estado = ? WHERE codigo_equipo = ?";
        
        try (Connection conn = Conexion.conectar();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, equipo.getEstado().getNombre());
            pstmt.setString(2, equipo.getCodigoEquipo());
            int filasActualizadas = pstmt.executeUpdate();
            
            return filasActualizadas > 0;
        } catch (SQLException e) {
            Logger.error("Error al actualizar equipo: " + equipo.getCodigoEquipo(), e);
            return false;
        }
    }

    /**
     * Elimina un equipo por su código.
     * Implementa el método eliminar de la interfaz DAO.
     */
    @Override
    public boolean eliminar(String codigo) {
        String sql = "DELETE FROM equipos WHERE codigo_equipo = ?";
        
        try (Connection conn = Conexion.conectar();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, codigo);
            int filasEliminadas = pstmt.executeUpdate();
            
            return filasEliminadas > 0;
        } catch (SQLException e) {
            Logger.error("Error al eliminar equipo: " + codigo, e);
            return false;
        }
    }

    /**
     * Obtiene el total de equipos en la base de datos.
     * Implementa el método contar de la interfaz DAO.
     */
    @Override
    public long contar() {
        String sql = "SELECT COUNT(*) FROM equipos";
        
        try (Connection conn = Conexion.conectar();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            Logger.error("Error al contar equipos", e);
        }
        return 0;
    }

    /**
     * Verifica si existe un equipo con el código especificado.
     * Implementa el método existe de la interfaz DAO.
     */
    @Override
    public boolean existe(String codigo) {
        return obtenerPorId(codigo) != null;
    }

    /**
     * Busca el último número del año actual y le suma 1.
     */
    private String generarSiguienteCodigo(Connection conn) throws SQLException {
        int anioActual = Year.now().getValue();
        String prefix = String.valueOf(anioActual);
        String sql = "SELECT codigo_equipo FROM equipos WHERE codigo_equipo LIKE ? ORDER BY id DESC LIMIT 1";
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, prefix + "%");
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                String ultimoCodigo = rs.getString("codigo_equipo");
                int correlativo = Integer.parseInt(ultimoCodigo.substring(4));
                return prefix + (correlativo + 1);
            } else {
                return prefix + "1";
            }
        }
    }
}