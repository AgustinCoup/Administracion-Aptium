package com.example.features.seguridad.dao;

import com.example.AbstractDAOTest;
import com.example.common.exception.ConflictoConcurrenciaException;
import com.example.common.exception.ResourceNotFoundException;
import com.example.features.seguridad.HasherPbkdf2;
import com.example.features.seguridad.HasherPbkdf2ParaTests;
import com.example.features.seguridad.model.HashPassword;
import com.example.features.seguridad.service.PasswordEliminacionService;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Los tests que escriben usan una fila propia ({@link #PROPOSITO_TEST}), nunca la sembrada: la
 * base H2 en memoria se comparte entre clases de test, y pisar la semilla haría fallar
 * {@link #semillaDeLaMigracion_verificaConLaPasswordInicialDocumentada} según el orden.
 */
class PasswordDAOTest extends AbstractDAOTest {

    /**
     * La password inicial que documenta {@code CLAUDE.md} ("Eliminar ingresos → password"). Vive
     * en el test, y sólo acá: {@code src/main} no la conoce.
     */
    private static final String PASSWORD_INICIAL_DOCUMENTADA = "aptium";

    private static final String PROPOSITO_TEST = "TEST_PASSWORD_DAO";

    private final PasswordDAO dao = new PasswordDAO();

    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM passwords WHERE proposito = '" + PROPOSITO_TEST + "'");
    }

    /**
     * El único test con el hasher real de 600 000 iteraciones, y el que garantiza que lo que
     * documenta {@code CLAUDE.md} no miente: la semilla de la V29 verifica contra esa password.
     */
    @Test
    void semillaDeLaMigracion_verificaConLaPasswordInicialDocumentada() {
        HashPassword semilla = dao.leer(PasswordEliminacionService.PROPOSITO);

        assertEquals(HasherPbkdf2.ALGORITMO, semilla.algoritmo());
        assertEquals(HasherPbkdf2.ITERACIONES, semilla.iteraciones());
        assertEquals(16, semilla.salt().length);
        assertTrue(new HasherPbkdf2().verificar(PASSWORD_INICIAL_DOCUMENTADA.toCharArray(), semilla));
        assertFalse(new HasherPbkdf2().verificar("otra".toCharArray(), semilla));
    }

    @Test
    void semillaDeLaMigracion_esInicial() {
        assertTrue(dao.esInicial(PasswordEliminacionService.PROPOSITO));
    }

    @Test
    void leer_propositoInexistente_lanzaResourceNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> dao.leer("NO_EXISTE"));
        assertThrows(ResourceNotFoundException.class, () -> dao.esInicial("NO_EXISTE"));
    }

    @Test
    void leer_devuelveLoQueSeGuardo() throws SQLException {
        HashPassword guardado = sembrarFilaDeTest();

        assertEquals(guardado, dao.leer(PROPOSITO_TEST));
    }

    @Test
    void reemplazar_apagaEsInicial() throws SQLException {
        HashPassword anterior = sembrarFilaDeTest();
        HashPassword nuevo = hasher().hashear("nueva123".toCharArray());

        dao.reemplazar(PROPOSITO_TEST, anterior, nuevo);

        assertFalse(dao.esInicial(PROPOSITO_TEST));
        assertEquals(nuevo, dao.leer(PROPOSITO_TEST));
    }

    /**
     * Dos cambios simultáneos: los dos leyeron el mismo hash, gana el primero y el segundo choca
     * en vez de pisarlo en silencio.
     */
    @Test
    void reemplazar_conHashViejo_conflicto() throws SQLException {
        HashPassword leidoPorLosDos = sembrarFilaDeTest();
        HashPassword delPrimero = hasher().hashear("primero1".toCharArray());
        HashPassword delSegundo = hasher().hashear("segundo2".toCharArray());
        dao.reemplazar(PROPOSITO_TEST, leidoPorLosDos, delPrimero);

        assertThrows(ConflictoConcurrenciaException.class,
            () -> dao.reemplazar(PROPOSITO_TEST, leidoPorLosDos, delSegundo));

        assertEquals(delPrimero, dao.leer(PROPOSITO_TEST), "la del primero sigue vigente");
    }

    /** Con pocas iteraciones: estos tests prueban el DAO, no el costo del hash. */
    private static HasherPbkdf2 hasher() {
        return HasherPbkdf2ParaTests.conIteraciones(1_000);
    }

    private HashPassword sembrarFilaDeTest() throws SQLException {
        HashPassword h = hasher().hashear("inicial1".toCharArray());
        java.util.Base64.Encoder b64 = java.util.Base64.getEncoder();
        ejecutarSQL("INSERT INTO passwords (proposito, algoritmo, iteraciones, salt, hash, es_inicial) "
            + "VALUES ('" + PROPOSITO_TEST + "', '" + h.algoritmo() + "', " + h.iteraciones() + ", '"
            + b64.encodeToString(h.salt()) + "', '" + b64.encodeToString(h.hash()) + "', TRUE)");
        return h;
    }
}
