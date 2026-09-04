package com.example.features.lotes.dao;

import com.example.AbstractDAOTest;
import com.example.common.constants.Constantes;
import com.example.common.exception.ConflictoConcurrenciaException;
import com.example.common.exception.DatabaseException;
import com.example.features.catalogo.dao.CatalogoOtrosDAO;
import com.example.features.equipos.ortopedias.dao.EquipoDAO;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.model.Material;
import com.example.features.equipos.otros.dao.EquipoOtrosDAO;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.lotes.model.Lote;
import com.example.features.lotes.model.LoteMaterialInfo;
import com.example.features.lotes.model.LoteMovimiento;
import com.example.infrastructure.db.ConnectionPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LoteDAOTest extends AbstractDAOTest {

    private final LoteDAO       dao           = new LoteDAO();
    private final EquipoDAO     equipoDAO     = new EquipoDAO();
    private final EquipoOtrosDAO equipoOtrosDAO = new EquipoOtrosDAO(new CatalogoOtrosDAO());

    // Fixtures inicializados en @BeforeEach
    private Equipo equipo;      // equipo ortopedia con 1 material, cantidad=3
    private int    materialId;  // id del material en equipo_materiales

    // ── Fixtures ──────────────────────────────────────────────────────────────

    @BeforeEach
    void crearEquipoConMaterial() {
        equipo = new Equipo();
        equipo.setNroCliente(1);
        equipo.setNroInstitucion(1);
        equipo.agregarMaterial(new Material(400, "Tornillera", 3));
        equipoDAO.guardarEquipo(equipo);
        // reload para obtener el id generado del material
        equipo = equipoDAO.obtenerPorId(String.valueOf(equipo.getId()));
        materialId = equipo.getMateriales().get(0).getId();
    }

    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM otros_material_movimientos");
        ejecutarSQL("DELETE FROM equipo_otros_materiales");
        ejecutarSQL("DELETE FROM equipo_otros");
        ejecutarSQL("DELETE FROM lotes");
        ejecutarSQL("DELETE FROM equipos");   // ON DELETE CASCADE cubre equipo_materiales y material_movimientos
        ejecutarSQL("DELETE FROM catalogo_otros WHERE descripcion = 'Elementos'");
    }

    // ── lanzarLote — validación ───────────────────────────────────────────────

    @Test
    void lanzarLote_movimientosNulos_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> dao.lanzarLote("E01", 120, 45, null, Map.of()));
    }

    @Test
    void lanzarLote_movimientosVacios_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> dao.lanzarLote("E01", 120, 45, Collections.emptyList(), Map.of()));
    }

    // ── lanzarLote — ortopedia, cantidad total ────────────────────────────────

    @Test
    void lanzarLote_ortopediaCantidadTotal_retornaLoteConDatos() {
        List<LoteMovimiento> movs = List.of(
            new LoteMovimiento(materialId, equipo.getId(), 3, EstadoEquipo.NUEVO)
        );
        Lote lote = dao.lanzarLote("E01", 120, 45, movs, Map.of());

        assertNotNull(lote);
        assertTrue(lote.getId() > 0);
        assertEquals("E01", lote.getAutoclaveNombre());
        assertEquals(120,   lote.getCapacidadTotal());
        assertNull(lote.getFechaFin(), "Lote recién creado no debe tener fecha_fin");
    }

    @Test
    void lanzarLote_ortopediaCantidadTotal_materialCambiaAEsterilizando() {
        List<LoteMovimiento> movs = List.of(
            new LoteMovimiento(materialId, equipo.getId(), 3, EstadoEquipo.NUEVO)
        );
        dao.lanzarLote("E01", 120, 45, movs, Map.of());

        Equipo cargado = equipoDAO.obtenerPorId(String.valueOf(equipo.getId()));
        assertEquals(EstadoEquipo.ESTERILIZANDO, cargado.getMateriales().get(0).getEstado());
    }

    @Test
    void lanzarLote_idNegocioIncrementaPorAnio() {
        List<LoteMovimiento> movs = List.of(
            new LoteMovimiento(materialId, equipo.getId(), 3, EstadoEquipo.NUEVO)
        );
        Lote lote = dao.lanzarLote("E01", 120, 45, movs, Map.of());
        String idNegocio = lote.getIdNegocio();
        assertTrue(idNegocio.startsWith(String.valueOf(LocalDate.now().getYear())));
    }

    // ── lanzarLote — ortopedia, cantidad parcial (split) ─────────────────────

    @Test
    void lanzarLote_ortopediaCantidadParcial_splitaMaterial() {
        // Mueve 1 de 3 → original queda en 2, nuevo material en ESTERILIZANDO con cantidad 1
        List<LoteMovimiento> movs = List.of(
            new LoteMovimiento(materialId, equipo.getId(), 1, EstadoEquipo.NUEVO)
        );
        Lote lote = dao.lanzarLote("E01", 120, 10, movs, Map.of());

        Equipo cargado = equipoDAO.obtenerPorId(String.valueOf(equipo.getId()));
        // Debe haber 2 filas: una en NUEVO (cantidad=2) y otra en ESTERILIZANDO (cantidad=1)
        assertEquals(2, cargado.getMateriales().size());
        int totalCantidad = cargado.getMateriales().stream().mapToInt(Material::getCantidad).sum();
        assertEquals(3, totalCantidad);

        List<LoteMaterialInfo> materialesLote = dao.obtenerMaterialesPorLote(lote.getId());
        assertEquals(1, materialesLote.size());
        assertEquals(1, materialesLote.get(0).getCantidad());
    }

    // ── lanzarLote — equipo_otros, REMITO total (materialId < 0) ────────────

    @Test
    void lanzarLote_otrosRemitoTotal_unaFilaEnLoteConCantidadCompleta() throws SQLException {
        int equipoOtrosId = insertarEquipoOtros(5);
        Lote lote = dao.lanzarLote("E01", 120, 60,
            List.of(new LoteMovimiento(-equipoOtrosId, equipoOtrosId, 5, true, EstadoEquipo.NUEVO)), Map.of());

        List<LoteMaterialInfo> mats = dao.obtenerMaterialesPorLote(lote.getId());
        assertEquals(1, mats.size());
        assertEquals(5, mats.get(0).getCantidad());
    }

    @Test
    void lanzarLote_otrosRemitoTotal_unaFilaEnEquipoOtrosMateriales() throws SQLException {
        int equipoOtrosId = insertarEquipoOtros(5);
        dao.lanzarLote("E01", 120, 60,
            List.of(new LoteMovimiento(-equipoOtrosId, equipoOtrosId, 5, true, EstadoEquipo.NUEVO)), Map.of());

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COUNT(*) FROM equipo_otros_materiales WHERE equipo_otros_id = ?")) {
            ps.setInt(1, equipoOtrosId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
        }
    }

    // ── lanzarLote — equipo_otros, REMITO PARCIAL (split) ────────────────────

    @Test
    void lanzarLote_otrosRemitoParcial_creaDosFilas() throws SQLException {
        int equipoOtrosId = insertarEquipoOtros(50);
        dao.lanzarLote("E01", 120, 10,
            List.of(new LoteMovimiento(-equipoOtrosId, equipoOtrosId, 20, true, EstadoEquipo.NUEVO)), Map.of());

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COUNT(*) FROM equipo_otros_materiales WHERE equipo_otros_id = ?")) {
            ps.setInt(1, equipoOtrosId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1), "Debe haber 2 filas: avanzada + restante");
            }
        }
    }

    @Test
    void lanzarLote_otrosRemitoParcial_sumaCantidadesConservaTotalOriginal() throws SQLException {
        int equipoOtrosId = insertarEquipoOtros(50);
        dao.lanzarLote("E01", 120, 10,
            List.of(new LoteMovimiento(-equipoOtrosId, equipoOtrosId, 20, true, EstadoEquipo.NUEVO)), Map.of());

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT SUM(cantidad) FROM equipo_otros_materiales WHERE equipo_otros_id = ?")) {
            ps.setInt(1, equipoOtrosId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(50, rs.getInt(1), "SUM(cantidad) debe ser siempre remito_cantidad");
            }
        }
    }

    @Test
    void lanzarLote_otrosRemitoParcial_elementosRestantesNoEsterilizando() throws SQLException {
        int equipoOtrosId = insertarEquipoOtros(50);
        dao.lanzarLote("E01", 120, 10,
            List.of(new LoteMovimiento(-equipoOtrosId, equipoOtrosId, 20, true, EstadoEquipo.NUEVO)), Map.of());

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT cantidad FROM equipo_otros_materiales " +
                 "WHERE equipo_otros_id = ? AND estado <> 'Esterilizando'")) {
            ps.setInt(1, equipoOtrosId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Debe existir fila con elementos aún no esterilizando");
                assertEquals(30, rs.getInt("cantidad"));
            }
        }
    }

    @Test
    void lanzarLote_otrosRemitoParcial_equipoNoAvanzaAEsterilizando() throws SQLException {
        int equipoOtrosId = insertarEquipoOtros(50);
        dao.lanzarLote("E01", 120, 10,
            List.of(new LoteMovimiento(-equipoOtrosId, equipoOtrosId, 20, true, EstadoEquipo.NUEVO)), Map.of());

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT estado FROM equipo_otros WHERE id = ?")) {
            ps.setInt(1, equipoOtrosId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertNotEquals("Esterilizando", rs.getString("estado"),
                    "El equipo no debe pasar a Esterilizando mientras quedan elementos sin procesar");
            }
        }
    }

    // ── Bloqueo optimista al lanzar (Paso 5 del plan de concurrencia) ──────────
    // El staging de LotesController se arma sobre un snapshot en memoria; entre que se lee y el
    // operador aprieta "Lanzar", otro cliente puede haber avanzado el material o lanzarlo en otro
    // lote. La guarda en aplicarMovimientoLote / aplicarMovimientoLoteOtros lo detecta y aborta el
    // lote entero — sin dejar la fila de `lotes` recién insertada huérfana.

    private int contarLotes() throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM lotes")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void lanzarLote_ortopediaEstadoCambiadoPorOtraConexion_conflictoYSinLoteHuerfano() throws SQLException {
        // B avanza el material y commitea mientras A tenía el staging armado con el estado NUEVO.
        try (Connection otra = ConnectionPool.getConnection()) {
            otra.setAutoCommit(false);
            try (PreparedStatement ps = otra.prepareStatement(
                    "UPDATE equipo_materiales SET estado = 'Lavando' WHERE id = ?")) {
                ps.setInt(1, materialId);
                ps.executeUpdate();
            }
            otra.commit();
        }

        assertThrows(ConflictoConcurrenciaException.class, () -> dao.lanzarLote("E01", 120, 45,
            List.of(new LoteMovimiento(materialId, equipo.getId(), 3, EstadoEquipo.NUEVO)), Map.of()));

        assertEquals(0, contarLotes(), "el lote se revirtió entero: no quedó fila huérfana en lotes");
        Equipo cargado = equipoDAO.obtenerPorId(String.valueOf(equipo.getId()));
        assertEquals(EstadoEquipo.LAVANDO, cargado.getMateriales().get(0).getEstado(),
            "el cambio de B quedó intacto");
    }

    @Test
    void lanzarLote_ortopediaMaterialYaEnLoteActivo_conflictoYNoCreaSegundoLote() throws SQLException {
        dao.lanzarLote("E01", 120, 45,
            List.of(new LoteMovimiento(materialId, equipo.getId(), 3, EstadoEquipo.NUEVO)), Map.of());
        assertEquals(1, contarLotes());

        // A tenía el material en el staging (estado NUEVO) y no se enteró de que ya fue lanzado.
        assertThrows(ConflictoConcurrenciaException.class, () -> dao.lanzarLote("E02", 120, 45,
            List.of(new LoteMovimiento(materialId, equipo.getId(), 3, EstadoEquipo.NUEVO)), Map.of()));

        assertEquals(1, contarLotes(), "el segundo lote no se creó");
    }

    @Test
    void lanzarLote_ortopediaMaterialDeLoteFallido_seRelanzaSinConflicto() throws SQLException {
        Lote fallido = dao.lanzarLote("E01", 120, 45,
            List.of(new LoteMovimiento(materialId, equipo.getId(), 3, EstadoEquipo.NUEVO)), Map.of());
        dao.marcarLoteFallo(fallido.getId());
        // marcarLoteFallo revierte el estado pero deja el lote_id apuntando al lote fallido:
        // ese material SÍ es relanzable, la guarda no debe darlo por "ya en otro lote".

        Lote nuevo = dao.lanzarLote("E02", 120, 45,
            List.of(new LoteMovimiento(materialId, equipo.getId(), 3, EstadoEquipo.NUEVO)), Map.of());

        assertNotNull(nuevo);
        assertEquals(2, contarLotes());
    }

    @Test
    void lanzarLote_dosLanzamientosParcialesDelMismoMaterial_ambosOkSinConflicto() throws SQLException {
        // El material queda en NUEVO con lote_id NULL tras cada split parcial: dos operadores
        // moviendo subcantidades distintas del mismo material son compatibles, no un choque.
        dao.lanzarLote("E01", 120, 10,
            List.of(new LoteMovimiento(materialId, equipo.getId(), 1, EstadoEquipo.NUEVO)), Map.of());
        Lote segundo = dao.lanzarLote("E02", 120, 10,
            List.of(new LoteMovimiento(materialId, equipo.getId(), 1, EstadoEquipo.NUEVO)), Map.of());

        assertNotNull(segundo);
        assertEquals(2, contarLotes());
    }

    @Test
    void lanzarLote_otrosDetallesEstadoCambiadoPorOtraConexion_conflictoYSinLoteHuerfano() throws SQLException {
        int[] fixture = insertarEquipoOtrosDetalles();
        int equipoOtrosId  = fixture[0];
        int otrosMaterialId = fixture[1];

        try (Connection otra = ConnectionPool.getConnection()) {
            otra.setAutoCommit(false);
            try (PreparedStatement ps = otra.prepareStatement(
                    "UPDATE equipo_otros_materiales SET estado = 'Lavando' WHERE id = ?")) {
                ps.setInt(1, otrosMaterialId);
                ps.executeUpdate();
            }
            otra.commit();
        }

        assertThrows(ConflictoConcurrenciaException.class, () -> dao.lanzarLote("E01", 120, 45,
            List.of(new LoteMovimiento(otrosMaterialId, equipoOtrosId, 5, true, EstadoEquipo.NUEVO)), Map.of()));

        assertEquals(0, contarLotes(), "no quedó fila huérfana en lotes");
    }

    // ── Escenario del bug: dos lotes simultáneos sobre el mismo REMITO ──────────
    // La UI siempre envía materialId < 0 para REMITO, incluso en splits posteriores.

    @Test
    void dosLotesSimultaneos_remitoNegativoId_cadaLoteTieneSuPropiaCantidad() throws SQLException {
        int equipoOtrosId = insertarEquipoOtros(50);

        // Lote1: 10 de 50, sin finalizar
        Lote lote1 = dao.lanzarLote("E01", 120, 10,
            List.of(new LoteMovimiento(-equipoOtrosId, equipoOtrosId, 10, true, EstadoEquipo.NUEVO)), Map.of());

        // Lote2: 10 de los 40 restantes, lote1 todavía activo
        Lote lote2 = dao.lanzarLote("E02", 120, 10,
            List.of(new LoteMovimiento(-equipoOtrosId, equipoOtrosId, 10, true, EstadoEquipo.NUEVO)), Map.of());

        List<LoteMaterialInfo> mats1 = dao.obtenerMaterialesPorLote(lote1.getId());
        assertEquals(1, mats1.size(), "Lote1 debe tener exactamente una fila");
        assertEquals(10, mats1.get(0).getCantidad(), "Lote1 debe tener 10 elementos");

        List<LoteMaterialInfo> mats2 = dao.obtenerMaterialesPorLote(lote2.getId());
        assertEquals(1, mats2.size(), "Lote2 debe tener exactamente una fila");
        assertEquals(10, mats2.get(0).getCantidad(), "Lote2 debe tener 10 elementos");

        // Los 30 restantes deben quedar sin lote
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT SUM(cantidad) FROM equipo_otros_materiales " +
                 "WHERE equipo_otros_id = ? AND lote_id IS NULL")) {
            ps.setInt(1, equipoOtrosId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(30, rs.getInt(1), "Deben quedar 30 sin lote");
            }
        }
    }

    @Test
    void dosLotesSimultaneos_sumaTotal_conservadaEn50() throws SQLException {
        int equipoOtrosId = insertarEquipoOtros(50);

        dao.lanzarLote("E01", 120, 10,
            List.of(new LoteMovimiento(-equipoOtrosId, equipoOtrosId, 15, true, EstadoEquipo.NUEVO)), Map.of());
        dao.lanzarLote("E02", 120, 10,
            List.of(new LoteMovimiento(-equipoOtrosId, equipoOtrosId, 15, true, EstadoEquipo.NUEVO)), Map.of());

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT SUM(cantidad) FROM equipo_otros_materiales WHERE equipo_otros_id = ?")) {
            ps.setInt(1, equipoOtrosId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(50, rs.getInt(1), "SUM(cantidad) siempre debe ser remito_cantidad");
            }
        }
    }

    @Test
    void dosLotesSimultaneos_finalizarAmbos_50elementosEsterilizados() throws SQLException {
        int equipoOtrosId = insertarEquipoOtros(50);

        Lote lote1 = dao.lanzarLote("E01", 120, 10,
            List.of(new LoteMovimiento(-equipoOtrosId, equipoOtrosId, 20, true, EstadoEquipo.NUEVO)), Map.of());
        Lote lote2 = dao.lanzarLote("E02", 120, 10,
            List.of(new LoteMovimiento(-equipoOtrosId, equipoOtrosId, 30, true, EstadoEquipo.NUEVO)), Map.of());

        dao.finalizarLote(lote1.getId());
        dao.finalizarLote(lote2.getId());

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT SUM(cantidad) FROM equipo_otros_materiales " +
                 "WHERE equipo_otros_id = ? AND estado = 'Esterilizado'")) {
            ps.setInt(1, equipoOtrosId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(50, rs.getInt(1),
                    "Los 50 elementos deben quedar en Esterilizado al finalizar ambos lotes");
            }
        }
    }

    @Test
    void tresSplitsSimultaneos_totalConservado() throws SQLException {
        int equipoOtrosId = insertarEquipoOtros(30);

        Lote lote1 = dao.lanzarLote("E01", 120, 10,
            List.of(new LoteMovimiento(-equipoOtrosId, equipoOtrosId, 10, true, EstadoEquipo.NUEVO)), Map.of());
        Lote lote2 = dao.lanzarLote("E02", 120, 10,
            List.of(new LoteMovimiento(-equipoOtrosId, equipoOtrosId, 10, true, EstadoEquipo.NUEVO)), Map.of());
        Lote lote3 = dao.lanzarLote("E03", 120, 10,
            List.of(new LoteMovimiento(-equipoOtrosId, equipoOtrosId, 10, true, EstadoEquipo.NUEVO)), Map.of());

        assertEquals(10, dao.obtenerMaterialesPorLote(lote1.getId()).stream()
            .mapToInt(LoteMaterialInfo::getCantidad).sum(), "Lote1 debe tener 10");
        assertEquals(10, dao.obtenerMaterialesPorLote(lote2.getId()).stream()
            .mapToInt(LoteMaterialInfo::getCantidad).sum(), "Lote2 debe tener 10");
        assertEquals(10, dao.obtenerMaterialesPorLote(lote3.getId()).stream()
            .mapToInt(LoteMaterialInfo::getCantidad).sum(), "Lote3 debe tener 10");

        // Ningún elemento sin lote (se asignaron todos)
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COALESCE(SUM(cantidad), 0) FROM equipo_otros_materiales " +
                 "WHERE equipo_otros_id = ? AND lote_id IS NULL")) {
            ps.setInt(1, equipoOtrosId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "No deben quedar elementos sin lote");
            }
        }
    }

    @Test
    void lanzarLote_mezclaOrtopediaYOtrosRemito_ambosEnMismoLote() throws SQLException {
        int equipoOtrosId = insertarEquipoOtros(10);

        Lote lote = dao.lanzarLote("E01", 120, 45, List.of(
            new LoteMovimiento(materialId, equipo.getId(), 3, EstadoEquipo.NUEVO),
            new LoteMovimiento(-equipoOtrosId, equipoOtrosId, 10, true, EstadoEquipo.NUEVO)
        ), Map.of());

        List<LoteMaterialInfo> mats = dao.obtenerMaterialesPorLote(lote.getId());
        assertEquals(2, mats.size(), "Debe haber 1 material de ortopedia y 1 de otros");
        int total = mats.stream().mapToInt(LoteMaterialInfo::getCantidad).sum();
        assertEquals(13, total, "3 ortopedia + 10 otros = 13 elementos en el lote");
    }

    @Test
    void dosEquiposOtrosRemito_enMismoLote_cadaUnoConSuCantidad() throws SQLException {
        int equipoA = insertarEquipoOtros(20);
        int equipoB = insertarEquipoOtros(15);

        Lote lote = dao.lanzarLote("E01", 120, 50, List.of(
            new LoteMovimiento(-equipoA, equipoA, 10, true, EstadoEquipo.NUEVO),
            new LoteMovimiento(-equipoB, equipoB, 15, true, EstadoEquipo.NUEVO)
        ), Map.of());

        try (Connection conn = ConnectionPool.getConnection()) {
            int cantA;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT SUM(cantidad) FROM equipo_otros_materiales " +
                    "WHERE equipo_otros_id = ? AND lote_id = ?")) {
                ps.setInt(1, equipoA); ps.setInt(2, lote.getId());
                try (ResultSet rs = ps.executeQuery()) { rs.next(); cantA = rs.getInt(1); }
            }
            int cantB;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT SUM(cantidad) FROM equipo_otros_materiales " +
                    "WHERE equipo_otros_id = ? AND lote_id = ?")) {
                ps.setInt(1, equipoB); ps.setInt(2, lote.getId());
                try (ResultSet rs = ps.executeQuery()) { rs.next(); cantB = rs.getInt(1); }
            }
            assertEquals(10, cantA, "EquipoA debe tener 10 en el lote");
            assertEquals(15, cantB, "EquipoB debe tener 15 en el lote");
        }
    }

    @Test
    void cicloCompleto_dosLotesParcialesEsterilizanTodosLosElementos() throws SQLException {
        int equipoOtrosId = insertarEquipoOtros(50);

        // Primer lote: 20 de 50
        Lote lote1 = dao.lanzarLote("E01", 120, 10,
            List.of(new LoteMovimiento(-equipoOtrosId, equipoOtrosId, 20, true, EstadoEquipo.NUEVO)), Map.of());
        dao.finalizarLote(lote1.getId());

        // Los 30 restantes deben ser una fila real en estado anterior al lote
        int materialIdRestante;
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id FROM equipo_otros_materiales " +
                 "WHERE equipo_otros_id = ? AND lote_id IS NULL")) {
            ps.setInt(1, equipoOtrosId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Los 30 restantes deben existir como fila real sin lote");
                materialIdRestante = rs.getInt("id");
            }
        }

        // Segundo lote: los 30 restantes (ya son fila real, usa path DETALLES)
        Lote lote2 = dao.lanzarLote("E01", 120, 15,
            List.of(new LoteMovimiento(materialIdRestante, equipoOtrosId, 30, true, EstadoEquipo.NUEVO)), Map.of());
        dao.finalizarLote(lote2.getId());

        // Invariante final: todos los 50 deben estar en Esterilizado
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT SUM(cantidad) FROM equipo_otros_materiales " +
                 "WHERE equipo_otros_id = ? AND estado = 'Esterilizado'")) {
            ps.setInt(1, equipoOtrosId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(50, rs.getInt(1),
                    "Todos los 50 elementos deben quedar en estado Esterilizado");
            }
        }
    }

    // ── consultas — estado vacío ──────────────────────────────────────────────

    @Test
    void obtenerLotesActivosPorAutoclave_sinLotes_retornaMapaVacio() {
        assertTrue(dao.obtenerLotesActivosPorAutoclave().isEmpty());
    }

    @Test
    void obtenerLotesFinalizados_sinLotes_retornaListaVacia() {
        assertTrue(dao.obtenerLotesFinalizados().isEmpty());
    }

    @Test
    void obtenerTodosLosLotes_sinLotes_retornaListaVacia() {
        assertTrue(dao.obtenerTodosLosLotes().isEmpty());
    }

    @Test
    void obtenerLotesEnRango_sinLotes_retornaListaVacia() {
        List<Lote> lotes = dao.obtenerLotesEnRango(
            LocalDate.now().minusDays(7), LocalDate.now());
        assertTrue(lotes.isEmpty());
    }

    @Test
    void obtenerClientesPorLote_sinMateriales_retornaListaVacia() {
        assertTrue(dao.obtenerClientesPorLote(9999).isEmpty());
    }

    @Test
    void obtenerMaterialesPorLote_sinMateriales_retornaListaVacia() {
        assertTrue(dao.obtenerMaterialesPorLote(9999).isEmpty());
    }

    // ── consultas — con lote activo ───────────────────────────────────────────

    @Test
    void obtenerLotesActivosPorAutoclave_conLoteActivo_retornaLote() {
        Lote lote = dao.lanzarLote("E01", 120, 45,
            List.of(new LoteMovimiento(materialId, equipo.getId(), 3, EstadoEquipo.NUEVO)), Map.of());

        Map<String, Lote> activos = dao.obtenerLotesActivosPorAutoclave();
        assertTrue(activos.containsKey("E01"));
        assertEquals(lote.getId(), activos.get("E01").getId());
    }

    @Test
    void obtenerTodosLosLotes_conLote_retornaListaConUnElemento() {
        dao.lanzarLote("E01", 120, 45,
            List.of(new LoteMovimiento(materialId, equipo.getId(), 3, EstadoEquipo.NUEVO)), Map.of());
        assertEquals(1, dao.obtenerTodosLosLotes().size());
    }

    @Test
    void obtenerClientesPorLote_conMaterial_retornaCliente() {
        Lote lote = dao.lanzarLote("E01", 120, 45,
            List.of(new LoteMovimiento(materialId, equipo.getId(), 3, EstadoEquipo.NUEVO)), Map.of());
        List<String> clientes = dao.obtenerClientesPorLote(lote.getId());
        assertEquals(1, clientes.size());
    }

    @Test
    void obtenerMaterialesPorLote_conMaterial_retornaMaterialInfo() {
        Lote lote = dao.lanzarLote("E01", 120, 45,
            List.of(new LoteMovimiento(materialId, equipo.getId(), 3, EstadoEquipo.NUEVO)), Map.of());
        List<LoteMaterialInfo> mats = dao.obtenerMaterialesPorLote(lote.getId());
        assertEquals(1, mats.size());
        assertEquals(400, mats.get(0).getCodigoCatalogo());
        assertEquals(3,   mats.get(0).getCantidad());
    }

    // ── finalizarLote ─────────────────────────────────────────────────────────

    @Test
    void finalizarLote_loteInexistente_retornaFalse() {
        assertFalse(dao.finalizarLote(999999));
    }

    @Test
    void finalizarLote_loteActivo_retornaTrue() {
        Lote lote = dao.lanzarLote("E01", 120, 45,
            List.of(new LoteMovimiento(materialId, equipo.getId(), 3, EstadoEquipo.NUEVO)), Map.of());
        assertTrue(dao.finalizarLote(lote.getId()));
    }

    @Test
    void finalizarLote_mueveMaterilaAEsterilizado() {
        Lote lote = dao.lanzarLote("E01", 120, 45,
            List.of(new LoteMovimiento(materialId, equipo.getId(), 3, EstadoEquipo.NUEVO)), Map.of());
        dao.finalizarLote(lote.getId());

        Equipo cargado = equipoDAO.obtenerPorId(String.valueOf(equipo.getId()));
        assertEquals(EstadoEquipo.ESTERILIZADO, cargado.getMateriales().get(0).getEstado());
    }

    @Test
    void finalizarLote_aparecEnLotesFinalizados() {
        Lote lote = dao.lanzarLote("E01", 120, 45,
            List.of(new LoteMovimiento(materialId, equipo.getId(), 3, EstadoEquipo.NUEVO)), Map.of());
        dao.finalizarLote(lote.getId());

        List<Lote> finalizados = dao.obtenerLotesFinalizados();
        assertEquals(1, finalizados.size());
        assertEquals(lote.getId(), finalizados.get(0).getId());
    }

    @Test
    void finalizarLote_yaFinalizado_retornaFalse() {
        Lote lote = dao.lanzarLote("E01", 120, 45,
            List.of(new LoteMovimiento(materialId, equipo.getId(), 3, EstadoEquipo.NUEVO)), Map.of());
        dao.finalizarLote(lote.getId());
        // Segundo intento: ya tiene fecha_fin → actualizarEstadoLoteAbierto devuelve 0 filas
        assertFalse(dao.finalizarLote(lote.getId()));
    }

    @Test
    void finalizarLote_aparecEnRango() {
        Lote lote = dao.lanzarLote("E01", 120, 45,
            List.of(new LoteMovimiento(materialId, equipo.getId(), 3, EstadoEquipo.NUEVO)), Map.of());
        dao.finalizarLote(lote.getId());

        List<Lote> lotes = dao.obtenerLotesEnRango(
            LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
        assertEquals(1, lotes.size());
        assertEquals(lote.getId(), lotes.get(0).getId());
    }

    // ── marcarLoteFallo ───────────────────────────────────────────────────────

    @Test
    void marcarLoteFallo_loteInexistente_retornaFalse() {
        assertFalse(dao.marcarLoteFallo(999999));
    }

    @Test
    void marcarLoteFallo_loteActivo_retornaTrue() {
        Lote lote = dao.lanzarLote("E01", 120, 45,
            List.of(new LoteMovimiento(materialId, equipo.getId(), 3, EstadoEquipo.NUEVO)), Map.of());
        assertTrue(dao.marcarLoteFallo(lote.getId()));
    }

    @Test
    void marcarLoteFallo_revierteEstadoMaterial() {
        Lote lote = dao.lanzarLote("E01", 120, 45,
            List.of(new LoteMovimiento(materialId, equipo.getId(), 3, EstadoEquipo.NUEVO)), Map.of());
        dao.marcarLoteFallo(lote.getId());

        Equipo cargado = equipoDAO.obtenerPorId(String.valueOf(equipo.getId()));
        // El estado anterior al movimiento de lanzarLote era NUEVO (sin lavado/empaque en fixture)
        assertNotEquals(EstadoEquipo.ESTERILIZANDO, cargado.getMateriales().get(0).getEstado());
    }

    @Test
    void marcarLoteFallo_yaFinalizado_retornaFalse() {
        Lote lote = dao.lanzarLote("E01", 120, 45,
            List.of(new LoteMovimiento(materialId, equipo.getId(), 3, EstadoEquipo.NUEVO)), Map.of());
        dao.marcarLoteFallo(lote.getId());
        assertFalse(dao.marcarLoteFallo(lote.getId()));
    }

    // ── helper ────────────────────────────────────────────────────────────────

    /** Inserta un equipo_otros de tipo REMITO con la cantidad dada y devuelve su id generado. */
    private int insertarEquipoOtros(int remitoCantidad) throws SQLException {
        String sql = "INSERT INTO equipo_otros (nro_cliente, estado, requiere_lavado, " +
                     "requiere_empaque, tipo_ingreso, remito_cantidad) " +
                     "VALUES (1, 'Nuevo', 0, 0, 'REMITO', ?)";
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, remitoCantidad);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("No se pudo insertar equipo_otros");
    }

    // ── obtenerMaterialesPorClientePorLote ────────────────────────────────────

    @Test
    void obtenerMaterialesPorClientePorLote_sinMateriales_retornaMapaVacio() {
        assertTrue(dao.obtenerMaterialesPorClientePorLote(9999).isEmpty());
    }

    @Test
    void obtenerMaterialesPorClientePorLote_conMaterial_retornaMapa() {
        Lote lote = dao.lanzarLote("E01", 120, 45,
            List.of(new LoteMovimiento(materialId, equipo.getId(), 3, EstadoEquipo.NUEVO)), Map.of());

        Map<String, List<String>> resultado = dao.obtenerMaterialesPorClientePorLote(lote.getId());
        assertFalse(resultado.isEmpty());
        assertEquals(1, resultado.size());
        // El primer (y único) cliente tiene al menos 1 material listado
        List<String> mats = resultado.values().iterator().next();
        assertFalse(mats.isEmpty());
    }

    // ── lanzarLote — equipo_otros, DETALLES (materialId > 0) ─────────────────

    @Test
    void lanzarLote_otrosDetalles_creaFilaEnEquipoOtrosMateriales() throws SQLException {
        int[] fixture = insertarEquipoOtrosDetalles();
        int equipoOtrosId  = fixture[0];
        int otrosMaterialId = fixture[1];

        Lote lote = dao.lanzarLote("E01", 120, 45,
            List.of(new LoteMovimiento(otrosMaterialId, equipoOtrosId, 5, true, EstadoEquipo.NUEVO)), Map.of());

        List<LoteMaterialInfo> mats = dao.obtenerMaterialesPorLote(lote.getId());
        assertFalse(mats.isEmpty());
        assertEquals(5, mats.get(0).getCantidad());
    }

    // ── finalizarLote — rutas de equipo_otros ─────────────────────────────────

    @Test
    void finalizarLote_otrosMateriales_actualizaEstadoAEsterilizado() throws SQLException {
        int[] fixture = insertarEquipoOtrosDetalles();
        int equipoOtrosId  = fixture[0];
        int otrosMaterialId = fixture[1];

        Lote lote = dao.lanzarLote("E01", 120, 45,
            List.of(new LoteMovimiento(otrosMaterialId, equipoOtrosId, 5, true, EstadoEquipo.NUEVO)), Map.of());
        dao.finalizarLote(lote.getId());

        EquipoOtros cargado = equipoOtrosDAO.obtenerTodos().stream()
            .filter(e -> e.getId() == equipoOtrosId).findFirst().orElseThrow();
        assertEquals(EstadoEquipo.ESTERILIZADO, cargado.getMateriales().get(0).getEstado());
    }

    // ── marcarLoteFallo — rutas de equipo_otros ───────────────────────────────

    @Test
    void marcarLoteFallo_otrosMateriales_revierteEstadoEquipoOtros() throws SQLException {
        int[] fixture = insertarEquipoOtrosDetalles();
        int equipoOtrosId  = fixture[0];
        int otrosMaterialId = fixture[1];

        Lote lote = dao.lanzarLote("E01", 120, 45,
            List.of(new LoteMovimiento(otrosMaterialId, equipoOtrosId, 5, true, EstadoEquipo.NUEVO)), Map.of());
        dao.marcarLoteFallo(lote.getId());

        EquipoOtros cargado = equipoOtrosDAO.obtenerTodos().stream()
            .filter(e -> e.getId() == equipoOtrosId).findFirst().orElseThrow();
        assertNotEquals(EstadoEquipo.ESTERILIZANDO, cargado.getMateriales().get(0).getEstado());
    }

    // ── Paso 7: secuencia de lotes bajo concurrencia ──────────────────────────
    //
    // LO QUE ESTOS DOS TESTS **NO** PUEDEN PROBAR, Y NO HAY QUE LEERLOS COMO SI LO PROBARAN:
    //
    // 1. Que la discriminación de la clase 23 esté FUERA de la transacción del intento fallido.
    //    H2 corre en READ COMMITTED, así que el SELECT de existeIdNegocio ve la fila ajena esté
    //    adentro del try o afuera: los dos tests quedan verdes también con el SELECT movido
    //    adentro, que es la versión que en MySQL (REPEATABLE READ) leería el snapshot fijado por
    //    obtenerSiguienteSecuencia, no vería la fila del otro operador, y no reintentaría NUNCA.
    //    Lo único que protege esa decisión es el comentario de LoteDAO.lanzarLote. Correr estos
    //    dos casos una vez contra un MySQL de desarrollo es la única verificación real.
    //
    // 2. El bloqueo del índice único del segundo INSERT: MySQL hace esperar al segundo operador
    //    hasta que el primero termina; H2 no reproduce eso.
    //
    // El caso del camino feliz (reintento que resuelve) necesita un seam, y por eso
    // LoteDAO.obtenerSiguienteSecuencia es package-private: la secuencia es MAX(secuencia) + 1, así
    // que el reintento sólo avanza si entre los dos intentos aparece una fila committeada por otro.
    // En un test secuencial la base no cambia sola, y un test con dos hilos dependería del timeout
    // de lock de H2. Simular la lectura obsoleta de A es determinista y no miente sobre nada.

    @Test
    void lanzarLote_secuenciaObsoleta_reintentaConLaSiguienteYLanza() throws SQLException {
        // B ya committeó su lote del año en curso; A había leído MAX(secuencia) = 0 antes de eso.
        int anio = LocalDate.now().getYear();
        insertarLoteCrudo(anio + "1", anio, 1);
        LoteDAOPrimeraSecuenciaObsoleta daoConLecturaObsoleta = new LoteDAOPrimeraSecuenciaObsoleta(1);

        Lote lote = daoConLecturaObsoleta.lanzarLote("E01", 120, 45,
            List.of(new LoteMovimiento(materialId, equipo.getId(), 3, EstadoEquipo.NUEVO)), Map.of());

        // Sin este conteo el test no discrimina: un lote con secuencia 2 es también lo que sale si
        // el primer intento nunca chocó. Dos resoluciones = hubo colisión y hubo reintento.
        assertEquals(2, daoConLecturaObsoleta.resoluciones(), "el lanzamiento necesitó dos intentos");
        assertEquals(2, lote.getSecuencia(), "el reintento recalculó la secuencia con la fila de B visible");
        assertEquals(anio + "2", lote.getIdNegocio());
        assertEquals(2, contarLotes(), "el intento fallido no dejó fila propia");
        Equipo cargado = equipoDAO.obtenerPorId(String.valueOf(equipo.getId()));
        assertEquals(EstadoEquipo.ESTERILIZANDO, cargado.getMateriales().get(0).getEstado(),
            "el segundo intento aplicó el lanzamiento entero, no sólo la cabecera");
    }

    /**
     * A leyó {@code MAX(secuencia)} antes de que B commiteara: el primer intento calcula la
     * secuencia que B ya se llevó, y del segundo en adelante la resolución es la real.
     */
    private static final class LoteDAOPrimeraSecuenciaObsoleta extends LoteDAO {
        private final int secuenciaObsoleta;
        private int resoluciones = 0;

        LoteDAOPrimeraSecuenciaObsoleta(int secuenciaObsoleta) {
            this.secuenciaObsoleta = secuenciaObsoleta;
        }

        /** Cuántos intentos resolvieron una secuencia: es el contador de intentos del bucle. */
        int resoluciones() {
            return resoluciones;
        }

        @Override
        int obtenerSiguienteSecuencia(Connection conn, int anio) throws SQLException {
            if (resoluciones++ == 0) return secuenciaObsoleta;
            return super.obtenerSiguienteSecuencia(conn, anio);
        }
    }

    @Test
    void lanzarLote_idNegocioDuplicado_reintentaYTerminaEnConflictoDeSecuencia() throws SQLException {
        // Una fila de otro año ocupando el id_negocio que le toca al próximo lote de este año:
        // es la única forma secuencial de forzar el choque, porque una fila coherente del año en
        // curso movería el MAX(secuencia) y no habría colisión. Los tres intentos recalculan lo
        // mismo, así que el bucle se agota — que es justo lo que este test mira.
        int anio = LocalDate.now().getYear();
        insertarLoteCrudo(anio + "1", anio - 1, 1);

        ConflictoConcurrenciaException e = assertThrows(ConflictoConcurrenciaException.class,
            () -> dao.lanzarLote("E01", 120, 45,
                List.of(new LoteMovimiento(materialId, equipo.getId(), 3, EstadoEquipo.NUEVO)), Map.of()));

        assertEquals(Constantes.Mensajes.CONFLICTO_SECUENCIA_LOTE, e.getMessage(),
            "agotar los reintentos llega al operador como conflicto de secuencia, no como error técnico");
        assertEquals(1, contarLotes(), "ningún intento dejó un lote a medio hacer");
        Equipo cargado = equipoDAO.obtenerPorId(String.valueOf(equipo.getId()));
        assertEquals(EstadoEquipo.NUEVO, cargado.getMateriales().get(0).getEstado(),
            "cada intento revirtió su transacción entera");
    }

    @Test
    void lanzarLote_autoclaveInexistente_fallaEnElActoSinCartelDeSecuencia() throws SQLException {
        // La FK a autoclaves da clase 23 en la MISMA sentencia que el UNIQUE (id_negocio). Sin
        // discriminar, esto se llevaría los tres intentos y saldría como conflicto de secuencia.
        // Que salga DatabaseException prueba que la discriminación cortó en el primer fallo:
        // ConflictoConcurrenciaException no hereda de DatabaseException.
        DatabaseException e = assertThrows(DatabaseException.class,
            () -> dao.lanzarLote("NO_EXISTE", 120, 45,
                List.of(new LoteMovimiento(materialId, equipo.getId(), 3, EstadoEquipo.NUEVO)), Map.of()));

        assertTrue(e.getMessage().contains("NO_EXISTE"), "el mensaje señala el autoclave, no la secuencia");
        assertInstanceOf(SQLException.class, e.getCause(), "la SQLException original viaja como causa");
        assertEquals(0, contarLotes());
    }

    /** Inserta una fila de lotes a mano, sin pasar por lanzarLote. */
    private void insertarLoteCrudo(String idNegocio, int anio, int secuencia) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO lotes (id_negocio, anio, secuencia, autoclave_nombre, " +
                 "capacidad_total, capacidad_usada) VALUES (?, ?, ?, 'E01', 120, 45)")) {
            ps.setString(1, idNegocio);
            ps.setInt(2, anio);
            ps.setInt(3, secuencia);
            ps.executeUpdate();
        }
    }

    /** Inserta un equipo_otros DETALLES con 1 material (cantidad=5). Devuelve [equipoOtrosId, materialId]. */
    private int[] insertarEquipoOtrosDetalles() throws SQLException {
        int equipoOtrosId;
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO equipo_otros (nro_cliente, estado, requiere_lavado, " +
                 "requiere_empaque, tipo_ingreso) VALUES (1, 'Nuevo', 1, 1, 'DETALLES')",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) equipoOtrosId = rs.getInt(1);
                else throw new SQLException("No se pudo insertar equipo_otros DETALLES");
            }
        }

        ejecutarSQL("INSERT IGNORE INTO catalogo_otros (descripcion) VALUES ('Elementos')");

        int catalogoId;
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id FROM catalogo_otros WHERE descripcion = 'Elementos'")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) catalogoId = rs.getInt(1);
                else throw new SQLException("No se encontró catalogo_otros 'Elementos'");
            }
        }

        int otrosMaterialId;
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO equipo_otros_materiales " +
                 "(equipo_otros_id, catalogo_otros_id, descripcion, cantidad, estado) " +
                 "VALUES (?, ?, 'Elementos', 5, 'Nuevo')",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, equipoOtrosId);
            ps.setInt(2, catalogoId);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) otrosMaterialId = rs.getInt(1);
                else throw new SQLException("No se pudo insertar equipo_otros_materiales");
            }
        }

        return new int[]{equipoOtrosId, otrosMaterialId};
    }
}
