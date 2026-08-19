package com.example.features.lavadero.dao.derivadores;

import com.example.common.constants.Constantes;
import com.example.common.exception.ResourceNotFoundException;
import com.example.features.clientes.dao.ClienteDAO;
import com.example.features.clientes.model.Cliente;
import com.example.features.lavadero.model.SalidaLista;

import java.util.Objects;

/**
 * Ingresa al CDE todas las salidas a nombre de APTIUM, sin importar de qué cliente vinieron.
 *
 * <p>El id se resuelve <b>por nombre</b> y nunca se hardcodea: depende del {@code AUTO_INCREMENT}
 * de {@code clientes} y no es el mismo en producción que en una BD de desarrollo. Si el cliente no
 * está, esto falla con {@link ResourceNotFoundException}: derivar en silencio a un cliente
 * equivocado sería mucho peor que no derivar.
 *
 * <p>A diferencia de su hermano {@link AsignadorClienteCDE#CLIENTE_ORIGINAL}, que es una lambda
 * sobre el propio registro, este necesita un DAO. Es la razón por la que el asignador es una
 * interfaz y no un campo del {@code SalidaLista}.
 */
public final class AsignadorClienteAptium implements AsignadorClienteCDE {

    private final ClienteDAO clienteDAO;

    /** Resuelto una sola vez: el cliente APTIUM no cambia de id mientras la app corre. */
    private Integer idAptium;

    public AsignadorClienteAptium(ClienteDAO clienteDAO) {
        this.clienteDAO = Objects.requireNonNull(clienteDAO, "clienteDAO no puede ser null");
    }

    @Override
    public int clientePara(SalidaLista salida) {
        if (idAptium == null) {
            idAptium = resolverPorNombre();
        }
        return idAptium;
    }

    /**
     * {@code buscarPorNombre} busca por coincidencia parcial, así que se filtra por igualdad
     * exacta: un cliente llamado "APTIUM SRL" no es el cliente APTIUM.
     */
    private int resolverPorNombre() {
        String nombre = Constantes.Lavadero.CLIENTE_APTIUM;
        return clienteDAO.buscarPorNombre(nombre).stream()
                .filter(c -> nombre.equalsIgnoreCase(c.getNombre()))
                .findFirst()
                .map(Cliente::getId)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "No existe el cliente '" + nombre + "' en la lista de clientes, así que no se "
                    + "puede ingresar nada al CDE a su nombre. Creá un cliente llamado exactamente '"
                    + nombre + "' y volvé a intentarlo."));
    }
}
