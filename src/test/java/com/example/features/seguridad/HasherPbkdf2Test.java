package com.example.features.seguridad;

import com.example.features.seguridad.model.HashPassword;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Con pocas iteraciones: 600 000 en cada test harían lenta la suite. El único test con el hasher
 * real es {@code PasswordDAOTest.semillaDeLaMigracion_verificaConLaPasswordInicialDocumentada}.
 */
class HasherPbkdf2Test {

    private static final int POCAS = 1_000;

    private final HasherPbkdf2 hasher = new HasherPbkdf2(POCAS);

    @Test
    void verificar_laMismaPassword_true() {
        HashPassword guardado = hasher.hashear("secreta1".toCharArray());

        assertTrue(hasher.verificar("secreta1".toCharArray(), guardado));
    }

    @Test
    void verificar_otra_false() {
        HashPassword guardado = hasher.hashear("secreta1".toCharArray());

        assertFalse(hasher.verificar("secreta2".toCharArray(), guardado));
        assertFalse(hasher.verificar("Secreta1".toCharArray(), guardado));
    }

    @Test
    void verificar_vacia_false() {
        HashPassword guardado = hasher.hashear("secreta1".toCharArray());

        assertFalse(hasher.verificar(new char[0], guardado));
        assertFalse(hasher.verificar(null, guardado));
    }

    @Test
    void hashear_vacia_lanza() {
        assertThrows(IllegalArgumentException.class, () -> hasher.hashear(new char[0]));
    }

    @Test
    void hashear_dosVeces_saltsDistintos() {
        HashPassword a = hasher.hashear("secreta1".toCharArray());
        HashPassword b = hasher.hashear("secreta1".toCharArray());

        assertFalse(Arrays.equals(a.salt(), b.salt()));
        assertFalse(Arrays.equals(a.hash(), b.hash()), "mismo texto, salts distintos: hashes distintos");
    }

    @Test
    void hashear_parametrosDelAlgoritmo() {
        HashPassword h = hasher.hashear("secreta1".toCharArray());

        assertEquals(HasherPbkdf2.ALGORITMO, h.algoritmo());
        assertEquals("PBKDF2WithHmacSHA256", h.algoritmo());
        assertEquals(POCAS, h.iteraciones());
        assertEquals(16, h.salt().length);
        assertEquals(32, h.hash().length, "clave de 256 bits");
    }

    @Test
    void hasherPorDefecto_usa600000Iteraciones() {
        assertEquals(600_000, HasherPbkdf2.ITERACIONES);
    }

    /**
     * Las iteraciones viajan en la fila para poder subirlas sin invalidar el hash vigente: quien
     * verifica usa las del guardado, no las suyas.
     */
    @Test
    void verificar_usaLasIteracionesDelGuardado() {
        HashPassword conN = new HasherPbkdf2(POCAS).hashear("secreta1".toCharArray());

        HasherPbkdf2 configuradoEnM = new HasherPbkdf2(POCAS * 2);

        assertTrue(configuradoEnM.verificar("secreta1".toCharArray(), conN));
    }

    /** El hasher no limpia el {@code char[]} que recibe: es de quien lo creó. */
    @Test
    void hashearYVerificar_noLimpianElArrayDelLlamador() {
        char[] password = "secreta1".toCharArray();

        HashPassword h = hasher.hashear(password);
        hasher.verificar(password, h);

        assertArrayEquals("secreta1".toCharArray(), password);
    }

    @Test
    void hashPassword_toStringNoExponeSaltNiHash() {
        HashPassword h = hasher.hashear("secreta1".toCharArray());
        String texto = h.toString();

        assertFalse(texto.contains(Base64.getEncoder().encodeToString(h.salt())));
        assertFalse(texto.contains(Base64.getEncoder().encodeToString(h.hash())));
        assertFalse(texto.contains(Arrays.toString(h.salt())));
        assertFalse(texto.contains(Arrays.toString(h.hash())));
        assertTrue(texto.contains(HasherPbkdf2.ALGORITMO));
    }

    @Test
    void hashPassword_copiasDefensivas() {
        HashPassword h = hasher.hashear("secreta1".toCharArray());

        h.salt()[0] ^= 1;
        h.hash()[0] ^= 1;

        assertTrue(hasher.verificar("secreta1".toCharArray(), h), "modificar lo devuelto no altera el hash");
    }
}
