package com.example.features.lavadero.dao;

import com.example.common.exception.BusinessException;
import com.example.common.exception.DatabaseException;
import com.example.features.lavadero.model.ElementoLavadoPendiente;
import com.example.features.lavadero.model.MarcaListo;
import com.example.features.lavadero.model.SalidaLista;
import com.example.infrastructure.db.ConnectionPool;
import com.example.infrastructure.db.TransactionalConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC de {@code salidas_lavadero}: qué está lavado y pendiente de secado/doblado, y el
 * marcado parcial de cantidades como "Listo".
 *
 * <p>No existe ninguna columna que diga "esto está lavado": se deriva del ciclo
 * ({@code ciclos_lavadero.fecha_fin IS NOT NULL}). Los saldos se calculan con el mismo patrón
 * de {@code LEFT JOIN} + {@code GROUP BY} + {@code HAVING} que ya usa
 * {@code CicloLavaderoDAO.SQL_DISPONIBLES}, que se sabe que corre igual en H2 y en MySQL.</p>
 *
 * <p>A diferencia de los DAOs viejos del lavadero, este no traga excepciones: un fallo de SQL
 * sale como {@link DatabaseException} y una regla de negocio violada como
 * {@link BusinessException}. Devolver una lista vacía ante un error de lectura le miente a la
 * pantalla.</p>
 */
public class SalidaLavaderoDAO {

    /**
     * Tandas lavadas con cantidad todavía sin marcar como Listo.
     *
     * <p>Agrupa por fila de ciclo y no por línea de clasificación: así se ve qué ciclo lavó
     * qué, que es lo que el operador tiene delante cuando dobla.</p>
     */
    private static final String SQL_PENDIENTES_DE_LISTO =
        "SELECT eci.id AS elemento_ciclo_id, eci.ciclo_id, cl.lavarropas_numero, ecl.ingreso_id, " +
        "       il.cliente_id, c.nombre AS cliente, cel.nombre AS elemento, " +
        "       eci.cantidad, COALESCE(SUM(sl.cantidad), 0) AS ya_lista, cl.fecha_fin " +
        "FROM elementos_ciclo_lavadero eci " +
        "JOIN ciclos_lavadero cl                    ON cl.id  = eci.ciclo_id " +
        "JOIN elementos_clasificacion_lavadero ecl  ON ecl.id = eci.elemento_clasificacion_id " +
        "JOIN catalogo_elementos_lavadero cel       ON cel.id = ecl.elemento_id " +
        "JOIN ingresos_lavadero il                  ON il.id  = ecl.ingreso_id " +
        "JOIN clientes c                            ON c.id   = il.cliente_id " +
        "LEFT JOIN salidas_lavadero sl              ON sl.elemento_ciclo_id = eci.id " +
        "WHERE cl.fecha_fin IS NOT NULL " +
        "GROUP BY eci.id, eci.ciclo_id, cl.lavarropas_numero, ecl.ingreso_id, il.cliente_id, c.nombre, " +
        "         cel.nombre, eci.cantidad, cl.fecha_fin " +
        "HAVING ya_lista < eci.cantidad " +
        "ORDER BY cl.fecha_fin, c.nombre, cel.nombre";

    /**
     * Salidas ya marcadas como Listo y sin destino asignado.
     *
     * <p>Rehace el mismo recorrido hacia atrás ({@code sl -> eci -> cl} y
     * {@code eci -> ecl -> il -> clientes}) para traer lavarropas, fecha de fin de ciclo y
     * cliente: no alcanza con leer {@code salidas_lavadero}.</p>
     */
    private static final String SQL_LISTAS_SIN_DESTINO =
        "SELECT sl.id AS salida_id, sl.cantidad, sl.fecha_listo, " +
        "       eci.ciclo_id, cl.lavarropas_numero, cl.fecha_fin, ecl.ingreso_id, " +
        "       il.cliente_id, c.nombre AS cliente, cel.nombre AS elemento " +
        "FROM salidas_lavadero sl " +
        "JOIN elementos_ciclo_lavadero eci          ON eci.id = sl.elemento_ciclo_id " +
        "JOIN ciclos_lavadero cl                    ON cl.id  = eci.ciclo_id " +
        "JOIN elementos_clasificacion_lavadero ecl  ON ecl.id = eci.elemento_clasificacion_id " +
        "JOIN catalogo_elementos_lavadero cel       ON cel.id = ecl.elemento_id " +
        "JOIN ingresos_lavadero il                  ON il.id  = ecl.ingreso_id " +
        "JOIN clientes c                            ON c.id   = il.cliente_id " +
        "WHERE sl.destino IS NULL " +
        "ORDER BY sl.fecha_listo, c.nombre, cel.nombre, sl.id";

