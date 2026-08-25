package com.example.features.lavadero.dao;

import com.example.common.exception.BusinessException;
import com.example.common.exception.DatabaseException;
import com.example.features.lavadero.dao.derivadores.DerivadorSalidas;
import com.example.features.lavadero.model.DestinoSalida;
import com.example.features.lavadero.model.ElementoLavadoPendiente;
import com.example.features.lavadero.model.EstadoIngresoLavadero;
import com.example.features.lavadero.model.MarcaListo;
import com.example.features.lavadero.model.SalidaLista;
import com.example.infrastructure.db.ConnectionPool;
import com.example.infrastructure.db.TransactionalConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    /**
     * Salida sin destino donde acumular lo que se marque de esta tanda, si ya hay una.
     *
     * <p>Se resuelve en dos sentencias (buscar el id, después sumar) y no con un {@code UPDATE}
     * con subconsulta sobre la misma tabla: MySQL no lo permite. {@code MIN(id)} elige siempre
     * la misma fila, así que datos viejos con más de una duplicada no se multiplican.</p>
     */
    private static final String SQL_SALIDA_ABIERTA_DE_TANDA =
        "SELECT MIN(id) AS id FROM salidas_lavadero WHERE elemento_ciclo_id = ? AND destino IS NULL";

    /** La fecha pasa a ser la del último agregado: es cuándo quedó listo todo lo que hay ahí. */
    private static final String SQL_SUMAR_A_SALIDA =
        "UPDATE salidas_lavadero SET cantidad = cantidad + ?, fecha_listo = NOW() WHERE id = ?";

    private static final String SQL_BORRAR_SALIDA_SIN_DESTINO =
        "DELETE FROM salidas_lavadero WHERE id = ? AND destino IS NULL";

    /** Existe y sigue sin destino. Releído dentro de la transacción, antes de escribir nada. */
    private static final String SQL_SIGUE_SIN_DESTINO =
        "SELECT 1 FROM salidas_lavadero WHERE id = ? AND destino IS NULL";

    /**
     * El {@code AND destino IS NULL} es redundante con la relectura previa y está a propósito:
     * si igual no afecta ninguna fila, el conteo lo detecta y la transacción entera se cae.
     */
    private static final String SQL_ESTAMPAR_DESTINO =
        "UPDATE salidas_lavadero SET destino = ?, equipo_otros_id = ?, fecha_salida = NOW() " +
        "WHERE id = ? AND destino IS NULL";

    /**
     * Cuánto clasificó un ingreso y cuánto de eso ya salió con destino asignado.
     *
     * <p>El total sale de la clasificación y no de los ciclos: un ingreso está terminado cuando
     * todo lo que se clasificó tiene destino, no cuando todo lo que se lavó lo tiene.</p>
     */
    private static final String SQL_TOTAL_Y_DERIVADO_DEL_INGRESO =
        "SELECT (SELECT COALESCE(SUM(ecl.cantidad), 0) " +
        "          FROM elementos_clasificacion_lavadero ecl " +
        "         WHERE ecl.ingreso_id = ?)                                          AS total, " +
        "       (SELECT COALESCE(SUM(sl.cantidad), 0) " +
        "          FROM salidas_lavadero sl " +
        "          JOIN elementos_ciclo_lavadero eci           ON eci.id  = sl.elemento_ciclo_id " +
        "          JOIN elementos_clasificacion_lavadero ecl2  ON ecl2.id = eci.elemento_clasificacion_id " +
        "         WHERE ecl2.ingreso_id = ? AND sl.destino IS NOT NULL)              AS derivado";

    private static final String SQL_FINALIZAR_INGRESO =
        "UPDATE ingresos_lavadero SET estado = '" + EstadoIngresoLavadero.FINALIZADO + "' WHERE id = ?";

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
                 PreparedStatement psAbierta  = conn.prepareStatement(SQL_SALIDA_ABIERTA_DE_TANDA);
                 PreparedStatement psSumar    = conn.prepareStatement(SQL_SUMAR_A_SALIDA);
                 PreparedStatement psInsertar = conn.prepareStatement(SQL_INSERTAR_SALIDA)) {
                for (MarcaListo marca : marcas) {
                    int elementoCicloId = marca.item().elementoCicloId();
                    int disponible      = saldoPendiente(psSaldo, elementoCicloId);
                    if (marca.cantidad() > disponible) {
                        throw new BusinessException(mensajeSaldoInsuficiente(marca, disponible));
                    }
                    acumularOInsertar(psAbierta, psSumar, psInsertar, elementoCicloId, marca.cantidad());
                }
            }
            tx.commit();
        } catch (SQLException e) {
            throw new DatabaseException("Error al marcar salidas de lavadero como Listo", e);
        }
    }

    /**
     * Devuelve una salida al estado "lavado, sin doblar". Atajo del método en lote.
     *
     * @throws BusinessException si la salida ya se derivó o ya no existe.
     */
    public void volverALavado(int salidaId) {
        volverALavado(List.of(salidaId));
    }

    /**
     * Devuelve al estado "lavado, sin doblar" la selección entera, en una sola transacción:
     * o vuelven todas o no vuelve ninguna.
     *
     * <p>Simétrico de {@link #marcarListo(List)} y por el mismo motivo: revertir cuatro filas
     * arrastradas y que la tercera falle dejaría dos revertidas y dos no, un estado que nadie
     * pidió y que la pantalla no muestra como parcial.</p>
     *
     * <p>Sólo se puede mientras la salida no tenga destino: una vez derivada es definitiva. El
     * {@code AND destino IS NULL} del {@code DELETE} hace las dos cosas a la vez — filtra y
     * detecta —, así que no hace falta releer antes.</p>
     *
     * @throws BusinessException si la selección está vacía o si alguna salida ya se derivó o
     *                           dejó de existir. En todos los casos no se borra ninguna fila.
     */
    public void volverALavado(List<Integer> salidaIds) {
        if (salidaIds == null || salidaIds.isEmpty()) {
            throw new BusinessException("No hay ninguna salida para volver a Lavado.");
        }

        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            Connection conn = tx.get();
            try (PreparedStatement ps = conn.prepareStatement(SQL_BORRAR_SALIDA_SIN_DESTINO)) {
                for (Integer salidaId : salidaIds) {
                    if (salidaId == null || salidaId <= 0) {
                        throw new BusinessException("Hay una salida sin identificar en la selección.");
                    }
                    ps.setInt(1, salidaId);
                    if (ps.executeUpdate() == 0) {
                        throw new BusinessException(mensajeNoRevertible(salidaId));
                    }
                }
            }
            tx.commit();
        } catch (SQLException e) {
            throw new DatabaseException("Error al volver a Lavado las salidas de lavadero", e);
        }
    }

    /**
     * Le asigna destino definitivo a una selección de salidas listas, en una sola transacción.
     *
     * <p>Lo que el derivador cree en otra feature (el ingreso de CDE) y el estampado del destino
     * acá tienen que ser atómicos: si el ingreso se crea y el destino no se estampa, la misma
     * ropa se puede volver a derivar y entra dos veces al CDE; si se estampa y el ingreso no se
     * crea, la ropa desaparece del circuito. Por eso el derivador recibe <b>esta</b>
     * {@link Connection} en vez de abrir la suya.</p>
     *
     * <p>El destino se relee antes de escribir nada: la pantalla trabaja sobre un snapshot y
     * derivar es irreversible, así que una selección vieja tiene que fallar entera y no derivar
     * de nuevo lo que ya salió.</p>
     *
     * @throws BusinessException si la selección está vacía o si alguna salida ya tiene destino.
     *                           No queda nada escrito.
     */
    public void derivar(DerivadorSalidas derivador, List<SalidaLista> salidas) {
        if (salidas == null || salidas.isEmpty()) {
            throw new BusinessException("No hay ninguna salida para derivar.");
        }

        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            Connection conn = tx.get();
            verificarSinDestino(conn, salidas);

            Map<Integer, Integer> equipoPorSalida = derivador.derivar(conn, salidas);
            estamparDestino(conn, salidas, derivador.accion().getDestinoPersistido(), equipoPorSalida);

            Set<Integer> ingresoIds = salidas.stream().map(SalidaLista::ingresoId).collect(Collectors.toSet());
            finalizarIngresosCompletos(conn, ingresoIds);

            tx.commit();
        } catch (SQLException e) {
            throw new DatabaseException("Error al derivar las salidas de lavadero", e);
        }
    }

    // ── privados ─────────────────────────────────────────────────────────────

    /** Ninguna salida de la selección puede tener destino ya asignado. */
    private void verificarSinDestino(Connection conn, List<SalidaLista> salidas) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_SIGUE_SIN_DESTINO)) {
            for (SalidaLista salida : salidas) {
                ps.setInt(1, salida.salidaId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new BusinessException(
                            salida.elementoNombre() + " (" + salida.clienteNombre()
                            + ") ya tiene un destino asignado o dejó de existir, así que no se "
                            + "derivó nada. Refrescá la pantalla.");
                    }
                }
            }
        }
    }

    /** Estampa destino, fecha y el ingreso de CDE creado (si la acción creó alguno). */
    private void estamparDestino(Connection conn,
                                 List<SalidaLista> salidas,
                                 DestinoSalida destino,
                                 Map<Integer, Integer> equipoPorSalida) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_ESTAMPAR_DESTINO)) {
            for (SalidaLista salida : salidas) {
                Integer equipoOtrosId = equipoPorSalida.get(salida.salidaId());
                ps.setString(1, destino.name());
                if (equipoOtrosId != null) ps.setInt(2, equipoOtrosId);
                else                       ps.setNull(2, Types.INTEGER);
                ps.setInt(3, salida.salidaId());
                if (ps.executeUpdate() != 1) {
                    throw new SQLException(
                        "No se pudo estampar el destino de la salida " + salida.salidaId()
                        + ": dejó de estar sin destino durante la derivación");
                }
            }
        }
    }

    /**
     * Pasa a FINALIZADO los ingresos cuya clasificación ya salió entera.
     *
     * <p>Mismo patrón que {@code CicloLavaderoDAO.actualizarEstadoIngresosAfectados}: por cada
     * ingreso tocado se pregunta si ya está todo cubierto y recién ahí se actualiza.</p>
     */
    private void finalizarIngresosCompletos(Connection conn, Set<Integer> ingresoIds) throws SQLException {
        if (ingresoIds.isEmpty()) return;
        try (PreparedStatement psVerificar = conn.prepareStatement(SQL_TOTAL_Y_DERIVADO_DEL_INGRESO);
             PreparedStatement psFinalizar = conn.prepareStatement(SQL_FINALIZAR_INGRESO)) {
            for (int ingresoId : ingresoIds) {
                psVerificar.setInt(1, ingresoId);
                psVerificar.setInt(2, ingresoId);
                try (ResultSet rs = psVerificar.executeQuery()) {
                    if (rs.next() && rs.getInt("derivado") >= rs.getInt("total")) {
                        psFinalizar.setInt(1, ingresoId);
                        psFinalizar.executeUpdate();
                    }
                }
            }
        }
    }

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

    /**
     * Suma la cantidad a la salida sin destino que ya tenga esa tanda, o crea una si no hay.
     *
     * <p>Marcar 4 y después 3 de la misma tanda tiene que dar <b>una</b> salida de 7 y no dos
     * filas: para el operador es la misma ropa, doblada en dos ratos. Como consecuencia, las
     * cantidades de una tanda ya no se pueden despachar a destinos distintos — se deriva todo
     * junto.</p>
     *
     * <p>Sólo se acumula sobre salidas sin destino: una ya derivada es definitiva, así que lo
     * que se marque después arranca una salida nueva.</p>
     */
    private void acumularOInsertar(PreparedStatement psAbierta, PreparedStatement psSumar,
                                   PreparedStatement psInsertar, int elementoCicloId,
                                   int cantidad) throws SQLException {
        Integer salidaAbierta = salidaAbiertaDe(psAbierta, elementoCicloId);
        if (salidaAbierta != null) {
            psSumar.setInt(1, cantidad);
            psSumar.setInt(2, salidaAbierta);
            psSumar.executeUpdate();
            return;
        }
        psInsertar.setInt(1, elementoCicloId);
        psInsertar.setInt(2, cantidad);
        psInsertar.executeUpdate();
    }

    /** Id de la salida sin destino de esa tanda, o {@code null} si todavía no hay ninguna. */
    private Integer salidaAbiertaDe(PreparedStatement psAbierta, int elementoCicloId) throws SQLException {
        psAbierta.setInt(1, elementoCicloId);
        try (ResultSet rs = psAbierta.executeQuery()) {
            if (!rs.next()) return null;
            int id = rs.getInt("id");
            return rs.wasNull() ? null : id;
        }
    }

    /** Saldo actual de la tanda. Sin fila (ciclo activo o tanda inexistente) es saldo cero. */
    private int saldoPendiente(PreparedStatement psSaldo, int elementoCicloId) throws SQLException {
        psSaldo.setInt(1, elementoCicloId);
        try (ResultSet rs = psSaldo.executeQuery()) {
            return rs.next() ? rs.getInt("pendiente") : 0;
        }
    }

    /**
     * Identifica la salida por id: es lo único que llega hasta acá, y el nombre del elemento
     * no distinguiría dos tandas iguales del mismo cliente. Lo accionable es refrescar.
     */
    private String mensajeNoRevertible(int salidaId) {
        return "La salida #" + salidaId + " ya no se puede volver a Lavado: o ya se le asignó un "
             + "destino, o dejó de existir. No se revirtió ninguna. Refrescá la pantalla.";
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
