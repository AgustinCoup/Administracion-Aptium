package com.example.infrastructure.db;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Techo de consulta y cancelación por token.
 *
 * <p>Dos tipos de test conviven a propósito. Los del {@code queryTimeout} usan H2 <b>real</b> y leen
 * el valor de vuelta con {@code getQueryTimeout()}, porque en H2 2.x el timeout es a nivel sesión y
 * asumirlo en vez de leerlo es exactamente el error que cuesta una depuración larga; cada uno abre
 * su propia conexión para que no le llegue el timeout que dejó otro test en la misma sesión. Los de
 * cancelación usan dobles: el test no puede depender de que H2 cancele igual que MySQL, y con un
 * doble se observa <em>a quién</em> se le pidió cancelar, que es lo que importa.
 */
class ConexionesSupervisadasTest {

    private static final AtomicInteger BASES = new AtomicInteger();

    private static final Runnable SIN_PERMISO = () -> { };

    @AfterEach
    void limpiarHilo() {
        TokenTarea.desasociarDelHiloActual();
    }

    // ---------- techo de consulta (anti-patrón A12) ----------

    @Test
    @DisplayName("una conexión en autocommit deja sus sentencias con el techo de consulta")
    void autocommit_poneQueryTimeout() throws Exception {
        try (Connection real = h2();
             Connection supervisada = ConexionesSupervisadas.envolver(real, SIN_PERMISO);
             Statement sentencia = supervisada.createStatement()) {

            assertEquals(ConnectionPool.TIMEOUT_CONSULTA_S, sentencia.getQueryTimeout());
        }
    }

    @Test
    @DisplayName("una conexión transaccional NO lleva techo de consulta")
    void transaccional_noPoneQueryTimeout() throws Exception {
        // El test de A12: las guardas FOR UPDATE del lavadero esperan por diseño hasta el
        // innodb_lock_wait_timeout de 50 s. Un techo de 30 s las taparía y el choque entre dos
        // operadores saldría como error técnico en vez de como "alguien se te adelantó".
        try (Connection real = h2()) {
            real.setAutoCommit(false);
            try (Connection supervisada = ConexionesSupervisadas.envolver(real, SIN_PERMISO);
                 PreparedStatement sentencia = supervisada.prepareStatement("SELECT 1")) {

                assertEquals(0, sentencia.getQueryTimeout());
            }
        }
    }

    @Test
    @DisplayName("unwrap devuelve el proxy, no la conexión cruda")
    void unwrap_noDejaEscaparLaConexionReal() throws Exception {
        try (Connection real = h2();
             Connection supervisada = ConexionesSupervisadas.envolver(real, SIN_PERMISO)) {

            assertSame(supervisada, supervisada.unwrap(Connection.class));
            assertThrows(SQLException.class, () -> supervisada.unwrap(org.h2.jdbc.JdbcConnection.class));
        }
    }

    @Test
    @DisplayName("cerrar la conexión libera el permiso aunque el cierre real falle")
    void close_liberaElPermisoUnaSolaVez() throws Exception {
        Connection real = mock(Connection.class);
        org.mockito.Mockito.doThrow(new SQLException("boom")).when(real).close();
        AtomicInteger liberaciones = new AtomicInteger();

        Connection supervisada = ConexionesSupervisadas.envolver(real, liberaciones::incrementAndGet);

        assertThrows(SQLException.class, supervisada::close);
        supervisada.close();   // close() de JDBC es idempotente: no puede liberar dos veces

        assertEquals(1, liberaciones.get());
    }

    // ---------- registro y cancelación ----------

    @Test
    @DisplayName("el registro queda vacío después de cerrar las conexiones")
    void registro_noRetieneTokensMuertos() throws Exception {
        TokenTarea token = TokenTarea.nuevo("tarea");
        TokenTarea.asociarAlHiloActual(token);

        Connection unaSupervisada = ConexionesSupervisadas.envolver(conexionFalsa(mock(Statement.class)), SIN_PERMISO);
        Connection otraSupervisada = ConexionesSupervisadas.envolver(conexionFalsa(mock(Statement.class)), SIN_PERMISO);
        unaSupervisada.createStatement();
        otraSupervisada.createStatement();

        unaSupervisada.close();
        otraSupervisada.close();

        assertEquals(0, ConexionesSupervisadas.tareasRegistradas());
    }

