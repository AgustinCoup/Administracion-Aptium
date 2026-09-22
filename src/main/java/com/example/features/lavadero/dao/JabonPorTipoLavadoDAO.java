package com.example.features.lavadero.dao;

import com.example.common.exception.DatabaseException;
import com.example.features.lavadero.model.JabonCatalogo;
import com.example.features.lavadero.model.TipoLavado;
import com.example.infrastructure.db.ConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * El jabón por defecto de cada tipo de lavado (tabla {@code jabon_por_tipo_lavado}, V26). Es lo que
 * la card de Ciclos carga sola cuando el operador elige el tipo, y lo que la pestaña de Ajustes
 * deja configurar.
 *
 * <p><b>Estas escrituras no llevan guarda, y es una excepción razonada.</b> La regla del repo pide
 * guarda para toda escritura que dependa de un dato leído antes; acá no hay tal dato: el upsert
 * <i>es</i> la intención completa del operador ("el default de Sucio es éste"), sobre una tabla de
 * una sola fila por tipo. Dos operadores configurando el mismo tipo no se pisan un cálculo, ganan
 * el último — que es exactamente lo que cualquiera de los dos esperaría. Ponerle una {@code version}
 * daría un cartel de conflicto por una preferencia de conveniencia, y un cartel que sobra entrena
 * a apretar "Sí" sin leer.</p>
 */
public class JabonPorTipoLavadoDAO {

    private static final Logger log = LoggerFactory.getLogger(JabonPorTipoLavadoDAO.class);

    /**
     * <b>El {@code JOIN} no filtra por {@code activo} a propósito.</b> Un default puede quedar
     * apuntando a un jabón dado de baja desde Ajustes, y qué hacer con eso lo decide la regla
     * ({@code SelectorJabonAutomatico}: no se toca nada), no la consulta. Si la consulta lo
     * escondiera, Ajustes no podría mostrar "el default de Sucio es un jabón dado de baja" — que
     * es justo lo que el operador necesita ver para arreglarlo.
     */
    private static final String SQL_DEFAULTS =
        "SELECT d.tipo_lavado, j.id, j.nombre, j.activo " +
        "FROM jabon_por_tipo_lavado d " +
        "JOIN catalogo_jabones j ON j.id = d.jabon_id";

    /** Upsert: la PK es el tipo, así que "configurar el default" es una sola sentencia. */
    private static final String SQL_GUARDAR =
        "INSERT INTO jabon_por_tipo_lavado (tipo_lavado, jabon_id) VALUES (?, ?) " +
        "ON DUPLICATE KEY UPDATE jabon_id = ?";

    /** La opción "(sin default)" de Ajustes: borrar la fila es un estado legítimo. */
    private static final String SQL_BORRAR =
        "DELETE FROM jabon_por_tipo_lavado WHERE tipo_lavado = ?";

    /**
     * Los defaults configurados. Un tipo sin fila simplemente no está en el mapa: "no hay default"
     * es un estado legítimo y no se representa con un {@code null} adentro.
     */
    public Map<TipoLavado, JabonCatalogo> obtenerDefaults() {
        Map<TipoLavado, JabonCatalogo> defaults = new EnumMap<>(TipoLavado.class);
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_DEFAULTS);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                TipoLavado tipo = parsear(rs.getString("tipo_lavado"));
                if (tipo == null) continue;
                defaults.put(tipo, new JabonCatalogo(
                    rs.getInt("id"), rs.getString("nombre"), rs.getBoolean("activo")));
            }
        } catch (SQLException e) {
            log.error("Error al leer los jabones por defecto", e);
            throw new DatabaseException("Error al leer los jabones por defecto", e);
        }
        return Collections.unmodifiableMap(defaults);
    }

    public void guardar(TipoLavado tipo, int jabonId) {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_GUARDAR)) {
            ps.setString(1, tipo.name());
            ps.setInt(2, jabonId);
            ps.setInt(3, jabonId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Error al guardar el jabón por defecto de {}", tipo, e);
            throw new DatabaseException("Error al guardar el jabón por defecto de " + tipo.getNombre(), e);
        }
    }

    /** Idempotente: borrar un default que ya no está es el resultado buscado, no un choque. */
    public void borrar(TipoLavado tipo) {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_BORRAR)) {
            ps.setString(1, tipo.name());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Error al borrar el jabón por defecto de {}", tipo, e);
            throw new DatabaseException("Error al borrar el jabón por defecto de " + tipo.getNombre(), e);
        }
    }

    // ── privados ─────────────────────────────────────────────────────────────

    /**
     * Estricto, y <b>no</b> {@link TipoLavado#desdeBD(String)}: aquél mapea lo desconocido a
     * {@code SUCIO}, que es lo correcto para {@code ciclos_lavadero.tipo_lavado} —columna
     * {@code NOT NULL} escrita siempre por el DAO, donde un valor raro significa que alguien tocó
     * la base y quedarse sin tipo sería peor—. Acá la columna <b>es la PK</b>: una fila con un
     * valor basura mapeada a {@code SUCIO} pisaría silenciosamente el default verdadero de Sucio,
     * según el orden en que las devuelva la base. Se saltea y se avisa.
     */
    private static TipoLavado parsear(String valor) {
        if (valor != null) {
            for (TipoLavado t : TipoLavado.values()) {
                if (t.name().equalsIgnoreCase(valor.trim())) return t;
            }
        }
        log.warn("tipo_lavado desconocido '{}' en jabon_por_tipo_lavado; se ignora esa fila", valor);
        return null;
    }
}
