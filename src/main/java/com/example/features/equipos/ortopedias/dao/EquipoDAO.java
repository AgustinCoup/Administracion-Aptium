package com.example.features.equipos.ortopedias.dao;

import com.example.common.constants.Constantes;
import com.example.common.dao.ControlConcurrencia;
import com.example.common.dao.DAO;
import com.example.common.exception.DatabaseException;
import com.example.common.exception.ResourceNotFoundException;
import com.example.common.paginacion.CriteriosPagina;
import com.example.common.paginacion.Pagina;
import com.example.features.equipos.dao.FiltroEquiposSql;
import com.example.features.equipos.model.FiltroEquipos;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.model.Material;
import com.example.infrastructure.db.ConnectionPool;
import com.example.infrastructure.db.TransactionalConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * DAO para gestionar equipos en la base de datos.
 * Implementa todas las operaciones CRUD de la interfaz DAO<Equipo, String>.
 * Usa el id autoincrementable como clave primaria.
 * 
 * MANEJO DE ERRORES:
 * - Lanza DatabaseException en caso de error SQL
 * - Lanza ResourceNotFoundException cuando no encuentra un equipo
 * - NO retorna null, lanza excepciones para flujo de error explícito
 */
public class EquipoDAO implements DAO<Equipo, String> {

    private static final Logger log = LoggerFactory.getLogger(EquipoDAO.class);

    private static final String SQL_CABECERA_EQUIPO =
        "SELECT e.id, e.nro_cliente, c.nombre AS cliente_nombre, e.nro_profesional, " +
        "       p.nombre AS profesional_nombre, e.paciente, " +
        "       e.nro_institucion, i.nombre AS institucion_nombre, e.estado, " +
        "       e.requiere_lavado, e.requiere_empaque, e.fecha_ingreso, e.version " +
        "FROM equipos e " +
        "LEFT JOIN clientes c ON e.nro_cliente = c.id " +
        "LEFT JOIN profesionales p ON e.nro_profesional = p.id " +
        "LEFT JOIN instituciones i ON e.nro_institucion = i.id ";

    /**
     * Query con materiales incluidos — resuelve el N+1 para listados masivos.
     *
     * <p>{@code ultimo_movimiento} es una subconsulta correlacionada, no un {@code LEFT JOIN}
     * contra una tabla derivada que agrupa {@code material_movimientos} entera: esa tabla recibe
     * una fila por cada cambio de estado y nunca se poda, así que la derivada se materializaba
     * completa en cada listado sin importar cuántos equipos devolviera. Con
     * {@code idx_mov_material (material_id)} (V1), la subconsulta es un index range scan de una
     * entrada por material.
     */
    private static final String SQL_EQUIPOS_CON_MATERIALES =
        "SELECT e.id, e.nro_cliente, c.nombre AS cliente_nombre, e.nro_profesional, " +
        "       p.nombre AS profesional_nombre, e.paciente, " +
        "       e.nro_institucion, i.nombre AS institucion_nombre, e.estado, " +
        "       e.requiere_lavado, e.requiere_empaque, e.fecha_ingreso, e.version, " +
        "       em.id AS mat_id, em.codigo_catalogo, cd.descripcion AS mat_descripcion, " +
        "       em.cantidad AS mat_cantidad, em.estado AS mat_estado, " +
        "       (SELECT MAX(mm.fecha) FROM material_movimientos mm WHERE mm.material_id = em.id) " +
        "           AS ultimo_movimiento, " +
        "       l.id_negocio AS lote_id_negocio " +
        "FROM equipos e " +
        "LEFT JOIN clientes c ON e.nro_cliente = c.id " +
        "LEFT JOIN profesionales p ON e.nro_profesional = p.id " +
        "LEFT JOIN instituciones i ON e.nro_institucion = i.id " +
        "LEFT JOIN equipo_materiales em ON em.equipo_id = e.id " +
        "LEFT JOIN catalogo_descripciones cd ON em.codigo_catalogo = cd.codigo " +
        "LEFT JOIN lotes l ON em.lote_id = l.id ";

