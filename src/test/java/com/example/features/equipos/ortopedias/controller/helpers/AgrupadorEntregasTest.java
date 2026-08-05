package com.example.features.equipos.ortopedias.controller.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.common.model.EntregaDestinoKey;
import com.example.common.model.EntregaDestinoKey.TipoDestino;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.model.Material;
import com.example.features.equipos.ortopedias.service.EstadoValidatorImpl;
import com.example.features.equipos.ortopedias.service.IEstadoValidator;
import com.example.features.equipos.ortopedias.view.helpers.MaterialEntregaItem;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.MaterialOtros;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AgrupadorEntregasTest {

    private final IEstadoValidator validator = new EstadoValidatorImpl();
    private AgrupadorEntregas agrupador;

    @BeforeEach
    void setUp() {
        agrupador = new AgrupadorEntregas(validator);
    }

    @Test
    @DisplayName("sin equipos no hay destinos")
    void agrupar_sinEquipos() {
        AgrupadorEntregas.Resultado resultado = agrupador.agrupar(List.of(), List.of());

        assertTrue(resultado.filas().isEmpty());
        assertTrue(resultado.materialesPorDestino().isEmpty());
        assertTrue(resultado.volumenPorDestino().isEmpty());
    }

    @Test
    @DisplayName("agrupa ortopedias por institución y descuenta lo ya entregado")
    void agrupar_ortopediaDescuentaEntregado() {
        Equipo equipo = ortopedia(7, "Hospital Central",
            material(100, "Placa", 5, EstadoEquipo.ESTERILIZADO),
            material(100, "Placa", 2, EstadoEquipo.ENTREGADO));

        AgrupadorEntregas.Resultado resultado = agrupador.agrupar(List.of(equipo), List.of());

        EntregaDestinoKey key = new EntregaDestinoKey(TipoDestino.INSTITUCION, 7);
        List<MaterialEntregaItem> materiales = resultado.materialesPorDestino().get(key);
        assertEquals(1, materiales.size());
        assertEquals("Placa", materiales.get(0).getMaterial());
        assertEquals(5, materiales.get(0).getCantidad());
        assertEquals(1, resultado.filas().size());
        assertEquals("Hospital Central", resultado.filas().get(0).getNombre());
    }

    @Test
    @DisplayName("un material entregado por completo no genera fila")
    void agrupar_todoEntregadoNoAparece() {
        Equipo equipo = ortopedia(7, "Hospital Central",
            material(100, "Placa", 3, EstadoEquipo.ENTREGADO));

        AgrupadorEntregas.Resultado resultado = agrupador.agrupar(List.of(equipo), List.of());

        assertTrue(resultado.filas().isEmpty(), "sin materiales pendientes no debe haber destino");
    }

    @Test
    @DisplayName("los equipos sin institución caen en el destino 'sin institución'")
    void agrupar_sinInstitucion() {
        Equipo equipo = ortopedia(null, "  ",
            material(100, "Placa", 1, EstadoEquipo.ESTERILIZADO));

        AgrupadorEntregas.Resultado resultado = agrupador.agrupar(List.of(equipo), List.of());

        assertEquals(1, resultado.filas().size());
        assertEquals(-1, resultado.filas().get(0).getKey().getId());
    }

    @Test
    @DisplayName("un REMITO sin filas reales rinde una sola fila Elementos y acumula volumen")
    void agrupar_remitoSinFilas() {
        EquipoOtros equipo = otros(3, "Clínica Norte", 12, 40, List.of());

        AgrupadorEntregas.Resultado resultado = agrupador.agrupar(List.of(), List.of(equipo));

        EntregaDestinoKey key = new EntregaDestinoKey(TipoDestino.CLIENTE, 3);
        assertEquals(List.of("Elementos"),
            resultado.materialesPorDestino().get(key).stream().map(MaterialEntregaItem::getMaterial).toList());
        assertEquals(12, resultado.materialesPorDestino().get(key).get(0).getCantidad());
        assertEquals(40, resultado.volumenPorDestino().get(key));
    }

    @Test
    @DisplayName("un REMITO ya entregado y sin filas reales igual genera fila: no lo filtra el agrupador")
    void agrupar_remitoEntregadoSinFilas_dependeDeLaConsulta() {
        // esEntregable() es "orden >= ESTERILIZADO", así que ENTREGADO la pasa, y sin
        // filas de material no hay nada que descontar: este agrupador, solo, no puede
        // excluirlo. Quien lo excluye es el WHERE de obtenerActivos(), que no trae los
        // equipos entregados — ver EquipoOtrosDAOTest.
        //
        // El caso no es alcanzable con datos reales (un remito que se movió ya tiene
        // filas), pero el test fija de quién depende la pantalla: si alguien vuelve a
        // alimentarla con el histórico completo, los entregados reaparecen acá.
        EquipoOtros entregado = otros(3, "Clínica Norte", 12, 40, List.of());
        when(entregado.calcularEstado()).thenReturn(EstadoEquipo.ENTREGADO);

        AgrupadorEntregas.Resultado resultado = agrupador.agrupar(List.of(), List.of(entregado));

        assertEquals(1, resultado.filas().size(),
            "el filtro por estado vive en la consulta, no acá");
    }

    @Test
    @DisplayName("un REMITO con filas ya entregadas no genera fila")
    void agrupar_remitoConFilasEntregadas_noAparece() {
        // Con filas reales sí hay de dónde descontar, y el agrupador se vale por sí mismo.
        EquipoOtros equipo = otros(3, "Clínica Norte", 12, 40,
            List.of(materialOtros("Elementos", 12, EstadoEquipo.ENTREGADO)));

        AgrupadorEntregas.Resultado resultado = agrupador.agrupar(List.of(), List.of(equipo));

        assertTrue(resultado.filas().isEmpty());
    }

    @Test
    @DisplayName("dos equipos del mismo cliente suman volumen y comparten destino")
    void agrupar_otrosDelMismoClienteSeSuman() {
        EquipoOtros uno = otros(3, "Clínica Norte", 1, 40, List.of());
        EquipoOtros dos = otros(3, "Clínica Norte", 2, 25, List.of());

        AgrupadorEntregas.Resultado resultado = agrupador.agrupar(List.of(), List.of(uno, dos));

        EntregaDestinoKey key = new EntregaDestinoKey(TipoDestino.CLIENTE, 3);
        assertEquals(1, resultado.filas().size());
        assertEquals(2, resultado.filas().get(0).getEquiposCount());
        assertEquals(65, resultado.volumenPorDestino().get(key));
    }

    @Test
    @DisplayName("los destinos salen ordenados por nombre, sin distinguir mayúsculas")
    void agrupar_ordenaPorNombre() {
        Equipo zeta = ortopedia(1, "zeta", material(100, "Placa", 1, EstadoEquipo.ESTERILIZADO));
        Equipo alfa = ortopedia(2, "Alfa",  material(100, "Placa", 1, EstadoEquipo.ESTERILIZADO));

        AgrupadorEntregas.Resultado resultado = agrupador.agrupar(List.of(zeta, alfa), List.of());

        assertEquals(List.of("Alfa", "zeta"),
            resultado.filas().stream().map(f -> f.getNombre()).toList());
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private static Equipo ortopedia(Integer nroInstitucion, String institucion, Material... materiales) {
        Equipo equipo = mock(Equipo.class);
        when(equipo.getId()).thenReturn(1);
        when(equipo.getNroInstitucion()).thenReturn(nroInstitucion);
        when(equipo.getInstitucionNombre()).thenReturn(institucion);
        when(equipo.getMateriales()).thenReturn(List.of(materiales));
        when(equipo.calcularEstado()).thenReturn(EstadoEquipo.ESTERILIZADO);
        return equipo;
    }

    private static Material material(int codigo, String descripcion, int cantidad, EstadoEquipo estado) {
        Material material = mock(Material.class);
        when(material.getCodigo()).thenReturn(codigo);
        when(material.getDescripcion()).thenReturn(descripcion);
        when(material.getCantidad()).thenReturn(cantidad);
        when(material.getEstado()).thenReturn(estado);
        return material;
    }

    private static MaterialOtros materialOtros(String descripcion, int cantidad, EstadoEquipo estado) {
        MaterialOtros material = mock(MaterialOtros.class);
        when(material.getDescripcion()).thenReturn(descripcion);
        when(material.getCantidad()).thenReturn(cantidad);
        when(material.getEstado()).thenReturn(estado);
        return material;
    }

    private static EquipoOtros otros(int nroCliente, String cliente, int remitoCantidad,
                                     int volumen, List<MaterialOtros> materiales) {
        EquipoOtros equipo = mock(EquipoOtros.class);
        when(equipo.getId()).thenReturn(nroCliente * 100 + remitoCantidad);
        when(equipo.getNroCliente()).thenReturn(nroCliente);
        when(equipo.getClienteNombre()).thenReturn(cliente);
        when(equipo.getRemitoCantidad()).thenReturn(remitoCantidad);
        when(equipo.getVolumenEquipo()).thenReturn(volumen);
        when(equipo.getMateriales()).thenReturn(materiales);
        when(equipo.calcularEstado()).thenReturn(EstadoEquipo.ESTERILIZADO);
        return equipo;
    }
}
