package com.example.features.actualizaciones.model;

import java.util.Map;

/**
 * Datos de un release de GitHub relevantes para el flujo de actualización.
 *
 * @param tag       tag del release (ej. "v1.2.3")
 * @param assets    nombre de asset → URL de descarga (ej. "aptium.jar" → URL)
 * @param changelog cuerpo/descripción del release
 */
public record ReleaseInfo(String tag, Map<String, String> assets, String changelog) {

    public ReleaseInfo {
        assets = Map.copyOf(assets);
    }
}
