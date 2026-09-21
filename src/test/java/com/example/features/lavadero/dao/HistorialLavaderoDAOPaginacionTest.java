package com.example.features.lavadero.dao;

import com.example.AbstractDAOTest;
import com.example.common.paginacion.CriteriosPagina;
import com.example.common.paginacion.Pagina;
import com.example.features.lavadero.model.FiltroHistorial;
import com.example.features.lavadero.model.IngresoHistorial;
import com.example.infrastructure.db.ConnectionPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * La paginación en SQL del historial de lavadero.
 *
 * <p><b>El test que sostiene el paso entero es el de equivalencia:</b> para cada filtro, el
 * conjunto que devuelven las páginas tiene que ser <em>exactamente</em> el que
 * {@link FiltradoEnMemoria} —el filtrado en memoria que esta traducción reemplaza, transcripto al
 * pie de esta clase— saca del listado completo. Un filtro que se quedara en memoria, o una
 * traducción que corriera un borde de fecha, pasaría cualquier test de "la página trae 50 filas" y
 * sólo se vería acá.</p>
 *
 * <p>Va en una clase aparte de {@code HistorialLavaderoDAOTest} a propósito: aquellos tests son
 * la red de seguridad de {@code obtenerHistorial()} y tienen que seguir pasando sin tocarse.</p>
 */
class HistorialLavaderoDAOPaginacionTest extends AbstractDAOTest {

    private static final String PREFIJO = "TestPag";

    private HistorialLavaderoDAO dao;

    /** Los cinco ingresos "con forma": cada uno cubre un filtro distinto. */
    private int iFinalizado;   // cliente con '%' en el nombre, 2025-04-01, lavarropas 7
    private int iLavado;       // 2025-03-15, lavarropas 5
    private int iClasificado;  // 2025-02-20 23:59 — el borde de "hasta"
    private int iPendiente;    // 2025-01-10, 2 bolsas, sin clasificar
    private int iSinFecha;     // fecha_ingreso NULL

    private String elementoBatas;
    private String elementoToallon;
    private String elementoSabana;

