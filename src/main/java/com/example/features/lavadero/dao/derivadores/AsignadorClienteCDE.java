package com.example.features.lavadero.dao.derivadores;

import com.example.features.lavadero.model.SalidaLista;

/**
 * Bajo qué cliente entra al CDE una salida de lavadero.
 *
 * <p>Es lo único que varía entre las dos acciones de CDE: conservar el cliente que trajo la
 * ropa o ingresarla a nombre de APTIUM. Por eso es un parámetro del armado y no una rama
 * dentro de {@link ConstructorIngresoCDE}.</p>
 */
@FunctionalInterface
public interface AsignadorClienteCDE {

    int clientePara(SalidaLista salida);

    /** Conserva el cliente que trajo la ropa. */
    AsignadorClienteCDE CLIENTE_ORIGINAL = SalidaLista::clienteId;
}
