package com.example.features.clientes.service;

import com.example.common.exception.ApplicationException;
import com.example.common.exception.DatabaseException;
import com.example.common.exception.ReferentialIntegrityException;
import com.example.common.exception.ResourceNotFoundException;
import com.example.common.exception.ValidationException;
import com.example.features.clientes.dao.ClienteDAO;
import com.example.features.clientes.dao.FusionClientesDAO;
import com.example.features.clientes.model.Cliente;
import java.util.Collections;
import java.util.List;

/**
 * Servicio de negocio para gestionar clientes.
 * Valida las reglas de negocio antes de delegar al DAO.
 * 
 * DEPENDENCY INJECTION:
 * - Recibe DAO por constructor para permitir testing
 */
public class ClienteService {

    private final ClienteDAO clienteDAO;
    private final FusionClientesDAO fusionClientesDAO;

    public ClienteService(ClienteDAO clienteDAO, FusionClientesDAO fusionClientesDAO) {
        if (clienteDAO == null) {
            throw new IllegalArgumentException("ClienteDAO no puede ser nulo");
        }
        if (fusionClientesDAO == null) {
            throw new IllegalArgumentException("FusionClientesDAO no puede ser nulo");
        }
        this.clienteDAO = clienteDAO;
        this.fusionClientesDAO = fusionClientesDAO;
    }

    /**
     * Busca clientes cuyo nombre contenga el substring proporcionado.
     * 
     * Aplica regla de negocio: requiere mínimo 3 caracteres.
     * Retorna lista vacía si el substring es menor a 3 caracteres.
     * 
     * @param substring Texto a buscar en los nombres de clientes
     * @return Lista de clientes que coinciden, o lista vacía si substring es insuficiente
     */
    public List<Cliente> buscarClientes(String substring) {
        if (substring == null || substring.trim().length() < 3) {
            return Collections.emptyList();
        }
        
        String textoLimpio = substring.trim();
        return clienteDAO.buscarPorNombre(textoLimpio);
    }

    /**
     * Obtiene un cliente específico por su identificador.
     * 
     * @param id Identificador único del cliente
     * @return Cliente encontrado, o null si no existe
     */
    public Cliente obtenerClientePorId(int id) {
        return clienteDAO.obtenerPorId(id);
    }

    /**
     * Obtiene todos los clientes de la base de datos.
     * 
     * @return Lista completa de clientes
     */
    public List<Cliente> obtenerTodosLosClientes() {
        return clienteDAO.obtenerTodos();
    }

    /**
     * Guarda un nuevo cliente en la base de datos.
     * 
     * Valida que el nombre no sea vacío.
     * 
     * @param cliente Cliente a guardar
     * @return true si se guardó exitosamente, false en caso contrario
     * @throws IllegalArgumentException si el cliente es nulo o el nombre está vacío
     */
    public boolean guardarCliente(Cliente cliente) {
        if (cliente == null) {
            throw new IllegalArgumentException("Cliente no puede ser nulo");
        }

        if (cliente.getNombre() == null || cliente.getNombre().trim().isEmpty()) {
            throw new IllegalArgumentException("Nombre del cliente no puede estar vacío");
        }

        return clienteDAO.guardar(cliente);
    }

    public void eliminarCliente(int id) {
        if (!clienteDAO.existe(id)) {
            throw new ResourceNotFoundException("Cliente", id);
        }
        try {
            clienteDAO.eliminar(id);
        } catch (ReferentialIntegrityException e) {
            throw new ApplicationException(
                "El cliente tiene equipos o ingresos registrados y no puede eliminarse.", e);
        } catch (DatabaseException e) {
            throw new ApplicationException("Error de base de datos al eliminar el cliente.", e);
        }
    }

    public void fusionarClientes(int idOrigen, int idDestino) {
        if (idOrigen == idDestino) {
            throw new ValidationException("No se puede fusionar un cliente consigo mismo");
        }
        if (!clienteDAO.existe(idOrigen)) {
            throw new ResourceNotFoundException("Cliente origen", idOrigen);
        }
        if (!clienteDAO.existe(idDestino)) {
            throw new ResourceNotFoundException("Cliente destino", idDestino);
        }
        fusionClientesDAO.fusionar(idOrigen, idDestino);
    }
}