    @BeforeEach
    void setUp() throws SQLException {
        dao = new HistorialLavaderoDAO();

        int acme    = insertarCliente(PREFIJO + "Acme");
        int beta    = insertarCliente(PREFIJO + "Beta");
        int poronto = insertarCliente(PREFIJO + " 50% Off");

        elementoBatas   = "Batas";
        elementoToallon = "Toallon";
        elementoSabana  = "Sabana grande";

        iFinalizado  = insertarIngreso(poronto, "'2025-04-01 12:00:00'", "FINALIZADO");
        iLavado      = insertarIngreso(beta,    "'2025-03-15 10:00:00'", "LAVADO");
        iClasificado = insertarIngreso(acme,    "'2025-02-20 23:59:00'", "CLASIFICADO");
        iPendiente   = insertarIngreso(acme,    "'2025-01-10 08:00:00'", "PENDIENTE");
        iSinFecha    = insertarIngreso(beta,    "NULL",                  "PENDIENTE");

        insertarBolsa(iPendiente);
        insertarBolsa(iPendiente);

        int clasifSabana  = clasificar(iFinalizado,  elementoSabana, 2);
        int clasifToallon = clasificar(iLavado,      elementoToallon, 3);
        clasificar(iClasificado, elementoBatas, 5);

        lavar(clasifSabana,  7, 2);
        lavar(clasifToallon, 5, 3);

        // Relleno para que haya varias páginas sin depender de los cinco de arriba.
        for (int dia = 1; dia <= 10; dia++) {
            insertarIngreso(acme, "'2024-06-" + String.format("%02d", dia) + " 09:00:00'", "CLASIFICADO");
        }
    }

    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM insumos_ciclo_lavadero");
        ejecutarSQL("DELETE FROM elementos_ciclo_lavadero");
        ejecutarSQL("DELETE FROM ciclos_lavadero");
        ejecutarSQL("DELETE FROM elementos_clasificacion_lavadero");
        ejecutarSQL("DELETE FROM bolsas_lavadero");
        ejecutarSQL("DELETE FROM ingresos_lavadero");
        ejecutarSQL("DELETE FROM clientes WHERE nombre LIKE '" + PREFIJO + "%'");
    }

    // ── paginación ───────────────────────────────────────────────────────────

    @Test
    void primeraPagina_traeElTamanioPedidoYElTotalCompleto() {
        Pagina<IngresoHistorial> pagina = dao.obtenerPagina(FiltroHistorial.sinFiltros(), criterios(1, 4));

        assertEquals(4, pagina.contenido().size());
        assertEquals(15, pagina.totalFilas());
        assertEquals(4, pagina.totalPaginas());
        assertEquals(1, pagina.numeroPagina());
    }

    @Test
    void ultimaPagina_traeElResto() {
        Pagina<IngresoHistorial> pagina = dao.obtenerPagina(FiltroHistorial.sinFiltros(), criterios(4, 4));

        assertEquals(3, pagina.contenido().size(), "15 ingresos de a 4 dejan 3 en la última página");
        assertEquals(15, pagina.totalFilas());
    }

    @Test
    void lasPaginasSucesivas_cubrenElListadoCompletoEnElMismoOrden() {
        List<IngresoHistorial> completo = dao.obtenerHistorial();

        assertEquals(completo, todasLasPaginas(FiltroHistorial.sinFiltros(), 4));
    }

    @Test
    void paginaMasAllaDelTotal_daPaginaVaciaConElTotalCorrectoYNoLanza() {
        Pagina<IngresoHistorial> pagina = dao.obtenerPagina(FiltroHistorial.sinFiltros(), criterios(99, 4));

        assertTrue(pagina.estaVacia());
        assertEquals(15, pagina.totalFilas());
    }

    @Test
    void filtroQueNoMatcheaNada_daPaginaVaciaConTotalCeroYNoLanza() {
        FiltroHistorial filtro = filtro(PREFIJO + "NoExiste", List.of(), null, null, null, null);

        Pagina<IngresoHistorial> pagina = dao.obtenerPagina(filtro, criterios(1, 4));

        assertTrue(pagina.estaVacia());
        assertEquals(0, pagina.totalFilas());
        assertEquals(1, pagina.totalPaginas(), "sin resultados hay una página, vacía");
    }

    /**
     * El verdadero ahorro del paso: los tres agregados se acotan a los ids de la página. Si
     * quedaran sin acotar, este test igual pasaría por el cruce en memoria — lo que verifica es
     * que acotarlos no perdió ningún agregado que sí corresponde.
     */
    @Test
    void losAgregadosDeUnaPagina_sonLosDeSusIngresosYNoLosDeOtraPagina() {
        Pagina<IngresoHistorial> segunda = dao.obtenerPagina(FiltroHistorial.sinFiltros(), criterios(2, 2));

        List<Integer> ids = segunda.contenido().stream().map(IngresoHistorial::id).toList();
        assertEquals(List.of(iClasificado, iPendiente), ids, "orden: fecha DESC, id DESC");

        IngresoHistorial clasificado = segunda.contenido().get(0);
        assertEquals(Set.of(elementoBatas), clasificado.elementos());
        assertTrue(clasificado.lavarropas().isEmpty());
        assertEquals(0, clasificado.cantBolsas());

        IngresoHistorial pendiente = segunda.contenido().get(1);
        assertEquals(2, pendiente.cantBolsas());
        assertTrue(pendiente.elementos().isEmpty());
    }

    @Test
    void primeraPagina_traeLosAgregadosDeSusPropiosIngresos() {
        Pagina<IngresoHistorial> primera = dao.obtenerPagina(FiltroHistorial.sinFiltros(), criterios(1, 2));

        IngresoHistorial finalizado = primera.contenido().get(0);
        assertEquals(iFinalizado, finalizado.id());
        assertEquals(Set.of(elementoSabana), finalizado.elementos());
        assertEquals(Set.of(7), finalizado.lavarropas());

        IngresoHistorial lavado = primera.contenido().get(1);
        assertEquals(Set.of(5), lavado.lavarropas());
    }

    // ── conteo ───────────────────────────────────────────────────────────────

    @Test
    void contarHistorial_coincideConElTotalDeLaPaginaParaElMismoFiltro() {
        List<FiltroHistorial> filtros = List.of(
            FiltroHistorial.sinFiltros(),
            filtro("acme", List.of(), null, null, null, null),
            filtro(null, List.of("PENDIENTE", "LAVADO"), null, null, null, null),
            filtro(null, List.of(), LocalDate.of(2025, 1, 1), LocalDate.of(2025, 3, 15), null, null),
            filtro(null, List.of(), null, null, "toall", null),
            filtro(null, List.of(), null, null, null, 7));

        for (FiltroHistorial f : filtros) {
            assertEquals(dao.obtenerPagina(f, criterios(1, 4)).totalFilas(), dao.contarHistorial(f),
                "el conteo y la página tienen que salir del mismo WHERE — filtro: " + f);
        }
    }

    @Test
    void obtenerPaginaConTotalConocido_respetaEseTotalYNoVuelveAContar() {
        Pagina<IngresoHistorial> pagina =
            dao.obtenerPagina(FiltroHistorial.sinFiltros(), criterios(1, 4), 99L);

        assertEquals(99L, pagina.totalFilas());
        assertEquals(4, pagina.contenido().size(), "el contenido sale de la base igual que siempre");
    }

    @Test
    void obtenerPaginaConTotalNegativo_lanza() {
        assertThrows(IllegalArgumentException.class,
            () -> dao.obtenerPagina(FiltroHistorial.sinFiltros(), criterios(1, 4), -1L));
    }

    // ── equivalencia con el filtrado en memoria ──────────────────────────────

    @Test
    void filtroDeCliente_daElMismoConjuntoQueElFiltradoEnMemoria() {
        assertEquivalente(filtro("acme", List.of(), null, null, null, null));
        assertEquivalente(filtro("BETA", List.of(), null, null, null, null));
        assertEquivalente(filtro("   ",  List.of(), null, null, null, null));
    }

    /**
     * El {@code %} del nombre del cliente se busca como carácter, no como comodín: el filtrado en
     * memoria usa {@code contains}, que no conoce comodines. Sin escapar el patrón, "50%" traería
     * también cualquier cliente que empiece con "50".
     */
    @Test
    void filtroDeClienteConComodinDeSql_loBuscaLiteral() {
        assertEquivalente(filtro("50%", List.of(), null, null, null, null));
        assertEquivalente(filtro("50% o", List.of(), null, null, null, null));

        assertEquals(1, dao.contarHistorial(filtro("50%", List.of(), null, null, null, null)));
        assertEquals(0, dao.contarHistorial(filtro("50%z", List.of(), null, null, null, null)));
    }

    @Test
    void filtroDeEstados_daElMismoConjuntoQueElFiltradoEnMemoria() {
        assertEquivalente(filtro(null, List.of("PENDIENTE"), null, null, null, null));
        assertEquivalente(filtro(null, List.of("PENDIENTE", "CLASIFICADO", "LAVADO"), null, null, null, null));
        assertEquivalente(filtro(null, List.of("FINALIZADO"), null, null, null, null));
        assertEquivalente(filtro(null, List.of(), null, null, null, null));
    }

    @Test
    void filtroDeFechas_daElMismoConjuntoQueElFiltradoEnMemoria() {
        assertEquivalente(filtro(null, List.of(), LocalDate.of(2025, 1, 1), null, null, null));
        assertEquivalente(filtro(null, List.of(), null, LocalDate.of(2025, 2, 20), null, null));
        assertEquivalente(filtro(null, List.of(), LocalDate.of(2025, 1, 10), LocalDate.of(2025, 3, 15), null, null));
        assertEquivalente(filtro(null, List.of(), LocalDate.of(2030, 1, 1), null, null, null));
    }

    /**
     * {@code hasta} es inclusivo <b>por día</b>: el ingreso de las 23:59 de ese mismo día entra.
     * En SQL eso es {@code < hasta + 1 día}; con {@code <= hasta} se perderían todos los ingresos
     * posteriores a la medianoche del último día, que son casi todos.
     */
    @Test
    void hastaEsInclusivoPorDia_elIngresoDeLas2359DeEseDiaEntra() {
        FiltroHistorial filtro = filtro(null, List.of(), null, LocalDate.of(2025, 2, 20), null, null);

        List<Integer> ids = todasLasPaginas(filtro, 4).stream().map(IngresoHistorial::id).toList();

        assertTrue(ids.contains(iClasificado), "el ingreso de las 23:59 del día 'hasta' tiene que entrar");
        assertFalse(ids.contains(iLavado), "el del día siguiente no");
        assertEquivalente(filtro);
    }

    /**
     * {@code ingresos_lavadero.fecha_ingreso} es nullable, así que la rama del filtrado en memoria
     * "un ingreso sin fecha pasa sólo si los dos extremos son nulos" hay que traducirla, no
     * borrarla. En SQL sale de que {@code NULL >= ?} es desconocido y no pasa.
     */
    @Test
    void ingresoSinFecha_pasaSoloSiLosDosExtremosSonNulos() {
        assertTrue(idsDe(FiltroHistorial.sinFiltros()).contains(iSinFecha));
        assertFalse(idsDe(filtro(null, List.of(), LocalDate.of(2000, 1, 1), null, null, null)).contains(iSinFecha));
        assertFalse(idsDe(filtro(null, List.of(), null, LocalDate.of(2030, 1, 1), null, null)).contains(iSinFecha));

        assertEquivalente(filtro(null, List.of(), LocalDate.of(2000, 1, 1), null, null, null));
        assertEquivalente(filtro(null, List.of(), null, LocalDate.of(2030, 1, 1), null, null));
    }

    @Test
    void filtroDeElemento_daElMismoConjuntoQueElFiltradoEnMemoria() {
        assertEquivalente(filtro(null, List.of(), null, null, "toall", null));
        assertEquivalente(filtro(null, List.of(), null, null, "SABANA", null));
        assertEquivalente(filtro(null, List.of(), null, null, "no existe este elemento", null));
    }

    @Test
    void filtroDeLavarropas_daElMismoConjuntoQueElFiltradoEnMemoria() {
        assertEquivalente(filtro(null, List.of(), null, null, null, 5));
        assertEquivalente(filtro(null, List.of(), null, null, null, 7));
        assertEquivalente(filtro(null, List.of(), null, null, null, 13));
    }

    @Test
    void losCincoFiltrosCombinados_danElMismoConjuntoQueElFiltradoEnMemoria() {
        assertEquivalente(filtro("beta", List.of("LAVADO"),
            LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), "toall", 5));
        assertEquivalente(filtro("acme", List.of("CLASIFICADO"),
            LocalDate.of(2024, 1, 1), LocalDate.of(2025, 12, 31), "batas", null));
        assertEquivalente(filtro("acme", List.of("CLASIFICADO"),
            LocalDate.of(2024, 1, 1), LocalDate.of(2025, 12, 31), "batas", 5));
        assertEquivalente(filtro(null, List.of("PENDIENTE", "LAVADO"),
            LocalDate.of(2025, 1, 10), LocalDate.of(2025, 3, 15), null, null));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * El filtro en SQL devuelve exactamente lo mismo que {@link FiltradoEnMemoria} sobre el
     * listado completo, en el mismo orden. Se recorren todas las páginas con un tamaño chico para
     * que la comparación también cubra los cortes entre páginas.
     */
    private void assertEquivalente(FiltroHistorial filtro) {
        List<IngresoHistorial> enMemoria = FiltradoEnMemoria.filtrar(dao.obtenerHistorial(), filtro);

        assertEquals(enMemoria, todasLasPaginas(filtro, 3), "filtro: " + filtro);
        assertEquals(enMemoria.size(), dao.contarHistorial(filtro), "conteo del filtro: " + filtro);
    }

    /**
     * El filtrado en memoria que la traducción a SQL reemplazó, transcripto tal cual de
     * {@code HistorialFilterStrategy} —la clase que el Paso 9 borró al quedarse sin uso en
     * producción.
     *
     * <p><b>Vive acá a propósito.</b> Es la semántica que aquella clase documentaba, y es contra
     * la que se compara el {@code WHERE} en SQL. Guardada sólo en el historial de git no
     * verificaría nada; acá, cualquier cambio del filtro en SQL que se aparte de estas reglas hace
     * fallar la comparación. Si alguna regla tiene que cambiar, se cambia en los dos lados y el
     * test lo obliga.</p>
     *
     * <ul>
     *   <li>Cliente: vacío → pasa; si no, {@code contains} insensible a mayúsculas sobre el nombre.</li>
     *   <li>Estados: lista vacía → pasa; si no, {@code estado.name()} tiene que estar en la lista
     *       (insensible a mayúsculas).</li>
     *   <li>Fechas: {@code fechaIngreso.toLocalDate()} dentro de {@code [desde, hasta]}; extremo
     *       {@code null} = abierto; {@code fechaIngreso == null} sólo pasa si ambos extremos son
     *       {@code null}.</li>
     *   <li>Elemento: vacío → pasa; si no, algún nombre de {@code elementos()} contiene el texto
     *       (insensible a mayúsculas).</li>
     *   <li>Lavarropas: {@code null} → pasa; si no, {@code lavarropas().contains(n)}.</li>
     * </ul>
     */
    private static final class FiltradoEnMemoria {

        static List<IngresoHistorial> filtrar(List<IngresoHistorial> fuente, FiltroHistorial f) {
            return fuente.stream()
                .filter(i -> cumpleCliente(i, f.cliente()))
                .filter(i -> cumpleEstado(i, f.estados()))
                .filter(i -> cumpleFechas(i, f.desde(), f.hasta()))
                .filter(i -> cumpleElemento(i, f.elemento()))
                .filter(i -> cumpleLavarropas(i, f.lavarropas()))
                .toList();
        }

        private static boolean cumpleCliente(IngresoHistorial ingreso, String filtro) {
            if (filtro == null || filtro.isBlank()) return true;
            String nombre = ingreso.clienteNombre();
            return nombre != null && nombre.toLowerCase().contains(filtro.toLowerCase());
        }

        private static boolean cumpleEstado(IngresoHistorial ingreso, List<String> estados) {
            return estados.isEmpty()
                || estados.stream().anyMatch(e -> e.equalsIgnoreCase(ingreso.estado().name()));
        }

        private static boolean cumpleFechas(IngresoHistorial ingreso, LocalDate desde, LocalDate hasta) {
            if (ingreso.fechaIngreso() == null) return desde == null && hasta == null;
            LocalDate dia = ingreso.fechaIngreso().toLocalDate();
            if (desde != null && dia.isBefore(desde)) return false;
            if (hasta != null && dia.isAfter(hasta))  return false;
            return true;
        }

        private static boolean cumpleElemento(IngresoHistorial ingreso, String filtro) {
            if (filtro == null || filtro.isBlank()) return true;
            String texto = filtro.toLowerCase();
            return ingreso.elementos().stream()
                .anyMatch(e -> e != null && e.toLowerCase().contains(texto));
        }

        private static boolean cumpleLavarropas(IngresoHistorial ingreso, Integer numero) {
            return numero == null || ingreso.lavarropas().contains(numero);
        }
    }

    private List<IngresoHistorial> todasLasPaginas(FiltroHistorial filtro, int tamanio) {
        List<IngresoHistorial> todo = new ArrayList<>();
        Pagina<IngresoHistorial> pagina = dao.obtenerPagina(filtro, criterios(1, tamanio));
        todo.addAll(pagina.contenido());
        for (int n = 2; n <= pagina.totalPaginas(); n++) {
            todo.addAll(dao.obtenerPagina(filtro, criterios(n, tamanio), pagina.totalFilas()).contenido());
        }
        return todo;
    }

    private List<Integer> idsDe(FiltroHistorial filtro) {
        return todasLasPaginas(filtro, 4).stream().map(IngresoHistorial::id).toList();
    }

    private static CriteriosPagina criterios(int numero, int tamanio) {
        return new CriteriosPagina(numero, tamanio);
    }

    private static FiltroHistorial filtro(String cliente, List<String> estados, LocalDate desde,
                                          LocalDate hasta, String elemento, Integer lavarropas) {
        return new FiltroHistorial(cliente, estados, desde, hasta, elemento, lavarropas);
    }

    // ── siembra ──────────────────────────────────────────────────────────────

    private int insertarCliente(String nombre) throws SQLException {
        ejecutarSQL("INSERT INTO clientes (nombre) VALUES ('" + nombre.replace("'", "''") + "')");
        return lastInsertId();
    }

    /** @param fecha literal SQL ya citado, o {@code NULL} para probar la columna nullable. */
    private int insertarIngreso(int clienteId, String fecha, String estado) throws SQLException {
        ejecutarSQL("INSERT INTO ingresos_lavadero (cliente_id, fecha_ingreso, peso_total_kg, estado) "
            + "VALUES (" + clienteId + ", " + fecha + ", 10.00, '" + estado + "')");
        return lastInsertId();
    }

    private void insertarBolsa(int ingresoId) throws SQLException {
        ejecutarSQL("INSERT INTO bolsas_lavadero (ingreso_id, peso_kg) VALUES (" + ingresoId + ", 3.00)");
    }

    private int clasificar(int ingresoId, String nombreElemento, int cantidad) throws SQLException {
        int elementoId = escalar("SELECT id FROM catalogo_elementos_lavadero WHERE nombre = '"
            + nombreElemento + "'");
        ejecutarSQL("INSERT INTO elementos_clasificacion_lavadero (ingreso_id, elemento_id, cantidad) "
            + "VALUES (" + ingresoId + ", " + elementoId + ", " + cantidad + ")");
        return lastInsertId();
    }

    /** Un ciclo finalizado en ese lavarropas con esa línea adentro. */
    private void lavar(int clasificacionId, int lavarropas, int cantidad) throws SQLException {
        int jabonId = escalar("SELECT id FROM catalogo_jabones ORDER BY id LIMIT 1");
        ejecutarSQL("INSERT INTO ciclos_lavadero "
            + "(lavarropas_numero, jabon_id, litros_jabon, tipo_lavado, estado, fecha_fin) "
            + "VALUES (" + lavarropas + ", " + jabonId + ", 1.50, 'SUCIO', 'FINALIZADO', NOW())");
        int cicloId = lastInsertId();
        ejecutarSQL("INSERT INTO elementos_ciclo_lavadero (ciclo_id, elemento_clasificacion_id, cantidad) "
            + "VALUES (" + cicloId + ", " + clasificacionId + ", " + cantidad + ")");
    }

    private int lastInsertId() throws SQLException {
        return escalar("SELECT LAST_INSERT_ID()");
    }

    private int escalar(String sql) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
