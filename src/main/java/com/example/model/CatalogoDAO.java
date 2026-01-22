package com.example.model;

import com.example.database.Conexion;
import java.sql.*;

public class CatalogoDAO {
    
    public String obtenerDescripcion(int codigo) {
        String sql = "SELECT descripcion FROM catalogo_descripciones WHERE codigo = ?";
        try (Connection conn = Conexion.conectar();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, codigo);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getString("descripcion");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "";
    }
}