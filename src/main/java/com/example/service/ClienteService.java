package com.example.service;

import com.example.model.Cliente;
import com.example.model.ClienteDAO;
import java.util.Collections;
import java.util.List;

/**
 * Servicio de negocio para gestionar clientes.
 * Valida las reglas de negocio antes de delegar al DAO.
 */
public class ClienteService {
    
    private ClienteDAO clienteDAO;
    
    public ClienteService() {
        this.clienteDAO = new ClienteDAO();
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
}
