package com.example.features.lavadero.dao;

import com.example.common.constants.Constantes;
import com.example.common.dao.ControlConcurrencia;
import com.example.common.exception.BusinessException;
import com.example.common.exception.ConflictoConcurrenciaException;
import com.example.common.exception.DatabaseException;
import com.example.features.lavadero.model.Lavarropas;
import com.example.infrastructure.db.ConnectionPool;
import com.example.infrastructure.db.TransactionalConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;

/**
 * ABM de lavarropas, con baja <b>lógica</b>: nada se borra nunca.
 *
 * <p>La historia de ciclos cuelga del número, así que retirar una máquina apaga su {@code activo}
 * y reactivarla lo vuelve a prender — es la misma máquina, recupera su historia. Por eso el alta
 * rechaza un número que ya existe <b>incluso si está dado de baja</b>: una máquina nueva que
 * reemplaza a una retirada tiene que tener sus estadísticas aisladas de la anterior.</p>
 *
 * <p><b>Los errores salen como {@link DatabaseException}, nunca como lista vacía.</b> Hasta la
 * V27, {@code obtenerTodos()} se comía el {@code SQLException} y devolvía vacío; con la grilla de
 * Ciclos armada desde la base eso pintaría una pantalla sin ningún lavarropas y sin decir por qué.
 * Mismo manejo que {@code SalidaLavaderoDAO}.</p>
 */
public class LavarropasDAO {

    private static final Logger log = LoggerFactory.getLogger(LavarropasDAO.class);

    /**
     * Los lavarropas que la grilla de Ciclos tiene que dibujar. <b>No son "los activos"</b>, y el
     * nombre del método lo dice a propósito.
     *
     * <p>El {@code OR EXISTS} no es defensivo, es <b>obligatorio</b>. Un lavarropas dado de baja
     * con un ciclo todavía abierto —se llega ahí por una corrección manual en la base, o porque se
     * lo retiró sin finalizar el ciclo— no tendría card si la grilla se armara sólo con
     * {@code activo = TRUE}, y por lo tanto <b>no tendría botón Finalizar</b>: el ciclo no cierra
     * nunca, el ingreso no llega a LAVADO, y la ropa desaparece de las dos pantallas — de
     * Disponibles porque {@code SQL_DISPONIBLES} la cuenta como consumida, y de Salidas porque
     * nunca hubo un ciclo finalizado. Ropa física irrecuperable desde la UI y sin un solo cartel.
     * Es textualmente el desenlace que el javadoc de
     * {@link CicloLavaderoDAO#SQL_CICLO_ACTIVO_DE_LAVARROPAS} declara inaceptable.</p>
     *
     * <p>Esa card se dibuja en modo activo, que ya oculta el panel de configuración —no se le puede
     * cargar nada— y desaparece sola en la relectura que sigue a finalizar el ciclo.</p>
     */
    private static final String SQL_DIBUJABLES =
        "SELECT numero, activo FROM lavarropas " +
        "WHERE activo = TRUE " +
        "   OR EXISTS (SELECT 1 FROM ciclos_lavadero c " +
        "               WHERE c.lavarropas_numero = lavarropas.numero AND c.fecha_fin IS NULL) " +
        "ORDER BY numero";

    /** Todos, activos y de baja: es lo que alimenta la pantalla de Ajustes. */
    private static final String SQL_TODOS =
        "SELECT numero, activo FROM lavarropas ORDER BY numero";

    private static final String SQL_INSERTAR =
        "INSERT INTO lavarropas (numero, activo) VALUES (?, TRUE)";

    /** Sólo para armar el mensaje del alta rechazada. Ver {@link #agregar(int)}. */
    private static final String SQL_ESTADO =
        "SELECT activo FROM lavarropas WHERE numero = ?";

    /** CAS: 0 filas significa que otro ya lo dio de baja. */
    private static final String SQL_DAR_DE_BAJA =
        "UPDATE lavarropas SET activo = FALSE WHERE numero = ? AND activo = TRUE";

    /** CAS: 0 filas significa que otro ya lo reactivó. */
    private static final String SQL_REACTIVAR =
        "UPDATE lavarropas SET activo = TRUE WHERE numero = ? AND activo = FALSE";

    /** Ver {@link #SQL_DIBUJABLES}: incluye los inactivos con un ciclo sin finalizar. */
    public List<Lavarropas> obtenerDibujables() {
        return leer(SQL_DIBUJABLES, "dibujables");
    }

