package com.example.model;

import com.example.database.Conexion;
import com.example.util.Logger;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para gestionar equipos en la base de datos.
 * Implementa todas las operaciones CRUD de la interfaz DAO<Equipo, String>.
 * Usa el id autoincrementable como clave primaria.
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

            // 1. Insertar el encabezado del Equipo (sin codigo_equipo)
            String sqlEquipo = "INSERT INTO equipos (nro_cliente, cliente_nombre, nro_profesional, paciente, nro_institucion, estado) " +
                               "VALUES (?, ?, ?, ?, ?, ?)";
            
            int equipoId;
            try (PreparedStatement psE = conn.prepareStatement(sqlEquipo, Statement.RETURN_GENERATED_KEYS)) {
                psE.setInt(1, equipo.getNroCliente());
                psE.setString(2, equipo.getClienteNombre());
                if (equipo.getNroProfesional() != null) {
                    psE.setInt(3, equipo.getNroProfesional());
                } else {
                    psE.setNull(3, Types.INTEGER);
                }
                psE.setString(4, equipo.getPacienteNombre());
                psE.setInt(5, equipo.getNroInstitucion());
                psE.setString(6, equipo.getEstado().getNombre());
                psE.executeUpdate();
                
                // Obtener el ID generado automáticamente
                try (ResultSet rs = psE.getGeneratedKeys()) {
                    if (rs.next()) {
                        equipoId = rs.getInt(1);
                        equipo.setId(equipoId);
                    } else {
                        throw new SQLException("No se generó ID para el equipo");
                    }
                }
            }

            // 2. Insertar la lista de Materiales (con estado)
            String sqlMaterial = "INSERT INTO equipo_materiales (equipo_id, codigo_catalogo, descripcion_copia, cantidad, estado) " +
                                 "VALUES (?, ?, ?, ?, ?)";
            
            try (PreparedStatement psM = conn.prepareStatement(sqlMaterial)) {
                for (Material mat : equipo.getMateriales()) {
                    psM.setInt(1, equipoId);
                    psM.setInt(2, mat.getCodigo());
                    psM.setString(3, mat.getDescripcion());
                    psM.setInt(4, mat.getCantidad());
                    psM.setString(5, mat.getEstado().getNombre());
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
            Logger.error("Error al guardar equipo", e);
            return false;
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException e) { Logger.error("Error al cerrar conexión", e); }
            }
        }
    }

    /**
     * Obtiene un equipo por su id.
     * Implementa el método obtenerPorId de la interfaz DAO.
     */
    @Override
    public Equipo obtenerPorId(String id) {
        String sql = "SELECT e.id, e.nro_cliente, e.cliente_nombre, e.nro_profesional, e.paciente, e.nro_institucion, i.nombre, e.estado FROM equipos e LEFT JOIN instituciones i ON e.nro_institucion = i.id WHERE e.id = ?";
        
        try (Connection conn = Conexion.conectar();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, Integer.parseInt(id));
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                Equipo eq = new Equipo();
                eq.setId(rs.getInt("id"));
                eq.setNroCliente(rs.getInt("nro_cliente"));
                eq.setClienteNombre(rs.getString("cliente_nombre"));
                Integer nroProfesional = rs.getObject("nro_profesional", Integer.class);
                eq.setNroProfesional(nroProfesional);
                eq.setPacienteNombre(rs.getString("paciente"));
                Integer nroInstitucion = rs.getObject("nro_institucion", Integer.class);
                eq.setNroInstitucion(nroInstitucion);
                eq.setInstitucionNombre(rs.getString("nombre"));
                eq.setEstado(EstadoEquipo.desdeBD(rs.getString("estado")));
                
                // Cargar materiales asociados con su estado
                cargarMateriales(conn, eq);
                
                return eq;
            }
        } catch (SQLException e) {
            Logger.error("Error al obtener equipo por id: " + id, e);
        }
        return null;
    }

    /**
     * Carga los materiales de un equipo desde la base de datos.
     * Incluye el estado de cada material.
     */
    private void cargarMateriales(Connection conn, Equipo equipo) throws SQLException {
        String sql = "SELECT codigo_catalogo, descripcion_copia, cantidad, estado FROM equipo_materiales WHERE equipo_id = ?";
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, equipo.getId());
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                Material mat = new Material(
                    rs.getInt("codigo_catalogo"),
                    rs.getString("descripcion_copia"),
                    rs.getInt("cantidad"),
                    EstadoEquipo.desdeBD(rs.getString("estado"))
                );
                equipo.agregarMaterial(mat);
            }
        }
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
        String sql = "SELECT e.id, e.nro_cliente, e.cliente_nombre, e.nro_profesional, e.paciente, e.nro_institucion, i.nombre, e.estado FROM equipos e LEFT JOIN instituciones i ON e.nro_institucion = i.id ORDER BY e.estado, e.id DESC";
        
        try (Connection conn = Conexion.conectar();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                Equipo eq = new Equipo();
                eq.setId(rs.getInt("id"));
                eq.setNroCliente(rs.getInt("nro_cliente"));
                eq.setClienteNombre(rs.getString("cliente_nombre"));
                Integer nroProfesional = rs.getObject("nro_profesional", Integer.class);
                eq.setNroProfesional(nroProfesional);
                eq.setPacienteNombre(rs.getString("paciente"));
                Integer nroInstitucion = rs.getObject("nro_institucion", Integer.class);
                eq.setNroInstitucion(nroInstitucion);
                eq.setInstitucionNombre(rs.getString("nombre"));
                eq.setEstado(EstadoEquipo.desdeBD(rs.getString("estado")));
                
                // Cargar materiales asociados con su estado
                cargarMateriales(conn, eq);
                
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
        String sql = "UPDATE equipos SET estado = ? WHERE id = ?";
        
        try (Connection conn = Conexion.conectar();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, equipo.getEstado().getNombre());
            pstmt.setInt(2, equipo.getId());
            int filasActualizadas = pstmt.executeUpdate();
            
            return filasActualizadas > 0;
        } catch (SQLException e) {
            Logger.error("Error al actualizar equipo: " + equipo.getId(), e);
            return false;
        }
    }

    /**
     * Elimina un equipo por su id.
     * Implementa el método eliminar de la interfaz DAO.
     */
    @Override
    public boolean eliminar(String id) {
        String sql = "DELETE FROM equipos WHERE id = ?";
        
        try (Connection conn = Conexion.conectar();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, Integer.parseInt(id));
            int filasEliminadas = pstmt.executeUpdate();
            
            return filasEliminadas > 0;
        } catch (SQLException e) {
            Logger.error("Error al eliminar equipo: " + id, e);
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
     * Verifica si existe un equipo con el id especificado.
     * Implementa el método existe de la interfaz DAO.
     */
    @Override
    public boolean existe(String id) {
        return obtenerPorId(id) != null;
    }
}