package com.example.features.seguridad.service;

import com.example.common.constants.Constantes;
import com.example.common.eliminacion.PuestoDeTrabajo;
import com.example.common.exception.PasswordIncorrectaException;
import com.example.common.exception.ValidationException;
import com.example.features.seguridad.HasherPbkdf2;
import com.example.features.seguridad.dao.PasswordDAO;
import com.example.features.seguridad.model.HashPassword;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * La password que pide eliminar un ingreso completo.
 *
 * <p><b>Qué protege y qué no.</b> Es una barrera contra borrados accidentales o no autorizados
 * de operadores, <b>no</b> un límite de seguridad contra quien tiene las credenciales de la base:
 * ésas están en {@code config.properties} y permiten todo, incluido el {@code UPDATE} que
 * restablece la password inicial. Por eso no hay rate limiting más allá del costo de PBKDF2
 * ({@value HasherPbkdf2#ITERACIONES} iteraciones, cientos de ms por intento).</p>
 *
 * <p><b>Los {@code char[]} son del llamador.</b> El service no los limpia ni los copia a un
 * {@code String}: el diálogo que los creó los limpia con {@code Arrays.fill} cuando ya no los
 * necesita.</p>
 *
 * <p><b>Ningún log recibe nada derivado de la password</b>: ni la candidata, ni su largo, ni el
 * hash. Se loguea que una verificación falló o que se cambió, y desde qué puesto.</p>
 *
 * <p>Sin JDBC: valida y delega. Todo corre fuera del EDT.</p>
 */
public class PasswordEliminacionService {

    private static final Logger log = LoggerFactory.getLogger(PasswordEliminacionService.class);

    /** La fila de {@code passwords} que sembró la V29. */
    public static final String PROPOSITO = "ELIMINAR_INGRESO";

    private final PasswordDAO dao;
    private final HasherPbkdf2 hasher;

    public PasswordEliminacionService(PasswordDAO dao, HasherPbkdf2 hasher) {
        if (dao == null || hasher == null) {
            throw new IllegalArgumentException("PasswordEliminacionService requiere dependencias no nulas");
        }
        this.dao = dao;
        this.hasher = hasher;
    }

    /**
     * @throws PasswordIncorrectaException si no coincide o si viene vacía, con el mismo mensaje en
     *         los dos casos
     */
    public void verificar(char[] password) {
        if (esVacia(password) || !hasher.verificar(password, dao.leer(PROPOSITO))) {
            log.warn("Verificación fallida de la contraseña de eliminación desde {}", PuestoDeTrabajo.actual());
            throw new PasswordIncorrectaException(Constantes.Mensajes.PASSWORD_INCORRECTA);
        }
    }

    /** ¿Sigue vigente la password que sembró la migración? El diálogo avisa mientras lo esté. */
    public boolean esInicial() {
        return dao.esInicial(PROPOSITO);
    }

    /**
     * Verifica la actual, valida la nueva y la reemplaza.
     *
     * <p>El hash contra el que se verifica la actual es el mismo que viaja como guarda al DAO: si
     * otro puesto la cambió en el medio, el reemplazo choca en vez de pisarla.</p>
     *
     * @throws PasswordIncorrectaException si la actual no coincide
     * @throws ValidationException         si la nueva no coincide con su repetición, es corta o es
     *                                     igual a la actual
     */
    public void cambiar(char[] actual, char[] nueva, char[] repetida) {
        HashPassword anterior = dao.leer(PROPOSITO);
        if (esVacia(actual) || !hasher.verificar(actual, anterior)) {
            log.warn("Cambio de contraseña de eliminación rechazado desde {}: la actual no coincide",
                PuestoDeTrabajo.actual());
            throw new PasswordIncorrectaException(Constantes.Mensajes.PASSWORD_ACTUAL_INCORRECTA);
        }

        char[] laNueva = nueva == null ? new char[0] : nueva;
        ValidationException.builder()
            .addErrorIf(!Arrays.equals(laNueva, repetida), Constantes.Mensajes.PASSWORD_NUEVA_NO_COINCIDE)
            .addErrorIf(laNueva.length < Constantes.Eliminacion.PASSWORD_MIN_LARGO,
                String.format(Constantes.Mensajes.PASSWORD_NUEVA_CORTA, Constantes.Eliminacion.PASSWORD_MIN_LARGO))
            .addErrorIf(Arrays.equals(laNueva, actual), Constantes.Mensajes.PASSWORD_NUEVA_IGUAL_A_LA_ACTUAL)
            .throwIfHasErrors();

        dao.reemplazar(PROPOSITO, anterior, hasher.hashear(laNueva));
        log.info("Contraseña de eliminación cambiada desde {}", PuestoDeTrabajo.actual());
    }

    private static boolean esVacia(char[] password) {
        return password == null || password.length == 0;
    }
}
