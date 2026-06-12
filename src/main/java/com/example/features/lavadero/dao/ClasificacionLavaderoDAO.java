package com.example.features.lavadero.dao;

import com.example.features.lavadero.model.ElementoClasificacion;
import com.example.infrastructure.db.TransactionalConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public class ClasificacionLavaderoDAO {

    private static final Logger log = LoggerFactory.getLogger(ClasificacionLavaderoDAO.class);

    public boolean guardar(int ingresoId, List<ElementoClasificacion> elementos) {
        String sql = "INSERT INTO elementos_clasificacion_lavadero (ingreso_id, elemento_id, cantidad) VALUES (?, ?, ?)";
        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            Connection conn = tx.get();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (ElementoClasificacion e : elementos) {
                    ps.setInt(1, ingresoId);
                    ps.setInt(2, e.getElementoId());
                    ps.setInt(3, e.getCantidad());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            tx.commit();
            return true;
        } catch (SQLException e) {
            log.error("Error al guardar clasificacion de ingreso {}", ingresoId, e);
            return false;
        }
    }
}
