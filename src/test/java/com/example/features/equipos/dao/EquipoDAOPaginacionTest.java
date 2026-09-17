package com.example.features.equipos.dao;

import com.example.AbstractDAOTest;
import com.example.common.paginacion.CriteriosPagina;
import com.example.common.paginacion.Pagina;
import com.example.features.equipos.model.FiltroEquipos;
import com.example.features.equipos.ortopedias.dao.EquipoDAO;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.model.Material;
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
 * La paginación en SQL de la grilla de ortopedias de Ver Equipos.
 *
 * <h2>El test que importa es el de equivalencia</h2>
 * Los tests de "la página trae 50 filas" son fáciles y no prueban nada interesante: una traducción
 * a SQL que cambió la semántica de un filtro los pasa todos. Lo que prueba que la traducción es
 * correcta es {@link Equivalencia}, que corre cada filtro por los dos caminos —el SQL nuevo y el
 * predicado en memoria que la pantalla usa hoy— sobre el mismo conjunto, y exige el mismo
 * resultado.
 *
 * <p>El predicado en memoria vive en {@link FiltradoDeReferencia}, transcripto de
 * {@code VerEquiposController.aplicarFiltros}. Es una transcripción y no una llamada porque ese
 * método es privado y está adentro de una clase de Swing; el Paso 11 del plan lo borra, y esta
 * copia queda como lo único que documenta qué hacía.
 */
class EquipoDAOPaginacionTest extends AbstractDAOTest {

