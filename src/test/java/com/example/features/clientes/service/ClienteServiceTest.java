package com.example.features.clientes.service;

import com.example.features.clientes.dao.ClienteDAO;
import com.example.features.clientes.model.Cliente;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    @Mock
    private ClienteDAO clienteDAO;

    private ClienteService service;

    @BeforeEach
    void setUp() {
        service = new ClienteService(clienteDAO);
    }

    // ── Constructor ──────────────────────────────────────────────────────────

    @Test
    void constructor_daoNull_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new ClienteService(null));
    }

    // ── buscarClientes ───────────────────────────────────────────────────────

    @Test
    void buscarClientes_null_retornaVacia() {
        assertTrue(service.buscarClientes(null).isEmpty());
        verifyNoInteractions(clienteDAO);
    }

    @Test
    void buscarClientes_cadenaVacia_retornaVacia() {
        assertTrue(service.buscarClientes("").isEmpty());
        verifyNoInteractions(clienteDAO);
    }

    @Test
    void buscarClientes_menosDe3Chars_retornaVacia() {
        assertTrue(service.buscarClientes("ab").isEmpty());
        verifyNoInteractions(clienteDAO);
    }

    @Test
    void buscarClientes_soloEspacios_retornaVacia() {
        // "  " trimmed → "" → length < 3
        assertTrue(service.buscarClientes("  ").isEmpty());
        verifyNoInteractions(clienteDAO);
    }

    @Test
    void buscarClientes_espaciosRelleno_cuentaCaracteresReales() {
        // "  a  " trimmed → "a" → length 1 < 3
        assertTrue(service.buscarClientes("  a  ").isEmpty());
        verifyNoInteractions(clienteDAO);
    }

    @Test
    void buscarClientes_exactamente3Chars_delegaADAO() {
        List<Cliente> esperado = Arrays.asList(new Cliente(1, "Abel"));
        when(clienteDAO.buscarPorNombre("abc")).thenReturn(esperado);

        List<Cliente> resultado = service.buscarClientes("abc");
        assertEquals(esperado, resultado);
        verify(clienteDAO).buscarPorNombre("abc");
    }

    @Test
    void buscarClientes_trim_antes_delegarADAO() {
        List<Cliente> esperado = Arrays.asList(new Cliente(1, "Abel"));
        when(clienteDAO.buscarPorNombre("abc")).thenReturn(esperado);

        List<Cliente> resultado = service.buscarClientes("  abc  ");
        assertEquals(esperado, resultado);
        verify(clienteDAO).buscarPorNombre("abc");
    }

    // ── obtenerClientePorId ───────────────────────────────────────────────────

    @Test
    void obtenerClientePorId_delegaADAO() {
        Cliente c = new Cliente(5, "Carlos");
        when(clienteDAO.obtenerPorId(5)).thenReturn(c);

        assertSame(c, service.obtenerClientePorId(5));
    }

    // ── obtenerTodosLosClientes ───────────────────────────────────────────────

    @Test
    void obtenerTodosLosClientes_delegaADAO() {
        List<Cliente> lista = Arrays.asList(new Cliente(1, "A"), new Cliente(2, "B"));
        when(clienteDAO.obtenerTodos()).thenReturn(lista);

        assertEquals(lista, service.obtenerTodosLosClientes());
    }

    // ── guardarCliente ────────────────────────────────────────────────────────

    @Test
    void guardarCliente_null_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> service.guardarCliente(null));
        verifyNoInteractions(clienteDAO);
    }

    @Test
    void guardarCliente_nombreNull_lanzaIllegalArgument() {
        Cliente c = new Cliente(0, null);
        assertThrows(IllegalArgumentException.class, () -> service.guardarCliente(c));
        verifyNoInteractions(clienteDAO);
    }

    @Test
    void guardarCliente_nombreVacio_lanzaIllegalArgument() {
        Cliente c = new Cliente(0, "   ");
        assertThrows(IllegalArgumentException.class, () -> service.guardarCliente(c));
        verifyNoInteractions(clienteDAO);
    }

    @Test
    void guardarCliente_valido_delegaADAO() {
        Cliente c = new Cliente(0, "Fernández");
        when(clienteDAO.guardar(c)).thenReturn(true);

        assertTrue(service.guardarCliente(c));
        verify(clienteDAO).guardar(c);
    }
}
