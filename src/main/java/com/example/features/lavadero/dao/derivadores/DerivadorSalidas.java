package com.example.features.lavadero.dao.derivadores;

import com.example.features.lavadero.model.AccionSalida;
import com.example.features.lavadero.model.SalidaLista;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Qué hace la aplicación con un conjunto de salidas de lavadero ya listas.
 * Una instancia por {@link AccionSalida}.
 *
 * <p>Lo único que varía entre acciones es <b>qué se crea en otro lado, si es que se crea algo</b>:
 * estampar el destino y recalcular el estado de los ingresos afectados es idéntico en todos los
 * casos y vive en {@code SalidaLavaderoDAO}. Agregar un destino nuevo es una clase más, una
 * constante más del enum y una entrada más en el cableado, sin tocar nada de lo existente.
 *
 * <p>Se indexa por <b>acción</b> y no por {@link com.example.features.lavadero.model.DestinoSalida}
 * porque las dos variantes de CDE comparten el destino persistido {@code CDE_OTROS} y se
 * diferencian sólo en el cliente que asignan: un mapa por destino no podría tener las dos.
 *
 * <p>Recibe la {@link Connection} de la transacción abierta por {@code SalidaLavaderoDAO}:
 * lo que el derivador escriba y el estampado del destino tienen que ser atómicos.
 */
public interface DerivadorSalidas {

    /** La acción que este derivador atiende. El destino a persistir sale de ella. */
    AccionSalida accion();

    /**
     * @return equipo_otros creado para cada salida (salidaId → equipoOtrosId).
     *         Vacío si esta acción no genera nada en el CDE.
     */
    Map<Integer, Integer> derivar(Connection conn, List<SalidaLista> salidas) throws SQLException;
}