    /**
     * Saldo pendiente de una tanda, releído dentro de la transacción.
     *
     * <p>Exige {@code fecha_fin IS NOT NULL}: una tanda de un ciclo todavía activo no está
     * lavada, así que no devuelve fila y se trata como saldo inexistente en vez de dejarla
     * pasar por no estar en la pantalla de la que salió.</p>
     */
    private static final String SQL_SALDO_PENDIENTE =
        "SELECT eci.cantidad - COALESCE(SUM(sl.cantidad), 0) AS pendiente " +
        "FROM elementos_ciclo_lavadero eci " +
        "JOIN ciclos_lavadero cl        ON cl.id = eci.ciclo_id " +
        "LEFT JOIN salidas_lavadero sl  ON sl.elemento_ciclo_id = eci.id " +
        "WHERE eci.id = ? AND cl.fecha_fin IS NOT NULL " +
        "GROUP BY eci.id, eci.cantidad";

    private static final String SQL_INSERTAR_SALIDA =
        "INSERT INTO salidas_lavadero (elemento_ciclo_id, cantidad, fecha_listo) VALUES (?, ?, NOW())";

    private static final String SQL_BORRAR_SALIDA_SIN_DESTINO =
        "DELETE FROM salidas_lavadero WHERE id = ? AND destino IS NULL";

    // ── lectura ──────────────────────────────────────────────────────────────

