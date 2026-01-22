package com.example.model;

import com.example.database.Conexion;
import com.example.model.Equipo;
import com.example.model.Material;
import java.sql.*;
import java.time.Year;

public class EquipoDAO {

    /**
     * Guarda un equipo completo y su lista de materiales en una sola transacción.
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
                psE.setString(6, equipo.getEstado());
                psE.executeUpdate();
            }

            // 3. Insertar la lista de Materiales
            String sqlMaterial = "INSERT INTO equipo_materiales (id_relacionado, equipo_codigo, codigo_catalogo, descripcion_copia, cantidad) " +
                                 "VALUES (?, ?, ?, ?, ?)";
            
            try (PreparedStatement psM = conn.prepareStatement(sqlMaterial)) {
                for (Material mat : equipo.getMateriales()) {
                    // El idRelacionado se genera aquí: "20261-400"
                    String idRel = equipo.getCodigoEquipo() + "-" + mat.getCodigo();
                    
                    psM.setString(1, idRel);
                    psM.setString(2, equipo.getCodigoEquipo());
                    psM.setInt(3, mat.getCodigo());
                    psM.setString(4, mat.getDescripcion());
                    psM.setInt(5, mat.getCantidad());
                    psM.addBatch(); // Optimización para múltiples registros
                }
                psM.executeBatch();
            }

            conn.commit(); // Confirmamos todos los cambios
            return true;

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            e.printStackTrace();
            return false;
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException e) { e.printStackTrace(); }
            }
        }
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
                // Cortamos el año y sumamos 1 al resto
                int correlativo = Integer.parseInt(ultimoCodigo.substring(4));
                return prefix + (correlativo + 1);
            } else {
                return prefix + "1"; // Primer equipo del año
            }
        }
    }
}