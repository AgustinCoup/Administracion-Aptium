package com.example.features.lavadero.dao;

import com.example.common.constants.Constantes;
import com.example.common.dao.ControlConcurrencia;
import com.example.common.exception.ConflictoConcurrenciaException;
import com.example.common.exception.DatabaseException;
import com.example.common.exception.LavarropasOcupadoException;
import com.example.common.exception.SaldoConsumidoException;
import com.example.features.lavadero.dao.helpers.LineaSobregirada;
import com.example.features.lavadero.model.CicloLavadero;
import com.example.features.lavadero.model.ConfiguracionCiclo;
import com.example.features.lavadero.model.ElementoCicloItem;
import com.example.features.lavadero.model.ElementoCicloMovimiento;
import com.example.features.lavadero.model.EstadoIngresoLavadero;
import com.example.features.lavadero.model.JabonCatalogo;
import com.example.features.lavadero.model.LanzamientoCiclo;
import com.example.features.lavadero.model.LineaLanzamiento;
import com.example.features.lavadero.model.TipoLavado;
import com.example.infrastructure.db.ConnectionPool;
import com.example.infrastructure.db.TransactionalConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public class CicloLavaderoDAO {

    private static final Logger log = LoggerFactory.getLogger(CicloLavaderoDAO.class);

    private static final String SQL_INSERTAR_CICLO =
        "INSERT INTO ciclos_lavadero (lavarropas_numero, jabon_id, litros_jabon, suavizante, potenciador, litros_totales, tipo_lavado, fecha_inicio, estado) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), 'ACTIVO')";

    private static final String SQL_INSERTAR_ELEMENTO =
        "INSERT INTO elementos_ciclo_lavadero (ciclo_id, elemento_clasificacion_id, cantidad, instancia_equipo_id) VALUES (?, ?, ?, ?)";

    private static final String SQL_INSERTAR_INSTANCIA =
        "INSERT INTO instancias_equipo_ciclo (elemento_clasificacion_id, total_partes) VALUES (?, ?)";

    private static final String SQL_ACTIVOS =
        "SELECT cl.id, cl.lavarropas_numero, cl.tipo_lavado, cl.jabon_id, cj.nombre AS jabon_nombre, " +
        "       cl.litros_jabon, cl.suavizante, cl.potenciador, cl.litros_totales, cl.fecha_inicio " +
        "FROM ciclos_lavadero cl " +
        "JOIN catalogo_jabones cj ON cj.id = cl.jabon_id " +
        "WHERE cl.fecha_fin IS NULL";

    private static final String SQL_FINALIZADOS =
        "SELECT cl.id, cl.lavarropas_numero, cl.tipo_lavado, cl.jabon_id, cj.nombre AS jabon_nombre, " +
        "       cl.litros_jabon, cl.suavizante, cl.potenciador, cl.litros_totales, " +
        "       cl.fecha_inicio, cl.fecha_fin " +
        "FROM ciclos_lavadero cl " +
        "JOIN catalogo_jabones cj ON cj.id = cl.jabon_id " +
        "WHERE cl.fecha_fin IS NOT NULL ORDER BY cl.fecha_fin DESC";

    private static final String SQL_TODOS =
        "SELECT cl.id, cl.lavarropas_numero, cl.tipo_lavado, cl.jabon_id, cj.nombre AS jabon_nombre, " +
        "       cl.litros_jabon, cl.suavizante, cl.potenciador, cl.litros_totales, " +
        "       cl.fecha_inicio, cl.fecha_fin " +
        "FROM ciclos_lavadero cl " +
        "JOIN catalogo_jabones cj ON cj.id = cl.jabon_id " +
        "ORDER BY CASE WHEN cl.fecha_fin IS NULL THEN 0 ELSE 1 END, cl.fecha_fin DESC";

    private static final String SQL_ELEMENTOS_DE_CICLO =
        "SELECT ecl.id, ecl.ingreso_id, cel.nombre, ecl.cantidad, " +
        "       eci.cantidad AS en_ciclo, c.nombre AS cliente, cel.categoria " +
        "FROM elementos_ciclo_lavadero eci " +
        "JOIN elementos_clasificacion_lavadero ecl ON ecl.id = eci.elemento_clasificacion_id " +
        "JOIN catalogo_elementos_lavadero cel       ON cel.id = ecl.elemento_id " +
        "JOIN ingresos_lavadero il                  ON il.id  = ecl.ingreso_id " +
        "JOIN clientes c                            ON c.id   = il.cliente_id " +
        "WHERE eci.ciclo_id = ? ORDER BY cel.nombre";

    private static final String SQL_DISPONIBLES =
        "SELECT ecl.id, ecl.ingreso_id, cel.nombre, ecl.cantidad, " +
        "       COALESCE(SUM(CASE WHEN eci.instancia_equipo_id IS NULL THEN eci.cantidad ELSE 0 END), 0) " +
        "         + COUNT(DISTINCT eci.instancia_equipo_id) AS ya_procesada, " +
        "       c.nombre AS cliente, cel.categoria " +
        "FROM elementos_clasificacion_lavadero ecl " +
        "JOIN catalogo_elementos_lavadero cel ON cel.id = ecl.elemento_id " +
        "JOIN ingresos_lavadero il            ON il.id  = ecl.ingreso_id " +
        "JOIN clientes c                      ON c.id   = il.cliente_id " +
        "LEFT JOIN elementos_ciclo_lavadero eci ON eci.elemento_clasificacion_id = ecl.id " +
        "WHERE il.estado = '" + EstadoIngresoLavadero.CLASIFICADO + "' " +
        "GROUP BY ecl.id, ecl.ingreso_id, cel.nombre, ecl.cantidad, c.nombre, cel.categoria " +
        "HAVING ya_procesada < ecl.cantidad " +
        "ORDER BY il.id, cel.nombre";

    /**
     * Bloqueo de la línea de clasificación antes de leer su saldo.
     *
     * <p>Releer el saldo sin bloquear la línea no alcanza: dos tandas que abren su transacción a
     * la vez leerían las dos el mismo saldo, las dos lo encontrarían suficiente y las dos
     * escribirían. El {@code FOR UPDATE} serializa las tandas que compiten por la misma línea.</p>
     *
     * <p><b>Se toman TODOS los bloqueos antes de leer el primer saldo</b>, no de a una línea por
     * vez. En {@code REPEATABLE READ} (el default de MySQL) la vista de lectura de la transacción
     * se fija en la <b>primera lectura no bloqueante</b>: si se intercalara bloqueo-lectura línea
     * por línea, el saldo de la segunda línea se leería con la vista fijada antes de tomar su
     * bloqueo, y una tanda que hubiera commiteado mientras tanto quedaría invisible — la guarda
     * miraría un saldo viejo y dejaría sobregirar. Tomando todos los bloqueos primero, cualquier
     * tanda que compita por estas líneas ya commiteó (y por eso soltó el bloqueo) o está frenada
     * detrás nuestro. <b>H2 no lo delata</b>: corre en {@code READ COMMITTED}, donde cada
     * sentencia ve un snapshot fresco, así que este orden hay que sostenerlo por razonamiento.</p>
     *
     * <p>Las líneas se toman en orden ascendente de id (ver {@link #consumoPorLinea}) para que
     * dos tandas que comparten más de una línea no puedan tomárselas cruzadas y trabarse.</p>
     */
    private static final String SQL_BLOQUEAR_LINEA =
        "SELECT id FROM elementos_clasificacion_lavadero WHERE id = ? FOR UPDATE";

    /**
     * Ciclo sin finalizar de un lavarropas, bloqueado antes de crear nada.
     *
     * <p>Un lavarropas no puede tener dos ciclos {@code ACTIVO} a la vez, y hasta acá nadie lo
     * exigía: {@link #obtenerCiclosActivosPorLavarropas()} los mete en un mapa por número, así que
     * de los dos la pantalla sólo puede mostrar uno — el otro queda <b>invisible y sin forma de
     * finalizarse</b>, y la ropa que se llevó no vuelve a aparecer ni en Disponibles ni en
     * Salidas. La pantalla decide qué cards están libres con el snapshot del último refresco; entre
     * ese refresco y el botón "Lanzar" el lavarropas pudo ocuparse.</p>
     *
     * <p>El {@code FOR UPDATE} no es decorativo ni redundante con la lectura: MySQL toma también
     * el hueco del rango, y ese hueco es incompatible con el {@code INSERT} de otra transacción.
     * Va antes que {@link #SQL_BLOQUEAR_LINEA} para que el orden entre las dos tablas sea siempre
     * el mismo.</p>
     *
     * <p><b>No serializa en el caso perfectamente simultáneo</b>, y conviene saberlo antes de
     * apoyarse en esto: dos gap locks <em>entre sí</em> son compatibles, así que dos lanzamientos
     * que lean a la vez pasan los dos la guarda y chocan recién en el {@code INSERT}, donde cada
     * uno espera el hueco del otro. MySQL corta ese abrazo abortando a uno. <b>El invariante se
     * mantiene igual</b> —nunca quedan dos ciclos abiertos—; lo que cambia es por dónde sale el
     * fallo, y por eso {@link #lanzarTanda} traduce el rollback a un choque en vez de dejarlo
     * salir como error de base. H2 no puede reproducirlo: corre en {@code READ COMMITTED} y no
     * toma gap locks.</p>
     *
     * <p><b>Los dos predicados tienen que estar en el índice</b>, y por eso existe
     * {@code idx_ciclos_lavarropas_fin (lavarropas_numero, fecha_fin)} (V22). Con el índice de V10
     * —{@code lavarropas_numero} solo— el {@code FOR UPDATE} bloqueaba en exclusiva <b>todas</b>
     * las filas históricas de ese lavarropas para encontrar la única que puede estar abierta: la
     * guarda era correcta y el bloqueo, desproporcionado y creciente con la historia.</p>
     */
    private static final String SQL_CICLO_ACTIVO_DE_LAVARROPAS =
        "SELECT id FROM ciclos_lavadero WHERE lavarropas_numero = ? AND fecha_fin IS NULL FOR UPDATE";

    /**
     * Saldo todavía disponible de una línea de clasificación, releído dentro de la transacción
     * del lanzamiento.
     *
     * <p><b>Deriva de {@link #SQL_DISPONIBLES}</b> y usa su misma aritmética: lo procesado son
     * las unidades regulares más <em>una</em> por instancia de equipo repartido, no una por
     * fracción (decisión C del blueprint de fracciones de equipo). Escribir acá una fórmula
     * propia haría que el bloqueo optimista rechace tandas legítimas en cuanto las dos
     * divergieran. Lo que sí se saca es el filtro por estado del ingreso: acá la línea se
     * identifica por id, no se está armando la lista de lo que se puede lavar.</p>
     */
    private static final String SQL_SALDO_DE_LINEA =
        "SELECT ecl.cantidad " +
        "     - (COALESCE(SUM(CASE WHEN eci.instancia_equipo_id IS NULL THEN eci.cantidad ELSE 0 END), 0) " +
        "        + COUNT(DISTINCT eci.instancia_equipo_id)) AS saldo " +
        "FROM elementos_clasificacion_lavadero ecl " +
        "LEFT JOIN elementos_ciclo_lavadero eci ON eci.elemento_clasificacion_id = ecl.id " +
        "WHERE ecl.id = ? " +
        "GROUP BY ecl.id, ecl.cantidad";

    /**
     * Detecta líneas de clasificación cuyo total procesado supera la cantidad clasificada
     * (decisión D del blueprint de fracciones de equipo). Misma fórmula que
     * {@link #SQL_DISPONIBLES}, pero sin el filtro {@code il.estado = 'CLASIFICADO'}: el dato
     * sucio puede estar en una línea ya avanzada a LAVADO/FINALIZADO. Sólo detección — no repara.
     */
    private static final String SQL_LINEAS_SOBREGIRADAS =
        "SELECT ecl.id, ecl.ingreso_id, cel.nombre, ecl.cantidad, " +
        "       COALESCE(SUM(CASE WHEN eci.instancia_equipo_id IS NULL THEN eci.cantidad ELSE 0 END), 0) " +
        "         + COUNT(DISTINCT eci.instancia_equipo_id) AS ya_procesada " +
        "FROM elementos_clasificacion_lavadero ecl " +
        "JOIN catalogo_elementos_lavadero cel ON cel.id = ecl.elemento_id " +
        "LEFT JOIN elementos_ciclo_lavadero eci ON eci.elemento_clasificacion_id = ecl.id " +
        "GROUP BY ecl.id, ecl.ingreso_id, cel.nombre, ecl.cantidad " +
        "HAVING ya_procesada > ecl.cantidad " +
        "ORDER BY ecl.ingreso_id, cel.nombre";

    public Map<Integer, CicloLavadero> obtenerCiclosActivosPorLavarropas() {
        Map<Integer, CicloLavadero> mapa = new LinkedHashMap<>();
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_ACTIVOS);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                CicloLavadero ciclo = mapearCiclo(rs);
                mapa.put(ciclo.getLavarropasNumero(), ciclo);
            }
        } catch (SQLException e) {
            log.error("Error al obtener ciclos activos", e);
        }
        return mapa;
    }

    public List<CicloLavadero> obtenerCiclosFinalizados() {
        List<CicloLavadero> lista = new ArrayList<>();
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FINALIZADOS);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearCicloCompleto(rs));
            }
        } catch (SQLException e) {
            log.error("Error al obtener ciclos finalizados", e);
        }
        return lista;
    }

    public List<CicloLavadero> obtenerTodosLosCiclos() {
        List<CicloLavadero> lista = new ArrayList<>();
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_TODOS);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearCicloCompleto(rs));
            }

        } catch (SQLException e) {
            throw new DatabaseException("Error al obtener todos los ciclos", e);
        }
        return lista;
    }

    public List<ElementoCicloItem> obtenerElementosDisponiblesParaCiclo() {
        List<ElementoCicloItem> lista = new ArrayList<>();
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_DISPONIBLES);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(new ElementoCicloItem(
                    rs.getInt("id"),
                    rs.getInt("ingreso_id"),
                    rs.getString("nombre"),
                    rs.getInt("cantidad"),
                    rs.getInt("ya_procesada"),
                    rs.getString("cliente"),
                    rs.getString("categoria")
                ));
            }
        } catch (SQLException e) {
            log.error("Error al obtener elementos disponibles", e);
        }
        return lista;
    }

    public List<LineaSobregirada> detectarLineasSobregiradas() {
        List<LineaSobregirada> lista = new ArrayList<>();
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_LINEAS_SOBREGIRADAS);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(new LineaSobregirada(
                    rs.getInt("id"),
                    rs.getInt("ingreso_id"),
                    rs.getString("nombre"),
                    rs.getInt("cantidad"),
                    rs.getInt("ya_procesada")
                ));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error al detectar líneas de clasificación sobregiradas", e);
        }
        return lista;
    }

    /**
     * Lanza una tanda entera —las instancias de equipo repartido y todos los ciclos que las
     * consumen— en <b>una sola transacción</b>.
     *
     * <p>Ese alcance es el que hace consistente al reparto. Con una transacción por lavarropas,
     * un fallo a mitad de tanda dejaba una instancia con {@code total_partes = N} y menos de N
     * fracciones: {@code SQL_DISPONIBLES} ya la contaba como consumida (cuenta instancias
     * distintas, no fracciones) mientras que Salidas nunca la aceptaba como completa, así que el
     * equipo desaparecía de las dos pantallas y el ingreso no podía llegar a FINALIZADO.
     * Reintentar tampoco servía: acuñaba una segunda instancia para el mismo equipo.
     *
     * <p>El saldo de las líneas de clasificación que la tanda consume se <b>relee dentro de la
     * transacción</b> antes de escribir nada: la pantalla lo calculó con {@link #SQL_DISPONIBLES}
     * y entre esa lectura y el botón "Lanzar" otro operador puede haberse llevado la misma ropa.
     * Sin la relectura, las dos tandas sobregiran la línea sin que nada falle — la basura que
     * {@link #detectarLineasSobregiradas()} sale a buscar a posteriori. Un solo saldo insuficiente
     * tira la tanda entera, por el mismo motivo que la hace atómica.</p>
     *
     * <p>Lo mismo vale para el destino: ningún lavarropas de la tanda puede tener ya un ciclo sin
     * finalizar (ver {@link #SQL_CICLO_ACTIVO_DE_LAVARROPAS}). Las dos guardas se toman antes de
     * escribir nada y en orden fijo entre sus tablas.</p>
     */
    public void lanzarTanda(List<LanzamientoCiclo> tanda) {
        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            Connection conn = tx.get();
            exigirLavarropasLibres(conn, tanda);
            exigirSaldoSuficiente(conn, tanda);
            Map<Integer, Integer> instancias = crearInstancias(conn, tanda);
            for (LanzamientoCiclo ciclo : tanda) {
                int cicloId = insertarCiclo(conn, ciclo.lavarropasNumero(), ciclo.config());
                insertarMovimientos(conn, cicloId, movimientosDe(ciclo, instancias));
            }
            tx.commit();
        } catch (SQLException e) {
            // La base abortó la transacción porque otra tanda le tenía trabadas las mismas líneas
            // o el mismo lavarropas (ver ControlConcurrencia.esContencionDeLock: no alcanza con
            // el tipo de la excepción). Es un choque, no una falla técnica, y sale como tal para
            // que el operador lea "alguien se te adelantó" y no "error al lanzar los ciclos".
            //
            // Va el mensaje GENÉRICO y no CONFLICTO_TANDA, y el tipo base y no SaldoConsumido:
            // acá no se sabe cuál de los dos recursos se trabó, y las dos cosas que
            // CONFLICTO_TANDA afirma serían inventadas. Nada quedó escrito, así que el staging
            // sigue siendo válido: se conserva, la pantalla se recarga y volver a apretar Lanzar
            // da el choque preciso si todavía hay uno.
            if (ControlConcurrencia.esContencionDeLock(e)) {
                log.warn("Tanda de {} ciclo(s) abortada por la base (contención de lock)",
                    tanda.size(), e);
                throw new ConflictoConcurrenciaException(Constantes.Mensajes.CONFLICTO_GENERICO);
            }
            log.error("Error al lanzar una tanda de {} ciclo(s)", tanda.size(), e);
            throw new DatabaseException("Error al lanzar los ciclos", e);
        }
    }

    public void finalizarCiclo(int cicloId) {
        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            Connection conn = tx.get();
            marcarFinalizado(conn, cicloId);
            Set<Integer> ingresoIds = obtenerIngresosAfectados(conn, cicloId);
            actualizarEstadoIngresosAfectados(conn, ingresoIds);
            tx.commit();
        } catch (SQLException e) {
            log.error("Error al finalizar ciclo {}", cicloId, e);
            throw new RuntimeException("Error al finalizar ciclo", e);
        }
    }

    // ── privados ─────────────────────────────────────────────────────────────

    private int insertarCiclo(Connection conn, int lavarropasNumero,
                               ConfiguracionCiclo config) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERTAR_CICLO, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, lavarropasNumero);
            ps.setInt(2, config.jabon().getId());
            ps.setBigDecimal(3, config.litrosJabon());
            ps.setBoolean(4, config.suavizante());
            ps.setBoolean(5, config.potenciador());
            if (config.litrosTotales() != null) ps.setBigDecimal(6, config.litrosTotales());
            else ps.setNull(6, Types.DECIMAL);
            ps.setString(7, config.tipoLavado().name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    private void insertarMovimientos(Connection conn, int cicloId,
                                      List<ElementoCicloMovimiento> movimientos) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERTAR_ELEMENTO)) {
            for (ElementoCicloMovimiento m : movimientos) {
                ps.setInt(1, cicloId);
                ps.setInt(2, m.getElementoClasificacionId());
                ps.setInt(3, m.getCantidad());
                if (m.getInstanciaEquipoId() != null) ps.setInt(4, m.getInstanciaEquipoId());
                else ps.setNull(4, Types.INTEGER);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * Rechaza la tanda entera si alguna línea de clasificación ya no tiene saldo para lo que la
     * tanda pretende consumir.
     *
     * <p>El chequeo y el consumo comparten conexión y transacción, así que <b>dos líneas de la
     * misma tanda que apuntan a la misma clasificación se ven entre sí</b>: es el mismo
     * razonamiento del javadoc de {@code SalidaLavaderoDAO.marcarListo}, resuelto de otra forma.
     * Ahí las marcas se intercalan chequeo-escritura porque llegan de a una; acá la tanda se
     * conoce entera de antemano, así que su consumo se agrega por línea
     * ({@link #consumoPorLinea}) y se compara de una sola vez contra el saldo. El efecto es el
     * mismo — dos líneas de la misma tanda no pueden sobregirar juntas — sin depender de que el
     * orden de escritura las cruce.</p>
     *
     * <p>Las dos pasadas —bloquear todo, después leer todo— no son estilo: son la condición para
     * que el saldo de la segunda línea no se lea con una vista anterior a su propio bloqueo. Ver
     * {@link #SQL_BLOQUEAR_LINEA}.</p>
     */
    private void exigirSaldoSuficiente(Connection conn, List<LanzamientoCiclo> tanda) throws SQLException {
        Map<Integer, Integer> consumo = consumoPorLinea(tanda);
        try (PreparedStatement psBloquear = conn.prepareStatement(SQL_BLOQUEAR_LINEA);
             PreparedStatement psSaldo    = conn.prepareStatement(SQL_SALDO_DE_LINEA)) {
            for (Integer lineaId : consumo.keySet()) {
                bloquearLinea(psBloquear, lineaId);
            }
            for (Map.Entry<Integer, Integer> linea : consumo.entrySet()) {
                int saldo = saldoDeLinea(psSaldo, linea.getKey());
                if (linea.getValue() > saldo) {
                    log.warn("Tanda rechazada: la línea de clasificación {} tiene saldo {} y la "
                        + "tanda pretende consumir {}", linea.getKey(), saldo, linea.getValue());
                    throw new SaldoConsumidoException(Constantes.Mensajes.CONFLICTO_TANDA);
                }
            }
        }
    }

    /**
     * Rechaza la tanda entera si alguno de sus lavarropas ya tiene un ciclo sin finalizar.
     *
     * <p>Los números se recorren en orden ascendente por el mismo motivo que las líneas de
     * clasificación: que dos tandas que comparten lavarropas no se los tomen cruzados. Ver
     * {@link #SQL_CICLO_ACTIVO_DE_LAVARROPAS} para por qué la lectura va bloqueante.</p>
     */
    private void exigirLavarropasLibres(Connection conn, List<LanzamientoCiclo> tanda) throws SQLException {
        List<Integer> numeros = tanda.stream()
            .map(LanzamientoCiclo::lavarropasNumero).distinct().sorted().toList();
        try (PreparedStatement ps = conn.prepareStatement(SQL_CICLO_ACTIVO_DE_LAVARROPAS)) {
            for (int numero : numeros) {
                ps.setInt(1, numero);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        log.warn("Tanda rechazada: el lavarropas {} ya tiene el ciclo {} sin "
                            + "finalizar", numero, rs.getInt("id"));
                        throw new LavarropasOcupadoException(
                            Constantes.Mensajes.CONFLICTO_LAVARROPAS_OCUPADO);
                    }
                }
            }
        }
    }

    /**
     * Cuánto consume la tanda de cada línea de clasificación, con la misma aritmética que
     * {@link #SQL_DISPONIBLES} descuenta: las líneas regulares suman su cantidad y cada instancia
     * de equipo repartido cuenta <b>1</b>, esté en uno o en N lavarropas de la tanda.
     *
     * <p>{@link TreeMap} y no un {@code HashMap}: recorrer las líneas siempre en el mismo orden
     * (id ascendente) es lo que evita que dos tandas que comparten líneas se traben tomándoselas
     * cruzadas (ver {@link #SQL_BLOQUEAR_LINEA}).</p>
     */
    private static Map<Integer, Integer> consumoPorLinea(List<LanzamientoCiclo> tanda) {
        Map<Integer, Integer> consumo = new TreeMap<>();
        Map<Integer, Set<Integer>> instanciasPorLinea = new TreeMap<>();
        for (LanzamientoCiclo ciclo : tanda) {
            for (LineaLanzamiento linea : ciclo.lineas()) {
                int lineaId = linea.elementoClasificacionId();
                if (linea.esFraccionDeEquipo()) {
                    instanciasPorLinea.computeIfAbsent(lineaId, k -> new HashSet<>())
                        .add(linea.instanciaStagingId());
                } else {
                    consumo.merge(lineaId, linea.cantidad(), Integer::sum);
                }
            }
        }
        instanciasPorLinea.forEach((lineaId, ids) -> consumo.merge(lineaId, ids.size(), Integer::sum));
        return consumo;
    }

    private void bloquearLinea(PreparedStatement psBloquear, int elementoClasificacionId) throws SQLException {
        psBloquear.setInt(1, elementoClasificacionId);
        try (ResultSet rs = psBloquear.executeQuery()) {
            rs.next();   // sin fila, la línea dejó de existir: saldoDeLinea devuelve 0 y choca
        }
    }

    /** Saldo actual de la línea. Sin fila (línea inexistente) es saldo cero. */
    private int saldoDeLinea(PreparedStatement psSaldo, int elementoClasificacionId) throws SQLException {
        psSaldo.setInt(1, elementoClasificacionId);
        try (ResultSet rs = psSaldo.executeQuery()) {
            return rs.next() ? rs.getInt("saldo") : 0;
        }
    }

    /**
     * {@code id de staging → id en la base}. Una sola instancia por id de staging, aunque sus
     * fracciones estén repartidas entre varios lavarropas de la tanda.
     */
    private Map<Integer, Integer> crearInstancias(Connection conn,
                                                   List<LanzamientoCiclo> tanda) throws SQLException {
        Map<Integer, Integer> instancias = new HashMap<>();
        for (LanzamientoCiclo ciclo : tanda) {
            for (LineaLanzamiento linea : ciclo.lineas()) {
                if (!linea.esFraccionDeEquipo()
                        || instancias.containsKey(linea.instanciaStagingId())) continue;
                instancias.put(linea.instanciaStagingId(),
                    insertarInstancia(conn, linea.elementoClasificacionId(), linea.totalPartes()));
            }
        }
        return instancias;
    }

    private int insertarInstancia(Connection conn, int elementoClasificacionId,
                                   int totalPartes) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERTAR_INSTANCIA, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, elementoClasificacionId);
            ps.setInt(2, totalPartes);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    /** Cambia los ids de instancia de staging por los que quedaron en la base. */
    private static List<ElementoCicloMovimiento> movimientosDe(LanzamientoCiclo ciclo,
                                                               Map<Integer, Integer> instancias) {
        List<ElementoCicloMovimiento> movimientos = new ArrayList<>();
        for (LineaLanzamiento linea : ciclo.lineas()) {
            movimientos.add(new ElementoCicloMovimiento(
                linea.elementoClasificacionId(), linea.cantidad(),
                linea.esFraccionDeEquipo() ? instancias.get(linea.instanciaStagingId()) : null));
        }
        return movimientos;
    }

    /**
     * Cierra el ciclo. El {@code AND fecha_fin IS NULL} es la guarda, no un filtro: de esa fecha
     * cuelgan Salidas ({@code cl.fecha_fin IS NOT NULL} es lo único que dice "esto está lavado") y
     * toda la trazabilidad del Historial, así que un segundo "Finalizar" sobre el mismo ciclo no
     * puede pisar la fecha real con la de ahora.
     *
     * <p>A diferencia de {@code SalidaLavaderoDAO.SQL_FINALIZAR_INGRESO} —que es la excepción
     * aceptada porque no escribe ninguna fecha y llegar a FINALIZADO es el resultado buscado—, acá
     * 0 filas sí es un choque: se lanza y la transacción entera se revierte, incluida la
     * actualización de estado de los ingresos afectados.</p>
     */
    private static final String SQL_MARCAR_FINALIZADO =
        "UPDATE ciclos_lavadero SET fecha_fin = NOW(), estado = 'FINALIZADO' " +
        "WHERE id = ? AND fecha_fin IS NULL";

    private void marcarFinalizado(Connection conn, int cicloId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_MARCAR_FINALIZADO)) {
            ps.setInt(1, cicloId);
            ControlConcurrencia.exigirFilaAfectada(ps.executeUpdate(),
                Constantes.Mensajes.CONFLICTO_CICLO_FINALIZADO);
        }
    }

    private Set<Integer> obtenerIngresosAfectados(Connection conn, int cicloId) throws SQLException {
        String sql = "SELECT DISTINCT ecl.ingreso_id " +
                     "FROM elementos_ciclo_lavadero eci " +
                     "JOIN elementos_clasificacion_lavadero ecl ON ecl.id = eci.elemento_clasificacion_id " +
                     "WHERE eci.ciclo_id = ?";
        Set<Integer> ids = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cicloId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getInt(1));
            }
        }
        return ids;
    }

    /**
     * Cuánto clasificó un ingreso y cuánto de eso ya entró a algún ciclo.
     *
     * <p>Los dos términos se agregan en subconsultas independientes y no con un {@code JOIN}
     * entre las dos tablas: un {@code LEFT JOIN} repite la línea de clasificación una vez por
     * fila de ciclo, así que {@code SUM(ecl.cantidad)} contaba el total tantas veces como
     * ciclos hubiera y una línea lavada en dos tandas nunca alcanzaba su propio total. Mismo
     * patrón que {@code SalidaLavaderoDAO.SQL_TOTAL_Y_DERIVADO_DEL_INGRESO}.</p>
     *
     * <p>Lo procesado usa la fórmula de la decisión C del blueprint de fracciones de equipo (la
     * misma de {@link #SQL_DISPONIBLES}): las N fracciones de un equipo repartido consumen 1
     * unidad de su línea, no N. Sin ella, sacar el fan-out haría pasar a LAVADO un ingreso que
     * todavía tiene equipos sin lavar.</p>
     */
    private static final String SQL_TOTAL_Y_PROCESADO_DEL_INGRESO =
        "SELECT (SELECT COALESCE(SUM(ecl.cantidad), 0) " +
        "          FROM elementos_clasificacion_lavadero ecl " +
        "         WHERE ecl.ingreso_id = ?)                                        AS total, " +
        "       (SELECT COALESCE(SUM(CASE WHEN eci.instancia_equipo_id IS NULL " +
        "                                 THEN eci.cantidad ELSE 0 END), 0) " +
        "             + COUNT(DISTINCT eci.instancia_equipo_id) " +
        "          FROM elementos_ciclo_lavadero eci " +
        "          JOIN elementos_clasificacion_lavadero ecl2 ON ecl2.id = eci.elemento_clasificacion_id " +
        "         WHERE ecl2.ingreso_id = ?)                                       AS procesado";

    private static final String SQL_MARCAR_LAVADO =
        "UPDATE ingresos_lavadero SET estado = '" + EstadoIngresoLavadero.LAVADO + "' WHERE id = ?";

    private void actualizarEstadoIngresosAfectados(Connection conn, Set<Integer> ingresoIds) throws SQLException {
        if (ingresoIds.isEmpty()) return;
        try (PreparedStatement psVerificar = conn.prepareStatement(SQL_TOTAL_Y_PROCESADO_DEL_INGRESO);
             PreparedStatement psMarcar   = conn.prepareStatement(SQL_MARCAR_LAVADO)) {
            for (int ingresoId : ingresoIds) {
                psVerificar.setInt(1, ingresoId);
                psVerificar.setInt(2, ingresoId);
                try (ResultSet rs = psVerificar.executeQuery()) {
                    if (rs.next() && rs.getInt("procesado") >= rs.getInt("total")) {
                        psMarcar.setInt(1, ingresoId);
                        psMarcar.executeUpdate();
                    }
                }
            }
        }
    }

    public List<ElementoCicloItem> obtenerElementosDeCiclo(int cicloId) {
        List<ElementoCicloItem> lista = new ArrayList<>();
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_ELEMENTOS_DE_CICLO)) {
            ps.setInt(1, cicloId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ElementoCicloItem item = new ElementoCicloItem(
                        rs.getInt("id"),
                        rs.getInt("ingreso_id"),
                        rs.getString("nombre"),
                        rs.getInt("cantidad"),
                        0,
                        rs.getString("cliente"),
                        rs.getString("categoria")
                    );
                    item.setCantidadEnCiclo(rs.getInt("en_ciclo"));
                    lista.add(item);
                }
            }
        } catch (SQLException e) {
            log.error("Error al obtener elementos de ciclo {}", cicloId, e);
        }
        return lista;
    }

    private CicloLavadero mapearCiclo(ResultSet rs) throws SQLException {
        return new CicloLavadero(
            rs.getInt("id"),
            rs.getInt("lavarropas_numero"),
            TipoLavado.desdeBD(rs.getString("tipo_lavado")),
            new JabonCatalogo(rs.getInt("jabon_id"), rs.getString("jabon_nombre")),
            rs.getBigDecimal("litros_jabon"),
            rs.getBoolean("suavizante"),
            rs.getBoolean("potenciador"),
            rs.getBigDecimal("litros_totales"),
            rs.getObject("fecha_inicio", LocalDateTime.class),
            null
        );
    }

    private CicloLavadero mapearCicloCompleto(ResultSet rs) throws SQLException {
        LocalDateTime fechaFin = rs.getObject("fecha_fin", LocalDateTime.class);
        return new CicloLavadero(
            rs.getInt("id"),
            rs.getInt("lavarropas_numero"),
            TipoLavado.desdeBD(rs.getString("tipo_lavado")),
            new JabonCatalogo(rs.getInt("jabon_id"), rs.getString("jabon_nombre")),
            rs.getBigDecimal("litros_jabon"),
            rs.getBoolean("suavizante"),
            rs.getBoolean("potenciador"),
            rs.getBigDecimal("litros_totales"),
            rs.getObject("fecha_inicio", LocalDateTime.class),
            fechaFin
        );
    }
}
