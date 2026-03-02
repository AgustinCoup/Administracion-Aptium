package com.example.common.util;

import com.example.common.constants.Constantes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class DateTimeDisplayUtils {

    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern(Constantes.Formatos.FORMATO_FECHA_HORA);

    private DateTimeDisplayUtils() {
        throw new UnsupportedOperationException("Clase utilitaria no instanciable");
    }

    public static String formatForUi(LocalDateTime dateTime) {
        if (dateTime == null) {
            return Constantes.Textos.SIN_MOVIMIENTO;
        }
        return dateTime.format(FORMATTER);
    }

    public static String formatForFilter(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(FORMATTER);
    }
}
