package com.example.perf;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Sembrador sintético sobre MySQL local, absorbido tal cual del Paso 1.5 de
 * {@code plans/rendimiento-historiales.md} hacia el Paso 3 de
 * {@code plans/conexiones-y-paginacion.md}: mide "el verdadero delay del pintado" (¿cuánto pesa
 * {@code setRowCount(0)} + {@code addRow} por fila en el EDT?) con volumen que la base de
 * desarrollo no tiene.
 *
 * <p><b>No es parte de {@code mvn test}.</b> {@link EnabledIfSystemProperty} lo salta siempre a
 * menos que se pida explícitamente con {@code -Daptium.perf=true}, y aun así su nombre no matchea
 * los patrones por defecto de Surefire (ningún {@code *Test.java}), así que ni un
 * {@code mvn test} corrido sin filtro lo alcanzaría por accidente.
 *
 * <p><b>Corre contra su propio MySQL local, nunca contra la base de desarrollo ni la de
 * producción.</b> Arrancarlo:
 * <pre>{@code
 * docker run -d --name aptium-perf -e MYSQL_ROOT_PASSWORD=perf \
 *     -e MYSQL_DATABASE=aptium_perf -p 3307:3306 mysql:8
 * }</pre>
 * Puerto 3307 a propósito: no colisiona con un MySQL local que ya esté en uso en 3306.
 *
 * <pre>{@code
 * mvn test -Dtest=SembradorRendimiento -Daptium.perf=true
 * mvn test -Dtest=SembradorRendimiento -Daptium.perf=true -Daptium.perf.factor=5
 * }</pre>
 *
 * <p><b>Tres guardas de seguridad, no negociables — un sembrador que se equivoca de base destruye
 * datos de producción:</b>
 * <ol>
 *   <li>el nombre de la base tiene que terminar en {@code _perf};</li>
 *   <li>el host tiene que ser {@code localhost} o {@code 127.0.0.1};</li>
 *   <li>las tablas que este sembrador puebla tienen que estar vacías, o contener sólo filas
 *       marcadas por una corrida anterior de este mismo sembrador (prefijo {@code PERF-} en
 *       {@code equipos.paciente}/{@code lotes.id_negocio}, o el cliente sintético
 *       {@value #CLIENTE_MARCA} en {@code equipo_otros.nro_cliente}/
 *       {@code ingresos_lavadero.cliente_id}). {@code ciclos_lavadero} no tiene un campo de texto
 *       para marcar, así que su regla es más estricta: tiene que estar vacía.</li>
 * </ol>
 * Las tres fallan con {@link IllegalStateException} y un mensaje explícito, no con un {@code
 * assert} mudo.
 *
 * <p><b>La forma de los datos es un supuesto documentado, no un dato de producción.</b> Los
 * conteos reales relevados en el Paso 2 de {@code plans/conexiones-y-paginacion.md}
 * (`equipos` 482, `equipo_otros` 946, `lotes` 917, `ingresos_lavadero` 0, `ciclos_lavadero` 0, con
 * un solo puesto en uso diario) son hoy más chicos que los de acá: este sembrador existe
 * justamente para ver qué pasa **antes** de que ese volumen llegue con los otros dos puestos, no
 * para reproducir el presente.
 */
@EnabledIfSystemProperty(named = "aptium.perf", matches = "true")
class SembradorRendimiento {

    private static final Logger log = LoggerFactory.getLogger(SembradorRendimiento.class);

    // ---- Forma de los datos: supuesto documentado, escalable con -Daptium.perf.factor ----
    static final int FACTOR              = Integer.getInteger("aptium.perf.factor", 1);
    static final int EQUIPOS_ORTOPEDIA   = 3_000 * FACTOR;
    static final int EQUIPOS_OTROS       = 3_000 * FACTOR;
    static final int MATERIALES_POR_EQUIPO = 4;
    static final int LOTES               = 2_000 * FACTOR;
    static final int CICLOS_LAVADERO     = 2_000 * FACTOR;
    static final int INGRESOS_LAVADERO   = 3_000 * FACTOR;
    static final int PCT_ACTIVOS         = 30;   // % que todavía no está entregado/finalizado

    private static final int TAMANO_LOTE_INSERT = 500;

    private static final String MARCA_TEXTO   = "PERF-";
    private static final String CLIENTE_MARCA = "PERF-SEMBRADOR-CLIENTE";

    private static final String[] ESTADOS_EQUIPO_ACTIVO =
        { "Nuevo", "Lavando", "Lavado", "Empaquetado", "Esterilizando", "Esterilizado" };

    @Test
    void sembrar() throws Exception {
        String host = env("DB_HOST", "localhost");
        String port = env("DB_PORT", "3307");
        String name = env("DB_NAME", "aptium_perf");
        String user = env("DB_USER", "root");
        String pass = env("DB_PASS", "perf");

        exigirNombreTerminaEnPerf(name);
        exigirHostLocal(host);

        String urlSinBase = "jdbc:mysql://" + host + ":" + port
            + "/?serverTimezone=UTC&connectionTimeZone=LOCAL&sslMode=REQUIRED";
        String url = "jdbc:mysql://" + host + ":" + port + "/" + name
            + "?serverTimezone=UTC&connectionTimeZone=LOCAL&sslMode=REQUIRED";

        crearBaseSiNoExiste(urlSinBase, name, user, pass);
        migrarEsquema(url, user, pass);

        log.info("Sembrando FACTOR={} contra {} ({} equipos ortopedia, {} equipo_otros, {} lotes, "
                + "{} ciclos, {} ingresos de lavadero)",
            FACTOR, url, EQUIPOS_ORTOPEDIA, EQUIPOS_OTROS, LOTES, CICLOS_LAVADERO, INGRESOS_LAVADERO);

        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            conn.setAutoCommit(false);

            int clienteMarcaId = obtenerOCrearClienteMarca(conn);
            exigirTablasSinDatosAjenos(conn, clienteMarcaId);

            List<Integer> instituciones = idsDe(conn, "SELECT id FROM instituciones");
            List<Integer> profesionales = idsDe(conn, "SELECT id FROM profesionales");
            List<Integer> codigosCatalogo = idsDe(conn, "SELECT codigo FROM catalogo_descripciones");
            List<Integer> catalogoOtros = idsDe(conn, "SELECT id FROM catalogo_otros");
            List<String> autoclaves = nombresDe(conn, "SELECT nombre FROM autoclaves");
            List<Integer> jabones = idsDe(conn, "SELECT id FROM catalogo_jabones");

            sembrarEquiposOrtopedia(conn, instituciones, profesionales, codigosCatalogo);
            sembrarEquiposOtros(conn, clienteMarcaId, catalogoOtros);
            sembrarLotes(conn, autoclaves);
            sembrarCiclosLavadero(conn, jabones);
            sembrarIngresosLavadero(conn, clienteMarcaId);

            conn.commit();
        }

        log.info("Sembrado completo. Apuntá la app a {}:{}/{} para tomar el baseline con volumen "
                + "(ver la tabla del Paso 3 de plans/conexiones-y-paginacion.md).",
            host, port, name);
    }

    // ---- Guardas de seguridad ----

    private static void exigirNombreTerminaEnPerf(String nombreBase) {
        if (!nombreBase.endsWith("_perf")) {
            throw new IllegalStateException(
                "DB_NAME='" + nombreBase + "' no termina en '_perf'. Este sembrador sólo corre "
                + "contra una base dedicada de rendimiento; abortado para no arriesgar la base "
                + "de desarrollo o producción.");
        }
    }

    private static void exigirHostLocal(String host) {
        if (!"localhost".equals(host) && !"127.0.0.1".equals(host)) {
            throw new IllegalStateException(
                "DB_HOST='" + host + "' no es local. Este sembrador sólo corre contra MySQL en "
                + "localhost o 127.0.0.1; abortado para no sembrar por accidente un servidor "
                + "remoto (que podría ser producción).");
        }
    }

    /**
     * Aborta si alguna de las tablas que este sembrador puebla ya tiene filas que no le
     * pertenecen. Sin esto, correrlo dos veces contra una base con datos reales de otro origen
     * mezclaría lo sintético con lo real sin avisar.
     */
    private void exigirTablasSinDatosAjenos(Connection conn, int clienteMarcaId) throws SQLException {
        exigirVaciaOMarcada(conn, "equipos", "paciente LIKE '" + MARCA_TEXTO + "%'");
        exigirVaciaOMarcada(conn, "equipo_otros", "nro_cliente = " + clienteMarcaId);
        exigirVaciaOMarcada(conn, "lotes", "id_negocio LIKE '" + MARCA_TEXTO + "%'");
        exigirVaciaOMarcada(conn, "ingresos_lavadero", "cliente_id = " + clienteMarcaId);
        // ciclos_lavadero no tiene un campo de texto para marcar: la regla es más estricta.
        exigirVacia(conn, "ciclos_lavadero");
    }

    private void exigirVaciaOMarcada(Connection conn, String tabla, String condicionMarca) throws SQLException {
        long total = contar(conn, "SELECT COUNT(*) FROM " + tabla);
        if (total == 0) {
            return;
        }
        long marcadas = contar(conn, "SELECT COUNT(*) FROM " + tabla + " WHERE " + condicionMarca);
        if (marcadas != total) {
            throw new IllegalStateException(
                "La tabla '" + tabla + "' tiene " + (total - marcadas) + " fila(s) que no fueron "
                + "sembradas por este sembrador (de " + total + " en total). Abortado: un "
                + "sembrador que mezcla datos ajenos con sintéticos puede destruirlos en una "
                + "corrida posterior. Si esto es una base de rendimiento nueva, verificá DB_NAME "
                + "y DB_HOST.");
        }
    }

    private void exigirVacia(Connection conn, String tabla) throws SQLException {
        long total = contar(conn, "SELECT COUNT(*) FROM " + tabla);
        if (total != 0) {
            throw new IllegalStateException(
                "La tabla '" + tabla + "' ya tiene " + total + " fila(s) y no hay forma de "
                + "distinguir cuáles son sintéticas. Abortado: correr este sembrador dos veces "
                + "sin vaciarla antes duplicaría o mezclaría datos.");
        }
    }

    // ---- Arranque de la base de rendimiento ----

    private static void crearBaseSiNoExiste(String urlSinBase, String nombreBase, String user, String pass)
            throws SQLException {
        if (!nombreBase.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalStateException("Nombre de base inválido: '" + nombreBase + "'");
        }
        try (Connection conn = DriverManager.getConnection(urlSinBase, user, pass);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE DATABASE IF NOT EXISTS " + nombreBase);
        }
    }

    private static void migrarEsquema(String url, String user, String pass) {
        Flyway flyway = Flyway.configure()
            .dataSource(url, user, pass)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .baselineVersion("1")
            .outOfOrder(true)
            .load();
        flyway.migrate();
    }

    // ---- Siembra por tabla ----

    private int obtenerOCrearClienteMarca(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT IGNORE INTO clientes (nombre) VALUES (?)")) {
            ps.setString(1, CLIENTE_MARCA);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM clientes WHERE nombre = ?")) {
            ps.setString(1, CLIENTE_MARCA);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private void sembrarEquiposOrtopedia(Connection conn, List<Integer> instituciones,
            List<Integer> profesionales, List<Integer> codigosCatalogo) throws SQLException {
        String sqlEquipo =
            "INSERT INTO equipos (nro_cliente, nro_profesional, paciente, nro_institucion, "
            + "estado, requiere_lavado, requiere_empaque, fecha_ingreso) VALUES (?,?,?,?,?,1,1,?)";
        String sqlMaterial =
            "INSERT INTO equipo_materiales (equipo_id, codigo_catalogo, cantidad, estado) "
            + "VALUES (?,?,?,?)";

        // El cliente de estos equipos es cualquiera de los ya seedeados por V3 (no llevan marca
        // propia porque paciente ya identifica la fila como sintética; no hace falta forzar el
        // cliente también).
        List<Integer> clientesExistentes = idsDe(conn, "SELECT id FROM clientes");

        try (PreparedStatement psEquipo = conn.prepareStatement(sqlEquipo, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement psMaterial = conn.prepareStatement(sqlMaterial)) {

            int pendientesMaterial = 0;
            for (int i = 1; i <= EQUIPOS_ORTOPEDIA; i++) {
                String estado = elegirEstado(i, ESTADOS_EQUIPO_ACTIVO, "Entregado");
                psEquipo.setInt(1, elegir(clientesExistentes, i));
                setIntONull(psEquipo, 2, profesionales.isEmpty() ? null : elegir(profesionales, i));
                psEquipo.setString(3, MARCA_TEXTO + "PACIENTE-" + i);
                psEquipo.setInt(4, elegir(instituciones, i));
                psEquipo.setString(5, estado);
                psEquipo.setTimestamp(6, haceHoras(i));
                psEquipo.executeUpdate();

                int equipoId;
                try (ResultSet rs = psEquipo.getGeneratedKeys()) {
                    rs.next();
                    equipoId = rs.getInt(1);
                }

                for (int m = 0; m < MATERIALES_POR_EQUIPO; m++) {
                    psMaterial.setInt(1, equipoId);
                    psMaterial.setInt(2, elegir(codigosCatalogo, i + m));
                    psMaterial.setInt(3, 1 + (m % 3));
                    psMaterial.setString(4, estado);
                    psMaterial.addBatch();
                    pendientesMaterial++;
                }
                if (pendientesMaterial >= TAMANO_LOTE_INSERT) {
                    psMaterial.executeBatch();
                    pendientesMaterial = 0;
                }
            }
            if (pendientesMaterial > 0) {
                psMaterial.executeBatch();
            }
        }
        log.info("Sembrados {} equipos de ortopedia con {} materiales cada uno",
            EQUIPOS_ORTOPEDIA, MATERIALES_POR_EQUIPO);
    }

    private void sembrarEquiposOtros(Connection conn, int clienteMarcaId, List<Integer> catalogoOtros)
            throws SQLException {
        String sqlEquipo =
            "INSERT INTO equipo_otros (nro_cliente, estado, requiere_lavado, requiere_empaque, "
            + "tipo_ingreso, volumen_equipo, fecha_ingreso) VALUES (?,?,1,1,'DETALLES',0,?)";
        String sqlMaterial =
            "INSERT INTO equipo_otros_materiales (equipo_otros_id, catalogo_otros_id, "
            + "descripcion, cantidad, estado) VALUES (?,?,?,?,?)";

        try (PreparedStatement psEquipo = conn.prepareStatement(sqlEquipo, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement psMaterial = conn.prepareStatement(sqlMaterial)) {

            int pendientesMaterial = 0;
            for (int i = 1; i <= EQUIPOS_OTROS; i++) {
                String estado = elegirEstado(i, ESTADOS_EQUIPO_ACTIVO, "Entregado");
                psEquipo.setInt(1, clienteMarcaId);
                psEquipo.setString(2, estado);
                psEquipo.setTimestamp(3, haceHoras(i));
                psEquipo.executeUpdate();

                int equipoId;
                try (ResultSet rs = psEquipo.getGeneratedKeys()) {
                    rs.next();
                    equipoId = rs.getInt(1);
                }

                for (int m = 0; m < MATERIALES_POR_EQUIPO; m++) {
                    int catalogoId = elegir(catalogoOtros, i + m);
                    psMaterial.setInt(1, equipoId);
                    psMaterial.setInt(2, catalogoId);
                    psMaterial.setString(3, MARCA_TEXTO + "MATERIAL-" + catalogoId);
                    psMaterial.setInt(4, 1 + (m % 3));
                    psMaterial.setString(5, estado);
                    psMaterial.addBatch();
                    pendientesMaterial++;
                }
                if (pendientesMaterial >= TAMANO_LOTE_INSERT) {
                    psMaterial.executeBatch();
                    pendientesMaterial = 0;
                }
            }
            if (pendientesMaterial > 0) {
                psMaterial.executeBatch();
            }
        }
        log.info("Sembrados {} equipos 'otros' con {} materiales cada uno",
            EQUIPOS_OTROS, MATERIALES_POR_EQUIPO);
    }

    private void sembrarLotes(Connection conn, List<String> autoclaves) throws SQLException {
        String sql =
            "INSERT INTO lotes (id_negocio, anio, secuencia, autoclave_nombre, capacidad_total, "
            + "capacidad_usada, fecha_inicio, fecha_fin, estado) VALUES (?,?,?,?,?,?,?,?,?)";
        int anio = LocalDateTime.now().getYear();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int pendientes = 0;
            for (int i = 1; i <= LOTES; i++) {
                boolean activo = esActivo(i);
                ps.setString(1, MARCA_TEXTO + "L-" + i);
                ps.setInt(2, anio);
                ps.setInt(3, i);
                ps.setString(4, elegir(autoclaves, i));
                ps.setInt(5, 300);
                ps.setInt(6, 50 + (i % 250));
                ps.setTimestamp(7, haceHoras(i));
                if (activo) {
                    ps.setNull(8, java.sql.Types.TIMESTAMP);
                } else {
                    ps.setTimestamp(8, haceHoras(i - 1));
                }
                ps.setString(9, activo ? "ACTIVO" : "FINALIZADO");
                ps.addBatch();
                pendientes++;
                if (pendientes >= TAMANO_LOTE_INSERT) {
                    ps.executeBatch();
                    pendientes = 0;
                }
            }
            if (pendientes > 0) {
                ps.executeBatch();
            }
        }
        log.info("Sembrados {} lotes", LOTES);
    }

    private void sembrarCiclosLavadero(Connection conn, List<Integer> jabones) throws SQLException {
        String sql =
            "INSERT INTO ciclos_lavadero (lavarropas_numero, jabon_id, litros_jabon, suavizante, "
            + "potenciador, litros_totales, fecha_inicio, fecha_fin, estado, tipo_lavado) "
            + "VALUES (?,?,?,?,?,?,?,?,?,?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int pendientes = 0;
            for (int i = 1; i <= CICLOS_LAVADERO; i++) {
                boolean activo = esActivo(i);
                ps.setInt(1, 1 + (i % 13));               // lavarropas 1-13, sembrados en V10
                ps.setInt(2, elegir(jabones, i));
                ps.setBigDecimal(3, new java.math.BigDecimal("0.5"));
                ps.setBoolean(4, i % 2 == 0);
                ps.setBoolean(5, i % 3 == 0);
                ps.setBigDecimal(6, new java.math.BigDecimal("13.0"));
                ps.setTimestamp(7, haceHoras(i));
                if (activo) {
                    ps.setNull(8, java.sql.Types.TIMESTAMP);
                } else {
                    ps.setTimestamp(8, haceHoras(i - 1));
                }
                ps.setString(9, activo ? "ACTIVO" : "FINALIZADO");
                ps.setString(10, i % 2 == 0 ? "LIMPIO" : "SUCIO");
                ps.addBatch();
                pendientes++;
                if (pendientes >= TAMANO_LOTE_INSERT) {
                    ps.executeBatch();
                    pendientes = 0;
                }
            }
            if (pendientes > 0) {
                ps.executeBatch();
            }
        }
        log.info("Sembrados {} ciclos de lavadero", CICLOS_LAVADERO);
    }

    private void sembrarIngresosLavadero(Connection conn, int clienteMarcaId) throws SQLException {
        String sqlIngreso =
            "INSERT INTO ingresos_lavadero (cliente_id, fecha_ingreso, peso_total_kg, estado) "
            + "VALUES (?,?,?,?)";
        String sqlBolsa =
            "INSERT INTO bolsas_lavadero (ingreso_id, peso_kg) VALUES (?,?)";
        String[] estados = { "PENDIENTE", "CLASIFICADO", "LAVADO", "FINALIZADO" };

        try (PreparedStatement psIngreso = conn.prepareStatement(sqlIngreso, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement psBolsa = conn.prepareStatement(sqlBolsa)) {

            int pendientesBolsa = 0;
            for (int i = 1; i <= INGRESOS_LAVADERO; i++) {
                String estado = estados[i % estados.length];
                psIngreso.setInt(1, clienteMarcaId);
                psIngreso.setTimestamp(2, haceHoras(i));
                psIngreso.setBigDecimal(3, new java.math.BigDecimal(10 + (i % 40)));
                psIngreso.setString(4, estado);
                psIngreso.executeUpdate();

                int ingresoId;
                try (ResultSet rs = psIngreso.getGeneratedKeys()) {
                    rs.next();
                    ingresoId = rs.getInt(1);
                }

                psBolsa.setInt(1, ingresoId);
                psBolsa.setBigDecimal(2, new java.math.BigDecimal(5 + (i % 10)));
                psBolsa.addBatch();
                pendientesBolsa++;
                if (pendientesBolsa >= TAMANO_LOTE_INSERT) {
                    psBolsa.executeBatch();
                    pendientesBolsa = 0;
                }
            }
            if (pendientesBolsa > 0) {
                psBolsa.executeBatch();
            }
        }
        log.info("Sembrados {} ingresos de lavadero (con su bolsa)", INGRESOS_LAVADERO);
    }

    // ---- Utilidades ----

    private static String env(String nombre, String porDefecto) {
        String valor = System.getenv(nombre);
        return valor != null ? valor : porDefecto;
    }

    private static String elegirEstado(int i, String[] activos, String terminal) {
        // PCT_ACTIVOS% queda en algún estado intermedio; el resto llega al final del flujo.
        if (i % 100 < PCT_ACTIVOS) {
            return activos[i % activos.length];
        }
        return terminal;
    }

    private static boolean esActivo(int i) {
        return i % 100 < PCT_ACTIVOS;
    }

    private static <T> T elegir(List<T> lista, int i) {
        return lista.get(Math.floorMod(i, lista.size()));
    }

    private static void setIntONull(PreparedStatement ps, int indice, Integer valor) throws SQLException {
        if (valor == null) {
            ps.setNull(indice, java.sql.Types.INTEGER);
        } else {
            ps.setInt(indice, valor);
        }
    }

    private static Timestamp haceHoras(int horas) {
        return Timestamp.valueOf(LocalDateTime.now().minusHours(horas));
    }

    private static long contar(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static List<Integer> idsDe(Connection conn, String sql) throws SQLException {
        List<Integer> lista = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                lista.add(rs.getInt(1));
            }
        }
        return lista;
    }

    private static List<String> nombresDe(Connection conn, String sql) throws SQLException {
        List<String> lista = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                lista.add(rs.getString(1));
            }
        }
        return lista;
    }
}