    /**
     * "No entregado" en SQL. No puede ser {@code e.estado <> 'Entregado'}: el estado
     * de un equipo de ortopedia es el <b>mínimo</b> de sus materiales
     * ({@link Equipo#calcularEstado()}), no la columna {@code e.estado}. Se traduce a
     * "tiene al menos un material sin entregar".
     *
     * <p>El segundo término no es opcional: un equipo sin materiales calcula NUEVO,
     * no ENTREGADO, así que también es activo.
     */
    private static final String SQL_WHERE_ACTIVOS =
        "WHERE EXISTS (SELECT 1 FROM equipo_materiales em_act " +
        "              WHERE em_act.equipo_id = e.id AND em_act.estado <> ?) " +
        "   OR NOT EXISTS (SELECT 1 FROM equipo_materiales em_vac " +
        "                  WHERE em_vac.equipo_id = e.id) ";

    /**
     * Guarda un equipo completo y su lista de materiales en una sola transacción.
     * Implementa el método guardar de la interfaz DAO.
     * 
     * @throws DatabaseException si hay error durante la transacción
     */
    @Override
    public boolean guardar(Equipo equipo) {
        return guardarEquipo(equipo);
    }

    /**
     * Guarda un equipo completo y su lista de materiales en una sola transacción.
     * Método público para compatibilidad con código existente.
     * 
     * @throws DatabaseException si hay error durante la transacción
     */
    public boolean guardarEquipo(Equipo equipo) {
        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            Connection conn = tx.get();
 
            // 1. Insertar encabezado del Equipo
            String sqlEquipo =
                "INSERT INTO equipos (nro_cliente, nro_profesional, paciente, nro_institucion, " +
                "estado, requiere_lavado, requiere_empaque) VALUES (?, ?, ?, ?, ?, ?, ?)";
 
            int equipoId;
            try (PreparedStatement psE = conn.prepareStatement(sqlEquipo, Statement.RETURN_GENERATED_KEYS)) {
                psE.setInt(1, equipo.getNroCliente());
                if (equipo.getNroProfesional() != null) {
                    psE.setInt(2, equipo.getNroProfesional());
                } else {
                    psE.setNull(2, Types.INTEGER);
                }
                psE.setString(3, equipo.getPacienteNombre());
                psE.setInt(4, equipo.getNroInstitucion());
                psE.setString(5, equipo.getEstado().getNombre());
                psE.setBoolean(6, equipo.isRequiereLavado());
                psE.setBoolean(7, equipo.isRequiereEmpaque());
                psE.executeUpdate();
 
                try (ResultSet rs = psE.getGeneratedKeys()) {
                    if (rs.next()) {
                        equipoId = rs.getInt(1);
                        equipo.setId(equipoId);
                    } else {
                        throw new SQLException("No se generó ID para el equipo");
                    }
                }
            }
 
            // 2. Insertar materiales y registrar movimiento inicial
            List<Material> materiales = equipo.getMateriales();
            if (materiales != null) {
                for (Material mat : materiales) {
                    guardarMaterialEnEquipo(conn, equipoId, mat);
                }
            }
 
            tx.commit();
            log.info("Equipo guardado exitosamente: ID={}", equipoId);
            return true;
 
        } catch (SQLException e) {
            log.error("Error al guardar equipo", e);
            throw new DatabaseException("Error al guardar equipo en la base de datos", e);
        }
    }

    /**
     * Inserta un material del equipo y su movimiento inicial dentro de la misma transacción.
     * Devuelve el id generado para el material recién creado.
     */
    private int guardarMaterialEnEquipo(Connection conn, int equipoId, Material material) throws SQLException {
        String sqlInsertMaterial = "INSERT INTO equipo_materiales (equipo_id, codigo_catalogo, cantidad, estado) " +
                                   "VALUES (?, ?, ?, ?)";
        String sqlInsertMovimiento = "INSERT INTO material_movimientos " +
                                     "(material_id, equipo_id, cantidad, estado_origen, estado_destino) " +
                                     "VALUES (?, ?, ?, ?, ?)";

        int codigoCatalogo = material.getCodigo();
        int cantidad = material.getCantidad();
        EstadoEquipo estadoMaterial = material.getEstado() != null ? material.getEstado() : EstadoEquipo.NUEVO;

        int nuevoMaterialId;
        try (PreparedStatement ps = conn.prepareStatement(sqlInsertMaterial, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, equipoId);
            ps.setInt(2, codigoCatalogo);
            ps.setInt(3, cantidad);
            ps.setString(4, estadoMaterial.getNombre());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    nuevoMaterialId = rs.getInt(1);
                } else {
                    throw new SQLException("No se generó ID para el nuevo material");
                }
            }
        }

        try (PreparedStatement ps = conn.prepareStatement(sqlInsertMovimiento)) {
            ps.setInt(1, nuevoMaterialId);
            ps.setInt(2, equipoId);
            ps.setInt(3, cantidad);
            ps.setNull(4, Types.VARCHAR);
            ps.setString(5, estadoMaterial.getNombre());
            ps.executeUpdate();
        }

        log.info("Material código={} (cantidad={}) agregado al equipo {} -> id={}",
            codigoCatalogo, cantidad, equipoId, nuevoMaterialId);
        return nuevoMaterialId;
    }

    /**
     * Obtiene un equipo por su id.
     * Implementa el método obtenerPorId de la interfaz DAO.
     * 
     * @throws ResourceNotFoundException si el equipo no existe
     * @throws DatabaseException si hay error de base de datos
     */
    @Override
    public Equipo obtenerPorId(String id) {
        String sql = SQL_CABECERA_EQUIPO + "WHERE e.id = ?";

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, Integer.parseInt(id));
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                Equipo eq = mapearEquipoBase(rs);
                cargarMateriales(conn, eq);
                return eq;
            } else {
                throw new ResourceNotFoundException("Equipo", id);
            }
        } catch (SQLException e) {
            log.error("Error al obtener equipo por id: {}", id, e);
            throw new DatabaseException("obtener", "Equipo", id, e);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("ID de equipo inválido: " + id, e);
        }
    }

    private Equipo mapearEquipoBase(ResultSet rs) throws SQLException {
        Equipo eq = new Equipo();
        eq.setId(rs.getInt("id"));
        eq.setNroCliente(rs.getInt("nro_cliente"));
        eq.setClienteNombre(rs.getString("cliente_nombre"));
        eq.setNroProfesional(rs.getObject("nro_profesional", Integer.class));
        eq.setProfesionalNombre(rs.getString("profesional_nombre"));
        eq.setPacienteNombre(rs.getString("paciente"));
        eq.setNroInstitucion(rs.getObject("nro_institucion", Integer.class));
        eq.setInstitucionNombre(rs.getString("institucion_nombre"));
        eq.setEstado(EstadoEquipo.desdeBD(rs.getString("estado")));
        eq.setRequiereLavado(rs.getBoolean("requiere_lavado"));
        eq.setRequiereEmpaque(rs.getBoolean("requiere_empaque"));
        Timestamp fi = rs.getTimestamp("fecha_ingreso");
        eq.setFechaIngreso(fi != null ? fi.toLocalDateTime() : null);
        eq.setVersion(rs.getInt("version"));
        return eq;
    }

    private Material mapearMaterial(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("ultimo_movimiento");
        Material mat = new Material(
            rs.getInt("mat_id"),
            rs.getInt("codigo_catalogo"),
            rs.getString("mat_descripcion"),
            rs.getInt("mat_cantidad"),
            EstadoEquipo.desdeBD(rs.getString("mat_estado")),
            ts != null ? java.time.LocalDateTime.ofInstant(ts.toInstant(), ZoneId.systemDefault()) : null
        );
        mat.setLoteIdNegocio(rs.getString("lote_id_negocio"));
        return mat;
    }

    /**
     * El {@code ORDER BY} de {@code extraWhere} ya no incluye la clave de material (ver el
     * javadoc de {@link #SQL_EQUIPOS_CON_MATERIALES}), así que los materiales de cada equipo se
     * ordenan acá, en memoria: son listas de 3-10 elementos, y el plegado por
     * {@code LinkedHashMap} no depende de que las filas de un equipo lleguen contiguas.
     */
    private List<Equipo> obtenerEquiposConJoin(String extraWhere, Object... params) {
        String sql = SQL_EQUIPOS_CON_MATERIALES + extraWhere;
        LinkedHashMap<Integer, Equipo> mapa = new LinkedHashMap<>();
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int equipoId = rs.getInt("id");
                if (!mapa.containsKey(equipoId)) {
                    mapa.put(equipoId, mapearEquipoBase(rs));
                }
                Integer matId = rs.getObject("mat_id", Integer.class);
                if (matId != null) {
                    mapa.get(equipoId).agregarMaterial(mapearMaterial(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Error al obtener equipos", e);
            throw new DatabaseException("Error al obtener equipos", e);
        }
        for (Equipo eq : mapa.values()) {
            eq.getMateriales().sort(java.util.Comparator.comparingInt(Material::getId));
        }
        return new ArrayList<>(mapa.values());
    }

    /**
     * Carga los materiales de un equipo desde la base de datos.
     * Incluye el estado de cada material.
     */
    private void cargarMateriales(Connection conn, Equipo equipo) throws SQLException {
        String sql = "SELECT em.id, em.codigo_catalogo, cd.descripcion, em.cantidad, em.estado, " +
                 "       (SELECT MAX(mm.fecha) FROM material_movimientos mm WHERE mm.material_id = em.id) " +
                 "           AS ultimo_movimiento " +
                 "FROM equipo_materiales em " +
             "LEFT JOIN catalogo_descripciones cd ON em.codigo_catalogo = cd.codigo " +
                 "WHERE em.equipo_id = ? ORDER BY em.id";
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, equipo.getId());
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                Timestamp ts = rs.getTimestamp("ultimo_movimiento");
                Material mat = new Material(
                    rs.getInt("id"),
                    rs.getInt("codigo_catalogo"),
                    rs.getString("descripcion"),
                    rs.getInt("cantidad"),
                    EstadoEquipo.desdeBD(rs.getString("estado")),
                    ts != null ? java.time.LocalDateTime.ofInstant(ts.toInstant(), ZoneId.systemDefault()) : null
                );
                equipo.agregarMaterial(mat);
            }
        }
    }

    /**
     * Obtiene todos los equipos.
     * Implementa el método obtenerTodos de la interfaz DAO.
     */
    @Override
    public List<Equipo> obtenerTodos() {
        return obtenerTodosLosEquipos();
    }

    /**
     * Obtiene todos los equipos con su estado y nombre de cliente.
     * Método público para compatibilidad con código existente.
     */
    public List<Equipo> obtenerTodosLosEquipos() {
        return obtenerEquiposConJoin("ORDER BY e.fecha_ingreso DESC, e.id DESC");
    }

    /**
     * Obtiene la cola activa: los equipos que todavía tienen algo sin entregar.
     *
     * <p>Equivale exactamente a filtrar {@link #obtenerTodos()} por
     * {@code calcularEstado() != ENTREGADO}, pero sin traer el histórico completo.
     * Ver {@link #SQL_WHERE_ACTIVOS} para por qué el filtro no es sobre {@code e.estado}.
     */
    public List<Equipo> obtenerActivos() {
        return obtenerEquiposConJoin(
            SQL_WHERE_ACTIVOS + "ORDER BY e.fecha_ingreso DESC, e.id DESC",
            EstadoEquipo.ENTREGADO.getNombre()
        );
    }

    /**
     * Actualiza el estado de un equipo existente.
     * Implementa el método actualizar de la interfaz DAO.
     *
     * <p>Escribe {@code estado} sin derivarlo de los materiales, así que no pasa por
     * {@link EquipoMaterialHelper#recalcularEstadoEquipo} y tiene que mantener la columna
     * {@code version} (V21) por su cuenta. Su único llamador es
     * {@code EquipoService.actualizar}, que a su vez no tiene llamador — ni en
     * {@code src/main} ni en {@code src/test}: la cadena entera está muerta. El bump va igual
     * para que quien la reconecte mañana no herede un agujero en el token de bloqueo optimista.
     */
    @Override
    public boolean actualizar(Equipo equipo) {
        String sql = "UPDATE equipos SET estado = ?, version = version + 1 WHERE id = ?";
        
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, equipo.getEstado().getNombre());
            pstmt.setInt(2, equipo.getId());
            int filasActualizadas = pstmt.executeUpdate();
            
            return filasActualizadas > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Error al actualizar equipo con ID: " + equipo.getId(), e);
        }
    }

    /**
     * Elimina un equipo por su id.
     * Implementa el método eliminar de la interfaz DAO.
     */
    @Override
    public boolean eliminar(String id) {
        String sql = "DELETE FROM equipos WHERE id = ?";
        
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, Integer.parseInt(id));
            int filasEliminadas = pstmt.executeUpdate();
            
            return filasEliminadas > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Error al eliminar equipo con ID: " + id, e);
        }
    }

    /**
     * Borrado guardado para {@code Correcciones}: CAS de una sola sentencia contra la
     * {@code version} que la pantalla tenía a la vista. No bumpea nada — la fila desaparece, así
     * que no queda token que invalidar.
     *
     * <p>El {@link #eliminar(String)} de la interfaz sigue siendo el borrado ciego. No lo use
     * ninguna ruta de Correcciones.
     */
    public void eliminarConVersion(int equipoId, int versionEsperada) {
        String sql = "DELETE FROM equipos WHERE id = ? AND version = ?";
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, equipoId);
            ps.setInt(2, versionEsperada);
            ControlConcurrencia.exigirFilaAfectada(ps.executeUpdate(), Constantes.Mensajes.CONFLICTO_CORRECCION);
        } catch (SQLException e) {
            throw new DatabaseException("Error al eliminar equipo con ID: " + equipoId, e);
        }
    }

    /**
     * Obtiene el total de equipos en la base de datos.
     * Implementa el método contar de la interfaz DAO.
     */
    @Override
    public long contar() {
        String sql = "SELECT COUNT(*) FROM equipos";
        
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            log.error("Error al contar equipos", e);
        }
        return 0;
    }

    /**
     * Verifica si existe un equipo con el id especificado.
     * Implementa el método existe de la interfaz DAO.
     */
    @Override
    public boolean existe(String id) {
        return obtenerPorId(id) != null;
    }

    // ============================================================
    // MÉTODOS PARA CORRECCIONES Y AUDITORÍA
    // ============================================================

    /**
     * Obtiene todos los equipos en estado NUEVO (solo los editables).
     * @return Lista de equipos en estado NUEVO
     */
    public List<Equipo> obtenerEquiposNuevos() {
        return obtenerEquiposConJoin(
            "WHERE e.estado = ? ORDER BY e.fecha_ingreso DESC, e.id DESC",
            EstadoEquipo.NUEVO.getNombre()
        );
    }

    // ============================================================
    // LISTADO PAGINADO (Ver Equipos — grilla de ortopedias)
    // ============================================================

    /**
     * Sólo los ids de la página, ordenados y paginados. Es el <b>primer viaje</b> de
     * {@link #obtenerPagina}; ver ahí por qué son dos.
     *
     * <p>Los {@code JOIN} contra {@code clientes}, {@code profesionales} e {@code instituciones}
     * <b>no</b> están acá: los pone {@link FiltroEquiposSql.Condicion#joins()}, y sólo cuando el
     * filtro de texto que los necesita tiene algo escrito. Esta consulta proyecta únicamente
     * {@code e.id}, así que un {@code JOIN} sin predicado que lo use no aporta nada — y cuesta el
     * índice del {@code ORDER BY}. Está medido; ver el javadoc de {@code Condicion}.
     *
     * <p>Cuando están, son a la PK de cada tabla, así que no pueden multiplicar filas: el
     * {@code LIMIT} cuenta equipos y el {@code COUNT(*)} de {@link #SQL_CONTAR} cuenta lo mismo.
     */
    private static final String SQL_IDS_PAGINA = "SELECT e.id FROM equipos e ";

    /** Mismo {@code FROM} que {@link #SQL_IDS_PAGINA}, para que el mismo filtro sirva a los dos. */
    private static final String SQL_CONTAR = "SELECT COUNT(*) FROM equipos e ";

    /**
     * El orden de la grilla de ortopedias de Ver Equipos, que es el mismo que traía
     * {@link #obtenerTodosLosEquipos()}. Cubierto entero por {@code idx_equipos_fecha_ingreso}
     * (V23): en InnoDB todo índice secundario incluye la PK.
     *
     * <p>{@code e.id DESC} no es decorativo: sin un desempate total, dos equipos con la misma
     * {@code fecha_ingreso} pueden alternar de orden entre una página y la siguiente, y entonces
     * uno aparece dos veces y el otro ninguna. {@code fecha_ingreso} tiene resolución de segundo y
     * una carga por lote crea varios equipos dentro del mismo, así que el empate no es teórico.
     */
    private static final String SQL_ORDEN_PAGINA = " ORDER BY e.fecha_ingreso DESC, e.id DESC";

    /**
     * Una página de equipos de ortopedia <b>con sus materiales</b>, con los filtros y el orden
     * resueltos en SQL, y el total de equipos que matchean.
     *
     * <h2>⚠️ Por qué son DOS viajes y no una consulta sola</h2>
     * {@link #SQL_EQUIPOS_CON_MATERIALES} devuelve <b>una fila por (equipo × material)</b>. Pegarle
     * un {@code LIMIT 50} corta <b>materiales</b>, no equipos: la página 1 traería los materiales
     * de los primeros ~12 equipos y el equipo del borde llegaría partido, con parte de sus
     * materiales en la página siguiente. Por eso el primer viaje elige los 50 <b>ids</b> con el
     * orden y el {@code LIMIT}, y el segundo trae el detalle completo de esos 50 con
     * {@code IN (…)}.
     *
     * <h2>⚠️ La alternativa "elegante" NO existe en MySQL</h2>
     * Lo primero que alguien va a querer hacer es plegar los dos viajes en uno:
     * <pre>{@code WHERE e.id IN (SELECT id FROM equipos … ORDER BY … LIMIT ? OFFSET ?)}</pre>
     * <b>Eso falla</b>, y no en tiempo de test sino en producción:
     * {@code ERROR 1235 (42000): This version of MySQL doesn't yet support
     * 'LIMIT & IN/ALL/ANY/SOME subquery'}. <b>H2 no lo delata</b>: lo acepta sin chistar, así que
     * la suite quedaría verde y la app rota en el puesto. El único rodeo que MySQL acepta es
     * envolverla en una tabla derivada ({@code IN (SELECT * FROM (SELECT … LIMIT ?) t)}), que
     * materializa la derivada, no es más rápido y es más frágil. Dos viajes explícitos.
     *
     * <h2>El orden lo fija el primer viaje</h2>
     * El segundo trae los mismos 50 equipos pero el servidor no está obligado a devolverlos en
     * ningún orden particular, así que se reordenan en memoria contra la lista de ids del primero.
     * Son 50 elementos.
     *
     * <p>Pedir una página más allá del total devuelve una página vacía con el total correcto y
     * <b>no</b> lanza: el operador puede tener abierta la página 7 cuando el filtro pasó a tener
     * dos.
     *
     * @throws DatabaseException si falla cualquiera de las consultas — nunca una lista a medias
     */
    public Pagina<Equipo> obtenerPagina(FiltroEquipos filtro, CriteriosPagina criterios) {
        return armarPagina(filtro, criterios, contar(filtro));
    }

    /**
     * La misma página, pero con el total ya sabido: <b>no vuelve a contar</b>.
     *
     * <p>El total sólo cambia cuando cambian los <em>filtros</em>. Ir de la página 3 a la 4 con el
     * mismo filtro no necesita un {@code COUNT(*)} nuevo, y hacerlo igual duplica las consultas que
     * esta paginación vino a ahorrar. El llamador es responsable de pasar el total que leyó con
     * <b>este mismo filtro</b>.
     */
    public Pagina<Equipo> obtenerPagina(FiltroEquipos filtro, CriteriosPagina criterios,
                                        long totalConocido) {
        if (totalConocido < 0) {
            throw new IllegalArgumentException("totalConocido no puede ser negativo: " + totalConocido);
        }
        return armarPagina(filtro, criterios, totalConocido);
    }

    private Pagina<Equipo> armarPagina(FiltroEquipos filtro, CriteriosPagina criterios, long total) {
        List<Integer> ids = idsDePagina(filtro, criterios);
        if (ids.isEmpty()) {
            return new Pagina<>(List.of(), criterios.numeroPagina(), criterios.tamanioPagina(), total);
        }
        return new Pagina<>(ordenarComo(ids, obtenerPorIds(ids)),
            criterios.numeroPagina(), criterios.tamanioPagina(), total);
    }

    /**
     * El detalle completo —con materiales— de un conjunto de ids. Segundo viaje de
     * {@link #obtenerPagina}, y también lo que usa
     * {@link com.example.features.equipos.dao.CdeConsultaDAO} para la mitad de ortopedias de su
     * unión: los dos necesitan exactamente lo mismo, y tener dos copias del mapeo sería la
     * divergencia esperando a pasar.
     *
     * <p>El orden que devuelve es el de {@link #SQL_ORDEN_PAGINA}, no el de {@code ids}: quien
     * necesite el orden de una unión lo reordena por su cuenta.
     *
     * @param ids no vacío; con la lista vacía el {@code IN ()} no es SQL válido
     */
    public List<Equipo> obtenerPorIds(List<Integer> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return obtenerEquiposConJoin(
            "WHERE e.id IN (" + FiltroEquiposSql.marcadores(ids.size()) + ")"
                + SQL_ORDEN_PAGINA, ids.toArray());
    }

    private List<Integer> idsDePagina(FiltroEquipos filtro, CriteriosPagina criterios) {
        FiltroEquiposSql.Condicion condicion = FiltroEquiposSql.paraOrtopedias(filtro);
        String sql = SQL_IDS_PAGINA + condicion.joins() + condicion.sql()
            + SQL_ORDEN_PAGINA + " LIMIT ? OFFSET ?";

        List<Integer> ids = new ArrayList<>();
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int siguiente = FiltroEquiposSql.aplicar(ps, condicion.parametros());
            ps.setInt(siguiente, criterios.tamanioPagina());
            ps.setLong(siguiente + 1, criterios.offset());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            log.error("Error al obtener la página de equipos", e);
            throw new DatabaseException("Error al obtener la página de equipos", e);
        }
        return ids;
    }

    /** Cuántos equipos matchean el filtro, con el <b>mismo</b> {@code WHERE} que la página. */
    public long contar(FiltroEquipos filtro) {
        FiltroEquiposSql.Condicion condicion = FiltroEquiposSql.paraOrtopedias(filtro);
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 SQL_CONTAR + condicion.joins() + condicion.sql())) {
            FiltroEquiposSql.aplicar(ps, condicion.parametros());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            log.error("Error al contar equipos con filtro", e);
            throw new DatabaseException("Error al contar los equipos", e);
        }
    }

    /** Devuelve {@code detalle} en el orden de {@code ids}. Son 50 elementos: el costo no importa. */
    private static List<Equipo> ordenarComo(List<Integer> ids, List<Equipo> detalle) {
        java.util.Map<Integer, Equipo> porId = new java.util.HashMap<>();
        for (Equipo equipo : detalle) {
            porId.put(equipo.getId(), equipo);
        }
        List<Equipo> ordenados = new ArrayList<>(ids.size());
        for (Integer id : ids) {
            Equipo equipo = porId.get(id);
            if (equipo != null) {
                ordenados.add(equipo);
            }
        }
        return ordenados;
    }

    public List<Equipo> obtenerEntreFechas(LocalDate desde, LocalDate hasta, Integer clienteId, Integer institucionId) {
        String where = "WHERE e.fecha_ingreso >= ? AND e.fecha_ingreso <= ?";
        List<Object> params = new ArrayList<>(Arrays.asList(
            Timestamp.valueOf(desde.atStartOfDay()),
            Timestamp.valueOf(hasta.atTime(23, 59, 59))
        ));
        if (clienteId != null) {
            where += " AND e.nro_cliente = ?";
            params.add(clienteId);
        }
        if (institucionId != null) {
            where += " AND e.nro_institucion = ?";
            params.add(institucionId);
        }
        return obtenerEquiposConJoin(where + " ORDER BY e.fecha_ingreso, e.id", params.toArray());
    }


}


