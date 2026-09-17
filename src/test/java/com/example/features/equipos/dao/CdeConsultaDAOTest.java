package com.example.features.equipos.dao;

import com.example.AbstractDAOTest;
import com.example.common.model.EquipoRegistrableInterface;
import com.example.common.model.EquipoRegistrableInterface.TipoEquipo;
import com.example.common.paginacion.CriteriosPagina;
import com.example.common.paginacion.Pagina;
import com.example.features.catalogo.dao.CatalogoOtrosDAO;
import com.example.features.equipos.model.FiltroEquipos;
import com.example.features.equipos.ortopedias.controller.helpers.CdeFilterCriteria;
import com.example.features.equipos.ortopedias.controller.helpers.CdeFilterStrategy;
import com.example.features.equipos.ortopedias.dao.EquipoDAO;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.model.Material;
import com.example.features.equipos.otros.dao.EquipoOtrosDAO;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.MaterialOtros;
import com.example.features.equipos.otros.model.TipoIngresoOtros;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La lista unificada de Estado de Procesos: {@code UNION ALL} de las dos tablas, paginado y
 * ordenado en SQL.
 *
 * <h2>Tres cosas distintas que hay que probar, y sólo una es obvia</h2>
 * <ol>
 *   <li><b>Qué filas entran</b> — {@link Equivalencia} lo compara contra {@code CdeFilterStrategy},
 *       que es el filtro que la pantalla usa hoy. Acá sí se <em>llama</em> al original y no se
 *       transcribe: es una clase plana, sin Swing.</li>
 *   <li><b>En qué orden salen</b> — {@link Orden}. La pantalla ordena por estado (más atrasado
 *       primero), no por fecha; si el orden no fuera global, la página 2 volvería a empezar por los
 *       más atrasados <em>de esa página</em>.</li>
 *   <li><b>Que cada equipo salga exactamente una vez al recorrer las páginas</b> — también en
 *       {@link Orden}, con empates a propósito. Es la propiedad que un orden no total rompe en
 *       silencio.</li>
 * </ol>
 */
class CdeConsultaDAOTest extends AbstractDAOTest {

    private final EquipoDAO equipoDAO = new EquipoDAO();
    private final EquipoOtrosDAO equipoOtrosDAO = new EquipoOtrosDAO(new CatalogoOtrosDAO());
    private final CdeConsultaDAO dao = new CdeConsultaDAO(equipoDAO, equipoOtrosDAO);

