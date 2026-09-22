package com.example.common.exception;

/**
 * Un lavarropas de la tanda fue dado de baja entre que la pantalla lo dibujó y el operador apretó
 * Lanzar.
 *
 * <p><b>Por qué es un tipo aparte y no un reuso de {@link LavarropasOcupadoException}.</b> Para la
 * base son dos rechazos del mismo lanzamiento, pero para el operador son dos cosas distintas:
 * "otro usuario lanzó un ciclo ahí" se resuelve esperando a que termine, y "esa máquina se retiró
 * del lavadero" no se resuelve nunca. Un cartel que dice la que no es entrena a apretar "Sí" sin
 * leer, y con eso desactiva también los avisos verdaderos.</p>
 *
 * <p><b>No dispara el descarte del staging.</b> La regla del repo es positiva —el staging de la
 * tanda se descarta <b>sólo</b> ante {@link SaldoConsumidoException}, que es el único choque que
 * invalida el trabajo en curso porque parte de esa ropa ya se la llevó otro y el operador no sabe
 * qué parte—. Acá la ropa sigue entera y disponible: lo único que se perdió es el destino, igual
 * que con el lavarropas ocupado. Escribir la regla al revés ("descartar salvo ante X") haría que
 * este choque heredara por omisión un descarte que no le corresponde.</p>
 *
 * <p>La lanza {@code CicloLavaderoDAO.exigirLavarropasActivos}, <b>dentro</b> de la transacción del
 * lanzamiento. No la lanza la pantalla de Ajustes: el staging vive en la memoria de cada cliente,
 * así que la baja no puede enterarse de lo que otra máquina tiene cargado.</p>
 */
public class LavarropasDeBajaException extends ConflictoConcurrenciaException {

    public LavarropasDeBajaException(String mensaje) {
        super(mensaje);
    }
}
