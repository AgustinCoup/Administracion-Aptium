package com.example.common.model;

import java.time.LocalDateTime;

import com.example.features.equipos.ortopedias.model.EstadoEquipo;

/**
 * Interfaz común para todos los materiales que participan en el flujo
 * de estados (ortopedia y otros).
 *
 * Permite que {@link com.example.features.equipos.ortopedias.controller.RegistrarEstadoController}
 * y los componentes de vista operen de forma uniforme sobre ambos tipos
 * sin conocer la implementación concreta.
 *
 * Implementaciones:
 * - {@link com.example.features.equipos.ortopedias.model.Material} (ortopedia)
 * - {@link com.example.features.equipos.otros.model.model.otros.otros.model.MaterialOtros}
 */
public interface MaterialRegistrableInterface {

    /** ID persistido en BD. null para materiales no guardados aún. */
    Integer getId();

    /** Descripción legible del material. */
    String getDescripcion();

    int getCantidad();
    void setCantidad(int cantidad);

    EstadoEquipo getEstado();
    void setEstado(EstadoEquipo estado);

    /** true si el material ya tiene un ID asignado por la BD. */
    boolean esPersistido();

    LocalDateTime getUltimoMovimiento();
    void setUltimoMovimiento(LocalDateTime ultimoMovimiento);
}