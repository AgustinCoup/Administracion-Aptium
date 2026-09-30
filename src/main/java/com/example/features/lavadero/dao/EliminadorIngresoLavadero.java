package com.example.features.lavadero.dao;

import com.example.common.constants.Constantes.Mensajes;
import com.example.common.dao.ControlConcurrencia;
import com.example.common.eliminacion.ArchivoIngresosDAO;
import com.example.common.eliminacion.Bloqueo;
import com.example.common.eliminacion.EliminacionBloqueadaException;
import com.example.common.eliminacion.IngresoAEliminar;
import com.example.common.eliminacion.IngresoArchivado;
import com.example.common.eliminacion.ModuloIngreso;
import com.example.common.eliminacion.ResumenEquipo;
import com.example.common.eliminacion.ResumenIngresoLavadero;
import com.example.common.eliminacion.ResumenIngresoLavadero.DerivadoCde;
import com.example.common.eliminacion.ResumenIngresoLavadero.LineaElemento;
import com.example.common.eliminacion.SnapshotJson;
import com.example.common.exception.ConflictoConcurrenciaException;
import com.example.common.exception.DatabaseException;
import com.example.common.exception.ResourceNotFoundException;
import com.example.features.equipos.otros.dao.BloqueoEquipoOtros;
import com.example.features.equipos.otros.dao.EliminadorEquipoOtros;
import com.example.features.lavadero.model.EstadoIngresoLavadero;
import com.example.infrastructure.db.ConnectionPool;
import com.example.infrastructure.db.TransactionalConnection;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Elimina un ingreso de Lavadero ({@code ingresos_lavadero}) en <b>cualquier estado</b>, con todo
 * lo que lo representa —salidas, fracciones de equipo, tandas, clasificación, bolsas— y con los
 * ingresos del CDE que se crearon al derivar su ropa. Antes de borrar, archiva el árbol completo en
 * {@code ingresos_eliminados} dentro de la misma transacción: una fila {@code LAVADERO} y una
 * {@code OTROS} por cada derivado, enlazada a la primera por {@code archivo_padre_id}.
 *
 * <p><b>El {@code equipo_otros} derivado se borra con las fases de {@link EliminadorEquipoOtros}</b>
 * ({@code bloquear} / {@code verificar} / {@code archivarYBorrar}) sobre esta misma conexión.
 * Ninguna consulta de borrado del CDE se copia acá: es el segundo punto donde Lavadero escribe en
 * tablas del CDE, junto con {@code DerivadorIngresoCDE}, y lo hace a través de la clase dueña.</p>
 *
 * <p><b>Reglas del usuario</b> (no reabrir): se rechaza si hay ropa del ingreso en un ciclo sin
 * finalizar, si un derivado está en un lote en curso, o si un derivado es <b>compartido</b> con otro
 * ingreso de Lavadero —un mismo {@code equipo_otros} puede sumar ropa de varios ingresos
 * ({@code ConstructorIngresoCDE} arma uno por cliente asignado, y con APTIUM todos caen en el
 * mismo), no hay vínculo material ↔ salida y no se puede descontar "sólo su parte"—. Un ciclo
 * finalizado que queda sin elementos se borra; uno que sigue teniendo ropa de otro ingreso,
 * no.</p>
 */
public class EliminadorIngresoLavadero {

    private static final Logger log = LoggerFactory.getLogger(EliminadorIngresoLavadero.class);

    /** Versión del contenido de {@code snapshot}; ver {@link SnapshotJson#raiz}. */
    static final int FORMATO_SNAPSHOT = 1;

    // ── resumen ──────────────────────────────────────────────────────────────

    private static final String SQL_CABECERA =
        "SELECT il.*, c.nombre AS cliente_nombre "
            + "FROM ingresos_lavadero il "
            + "LEFT JOIN clientes c ON c.id = il.cliente_id "
            + "WHERE il.id = ?";

    /** Join histórico: no filtra por {@code activo}, un elemento dado de baja conserva su nombre. */
    private static final String SQL_ELEMENTOS =
        "SELECT cel.nombre, ecl.cantidad "
            + "FROM elementos_clasificacion_lavadero ecl "
            + "JOIN catalogo_elementos_lavadero cel ON cel.id = ecl.elemento_id "
            + "WHERE ecl.ingreso_id = ? ORDER BY ecl.id";

    private static final String SQL_LAVARROPAS_EN_CURSO_DEL_INGRESO =
        "SELECT DISTINCT cl.lavarropas_numero "
            + "FROM elementos_ciclo_lavadero eci "
            + "JOIN elementos_clasificacion_lavadero ecl ON ecl.id = eci.elemento_clasificacion_id "
            + "JOIN ciclos_lavadero cl ON cl.id = eci.ciclo_id "
            + "WHERE ecl.ingreso_id = ? AND cl.fecha_fin IS NULL "
            + "ORDER BY cl.lavarropas_numero";

    /**
     * Los {@code equipo_otros} derivados de este ingreso. Dos caminos porque una salida tiene
     * poblada exactamente una de sus dos columnas ({@code V20}): la tanda regular o la instancia de
     * un equipo repartido. Cada rama entra por {@code ingreso_id} y baja por índices.
     */
    private static final String SQL_DERIVADOS_DEL_INGRESO =
        "SELECT s.equipo_otros_id FROM salidas_lavadero s "
            + "JOIN elementos_ciclo_lavadero eci ON eci.id = s.elemento_ciclo_id "
            + "JOIN elementos_clasificacion_lavadero ecl ON ecl.id = eci.elemento_clasificacion_id "
            + "WHERE ecl.ingreso_id = ? AND s.equipo_otros_id IS NOT NULL "
            + "UNION "
            + "SELECT s.equipo_otros_id FROM salidas_lavadero s "
            + "JOIN instancias_equipo_ciclo ie ON ie.id = s.instancia_equipo_id "
            + "JOIN elementos_clasificacion_lavadero ecl ON ecl.id = ie.elemento_clasificacion_id "
            + "WHERE ecl.ingreso_id = ? AND s.equipo_otros_id IS NOT NULL";

