package com.example.features.actualizaciones.service;

import com.example.features.actualizaciones.exception.ActualizacionException;
import com.example.features.actualizaciones.model.ReleaseInfo;

/**
 * Puerto de acceso al último release publicado (permite fakear la llamada de red en tests).
 */
public interface IReleaseRepository {

    /**
     * @throws ActualizacionException si falla la consulta o el parseo de la respuesta
     */
    ReleaseInfo obtenerUltimoRelease();
}
