package com.example.features.seguridad.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * Una password guardada: algoritmo, iteraciones, salt y hash. Nunca la password.
 *
 * <p>Los arrays se copian al entrar y al salir, así que nadie puede alterar un hash ya construido.
 * {@link #toString()} está sobreescrito para <b>no</b> imprimir ni salt ni hash (el del record los
 * mostraría como {@code [B@…}, y alcanza con que alguien lo cambie a {@code Arrays.toString} para
 * que termine en un log). {@link #equals} compara contenido para que el record se comporte como
 * valor, pero <b>no se usa para verificar una password</b>: no es de tiempo constante. Eso lo hace
 * {@code HasherPbkdf2.verificar} con {@code MessageDigest.isEqual}.</p>
 */
public record HashPassword(String algoritmo, int iteraciones, byte[] salt, byte[] hash) {

    public HashPassword {
        if (algoritmo == null || algoritmo.isBlank()) {
            throw new IllegalArgumentException("algoritmo requerido");
        }
        if (iteraciones <= 0) {
            throw new IllegalArgumentException("iteraciones debe ser positivo");
        }
        if (salt == null || salt.length == 0 || hash == null || hash.length == 0) {
            throw new IllegalArgumentException("salt y hash requeridos");
        }
        salt = salt.clone();
        hash = hash.clone();
    }

    @Override
    public byte[] salt() {
        return salt.clone();
    }

    @Override
    public byte[] hash() {
        return hash.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof HashPassword otro
            && iteraciones == otro.iteraciones
            && algoritmo.equals(otro.algoritmo)
            && Arrays.equals(salt, otro.salt)
            && Arrays.equals(hash, otro.hash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(algoritmo, iteraciones, Arrays.hashCode(salt), Arrays.hashCode(hash));
    }

    @Override
    public String toString() {
        return "HashPassword[algoritmo=" + algoritmo + ", iteraciones=" + iteraciones + "]";
    }
}