    // ── Fase A: bloquear. Los %s son la lista de ids (un ? por id). ─────────
    //
    // El ORDER BY de estas sentencias ordena el resultado, NO los locks: InnoDB bloquea en el orden
    // en que recorre el índice. Ver el caso residual (c) del javadoc de eliminar().

    private static final String SQL_BLOQUEAR_INGRESO =
        "SELECT estado FROM ingresos_lavadero WHERE id = ? FOR UPDATE";

    private static final String SQL_BLOQUEAR_LINEAS =
        "SELECT id FROM elementos_clasificacion_lavadero WHERE ingreso_id = ? ORDER BY id FOR UPDATE";

    private static final String SQL_BLOQUEAR_TANDAS =
        "SELECT id, ciclo_id FROM elementos_ciclo_lavadero "
            + "WHERE elemento_clasificacion_id IN (%s) ORDER BY id FOR UPDATE";

    private static final String SQL_BLOQUEAR_INSTANCIAS =
        "SELECT id FROM instancias_equipo_ciclo WHERE elemento_clasificacion_id IN (%s) ORDER BY id FOR UPDATE";

    /**
     * Las salidas van en dos sentencias, una por columna, y no en una con {@code OR}: con
     * {@code elemento_ciclo_id IN (…) OR instancia_equipo_id IN (…)} MySQL puede elegir recorrer la
     * tabla entera, y un {@code FOR UPDATE} bajo {@code REPEATABLE READ} bloquea todo lo que recorre
     * —todas las salidas del sistema, hasta el {@code commit}—.
     */
    private static final String SQL_BLOQUEAR_SALIDAS_DE_TANDAS =
        "SELECT id, equipo_otros_id FROM salidas_lavadero WHERE elemento_ciclo_id IN (%s) ORDER BY id FOR UPDATE";

    private static final String SQL_BLOQUEAR_SALIDAS_DE_INSTANCIAS =
        "SELECT id, equipo_otros_id FROM salidas_lavadero WHERE instancia_equipo_id IN (%s) ORDER BY id FOR UPDATE";

    // ── Fase B: verificar (lecturas no bloqueantes) ──────────────────────────

    private static final String SQL_LAVARROPAS_EN_CURSO =
        "SELECT DISTINCT lavarropas_numero FROM ciclos_lavadero "
            + "WHERE fecha_fin IS NULL AND id IN (%s) ORDER BY lavarropas_numero";

    /** El ingreso de Lavadero de cada salida, por la columna que tenga poblada. */
    private static final String SQL_INGRESOS_DE_SALIDAS =
        "SELECT DISTINCT ecl.ingreso_id FROM salidas_lavadero s "
            + "LEFT JOIN elementos_ciclo_lavadero ec ON ec.id = s.elemento_ciclo_id "
            + "LEFT JOIN instancias_equipo_ciclo ie ON ie.id = s.instancia_equipo_id "
            + "JOIN elementos_clasificacion_lavadero ecl "
            + "  ON ecl.id = COALESCE(ec.elemento_clasificacion_id, ie.elemento_clasificacion_id) "
            + "WHERE s.id IN (%s) ORDER BY ecl.ingreso_id";

    // ── Fase C: snapshot y borrado ───────────────────────────────────────────

    private static final String SQL_SNAPSHOT_BOLSAS =
        "SELECT * FROM bolsas_lavadero WHERE ingreso_id = ? ORDER BY id";

    private static final String SQL_SNAPSHOT_LINEAS =
        "SELECT ecl.*, cel.nombre AS elemento_nombre "
            + "FROM elementos_clasificacion_lavadero ecl "
            + "LEFT JOIN catalogo_elementos_lavadero cel ON cel.id = ecl.elemento_id "
            + "WHERE ecl.ingreso_id = ? ORDER BY ecl.id";

    private static final String SQL_SNAPSHOT_TANDAS =
        "SELECT * FROM elementos_ciclo_lavadero WHERE id IN (%s) ORDER BY id";

    private static final String SQL_SNAPSHOT_CICLOS =
        "SELECT cl.*, cj.nombre AS jabon_nombre "
            + "FROM ciclos_lavadero cl "
            + "LEFT JOIN catalogo_jabones cj ON cj.id = cl.jabon_id "
            + "WHERE cl.id IN (%s) ORDER BY cl.id";

    private static final String SQL_SNAPSHOT_INSUMOS =
        "SELECT icl.ciclo_id, ci.id AS insumo_id, ci.nombre AS insumo_nombre "
            + "FROM insumos_ciclo_lavadero icl "
            + "JOIN catalogo_insumos ci ON ci.id = icl.insumo_id "
            + "WHERE icl.ciclo_id IN (%s) ORDER BY icl.ciclo_id, ci.nombre";

    private static final String SQL_SNAPSHOT_INSTANCIAS =
        "SELECT * FROM instancias_equipo_ciclo WHERE id IN (%s) ORDER BY id";

    private static final String SQL_SNAPSHOT_SALIDAS =
        "SELECT * FROM salidas_lavadero WHERE id IN (%s) ORDER BY id";

    private static final String SQL_BORRAR_SALIDAS =
        "DELETE FROM salidas_lavadero WHERE id IN (%s)";

    private static final String SQL_BORRAR_TANDAS =
        "DELETE FROM elementos_ciclo_lavadero WHERE id IN (%s)";

    private static final String SQL_BORRAR_INSTANCIAS =
        "DELETE FROM instancias_equipo_ciclo WHERE id IN (%s)";

