package com.example.features.seguridad;

import com.example.features.seguridad.model.HashPassword;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Hash de passwords con PBKDF2-HMAC-SHA256, del JDK: sin dependencias.
 *
 * <ul>
 *   <li><b>Salt aleatorio de 16 bytes por hash</b>, de {@link SecureRandom}.</li>
 *   <li><b>{@value #ITERACIONES} iteraciones</b>, la recomendación de OWASP para
 *       PBKDF2-HMAC-SHA256. Viajan en el {@link HashPassword} (y en la fila): {@link #verificar}
 *       usa las del guardado, así que se pueden subir más adelante sin invalidar el hash
 *       vigente.</li>
 *   <li><b>Comparación en tiempo constante</b> con {@link MessageDigest#isEqual}, nunca
 *       {@code Arrays.equals}.</li>
 *   <li><b>{@link PBEKeySpec#clearPassword()} en un {@code finally}</b>: el spec guarda su propia
 *       copia de la password.</li>
 * </ul>
 *
 * <p><b>No limpia el {@code char[]} que recibe:</b> es de quien lo creó, que es quien sabe cuándo
 * dejó de necesitarlo (el diálogo, después de verificar y borrar).</p>
 *
 * <p>Tarda cientos de milisegundos a propósito: nunca se llama desde el EDT. {@code EdtGuard} no
 * lo detectaría, porque no es I/O.</p>
 */
public class HasherPbkdf2 {

    public static final String ALGORITMO = "PBKDF2WithHmacSHA256";
    public static final int ITERACIONES = 600_000;
    private static final int SALT_BYTES = 16;
    private static final int CLAVE_BITS = 256;

    private final SecureRandom random = new SecureRandom();
    private final int iteraciones;

    public HasherPbkdf2() {
        this(ITERACIONES);
    }

    /** Sólo para tests: 600 000 iteraciones en cada test harían lenta la suite. */
    HasherPbkdf2(int iteraciones) {
        if (iteraciones <= 0) {
            throw new IllegalArgumentException("iteraciones debe ser positivo");
        }
        this.iteraciones = iteraciones;
    }

    /** Hash nuevo, con salt nuevo. */
    public HashPassword hashear(char[] password) {
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("No se hashea una password vacía");
        }
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = derivar(ALGORITMO, password, salt, iteraciones, CLAVE_BITS);
        try {
            return new HashPassword(ALGORITMO, iteraciones, salt, hash);
        } finally {
            Arrays.fill(hash, (byte) 0);
        }
    }

    /**
     * Recalcula con el algoritmo, el salt, las iteraciones y el largo <b>del guardado</b>, y
     * compara en tiempo constante. Una candidata vacía o nula no coincide nunca.
     */
    public boolean verificar(char[] candidata, HashPassword guardado) {
        if (candidata == null || candidata.length == 0) {
            return false;
        }
        byte[] esperado = guardado.hash();
        byte[] calculado = derivar(guardado.algoritmo(), candidata, guardado.salt(),
            guardado.iteraciones(), esperado.length * Byte.SIZE);
        try {
            return MessageDigest.isEqual(calculado, esperado);
        } finally {
            Arrays.fill(calculado, (byte) 0);
        }
    }

    private static byte[] derivar(String algoritmo, char[] password, byte[] salt, int iteraciones, int bits) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iteraciones, bits);
        try {
            return SecretKeyFactory.getInstance(algoritmo).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            // Sin la password ni el spec en el mensaje: sólo el algoritmo, que no es secreto.
            throw new IllegalStateException("No se pudo derivar la clave con " + algoritmo, e);
        } finally {
            spec.clearPassword();
        }
    }
}
