package com.example.features.clientes.service;

import com.example.features.clientes.model.Cliente;
import com.example.features.clientes.dao.ClienteDAO;
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
    
    /**
     * Constructor con inyección de dependencias.
     * 
     * @param clienteDAO DAO para acceso a datos
     */
    public ClienteService(ClienteDAO clienteDAO) {
        if (clienteDAO == null) {
            throw new IllegalArgumentException("ClienteDAO no puede ser nulo");
        }
        this.clienteDAO = clienteDAO;
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
}


