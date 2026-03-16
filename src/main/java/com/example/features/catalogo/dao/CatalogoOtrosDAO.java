package com.example.features.catalogo.dao;

import com.example.infrastructure.db.ConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para la tabla {@code catalogo_otros}.
 *
 * Características:
 * - Sin seed: las entradas se crean on-the-fly al guardar un equipo.
 * - Búsqueda por coincidencia parcial, desde 1 carácter.
 * - Upsert: si la descripción ya existe, devuelve su ID existente.
 */
public class CatalogoOtrosDAO {

    private static final Logger log = LoggerFactory.getLogger(CatalogoOtrosDAO.class);

    /**
     * Busca descripciones que contengan el texto dado (case-insensitive).
     * Sin mínimo de caracteres: 1 carácter ya dispara la búsqueda.
     *
     * @param texto Fragmento a buscar
     * @return Lista de descripciones coincidentes (nunca null)
     */
    public List<String> buscarPorDescripcionParcial(String texto) {
        List<String> resultados = new ArrayList<>();
        if (texto == null || texto.trim().isEmpty()) return resultados;

        String sql = "SELECT descripcion FROM catalogo_otros " +
                     "WHERE descripcion LIKE ? ORDER BY descripcion LIMIT 20";

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, "%" + texto.trim() + "%");
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    resultados.add(rs.getString("descripcion"));
                }
            }
        } catch (SQLException e) {
            log.error("Error buscando en catalogo_otros: texto='{}'", texto, e);
        }
        return resultados;
    }

    /**
     * Retorna el ID de la descripción si ya existe, o -1 si no.
     *
     * @param descripcion Texto exacto a buscar
     * @return ID existente o -1
     */
    public int obtenerIdPorDescripcion(String descripcion) {
        if (descripcion == null || descripcion.trim().isEmpty()) return -1;

        String sql = "SELECT id FROM catalogo_otros WHERE descripcion = ?";
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, descripcion.trim());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getInt("id");
            }
        } catch (SQLException e) {
            log.error("Error obteniendo id de catalogo_otros: '{}'", descripcion, e);
        }
        return -1;
    }

    /**
     * Busca o crea la entrada para la descripción dada y devuelve su ID.
     * Opera dentro de la conexión/transacción proporcionada para garantizar
     * atomicidad cuando se llama desde {@link EquipoOtrosDAO}.
     *
     * @param conn        Conexión activa (propiedad del caller; no se cierra aquí)
     * @param descripcion Descripción a insertar o reutilizar
     * @return ID de la entrada en catalogo_otros
     * @throws SQLException si falla la operación
     */
    public int obtenerOCrear(Connection conn, String descripcion) throws SQLException {
        String desc = descripcion.trim();

        // 1. Intentar insertar (ignorando duplicado de UNIQUE)
        String sqlInsert = "INSERT IGNORE INTO catalogo_otros (descripcion) VALUES (?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sqlInsert)) {
            pstmt.setString(1, desc);
            pstmt.executeUpdate();
        }

        // 2. Leer el ID (ya existía o acabamos de crearlo)
        String sqlSelect = "SELECT id FROM catalogo_otros WHERE descripcion = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sqlSelect)) {
            pstmt.setString(1, desc);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getInt("id");
                throw new SQLException("No se pudo obtener ID de catalogo_otros para: " + desc);
            }
        }
    }
}