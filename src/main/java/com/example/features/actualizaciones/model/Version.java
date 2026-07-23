package com.example.features.actualizaciones.model;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Versión semántica (major.minor.patch), con un 4° segmento opcional de hotfix
 * (major.minor.patch.hotfix, ej. "1.1.4.2"), con o sin prefijo "v".
 * Value object inmutable — comparación numérica, no lexicográfica.
 */
public final class Version implements Comparable<Version> {

    private static final Pattern FORMATO = Pattern.compile("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:\\.(\\d+))?$");

    private final int major;
    private final int minor;
    private final int patch;
    private final int hotfix;

    private Version(int major, int minor, int patch, int hotfix) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.hotfix = hotfix;
    }

    public static Version parse(String texto) {
        if (texto == null) {
            throw new IllegalArgumentException("La versión no puede ser nula");
        }
        Matcher matcher = FORMATO.matcher(texto.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Formato de versión inválido: " + texto);
        }
        String hotfixTexto = matcher.group(4);
        return new Version(
            Integer.parseInt(matcher.group(1)),
            Integer.parseInt(matcher.group(2)),
            Integer.parseInt(matcher.group(3)),
            hotfixTexto == null ? 0 : Integer.parseInt(hotfixTexto)
        );
    }

    public int major() {
        return major;
    }

    public int minor() {
        return minor;
    }

    public int patch() {
        return patch;
    }

    public int hotfix() {
        return hotfix;
    }

    @Override
    public int compareTo(Version otra) {
        int cmp = Integer.compare(this.major, otra.major);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(this.minor, otra.minor);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(this.patch, otra.patch);
        if (cmp != 0) {
            return cmp;
        }
        return Integer.compare(this.hotfix, otra.hotfix);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Version otra)) {
            return false;
        }
        return major == otra.major && minor == otra.minor && patch == otra.patch && hotfix == otra.hotfix;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch, hotfix);
    }

    @Override
    public String toString() {
        String base = major + "." + minor + "." + patch;
        return hotfix == 0 ? base : base + "." + hotfix;
    }
}
