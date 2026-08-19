package com.example.features.lavadero.dao.derivadores;

import com.example.features.lavadero.model.AccionSalida;
import com.example.features.lavadero.model.SalidaLista;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

/**
 * La ropa se devuelve al cliente y no entra al CDE: no hay nada que crear en ningún lado.
 *
 * <p>Existe para que el orquestador no tenga un {@code if} sobre el enum. El estampado del
 * destino en {@code salidas_lavadero} lo hace igual {@code SalidaLavaderoDAO}, que es lo común
 * a todas las acciones.
 */
public final class DerivadorFueraDeFlujo implements DerivadorSalidas {

    @Override
    public AccionSalida accion() {
        return AccionSalida.FUERA_DE_FLUJO;
    }

    @Override
    public Map<Integer, Integer> derivar(Connection conn, List<SalidaLista> salidas) {
        return Map.of();
    }
}
