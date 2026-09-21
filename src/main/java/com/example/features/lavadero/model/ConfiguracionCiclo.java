package com.example.features.lavadero.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Configuración con la que se lanza un ciclo de lavado.
 *
 * <p>Agrupa los parámetros que antes viajaban sueltos y posicionales por service, DAO y
 * controller. No valida nada: la validación vive en {@code CicloLavaderoService}.</p>
 *
 * @param tipoLavado  tipo de lavado; obligatorio (lo exige el service)
 * @param jabon       jabón del catálogo; obligatorio (lo exige el service)
 * @param litrosJabon mililitros de jabón; obligatorio y mayor a cero (lo exige el service)
 * @param insumos     insumos extra del ciclo; puede estar vacía — un ciclo sin suavizante es un
 *                    ciclo válido. {@code null} se normaliza a lista vacía, para que un
 *                    {@code null} que se cuele desde la card no reviente en el DAO
 */
public record ConfiguracionCiclo(TipoLavado tipoLavado, JabonCatalogo jabon, BigDecimal litrosJabon,
                                 List<InsumoCatalogo> insumos) {

    public ConfiguracionCiclo {
        insumos = insumos == null ? List.of() : List.copyOf(insumos);
    }
}