    @Test
    @DisplayName("cerrar una conexión no desregistra las sentencias de la otra del mismo token")
    void dosConexionesDelMismoToken_noSePisan() throws Exception {
        TokenTarea token = TokenTarea.nuevo("tarea");
        TokenTarea.asociarAlHiloActual(token);

        Statement sentenciaCerrada = mock(Statement.class);
        Statement sentenciaViva    = mock(Statement.class);
        Connection conexionCerrada = ConexionesSupervisadas.envolver(conexionFalsa(sentenciaCerrada), SIN_PERMISO);
        Connection conexionViva    = ConexionesSupervisadas.envolver(conexionFalsa(sentenciaViva), SIN_PERMISO);
        conexionCerrada.createStatement();
        conexionViva.createStatement();

        conexionCerrada.close();
        ConexionesSupervisadas.cancelarDe(token);

        verify(sentenciaCerrada, never()).cancel();
        verify(sentenciaViva).cancel();

        conexionViva.close();
    }

    @Test
    @DisplayName("cancelar un token sin sentencias no lanza")
    void cancelarDe_sinSentenciasNoLanza() {
        assertDoesNotThrow(() -> ConexionesSupervisadas.cancelarDe(TokenTarea.nuevo("vacia")));
        assertDoesNotThrow(() -> ConexionesSupervisadas.cancelarDe(null));
    }

    @Test
    @DisplayName("una sentencia que ya no se puede cancelar no propaga el error")
    void cancelarDe_esBestEffort() throws Exception {
        TokenTarea token = TokenTarea.nuevo("tarea");
        TokenTarea.asociarAlHiloActual(token);
        Statement sentencia = mock(Statement.class);
        org.mockito.Mockito.doThrow(new SQLException("ya terminó")).when(sentencia).cancel();

        Connection supervisada = ConexionesSupervisadas.envolver(conexionFalsa(sentencia), SIN_PERMISO);
        supervisada.createStatement();

        assertDoesNotThrow(() -> ConexionesSupervisadas.cancelarDe(token));
        supervisada.close();
    }

    @Test
    @DisplayName("cancelar una tarea YA TERMINADA no toca la consulta viva de la que heredó el hilo")
    void cancelarTareaMuerta_noMataLaConsultaDeLaSiguiente() throws Exception {
        // Anti-patrón A13, y el único test que lo detecta. SwingWorker reutiliza sus diez hilos, y
        // RefrescadorPantallas.refrescarAhora() cancela la ejecución anterior de forma
        // incondicional aunque ya haya terminado. Con un registro por hilo —la forma obvia de
        // escribir esto— la segunda verificación de abajo fallaría: se estaría matando la consulta
        // de otra pantalla.
        Statement sentenciaDeLaTerminada = mock(Statement.class);
        TokenTarea tareaTerminada = TokenTarea.nuevo("refresco-historial-lavadero");
        TokenTarea.asociarAlHiloActual(tareaTerminada);
        Connection conexionDeLaTerminada =
            ConexionesSupervisadas.envolver(conexionFalsa(sentenciaDeLaTerminada), SIN_PERMISO);
        conexionDeLaTerminada.createStatement();
        // La tarea termina: TareaUI saca el token del hilo y olvida su registro.
        TokenTarea.desasociarDelHiloActual();
        ConexionesSupervisadas.olvidar(tareaTerminada);

        // El MISMO hilo arranca otra tarea, de otra pantalla, y su consulta está en vuelo.
        Statement sentenciaEnVuelo = mock(Statement.class);
        TokenTarea tareaNueva = TokenTarea.nuevo("refresco-operativo");
        TokenTarea.asociarAlHiloActual(tareaNueva);
        Connection conexionEnVuelo =
            ConexionesSupervisadas.envolver(conexionFalsa(sentenciaEnVuelo), SIN_PERMISO);
        conexionEnVuelo.createStatement();

        ConexionesSupervisadas.cancelarDe(tareaTerminada);

        verify(sentenciaDeLaTerminada, never()).cancel();
        verify(sentenciaEnVuelo, never()).cancel();

        // Contraprueba: sin esto el test pasaría igual si la sentencia nunca se hubiera
        // registrado, y no estaría verificando nada.
        ConexionesSupervisadas.cancelarDe(tareaNueva);
        verify(sentenciaEnVuelo).cancel();

        conexionDeLaTerminada.close();
        conexionEnVuelo.close();
    }

    // ---------- helpers ----------

    /** Conexión en autocommit que devuelve siempre la misma sentencia, cualquiera sea el overload. */
    private static Connection conexionFalsa(Statement sentencia) throws SQLException {
        Connection real = mock(Connection.class);
        when(real.getAutoCommit()).thenReturn(true);
        when(real.createStatement()).thenReturn(sentencia);
        when(real.prepareStatement(anyString())).thenReturn(mock(PreparedStatement.class));
        return real;
    }

    /** Una base H2 nueva por test: la sesión arrastra el QUERY_TIMEOUT que le haya puesto otro. */
    private static Connection h2() throws SQLException {
        return DriverManager.getConnection("jdbc:h2:mem:supervisadas_" + BASES.incrementAndGet(), "sa", "");
    }
}
