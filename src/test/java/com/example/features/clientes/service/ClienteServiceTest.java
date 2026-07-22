package com.example.features.clientes.service;

import com.example.common.exception.ApplicationException;
import com.example.common.exception.DatabaseException;
import com.example.common.exception.ReferentialIntegrityException;
import com.example.common.exception.ResourceNotFoundException;
import com.example.common.exception.ValidationException;
import com.example.features.clientes.dao.ClienteDAO;
import com.example.features.clientes.dao.FusionClientesDAO;
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

    @Mock
    private FusionClientesDAO fusionClientesDAO;

    private ClienteService service;

    @BeforeEach
    void setUp() {
        service = new ClienteService(clienteDAO, fusionClientesDAO);
    }

    // ── Constructor ──────────────────────────────────────────────────────────

    @Test
    void constructor_clienteDaoNull_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new ClienteService(null, fusionClientesDAO));
    }

    @Test
    void constructor_fusionDaoNull_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new ClienteService(clienteDAO, null));
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

    // ── eliminarCliente ───────────────────────────────────────────────────────

    @Test
    void eliminarCliente_existente_delegaADAO() {
        when(clienteDAO.existe(7)).thenReturn(true);
        when(clienteDAO.eliminar(7)).thenReturn(true);

        service.eliminarCliente(7);

        verify(clienteDAO).eliminar(7);
    }

    @Test
    void eliminarCliente_noExiste_lanzaResourceNotFoundException() {
        when(clienteDAO.existe(99)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.eliminarCliente(99));
        verify(clienteDAO, never()).eliminar(anyInt());
    }

    @Test
    void eliminarCliente_conReferencias_mensajeEspecificoDeNegocio() {
        when(clienteDAO.existe(3)).thenReturn(true);
        when(clienteDAO.eliminar(3)).thenThrow(
            new ReferentialIntegrityException("Cliente con ID 3 está referenciado", null));

        ApplicationException e = assertThrows(ApplicationException.class,
            () -> service.eliminarCliente(3));

        assertTrue(e.getMessage().contains("no puede eliminarse"));
    }

    @Test
    void eliminarCliente_errorDeBDGenerico_mensajeGenerico() {
        when(clienteDAO.existe(3)).thenReturn(true);
        when(clienteDAO.eliminar(3)).thenThrow(new DatabaseException("conexión perdida"));

        ApplicationException e = assertThrows(ApplicationException.class,
            () -> service.eliminarCliente(3));

        assertTrue(e.getMessage().contains("Error de base de datos"));
    }

    // ── fusionarClientes ──────────────────────────────────────────────────────

    @Test
    void fusionarClientes_casoFeliz_delegaAFusionDAO() {
        when(clienteDAO.existe(1)).thenReturn(true);
        when(clienteDAO.existe(2)).thenReturn(true);

        service.fusionarClientes(1, 2);

        verify(fusionClientesDAO).fusionar(1, 2);
    }

    @Test
    void fusionarClientes_mismoId_lanzaValidationException() {
        assertThrows(ValidationException.class, () -> service.fusionarClientes(5, 5));
        verifyNoInteractions(fusionClientesDAO);
    }

    @Test
    void fusionarClientes_origenNoExiste_lanzaResourceNotFoundException() {
        when(clienteDAO.existe(99)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.fusionarClientes(99, 2));
        verifyNoInteractions(fusionClientesDAO);
    }

    @Test
    void fusionarClientes_destinoNoExiste_lanzaResourceNotFoundException() {
        when(clienteDAO.existe(1)).thenReturn(true);
        when(clienteDAO.existe(99)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.fusionarClientes(1, 99));
        verifyNoInteractions(fusionClientesDAO);
    }
}
