package com.example.features.lavadero.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Configuración de una card de lavarropas capturada para copiar y pegar en otra: tipo de lavado,
 * jabón, mililitros de jabón e insumos extra. No incluye los elementos cargados — eso es staging,
 * no configuración.
 *
 * <p>Cualquier campo puede venir {@code null} o vacío: se copia <b>lo que hay</b>, no lo que
 * debería haber. Copiar una card a medio configurar es un caso normal.</p>
 *
 * @param tipo         tipo de lavado elegido, o {@code null} si la card no lo tenía
 * @param jabon        jabón elegido, o {@code null}
 * @param litrosJabon  mililitros de jabón, o {@code null}
 * @param insumos      insumos extra elegidos, en el orden en que se agregaron
 */
public record ConfiguracionCopiada(TipoLavado tipo, JabonCatalogo jabon, BigDecimal litrosJabon,
                                    List<InsumoCatalogo> insumos) {

    public ConfiguracionCopiada {
        insumos = insumos == null ? List.of() : List.copyOf(insumos);
    }
}
