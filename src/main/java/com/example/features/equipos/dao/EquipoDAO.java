package com.example.features.equipos.dao;

import com.example.common.dao.DAO;
import com.example.common.exception.DatabaseException;
import com.example.common.exception.ResourceNotFoundException;
import com.example.features.equipos.model.Equipo;
import com.example.features.equipos.model.EstadoEquipo;
import com.example.features.equipos.model.Material;
import com.example.infrastructure.db.ConnectionPool;
import com.example.features.equipos.model.EquipoAuditoria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.time.ZoneId;

/**
 * DAO para gestionar equipos en la base de datos.
 * Implementa todas las operaciones CRUD de la interfaz DAO<Equipo, String>.
 * Usa el id autoincrementable como clave primaria.
 * 
 * MANEJO DE ERRORES:
 * - Lanza DatabaseException en caso de error SQL
 * - Lanza ResourceNotFoundException cuando no encuentra un equipo
 * - NO retorna null, lanza excepciones para flujo de error explícito
 */
public class EquipoDAO implements DAO<Equipo, String> {

    private static final Logger log = LoggerFactory.getLogger(EquipoDAO.class);

    /**
     * Guarda un equipo completo y su lista de materiales en una sola transacción.
     * Implementa el método guardar de la interfaz DAO.
     * 
     * @throws DatabaseException si hay error durante la transacción
     */
    @Override
    public boolean guardar(Equipo equipo) {
        return guardarEquipo(equipo);
    }