    public List<Lavarropas> obtenerTodos() {
        return leer(SQL_TODOS, "todos");
    }

    /**
     * Da de alta un lavarropas con el número que eligió el operador.
     *
     * <p><b>El {@code INSERT} va primero y el {@code SELECT} sólo después de que choque.</b> Al
     * revés —mirar si existe y después insertar— hay una ventana entre las dos sentencias en la
     * que otro operador crea el mismo número, y el rechazo se convierte en un error técnico por
     * violación de PK. Acá la PK <b>es</b> la guarda; la segunda lectura no decide nada, sólo elige
     * cuál de los dos carteles mostrar.</p>
     *
     * <p>Y esa segunda lectura va con la conexión del {@code INSERT} ya cerrada: ninguna operación
     * mantiene dos conexiones abiertas a la vez (el semáforo del pool reparte 5 permisos).</p>
     *
     * @throws BusinessException si el número ya existe — con mensaje distinto según esté activo o
     *                           dado de baja, porque el segundo tiene una acción concreta
     *                           (reactivarlo) que el primero no tiene
     */
    public void agregar(int numero) {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERTAR)) {
            ps.setInt(1, numero);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (esViolacionDeClave(e)) {
                log.warn("Alta rechazada: el lavarropas {} ya existe", numero);
                throw new BusinessException(mensajeDeDuplicado(numero));
            }
            log.error("Error al agregar el lavarropas {}", numero, e);
            throw new DatabaseException("Error al agregar el lavarropas #" + numero, e);
        }
    }

    /**
     * Retira un lavarropas del lavadero. No se puede si tiene un ciclo sin finalizar: ese ciclo
     * todavía tiene que poder cerrarse.
     *
     * <p><b>El orden de bloqueo es {@code lavarropas} → {@code ciclos_lavadero}, el mismo que
     * {@link CicloLavaderoDAO#lanzarTanda}, y no es opinable.</b> Las dos operaciones compiten por
     * las mismas filas; invertir el orden en una de las dos da un deadlock cruzado que <b>H2 no
     * reproduce</b>. El bloqueo de {@code lavarropas} va primero porque es el único
     * <b>exclusivo</b>: el de {@code ciclos_lavadero} es un gap lock sobre un lavarropas libre, y
     * dos gap locks entre sí son compatibles, así que no ordena nada al tomarse. El entrelazado
     * completo está en el javadoc de {@link CicloLavaderoDAO#SQL_LAVARROPAS_ACTIVO}.</p>
     *
     * <p>Las dos lecturas son bloqueantes, así que no hay ninguna lectura no bloqueante antes de
     * ellas: bajo {@code REPEATABLE READ} la vista se fija en la primera lectura no bloqueante, y
     * una guarda tomada después leería un estado anterior a su propio bloqueo.</p>
     *
     * <p><b>Esta pantalla no es la que impide lanzar en un lavarropas de baja.</b> El staging vive
     * en la memoria de cada cliente: otra máquina puede tener ropa cargada acá y la baja no puede
     * enterarse. La verificación que sirve es la de {@code lanzarTanda}, dentro de su
     * transacción.</p>
     */
    public void darDeBaja(int numero) {
        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            Connection conn = tx.get();
            bloquear(conn, numero);
            exigirSinCicloActivo(conn, numero);
            try (PreparedStatement ps = conn.prepareStatement(SQL_DAR_DE_BAJA)) {
                ps.setInt(1, numero);
                ControlConcurrencia.exigirFilaAfectada(ps.executeUpdate(),
                    Constantes.Mensajes.CONFLICTO_LAVARROPAS_YA_DE_BAJA);
            }
            tx.commit();
        } catch (SQLException e) {
            // darDeBaja abre dos FOR UPDATE: bloquea y espera hasta el innodb_lock_wait_timeout
            // (50 s), y además puede comerse un deadlock. Cuando la base corta la espera eso es un
            // choque, no una falla técnica, y tiene que salir como tal — si no, el operador lee
            // "error al dar de baja" en vez de "alguien se te adelantó". Y no alcanza con
            // catch (SQLTransactionRollbackException): el lock wait timeout (1205) viaja con
            // SQLSTATE HY000 como SQLException pelada. Nada quedó escrito.
            if (ControlConcurrencia.esContencionDeLock(e)) {
                log.warn("Baja del lavarropas {} abortada por la base (contención de lock)", numero, e);
                throw new ConflictoConcurrenciaException(Constantes.Mensajes.CONFLICTO_GENERICO);
            }
            log.error("Error al dar de baja el lavarropas {}", numero, e);
            throw new DatabaseException("Error al dar de baja el lavarropas #" + numero, e);
        }
    }

    /**
     * Vuelve a poner en servicio un lavarropas retirado, que recupera su historia: es la misma
     * máquina.
     *
     * <p>Sin transacción, a diferencia de {@link #darDeBaja(int)}: es una sola sentencia, y su
     * propio {@code WHERE activo = FALSE} es la guarda. Tampoco necesita mirar los ciclos — un
     * lavarropas que vuelve a estar disponible no puede romper nada que estuviera abierto.</p>
     */
    public void reactivar(int numero) {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_REACTIVAR)) {
            ps.setInt(1, numero);
            ControlConcurrencia.exigirFilaAfectada(ps.executeUpdate(),
                Constantes.Mensajes.CONFLICTO_LAVARROPAS_YA_ACTIVO);
        } catch (SQLException e) {
            log.error("Error al reactivar el lavarropas {}", numero, e);
            throw new DatabaseException("Error al reactivar el lavarropas #" + numero, e);
        }
    }

    // ── privados ─────────────────────────────────────────────────────────────

    private List<Lavarropas> leer(String sql, String que) {
        List<Lavarropas> resultado = new ArrayList<>();
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                resultado.add(new Lavarropas(rs.getInt("numero"), rs.getBoolean("activo")));
            }
        } catch (SQLException e) {
            log.error("Error al obtener los lavarropas ({})", que, e);
            throw new DatabaseException("Error al obtener los lavarropas", e);
        }
        return resultado;
    }

    /** Toma el bloqueo exclusivo de la fila. Ver el javadoc de {@link #darDeBaja(int)}. */
    private void bloquear(Connection conn, int numero) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(CicloLavaderoDAO.SQL_LAVARROPAS_ACTIVO)) {
            ps.setInt(1, numero);
            try (ResultSet rs = ps.executeQuery()) {
                // Sin fila el lavarropas no existe, y el CAS de abajo lo rechaza con 0 filas.
                rs.next();
            }
        }
    }

    /**
     * La misma guarda que usa el lanzamiento, con la misma constante: un lavarropas con un ciclo
     * sin finalizar no se puede retirar, porque ese ciclo todavía tiene que poder cerrarse.
     */
    private void exigirSinCicloActivo(Connection conn, int numero) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(CicloLavaderoDAO.SQL_CICLO_ACTIVO_DE_LAVARROPAS)) {
            ps.setInt(1, numero);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    log.warn("Baja rechazada: el lavarropas {} tiene el ciclo {} sin finalizar",
                        numero, rs.getInt("id"));
                    throw new BusinessException(
                        String.format(Constantes.Mensajes.CONFLICTO_LAVARROPAS_EN_USO, numero));
                }
            }
        }
    }

    /**
     * Segunda lectura, con la conexión del {@code INSERT} ya cerrada, sólo para elegir el cartel.
     * Si para cuando llega acá el número ya no existe —otro lo borró a mano entre las dos—, el
     * mensaje neutro es el correcto: lo único seguro es que el alta no entró.
     */
    private String mensajeDeDuplicado(int numero) {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_ESTADO)) {
            ps.setInt(1, numero);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && !rs.getBoolean("activo")) {
                    return String.format(Constantes.Mensajes.LAVARROPAS_YA_EXISTE_DE_BAJA, numero);
                }
            }
        } catch (SQLException e) {
            log.error("Error al leer el estado del lavarropas {} para el mensaje de duplicado",
                numero, e);
        }
        return String.format(Constantes.Mensajes.LAVARROPAS_YA_EXISTE, numero);
    }

    /**
     * Violación de clave. El tipo es lo que mira primero; el {@code SQLState} que empieza con
     * {@code "23"} (integrity constraint violation, igual en H2 y MySQL) queda como red de
     * seguridad para drivers que no lo mapeen al subtipo.
     */
    private static boolean esViolacionDeClave(SQLException e) {
        return e instanceof SQLIntegrityConstraintViolationException
            || (e.getSQLState() != null && e.getSQLState().startsWith("23"));
    }
}
