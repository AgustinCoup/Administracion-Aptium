package com.example.common.util;

import java.util.Locale;

public final class TextFilterUtils {

    private TextFilterUtils() {
        throw new UnsupportedOperationException("Clase utilitaria no instanciable");
    }

    public static boolean containsIgnoreCase(String value, String filter) {
        if (filter == null || filter.isEmpty()) {
            return true;
        }
        if (value == null) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT));
    }
}