    /** La clasificación y las bolsas caen por {@code CASCADE} ({@code V7}, {@code V9}). */
    private static final String SQL_BORRAR_INGRESO =
        "DELETE FROM ingresos_lavadero WHERE id = ? AND estado = ?";

    /**
     * Sin guarda de conteo: que un ciclo siga teniendo ropa de otro ingreso es legítimo, y entonces
     * no se borra. Los insumos caen por {@code CASCADE} ({@code V24}). El {@code NOT EXISTS} dentro
     * del {@code DELETE} es una lectura <b>bloqueante</b> en MySQL (toma locks compartidos sobre las
     * tandas de los otros ingresos del ciclo): ver el caso residual (e) de {@link #eliminar}.
     */
    private static final String SQL_BORRAR_CICLOS_VACIOS =
        "DELETE FROM ciclos_lavadero WHERE id IN (%s) AND fecha_fin IS NOT NULL "
            + "AND NOT EXISTS (SELECT 1 FROM elementos_ciclo_lavadero e WHERE e.ciclo_id = ciclos_lavadero.id)";

    private final EliminadorEquipoOtros eliminadorOtros;
    private final ArchivoIngresosDAO archivo;

    public EliminadorIngresoLavadero(EliminadorEquipoOtros eliminadorOtros, ArchivoIngresosDAO archivo) {
        this.eliminadorOtros = Objects.requireNonNull(eliminadorOtros, "eliminadorOtros");
        this.archivo = Objects.requireNonNull(archivo, "archivo");
    }

    /**
     * Lo que el diálogo de confirmación muestra: cabecera, elementos clasificados, los ingresos del
     * CDE que se borran con este, los bloqueos y el token de guarda (estado + derivados). Lectura
     * sin transacción: es informativa, y {@link #eliminar} re-verifica todo con sus propios locks.
     *
     * <p>Calcula los mismos bloqueos que la transacción y en el mismo orden (ciclos en curso por
     * número de lavarropas; después, por derivado ascendente, si es compartido y sus lotes en
     * curso), para que el operador lea lo mismo si el bloqueo se vio acá o apareció por una
     * carrera. Los de cada derivado salen de {@link EliminadorEquipoOtros#resumir}, que se llama
     * <b>después</b> de cerrar la conexión propia: ninguna operación tiene dos conexiones abiertas a
     * la vez (la aritmética del semáforo de {@code ConnectionPool} depende de eso).</p>
     *
     * @throws ResourceNotFoundException si el ingreso ya no existe (otro lo eliminó)
     */
    public ResumenIngresoLavadero resumir(int ingresoId) {
        LecturaPropia propia;
        try (Connection conn = ConnectionPool.getConnection()) {
            propia = leerPropio(conn, ingresoId);
        } catch (SQLException e) {
            log.error("Error al resumir el ingreso de lavadero {} para eliminarlo", ingresoId, e);
            throw new DatabaseException("Error al leer el ingreso de lavadero " + ingresoId + " para eliminarlo", e);
        }

        List<Bloqueo> bloqueos = new ArrayList<>();
        propia.lavarropasEnCurso().forEach(n -> bloqueos.add(new Bloqueo.CicloEnCurso(n)));
        List<DerivadoCde> derivados = new ArrayList<>();
        for (int equipoOtrosId : propia.derivados()) {
            ResumenEquipo derivado;
            try {
                derivado = eliminadorOtros.resumir(equipoOtrosId);
            } catch (ResourceNotFoundException borradoEnElMedio) {
                // Lo eliminaron desde Ver Equipos entre las dos lecturas: su salida ya quedó con
                // equipo_otros_id NULL, así que tampoco es un derivado para la transacción.
                continue;
            }
            derivados.add(new DerivadoCde(equipoOtrosId, derivado.estado(), unidades(derivado), derivado.version()));
            List<Integer> otros = derivado.ingresosLavaderoOrigen().stream()
                .filter(id -> id != ingresoId).toList();
            if (!otros.isEmpty()) {
                bloqueos.add(new Bloqueo.DerivadoCompartido(equipoOtrosId, otros));
            }
            bloqueos.addAll(lotesDelDerivado(equipoOtrosId, derivado.bloqueos()));
        }
        Cabecera c = propia.cabecera();
        return new ResumenIngresoLavadero(new IngresoAEliminar(ModuloIngreso.LAVADERO, ingresoId),
            c.clienteNombre(), c.fechaIngreso(), c.estado(), c.pesoTotalKg(), propia.elementos(),
            derivados, bloqueos);
    }

