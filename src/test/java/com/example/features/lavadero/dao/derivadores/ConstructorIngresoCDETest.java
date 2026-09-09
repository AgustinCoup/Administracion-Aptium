package com.example.features.lavadero.dao.derivadores;

import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.MaterialOtros;
import com.example.features.equipos.otros.model.TipoIngresoOtros;
import com.example.features.lavadero.model.SalidaLista;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Armado de ingresos de CDE a partir de salidas de lavadero: agrupación, unificación y banderas. */
class ConstructorIngresoCDETest {

    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 8, 18, 10, 0);

    /** Todas las salidas caen en el mismo cliente 99: es la variante APTIUM sin necesitar la BD. */
    private static final AsignadorClienteCDE SIEMPRE_99 = salida -> 99;

    private final ConstructorIngresoCDE constructor = new ConstructorIngresoCDE();

    private static SalidaLista salida(int salidaId, int clienteId, String elemento, int cantidad) {
        return new SalidaLista(salidaId, 1, null, "3", 7, clienteId, "Cliente " + clienteId,
                               elemento, cantidad, FECHA, FECHA);
    }

    private static MaterialOtros materialLlamado(EquipoOtros ingreso, String descripcion) {
        return ingreso.getMateriales().stream()
                .filter(m -> m.getDescripcion().equals(descripcion))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no hay material " + descripcion));
    }

    @Test
    @DisplayName("una salida de un cliente produce un ingreso con un material")
    void unaSalidaUnIngreso() {
        IngresosCDE resultado = constructor.construir(
            List.of(salida(1, 10, "Batas", 5)), AsignadorClienteCDE.CLIENTE_ORIGINAL);

        assertEquals(1, resultado.ingresos().size());
        EquipoOtros ingreso = resultado.ingresos().get(0);
        assertEquals(10, ingreso.getNroCliente());
        assertEquals(1, ingreso.getMateriales().size());
        assertEquals("Batas", ingreso.getMateriales().get(0).getDescripcion());
        assertEquals(5, ingreso.getMateriales().get(0).getCantidad());
        assertEquals(List.of(1), resultado.salidaIds().get(ingreso));
    }

    @Test
    @DisplayName("tres salidas de dos clientes producen dos ingresos, cada uno con lo suyo")
    void agrupaPorCliente() {
        IngresosCDE resultado = constructor.construir(
            List.of(salida(1, 10, "Batas", 5),
                    salida(2, 20, "Sabanas", 3),
                    salida(3, 10, "Camisolines", 2)),
            AsignadorClienteCDE.CLIENTE_ORIGINAL);

        assertEquals(2, resultado.ingresos().size());
        EquipoOtros cliente10 = resultado.ingresos().get(0);
        EquipoOtros cliente20 = resultado.ingresos().get(1);

        assertEquals(10, cliente10.getNroCliente());
        assertEquals(2, cliente10.getMateriales().size());
        assertEquals(List.of(1, 3), resultado.salidaIds().get(cliente10));

        assertEquals(20, cliente20.getNroCliente());
        assertEquals(1, cliente20.getMateriales().size());
        assertEquals(List.of(2), resultado.salidaIds().get(cliente20));
    }

    @Test
    @DisplayName("mismo cliente y mismo elemento se unifican en un material con la suma")
    void unificaMismoElemento() {
        IngresosCDE resultado = constructor.construir(
            List.of(salida(1, 10, "Batas", 5),
                    salida(2, 10, "Batas", 4)),
            AsignadorClienteCDE.CLIENTE_ORIGINAL);

        assertEquals(1, resultado.ingresos().size());
        EquipoOtros ingreso = resultado.ingresos().get(0);
        assertEquals(1, ingreso.getMateriales().size());
        assertEquals(9, ingreso.getMateriales().get(0).getCantidad());
        assertEquals(List.of(1, 2), resultado.salidaIds().get(ingreso));
    }

    @Test
    @DisplayName("mismo cliente y distinto elemento producen un ingreso con dos materiales")
    void distintoElementoNoSeUnifica() {
        IngresosCDE resultado = constructor.construir(
            List.of(salida(1, 10, "Batas", 5),
                    salida(2, 10, "Sabanas", 4)),
            AsignadorClienteCDE.CLIENTE_ORIGINAL);

        assertEquals(1, resultado.ingresos().size());
        EquipoOtros ingreso = resultado.ingresos().get(0);
        assertEquals(2, ingreso.getMateriales().size());
        assertEquals(5, materialLlamado(ingreso, "Batas").getCantidad());
        assertEquals(4, materialLlamado(ingreso, "Sabanas").getCantidad());
    }

    @Test
    @DisplayName("con un asignador fijo, tres clientes distintos caen en un solo ingreso")
    void asignadorFijoAgrupaTodoJunto() {
        IngresosCDE resultado = constructor.construir(
            List.of(salida(1, 10, "Batas", 5),
                    salida(2, 20, "Sabanas", 3),
                    salida(3, 30, "Camisolines", 2)),
            SIEMPRE_99);

        assertEquals(1, resultado.ingresos().size());
        EquipoOtros ingreso = resultado.ingresos().get(0);
        assertEquals(99, ingreso.getNroCliente());
        assertEquals(3, ingreso.getMateriales().size());
        assertEquals(List.of(1, 2, 3), resultado.salidaIds().get(ingreso));
    }

    @Test
    @DisplayName("con un asignador fijo, la unificación es por grupo y no por cliente original")
    void asignadorFijoUnificaEntreClientes() {
        IngresosCDE resultado = constructor.construir(
            List.of(salida(1, 10, "Batas", 5),
                    salida(2, 20, "Batas", 4)),
            SIEMPRE_99);

        assertEquals(1, resultado.ingresos().size());
        EquipoOtros ingreso = resultado.ingresos().get(0);
        assertEquals(1, ingreso.getMateriales().size());
        assertEquals(9, ingreso.getMateriales().get(0).getCantidad());
    }

    @Test
    @DisplayName("todo ingreso nace como DETALLES, sin lavado, con empaque y en NUEVO")
    void banderasDelIngreso() {
        IngresosCDE resultado = constructor.construir(
            List.of(salida(1, 10, "Batas", 5),
                    salida(2, 20, "Sabanas", 3)),
            AsignadorClienteCDE.CLIENTE_ORIGINAL);

        for (EquipoOtros ingreso : resultado.ingresos()) {
            assertEquals(TipoIngresoOtros.DETALLES, ingreso.getTipoIngreso());
            assertFalse(ingreso.isRequiereLavado());
            assertTrue(ingreso.isRequiereEmpaque());
            assertEquals(EstadoEquipo.NUEVO, ingreso.getEstado());
        }
    }

    @Test
    @DisplayName("lo que salió del lavadero no se vuelve a lavar: NUEVO salta a EMPAQUETADO")
    void saltaElLavadoEnCDE() {
        IngresosCDE resultado = constructor.construir(
            List.of(salida(1, 10, "Batas", 5),
                    salida(2, 20, "Sabanas", 3)),
            AsignadorClienteCDE.CLIENTE_ORIGINAL);

        for (EquipoOtros ingreso : resultado.ingresos()) {
            assertEquals(EstadoEquipo.EMPAQUETADO, ingreso.getSiguienteEstado(EstadoEquipo.NUEVO));
        }
    }

    @Test
    @DisplayName("una lista vacía no arma nada ni falla")
    void listaVacia() {
        IngresosCDE resultado = constructor.construir(List.of(), AsignadorClienteCDE.CLIENTE_ORIGINAL);

        assertTrue(resultado.ingresos().isEmpty());
        assertTrue(resultado.salidaIds().isEmpty());
    }

    @Test
    @DisplayName("CLIENTE_ORIGINAL usa el cliente de la salida")
    void clienteOriginalConservaElCliente() {
        assertEquals(42, AsignadorClienteCDE.CLIENTE_ORIGINAL.clientePara(salida(1, 42, "Batas", 5)));
    }

    @Test
    @DisplayName("dos ingresos con los mismos materiales son claves distintas del mapa")
    void mapaIndexadoPorInstancia() {
        IngresosCDE resultado = constructor.construir(
            List.of(salida(1, 10, "Batas", 5),
                    salida(2, 20, "Batas", 5)),
            AsignadorClienteCDE.CLIENTE_ORIGINAL);

        assertEquals(2, resultado.ingresos().size());
        assertEquals(2, resultado.salidaIds().size());
        assertEquals(List.of(1), resultado.salidaIds().get(resultado.ingresos().get(0)));
        assertEquals(List.of(2), resultado.salidaIds().get(resultado.ingresos().get(1)));
    }
}
