package com.example.infrastructure.db;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Wrapper {@link AutoCloseable} para transacciones JDBC.
 *
 * <p>Permite usar {@code try-with-resources} en operaciones transaccionales
 * sin repetir el boilerplate de rollback/close en cada DAO:
 *
 * <pre>{@code
 * try (TransactionalConnection tx = TransactionalConnection.begin()) {
 *     Connection conn = tx.get();
 *     // ... operaciones SQL ...
 *     tx.commit();           // marcar como exitosa
 * }                          // close() hace rollback si commit() no fue llamado
 * }</pre>
 *
 * <p>Garantías:
 * <ul>
 *   <li>Si {@link #commit()} fue llamado: {@link #close()} solo cierra la conexión.</li>
 *   <li>Si {@link #commit()} NO fue llamado (excepción o return temprano):
 *       {@link #close()} hace rollback antes de cerrar, sin importar el tipo
 *       de excepción (SQL o RuntimeException).</li>
 *   <li>La conexión siempre se devuelve al pool, incluso si el rollback falla.</li>
 * </ul>
 */
public final class TransactionalConnection implements AutoCloseable {

    private final Connection conn;
    private boolean committed = false;

    private TransactionalConnection(Connection conn) throws SQLException {
        this.conn = conn;
        this.conn.setAutoCommit(false);
    }

    /**
     * Obtiene una conexión del pool e inicia una transacción.
     *
     * @return nuevo {@code TransactionalConnection} con autoCommit=false
     * @throws SQLException si no hay conexiones disponibles o falla setAutoCommit
     */
    public static TransactionalConnection begin() throws SQLException {
        return new TransactionalConnection(ConnectionPool.getConnection());
    }

    /**
     * Devuelve la conexión subyacente para ejecutar statements.
     *
     * <p>No llamar a {@code conn.commit()}, {@code conn.rollback()} ni
     * {@code conn.close()} directamente — usar {@link #commit()} y
     * dejar que {@link #close()} maneje el ciclo de vida.
     */
    public Connection get() {
        return conn;
    }

    /**
     * Confirma la transacción.
     *
     * <p>Debe llamarse antes de que el bloque {@code try-with-resources} termine.
     * Si no se llama (por excepción o retorno anticipado), {@link #close()}
     * hace rollback automáticamente.
     *
     * @throws SQLException si el commit falla
     */
    public void commit() throws SQLException {
        conn.commit();
        committed = true;
    }

    /**
     * Cierra la conexión, haciendo rollback si {@link #commit()} no fue llamado.
     *
     * <p>Llamado automáticamente por {@code try-with-resources}. El rollback
     * y el close ocurren en bloques separados para garantizar que la conexión
     * siempre se devuelva al pool aunque el rollback falle.
     */
    @Override
    public void close() throws SQLException {
        try {
            if (!committed) {
                conn.rollback();
            }
        } finally {
            conn.close();
        }
    }
}