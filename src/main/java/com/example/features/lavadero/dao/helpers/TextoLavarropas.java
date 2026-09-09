package com.example.features.lavadero.dao.helpers;

import java.util.List;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * Arma el texto de lavarropas de un {@code Equipo*} repartido a partir de sus fracciones:
 * {@code "3, 5"}. Lo comparten Salidas ({@link AgrupadorInstanciasSalida}) y el Historial
 * ({@link AgrupadorLineasHistorial}), que agrupan con reglas de negocio opuestas pero muestran
 * los lavarropas igual.
 *
 * <p>La agregación se hace acá y no con {@code GROUP_CONCAT}/{@code STRING_AGG} en SQL para no
 * introducir una función de texto que se comporte distinto entre H2 (tests) y MySQL
 * (producción): cada fila cruda se lee tal cual y se arma el grupo en memoria.</p>
 */
public final class TextoLavarropas {

    private TextoLavarropas() { }

    /** Números distintos del grupo, ordenados y separados por coma. Grupo vacío → texto vacío. */
    public static <T> String de(List<T> grupo, ToIntFunction<T> numero) {
        return grupo.stream().mapToInt(numero).distinct().sorted()
            .mapToObj(String::valueOf).collect(Collectors.joining(", "));
    }
}
