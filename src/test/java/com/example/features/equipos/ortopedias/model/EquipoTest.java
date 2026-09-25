package com.example.features.equipos.ortopedias.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EquipoTest {

    private static final LocalDateTime AYER = LocalDateTime.of(2026, 9, 24, 10, 0);

    private static Equipo equipoConMateriales(Material... materiales) {
        Equipo equipo = new Equipo();
        equipo.setId(7);
        equipo.setNroCliente(3);
        equipo.setClienteNombre("Cliente");
        equipo.setNroProfesional(11);
        equipo.setProfesionalNombre("Dr. X");
        equipo.setPacienteNombre("Paciente");
        equipo.setNroInstitucion(5);
        equipo.setInstitucionNombre("Hospital");
        equipo.setFechaIngreso(AYER);
        equipo.setRequiereLavado(false);
        equipo.setRequiereEmpaque(true);
        equipo.setEstado(EstadoEquipo.NUEVO);
        equipo.setVersion(4);
        for (Material m : materiales) equipo.agregarMaterial(m);
        return equipo;
    }

    /** Cantidad por estado de un código: la vista que importa del preview, sin depender del orden. */
    private static Map<EstadoEquipo, Integer> cantidadesPorEstado(Equipo equipo, int codigo) {
        return equipo.getMateriales().stream()
            .filter(m -> m.getCodigo() == codigo)
            .collect(Collectors.groupingBy(Material::getEstado,
                Collectors.summingInt(Material::getCantidad)));
    }

    // ── copiarParaPreview ────────────────────────────────────────────────────

    @Test
    @DisplayName("un preview sobre la copia no toca el original ni sus materiales")
    void copiarParaPreview_esIndependiente() {
        Material original = new Material(1, 400, "Tornillo", 10, EstadoEquipo.NUEVO, AYER);
        Equipo equipo = equipoConMateriales(original);

        Equipo copia = equipo.copiarParaPreview();
        copia.aplicarMovimientoPreview(copia.getMateriales().get(0), 4, EstadoEquipo.LAVANDO);

        assertEquals(1, equipo.getMateriales().size());
        assertEquals(10, original.getCantidad());
        assertEquals(EstadoEquipo.NUEVO, original.getEstado());
        assertEquals(2, copia.getMateriales().size());
    }

    @Test
    @DisplayName("la copia es profunda: equipo y cada material son objetos nuevos")
    void copiarParaPreview_esProfunda() {
        Material m1 = new Material(1, 400, "Tornillo", 10, EstadoEquipo.NUEVO, AYER);
        Material m2 = new Material(2, 500, "Placa", 1, EstadoEquipo.LAVADO, null);
        Equipo equipo = equipoConMateriales(m1, m2);

        Equipo copia = equipo.copiarParaPreview();

        assertNotSame(equipo, copia);
        assertNotSame(equipo.getMateriales(), copia.getMateriales());
        assertNotSame(m1, copia.getMateriales().get(0));
        assertNotSame(m2, copia.getMateriales().get(1));
    }

    @Test
    @DisplayName("la copia conserva ids, cantidades, estados, ultimoMovimiento y los datos del equipo")
    void copiarParaPreview_conservaIdsYUltimoMovimiento() {
        Material m1 = new Material(1, 400, "Tornillo", 10, EstadoEquipo.NUEVO, AYER);
        m1.setLoteIdNegocio("L-1");
        Equipo equipo = equipoConMateriales(m1);

        Equipo copia = equipo.copiarParaPreview();
        Material c1 = copia.getMateriales().get(0);

        assertEquals(1, c1.getId());
        assertEquals(400, c1.getCodigo());
        assertEquals("Tornillo", c1.getDescripcion());
        assertEquals(10, c1.getCantidad());
        assertEquals(EstadoEquipo.NUEVO, c1.getEstado());
        assertEquals(AYER, c1.getUltimoMovimiento());
        assertEquals("L-1", c1.getLoteIdNegocio());

        assertEquals(7, copia.getId());
        assertEquals(3, copia.getNroCliente());
        assertEquals("Cliente", copia.getClienteNombre());
        assertEquals(11, copia.getNroProfesional());
        assertEquals("Dr. X", copia.getProfesionalNombre());
        assertEquals("Paciente", copia.getPacienteNombre());
        assertEquals(5, copia.getNroInstitucion());
        assertEquals("Hospital", copia.getInstitucionNombre());
        assertEquals(AYER, copia.getFechaIngreso());
        assertEquals(false, copia.isRequiereLavado());
        assertEquals(true, copia.isRequiereEmpaque());
        assertEquals(EstadoEquipo.NUEVO, copia.getEstado());
        assertEquals(4, copia.getVersion());
    }

    // ── Previews encadenados (lo que hará el avance múltiple) ────────────────

    @Test
    @DisplayName("dos previews seguidos sobre materiales distintos: cada uno termina en su destino")
    void aplicarMovimientoPreview_dosMaterialesDistintosSeguidos_cadaUnoTerminaEnSuDestino() {
        Material tornillo = new Material(1, 400, "Tornillo", 10, EstadoEquipo.NUEVO, AYER);
        Material placa    = new Material(2, 500, "Placa", 3, EstadoEquipo.NUEVO, AYER);
        Equipo equipo = equipoConMateriales(tornillo, placa);

        equipo.aplicarMovimientoPreview(tornillo, 4, EstadoEquipo.LAVANDO);
        equipo.aplicarMovimientoPreview(placa, 3, EstadoEquipo.LAVANDO);

        assertEquals(Map.of(EstadoEquipo.NUEVO, 6, EstadoEquipo.LAVANDO, 4), cantidadesPorEstado(equipo, 400));
        assertEquals(Map.of(EstadoEquipo.LAVANDO, 3), cantidadesPorEstado(equipo, 500));
    }

    @Test
    @DisplayName("dos filas del mismo código y estado avanzadas seguidas no pierden cantidad")
    void aplicarMovimientoPreview_dosFilasMismoCodigoMismoEstado_noPierdeCantidad() {
        Material a = new Material(1, 400, "Tornillo", 5, EstadoEquipo.NUEVO, AYER);
        Material b = new Material(2, 400, "Tornillo", 7, EstadoEquipo.NUEVO, AYER.plusHours(1));
        Equipo equipo = equipoConMateriales(a, b);

        equipo.aplicarMovimientoPreview(a, 5, EstadoEquipo.LAVANDO);
        equipo.aplicarMovimientoPreview(b, 3, EstadoEquipo.LAVANDO);

        Map<EstadoEquipo, Integer> cantidades = cantidadesPorEstado(equipo, 400);
        assertEquals(Map.of(EstadoEquipo.NUEVO, 4, EstadoEquipo.LAVANDO, 8), cantidades);
        assertEquals(12, cantidades.values().stream().mapToInt(Integer::intValue).sum());
        List<Material> enLavando = equipo.getMateriales().stream()
            .filter(m -> m.getEstado() == EstadoEquipo.LAVANDO).collect(Collectors.toList());
        assertEquals(1, enLavando.size(), "el preview unifica las filas del mismo código y estado");
    }
}