    public List<ElementoLavadoPendiente> obtenerLavadosPendientesDeListo() {
        List<ElementoLavadoPendiente> lista = new ArrayList<>();
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_PENDIENTES_DE_LISTO);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(new ElementoLavadoPendiente(
                    rs.getInt("elemento_ciclo_id"),
                    rs.getInt("ciclo_id"),
                    rs.getInt("lavarropas_numero"),
                    rs.getInt("ingreso_id"),
                    rs.getInt("cliente_id"),
                    rs.getString("cliente"),
                    rs.getString("elemento"),
                    rs.getInt("cantidad"),
                    rs.getInt("ya_lista"),
                    rs.getObject("fecha_fin", LocalDateTime.class)
                ));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error al obtener los elementos lavados pendientes de Listo", e);
        }
        return lista;
    }

    public List<SalidaLista> obtenerListasSinDestino() {
        List<SalidaLista> lista = new ArrayList<>();
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_LISTAS_SIN_DESTINO);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(new SalidaLista(
                    rs.getInt("salida_id"),
                    rs.getInt("ciclo_id"),
                    rs.getInt("lavarropas_numero"),
                    rs.getInt("ingreso_id"),
                    rs.getInt("cliente_id"),
                    rs.getString("cliente"),
                    rs.getString("elemento"),
                    rs.getInt("cantidad"),
                    rs.getObject("fecha_fin", LocalDateTime.class),
                    rs.getObject("fecha_listo", LocalDateTime.class)
                ));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error al obtener las salidas listas sin destino", e);
        }
        return lista;
    }

    // ── escritura ────────────────────────────────────────────────────────────

    /**
     * Marca como Listo la selección entera en una sola transacción: o entran todas las marcas
     * o no entra ninguna.
     *
     * <p>El saldo de cada tanda se relee <b>dentro</b> de la transacción; no se confía en el
     * snapshot que tiene la pantalla. Sin esto, dos marcados seguidos sobre la misma lectura
     * dejan la tabla sobregirada sin que nada falle a la vista, que es el peor resultado
     * posible: no rompe nada visible, sólo deja los datos mal.</p>
     *
     * <p>Como el chequeo y la inserción se intercalan sobre la misma conexión, dos marcas de la
     * misma tanda dentro de la misma llamada se ven entre sí y tampoco pueden sobregirar juntas.</p>
     *
     * @throws BusinessException si la selección está vacía, si alguna cantidad no es positiva o
     *                           si alguna supera el saldo disponible. En todos los casos no queda
     *                           ninguna fila insertada.
     */
    public void marcarListo(List<MarcaListo> marcas) {
        validarMarcas(marcas);

        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            Connection conn = tx.get();
            try (PreparedStatement psSaldo    = conn.prepareStatement(SQL_SALDO_PENDIENTE);
                 PreparedStatement psInsertar = conn.prepareStatement(SQL_INSERTAR_SALIDA)) {
                for (MarcaListo marca : marcas) {
                    int elementoCicloId = marca.item().elementoCicloId();
                    int disponible      = saldoPendiente(psSaldo, elementoCicloId);
                    if (marca.cantidad() > disponible) {
                        throw new BusinessException(mensajeSaldoInsuficiente(marca, disponible));
                    }
                    psInsertar.setInt(1, elementoCicloId);
                    psInsertar.setInt(2, marca.cantidad());
                    psInsertar.executeUpdate();
                }
            }
            tx.commit();
        } catch (SQLException e) {
            throw new DatabaseException("Error al marcar salidas de lavadero como Listo", e);
        }
    }

    /**
     * Devuelve una salida al estado "lavado, sin doblar" borrándola.
     *
     * <p>Sólo se puede mientras no tenga destino: una vez derivada es definitiva.</p>
     *
     * @throws BusinessException si la salida ya se derivó o ya no existe.
     */
    public void volverALavado(int salidaId) {
        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            Connection conn = tx.get();
            try (PreparedStatement ps = conn.prepareStatement(SQL_BORRAR_SALIDA_SIN_DESTINO)) {
                ps.setInt(1, salidaId);
                if (ps.executeUpdate() == 0) {
                    throw new BusinessException(
                        "Esa salida ya no se puede volver a Lavado: o ya se le asignó un destino, "
                        + "o dejó de existir. Refrescá la pantalla.");
                }
            }
            tx.commit();
        } catch (SQLException e) {
            throw new DatabaseException("Error al volver a Lavado la salida " + salidaId, e);
        }
    }

    // ── privados ─────────────────────────────────────────────────────────────

    private void validarMarcas(List<MarcaListo> marcas) {
        if (marcas == null || marcas.isEmpty()) {
            throw new BusinessException("No hay ninguna cantidad para marcar como Listo.");
        }
        for (MarcaListo marca : marcas) {
            if (marca.cantidad() <= 0) {
                throw new BusinessException(
                    "La cantidad a marcar como Listo de " + describir(marca.item())
                    + " tiene que ser mayor que cero.");
            }
        }
    }

    /** Saldo actual de la tanda. Sin fila (ciclo activo o tanda inexistente) es saldo cero. */
    private int saldoPendiente(PreparedStatement psSaldo, int elementoCicloId) throws SQLException {
        psSaldo.setInt(1, elementoCicloId);
        try (ResultSet rs = psSaldo.executeQuery()) {
            return rs.next() ? rs.getInt("pendiente") : 0;
        }
    }

    private String mensajeSaldoInsuficiente(MarcaListo marca, int disponible) {
        return describir(marca.item()) + " ya no tiene " + marca.cantidad()
             + " disponibles (quedan " + disponible + "). Refrescá la pantalla.";
    }

    /** Identifica una tanda como la ve el operador: elemento, cliente y lavarropas. */
    private String describir(ElementoLavadoPendiente item) {
        return item.elementoNombre() + " (" + item.clienteNombre()
             + ", lavarropas " + item.lavarropasNumero() + ")";
    }
}