    private final EquipoDAO dao = new EquipoDAO();

    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM material_movimientos");
        ejecutarSQL("DELETE FROM equipo_materiales");
        ejecutarSQL("DELETE FROM equipos");
    }

    // ── bordes de página ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("bordes de página")
    class Bordes {

        @Test
        @DisplayName("la página 1 trae el tamaño pedido y el total es el de la tabla entera")
        void primeraPaginaCompleta() {
            sembrar(51);

            Pagina<Equipo> pagina = dao.obtenerPagina(FiltroEquipos.sinFiltros(), new CriteriosPagina(1, 50));

            assertEquals(50, pagina.contenido().size());
            assertEquals(51, pagina.totalFilas());
            assertEquals(2, pagina.totalPaginas());
        }

        @Test
        @DisplayName("la última página viene incompleta, y eso no es un error")
        void ultimaPaginaIncompleta() {
            sembrar(51);

            Pagina<Equipo> pagina = dao.obtenerPagina(FiltroEquipos.sinFiltros(), new CriteriosPagina(2, 50));

            assertEquals(1, pagina.contenido().size());
            assertEquals(51, pagina.totalFilas());
            assertTrue(pagina.esUltima());
        }

        /**
         * El operador puede tener abierta la página 7 cuando otro borra filas o cuando el filtro
         * pasa a tener dos. Lanzar ahí convertiría una situación normal en un cartel de error.
         */
        @Test
        @DisplayName("una página más allá del total viene vacía y no lanza")
        void paginaFueraDeRango_vaciaYConTotalCorrecto() {
            sembrar(5);

            Pagina<Equipo> pagina = dao.obtenerPagina(FiltroEquipos.sinFiltros(), new CriteriosPagina(9, 50));

            assertTrue(pagina.estaVacia());
            assertEquals(5, pagina.totalFilas());
        }

        @Test
        @DisplayName("un filtro que no matchea nada devuelve página vacía con total 0")
        void sinResultados() {
            sembrar(5);

            FiltroEquipos filtro = new FiltroEquipos(
                List.of(), "no existe ningún cliente así", null, null, null, List.of(), null, null);
            Pagina<Equipo> pagina = dao.obtenerPagina(filtro, CriteriosPagina.primera());

            assertTrue(pagina.estaVacia());
            assertEquals(0, pagina.totalFilas());
            assertEquals(1, pagina.totalPaginas(), "sin resultados hay una página, no cero");
        }

        /**
         * <b>El test del plegado.</b> {@code SQL_EQUIPOS_CON_MATERIALES} devuelve una fila por
         * (equipo × material): si la paginación se hiciera con un {@code LIMIT} sobre esa consulta,
         * el {@code LIMIT 50} cortaría <b>materiales</b> y el equipo del borde llegaría partido —
         * con parte de sus 12 materiales en la página siguiente, o directamente empujando a los
         * otros 49 equipos fuera de la página.
         */
        @Test
        @DisplayName("un equipo de 12 materiales en el borde sale UNA vez y con sus 12 materiales")
        void equipoConMuchosMateriales_enElBordeDePagina() {
            // fechas descendentes: el primero sembrado es el más nuevo y encabeza la página 1
            for (int i = 0; i < 51; i++) {
                int materiales = (i == 49) ? 12 : 1;   // el 50.º, o sea el último de la página 1
                int id = sembrarUno(materiales);
                fecharEn(id, LocalDateTime.of(2026, 1, 1, 12, 0).minusDays(i));
            }

            Pagina<Equipo> pagina1 = dao.obtenerPagina(FiltroEquipos.sinFiltros(), new CriteriosPagina(1, 50));
            Pagina<Equipo> pagina2 = dao.obtenerPagina(FiltroEquipos.sinFiltros(), new CriteriosPagina(2, 50));

            assertEquals(50, pagina1.contenido().size(), "el LIMIT tiene que contar equipos, no materiales");
            Equipo enElBorde = pagina1.contenido().get(49);
            assertEquals(12, enElBorde.getMateriales().size(),
                "el equipo del borde tiene que llegar completo");
            assertEquals(1, pagina2.contenido().size());
            assertFalse(ids(pagina2.contenido()).contains(enElBorde.getId()),
                "y no puede aparecer también en la página siguiente");
        }

        /**
         * Sin un orden <b>total</b>, dos filas empatadas pueden alternar entre consultas y una sale
         * en dos páginas mientras la otra no sale en ninguna. {@code fecha_ingreso} tiene
         * resolución de segundo y una carga por lote crea varios equipos dentro del mismo, así que
         * el empate no es teórico.
         */
        @Test
        @DisplayName("con fechas repetidas, recorrer las páginas devuelve cada equipo exactamente una vez")
        void ordenTotal_sinRepetidosNiFaltantes() {
            for (int i = 0; i < 25; i++) {
                fecharEn(sembrarUno(1), LocalDateTime.of(2026, 3, 10, 9, 0));   // todas iguales
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

    // ── conteo ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("contar y obtenerPagina hablan del mismo conjunto")
    void conteoYPaginaCoinciden() {
        sembrarConEstados(EstadoEquipo.NUEVO, EstadoEquipo.NUEVO, EstadoEquipo.ESTERILIZADO);

        FiltroEquipos filtro = new FiltroEquipos(
            List.of(EstadoEquipo.NUEVO.getNombre()), null, null, null, null, List.of(), null, null);

        assertEquals(dao.contar(filtro),
            dao.obtenerPagina(filtro, CriteriosPagina.primera()).totalFilas());
        assertEquals(2, dao.contar(filtro));
    }

    /**
     * Si el conteo y la página usaran {@code WHERE} distintos, la UI diría "127 resultados" y
     * mostraría otra cosa. Nadie lo nota hasta que un operador cuenta a mano.
     */
    @Test
    @DisplayName("el total sobrevive a recorrer las páginas: sale del mismo WHERE que el contenido")
    void totalCoincideConLoRecorrido() {
        sembrar(7);
        FiltroEquipos filtro = FiltroEquipos.sinFiltros();

        long total = dao.contar(filtro);
        int recorridas = 0;
        for (int pagina = 1; pagina <= 4; pagina++) {
            recorridas += dao.obtenerPagina(filtro, new CriteriosPagina(pagina, 2), total)
                .contenido().size();
        }

        assertEquals(total, recorridas);
    }

    // ── equivalencia con el filtrado en memoria de hoy ───────────────────────

    @Nested
    @DisplayName("equivalencia con el filtrado en memoria de Ver Equipos")
    class Equivalencia {

        @Test
        @DisplayName("estados")
        void porEstado() {
            sembrarElUniverso();
            exigirEquivalencia(new FiltroEquipos(
                List.of(EstadoEquipo.NUEVO.getNombre(), EstadoEquipo.ESTERILIZADO.getNombre()),
                null, null, null, null, List.of(), null, null));
        }

        @Test
        @DisplayName("cliente")
        void porCliente() {
            sembrarElUniverso();
            exigirEquivalencia(new FiltroEquipos(
                List.of(), "clinica", null, null, null, List.of(), null, null));
        }

        @Test
        @DisplayName("profesional")
        void porProfesional() {
            sembrarElUniverso();
            exigirEquivalencia(new FiltroEquipos(
                List.of(), null, "aznar", null, null, List.of(), null, null));
        }

        @Test
        @DisplayName("paciente")
        void porPaciente() {
            sembrarElUniverso();
            exigirEquivalencia(new FiltroEquipos(
                List.of(), null, null, "pére", null, List.of(), null, null));
        }

        @Test
        @DisplayName("institución")
        void porInstitucion() {
            sembrarElUniverso();
            exigirEquivalencia(new FiltroEquipos(
                List.of(), null, null, null, "clinica", List.of(), null, null));
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
        @DisplayName("los seis filtros combinados")
        void combinados() {
            sembrarElUniverso();
            exigirEquivalencia(new FiltroEquipos(
                List.of(EstadoEquipo.NUEVO.getNombre()), "clinica", null, null, null, List.of(),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5)));
        }

        /**
         * {@code hasta} es inclusivo por día y la columna es un {@code TIMESTAMP}: un equipo de las
         * 23:59 de ese mismo día tiene que entrar. Con {@code <= hasta} se perdería, y el operador
         * vería desaparecer justo lo que acaba de cargar.
         */
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
         * {@code equipos.fecha_ingreso} es nullable (V1: {@code TIMESTAMP DEFAULT CURRENT_TIMESTAMP},
         * sin {@code NOT NULL}). {@code VerEquiposController.cumpleFecha} deja pasar un equipo sin
         * fecha <b>sólo</b> si los dos extremos son nulos; en SQL, {@code NULL >= ?} es desconocido
         * y no pasa, y sin extremos no hay cláusula. La rama existe traducida, no borrada — y sin
         * este test nadie se enteraría de la diferencia.
         */
        @Test
        @DisplayName("un equipo sin fecha entra sin filtro de fechas y sale con cualquiera de los dos extremos")
        void fechaNula() {
            int conFecha = sembrarUno(1);
            fecharEn(conFecha, LocalDateTime.of(2026, 1, 15, 10, 0));
            int sinFecha = sembrarUno(1);
            desfechar(sinFecha);

            FiltroEquipos sinExtremos = FiltroEquipos.sinFiltros();
            assertTrue(ids(dao.obtenerPagina(sinExtremos, CriteriosPagina.primera()).contenido())
                .contains(sinFecha), "sin filtro de fechas, el equipo sin fecha entra");
            exigirEquivalencia(sinExtremos);

            FiltroEquipos conDesde = new FiltroEquipos(List.of(), null, null, null, null, List.of(),
                LocalDate.of(2020, 1, 1), null);
            assertFalse(ids(dao.obtenerPagina(conDesde, CriteriosPagina.primera()).contenido())
                .contains(sinFecha), "con un extremo puesto, el equipo sin fecha sale");
            exigirEquivalencia(conDesde);

            exigirEquivalencia(new FiltroEquipos(List.of(), null, null, null, null, List.of(),
                null, LocalDate.of(2030, 1, 1)));
        }

        /**
         * Compara el conjunto que devuelve el SQL nuevo contra el que devuelve el predicado en
         * memoria de hoy, sobre el mismo universo. Compara <b>conjuntos</b> y no listas: lo que
         * este test cuida es <em>qué</em> entra, no en qué orden — el orden lo cuidan los tests de
         * arriba.
         */
        private void exigirEquivalencia(FiltroEquipos filtro) {
            List<Equipo> universo = dao.obtenerTodos();
            Set<Integer> enMemoria = Set.copyOf(ids(FiltradoDeReferencia.filtrar(universo, filtro)));

            // Página grande a propósito: lo que se compara es el conjunto entero, no una página.
            Pagina<Equipo> pagina = dao.obtenerPagina(filtro, new CriteriosPagina(1, 1000));

            assertEquals(enMemoria, Set.copyOf(ids(pagina.contenido())),
                "el filtro en SQL y el filtro en memoria tienen que dar el mismo conjunto");
            assertEquals(enMemoria.size(), pagina.totalFilas(),
                "y el total tiene que coincidir con ese conjunto");
        }
    }

    /**
     * El filtrado de {@code VerEquiposController.aplicarFiltros}, transcripto tal cual para la
     * grilla de ortopedias. Es la implementación de referencia contra la que se compara la
     * traducción a SQL.
     *
     * <p><b>Transcripto, no llamado:</b> el original es privado y vive dentro de una clase de
     * Swing. El Paso 11 del plan lo borra al migrar la pantalla a páginas, y a partir de ahí esta
     * copia es lo único que documenta qué hacía — que es exactamente lo que hace falta para que
     * borrarlo sea seguro.
     */
    private static final class FiltradoDeReferencia {

        static List<Equipo> filtrar(List<Equipo> todos, FiltroEquipos f) {
            String cliente     = normalizar(f.cliente());
            String profesional = normalizar(f.profesional());
            String paciente    = normalizar(f.paciente());
            String institucion = normalizar(f.institucion());

            return todos.stream()
                .filter(e -> cumpleEstado(e.getEstado(), f.estados()))
                .filter(e -> cumpleTexto(e.getClienteNombre(), cliente))
                .filter(e -> cumpleTexto(e.getProfesionalNombre(), profesional))
                .filter(e -> cumpleTexto(e.getPacienteNombre(), paciente))
                .filter(e -> cumpleTexto(e.getInstitucionNombre(), institucion))
                .filter(e -> cumpleFecha(e.getFechaIngreso(), f.desde(), f.hasta()))
                .collect(Collectors.toList());
        }

        private static String normalizar(String texto) {
            return texto == null ? "" : texto.trim().toLowerCase();
        }

        private static boolean cumpleEstado(EstadoEquipo estado, List<String> seleccionados) {
            return seleccionados.isEmpty() || seleccionados.contains(estado.getNombre());
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

    /** Un universo chico pero variado: distintos clientes, profesionales, pacientes y fechas. */
    private void sembrarElUniverso() {
        crear(1, 1, 1, "Pérez Juan",  EstadoEquipo.NUEVO,        LocalDateTime.of(2026, 1, 1, 9, 0));
        crear(3, 2, 2, "Gómez Ana",   EstadoEquipo.NUEVO,        LocalDateTime.of(2026, 1, 2, 9, 0));
        crear(3, 2, 2, "Pérez Luis",  EstadoEquipo.ESTERILIZADO, LocalDateTime.of(2026, 1, 3, 9, 0));
        crear(4, 3, 3, "López Mario", EstadoEquipo.ENTREGADO,    LocalDateTime.of(2026, 1, 4, 9, 0));
        crear(1, 1, 1, "Díaz Sol",    EstadoEquipo.LAVANDO,      LocalDateTime.of(2026, 1, 5, 9, 0));
    }

    /**
     * Crea un equipo con un material en {@code estado} y fija la columna {@code estado} del
     * agregado al mismo valor, que es lo que persistiría {@code recalcularEstadoEquipo} — la
     * equivalencia entre la columna y {@code calcularEstado()} la prueba
     * {@link EstadoPersistidoEsElCalculadoTest}, no este test.
     */
    private void crear(int cliente, Integer profesional, int institucion, String paciente,
                       EstadoEquipo estado, LocalDateTime fecha) {
        Equipo equipo = new Equipo();
        equipo.setNroCliente(cliente);
        equipo.setNroProfesional(profesional);
        equipo.setNroInstitucion(institucion);
        equipo.setPacienteNombre(paciente);
        equipo.setEstado(estado);
        equipo.agregarMaterial(new Material(400, "Tornillera", 1, estado));
        dao.guardarEquipo(equipo);
        fecharEn(equipo.getId(), fecha);
    }

    private void sembrar(int cuantos) {
        for (int i = 0; i < cuantos; i++) {
            sembrarUno(1);
        }
    }

    private void sembrarConEstados(EstadoEquipo... estados) {
        for (EstadoEquipo estado : estados) {
            crear(1, 1, 1, "Paciente", estado, LocalDateTime.of(2026, 1, 1, 9, 0));
        }
    }

    private int sembrarUno(int cuantosMateriales) {
        Equipo equipo = new Equipo();
        equipo.setNroCliente(1);
        equipo.setNroProfesional(1);
        equipo.setNroInstitucion(1);
        equipo.setPacienteNombre("Paciente");
        equipo.setEstado(EstadoEquipo.NUEVO);
        for (int i = 0; i < cuantosMateriales; i++) {
            equipo.agregarMaterial(new Material(400 + i, "Tornillera", 1));
        }
        dao.guardarEquipo(equipo);
        return equipo.getId();
    }

    private void fecharEn(int equipoId, LocalDateTime fecha) {
        ejecutar("UPDATE equipos SET fecha_ingreso = '" + java.sql.Timestamp.valueOf(fecha)
            + "' WHERE id = " + equipoId);
    }

    private void desfechar(int equipoId) {
        ejecutar("UPDATE equipos SET fecha_ingreso = NULL WHERE id = " + equipoId);
    }

    private void ejecutar(String sql) {
        try {
            ejecutarSQL(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo preparar el fixture: " + sql, e);
        }
    }

    private static List<Integer> ids(List<Equipo> equipos) {
        return equipos.stream().map(Equipo::getId).toList();
    }
}
