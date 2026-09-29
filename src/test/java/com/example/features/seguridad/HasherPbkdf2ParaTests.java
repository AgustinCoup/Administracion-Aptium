package com.example.features.seguridad;

/**
 * Da acceso al constructor package-private de {@link HasherPbkdf2} a los tests de otros paquetes.
 * Vive en {@code src/test}: el código de producción no puede bajar las iteraciones.
 */
public final class HasherPbkdf2ParaTests {

    private HasherPbkdf2ParaTests() {}

    public static HasherPbkdf2 conIteraciones(int iteraciones) {
        return new HasherPbkdf2(iteraciones);
    }
}
