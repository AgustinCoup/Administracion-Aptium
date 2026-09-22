package com.example.features.lavadero.dao;

import com.example.AbstractDAOTest;
import com.example.common.exception.BusinessException;
import com.example.common.exception.ConflictoConcurrenciaException;
import com.example.common.exception.DatabaseException;
import com.example.features.lavadero.model.Lavarropas;
import com.example.infrastructure.db.ConnectionPool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LavarropasDAOTest extends AbstractDAOTest {

    /** Los 13 lavarropas del seed de la V10; lo que se cree en un test va de acá para arriba. */
    private static final int PRIMER_NUMERO_LIBRE = 100;

    private final LavarropasDAO dao = new LavarropasDAO();

    @Override
    protected void limpiarTablas() throws SQLException {
        // Los 13 del seed son datos de referencia y no se borran, pero sí hay que devolverlos a
        // `activo = TRUE`: las bajas de un test dejarían a los siguientes mirando otra realidad.
        ejecutarSQL("DELETE FROM elementos_ciclo_lavadero");
        ejecutarSQL("DELETE FROM ciclos_lavadero");
        ejecutarSQL("DELETE FROM lavarropas WHERE numero >= " + PRIMER_NUMERO_LIBRE);
        ejecutarSQL("UPDATE lavarropas SET activo = TRUE");
    }

    // ── Lectura ───────────────────────────────────────────────────────────────

    @Test
    void obtenerTodos_retornaLosSeeds() {
        assertEquals(13, dao.obtenerTodos().size(), "Deben existir los 13 lavarropas del seed");
    }

    @Test
    void obtenerTodos_ordenadosPorNumero() {
        List<Lavarropas> lista = dao.obtenerTodos();

        for (int i = 0; i < lista.size() - 1; i++) {
            assertTrue(lista.get(i).getNumero() < lista.get(i + 1).getNumero(),
                "Los lavarropas deben estar ordenados por número");
        }
    }

    @Test
    void obtenerTodos_losDelSeedNacenActivos() {
        dao.obtenerTodos().forEach(lv -> assertTrue(lv.isActivo(),
            "El DEFAULT TRUE de la V26 deja activos a todos los preexistentes"));
    }

    @Test
    @DisplayName("obtenerTodos trae también los dados de baja: es lo que muestra Ajustes")
    void obtenerTodos_incluyeLosDeBaja() {
        dao.darDeBaja(1);

        List<Lavarropas> lista = dao.obtenerTodos();

        assertEquals(13, lista.size());
        assertFalse(buscar(lista, 1).isActivo());
    }

    @Test
    @DisplayName("obtenerDibujables deja afuera a los de baja SIN ciclo abierto")
    void obtenerDibujables_excluyeLosDeBajaSinCiclo() {
        dao.darDeBaja(1);

        List<Lavarropas> lista = dao.obtenerDibujables();

        assertEquals(12, lista.size());
        assertNull(buscarONull(lista, 1));
    }

    /**
     * El caso que sostiene el {@code OR EXISTS} de {@code SQL_DIBUJABLES}, y el que más fácil se
     * rompe "simplificando" la consulta a {@code WHERE activo = TRUE}.
     *
     * <p>Sin card no hay botón Finalizar: el ciclo no cierra nunca, el ingreso no llega a LAVADO,
     * y la ropa desaparece de Disponibles (que ya la cuenta como consumida) y de Salidas (que
     * espera un ciclo finalizado). Es el escenario que {@code SQL_CICLO_ACTIVO_DE_LAVARROPAS}
     * existe para impedir, entrando por otra puerta.</p>
     */
    @Test
    @DisplayName("obtenerDibujables SÍ trae un lavarropas de baja con un ciclo sin finalizar")
    void obtenerDibujables_incluyeLosDeBajaConCicloAbierto() throws SQLException {
        insertarCicloAbierto(1);
        darDeBajaPorSQL(1);   // a mano: darDeBaja() lo rechazaría, que es justo su trabajo

        List<Lavarropas> lista = dao.obtenerDibujables();

        assertNotNull(buscarONull(lista, 1),
            "sin card no hay botón Finalizar, y ese ciclo no cierra nunca");
        assertFalse(buscar(lista, 1).isActivo(), "se dibuja, pero no está activo");
    }

    // ── Alta ──────────────────────────────────────────────────────────────────

    @Test
    void agregar_numeroLibre_loCreaActivo() {
        dao.agregar(PRIMER_NUMERO_LIBRE);

        assertTrue(buscar(dao.obtenerTodos(), PRIMER_NUMERO_LIBRE).isActivo());
    }

    @Test
    @DisplayName("Alta duplicada de uno ACTIVO: el cartel dice sólo que ya existe")
    void agregar_numeroExistenteActivo_rechazaConElMensajeSimple() {
        BusinessException e = assertThrows(BusinessException.class, () -> dao.agregar(1));

        assertTrue(e.getMessage().contains("ya existe"));
        assertFalse(e.getMessage().contains("dado de baja"),
            "el #1 está activo: hablar de una baja sería mentir");
        assertFalse(e.getMessage().contains("Reactivalo"));
    }

    /**
     * El otro cartel. Son dos mensajes distintos a propósito: el número identifica a la máquina y
     * su historia cuelga de él, así que "ya existe pero está de baja" tiene una acción concreta
     * —reactivarlo, y recupera su historia— que "ya existe" no tiene.
     */
    @Test
    @DisplayName("Alta duplicada de uno DE BAJA: el cartel manda a reactivarlo")
    void agregar_numeroExistenteDeBaja_rechazaConElMensajeQueMandaAReactivar() {
        dao.darDeBaja(1);

        BusinessException e = assertThrows(BusinessException.class, () -> dao.agregar(1));

        assertTrue(e.getMessage().contains("dado de baja"));
        assertTrue(e.getMessage().contains("Reactivalo"));
    }

    @Test
    void agregar_duplicado_noPisaElEstadoDelQueYaEstaba() {
        dao.darDeBaja(1);

        assertThrows(BusinessException.class, () -> dao.agregar(1));

        assertFalse(buscar(dao.obtenerTodos(), 1).isActivo(),
            "el alta rechazada no puede reactivarlo por la ventana de atrás");
    }

    // ── Baja ──────────────────────────────────────────────────────────────────

    @Test
    void darDeBaja_sinCicloAbierto_loDesactiva() {
        dao.darDeBaja(2);

        assertFalse(buscar(dao.obtenerTodos(), 2).isActivo());
    }

    @Test
    @DisplayName("No se da de baja un lavarropas con un ciclo sin finalizar")
    void darDeBaja_conCicloAbierto_rechazaYLoDejaActivo() throws SQLException {
        insertarCicloAbierto(2);

        BusinessException e = assertThrows(BusinessException.class, () -> dao.darDeBaja(2));

        assertTrue(e.getMessage().contains("ciclo sin finalizar"));
        assertTrue(buscar(dao.obtenerTodos(), 2).isActivo(),
            "la transacción se revierte entera: sigue activo");
    }

    @Test
    void darDeBaja_conCicloYaFinalizado_loDesactiva() throws SQLException {
        insertarCicloFinalizado(2);

        dao.darDeBaja(2);

        assertFalse(buscar(dao.obtenerTodos(), 2).isActivo());
    }

    @Test
    @DisplayName("Segunda baja: el CAS no matchea y sale como conflicto")
    void darDeBaja_dosVeces_laSegundaEsConflicto() {
        dao.darDeBaja(2);

        assertThrows(ConflictoConcurrenciaException.class, () -> dao.darDeBaja(2));
    }

    // ── Reactivación ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Reactivar recupera la máquina, no crea una nueva: su historia sigue ahí")
    void reactivar_unoDeBaja_loVuelveAActivarConSuHistoria() throws SQLException {
        insertarCicloFinalizado(2);
        dao.darDeBaja(2);

        dao.reactivar(2);

        assertTrue(buscar(dao.obtenerTodos(), 2).isActivo());
        assertEquals(1, contar("SELECT COUNT(*) FROM ciclos_lavadero WHERE lavarropas_numero = 2"));
    }

    @Test
    void reactivar_unoYaActivo_esConflicto() {
        assertThrows(ConflictoConcurrenciaException.class, () -> dao.reactivar(2));
    }

    // ── Manejo de errores ─────────────────────────────────────────────────────

    /**
     * Antes de este paso {@code obtenerTodos()} se comía el {@code SQLException} y devolvía lista
     * vacía. Con la grilla de Ciclos armada desde la base, eso pinta una pantalla sin un solo
     * lavarropas y sin decir por qué — el fallo silencioso que esta suite tiene que fijar.
     */
    @Test
    @DisplayName("Un fallo de SQL sale como DatabaseException, no como lista vacía")
    void obtenerTodos_conLaTablaRota_lanzaEnVezDeDevolverVacio() throws SQLException {
        ejecutarSQL("ALTER TABLE lavarropas RENAME TO lavarropas_tmp");
        try {
            assertThrows(DatabaseException.class, dao::obtenerTodos);
            assertThrows(DatabaseException.class, dao::obtenerDibujables);
        } finally {
            ejecutarSQL("ALTER TABLE lavarropas_tmp RENAME TO lavarropas");
        }
    }

    /**
     * {@code darDeBaja} abre dos {@code FOR UPDATE}: bloquea y espera. Cuando la base corta esa
     * espera —lock wait timeout o deadlock— es un choque entre operadores, no una falla técnica, y
     * el operador tiene que leer "alguien se te adelantó". No alcanza con
     * {@code catch (SQLTransactionRollbackException)}: el timeout de MySQL (1205) viaja con
     * {@code SQLSTATE HY000} como {@code SQLException} pelada, y H2 corta con su propio 50200.
     *
     * <p>El bloqueo acá es <b>real</b>: otra conexión se queda con la fila y el primer
     * {@code FOR UPDATE} de {@code darDeBaja} espera hasta que la base lo corta (el
     * {@code LOCK_TIMEOUT} de H2, 2 s), así que el test tarda eso. Lo que <b>no</b> se puede
     * reproducir en H2 es el <em>deadlock cruzado</em> que el orden de bloqueo existe para evitar
     * —corre en {@code READ COMMITTED} y no toma gap locks—: eso se sostiene por razonamiento, y
     * está escrito en el javadoc de {@code CicloLavaderoDAO.SQL_LAVARROPAS_ACTIVO}.</p>
     */
    @Test
    @DisplayName("La contención de lock sale como conflicto, no como DatabaseException")
    void darDeBaja_contencionDeLock_saleComoConflicto() throws SQLException {
        try (Connection otro = ConnectionPool.getConnection()) {
            otro.setAutoCommit(false);
            try (Statement st = otro.createStatement();
                 ResultSet rs = st.executeQuery(
                     "SELECT activo FROM lavarropas WHERE numero = 3 FOR UPDATE")) {
                rs.next();
            }

            // El primer FOR UPDATE de darDeBaja choca contra el de arriba y la base corta la
            // espera. Eso es un choque entre operadores, no una falla técnica.
            assertThrows(ConflictoConcurrenciaException.class, () -> dao.darDeBaja(3));

            otro.rollback();
        }
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private void insertarCicloAbierto(int numero) throws SQLException {
        insertarCiclo(numero, "NULL");
    }

    private void insertarCicloFinalizado(int numero) throws SQLException {
        insertarCiclo(numero, "NOW()");
    }

    private void insertarCiclo(int numero, String fechaFin) throws SQLException {
        int jabonId = contar("SELECT id FROM catalogo_jabones ORDER BY id LIMIT 1");
        ejecutarSQL("INSERT INTO ciclos_lavadero "
            + "(lavarropas_numero, jabon_id, litros_jabon, tipo_lavado, fecha_inicio, fecha_fin, estado) "
            + "VALUES (" + numero + ", " + jabonId + ", 1.50, 'SUCIO', NOW(), " + fechaFin + ", "
            + (fechaFin.equals("NULL") ? "'ACTIVO'" : "'FINALIZADO'") + ")");
    }

    /** La baja que {@code darDeBaja} rechazaría: así se llega al estado que hay que soportar. */
    private void darDeBajaPorSQL(int numero) throws SQLException {
        ejecutarSQL("UPDATE lavarropas SET activo = FALSE WHERE numero = " + numero);
    }

    private static Lavarropas buscar(List<Lavarropas> lista, int numero) {
        Lavarropas encontrado = buscarONull(lista, numero);
        assertNotNull(encontrado, "No se encontró el lavarropas #" + numero);
        return encontrado;
    }

    private static Lavarropas buscarONull(List<Lavarropas> lista, int numero) {
        return lista.stream().filter(lv -> lv.getNumero() == numero).findFirst().orElse(null);
    }

    private int contar(String sql) {
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new IllegalStateException("Consulta fallida: " + sql, e);
        }
    }
}