    /**
     * Guarda un equipo completo y su lista de materiales en una sola transacción.
     * Método público para compatibilidad con código existente.
     * 
     * @throws DatabaseException si hay error durante la transacción
     */
    public boolean guardarEquipo(Equipo equipo) {
        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();
            conn.setAutoCommit(false); // Iniciamos transacción

            // 1. Insertar el encabezado del Equipo (sin codigo_equipo)
            String sqlEquipo = "INSERT INTO equipos (nro_cliente, nro_profesional, paciente, nro_institucion, estado, requiere_lavado, requiere_empaque) " +
                               "VALUES (?, ?, ?, ?, ?, ?, ?)";
            
            int equipoId;
            try (PreparedStatement psE = conn.prepareStatement(sqlEquipo, Statement.RETURN_GENERATED_KEYS)) {
                psE.setInt(1, equipo.getNroCliente());
                if (equipo.getNroProfesional() != null) {
                    psE.setInt(2, equipo.getNroProfesional());
                } else {
                    psE.setNull(2, Types.INTEGER);
                }
                psE.setString(3, equipo.getPacienteNombre());
                psE.setInt(4, equipo.getNroInstitucion());
                psE.setString(5, equipo.getEstado().getNombre());
                psE.setBoolean(6, equipo.isRequiereLavado());
                psE.setBoolean(7, equipo.isRequiereEmpaque());
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

            // 2. Insertar la lista de Materiales (con estado) y registrar movimiento inicial
            String sqlMaterial = "INSERT INTO equipo_materiales (equipo_id, codigo_catalogo, cantidad, estado) " +
                                 "VALUES (?, ?, ?, ?)";
            String sqlMovimiento = "INSERT INTO material_movimientos " +
                                   "(material_id, equipo_id, cantidad, estado_origen, estado_destino) " +
                                   "VALUES (?, ?, ?, ?, ?)";

            try (PreparedStatement psM = conn.prepareStatement(sqlMaterial, Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement psMov = conn.prepareStatement(sqlMovimiento)) {
                for (Material mat : equipo.getMateriales()) {
                    psM.setInt(1, equipoId);
                    psM.setInt(2, mat.getCodigo());
                    psM.setInt(3, mat.getCantidad());
                    psM.setString(4, mat.getEstado().getNombre());
                    psM.executeUpdate();

                    int materialId;
                    try (ResultSet rsMat = psM.getGeneratedKeys()) {
                        if (rsMat.next()) {
                            materialId = rsMat.getInt(1);
                        } else {
                            throw new SQLException("No se generó ID para material");
                        }
                    }

                    psMov.setInt(1, materialId);
                    psMov.setInt(2, equipoId);
                    psMov.setInt(3, mat.getCantidad());
                    psMov.setNull(4, Types.VARCHAR);
                    psMov.setString(5, mat.getEstado().getNombre());
                    psMov.executeUpdate();
                }
            }

            conn.commit();
            log.info("Equipo guardado exitosamente: ID={}", equipoId);
            return true;

        } catch (SQLException e) {
            if (conn != null) {
                try { 
                    conn.rollback(); 
                    log.warn("Transacción revertida por error");
                } catch (SQLException ex) { 
                    log.error("Error al hacer rollback", ex); 
                }
            }
            log.error("Error al guardar equipo", e);
            throw new DatabaseException("Error al guardar equipo en la base de datos", e);
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException e) { 
                    log.error("Error al cerrar conexión", e); 
                }
            }
        }
    }

    /**
     * Obtiene un equipo por su id.
     * Implementa el método obtenerPorId de la interfaz DAO.
     * 
     * @throws ResourceNotFoundException si el equipo no existe
     * @throws DatabaseException si hay error de base de datos
     */
    @Override
    public Equipo obtenerPorId(String id) {
        String sql = "SELECT e.id, e.nro_cliente, c.nombre AS cliente_nombre, e.nro_profesional, e.paciente, e.nro_institucion, i.nombre, e.estado, e.requiere_lavado, e.requiere_empaque FROM equipos e LEFT JOIN clientes c ON e.nro_cliente = c.id LEFT JOIN instituciones i ON e.nro_institucion = i.id WHERE e.id = ?";
        
        try (Connection conn = ConnectionPool.getConnection();
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
                eq.setRequiereLavado(rs.getBoolean("requiere_lavado"));
                eq.setRequiereEmpaque(rs.getBoolean("requiere_empaque"));
                
                // Cargar materiales asociados con su estado
                cargarMateriales(conn, eq);
                
                return eq;
            } else {
                throw new ResourceNotFoundException("Equipo", id);
            }
        } catch (SQLException e) {
            log.error("Error al obtener equipo por id: {}", id, e);
            throw new DatabaseException("obtener", "Equipo", id, e);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("ID de equipo inválido: " + id, e);
        }
    }

    /**
     * Carga los materiales de un equipo desde la base de datos.
     * Incluye el estado de cada material.
     */
    private void cargarMateriales(Connection conn, Equipo equipo) throws SQLException {
        String sql = "SELECT em.id, em.codigo_catalogo, cd.descripcion, em.cantidad, em.estado, mm.ultimo_movimiento " +
                 "FROM equipo_materiales em " +
             "LEFT JOIN catalogo_descripciones cd ON em.codigo_catalogo = cd.codigo " +
                 "LEFT JOIN (" +
                 "  SELECT material_id, MAX(fecha) AS ultimo_movimiento " +
                 "  FROM material_movimientos GROUP BY material_id" +
                 ") mm ON em.id = mm.material_id " +
                 "WHERE em.equipo_id = ? ORDER BY em.id";
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, equipo.getId());
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                Timestamp ts = rs.getTimestamp("ultimo_movimiento");
                Material mat = new Material(
                    rs.getInt("id"),
                    rs.getInt("codigo_catalogo"),
                    rs.getString("descripcion"),
                    rs.getInt("cantidad"),
                    EstadoEquipo.desdeBD(rs.getString("estado")),
                    ts != null ? java.time.LocalDateTime.ofInstant(ts.toInstant(), ZoneId.systemDefault()) : null
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
        String sql = "SELECT e.id, e.nro_cliente, c.nombre AS cliente_nombre, e.nro_profesional, e.paciente, e.nro_institucion, i.nombre, e.estado, e.requiere_lavado, e.requiere_empaque FROM equipos e LEFT JOIN clientes c ON e.nro_cliente = c.id LEFT JOIN instituciones i ON e.nro_institucion = i.id ORDER BY e.estado, e.id DESC";
        
        try (Connection conn = ConnectionPool.getConnection();
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
                eq.setRequiereLavado(rs.getBoolean("requiere_lavado"));
                eq.setRequiereEmpaque(rs.getBoolean("requiere_empaque"));
                
                // Cargar materiales asociados con su estado
                cargarMateriales(conn, eq);
                
                equipos.add(eq);
            }
        } catch (SQLException e) {
            log.error("Error al obtener todos los equipos", e);
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
        
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, equipo.getEstado().getNombre());
            pstmt.setInt(2, equipo.getId());
            int filasActualizadas = pstmt.executeUpdate();
            
            return filasActualizadas > 0;
        } catch (SQLException e) {
            log.error("Error al actualizar equipo: {}", equipo.getId(), e);
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
        
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, Integer.parseInt(id));
            int filasEliminadas = pstmt.executeUpdate();
            
            return filasEliminadas > 0;
        } catch (SQLException e) {
            log.error("Error al eliminar equipo: {}", id, e);
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
        
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            log.error("Error al contar equipos", e);
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

    // ============================================================
    // MÉTODOS PARA CORRECCIONES Y AUDITORÍA
    // ============================================================

    /**
     * Obtiene todos los equipos en estado NUEVO (solo los editables).
     * @return Lista de equipos en estado NUEVO
     */
    public List<Equipo> obtenerEquiposNuevos() {
        List<Equipo> equipos = new ArrayList<>();
        String sql = "SELECT e.id, e.nro_cliente, c.nombre AS cliente_nombre, e.nro_profesional, e.paciente, e.nro_institucion, i.nombre, e.estado, e.requiere_lavado, e.requiere_empaque " +
                    "FROM equipos e " +
                    "LEFT JOIN clientes c ON e.nro_cliente = c.id " +
                    "LEFT JOIN instituciones i ON e.nro_institucion = i.id " +
                    "WHERE e.estado = 'Nuevo' " +
                    "ORDER BY e.fecha_ingreso DESC";
        
        try (Connection conn = ConnectionPool.getConnection();
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
                eq.setRequiereLavado(rs.getBoolean("requiere_lavado"));
                eq.setRequiereEmpaque(rs.getBoolean("requiere_empaque"));
                
                cargarMateriales(conn, eq);
                equipos.add(eq);
            }
        } catch (SQLException e) {
            log.error("Error al obtener equipos nuevos", e);
        }
        return equipos;
    }


}