    /**
     * Archiva y borra el ingreso, sus derivados y los ciclos finalizados que quedan vacíos, en una
     * transacción y en tres fases.
     *
     * <h2>Fase A — bloquear todo (ninguna lectura no bloqueante antes del final de esta fase)</h2>
     * <ol>
     *   <li>el ingreso {@code FOR UPDATE}. Sin fila, o con otro estado que el visto → conflicto;</li>
     *   <li>sus líneas de clasificación, por {@code ingreso_id};</li>
     *   <li>las tandas ({@code elementos_ciclo_lavadero}) de esas líneas, con su {@code ciclo_id};</li>
     *   <li>las instancias de equipo repartido de esas líneas;</li>
     *   <li>las salidas de esas tandas y de esas instancias (dos sentencias, ver
     *       {@link #SQL_BLOQUEAR_SALIDAS_DE_TANDAS});</li>
     *   <li>los derivados = los {@code equipo_otros_id} no nulos de (5). Si no son exactamente los
     *       vistos → conflicto. Para cada uno, en orden ascendente,
     *       {@link EliminadorEquipoOtros#bloquear} (sus salidas → materiales → cabecera). Si la
     *       cabecera falta (otro lo borró desde Ver Equipos) → conflicto. Su {@code version} se
     *       compara con la vista recién después de (9).</li>
     * </ol>
     *
     * <h2>Fase B — verificar (acá empiezan las lecturas no bloqueantes)</h2>
     * <ol start="7">
     *   <li>los ciclos sin finalizar entre los de (3) → {@link Bloqueo.CicloEnCurso};</li>
     *   <li>por derivado: las salidas que {@code bloquear} encontró y no son de (5) son de otro
     *       ingreso → {@link Bloqueo.DerivadoCompartido}, con esos ingresos. Después
     *       {@link EliminadorEquipoOtros#verificar} → {@link Bloqueo.DerivadoEnLoteEnCurso};</li>
     *   <li>si hay bloqueos → {@link EliminacionBloqueadaException} con <b>todos</b>. Si no, la
     *       {@code version} de cada derivado tiene que ser la vista (otro lo avanzó, lo entregó o lo
     *       corrigió → conflicto: igual que desde Ver Equipos, se archiva lo que el operador
     *       confirmó). Va después de los bloqueos porque {@code lanzarLote} también bumpea la
     *       {@code version}, y un lote en curso se informa como bloqueo.</li>
     * </ol>
     *
     * <h2>Fase C — archivar y borrar (el orden lo dictan las FK {@code RESTRICT})</h2>
     * <ol start="10">
     *   <li>el snapshot del ingreso → archivo (la fila padre);</li>
     *   <li>cada derivado con {@link EliminadorEquipoOtros#archivarYBorrar}, con la {@code version}
     *       que trajo su {@code bloquear} y el archivo de (10) como padre. El {@code SET NULL} de la
     *       FK le pone {@code equipo_otros_id = NULL} a nuestras salidas, que siguen existiendo;</li>
     *   <li>salidas ({@code RESTRICT} hacia tandas e instancias);</li>
     *   <li>tandas ({@code RESTRICT} hacia líneas e instancias);</li>
     *   <li>instancias ({@code RESTRICT} hacia líneas);</li>
     *   <li>el ingreso con CAS de {@code estado}; líneas y bolsas caen por {@code CASCADE};</li>
     *   <li>los ciclos finalizados de (3) que quedaron sin elementos.</li>
     * </ol>
     * <p>(12)–(14) son guardas de conteo con {@code executeUpdate()}, nunca {@code executeBatch()}
     * ({@code SUCCESS_NO_INFO} con {@code rewriteBatchedStatements=true}): con todo bloqueado desde
     * la Fase A, un conteo distinto de lo bloqueado es un bug, y aborta.</p>
     *
     * <h2>Por qué la Fase A termina antes de la primera lectura no bloqueante</h2>
     * <p>Bajo el {@code REPEATABLE READ} de MySQL la vista de la transacción se fija en la primera
     * lectura no bloqueante. (7) y (8) —el ciclo en curso y el lote en curso— se leen
     * <b>después</b> de tener todo tomado: un ciclo lanzado mientras (2) esperaba, o un lote lanzado
     * sobre el derivado mientras (6) esperaba, ya commiteó y la vista lo incluye. Una lectura común
     * antes de (6) congelaría una vista donde ese lote no existe, y se borraría un ingreso del CDE
     * que está en el autoclave. <b>H2 no lo delata</b> (corre en {@code READ COMMITTED}).</p>
     *
     * <h2>La matriz de entrelazados (H2 no la reproduce; ningún test la defiende)</h2>
     * <p>Una entrada por escritor concurrente. El borrado no toma nunca {@code lavarropas} y toma
     * {@code ciclos_lavadero} último, en (16): respeta el orden {@code lavarropas →
     * ciclos_lavadero} de {@code lanzarTanda} y {@code LavarropasDAO.darDeBaja}.</p>
     * <ul>
     *   <li><b>Clasificación</b> ({@code ClasificacionLavaderoDAO.guardar}: ingreso → {@code INSERT}
     *       líneas). Los dos toman primero el ingreso y se serializan ahí. Si clasifica primero, (1)
     *       lee {@code CLASIFICADO} ≠ {@code PENDIENTE} visto → conflicto. Si borra primero, el CAS
     *       de la clasificación afecta 0 filas → {@code CONFLICTO_CLASIFICACION}.</li>
     *   <li><b>{@code lanzarTanda}</b> ({@code lavarropas} → ciclos (gap) → líneas ascendentes →
     *       {@code INSERT}s). Se encuentran en las líneas, que los dos toman en orden de id (el
     *       índice de (2) es {@code (ingreso_id, id)}). Si la tanda las toma primero, el borrado
     *       espera en (2) y después (7) ve el ciclo nuevo → {@code CicloEnCurso}. Si el borrado las
     *       toma primero, la tanda espera, y al commitear el borrado la línea ya no existe → saldo
     *       0 → {@code SaldoConsumidoException} → la tanda descarta el staging, que es lo correcto:
     *       esa ropa ya no existe.</li>
     *   <li><b>{@code finalizarCiclo}</b> (ciclo → ingreso). Puede esperar en nuestro ingreso con el
     *       ciclo tomado, pero el borrado no bloquea {@code ciclos_lavadero} antes de (16): lo lee
     *       en (7) sin bloquear, lo ve en curso (la finalización no commiteó) → rechaza y suelta.
     *       (16) sólo toca ciclos que (7) vio finalizados, y un {@code finalizarCiclo} sobre un
     *       ciclo ya finalizado aborta en su CAS sin llegar al ingreso: a lo sumo una espera.</li>
     *   <li><b>{@code marcarListo}</b> (tandas → instancias → salidas). No toma ni el ingreso ni las
     *       líneas: se encuentran en (3)/(4). Si marca primero, (5) ve sus salidas y se borran con
     *       el ingreso; si borra primero, la tanda desaparece → saldo 0 → conflicto.</li>
     *   <li><b>{@code volverALavado}</b> (sólo salidas). Si revierte primero, (5) ya no ve esa
     *       salida; si borra primero, su {@code DELETE … AND destino IS NULL} afecta 0 filas →
     *       conflicto.</li>
     *   <li><b>{@code derivar}</b> ({@code INSERT equipo_otros} → salidas → ingreso). Si no toca
     *       nuestro ingreso al final y commitea primero, (5) ve el {@code equipo_otros_id} nuevo →
     *       derivados ≠ vistos → conflicto (lo fija
     *       {@code ConcurrenciaOptimistaTest.eliminarIngresoLavaderoQueOtroDerivoDespuesDeLeer}).
     *       Si el borrado toma las salidas primero, el {@code UPDATE} CAS de {@code derivar} afecta
     *       0 filas → conflicto, y su {@code equipo_otros} se revierte con él.</li>
     *   <li><b>El borrado de un derivado desde Ver Equipos</b> ({@link EliminadorEquipoOtros#eliminar}:
     *       salidas → materiales → cabecera). Este borrado toma las mismas tres tablas en el mismo
     *       orden relativo: (5) y (6). Si Ver Equipos commitea primero, (5) ve
     *       {@code equipo_otros_id NULL} → derivados ≠ vistos → conflicto; si commitea éste, el
     *       {@code bloquear} de Ver Equipos no encuentra la cabecera → conflicto.</li>
     *   <li><b>Registrar Estado, entregas, {@code lanzarLote}, {@code finalizarLote} sobre el
     *       derivado</b> (materiales → cabecera): (6) toma materiales → cabecera, el mismo orden, y
     *       ninguno de ellos toca tablas de Lavadero. Un lote lanzado mientras (6) esperaba se ve
     *       en (8) → {@code DerivadoEnLoteEnCurso}.</li>
     * </ul>
     *
     * <h2>Casos residuales de deadlock, nombrados y aceptados</h2>
     * <p>En todos, MySQL detecta el ciclo y aborta a uno; si es el borrado, sale como
     * {@code CONFLICTO_ELIMINACION} ("volvé a intentar") y no queda nada escrito. (a) y (b) los vio
     * el plan; (c), (d) y (e) aparecieron al escribir esta matriz y el usuario los aceptó
     * (2026-09-29) antes que cambiar el orden.</p>
     * <ol type="a">
     *   <li><b>(16) contra el gap lock de {@code lanzarTanda}.</b> Una tanda que espera nuestras
     *       líneas ya tiene tomado el lock de {@code SQL_CICLO_ACTIVO_DE_LAVARROPAS} de su
     *       lavarropas. Si ese lock cubre el registro de índice del ciclo finalizado que (16) borra
     *       —porque es next-key sobre el primer ciclo finalizado, o porque el optimizador usó
     *       {@code idx_ciclos_lavarropas} ({@code V10}) en vez de {@code idx_ciclos_lavarropas_fin}
     *       ({@code V22}) y bloqueó toda la historia del lavarropas—, el borrado espera a la tanda
     *       y la tanda al borrado. La tanda que pierde sale como {@code CONFLICTO_GENERICO} y
     *       conserva el staging.</li>
     *   <li><b>{@code derivar} se cruza de frente</b>: salidas → ingreso contra ingreso → salidas,
     *       cuando la derivación completa nuestro ingreso y quiere pasarlo a {@code FINALIZADO}. Si
     *       pierde {@code derivar}, sale como {@code CONFLICTO_SALIDA} (traduce la contención igual
     *       que {@code marcarListo}).</li>
     *   <li><b>El orden dentro de una tabla.</b> InnoDB bloquea en el orden en que recorre el índice
     *       —(3) y (4) por línea, (5) por tanda y por instancia—, no en el del {@code ORDER BY id}.
     *       {@code marcarListo} toma tandas e instancias por id ascendente, el {@code bloquear} de
     *       Ver Equipos toma las salidas del derivado por id ascendente, y {@code volverALavado},
     *       {@code derivar}, {@code aplicarMovimientos} y {@code lanzarLote} toman sus filas en el
     *       orden de la lista que reciben. Con dos filas del mismo ingreso cuyo orden de id no es
     *       el del índice (una línea lavada en un ciclo posterior a otra), los dos pueden tomárselas
     *       cruzadas. Ordenarlas por id exigiría conocer los ids antes de bloquear, o sea una
     *       lectura no bloqueante antes de terminar la Fase A: justo lo que no se puede. El otro, si
     *       pierde, sale como lo traduzca su {@code catch}: {@code marcarListo},
     *       {@code volverALavado}, {@code derivar} y los dos {@code aplicarMovimientos} como
     *       conflicto; {@code lanzarLote}, todavía como error técnico (decisión de su javadoc).</li>
     *   <li><b>La fusión de clientes</b> ({@code FusionClientesDAO}: {@code equipo_otros} →
     *       {@code ingresos_lavadero}, al revés que acá) sobre el cliente de un ingreso con derivado
     *       mientras se lo borra. Si pierde la fusión, hoy sale como error técnico: no traduce la
     *       contención.</li>
     *   <li><b>El {@code NOT EXISTS} de (16)</b> toma locks compartidos sobre las tandas de los
     *       <em>otros</em> ingresos del mismo ciclo: choca con un {@code marcarListo} que tiene una
     *       de ellas y espera una nuestra, o con otro borrado que está en su (16) sobre el mismo
     *       ciclo. Se prefirió a decidir el vaciado con una lectura no bloqueante, que en esa misma
     *       carrera dejaría un ciclo vacío en Ver Ciclos.</li>
     * </ol>
     * <p>Se heredan además los de {@link EliminadorEquipoOtros#bloquear} sobre el derivado
     * (Correcciones, que toma cabecera → materiales).</p>
     *
     * @param estadoVisto     el estado del {@link ResumenIngresoLavadero} que confirmó el operador
     * @param derivadosVistos sus {@link ResumenIngresoLavadero#versionesDerivados()}: id → version
     * @throws ConflictoConcurrenciaException si el ingreso cambió de estado, ganó o perdió un
     *                                        derivado, o desapareció desde el resumen; o si la base
     *                                        cortó la espera de un lock
     * @throws EliminacionBloqueadaException  si hay un ciclo en curso, un derivado compartido o un
     *                                        derivado en un lote en curso; no se borró nada
     * @throws DatabaseException              ante cualquier otro error de base; no se borró nada
     */
    public void eliminar(int ingresoId, EstadoIngresoLavadero estadoVisto, Map<Integer, Integer> derivadosVistos,
                         String motivo, String puesto) {
        Objects.requireNonNull(estadoVisto, "estadoVisto");
        Objects.requireNonNull(derivadosVistos, "derivadosVistos");
        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            Connection conn = tx.get();

            Afectados afectados = bloquearTodo(conn, ingresoId, estadoVisto, derivadosVistos);  // Fase A

            List<Bloqueo> bloqueos = verificar(conn, afectados);                               // Fase B
            if (!bloqueos.isEmpty()) {
                throw new EliminacionBloqueadaException(bloqueos);
            }
            exigirVersionesVistas(afectados, derivadosVistos);

            archivarYBorrar(conn, afectados, motivo, puesto);                                  // Fase C
            tx.commit();
            log.info("Ingreso de lavadero {} eliminado y archivado desde {} ({} derivado(s) al CDE)",
                ingresoId, puesto, afectados.derivados().size());
        } catch (SQLException e) {
            if (ControlConcurrencia.esContencionDeLock(e)) {
                log.warn("Eliminación del ingreso de lavadero {} abortada por la base (contención de lock)",
                    ingresoId, e);
                throw new ConflictoConcurrenciaException(Mensajes.CONFLICTO_ELIMINACION, e);
            }
            log.error("Error al eliminar el ingreso de lavadero {}", ingresoId, e);
            throw new DatabaseException("Error al eliminar el ingreso de lavadero " + ingresoId, e);
        }
    }

    // ── Fase A ───────────────────────────────────────────────────────────────

    /** Pasos (1) a (6). Sólo {@code FOR UPDATE}: ninguna lectura común. */
    private Afectados bloquearTodo(Connection conn, int ingresoId, EstadoIngresoLavadero estadoVisto,
                                   Map<Integer, Integer> derivadosVistos) throws SQLException {
        String estadoBD = bloquearIngreso(conn, ingresoId);                                     // (1)
        if (estadoBD == null || EstadoIngresoLavadero.desdeBD(estadoBD) != estadoVisto) {
            throw new ConflictoConcurrenciaException(Mensajes.CONFLICTO_ELIMINACION);
        }
        List<Integer> lineas = ids(conn, SQL_BLOQUEAR_LINEAS, ingresoId);                      // (2)

        List<Integer> tandas = new ArrayList<>();                                              // (3)
        Set<Integer> ciclos = new TreeSet<>();
        if (!lineas.isEmpty()) {
            try (PreparedStatement ps = conLista(conn, SQL_BLOQUEAR_TANDAS, lineas);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tandas.add(rs.getInt("id"));
                    ciclos.add(rs.getInt("ciclo_id"));
                }
            }
        }
        List<Integer> instancias = ids(conn, SQL_BLOQUEAR_INSTANCIAS, lineas);                 // (4)

        Set<Integer> salidas = new TreeSet<>();                                                // (5)
        Set<Integer> idsDerivados = new TreeSet<>();
        bloquearSalidas(conn, SQL_BLOQUEAR_SALIDAS_DE_TANDAS, tandas, salidas, idsDerivados);
        bloquearSalidas(conn, SQL_BLOQUEAR_SALIDAS_DE_INSTANCIAS, instancias, salidas, idsDerivados);

        if (!idsDerivados.equals(derivadosVistos.keySet())) {                                  // (6)
            throw new ConflictoConcurrenciaException(Mensajes.CONFLICTO_ELIMINACION);
        }
        List<BloqueoEquipoOtros> derivados = new ArrayList<>();
        for (int equipoOtrosId : idsDerivados) {
            derivados.add(eliminadorOtros.bloquear(conn, equipoOtrosId)
                .orElseThrow(() -> new ConflictoConcurrenciaException(Mensajes.CONFLICTO_ELIMINACION)));
        }
        return new Afectados(ingresoId, estadoBD, lineas, tandas, ciclos, instancias, salidas, derivados);
    }

    /** @return el estado tal como está en la base, o {@code null} si el ingreso ya no existe */
    private static String bloquearIngreso(Connection conn, int ingresoId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_BLOQUEAR_INGRESO)) {
            ps.setInt(1, ingresoId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("estado") : null;
            }
        }
    }

    private static void bloquearSalidas(Connection conn, String sql, List<Integer> padres,
                                        Set<Integer> salidas, Set<Integer> idsDerivados) throws SQLException {
        if (padres.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = conLista(conn, sql, padres);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                salidas.add(rs.getInt("id"));
                int equipoOtrosId = rs.getInt("equipo_otros_id");
                if (!rs.wasNull()) {
                    idsDerivados.add(equipoOtrosId);
                }
            }
        }
    }

    // ── Fase B ───────────────────────────────────────────────────────────────

    /** Pasos (7) y (8). La primera sentencia de acá es la primera lectura no bloqueante. */
    private List<Bloqueo> verificar(Connection conn, Afectados afectados) throws SQLException {
        List<Bloqueo> bloqueos = new ArrayList<>();
        for (int numero : ids(conn, SQL_LAVARROPAS_EN_CURSO, afectados.ciclos())) {           // (7)
            bloqueos.add(new Bloqueo.CicloEnCurso(numero));
        }
        for (BloqueoEquipoOtros derivado : afectados.derivados()) {                            // (8)
            // bloquear() trajo TODAS las salidas que apuntan al derivado: las que no son de este
            // ingreso son de otro, y entonces el derivado es compartido.
            List<Integer> ajenas = derivado.salidaIds().stream()
                .filter(id -> !afectados.salidas().contains(id)).toList();
            if (!ajenas.isEmpty()) {
                bloqueos.add(new Bloqueo.DerivadoCompartido(derivado.equipoOtrosId(),
                    ids(conn, SQL_INGRESOS_DE_SALIDAS, ajenas)));
            }
            bloqueos.addAll(lotesDelDerivado(derivado.equipoOtrosId(), eliminadorOtros.verificar(conn, derivado)));
        }
        return bloqueos;
    }

    /**
     * Un {@code DerivadoEnLoteEnCurso} por cada {@link Bloqueo.LoteEnCurso} del derivado. El resumen
     * y la transacción arman los bloqueos de cada derivado igual —compartido primero, después sus
     * lotes—, para que el operador lea lo mismo venga de donde venga.
     */
    private static List<Bloqueo> lotesDelDerivado(int equipoOtrosId, List<? extends Bloqueo> lotes) {
        List<Bloqueo> bloqueos = new ArrayList<>();
        for (Bloqueo lote : lotes) {
            if (lote instanceof Bloqueo.LoteEnCurso l) {
                bloqueos.add(new Bloqueo.DerivadoEnLoteEnCurso(equipoOtrosId, l.loteIdNegocio()));
            }
        }
        return bloqueos;
    }

    /**
     * La {@code version} de cada derivado, tomada {@code FOR UPDATE} en (6), contra la que vio el
     * operador. Se compara <b>después</b> de los bloqueos, igual que en
     * {@code EliminadorEquipoOrtopedia.eliminar}: {@code lanzarLote} bumpea la {@code version}, y un
     * derivado que otro metió en un lote tiene que salir como "finalizá el lote", no como conflicto.
     * No cambia ningún lock: sólo compara lo que (6) ya leyó.
     */
    private static void exigirVersionesVistas(Afectados afectados, Map<Integer, Integer> derivadosVistos) {
        for (BloqueoEquipoOtros derivado : afectados.derivados()) {
            if (derivado.version() != derivadosVistos.get(derivado.equipoOtrosId())) {
                throw new ConflictoConcurrenciaException(Mensajes.CONFLICTO_ELIMINACION);
            }
        }
    }

    // ── Fase C ───────────────────────────────────────────────────────────────

    /** Pasos (10) a (16). */
    private void archivarYBorrar(Connection conn, Afectados afectados, String motivo, String puesto)
            throws SQLException {
        int archivoId = archivo.archivar(conn, copiaParaArchivo(conn, afectados, motivo, puesto)); // (10)
        for (BloqueoEquipoOtros derivado : afectados.derivados()) {                            // (11)
            eliminadorOtros.archivarYBorrar(conn, derivado.equipoOtrosId(), derivado.version(),
                motivo, puesto, archivoId);
        }
        borrarContando(conn, SQL_BORRAR_SALIDAS, afectados.salidas());                         // (12)
        borrarContando(conn, SQL_BORRAR_TANDAS, afectados.tandas());                           // (13)
        borrarContando(conn, SQL_BORRAR_INSTANCIAS, afectados.instancias());                   // (14)
        try (PreparedStatement ps = conn.prepareStatement(SQL_BORRAR_INGRESO)) {               // (15)
            ps.setInt(1, afectados.ingresoId());
            ps.setString(2, afectados.estadoBD());
            ControlConcurrencia.exigirFilaAfectada(ps.executeUpdate(), Mensajes.CONFLICTO_ELIMINACION);
        }
        if (!afectados.ciclos().isEmpty()) {                                                   // (16)
            try (PreparedStatement ps = conLista(conn, SQL_BORRAR_CICLOS_VACIOS, afectados.ciclos())) {
                ps.executeUpdate();
            }
        }
    }

    /** Guarda de conteo con {@code executeUpdate()}: se borra exactamente lo que se bloqueó. */
    private static void borrarContando(Connection conn, String sql, Collection<Integer> ids) throws SQLException {
        if (ids.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = conLista(conn, sql, ids)) {
            ControlConcurrencia.exigirFilasAfectadas(ids.size(), ps.executeUpdate(),
                Mensajes.CONFLICTO_ELIMINACION);
        }
    }

    /**
     * El árbol del ingreso, leído antes de borrar nada (las salidas conservan acá su
     * {@code equipo_otros_id}, que (11) pone en {@code NULL}): cabecera con el nombre del cliente,
     * bolsas, líneas con el nombre del elemento, tandas, los ciclos que las lavaron con su
     * configuración e insumos, instancias y salidas con destino y fechas.
     */
    private static IngresoArchivado copiaParaArchivo(Connection conn, Afectados afectados, String motivo,
                                                     String puesto) throws SQLException {
        int ingresoId = afectados.ingresoId();
        JSONObject raiz = SnapshotJson.raiz(ModuloIngreso.LAVADERO, FORMATO_SNAPSHOT);
        String clienteNombre;
        LocalDateTime fechaIngreso;
        try (PreparedStatement ps = conn.prepareStatement(SQL_CABECERA)) {
            ps.setInt(1, ingresoId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    // Imposible con el ingreso bloqueado; si pasa, que no quede un archivo vacío.
                    throw new SQLException("ingresos_lavadero " + ingresoId + " desapareció con la fila bloqueada");
                }
                raiz.put("ingreso", SnapshotJson.filaActual(rs));
                clienteNombre = rs.getString("cliente_nombre");
                fechaIngreso = aFecha(rs.getTimestamp("fecha_ingreso"));
            }
        }
        raiz.put("bolsas", filas(conn, SQL_SNAPSHOT_BOLSAS, ingresoId));
        raiz.put("lineas", filas(conn, SQL_SNAPSHOT_LINEAS, ingresoId));
        raiz.put("tandas", filas(conn, SQL_SNAPSHOT_TANDAS, afectados.tandas()));
        raiz.put("ciclos", filas(conn, SQL_SNAPSHOT_CICLOS, afectados.ciclos()));
        raiz.put("insumos_ciclos", filas(conn, SQL_SNAPSHOT_INSUMOS, afectados.ciclos()));
        raiz.put("instancias", filas(conn, SQL_SNAPSHOT_INSTANCIAS, afectados.instancias()));
        raiz.put("salidas", filas(conn, SQL_SNAPSHOT_SALIDAS, afectados.salidas()));
        return new IngresoArchivado(ModuloIngreso.LAVADERO, ingresoId, clienteNombre, fechaIngreso,
            EstadoIngresoLavadero.desdeBD(afectados.estadoBD()).name(), motivo, puesto, null,
            raiz.toString());
    }

    // ── Resumen ──────────────────────────────────────────────────────────────

    private static LecturaPropia leerPropio(Connection conn, int ingresoId) throws SQLException {
        Cabecera cabecera = leerCabecera(conn, ingresoId);
        List<LineaElemento> elementos = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SQL_ELEMENTOS)) {
            ps.setInt(1, ingresoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    elementos.add(new LineaElemento(rs.getString("nombre"), rs.getInt("cantidad")));
                }
            }
        }
        List<Integer> lavarropasEnCurso = ids(conn, SQL_LAVARROPAS_EN_CURSO_DEL_INGRESO, ingresoId);
        Set<Integer> derivados = new TreeSet<>();
        try (PreparedStatement ps = conn.prepareStatement(SQL_DERIVADOS_DEL_INGRESO)) {
            ps.setInt(1, ingresoId);
            ps.setInt(2, ingresoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    derivados.add(rs.getInt(1));
                }
            }
        }
        return new LecturaPropia(cabecera, elementos, lavarropasEnCurso, List.copyOf(derivados));
    }

    private static Cabecera leerCabecera(Connection conn, int ingresoId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_CABECERA)) {
            ps.setInt(1, ingresoId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ResourceNotFoundException(Mensajes.INGRESO_YA_ELIMINADO);
                }
                return new Cabecera(rs.getString("cliente_nombre"), aFecha(rs.getTimestamp("fecha_ingreso")),
                    EstadoIngresoLavadero.desdeBD(rs.getString("estado")), rs.getBigDecimal("peso_total_kg"));
            }
        }
    }

    private static int unidades(ResumenEquipo derivado) {
        return derivado.materiales().stream().mapToInt(ResumenEquipo.LineaMaterial::cantidad).sum();
    }

    // ── JDBC ─────────────────────────────────────────────────────────────────

    /** La primera columna de cada fila, con un único parámetro. */
    private static List<Integer> ids(Connection conn, String sql, int parametro) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, parametro);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        }
        return ids;
    }

    /** La primera columna de cada fila, con la lista en el {@code %s}. Vacía no consulta. */
    private static List<Integer> ids(Connection conn, String sql, Collection<Integer> lista) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        if (lista.isEmpty()) {
            return ids;
        }
        try (PreparedStatement ps = conLista(conn, sql, lista);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getInt(1));
            }
        }
        return ids;
    }

    private static JSONArray filas(Connection conn, String sql, int parametro) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, parametro);
            try (ResultSet rs = ps.executeQuery()) {
                return SnapshotJson.filas(rs);
            }
        }
    }

    private static JSONArray filas(Connection conn, String sql, Collection<Integer> lista) throws SQLException {
        if (lista.isEmpty()) {
            return new JSONArray();
        }
        try (PreparedStatement ps = conLista(conn, sql, lista);
             ResultSet rs = ps.executeQuery()) {
            return SnapshotJson.filas(rs);
        }
    }

    /**
     * Prepara {@code plantilla} con un {@code ?} por id en el {@code %s}, en el orden de la
     * colección. Quien llama no la usa con la lista vacía: un {@code IN ()} no es SQL válido.
     */
    private static PreparedStatement conLista(Connection conn, String plantilla, Collection<Integer> ids)
            throws SQLException {
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("Un IN () vacío no es SQL válido: " + plantilla);
        }
        PreparedStatement ps = conn.prepareStatement(
            String.format(plantilla, String.join(", ", Collections.nCopies(ids.size(), "?"))));
        try {
            int i = 1;
            for (int id : ids) {
                ps.setInt(i++, id);
            }
            return ps;
        } catch (SQLException e) {
            ps.close();
            throw e;
        }
    }

    private static LocalDateTime aFecha(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    // ── Tipos internos ───────────────────────────────────────────────────────

    /**
     * Lo que la Fase A dejó tomado {@code FOR UPDATE}. Todo se leyó con locks, así que es lo último
     * commiteado, no una vista vieja.
     *
     * @param estadoBD   el estado tal como está en la base: es el valor del CAS de (15)
     * @param salidas    las de las tandas y las instancias de este ingreso
     * @param derivados  lo que {@link EliminadorEquipoOtros#bloquear} tomó de cada derivado, en orden
     *                   ascendente
     */
    private record Afectados(int ingresoId, String estadoBD, List<Integer> lineas, List<Integer> tandas,
                             Set<Integer> ciclos, List<Integer> instancias, Set<Integer> salidas,
                             List<BloqueoEquipoOtros> derivados) {}

    private record Cabecera(String clienteNombre, LocalDateTime fechaIngreso, EstadoIngresoLavadero estado,
                            BigDecimal pesoTotalKg) {}

    /** Lo que el resumen lee con su propia conexión, antes de pedir el de cada derivado. */
    private record LecturaPropia(Cabecera cabecera, List<LineaElemento> elementos,
                                 List<Integer> lavarropasEnCurso, List<Integer> derivados) {}
}
