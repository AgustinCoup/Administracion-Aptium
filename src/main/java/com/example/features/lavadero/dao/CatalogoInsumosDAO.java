package com.example.features.lavadero.dao;

import com.example.common.exception.DatabaseException;
import com.example.features.lavadero.model.InsumoCatalogo;
import com.example.infrastructure.db.ConnectionPool;

import java.sql.*;
import java.util.*;

public class CatalogoInsumosDAO {

    private static final String SQL_TODOS =
        "SELECT id, nombre, activo FROM catalogo_insumos ORDER BY nombre";

    /**
     * Todo el catálogo, activos e inactivos: hoy nadie da de baja un insumo, así que no hay nada
     * que filtrar (el plan de Ajustes agrega un {@code findActivos()}).
     *
     * <p>Un fallo de SQL sale como {@link DatabaseException}, no como lista vacía —a diferencia de
     * {@code CatalogoJabonesDAO}—: una lista vacía le dice a la card "no hay insumos
     * configurados", que no es lo mismo que "no pude leer el catálogo".</p>
     */
    public List<InsumoCatalogo> findAll() {
        List<InsumoCatalogo> result = new ArrayList<>();
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_TODOS);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new InsumoCatalogo(
                    rs.getInt("id"), rs.getString("nombre"), rs.getBoolean("activo")));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error al cargar el catálogo de insumos", e);
        }
        return Collections.unmodifiableList(result);
    }
}
