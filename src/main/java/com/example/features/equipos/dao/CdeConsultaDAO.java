package com.example.features.equipos.dao;

import com.example.common.exception.DatabaseException;
import com.example.common.model.EquipoRegistrableInterface;
import com.example.common.model.EquipoRegistrableInterface.TipoEquipo;
import com.example.common.paginacion.CriteriosPagina;
import com.example.common.paginacion.Pagina;
import com.example.features.equipos.model.FiltroEquipos;
import com.example.features.equipos.ortopedias.dao.EquipoDAO;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.otros.dao.EquipoOtrosDAO;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.infrastructure.db.ConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * La lista unificada de la pantalla <b>Estado de Procesos</b>: equipos de ortopedia y "otros"
 * mezclados en una sola grilla, paginados y ordenados <b>en SQL</b>.
 *
 * <h2>Por qué esto vive en un DAO propio y no dentro de EquipoDAO o de EquipoOtrosDAO</h2>
 * Cruza dos features. Meter el {@code UNION ALL} en cualquiera de los dos DAO le daría a esa
 * feature conocimiento de la otra: {@code EquipoDAO} pasaría a saber que existe
 * {@code equipo_otros} y a tener que mantenerse al día con su esquema. Es exactamente la razón por
 * la que {@code DerivadorIngresoCDE} —el otro punto donde una feature toca tablas de otra— también
 * vive solo. Acá la dependencia va en la dirección correcta: este DAO conoce a los dos, y ninguno
 * de los dos lo conoce a él.
 *
 * <p><b>Y no se extrae ninguna abstracción común entre los dos DAO de equipos.</b> Tablas,
 * columnas, modelos y modalidades distintas: sólo comparten la <em>forma</em> de las consultas, que
 * es el peor motivo posible para una superclase. Lo único compartido es la semántica de los
 * filtros, y vive en {@link FiltroEquiposSql}.
 *
 * <h2>Dos viajes, igual que los otros dos DAO</h2>
 * El primero une las dos tablas proyectando <b>sólo lo necesario para ordenar y paginar</b>
 * ({@code id}, {@code tipo}, {@code orden_estado}, {@code fecha_ingreso}); el segundo trae el
 * detalle —<b>con materiales</b>— de esos 50, una consulta por tabla, reusando
 * {@link EquipoDAO#obtenerPorIds(List)} y {@link EquipoOtrosDAO#obtenerPorIds(List)}.
 *
 * <p><b>Los materiales no son opcionales.</b> {@code PantallaVerCDEv2} delega en
 * {@code PanelEquipoMaterial}, que es un {@code JSplitPane} de <b>dos</b> tablas: al seleccionar un
 * equipo arriba, carga sus materiales abajo <em>desde el objeto en memoria</em>
 * ({@code modeloMateriales.cargarMateriales(eq)}), sin ir a la base. Traer los materiales acotados
 * a los 50 de la página es barato y <b>preserva</b> que seleccionar una fila no dispare ninguna
 * consulta, que es como funciona hoy.
 *
 * <p>Un {@code JOIN} de equipos con materiales dentro del {@code UNION ALL} no serviría igual: una
 * fila por (equipo × material) hace que {@code LIMIT 50} corte <b>materiales</b> y no equipos. Es
 * el mismo motivo por el que los otros dos DAO hacen dos viajes; está explicado entero en el
 * javadoc de {@link EquipoDAO#obtenerPagina}, incluido por qué la versión "elegante"
 * ({@code IN (SELECT … LIMIT ?)}) <b>no existe en MySQL</b> y H2 no lo delata.
 *
 * <p><b>Las cuatro consultas de una página son secuenciales, nunca anidadas</b> (conteo, claves,
 * detalle de ortopedias, detalle de "otros"): cada una abre y cierra su conexión antes de que
 * empiece la siguiente. Es el invariante del que depende la aritmética del semáforo de
 * {@code ConnectionPool} —ninguna operación mantiene dos conexiones abiertas a la vez— y esta clase
 * es la que más cerca está de romperlo, así que queda dicho también acá.
 */
public class CdeConsultaDAO {

    private static final Logger log = LoggerFactory.getLogger(CdeConsultaDAO.class);

    /** Etiqueta de {@code equipos} en la columna discriminadora de la unión. */
    private static final String TIPO_ORTOPEDIA = "ORTOPEDIA";
    /** Etiqueta de {@code equipo_otros} en la columna discriminadora de la unión. */
    private static final String TIPO_OTROS = "OTROS";

    /**
     * El {@code CASE} que traduce la columna {@code estado} al orden del flujo, <b>generado desde
     * {@link EstadoEquipo}</b> en vez de tipeado a mano.
     *
     * <h2>Por qué generado y no copiado</h2>
     * El mismo mapeo (nombre → orden) existe ya en tres lugares: el enum,
     * {@code EquipoMaterialHelper.recalcularEstadoEquipo} y
     * {@code EquipoOtrosMaterialHelper.recalcularEstadoEquipo}. Una cuarta copia literal acá sería
     * la que se olvida de actualizar el día que se agregue un estado — y el síntoma no sería un
     * error sino un <b>orden equivocado</b>, que nadie reporta como bug. Generándolo, el orden de
     * esta consulta no puede divergir de {@code EstadoEquipo.getOrden()}, que es el que usa hoy
     * {@code EquipoTableModel.actualizarDatos} para ordenar la grilla en memoria.
     *
     * <p><b>Esto no es SQL armada con input del operador.</b> Los literales salen de constantes del
     * enum, no de nada que se tipee en la pantalla. Lo que viene del operador viaja por {@code ?},
     * sin excepción (ver {@link FiltroEquiposSql}).
     *
     * <p>{@code ELSE} devuelve el orden de {@code NUEVO}, que es lo mismo que hace
     * {@code EstadoEquipo.desdeBD(null)} y el {@code ELSE 1} de los dos helpers: la columna es
     * nullable en las dos tablas (V1, V2) y un valor desconocido tiene que ordenarse como el estado
     * más atrasado, no desaparecer del orden.
     */
    private static String casoOrdenEstado(String columna) {
        StringBuilder sb = new StringBuilder("CASE ");
        for (EstadoEquipo estado : EstadoEquipo.values()) {
            sb.append("WHEN ").append(columna).append(" = '").append(estado.getNombre())
              .append("' THEN ").append(estado.getOrden()).append(' ');
        }
        return sb.append("ELSE ").append(EstadoEquipo.NUEVO.getOrden()).append(" END").toString();
    }

    /**
     * La mitad de ortopedias de la unión. Los {@code JOIN} contra {@code clientes},
     * {@code profesionales} e {@code instituciones} los agrega
     * {@link FiltroEquiposSql.Condicion#joins()} sólo cuando su filtro de texto tiene algo escrito:
     * esta rama proyecta cuatro columnas de {@code equipos} y nada más, así que un {@code JOIN} sin
     * predicado que lo use no aporta nada y cuesta el índice del {@code ORDER BY}.
     */
    private static final String SQL_RAMA_ORTOPEDIA =
        "SELECT e.id AS id, '" + TIPO_ORTOPEDIA + "' AS tipo, "
        + casoOrdenEstado("e.estado") + " AS orden_estado, e.fecha_ingreso AS fecha_ingreso "
        + "FROM equipos e ";

    /** La mitad de "otros" de la unión; su {@code JOIN} con {@code clientes}, igual que arriba. */
    private static final String SQL_RAMA_OTROS =
        "SELECT eo.id AS id, '" + TIPO_OTROS + "' AS tipo, "
        + casoOrdenEstado("eo.estado") + " AS orden_estado, eo.fecha_ingreso AS fecha_ingreso "
        + "FROM equipo_otros eo ";

    /**
     * El orden de Estado de Procesos. <b>No es {@code fecha_ingreso DESC}</b>, y ésa es la
     * corrección que define este DAO.
     *
     * <h2>{@code orden_estado} va primero porque es lo que la pantalla muestra hoy</h2>
     * {@code EquipoTableModel.actualizarDatos} ordena la grilla por
     * {@code calcularEstado().getOrden()} ascendente — "más atrasado primero". Paginar por fecha y
     * dejar que el {@code TableModel} reordene <b>dentro de cada página de 50</b> rompería el orden
     * global: la página 2 volvería a empezar por los más atrasados <em>de esa página</em>, y el
     * operador vería el mismo estado repetirse pestaña tras pestaña. El orden tiene que venir de
     * SQL, que es el único lugar desde el que puede ser global.
     *
     * <h2>{@code tipo} e {@code id} desempatan, y no son decorativos</h2>
     * Con {@code LIMIT/OFFSET}, un orden que no es <b>total</b> no define qué fila cae en qué
     * página: dos filas empatadas pueden alternar entre una consulta y la siguiente, y entonces una
     * aparece en dos páginas y la otra en ninguna. Los empates acá no son teóricos: la unión mezcla
     * dos tablas con ids independientes (el equipo 7 de ortopedia y el 7 de "otros" existen los
     * dos), {@code fecha_ingreso} tiene resolución de segundo, y {@code orden_estado} toma sólo
     * siete valores distintos. {@code tipo} + {@code id} cierran el orden.
     *
     * <h2>⚠️ Este ORDER BY no usa ningún índice, y no puede</h2>
     * Medido con {@code EXPLAIN} sobre 3 000 + 3 000 equipos (MySQL 8.0.43, con la V23 aplicada):
     * las dos ramas salen {@code type: ALL} y el derivado sale
     * {@code ALL … rows: 1800 … Using filesort}. <b>No es un defecto de esta consulta: es inherente
     * a pedir un orden global sobre una unión con {@code LIMIT}.</b> La clave de orden cruza las dos
     * tablas, así que ningún índice de una sola puede darlo ya ordenado; el servidor tiene que
     * materializar la unión, ordenarla y recién ahí cortar. Compárese con
     * {@link EquipoDAO#obtenerPagina} y {@link EquipoOtrosDAO#obtenerPagina}, que sí salen por
     * {@code Backward index scan} porque ordenan sobre una sola tabla.
     *
     * <p><b>Por qué se acepta hoy:</b> producción tiene ~1 400 equipos entre las dos tablas
     * (482 + 946, medidos en el Paso 2 del plan). Materializar y ordenar eso es del orden de los
     * milisegundos, y a cambio se deja de traer y de repintar el histórico completo, que es lo que
     * este paso vino a arreglar.
     *
     * <p><b>La salida, escrita acá para que nadie tenga que redescubrirla el día que duela:</b> para
     * los primeros {@code OFFSET + LIMIT} de un orden global, a cada rama le alcanza con <em>sus</em>
     * primeros {@code OFFSET + LIMIT} — así que el {@code LIMIT} se puede empujar dentro de cada
     * rama ({@code (SELECT … ORDER BY … LIMIT ?) UNION ALL (SELECT … ORDER BY … LIMIT ?)}) y el
     * derivado baja de "toda la historia" a 2 × (offset + 50). Para que además desaparezca el
     * filesort de cada rama haría falta un índice compuesto {@code (estado, fecha_ingreso)}, que la
     * V23 no trae: son dos índices de una columna cada uno. Nada de esto se hace antes de que una
     * medición lo pida — lo decide el Paso 13 del plan.
     */
    private static final String SQL_ORDEN =
        " ORDER BY u.orden_estado ASC, u.fecha_ingreso DESC, u.tipo ASC, u.id DESC";

    private final EquipoDAO equipoDAO;
    private final EquipoOtrosDAO equipoOtrosDAO;

    public CdeConsultaDAO(EquipoDAO equipoDAO, EquipoOtrosDAO equipoOtrosDAO) {
        this.equipoDAO = Objects.requireNonNull(equipoDAO, "equipoDAO");
        this.equipoOtrosDAO = Objects.requireNonNull(equipoOtrosDAO, "equipoOtrosDAO");
    }

    /**
     * Una página de la lista unificada, con sus materiales, con los filtros y el orden resueltos en
     * SQL, y el total de equipos que matchean.
     *
     * <p>Pedir una página más allá del total devuelve una página vacía con el total correcto y
     * <b>no</b> lanza.
     *
     * @throws DatabaseException si falla cualquiera de las consultas — nunca una lista a medias
     */
    public Pagina<EquipoRegistrableInterface> obtenerPagina(FiltroEquipos filtro,
                                                            CriteriosPagina criterios) {
        return armarPagina(filtro, criterios, contar(filtro));
    }

    /**
     * La misma página, pero con el total ya sabido: <b>no vuelve a contar</b>. El total sólo cambia
     * cuando cambian los <em>filtros</em>; ir de la página 3 a la 4 lo arrastra tal cual, y volver
     * a contar duplicaría las consultas que esta paginación vino a ahorrar.
     */
    public Pagina<EquipoRegistrableInterface> obtenerPagina(FiltroEquipos filtro,
                                                            CriteriosPagina criterios,
                                                            long totalConocido) {
        if (totalConocido < 0) {
            throw new IllegalArgumentException("totalConocido no puede ser negativo: " + totalConocido);
        }
        return armarPagina(filtro, criterios, totalConocido);
    }

    /**
     * Cuántos equipos —de los dos tipos— matchean el filtro, con los <b>mismos</b> {@code WHERE}
     * que la página, de los mismos métodos de {@link FiltroEquiposSql}.
     *
     * <p>Un solo {@code COUNT(*)} sobre la unión y no dos consultas sumadas: es un viaje en vez de
     * dos, y sobre todo es imposible que las dos mitades se cuenten con criterios distintos.
     */
    public long contar(FiltroEquipos filtro) {
        Union union = union(filtro);
        String sql = "SELECT COUNT(*) FROM (" + union.sql() + ") u";
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            FiltroEquiposSql.aplicar(ps, union.parametros());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            log.error("Error al contar la lista unificada del CDE", e);
            throw new DatabaseException("Error al contar los equipos del CDE", e);
        }
    }

    // ── armado ───────────────────────────────────────────────────────────────

    private Pagina<EquipoRegistrableInterface> armarPagina(FiltroEquipos filtro,
                                                           CriteriosPagina criterios, long total) {
        List<ClaveCde> claves = clavesDePagina(filtro, criterios);
        if (claves.isEmpty()) {
            return new Pagina<>(List.of(), criterios.numeroPagina(), criterios.tamanioPagina(), total);
        }
        return new Pagina<>(detalleEnElOrdenDe(claves),
            criterios.numeroPagina(), criterios.tamanioPagina(), total);
    }

    /** Qué equipo es cada fila de la página: la tabla de la que salió y su id dentro de ella. */
    private record ClaveCde(TipoEquipo tipo, int id) {
    }

    private List<ClaveCde> clavesDePagina(FiltroEquipos filtro, CriteriosPagina criterios) {
        Union union = union(filtro);
        String sql = "SELECT u.id, u.tipo FROM (" + union.sql() + ") u"
            + SQL_ORDEN + " LIMIT ? OFFSET ?";

        List<ClaveCde> claves = new ArrayList<>();
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int siguiente = FiltroEquiposSql.aplicar(ps, union.parametros());
            ps.setInt(siguiente, criterios.tamanioPagina());
            ps.setLong(siguiente + 1, criterios.offset());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    claves.add(new ClaveCde(
                        TIPO_OTROS.equals(rs.getString("tipo")) ? TipoEquipo.OTROS : TipoEquipo.ORTOPEDIA,
                        rs.getInt("id")));
                }
            }
        } catch (SQLException e) {
            log.error("Error al obtener la página de la lista unificada del CDE", e);
            throw new DatabaseException("Error al obtener la página de equipos del CDE", e);
        }
        return claves;
    }

    /**
     * Segundo viaje: el detalle con materiales de los equipos de la página, devuelto en el orden
     * que fijó el primero.
     *
     * <p>El servidor no está obligado a devolver el {@code IN (…)} en ningún orden particular, y de
     * todos modos son dos consultas separadas, así que el orden se rearma acá contra la lista de
     * claves. Son 50 elementos.
     */
    private List<EquipoRegistrableInterface> detalleEnElOrdenDe(List<ClaveCde> claves) {
        List<Integer> idsOrtopedia = idsDe(claves, TipoEquipo.ORTOPEDIA);
        List<Integer> idsOtros     = idsDe(claves, TipoEquipo.OTROS);

        Map<Integer, Equipo> ortopedias = new HashMap<>();
        for (Equipo equipo : equipoDAO.obtenerPorIds(idsOrtopedia)) {
            ortopedias.put(equipo.getId(), equipo);
        }
        Map<Integer, EquipoOtros> otros = new HashMap<>();
        for (EquipoOtros equipo : equipoOtrosDAO.obtenerPorIds(idsOtros)) {
            otros.put(equipo.getId(), equipo);
        }

        List<EquipoRegistrableInterface> ordenados = new ArrayList<>(claves.size());
        for (ClaveCde clave : claves) {
            EquipoRegistrableInterface equipo = clave.tipo() == TipoEquipo.ORTOPEDIA
                ? ortopedias.get(clave.id())
                : otros.get(clave.id());
            // Un null acá significa que otro operador borró el equipo entre los dos viajes. Se
            // omite en silencio: la fila ya no existe, y la alternativa —fallar la página entera—
            // castigaría al operador por algo que pasó en otra máquina.
            if (equipo != null) {
                ordenados.add(equipo);
            }
        }
        return ordenados;
    }

    private static List<Integer> idsDe(List<ClaveCde> claves, TipoEquipo tipo) {
        return claves.stream().filter(c -> c.tipo() == tipo).map(ClaveCde::id).toList();
    }

    // ── la unión ─────────────────────────────────────────────────────────────

    /** El {@code UNION ALL} armado y los parámetros de sus dos ramas, en orden. */
    private record Union(String sql, List<Object> parametros) {
    }

    /**
     * Arma el {@code UNION ALL} con el {@code WHERE} de cada rama.
     *
     * <p>Lo usan la página y el conteo: <b>que salga de un solo método no es estilo, es
     * corrección</b>. Si divergieran, la UI diría "127 resultados" y mostraría otra cosa, y nadie
     * lo notaría hasta que alguien contara a mano.
     *
     * <p>La rama de "otros" usa {@link FiltroEquiposSql#paraOtrosEnUnionCde} y no
     * {@code paraOtros}: esta pantalla aplica el filtro de institución también a los "otros", donde
     * el valor es siempre cadena vacía, y por eso desaparecen en cuanto el campo tiene texto. Está
     * explicado en el javadoc de ese método; acá basta con no "arreglarlo".
     */
    private static Union union(FiltroEquipos filtro) {
        FiltroEquiposSql.Condicion ortopedia = FiltroEquiposSql.paraOrtopedias(filtro);
        FiltroEquiposSql.Condicion otros     = FiltroEquiposSql.paraOtrosEnUnionCde(filtro);

        List<Object> parametros = new ArrayList<>(ortopedia.parametros());
        parametros.addAll(otros.parametros());

        return new Union(
            SQL_RAMA_ORTOPEDIA + ortopedia.joins() + ortopedia.sql()
                + " UNION ALL "
                + SQL_RAMA_OTROS + otros.joins() + otros.sql(),
            parametros);
    }
}