    private final CdeFilterStrategy estrategiaDeHoy = new CdeFilterStrategy();

    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM material_movimientos");
        ejecutarSQL("DELETE FROM equipo_materiales");
        ejecutarSQL("DELETE FROM equipos");
        ejecutarSQL("DELETE FROM otros_material_movimientos");
        ejecutarSQL("DELETE FROM equipo_otros_materiales");
        ejecutarSQL("DELETE FROM equipo_otros");
        ejecutarSQL("DELETE FROM catalogo_otros WHERE descripcion LIKE 'TestDesc%' OR descripcion = 'Elementos'");
    }

    // ── la unión trae las dos tablas, con materiales ─────────────────────────

    @Test
    @DisplayName("la página mezcla las dos tablas")
    void mezclaLasDosTablas() {
        ortopedia(1, 1, EstadoEquipo.NUEVO, 1, LocalDateTime.of(2026, 1, 1, 9, 0));
        otrosDetalles(1, EstadoEquipo.NUEVO, 1, LocalDateTime.of(2026, 1, 2, 9, 0));

        Pagina<EquipoRegistrableInterface> pagina =
            dao.obtenerPagina(FiltroEquipos.sinFiltros(), CriteriosPagina.primera());

        assertEquals(2, pagina.totalFilas());
        assertEquals(Set.of(TipoEquipo.ORTOPEDIA, TipoEquipo.OTROS),
            pagina.contenido().stream().map(EquipoRegistrableInterface::getTipo)
                .collect(java.util.stream.Collectors.toSet()));
    }

    /**
     * <b>Esto es lo que preserva que seleccionar una fila no dispare una consulta.</b>
     * {@code PantallaVerCDEv2} delega en {@code PanelEquipoMaterial}, que carga los materiales del
     * equipo seleccionado <em>desde el objeto en memoria</em>. Si la página trajera equipos sin
     * materiales, la tabla de abajo quedaría vacía y nadie lo vería como error de la paginación:
     * se leería como "este equipo no tiene materiales".
     */
    @Test
    @DisplayName("los equipos de la página vienen CON sus materiales, los de las dos tablas")
    void laPaginaTraeLosMateriales() {
        ortopedia(1, 1, EstadoEquipo.NUEVO, 4, LocalDateTime.of(2026, 1, 1, 9, 0));
        otrosDetalles(1, EstadoEquipo.NUEVO, 3, LocalDateTime.of(2026, 1, 2, 9, 0));

        Pagina<EquipoRegistrableInterface> pagina =
            dao.obtenerPagina(FiltroEquipos.sinFiltros(), CriteriosPagina.primera());

        for (EquipoRegistrableInterface equipo : pagina.contenido()) {
            int esperados = equipo.getTipo() == TipoEquipo.ORTOPEDIA ? 4 : 3;
            assertEquals(esperados, equipo.getMaterialesRegistrables().size(),
                "el equipo " + equipo.getTipo() + " id=" + equipo.getId() + " llegó sin sus materiales");
        }
    }

    /**
     * Mismo motivo que en los otros dos DAO: una fila por (equipo × material) haría que el
     * {@code LIMIT} cortara materiales. Acá además el corte cruza las dos tablas.
     */
    @Test
    @DisplayName("un equipo de 12 materiales en el borde sale UNA vez y completo")
    void equipoConMuchosMateriales_enElBordeDePagina() {
        // Todos en el mismo estado para que el orden lo decida la fecha: el más nuevo primero.
        for (int i = 0; i < 10; i++) {
            ortopedia(1, 1, EstadoEquipo.NUEVO, i == 4 ? 12 : 1,
                LocalDateTime.of(2026, 1, 20, 9, 0).minusDays(i));
        }

        Pagina<EquipoRegistrableInterface> pagina1 = dao.obtenerPagina(FiltroEquipos.sinFiltros(), new CriteriosPagina(1, 5));
        Pagina<EquipoRegistrableInterface> pagina2 = dao.obtenerPagina(FiltroEquipos.sinFiltros(), new CriteriosPagina(2, 5));

        assertEquals(5, pagina1.contenido().size(), "el LIMIT tiene que contar equipos, no materiales");
        EquipoRegistrableInterface enElBorde = pagina1.contenido().get(4);
        assertEquals(12, enElBorde.getMaterialesRegistrables().size());
        assertFalse(claves(pagina2.contenido()).contains(clave(enElBorde)),
            "y no puede aparecer también en la página siguiente");
    }

    @Test
    @DisplayName("contar y obtenerPagina hablan del mismo conjunto")
    void conteoYPaginaCoinciden() {
        sembrarElUniverso();
        FiltroEquipos filtro = new FiltroEquipos(
            List.of(EstadoEquipo.NUEVO.getNombre()), null, null, null, null, List.of(), null, null);

        assertEquals(dao.contar(filtro),
            dao.obtenerPagina(filtro, CriteriosPagina.primera()).totalFilas());
    }

    @Test
    @DisplayName("una página más allá del total viene vacía y no lanza")
    void paginaFueraDeRango() {
        sembrarElUniverso();

        Pagina<EquipoRegistrableInterface> pagina =
            dao.obtenerPagina(FiltroEquipos.sinFiltros(), new CriteriosPagina(9, 50));

        assertTrue(pagina.estaVacia());
        assertEquals(6, pagina.totalFilas());
    }

    // ── orden ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("orden")
    class Orden {

        /**
         * {@code EquipoTableModel.actualizarDatos} ordena por {@code calcularEstado().getOrden()}
         * ascendente. Ese orden pasa a venir de SQL, y tiene que ser <b>global</b>: si se calculara
         * dentro de cada página, la página 2 volvería a arrancar por los más atrasados de esa
         * página y el operador vería el mismo estado repetirse pestaña tras pestaña.
         */
        @Test
        @DisplayName("más atrasado primero, y el orden es global, no por página")
        void ordenPorEstadoAscendenteYGlobal() {
            // Se siembran a propósito en el orden contrario al que tienen que salir.
            ortopedia(1, 1, EstadoEquipo.ENTREGADO,    1, LocalDateTime.of(2026, 1, 1, 9, 0));
            otrosDetalles(1, EstadoEquipo.ESTERILIZADO, 1, LocalDateTime.of(2026, 1, 2, 9, 0));
            ortopedia(1, 1, EstadoEquipo.EMPAQUETADO,  1, LocalDateTime.of(2026, 1, 3, 9, 0));
            otrosDetalles(1, EstadoEquipo.LAVANDO,     1, LocalDateTime.of(2026, 1, 4, 9, 0));
            ortopedia(1, 1, EstadoEquipo.NUEVO,        1, LocalDateTime.of(2026, 1, 5, 9, 0));

            List<EstadoEquipo> recorrido = new ArrayList<>();
            for (int pagina = 1; pagina <= 5; pagina++) {
                dao.obtenerPagina(FiltroEquipos.sinFiltros(), new CriteriosPagina(pagina, 1))
                    .contenido().forEach(e -> recorrido.add(e.calcularEstado()));
            }

            assertEquals(List.of(EstadoEquipo.NUEVO, EstadoEquipo.LAVANDO, EstadoEquipo.EMPAQUETADO,
                    EstadoEquipo.ESTERILIZADO, EstadoEquipo.ENTREGADO),
                recorrido,
                "recorriendo página por página tiene que salir el orden global, no uno por página");
        }

        @Test
        @DisplayName("dentro del mismo estado manda la fecha, de la más nueva a la más vieja")
        void dentroDelEstadoOrdenaPorFechaDescendente() {
            int viejo  = ortopedia(1, 1, EstadoEquipo.NUEVO, 1, LocalDateTime.of(2026, 1, 1, 9, 0));
            int nuevo  = ortopedia(1, 1, EstadoEquipo.NUEVO, 1, LocalDateTime.of(2026, 1, 9, 9, 0));
            int medio  = ortopedia(1, 1, EstadoEquipo.NUEVO, 1, LocalDateTime.of(2026, 1, 5, 9, 0));

            List<Integer> orden = dao.obtenerPagina(FiltroEquipos.sinFiltros(), CriteriosPagina.primera())
                .contenido().stream().map(EquipoRegistrableInterface::getId).toList();

            assertEquals(List.of(nuevo, medio, viejo), orden);
        }

        /**
         * <b>La razón por la que el {@code ORDER BY} desempata con {@code tipo} e {@code id}.</b>
         * Los empates acá no son teóricos: la unión mezcla dos tablas con ids independientes (el
         * equipo 7 de ortopedia y el 7 de "otros" coexisten), {@code fecha_ingreso} tiene
         * resolución de segundo y el estado toma siete valores. Sin un orden total, una fila cae en
         * dos páginas y otra en ninguna — y la UI no tiene cómo notarlo.
         */
        @Test
        @DisplayName("con estado y fecha empatados en las dos tablas, cada equipo sale exactamente una vez")
        void ordenTotal_sinRepetidosNiFaltantes() {
            LocalDateTime mismaFecha = LocalDateTime.of(2026, 2, 2, 10, 0);
            for (int i = 0; i < 6; i++) {
                ortopedia(1, 1, EstadoEquipo.NUEVO, 1, mismaFecha);
                otrosDetalles(1, EstadoEquipo.NUEVO, 1, mismaFecha);
            }

            List<String> recorridas = new ArrayList<>();
            for (int pagina = 1; pagina <= 4; pagina++) {
                recorridas.addAll(claves(dao.obtenerPagina(
                    FiltroEquipos.sinFiltros(), new CriteriosPagina(pagina, 3)).contenido()));
            }

            assertEquals(12, recorridas.size());
            assertEquals(12, Set.copyOf(recorridas).size(),
                "ningún equipo repetido y ninguno faltante entre páginas: " + recorridas);
        }
    }

    // ── equivalencia con CdeFilterStrategy ───────────────────────────────────

    @Nested
    @DisplayName("equivalencia con CdeFilterStrategy")
    class Equivalencia {

        @Test
        @DisplayName("sin filtros")
        void sinFiltros() {
            sembrarElUniverso();
            exigirEquivalencia(FiltroEquipos.sinFiltros());
        }

        @Test
        @DisplayName("estados")
        void porEstado() {
            sembrarElUniverso();
            exigirEquivalencia(new FiltroEquipos(
                List.of(EstadoEquipo.NUEVO.getNombre(), EstadoEquipo.LAVANDO.getNombre()),
                null, null, null, null, List.of(), null, null));
        }

        /**
         * El default de la pantalla: {@code aplicarFiltroInicial()} entra con todos los estados
         * tildados <b>menos</b> ENTREGADO. Es el filtro que ve el 99 % de las visitas.
         */
        @Test
        @DisplayName("el default de la pantalla: todos los estados menos ENTREGADO")
        void defaultDeLaPantalla() {
            sembrarElUniverso();
            List<String> menosEntregado = java.util.Arrays.stream(EstadoEquipo.values())
                .filter(e -> e != EstadoEquipo.ENTREGADO)
                .map(EstadoEquipo::getNombre)
                .toList();

            exigirEquivalencia(new FiltroEquipos(
                menosEntregado, null, null, null, null, List.of(), null, null));
        }

        @Test
        @DisplayName("cliente")
        void porCliente() {
            sembrarElUniverso();
            exigirEquivalencia(new FiltroEquipos(
                List.of(), "clinica", null, null, null, List.of(), null, null));
        }

        /**
         * <b>La semántica rara que hay que preservar exactamente.</b> {@code CdeFilterStrategy}
         * filtra por {@code getDescripcionSecundaria()}, que para "otros" es cadena vacía: con el
         * campo institución escrito, los "otros" desaparecen de la grilla; con el campo en blanco,
         * aparecen todos. No se arregla: es el comportamiento que el operador conoce, y arreglarlo
         * sería un cambio visible decidido de contrabando.
         */
        @Test
        @DisplayName("institución escrita: los 'otros' desaparecen, igual que hoy")
        void porInstitucion_excluyeALosOtros() {
            sembrarElUniverso();
            FiltroEquipos filtro = new FiltroEquipos(
                List.of(), null, null, null, "clinica", List.of(), null, null);

            Pagina<EquipoRegistrableInterface> pagina = dao.obtenerPagina(filtro, new CriteriosPagina(1, 1000));

            assertFalse(pagina.estaVacia(), "las ortopedias de esa institución sí tienen que entrar");
            assertTrue(pagina.contenido().stream().allMatch(e -> e.getTipo() == TipoEquipo.ORTOPEDIA),
                "ningún 'otros' puede entrar con el campo institución escrito");
            exigirEquivalencia(filtro);
        }

        @Test
        @DisplayName("institución en blanco: los 'otros' vuelven")
        void institucionEnBlanco_incluyeALosOtros() {
            sembrarElUniverso();
            FiltroEquipos filtro = new FiltroEquipos(
                List.of(), null, null, null, "  ", List.of(), null, null);

            Pagina<EquipoRegistrableInterface> pagina = dao.obtenerPagina(filtro, new CriteriosPagina(1, 1000));

            assertTrue(pagina.contenido().stream().anyMatch(e -> e.getTipo() == TipoEquipo.OTROS));
            exigirEquivalencia(filtro);
        }

        @Test
        @DisplayName("cliente e institución y estados, combinados")
        void combinados() {
            sembrarElUniverso();
            exigirEquivalencia(new FiltroEquipos(
                List.of(EstadoEquipo.NUEVO.getNombre()), "clinica", null, null, "clinica",
                List.of(), null, null));
        }

        /**
         * Compara el conjunto que devuelve el SQL contra el que devuelve {@code CdeFilterStrategy}
         * sobre el mismo universo — el original, no una transcripción: es una clase plana.
         * Conjuntos y no listas, porque el orden lo cuidan los tests de {@link Orden}.
         */
        private void exigirEquivalencia(FiltroEquipos filtro) {
            List<EquipoRegistrableInterface> universo = new ArrayList<>();
            universo.addAll(equipoDAO.obtenerTodos());
            universo.addAll(equipoOtrosDAO.obtenerTodos());

            CdeFilterCriteria criterio = new CdeFilterCriteria(
                filtro.cliente() == null ? "" : filtro.cliente(),
                filtro.institucion() == null ? "" : filtro.institucion(),
                filtro.estados());
            Set<String> enMemoria = Set.copyOf(claves(estrategiaDeHoy.filter(universo, criterio)));

            Pagina<EquipoRegistrableInterface> pagina = dao.obtenerPagina(filtro, new CriteriosPagina(1, 1000));

            assertEquals(enMemoria, Set.copyOf(claves(pagina.contenido())),
                "el filtro en SQL y CdeFilterStrategy tienen que dar el mismo conjunto");
            assertEquals(enMemoria.size(), pagina.totalFilas(),
                "y el total tiene que coincidir con ese conjunto");
        }
    }

    // ── siembra ──────────────────────────────────────────────────────────────

    /**
     * Un universo chico con las dos tablas, varios estados, dos clientes y dos instituciones —
     * suficiente para que cada filtro deje afuera algo y no pase por ser todo igual.
     */
    private void sembrarElUniverso() {
        ortopedia(1, 1, EstadoEquipo.NUEVO,        1, LocalDateTime.of(2026, 1, 1, 9, 0));
        ortopedia(3, 2, EstadoEquipo.LAVANDO,      1, LocalDateTime.of(2026, 1, 2, 9, 0));
        ortopedia(3, 2, EstadoEquipo.ENTREGADO,    1, LocalDateTime.of(2026, 1, 3, 9, 0));
        otrosDetalles(1, EstadoEquipo.NUEVO,       1, LocalDateTime.of(2026, 1, 4, 9, 0));
        otrosDetalles(3, EstadoEquipo.ESTERILIZADO, 1, LocalDateTime.of(2026, 1, 5, 9, 0));
        otrosRemito(1, LocalDateTime.of(2026, 1, 6, 9, 0));
    }

    private int ortopedia(int cliente, int institucion, EstadoEquipo estado, int materiales,
                          LocalDateTime fecha) {
        Equipo equipo = new Equipo();
        equipo.setNroCliente(cliente);
        equipo.setNroProfesional(1);
        equipo.setNroInstitucion(institucion);
        equipo.setPacienteNombre("Paciente");
        equipo.setEstado(estado);
        for (int i = 0; i < materiales; i++) {
            equipo.agregarMaterial(new Material(400 + i, "Tornillera", 1, estado));
        }
        equipoDAO.guardarEquipo(equipo);
        fecharEn("equipos", equipo.getId(), fecha);
        return equipo.getId();
    }

    private int otrosDetalles(int cliente, EstadoEquipo estado, int materiales, LocalDateTime fecha) {
        EquipoOtros equipo = new EquipoOtros();
        equipo.setNroCliente(cliente);
        equipo.setTipoIngreso(TipoIngresoOtros.DETALLES);
        equipo.setEstado(estado);
        for (int i = 0; i < materiales; i++) {
            equipo.agregarMaterial(new MaterialOtros(null, "TestDescRopa " + i, 1, estado));
        }
        equipoOtrosDAO.guardar(equipo);
        fecharEn("equipo_otros", equipo.getId(), fecha);
        return equipo.getId();
    }

    private int otrosRemito(int cliente, LocalDateTime fecha) {
        EquipoOtros equipo = new EquipoOtros();
        equipo.setNroCliente(cliente);
        equipo.setTipoIngreso(TipoIngresoOtros.REMITO);
        equipo.setRemitoCantidad(5);
        equipoOtrosDAO.guardar(equipo);
        fecharEn("equipo_otros", equipo.getId(), fecha);
        return equipo.getId();
    }

    private void fecharEn(String tabla, int id, LocalDateTime fecha) {
        try {
            ejecutarSQL("UPDATE " + tabla + " SET fecha_ingreso = '"
                + java.sql.Timestamp.valueOf(fecha) + "' WHERE id = " + id);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo preparar el fixture", e);
        }
    }

    /**
     * Identidad de un equipo dentro de la unión: el id solo no alcanza, porque el equipo 7 de
     * ortopedia y el 7 de "otros" son dos equipos distintos.
     */
    private static String clave(EquipoRegistrableInterface equipo) {
        return equipo.getTipo() + "#" + equipo.getId();
    }

    private static List<String> claves(List<EquipoRegistrableInterface> equipos) {
        return equipos.stream().map(CdeConsultaDAOTest::clave).toList();
    }
}
