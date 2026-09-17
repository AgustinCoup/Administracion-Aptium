package com.example.features.equipos.dao;

import com.example.AbstractDAOTest;
import com.example.common.paginacion.CriteriosPagina;
import com.example.common.paginacion.Pagina;
import com.example.features.catalogo.dao.CatalogoOtrosDAO;
import com.example.features.equipos.model.FiltroEquipos;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.otros.dao.EquipoOtrosDAO;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.MaterialOtros;
import com.example.features.equipos.otros.model.TipoIngresoOtros;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La paginación en SQL de la grilla de "otros" de Ver Equipos. Misma forma que
 * {@link EquipoDAOPaginacionTest}, y por las mismas razones; acá se agregan las dos cosas propias
 * de esta tabla: el filtro por <b>tipo de ingreso</b> y el hecho de que profesional, paciente e
 * institución <b>no</b> la filtran.
 */
class EquipoOtrosDAOPaginacionTest extends AbstractDAOTest {

    private final EquipoOtrosDAO dao = new EquipoOtrosDAO(new CatalogoOtrosDAO());

    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM otros_material_movimientos");
        ejecutarSQL("DELETE FROM equipo_otros_materiales");
        ejecutarSQL("DELETE FROM equipo_otros");
        ejecutarSQL("DELETE FROM catalogo_otros WHERE descripcion LIKE 'TestDesc%' OR descripcion = 'Elementos'");
    }

    @Nested
    @DisplayName("bordes de página")
    class Bordes {

        @Test
        @DisplayName("la página 1 trae el tamaño pedido y el total es el de la tabla entera")
        void primeraPaginaCompleta() {
            sembrar(51);

            Pagina<EquipoOtros> pagina = dao.obtenerPagina(FiltroEquipos.sinFiltros(), new CriteriosPagina(1, 50));

            assertEquals(50, pagina.contenido().size());
            assertEquals(51, pagina.totalFilas());
        }

        @Test
        @DisplayName("una página más allá del total viene vacía y no lanza")
        void paginaFueraDeRango() {
            sembrar(3);

            Pagina<EquipoOtros> pagina = dao.obtenerPagina(FiltroEquipos.sinFiltros(), new CriteriosPagina(9, 50));

            assertTrue(pagina.estaVacia());
            assertEquals(3, pagina.totalFilas());
        }

        /**
         * <b>El test del plegado.</b> {@code SQL_CABECERA} devuelve una fila por (equipo ×
         * material): con un {@code LIMIT} sobre esa consulta, el corte caería sobre los materiales
         * y el equipo del borde llegaría partido.
         */
        @Test
        @DisplayName("un equipo de 12 materiales en el borde sale UNA vez y con sus 12 materiales")
        void equipoConMuchosMateriales_enElBordeDePagina() {
            for (int i = 0; i < 51; i++) {
                int materiales = (i == 49) ? 12 : 1;
                int id = sembrarUno(materiales);
                fecharEn(id, LocalDateTime.of(2026, 1, 1, 12, 0).minusDays(i));
            }

            Pagina<EquipoOtros> pagina1 = dao.obtenerPagina(FiltroEquipos.sinFiltros(), new CriteriosPagina(1, 50));
            Pagina<EquipoOtros> pagina2 = dao.obtenerPagina(FiltroEquipos.sinFiltros(), new CriteriosPagina(2, 50));

            assertEquals(50, pagina1.contenido().size(), "el LIMIT tiene que contar equipos, no materiales");
            EquipoOtros enElBorde = pagina1.contenido().get(49);
            assertEquals(12, enElBorde.getMateriales().size());
            assertEquals(1, pagina2.contenido().size());
            assertFalse(ids(pagina2.contenido()).contains(enElBorde.getId()));
        }

        @Test
        @DisplayName("con fechas repetidas, recorrer las páginas devuelve cada equipo exactamente una vez")
        void ordenTotal_sinRepetidosNiFaltantes() {
            for (int i = 0; i < 25; i++) {
                fecharEn(sembrarUno(1), LocalDateTime.of(2026, 3, 10, 9, 0));
            }

            List<Integer> recorridas = new ArrayList<>();
            for (int pagina = 1; pagina <= 5; pagina++) {
                recorridas.addAll(ids(dao.obtenerPagina(
                    FiltroEquipos.sinFiltros(), new CriteriosPagina(pagina, 5)).contenido()));
            }

            assertEquals(25, recorridas.size());
            assertEquals(25, Set.copyOf(recorridas).size(), "ningún equipo repetido entre páginas");
        }
    }

    @Test
    @DisplayName("contar y obtenerPagina hablan del mismo conjunto")
    void conteoYPaginaCoinciden() {
        sembrarElUniverso();
        FiltroEquipos filtro = new FiltroEquipos(
            List.of(), null, null, null, null, List.of(TipoIngresoOtros.REMITO.getNombre()), null, null);

        assertEquals(dao.contar(filtro),
            dao.obtenerPagina(filtro, CriteriosPagina.primera()).totalFilas());
    }

    @Test
    @DisplayName("un REMITO sin materiales viaja en la página igual que los demás")
    void remitoSinMateriales_entraEnLaPagina() {
        int remito = sembrarRemito(5, LocalDateTime.of(2026, 1, 1, 9, 0));

        Pagina<EquipoOtros> pagina = dao.obtenerPagina(FiltroEquipos.sinFiltros(), CriteriosPagina.primera());

        EquipoOtros traido = pagina.contenido().stream()
            .filter(e -> e.getId() == remito).findFirst().orElseThrow();
        assertTrue(traido.getMateriales().isEmpty());
        assertEquals(5, traido.getRemitoCantidad());
    }

    @Nested
    @DisplayName("equivalencia con el filtrado en memoria de Ver Equipos")
    class Equivalencia {

        @Test
        @DisplayName("estados")
        void porEstado() {
            sembrarElUniverso();
            exigirEquivalencia(new FiltroEquipos(
                List.of(EstadoEquipo.NUEVO.getNombre()), null, null, null, null, List.of(), null, null));
        }

        @Test
        @DisplayName("cliente")
        void porCliente() {
            sembrarElUniverso();
            exigirEquivalencia(new FiltroEquipos(
                List.of(), "clinica", null, null, null, List.of(), null, null));
        }

        @Test
        @DisplayName("tipo de ingreso")
        void porTipoIngreso() {
            sembrarElUniverso();
            exigirEquivalencia(new FiltroEquipos(
                List.of(), null, null, null, null, List.of(TipoIngresoOtros.REMITO.getNombre()),
                null, null));
        }

        @Test
        @DisplayName("rango de fechas")
        void porFechas() {
            sembrarElUniverso();
            exigirEquivalencia(new FiltroEquipos(
                List.of(), null, null, null, null, List.of(),
                LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 4)));
        }

        @Test
        @DisplayName("los cuatro filtros combinados")
        void combinados() {
            sembrarElUniverso();
            exigirEquivalencia(new FiltroEquipos(
                List.of(EstadoEquipo.NUEVO.getNombre()), "clinica", null, null, null,
                List.of(TipoIngresoOtros.DETALLES.getNombre()),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5)));
        }

        /**
         * La semántica que hay que <b>preservar</b>, no arreglar: escribir un profesional, un
         * paciente o una institución filtra la grilla de ortopedias y deja ésta intacta. Si algún
         * día se decide cambiarlo, se cambia a propósito — y este test es el que lo va a avisar.
         */
        @Test
        @DisplayName("profesional, paciente e institución no filtran esta grilla, igual que hoy")
        void tresCamposQueNoAplican() {
            sembrarElUniverso();
            FiltroEquipos conLosTres = new FiltroEquipos(
                List.of(), null, "aznar", "pérez", "hospital", List.of(), null, null);

            assertEquals(
                ids(dao.obtenerPagina(FiltroEquipos.sinFiltros(), new CriteriosPagina(1, 1000)).contenido()),
                ids(dao.obtenerPagina(conLosTres, new CriteriosPagina(1, 1000)).contenido()),
                "los tres campos no aplican a 'otros': la grilla tiene que quedar igual");
            exigirEquivalencia(conLosTres);
        }

        @Test
        @DisplayName("hasta inclusivo: un equipo de las 23:59 del día hasta entra")
        void hastaInclusivo() {
            int id = sembrarUno(1);
            fecharEn(id, LocalDateTime.of(2026, 1, 31, 23, 59, 59));

            FiltroEquipos filtro = new FiltroEquipos(List.of(), null, null, null, null, List.of(),
                null, LocalDate.of(2026, 1, 31));

            assertEquals(List.of(id), ids(dao.obtenerPagina(filtro, CriteriosPagina.primera()).contenido()));
            exigirEquivalencia(filtro);
        }

        /**
         * {@code equipo_otros.fecha_ingreso} es nullable (V2). Misma regla que en ortopedias: sin
         * fecha se entra sólo si los dos extremos están abiertos.
         */
        @Test
        @DisplayName("un equipo sin fecha entra sin filtro de fechas y sale con cualquier extremo")
        void fechaNula() {
            fecharEn(sembrarUno(1), LocalDateTime.of(2026, 1, 15, 10, 0));
            int sinFecha = sembrarUno(1);
            desfechar(sinFecha);

            FiltroEquipos sinExtremos = FiltroEquipos.sinFiltros();
            assertTrue(ids(dao.obtenerPagina(sinExtremos, CriteriosPagina.primera()).contenido())
                .contains(sinFecha));
            exigirEquivalencia(sinExtremos);

            FiltroEquipos conDesde = new FiltroEquipos(List.of(), null, null, null, null, List.of(),
                LocalDate.of(2020, 1, 1), null);
            assertFalse(ids(dao.obtenerPagina(conDesde, CriteriosPagina.primera()).contenido())
                .contains(sinFecha));
            exigirEquivalencia(conDesde);
        }

        private void exigirEquivalencia(FiltroEquipos filtro) {
            Set<Integer> enMemoria = Set.copyOf(ids(FiltradoDeReferencia.filtrar(dao.obtenerTodos(), filtro)));

            Pagina<EquipoOtros> pagina = dao.obtenerPagina(filtro, new CriteriosPagina(1, 1000));

            assertEquals(enMemoria, Set.copyOf(ids(pagina.contenido())),
                "el filtro en SQL y el filtro en memoria tienen que dar el mismo conjunto");
            assertEquals(enMemoria.size(), pagina.totalFilas());
        }
    }

    /**
     * La rama de "otros" de {@code VerEquiposController.aplicarFiltros}, transcripta tal cual: sólo
     * estados, cliente, tipo de ingreso y fechas. Ver el javadoc de la clase gemela en
     * {@link EquipoDAOPaginacionTest} para por qué se transcribe en vez de llamarse.
     */
    private static final class FiltradoDeReferencia {

        static List<EquipoOtros> filtrar(List<EquipoOtros> todos, FiltroEquipos f) {
            String cliente = f.cliente() == null ? "" : f.cliente().trim().toLowerCase();

            return todos.stream()
                .filter(e -> f.estados().isEmpty() || f.estados().contains(e.getEstado().getNombre()))
                .filter(e -> cumpleTexto(e.getClienteNombre(), cliente))
                .filter(e -> f.tiposIngreso().isEmpty()
                          || f.tiposIngreso().contains(e.getTipoIngreso().getNombre()))
                .filter(e -> cumpleFecha(e.getFechaIngreso(), f.desde(), f.hasta()))
                .collect(Collectors.toList());
        }

        private static boolean cumpleTexto(String campo, String filtro) {
            if (filtro.isEmpty()) return true;
            return campo != null && campo.toLowerCase().contains(filtro);
        }

        private static boolean cumpleFecha(LocalDateTime fecha, LocalDate desde, LocalDate hasta) {
            if (fecha == null) return desde == null && hasta == null;
            LocalDate dia = fecha.toLocalDate();
            if (desde != null && dia.isBefore(desde)) return false;
            if (hasta != null && dia.isAfter(hasta))  return false;
            return true;
        }
    }

    // ── siembra ──────────────────────────────────────────────────────────────

    private void sembrarElUniverso() {
        sembrarDetalles(1, EstadoEquipo.NUEVO,        LocalDateTime.of(2026, 1, 1, 9, 0));
        sembrarDetalles(3, EstadoEquipo.NUEVO,        LocalDateTime.of(2026, 1, 2, 9, 0));
        sembrarDetalles(3, EstadoEquipo.ESTERILIZADO, LocalDateTime.of(2026, 1, 3, 9, 0));
        sembrarRemito(4, LocalDateTime.of(2026, 1, 4, 9, 0));
        sembrarRemito(2, LocalDateTime.of(2026, 1, 5, 9, 0));
    }

    /**
     * Crea un DETALLES con un material en {@code estado} y deja la columna del agregado en el mismo
     * valor — que es lo que persistiría {@code recalcularEstadoEquipo}. La equivalencia entre la
     * columna y {@code calcularEstado()} la prueba {@link EstadoPersistidoEsElCalculadoTest}.
     */
    private int sembrarDetalles(int cliente, EstadoEquipo estado, LocalDateTime fecha) {
        EquipoOtros equipo = new EquipoOtros();
        equipo.setNroCliente(cliente);
        equipo.setTipoIngreso(TipoIngresoOtros.DETALLES);
        equipo.setEstado(estado);
        equipo.agregarMaterial(new MaterialOtros(null, "TestDescRopa", 2, estado));
        dao.guardar(equipo);
        fecharEn(equipo.getId(), fecha);
        return equipo.getId();
    }

    private int sembrarRemito(int cantidad, LocalDateTime fecha) {
        EquipoOtros equipo = new EquipoOtros();
        equipo.setNroCliente(1);
        equipo.setTipoIngreso(TipoIngresoOtros.REMITO);
        equipo.setRemitoCantidad(cantidad);
        dao.guardar(equipo);
        fecharEn(equipo.getId(), fecha);
        return equipo.getId();
    }

    private void sembrar(int cuantos) {
        for (int i = 0; i < cuantos; i++) {
            sembrarUno(1);
        }
    }

    private int sembrarUno(int cuantosMateriales) {
        EquipoOtros equipo = new EquipoOtros();
        equipo.setNroCliente(1);
        equipo.setTipoIngreso(TipoIngresoOtros.DETALLES);
        for (int i = 0; i < cuantosMateriales; i++) {
            equipo.agregarMaterial(new MaterialOtros("TestDescRopa " + i, 1));
        }
        dao.guardar(equipo);
        return equipo.getId();
    }

    private void fecharEn(int equipoId, LocalDateTime fecha) {
        ejecutar("UPDATE equipo_otros SET fecha_ingreso = '" + java.sql.Timestamp.valueOf(fecha)
            + "' WHERE id = " + equipoId);
    }

    private void desfechar(int equipoId) {
        ejecutar("UPDATE equipo_otros SET fecha_ingreso = NULL WHERE id = " + equipoId);
    }

    private void ejecutar(String sql) {
        try {
            ejecutarSQL(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo preparar el fixture: " + sql, e);
        }
    }

    private static List<Integer> ids(List<EquipoOtros> equipos) {
        return equipos.stream().map(EquipoOtros::getId).toList();
    }
}
