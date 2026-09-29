package com.example.features.seguridad.dao;

import com.example.common.constants.Constantes;
import com.example.common.dao.ControlConcurrencia;
import com.example.common.exception.DatabaseException;
import com.example.common.exception.ResourceNotFoundException;
import com.example.features.seguridad.model.HashPassword;
import com.example.infrastructure.db.ConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;

/**
 * Tabla {@code passwords}: una fila por propósito (V29). Guarda hashes, nunca passwords.
 *
 * <p><b>Ningún log ni mensaje de este DAO lleva salt ni hash</b>: sólo el propósito, que no es
 * secreto.</p>
 */
public class PasswordDAO {

    private static final Logger log = LoggerFactory.getLogger(PasswordDAO.class);

    private static final String SQL_LEER =
        "SELECT algoritmo, iteraciones, salt, hash FROM passwords WHERE proposito = ?";

    private static final String SQL_ES_INICIAL =
        "SELECT es_inicial FROM passwords WHERE proposito = ?";

    /**
     * CAS sobre el hash que se verificó: dos cambios simultáneos leen el mismo, gana el primero y
     * el segundo afecta 0 filas. Sin la guarda, el segundo pisaría al primero en silencio y el
     * primero se quedaría con una password que ya no sirve sin saberlo.
     */
    private static final String SQL_REEMPLAZAR =
        "UPDATE passwords SET algoritmo = ?, iteraciones = ?, salt = ?, hash = ?, "
            + "es_inicial = FALSE, actualizado_en = CURRENT_TIMESTAMP "
            + "WHERE proposito = ? AND hash = ? AND salt = ?";

    /** @throws ResourceNotFoundException si no hay fila para ese propósito (instalación rota) */
    public HashPassword leer(String proposito) {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_LEER)) {
            ps.setString(1, proposito);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ResourceNotFoundException("password", proposito);
                }
                Base64.Decoder b64 = Base64.getDecoder();
                return new HashPassword(rs.getString("algoritmo"), rs.getInt("iteraciones"),
                    b64.decode(rs.getString("salt")), b64.decode(rs.getString("hash")));
            }
        } catch (SQLException e) {
            log.error("Error al leer la password de {}", proposito, e);
            throw new DatabaseException("Error al leer la contraseña", e);
        }
    }

    /** @throws ResourceNotFoundException si no hay fila para ese propósito */
    public boolean esInicial(String proposito) {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_ES_INICIAL)) {
            ps.setString(1, proposito);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ResourceNotFoundException("password", proposito);
                }
                return rs.getBoolean(1);
            }
        } catch (SQLException e) {
            log.error("Error al leer si la password de {} es la inicial", proposito, e);
            throw new DatabaseException("Error al leer la contraseña", e);
        }
    }

    /**
     * Reemplaza {@code anterior} por {@code nuevo} y apaga {@code es_inicial}.
     *
     * @throws com.example.common.exception.ConflictoConcurrenciaException si la fila ya no tiene
     *         {@code anterior}: otro puesto la cambió mientras tanto
     */
    public void reemplazar(String proposito, HashPassword anterior, HashPassword nuevo) {
        Base64.Encoder b64 = Base64.getEncoder();
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_REEMPLAZAR)) {
            ps.setString(1, nuevo.algoritmo());
            ps.setInt(2, nuevo.iteraciones());
            ps.setString(3, b64.encodeToString(nuevo.salt()));
            ps.setString(4, b64.encodeToString(nuevo.hash()));
            ps.setString(5, proposito);
            ps.setString(6, b64.encodeToString(anterior.hash()));
            ps.setString(7, b64.encodeToString(anterior.salt()));
            ControlConcurrencia.exigirFilaAfectada(ps.executeUpdate(), Constantes.Mensajes.CONFLICTO_PASSWORD);
        } catch (SQLException e) {
            log.error("Error al reemplazar la password de {}", proposito, e);
            throw new DatabaseException("Error al cambiar la contraseña", e);
        }
    }
}
