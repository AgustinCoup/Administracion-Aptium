package com.example.features.lotes.dao;

import com.example.common.constants.Constantes;
import com.example.common.dao.ErroresSql;
import com.example.common.exception.ConflictoConcurrenciaException;
import com.example.common.exception.DatabaseException;
import com.example.features.equipos.ortopedias.dao.EquipoMaterialHelper;
import com.example.features.equipos.otros.dao.EquipoOtrosMaterialHelper;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.lotes.model.Lote;
import com.example.features.lotes.model.LoteMaterialInfo;
import com.example.features.lotes.model.LoteMovimiento;
import com.example.infrastructure.db.ConnectionPool;
import com.example.infrastructure.db.TransactionalConnection;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LoteDAO {

    /** Cuántas veces se recalcula la secuencia antes de darse por vencido. Ver {@link #lanzarLote}. */
    private static final int MAX_INTENTOS_SECUENCIA = 3;

    /**
     * El {@code INSERT INTO lotes} violó una restricción de integridad. Interna al DAO.
     *
     * <p>Extiende {@link RuntimeException} y no {@link SQLException} a propósito:
     * {@link #intentarLanzarLote} tiene un {@code catch (SQLException)} que la traduciría a
     * {@link DatabaseException} antes de que el bucle de reintentos la viera, y el reintento no se
     * dispararía nunca.</p>
     *
     * <p>Lleva el {@code idNegocio} que se intentó para que el bucle pueda averiguar, ya fuera de la
     * transacción, si la restricción violada fue el {@code UNIQUE} o la FK a {@code autoclaves}.</p>
     */
    private static final class SecuenciaDuplicadaException extends RuntimeException {
        private final String idNegocio;
        private final SQLException causaSql;

        SecuenciaDuplicadaException(String idNegocio, SQLException causaSql) {
            super("Violación de integridad al insertar el lote " + idNegocio, causaSql);
            this.idNegocio = idNegocio;
            this.causaSql  = causaSql;
        }

        String getIdNegocio()    { return idNegocio; }
        SQLException getCausaSql() { return causaSql; }
    }

    // ── Consultas ────────────────────────────────────────────────────────────

    public Map<String, Lote> obtenerLotesActivosPorAutoclave() {
        Map<String, Lote> activos = new HashMap<>();
        String sql = "SELECT id, id_negocio, anio, secuencia, autoclave_nombre, " +
                     "capacidad_total, capacidad_usada, fecha_inicio, fecha_fin, estado " +
                     "FROM lotes WHERE fecha_fin IS NULL";

        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Lote lote = mapLoteConMateriales(rs);
                activos.put(lote.getAutoclaveNombre(), lote);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error al obtener lotes activos", e);
        }
        return activos;
    }

    public List<Lote> obtenerLotesFinalizados() {
        List<Lote> finalizados = new ArrayList<>();
        String sql = "SELECT id, id_negocio, anio, secuencia, autoclave_nombre, " +
                     "capacidad_total, capacidad_usada, fecha_inicio, fecha_fin, estado " +
                     "FROM lotes WHERE fecha_fin IS NOT NULL ORDER BY fecha_fin DESC, id DESC";

        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) finalizados.add(mapLote(rs));
        } catch (SQLException e) {
            throw new DatabaseException("Error al obtener lotes finalizados", e);
        }
        return finalizados;
    }

    public List<Lote> obtenerTodosLosLotes() {
        List<Lote> todos = new ArrayList<>();
        String sql = "SELECT id, id_negocio, anio, secuencia, autoclave_nombre, " +
                     "capacidad_total, capacidad_usada, fecha_inicio, fecha_fin, estado " +
                     "FROM lotes ORDER BY fecha_inicio DESC, id DESC";

        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) todos.add(mapLote(rs));
        } catch (SQLException e) {
            throw new DatabaseException("Error al obtener todos los lotes", e);
        }
        return todos;
    }

    /**
     * Devuelve los lotes cuya {@code fecha_inicio} cae en [desde, hasta] (inclusive).
     * No carga materiales; usá {@link #obtenerMaterialesPorLote(int)} para eso.
     */
    public List<Lote> obtenerLotesEnRango(LocalDate desde, LocalDate hasta) {
        List<Lote> lotes = new ArrayList<>();
        String sql = "SELECT id, id_negocio, anio, secuencia, autoclave_nombre, " +
                     "capacidad_total, capacidad_usada, fecha_inicio, fecha_fin, estado " +
                     "FROM lotes " +
                     "WHERE DATE(fecha_inicio) BETWEEN ? AND ? AND estado = 'EXITOSO' " +
                     "ORDER BY fecha_inicio ASC, id ASC";

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDate(1, Date.valueOf(desde));
            pstmt.setDate(2, Date.valueOf(hasta));
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) lotes.add(mapLote(rs));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error al obtener lotes en rango [" + desde + " - " + hasta + "]", e);
        }
        return lotes;
    }

    public List<String> obtenerClientesPorLote(int loteId) {
        List<String> clientes = new ArrayList<>();
        String sql = "SELECT DISTINCT c.nombre " +
                     "FROM clientes c " +
                     "  JOIN equipos e           ON e.nro_cliente = c.id " +
                     "  JOIN equipo_materiales em ON em.equipo_id  = e.id " +
                     "WHERE em.lote_id = ? ORDER BY c.nombre";

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, loteId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) clientes.add(rs.getString("nombre"));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error al obtener clientes del lote " + loteId, e);
        }
        return clientes;
    }

    public Map<String, List<String>> obtenerMaterialesPorClientePorLote(int loteId) {
        Map<String, List<String>> resultado = new LinkedHashMap<>();
        String sql =
            "SELECT c.nombre AS cliente, cd.descripcion, em.cantidad " +
            "FROM equipo_materiales em " +
            "  JOIN equipos e                 ON em.equipo_id      = e.id " +
            "  JOIN clientes c                ON e.nro_cliente     = c.id " +
            "  JOIN catalogo_descripciones cd ON em.codigo_catalogo = cd.codigo " +
            "WHERE em.lote_id = ? ORDER BY c.nombre, cd.descripcion";

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, loteId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String cliente  = rs.getString("cliente");
                    String material = rs.getString("descripcion") + " x" + rs.getInt("cantidad");
                    resultado.computeIfAbsent(cliente, k -> new ArrayList<>()).add(material);
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error al obtener materiales por cliente del lote " + loteId, e);
        }
        return resultado;
    }

    public Map<String, List<String>> obtenerOtrosPorClientePorLote(int loteId) {
        Map<String, List<String>> resultado = new LinkedHashMap<>();
        String sql =
            "SELECT c.nombre AS cliente, eo.remito_id, " +
            "       eom.descripcion, eom.cantidad " +
            "FROM equipo_otros_materiales eom " +
            "  JOIN equipo_otros eo ON eom.equipo_otros_id = eo.id " +
            "  JOIN clientes c     ON eo.nro_cliente       = c.id " +
            "WHERE eom.lote_id = ? " +
            "ORDER BY c.nombre, eom.id";

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, loteId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String  cliente   = rs.getString("cliente");
                    String  remitoId  = rs.getString("remito_id");
                    String  linea     = remitoId != null
                            ? remitoId
                            : rs.getString("descripcion") + " x" + rs.getInt("cantidad");
                    resultado.computeIfAbsent(cliente, k -> new ArrayList<>()).add(linea);
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error al obtener materiales otros por cliente del lote " + loteId, e);
        }
        // Litros desde lote_otros_volumenes en query separada: un JOIN con las filas de
        // material multiplicaría los litros por la cantidad de filas del ingreso.
        // Solo se anexan a clientes ya presentes (los lotes fallidos relanzados dejan
        // filas de volumen sin materiales, que no deben generar líneas fantasma).
        Map<String, Integer> litrosPorCliente = obtenerLitrosPorCliente(loteId);
        for (Map.Entry<String, List<String>> entry : resultado.entrySet())
            entry.getValue().add("Litros: " + litrosPorCliente.getOrDefault(entry.getKey(), 0));
        return resultado;
    }

    private Map<String, Integer> obtenerLitrosPorCliente(int loteId) {
        Map<String, Integer> litros = new LinkedHashMap<>();
        String sql =
            "SELECT c.nombre AS cliente, SUM(v.volumen) AS litros " +
            "FROM lote_otros_volumenes v " +
            "  JOIN equipo_otros eo ON v.equipo_otros_id = eo.id " +
            "  JOIN clientes c     ON eo.nro_cliente     = c.id " +
            "WHERE v.lote_id = ? " +
            "GROUP BY c.nombre";
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, loteId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) litros.put(rs.getString("cliente"), rs.getInt("litros"));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error al obtener litros por cliente del lote " + loteId, e);
        }
        return litros;
    }

    public List<LoteMaterialInfo> obtenerMaterialesPorLote(int loteId) {
        List<LoteMaterialInfo> materiales = new ArrayList<>();
        // UNION: materiales de ortopedia + materiales de equipo_otros
        String sql =
            "SELECT em.id, em.equipo_id, em.codigo_catalogo, " +
            "       COALESCE(cd.descripcion, '') AS descripcion, em.cantidad, " +
            "       COALESCE(cd.volumen, 1) AS volumen " +
            "FROM equipo_materiales em " +
            "LEFT JOIN catalogo_descripciones cd ON em.codigo_catalogo = cd.codigo " +
            "WHERE em.lote_id = ? " +
            "UNION ALL " +
            // El volumen de los "otros" pertenece al ingreso (lote_otros_volumenes),
            // no a la fila de material: acá va 0 fijo.
            "SELECT eom.id, eom.equipo_otros_id, 0, eom.descripcion, eom.cantidad, 0 " +
            "FROM equipo_otros_materiales eom " +
            "WHERE eom.lote_id = ? " +
            "ORDER BY id";

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, loteId);
            pstmt.setInt(2, loteId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    materiales.add(new LoteMaterialInfo(
                        rs.getInt("id"),
                        rs.getInt("equipo_id"),
                        rs.getInt("codigo_catalogo"),
                        rs.getString("descripcion"),
                        rs.getInt("cantidad"),
                        rs.getInt("volumen")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error al obtener materiales del lote " + loteId, e);
        }
        return materiales;
    }

    /** Litros declarados por ingreso (equipo_otros) para un lote. */
    public Map<Integer, Integer> obtenerVolumenesPorLote(int loteId) {
        Map<Integer, Integer> volumenes = new LinkedHashMap<>();
        String sql = "SELECT equipo_otros_id, volumen FROM lote_otros_volumenes WHERE lote_id = ?";
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, loteId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) volumenes.put(rs.getInt("equipo_otros_id"), rs.getInt("volumen"));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error al obtener volúmenes del lote " + loteId, e);
        }
        return volumenes;
    }

    // ── Mutaciones ───────────────────────────────────────────────────────────

    public boolean finalizarLote(int loteId) {
        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            Connection conn = tx.get();

            if (!actualizarEstadoLoteAbierto(conn, loteId, "EXITOSO")) {
                return false;
            }

            // Ortopedia
            Set<Integer> equiposAfectados = new HashSet<>();
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT id, equipo_id, cantidad, estado FROM equipo_materiales WHERE lote_id = ? FOR UPDATE")) {
                pstmt.setInt(1, loteId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        int materialId = rs.getInt("id");
                        int equipoId = rs.getInt("equipo_id");
                        int cantidad = rs.getInt("cantidad");
                        String estadoActual = rs.getString("estado");
                        equiposAfectados.add(equipoId);
                        actualizarEstadoMaterial(conn, materialId, EstadoEquipo.ESTERILIZADO.getNombre());
                        registrarMovimiento(conn, materialId, equipoId, cantidad, estadoActual,
                            EstadoEquipo.ESTERILIZADO.getNombre());
                    }
                }
            }
            procesarEquiposAfectados(conn, equiposAfectados);

            // Otros
            Set<Integer> equiposOtrosAfectados = new HashSet<>();
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT id, equipo_otros_id, cantidad, estado FROM equipo_otros_materiales WHERE lote_id = ? FOR UPDATE")) {
                pstmt.setInt(1, loteId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        int materialId   = rs.getInt("id");
                        int equipoOtrosId = rs.getInt("equipo_otros_id");
                        int cantidad     = rs.getInt("cantidad");
                        String estadoActual = rs.getString("estado");
                        equiposOtrosAfectados.add(equipoOtrosId);
                        actualizarEstadoMaterialOtros(conn, materialId, EstadoEquipo.ESTERILIZADO.getNombre());
                        registrarMovimientoOtros(conn, materialId, equipoOtrosId, cantidad, estadoActual,
                            EstadoEquipo.ESTERILIZADO.getNombre());
                    }
                }
            }
            procesarEquiposOtrosAfectados(conn, equiposOtrosAfectados);
            for (Integer eqId : equiposOtrosAfectados) acumularVolumenEquipoOtros(conn, eqId, loteId);

            tx.commit();
            return true;
        } catch (SQLException e) {
            throw new DatabaseException("Error al finalizar lote " + loteId, e);
        }
    }

    public boolean marcarLoteFallo(int loteId) {
        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            Connection conn = tx.get();

            if (!actualizarEstadoLoteAbierto(conn, loteId, "FALLIDO")) {
                return false;
            }

            // Ortopedia
            Set<Integer> equiposAfectados = new HashSet<>();
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT id, equipo_id, cantidad FROM equipo_materiales " +
                    "WHERE lote_id = ? AND estado = ? FOR UPDATE")) {
                pstmt.setInt(1, loteId);
                pstmt.setString(2, EstadoEquipo.ESTERILIZANDO.getNombre());
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        int materialId = rs.getInt("id");
                        int equipoId   = rs.getInt("equipo_id");
                        int cantidad   = rs.getInt("cantidad");
                        equiposAfectados.add(equipoId);
                        String estadoAnterior = obtenerEstadoAnteriorDesdeMovimiento(conn, materialId,
                            EstadoEquipo.ESTERILIZANDO.getNombre(), EstadoEquipo.EMPAQUETADO.getNombre());
                        actualizarEstadoMaterial(conn, materialId, estadoAnterior);
                        registrarMovimiento(conn, materialId, equipoId, cantidad,
                            EstadoEquipo.ESTERILIZANDO.getNombre(), estadoAnterior);
                    }
                }
            }
            procesarEquiposAfectados(conn, equiposAfectados);

            // Otros
            Set<Integer> equiposOtrosAfectados = new HashSet<>();
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT id, equipo_otros_id, cantidad FROM equipo_otros_materiales " +
                    "WHERE lote_id = ? AND estado = ? FOR UPDATE")) {
                pstmt.setInt(1, loteId);
                pstmt.setString(2, EstadoEquipo.ESTERILIZANDO.getNombre());
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        int materialId    = rs.getInt("id");
                        int equipoOtrosId = rs.getInt("equipo_otros_id");
                        int cantidad      = rs.getInt("cantidad");
                        equiposOtrosAfectados.add(equipoOtrosId);
                        String estadoAnterior = obtenerEstadoAnteriorOtros(conn, materialId,
                            EstadoEquipo.ESTERILIZANDO.getNombre(), EstadoEquipo.EMPAQUETADO.getNombre());
                        actualizarEstadoMaterialOtros(conn, materialId, estadoAnterior);
                        registrarMovimientoOtros(conn, materialId, equipoOtrosId, cantidad,
                            EstadoEquipo.ESTERILIZANDO.getNombre(), estadoAnterior);
                    }
                }
            }
            procesarEquiposOtrosAfectados(conn, equiposOtrosAfectados);

            tx.commit();
            return true;
        } catch (SQLException e) {
            throw new DatabaseException("Error al marcar lote como fallido " + loteId, e);
        }
    }

    /**
     * Lanza un lote, reintentando si la secuencia que calculó ya se la llevó otro operador.
     *
     * <p><b>Por qué hay reintento acá y en ninguna guarda.</b> {@link #obtenerSiguienteSecuencia}
     * es un {@code MAX(secuencia) + 1}: dos lanzamientos simultáneos calculan el mismo número y el
     * segundo choca contra el {@code UNIQUE (id_negocio)}. Eso <b>no</b> es un lost update — no hay
     * ningún dato que el operador haya visto y se esté pisando, sólo una identidad que hay que
     * asignar. Recalcularla y volver a intentar da exactamente el resultado que el operador pidió,
     * así que reintentar es correcto. En una guarda no lo sería: ahí el {@code 0 filas} significa
     * que la realidad cambió, y reintentar pisaría el trabajo del otro.</p>
     *
     * <p><b>Qué NO se reintenta.</b> El bucle sólo captura {@link SecuenciaDuplicadaException}.
     * Un {@link ConflictoConcurrenciaException} de las guardas de materiales propaga en el acto.</p>
     *
     * <p><b>Qué cubre en MySQL real, y qué no.</b> Con dos lanzamientos simultáneos el segundo
     * {@code INSERT} <b>bloquea</b> en el índice único hasta que el primero termina; recién ahí sale
     * la clase {@code 23} y el reintento hace su trabajo. Ése es el caso común, porque estas
     * transacciones son cortas. Si el primero tarda más que {@code innodb_lock_wait_timeout}, lo que
     * sale es un <i>lock wait timeout</i> ({@code 40001}/{@code HY000}), que <b>no</b> se reintenta y
     * se sigue reportando como {@link DatabaseException}: reintentar una espera de lock agotada es
     * apilar minutos de espera sobre una base ya trabada.</p>
     */
    public Lote lanzarLote(String autoclaveNombre, int capacidadTotal, int capacidadUsada,
                           List<LoteMovimiento> movimientos,
                           Map<Integer, Integer> volumenesPorIngreso) {
        if (movimientos == null || movimientos.isEmpty()) {
            throw new IllegalArgumentException("La lista de movimientos no puede ser nula o vacía");
        }

        for (int intento = 1; intento <= MAX_INTENTOS_SECUENCIA; intento++) {
            try {
                return intentarLanzarLote(autoclaveNombre, capacidadTotal, capacidadUsada,
                                          movimientos, volumenesPorIngreso);
            } catch (SecuenciaDuplicadaException e) {
                // ── LA LECTURA VA ACÁ AFUERA A PROPÓSITO. NO LA MUEVA ADENTRO DEL INTENTO. ──
                // La clase 23 del INSERT INTO lotes no dice QUÉ restricción se violó: puede ser el
                // UNIQUE (id_negocio) — el choque de secuencia que este bucle resuelve — o la FK a
                // autoclaves (V1__baseline.sql:61), que un autoclave borrado o renombrado dispara en
                // la MISMA sentencia. Sin discriminar, un problema de autoclave se llevaría los tres
                // intentos y terminaría con un cartel de "conflicto de secuencia" que diagnostica
                // cualquier cosa menos el problema.
                //
                // El chequeo tiene que correr sobre una conexión nueva y DESPUÉS de que la
                // transacción del intento revirtió. Adentro del intento leería el snapshot que esa
                // transacción fijó en su primera lectura (obtenerSiguienteSecuencia), y bajo el
                // REPEATABLE READ de MySQL la fila que el otro operador committeó es INVISIBLE: el
                // chequeo daría vacío, concluiría "no era el duplicado" y el reintento no se
                // dispararía NUNCA en producción. Que el INSERT sí haya visto la fila no es
                // contradicción: la verificación de unicidad del índice no pasa por el snapshot.
                //
                // Y los tests NO defienden esto: H2 corre en READ COMMITTED, ve la fila ajena esté
                // el SELECT adentro o afuera, así que la versión rota queda verde. Lo único que
                // protege esta decisión es este comentario.
                if (!existeIdNegocio(e.getIdNegocio())) {
                    throw new DatabaseException(
                        "Error al lanzar lote para autoclave: " + autoclaveNombre, e.getCausaSql());
                }
                // Era el duplicado de secuencia: el próximo intento la recalcula con la fila ajena
                // ya visible.
            }
        }
        throw new ConflictoConcurrenciaException(Constantes.Mensajes.CONFLICTO_SECUENCIA_LOTE);
    }

    /**
     * Un intento de lanzamiento, con su propia transacción.
     *
     * <p>El {@code try (TransactionalConnection …)} va acá adentro y no en el bucle: si los intentos
     * compartieran la transacción, el reintento releería el mismo {@code MAX(secuencia)} — el del
     * snapshot ya fijado — y chocaría para siempre contra el mismo {@code id_negocio}.</p>
     */
    private Lote intentarLanzarLote(String autoclaveNombre, int capacidadTotal, int capacidadUsada,
                                    List<LoteMovimiento> movimientos,
                                    Map<Integer, Integer> volumenesPorIngreso) {
        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            Connection conn = tx.get();

            int anio      = java.time.LocalDate.now().getYear();
            int secuencia = obtenerSiguienteSecuencia(conn, anio);
            String idNegocio = construirIdNegocio(anio, secuencia);

            int loteId = insertarLote(conn, idNegocio, anio, secuencia, autoclaveNombre,
                                      capacidadTotal, capacidadUsada);

            Set<Integer> equiposAfectados      = new HashSet<>();
            Set<Integer> equiposOtrosAfectados = new HashSet<>();
            for (LoteMovimiento mov : movimientos) {
                if (mov.isEsOtros()) {
                    equiposOtrosAfectados.add(mov.getEquipoId());
                    aplicarMovimientoLoteOtros(conn, loteId, mov);
                } else {
                    equiposAfectados.add(mov.getEquipoId());
                    aplicarMovimientoLote(conn, loteId, mov);
                }
            }

            procesarEquiposSoloRecalculo(conn, equiposAfectados);
            procesarEquiposOtrosAfectados(conn, equiposOtrosAfectados);
            insertarVolumenesIngreso(conn, loteId, volumenesPorIngreso);
 
            tx.commit();
            return obtenerLotePorId(conn, loteId);
        } catch (SQLException e) {
            throw new DatabaseException("Error al lanzar lote para autoclave: " + autoclaveNombre, e);
        }
    }

    /** Inserta una fila por ingreso en lote_otros_volumenes dentro de la transacción del lote. */
    private void insertarVolumenesIngreso(Connection conn, int loteId,
                                          Map<Integer, Integer> volumenesPorIngreso) throws SQLException {
        if (volumenesPorIngreso == null || volumenesPorIngreso.isEmpty()) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO lote_otros_volumenes (lote_id, equipo_otros_id, volumen) VALUES (?, ?, ?)")) {
            for (Map.Entry<Integer, Integer> entry : volumenesPorIngreso.entrySet()) {
                ps.setInt(1, loteId);
                ps.setInt(2, entry.getKey());
                ps.setInt(3, entry.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // ── Helpers privados de LoteDAO ──────────────────────────────────────────

    private int obtenerSiguienteSecuencia(Connection conn, int anio) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "SELECT COALESCE(MAX(secuencia), 0) AS max_seq FROM lotes WHERE anio = ?")) {
            pstmt.setInt(1, anio);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getInt("max_seq") + 1;
            }
        }
        return 1;
    }

    /**
     * ¿Ya existe un lote con ese {@code id_negocio}?
     *
     * <p>Abre su <b>propia</b> conexión del pool a propósito: no recibe la del intento fallido —
     * que además ya está cerrada — porque el punto de esta lectura es ver el estado committeado por
     * el otro operador, y bajo {@code REPEATABLE READ} el snapshot de aquella transacción no lo
     * incluye. Ver el comentario largo en {@link #lanzarLote}.</p>
     *
     * <p>Es un {@code SELECT} común, sin {@code FOR UPDATE}: no hay nada que bloquear — la fila es
     * del lote ajeno y no se va a tocar.</p>
     */
    private boolean existeIdNegocio(String idNegocio) {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                 "SELECT 1 FROM lotes WHERE id_negocio = ?")) {
            pstmt.setString(1, idNegocio);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error al verificar el id de negocio del lote: " + idNegocio, e);
        }
    }

    private String construirIdNegocio(int anio, int secuencia) {
        return String.valueOf(anio) + secuencia;
    }

    private boolean actualizarEstadoLoteAbierto(Connection conn, int loteId, String estado) throws SQLException {
        String sql = "UPDATE lotes SET fecha_fin = CURRENT_TIMESTAMP, estado = ? " +
                     "WHERE id = ? AND fecha_fin IS NULL";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, estado);
            pstmt.setInt(2, loteId);
            return pstmt.executeUpdate() > 0;
        }
    }

    private void actualizarEstadoMaterial(Connection conn, int materialId, String nuevoEstado) throws SQLException {
        try (PreparedStatement update = conn.prepareStatement(
                "UPDATE equipo_materiales SET estado = ? WHERE id = ?")) {
            update.setString(1, nuevoEstado);
            update.setInt(2, materialId);
            update.executeUpdate();
        }
    }

    private void actualizarEstadoMaterial(Connection conn, int materialId, String nuevoEstado, Integer loteId)
            throws SQLException {
        try (PreparedStatement update = conn.prepareStatement(
                "UPDATE equipo_materiales SET estado = ?, lote_id = ? WHERE id = ?")) {
            update.setString(1, nuevoEstado);
            if (loteId != null) {
                update.setInt(2, loteId);
            } else {
                update.setNull(2, Types.INTEGER);
            }
            update.setInt(3, materialId);
            update.executeUpdate();
        }
    }

    private void actualizarCantidadMaterial(Connection conn, int materialId, int cantidadNueva) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "UPDATE equipo_materiales SET cantidad = ? WHERE id = ?")) {
            pstmt.setInt(1, cantidadNueva);
            pstmt.setInt(2, materialId);
            pstmt.executeUpdate();
        }
    }

    private void registrarMovimiento(Connection conn, int materialId, int equipoId, int cantidad,
                                     String estadoOrigen, String estadoDestino) throws SQLException {
        try (PreparedStatement mov = conn.prepareStatement(
                "INSERT INTO material_movimientos " +
                "(material_id, equipo_id, cantidad, estado_origen, estado_destino) " +
                "VALUES (?, ?, ?, ?, ?)")) {
            mov.setInt(1, materialId);
            mov.setInt(2, equipoId);
            mov.setInt(3, cantidad);
            mov.setString(4, estadoOrigen);
            mov.setString(5, estadoDestino);
            mov.executeUpdate();
        }
    }

    private String obtenerEstadoAnteriorDesdeMovimiento(Connection conn, int materialId,
                                                        String estadoDestino, String valorPorDefecto)
            throws SQLException {
        String sql = "SELECT estado_origen FROM material_movimientos " +
                     "WHERE material_id = ? AND estado_destino = ? " +
                     "ORDER BY fecha DESC LIMIT 1";
        try (PreparedStatement pstmtPrev = conn.prepareStatement(sql)) {
            pstmtPrev.setInt(1, materialId);
            pstmtPrev.setString(2, estadoDestino);
            try (ResultSet rsPrev = pstmtPrev.executeQuery()) {
                if (rsPrev.next()) {
                    String estadoAnterior = rsPrev.getString("estado_origen");
                    return estadoAnterior != null ? estadoAnterior : valorPorDefecto;
                }
            }
        }
        return valorPorDefecto;
    }

    private void procesarEquiposAfectados(Connection conn, Iterable<Integer> equiposAfectados) throws SQLException {
        for (Integer equipoId : equiposAfectados) {
            recalcularEstadoEquipo(conn, equipoId);
            unificarMaterialesDuplicados(conn, equipoId);
        }
    }

    private void procesarEquiposSoloRecalculo(Connection conn, Iterable<Integer> equiposAfectados) throws SQLException {
        for (Integer equipoId : equiposAfectados) {
            recalcularEstadoEquipo(conn, equipoId);
        }
    }

    private void recalcularEstadoEquipo(Connection conn, int equipoId) throws SQLException {
        EquipoMaterialHelper.recalcularEstadoEquipo(conn, equipoId);
    }

    private void unificarMaterialesDuplicados(Connection conn, int equipoId) throws SQLException {
        EquipoMaterialHelper.unificarMaterialesDuplicados(conn, equipoId);
    }

    private void procesarEquiposOtrosAfectados(Connection conn, Iterable<Integer> equiposOtrosAfectados)
            throws SQLException {
        for (Integer eqId : equiposOtrosAfectados) {
            recalcularEstadoEquipoOtros(conn, eqId);
            EquipoOtrosMaterialHelper.unificarMaterialesDuplicados(conn, eqId);
        }
    }

    /**
     * Rechaza el movimiento si el estado leído {@code FOR UPDATE} no coincide con el que la
     * pantalla mostraba, o si el material ya está asignado a un lote todavía abierto. Las dos
     * condiciones son el mismo <i>lost update</i>: el staging se armó sobre un snapshot que ya no
     * vale.
     *
     * <p>El {@code lote_id} sólo cuenta si apunta a un lote <b>activo</b> ({@code fecha_fin IS
     * NULL}): {@code marcarLoteFallo} revierte el estado del material pero deja el {@code lote_id}
     * apuntando al lote fallido, y ese material sí es relanzable.
     */
    private void guardarConcurrencia(Connection conn, String estadoActual, Integer loteIdActual,
                                     LoteMovimiento movimiento) throws SQLException {
        EstadoEquipo esperado = movimiento.getEstadoOrigenEsperado();
        boolean estadoCambio = esperado == null
            || estadoActual == null
            || !estadoActual.equalsIgnoreCase(esperado.getNombre());
        if (estadoCambio || (loteIdActual != null && loteSigueActivo(conn, loteIdActual))) {
            throw new ConflictoConcurrenciaException(Constantes.Mensajes.CONFLICTO_LOTE);
        }
    }

    private boolean loteSigueActivo(Connection conn, int loteId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM lotes WHERE id = ? AND fecha_fin IS NULL")) {
            ps.setInt(1, loteId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void aplicarMovimientoLote(Connection conn, int loteId,
                                       LoteMovimiento movimiento) throws SQLException {
        String sqlSelect =
            "SELECT codigo_catalogo, cantidad, estado, lote_id " +
            "FROM equipo_materiales WHERE id = ? AND equipo_id = ? FOR UPDATE";
        String sqlInsert =
            "INSERT INTO equipo_materiales (equipo_id, codigo_catalogo, cantidad, estado, lote_id) " +
            "VALUES (?, ?, ?, ?, ?)";

        int materialId = movimiento.getMaterialId();
        int equipoId = movimiento.getEquipoId();
        int cantidadMover = movimiento.getCantidad();

        int codigo;
        int cantidadActual;
        String estadoActual;
        Integer loteIdActual;

        try (PreparedStatement pstmt = conn.prepareStatement(sqlSelect)) {
            pstmt.setInt(1, materialId);
            pstmt.setInt(2, equipoId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) throw new SQLException("No se encontró el lote a mover: " + materialId);
                codigo = rs.getInt("codigo_catalogo");
                cantidadActual = rs.getInt("cantidad");
                estadoActual = rs.getString("estado");
                int l = rs.getInt("lote_id");
                loteIdActual = rs.wasNull() ? null : l;
            }
        }

        // Guarda de concurrencia, ANTES de validar la cantidad: si el estado cambió o el material
        // ya está en otro lote abierto, el saldo que vio el operador es de otra realidad y
        // "cantidad inválida" sería un mensaje engañoso. El throw revierte el lote entero.
        guardarConcurrencia(conn, estadoActual, loteIdActual, movimiento);

        if (cantidadMover <= 0 || cantidadMover > cantidadActual) {
            throw new SQLException("Cantidad inválida para mover en lote: " + materialId);
        }

        if (cantidadMover == cantidadActual) {
            actualizarEstadoMaterial(conn, materialId, EstadoEquipo.ESTERILIZANDO.getNombre(), loteId);
            registrarMovimiento(conn, materialId, equipoId, cantidadMover, estadoActual,
                EstadoEquipo.ESTERILIZANDO.getNombre());
        } else {
            int cantidadRestante = cantidadActual - cantidadMover;
            actualizarCantidadMaterial(conn, materialId, cantidadRestante);

            int nuevoMaterialId;
            try (PreparedStatement pstmt = conn.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setInt(1, equipoId);
                pstmt.setInt(2, codigo);
                pstmt.setInt(3, cantidadMover);
                pstmt.setString(4, EstadoEquipo.ESTERILIZANDO.getNombre());
                pstmt.setInt(5, loteId);
                pstmt.executeUpdate();
                try (ResultSet rsNuevo = pstmt.getGeneratedKeys()) {
                    if (!rsNuevo.next()) throw new SQLException("No se generó ID para el nuevo lote");
                    nuevoMaterialId = rsNuevo.getInt(1);
                }
            }

            registrarMovimiento(conn, nuevoMaterialId, equipoId, cantidadMover, estadoActual,
                EstadoEquipo.ESTERILIZANDO.getNombre());
        }
    }

    /**
     * Inserta la cabecera del lote.
     *
     * <p>El {@code catch} está acotado al {@code executeUpdate()} de <b>esta</b> sentencia: una
     * violación de integridad de otra sentencia de la transacción no tiene nada que ver con la
     * secuencia y no debe confundirse con ella.</p>
     */
    private int insertarLote(Connection conn, String idNegocio, int anio, int secuencia,
                             String autoclaveNombre, int capacidadTotal, int capacidadUsada) throws SQLException {
        String sql = "INSERT INTO lotes (id_negocio, anio, secuencia, autoclave_nombre, " +
                     "capacidad_total, capacidad_usada, fecha_inicio) " +
                     "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, idNegocio);
            pstmt.setInt(2, anio);
            pstmt.setInt(3, secuencia);
            pstmt.setString(4, autoclaveNombre);
            pstmt.setInt(5, capacidadTotal);
            pstmt.setInt(6, capacidadUsada);
            try {
                pstmt.executeUpdate();
            } catch (SQLException e) {
                // Puede ser el UNIQUE (id_negocio) o la FK a autoclaves: quién fue lo decide
                // lanzarLote, fuera de esta transacción.
                if (ErroresSql.esViolacionDeIntegridad(e)) {
                    throw new SecuenciaDuplicadaException(idNegocio, e);
                }
                throw e;
            }
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("No se generó ID para el lote");
    }

    // ── Mappers ──────────────────────────────────────────────────────────────

    private Lote mapLote(ResultSet rs) throws SQLException {
        Timestamp inicio = rs.getTimestamp("fecha_inicio");
        Timestamp fin    = rs.getTimestamp("fecha_fin");
        LocalDateTime fechaInicio = inicio != null
            ? LocalDateTime.ofInstant(inicio.toInstant(), ZoneId.systemDefault()) : null;
        LocalDateTime fechaFin = fin != null
            ? LocalDateTime.ofInstant(fin.toInstant(), ZoneId.systemDefault()) : null;
        String estado = rs.getString("estado");
        if (estado == null) estado = "ACTIVO";

        return new Lote(
            rs.getInt("id"),
            rs.getString("id_negocio"),
            rs.getInt("anio"),
            rs.getInt("secuencia"),
            rs.getString("autoclave_nombre"),
            rs.getInt("capacidad_total"),
            rs.getInt("capacidad_usada"),
            fechaInicio,
            fechaFin,
            estado,
            new ArrayList<>()
        );
    }

    private Lote mapLoteConMateriales(ResultSet rs) throws SQLException {
        Lote lote = mapLote(rs);
        List<LoteMaterialInfo> materiales = obtenerMaterialesPorLote(lote.getId());
        return new Lote(
            lote.getId(), lote.getIdNegocio(), lote.getAnio(), lote.getSecuencia(),
            lote.getAutoclaveNombre(), lote.getCapacidadTotal(), lote.getCapacidadUsada(),
            lote.getFechaInicio(), lote.getFechaFin(), lote.getEstado(), materiales
        );
    }

    private Lote obtenerLotePorId(Connection conn, int loteId) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "SELECT id, id_negocio, anio, secuencia, autoclave_nombre, " +
                "capacidad_total, capacidad_usada, fecha_inicio, fecha_fin, estado " +
                "FROM lotes WHERE id = ?")) {
            pstmt.setInt(1, loteId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return mapLote(rs);
            }
        }
        throw new SQLException("No se encontró el lote recién insertado con id=" + loteId);
    }

    // ── Helpers para equipo_otros_materiales ─────────────────────────────────

    /**
     * Aplica un movimiento de lote sobre equipo_otros_materiales.
     * materialId < 0 indica REMITO (no hay fila real); se crea una nueva.
     * materialId > 0 indica DETALLES (fila existente).
     */
    private void aplicarMovimientoLoteOtros(Connection conn, int loteId,
                                             LoteMovimiento movimiento) throws SQLException {
        int materialId    = movimiento.getMaterialId();
        int equipoOtrosId = movimiento.getEquipoId();
        int cantidadMover = movimiento.getCantidad();

        if (materialId < 0) {
            // REMITO: leer estado actual y cantidad total original
            String estadoActual;
            int remitoCantidad;
            // FOR UPDATE por el mismo motivo que en el camino de DETALLES: la guarda de abajo
            // compara contra este `estado`, y sin el lock dos lotes armados a la vez lo leerían
            // los dos del mismo snapshot y pasarían los dos.
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT estado, remito_cantidad FROM equipo_otros WHERE id = ? FOR UPDATE")) {
                ps.setInt(1, equipoOtrosId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new SQLException("equipo_otros no encontrado: " + equipoOtrosId);
                    estadoActual   = rs.getString("estado");
                    remitoCantidad = rs.getInt("remito_cantidad");
                }
            }

            // Guarda de concurrencia del REMITO: su guarda es el estado del encabezado (no hay
            // fila de material, y el lote_id vive en las filas materializadas). Dos lotes partiendo
            // el mismo REMITO en paralelo es un caso legítimo — el equipo sigue en su estado hasta
            // que se procesan todos los elementos —, así que sólo choca si el encabezado ya avanzó
            // entero a otro estado.
            guardarConcurrencia(conn, estadoActual, null, movimiento);

            int catalogoId = obtenerOCrearCatalogoOtros(conn, "Elementos");

            // Tras el primer split existen filas reales en equipo_otros_materiales.
            // La cantidad disponible real es la suma de esas filas (no remito_cantidad).
            // Si no hay filas todavía, es el primer split y se usa remito_cantidad.
            int disponibleMaterializado;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(SUM(cantidad), 0) FROM equipo_otros_materiales " +
                    "WHERE equipo_otros_id = ? AND estado = ? AND lote_id IS NULL")) {
                ps.setInt(1, equipoOtrosId);
                ps.setString(2, estadoActual);
                try (ResultSet rs = ps.executeQuery()) {
                    disponibleMaterializado = rs.next() ? rs.getInt(1) : 0;
                }
            }

            if (disponibleMaterializado > 0) {
                // Split posterior: eliminar filas "disponibles" sin lote (sin movimientos asociados)
                // y recrearlas con las cantidades correctas vía materializarRemitoSplit.
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM equipo_otros_materiales " +
                        "WHERE equipo_otros_id = ? AND estado = ? AND lote_id IS NULL")) {
                    ps.setInt(1, equipoOtrosId);
                    ps.setString(2, estadoActual);
                    ps.executeUpdate();
                }
                EquipoOtrosMaterialHelper.materializarRemitoSplit(
                        conn, equipoOtrosId, catalogoId, disponibleMaterializado,
                        estadoActual, cantidadMover,
                        EstadoEquipo.ESTERILIZANDO.getNombre(), loteId);
            } else {
                // Primer split: no existen filas reales aún
                EquipoOtrosMaterialHelper.materializarRemitoSplit(
                        conn, equipoOtrosId, catalogoId, remitoCantidad,
                        estadoActual, cantidadMover,
                        EstadoEquipo.ESTERILIZANDO.getNombre(), loteId);
            }
            return;
        }

        // DETALLES: fila real
        String sqlSelect =
            "SELECT catalogo_otros_id, descripcion, cantidad, estado, lote_id " +
            "FROM equipo_otros_materiales WHERE id = ? AND equipo_otros_id = ? FOR UPDATE";

        int    catalogoId;
        String descripcion;
        int    cantidadActual;
        String estadoActual;
        Integer loteIdActual;

        try (PreparedStatement ps = conn.prepareStatement(sqlSelect)) {
            ps.setInt(1, materialId);
            ps.setInt(2, equipoOtrosId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("No se encontró material otros para lote: " + materialId);
                catalogoId     = rs.getInt("catalogo_otros_id");
                descripcion    = rs.getString("descripcion");
                cantidadActual = rs.getInt("cantidad");
                estadoActual   = rs.getString("estado");
                int l = rs.getInt("lote_id");
                loteIdActual   = rs.wasNull() ? null : l;
            }
        }

        guardarConcurrencia(conn, estadoActual, loteIdActual, movimiento);

        if (cantidadMover <= 0 || cantidadMover > cantidadActual)
            throw new SQLException("Cantidad inválida para mover en lote otros: " + materialId);

        if (cantidadMover == cantidadActual) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE equipo_otros_materiales SET estado = ?, lote_id = ? WHERE id = ?")) {
                ps.setString(1, EstadoEquipo.ESTERILIZANDO.getNombre());
                ps.setInt(2, loteId);
                ps.setInt(3, materialId);
                ps.executeUpdate();
            }
            registrarMovimientoOtros(conn, materialId, equipoOtrosId, cantidadMover, estadoActual,
                EstadoEquipo.ESTERILIZANDO.getNombre());
        } else {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE equipo_otros_materiales SET cantidad = ? WHERE id = ?")) {
                ps.setInt(1, cantidadActual - cantidadMover);
                ps.setInt(2, materialId);
                ps.executeUpdate();
            }
            int nuevoMatId;
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO equipo_otros_materiales " +
                    "(equipo_otros_id, catalogo_otros_id, descripcion, cantidad, estado, lote_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, equipoOtrosId);
                ps.setInt(2, catalogoId);
                ps.setString(3, descripcion);
                ps.setInt(4, cantidadMover);
                ps.setString(5, EstadoEquipo.ESTERILIZANDO.getNombre());
                ps.setInt(6, loteId);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (!rs.next()) throw new SQLException("No se generó ID para split material otros en lote");
                    nuevoMatId = rs.getInt(1);
                }
            }
            registrarMovimientoOtros(conn, nuevoMatId, equipoOtrosId, cantidadMover, estadoActual,
                EstadoEquipo.ESTERILIZANDO.getNombre());
        }
    }

    private int obtenerOCrearCatalogoOtros(Connection conn, String descripcion) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM catalogo_otros WHERE descripcion = ?")) {
            ps.setString(1, descripcion);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO catalogo_otros (descripcion) VALUES (?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, descripcion);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("No se pudo obtener o crear catalogo_otros: " + descripcion);
    }

    /**
     * Deriva el estado del equipo "otros" desde sus materiales.
     * Delega en {@link EquipoOtrosMaterialHelper#recalcularEstadoEquipo}, igual que
     * {@link #recalcularEstadoEquipo} delega en el helper de ortopedias.
     */
    private void recalcularEstadoEquipoOtros(Connection conn, int equipoOtrosId) throws SQLException {
        EquipoOtrosMaterialHelper.recalcularEstadoEquipo(conn, equipoOtrosId);
    }

    private void actualizarEstadoMaterialOtros(Connection conn, int materialId,
                                               String nuevoEstado) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE equipo_otros_materiales SET estado = ? WHERE id = ?")) {
            ps.setString(1, nuevoEstado);
            ps.setInt(2, materialId);
            ps.executeUpdate();
        }
    }

    private void registrarMovimientoOtros(Connection conn, int materialId, int equipoOtrosId,
                                          int cantidad, String estadoOrigen,
                                          String estadoDestino) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO otros_material_movimientos " +
                "(material_id, equipo_otros_id, cantidad, estado_origen, estado_destino) " +
                "VALUES (?, ?, ?, ?, ?)")) {
            ps.setInt(1, materialId);
            ps.setInt(2, equipoOtrosId);
            ps.setInt(3, cantidad);
            if (estadoOrigen != null) ps.setString(4, estadoOrigen);
            else                      ps.setNull(4, Types.VARCHAR);
            ps.setString(5, estadoDestino);
            ps.executeUpdate();
        }
    }

    /** Lee los litros del ingreso en este lote (lote_otros_volumenes) y los acumula en equipo_otros.volumen_equipo. */
    private void acumularVolumenEquipoOtros(Connection conn, int equipoOtrosId,
                                            int loteId) throws SQLException {
        int suma = 0;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(SUM(volumen), 0) FROM lote_otros_volumenes " +
                "WHERE equipo_otros_id = ? AND lote_id = ?")) {
            ps.setInt(1, equipoOtrosId);
            ps.setInt(2, loteId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) suma = rs.getInt(1);
            }
        }
        if (suma > 0) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE equipo_otros SET volumen_equipo = volumen_equipo + ? WHERE id = ?")) {
                ps.setInt(1, suma);
                ps.setInt(2, equipoOtrosId);
                ps.executeUpdate();
            }
        }
    }

    private String obtenerEstadoAnteriorOtros(Connection conn, int materialId,
                                              String estadoDestino, String valorPorDefecto)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT estado_origen FROM otros_material_movimientos " +
                "WHERE material_id = ? AND estado_destino = ? ORDER BY fecha DESC LIMIT 1")) {
            ps.setInt(1, materialId);
            ps.setString(2, estadoDestino);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String est = rs.getString("estado_origen");
                    return est != null ? est : valorPorDefecto;
                }
            }
        }
        return valorPorDefecto;
    }

}