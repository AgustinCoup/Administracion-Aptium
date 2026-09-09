package com.example.infrastructure.db;

import com.example.AbstractDAOTest;
import com.example.common.exception.ConflictoConcurrenciaException;
import com.example.common.exception.LavarropasOcupadoException;
import com.example.common.exception.SaldoConsumidoException;
import com.example.features.catalogo.dao.CatalogoDAO;
import com.example.features.catalogo.dao.CatalogoOtrosDAO;
import com.example.features.clientes.dao.ClienteDAO;
import com.example.features.clientes.dao.FusionClientesDAO;
import com.example.features.equipos.ortopedias.dao.AuditoriaDAO;
import com.example.features.equipos.ortopedias.dao.EquipoDAO;
import com.example.features.equipos.ortopedias.dao.MaterialDAO;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.model.Material;
import com.example.features.equipos.ortopedias.model.MovimientoMaterial;
import com.example.features.equipos.ortopedias.service.EquipoCorreccionService;
import com.example.features.equipos.otros.dao.EquipoOtrosDAO;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.MaterialOtros;
import com.example.features.equipos.otros.model.TipoIngresoOtros;
import com.example.features.lavadero.dao.CicloLavaderoDAO;
import com.example.features.lavadero.dao.ClasificacionLavaderoDAO;
import com.example.features.lavadero.dao.SalidaLavaderoDAO;
import com.example.features.lavadero.model.ConfiguracionCiclo;
import com.example.features.lavadero.model.ElementoClasificacion;
import com.example.features.lavadero.model.ElementoLavadoPendiente;
import com.example.features.lavadero.model.JabonCatalogo;
import com.example.features.lavadero.model.LanzamientoCiclo;
import com.example.features.lavadero.model.LineaLanzamiento;
import com.example.features.lavadero.model.MarcaListo;
import com.example.features.lavadero.model.TipoLavado;
import com.example.features.lotes.dao.LoteDAO;
import com.example.features.lotes.model.LoteMovimiento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite transversal del bloqueo optimista: <b>la regla, no los casos sueltos</b>.
 *
 * <p>Cada flujo protegido tiene además sus tests en el {@code *DAOTest} de su feature, que cubren
 * las variantes. Acá va <em>un</em> caso por flujo, todos con la misma forma, para que se lea de
 * corrido qué garantiza el mecanismo:
 *
 * <pre>
 *   A lee  →  B modifica y commitea  →  A escribe  →  conflicto
 *                                                     y el estado final es exactamente el de B
 * </pre>
 *
 * <p>La segunda mitad es la que importa. Que A reciba una {@link ConflictoConcurrenciaException}
 * es la mitad barata; lo que hay que demostrar es que <b>no quedó nada de A</b> — ni una fila a
 * medias, ni un lote huérfano, ni un movimiento con el estado de origen equivocado. Un rechazo que
 * igual deja rastro es peor que no rechazar: nadie va a buscar ese rastro.
 *
 * <h2>Qué verifica y qué no</h2>
 *
 * <p>Estos tests corren sobre <b>H2 en modo MySQL</b> ({@link AbstractDAOTest}), con
 * {@code maximumPoolSize = 5}, así que dos conexiones simultáneas salen sin infraestructura nueva.
 * Pero <b>H2 no es MySQL</b>: el nivel de aislamiento por defecto y el comportamiento de
 * {@code SELECT ... FOR UPDATE} difieren (H2 corre en {@code READ COMMITTED}; MySQL, en
 * {@code REPEATABLE READ}).
 *
 * <p>Lo que se verifica acá es <b>la guarda</b> —la condición del {@code WHERE} no matchea, o el
 * valor releído no coincide, y entonces la operación se cae y revierte—, que es idéntica en los
 * dos motores. Lo que <b>no</b> se puede verificar acá es el comportamiento del lock: un test de
 * deadlock o de bloqueo mutuo pasaría en H2 y mentiría sobre producción. Para eso está el smoke
 * manual de dos máquinas contra la misma base MySQL.
 *
 * <p>Por eso los casos no arrancan dos hilos: B commitea <b>antes</b> de que A escriba. Esa es
 * exactamente la ventana que el plan viene a cerrar —el tiempo de pensar del operador— y no
 * depende del scheduler para reproducirse.
 */
class ConcurrenciaOptimistaTest extends AbstractDAOTest {

    private final EquipoDAO      equipoDAO      = new EquipoDAO();
    private final MaterialDAO    materialDAO    = new MaterialDAO();
    private final EquipoOtrosDAO equipoOtrosDAO = new EquipoOtrosDAO(new CatalogoOtrosDAO());
    private final LoteDAO        loteDAO        = new LoteDAO();

    private final ClasificacionLavaderoDAO clasificacionDAO = new ClasificacionLavaderoDAO();
    private final CicloLavaderoDAO         cicloDAO         = new CicloLavaderoDAO();
    private final SalidaLavaderoDAO        salidaDAO        = new SalidaLavaderoDAO();

    /** Para el caso del Paso 5: sólo necesita ver el conflicto propagar antes de auditar. */
    private final EquipoCorreccionService correccionService =
        new EquipoCorreccionService(equipoDAO, materialDAO, new AuditoriaDAO(), new CatalogoDAO());
    private final FusionClientesDAO fusionDAO  = new FusionClientesDAO();
    private final ClienteDAO        clienteDAO = new ClienteDAO();

    /** Los ids de instancia del staging son locales a la tanda: cualquiera sirve. */
    private static final int STAGING_ID = 1;

    private JabonCatalogo jabon;

    @BeforeEach
    void leerSemillas() throws SQLException {
        jabon = primerJabon();
    }

    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM salidas_lavadero");
        ejecutarSQL("DELETE FROM elementos_ciclo_lavadero");
        ejecutarSQL("DELETE FROM instancias_equipo_ciclo");
        ejecutarSQL("DELETE FROM ciclos_lavadero");
        ejecutarSQL("DELETE FROM elementos_clasificacion_lavadero");
        ejecutarSQL("DELETE FROM bolsas_lavadero");
        ejecutarSQL("DELETE FROM ingresos_lavadero");
        ejecutarSQL("DELETE FROM otros_material_movimientos");
        ejecutarSQL("DELETE FROM equipo_otros_materiales");
        ejecutarSQL("DELETE FROM equipo_otros");
        ejecutarSQL("DELETE FROM lote_otros_volumenes");
        ejecutarSQL("DELETE FROM lotes");
        ejecutarSQL("DELETE FROM equipos");   // CASCADE cubre equipo_materiales y material_movimientos
        ejecutarSQL("DELETE FROM catalogo_otros WHERE descripcion LIKE 'TestConc%' OR descripcion = 'Elementos'");
        ejecutarSQL("DELETE FROM clientes WHERE nombre LIKE 'TestConc%'");
    }

    // ── Registrar Estado — ortopedias ─────────────────────────────────────────

    @Test
    @DisplayName("Registrar Estado (ortopedias): el segundo operador choca y no deja movimiento falso")
    void registrarEstadoOrtopedias() {
        Equipo equipo = equipoOrtopediaConMaterial(3);
        int materialId = equipo.getMateriales().get(0).getId();

        // B avanza el material y commitea.
        materialDAO.aplicarMovimientos(equipo.getId(),
            List.of(new MovimientoMaterial(materialId, 3, EstadoEquipo.NUEVO, EstadoEquipo.LAVANDO)));
        int movimientosTrasB = escalar("SELECT COUNT(*) FROM material_movimientos");

        // A confirma con el snapshot viejo: para él el material seguía en NUEVO.
        assertThrows(ConflictoConcurrenciaException.class, () -> materialDAO.aplicarMovimientos(
            equipo.getId(),
            List.of(new MovimientoMaterial(materialId, 3, EstadoEquipo.NUEVO, EstadoEquipo.LAVANDO))));

        Equipo despues = equipoDAO.obtenerPorId(String.valueOf(equipo.getId()));
        assertEquals(EstadoEquipo.LAVANDO, despues.getMateriales().get(0).getEstado(),
            "queda el avance de B, no el de A");
        assertEquals(movimientosTrasB, escalar("SELECT COUNT(*) FROM material_movimientos"),
            "el movimiento de A no se registró: sin la guarda habría un estado_origen 'Nuevo' falso");
    }

    // ── Registrar Estado — otros ──────────────────────────────────────────────

    @Test
    @DisplayName("Registrar Estado (otros): mismo choque, mismo resultado que en ortopedias")
    void registrarEstadoOtros() {
        EquipoOtros equipo = equipoOtrosConMaterial(3);
        int materialId = equipo.getMateriales().get(0).getId();

        equipoOtrosDAO.aplicarMovimientos(equipo.getId(),
            List.of(new MovimientoMaterial(materialId, 3, EstadoEquipo.NUEVO, EstadoEquipo.LAVANDO)));
        int movimientosTrasB = escalar("SELECT COUNT(*) FROM otros_material_movimientos");

        assertThrows(ConflictoConcurrenciaException.class, () -> equipoOtrosDAO.aplicarMovimientos(
            equipo.getId(),
            List.of(new MovimientoMaterial(materialId, 3, EstadoEquipo.NUEVO, EstadoEquipo.LAVANDO))));

        assertEquals(EstadoEquipo.LAVANDO.getNombre(),
            texto("SELECT estado FROM equipo_otros_materiales WHERE id = " + materialId),
            "queda el avance de B");
        assertEquals(movimientosTrasB, escalar("SELECT COUNT(*) FROM otros_material_movimientos"),
            "el movimiento de A no se registró");
    }

    // ── Lanzar Lote ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Lanzar Lote: el segundo lote se cae entero y no deja fila huérfana en `lotes`")
    void lanzarLote() {
        Equipo equipo = equipoOrtopediaConMaterial(3);
        int materialId = equipo.getMateriales().get(0).getId();

        // B lanza primero: el material queda ESTERILIZANDO y con lote_id poblado.
        loteDAO.lanzarLote("E01", 120, 45,
            List.of(new LoteMovimiento(materialId, equipo.getId(), 3, EstadoEquipo.NUEVO)),
            Map.of());

        // A arrastró el mismo material a su autoclave cuando todavía estaba en NUEVO.
        assertThrows(ConflictoConcurrenciaException.class, () -> loteDAO.lanzarLote("E02", 120, 45,
            List.of(new LoteMovimiento(materialId, equipo.getId(), 3, EstadoEquipo.NUEVO)),
            Map.of()));

        assertEquals(1, escalar("SELECT COUNT(*) FROM lotes"),
            "el lote de A se revirtió entero: sin eso quedaría un lote vacío ocupando el autoclave");
        assertEquals(0, escalar("SELECT COUNT(*) FROM lotes WHERE autoclave_nombre = 'E02'"));
        assertEquals(1, escalar(
            "SELECT COUNT(*) FROM equipo_materiales WHERE lote_id IS NOT NULL"),
            "el material sigue apuntando sólo al lote de B");
    }

    // ── Lavadero: clasificación ───────────────────────────────────────────────

    @Test
    @DisplayName("Clasificación: la segunda no duplica la ropa del ingreso")
    void clasificacion() {
        int ingresoId = ingresoDeLavadero("TestConcClasif", "PENDIENTE");
        int elemento1 = catalogoElementoId(1);
        int elemento2 = catalogoElementoId(2);

        clasificacionDAO.guardar(ingresoId, List.of(new ElementoClasificacion(elemento1, 10)));

        assertThrows(ConflictoConcurrenciaException.class, () -> clasificacionDAO.guardar(
            ingresoId, List.of(new ElementoClasificacion(elemento2, 7))));

        assertEquals(1, escalar("SELECT COUNT(*) FROM elementos_clasificacion_lavadero"),
            "sin la guarda el ingreso quedaba con el doble de ropa de la que entró");
        assertEquals(10, escalar("SELECT SUM(cantidad) FROM elementos_clasificacion_lavadero"));
    }

    // ── Lavadero: lanzar tanda ────────────────────────────────────────────────

    @Test
    @DisplayName("Lanzar tanda: la segunda no sobregira la línea de clasificación")
    void lanzarTanda() {
        int ingresoId = ingresoDeLavadero("TestConcTanda", "CLASIFICADO");
        int lineaId = insertarClasificacion(ingresoId, catalogoElementoId(1), 10);

        // B se lleva 7 de las 10 unidades.
        cicloDAO.lanzarTanda(List.of(new LanzamientoCiclo(1, config(), List.of(
            new LineaLanzamiento(lineaId, 7)))));

        // A armó su tanda cuando había 10 disponibles y pide 6: ya no alcanzan. El subtipo importa:
        // es el único choque del lanzamiento ante el cual el controller descarta el staging.
        assertThrows(SaldoConsumidoException.class,
            () -> cicloDAO.lanzarTanda(List.of(new LanzamientoCiclo(2, config(), List.of(
                new LineaLanzamiento(lineaId, 6))))));

        assertEquals(1, escalar("SELECT COUNT(*) FROM ciclos_lavadero"),
            "la tanda de A no dejó ni un ciclo: es todo o nada");
        assertEquals(7, escalar("SELECT SUM(cantidad) FROM elementos_ciclo_lavadero"),
            "la línea no se sobregiró — justo lo que detectarLineasSobregiradas() sale a buscar");
        assertTrue(cicloDAO.detectarLineasSobregiradas().isEmpty(),
            "el detector de líneas sobregiradas no encuentra nada");
    }

    /**
     * El reparto de un mismo equipo entre dos lavarropas de <b>la misma</b> tanda consume 1 unidad,
     * no 2. Es el falso positivo que rompería el flujo si la fórmula de saldo divergiera de
     * {@code SQL_DISPONIBLES}: una tanda legítima rechazada es peor que el bug que la guarda cierra,
     * porque el operador no tiene forma de saber qué hacer con el cartel.
     */
    @Test
    @DisplayName("Lanzar tanda: un equipo repartido en dos lavarropas no se rechaza a sí mismo")
    void lanzarTandaConFraccionesNoEsUnFalsoPositivo() {
        int ingresoId = ingresoDeLavadero("TestConcFrac", "CLASIFICADO");
        int lineaId = insertarClasificacion(ingresoId, catalogoElementoId(1), 1);

        cicloDAO.lanzarTanda(List.of(
            new LanzamientoCiclo(1, config(), List.of(new LineaLanzamiento(lineaId, 1, STAGING_ID, 2))),
            new LanzamientoCiclo(2, config(), List.of(new LineaLanzamiento(lineaId, 1, STAGING_ID, 2)))));

        assertEquals(2, escalar("SELECT COUNT(*) FROM elementos_ciclo_lavadero"));
        assertEquals(1, escalar("SELECT COUNT(*) FROM instancias_equipo_ciclo"),
            "las dos fracciones son una sola instancia y consumen 1 de la línea de 1");
    }

    /**
     * Un lavarropas no puede tener dos ciclos sin finalizar: de los dos, la pantalla sólo puede
     * mostrar uno ({@code obtenerCiclosActivosPorLavarropas} los mete en un mapa por número), así
     * que el otro quedaría invisible, imposible de finalizar, y la ropa que se llevó no volvería a
     * aparecer ni en Disponibles ni en Salidas.
     */
    @Test
    @DisplayName("Lanzar tanda: no se lanza un segundo ciclo en un lavarropas ya ocupado")
    void lanzarTandaSobreLavarropasOcupado() {
        int ingresoId = ingresoDeLavadero("TestConcOcupado", "CLASIFICADO");
        int lineaId = insertarClasificacion(ingresoId, catalogoElementoId(1), 10);

        // B ocupa el lavarropas 1 y no lo finaliza.
        cicloDAO.lanzarTanda(List.of(new LanzamientoCiclo(1, config(), List.of(
            new LineaLanzamiento(lineaId, 3)))));

        // A lo tenía como libre en su último refresco y le manda otra tanda. El subtipo importa:
        // es lo que le dice al controller que NO descarte el staging de los otros lavarropas.
        assertThrows(LavarropasOcupadoException.class,
            () -> cicloDAO.lanzarTanda(List.of(new LanzamientoCiclo(1, config(), List.of(
                new LineaLanzamiento(lineaId, 4))))));

        assertEquals(1, escalar("SELECT COUNT(*) FROM ciclos_lavadero WHERE lavarropas_numero = 1"));
        assertEquals(3, escalar("SELECT SUM(cantidad) FROM elementos_ciclo_lavadero"),
            "la tanda de A no dejó nada: es todo o nada");
    }

    /**
     * {@code fecha_fin} es el único dato que dice "esto está lavado" —lo leen Salidas y todo el
     * Historial—, así que un segundo Finalizar no puede reemplazarla por la de ahora.
     */
    @Test
    @DisplayName("Finalizar ciclo: el segundo Finalizar no pisa la fecha de fin del primero")
    void finalizarCicloYaFinalizado() {
        int ingresoId = ingresoDeLavadero("TestConcFin", "CLASIFICADO");
        int lineaId = insertarClasificacion(ingresoId, catalogoElementoId(1), 5);
        cicloDAO.lanzarTanda(List.of(new LanzamientoCiclo(1, config(), List.of(
            new LineaLanzamiento(lineaId, 5)))));
        int cicloId = escalar("SELECT MAX(id) FROM ciclos_lavadero");

        // B lo finaliza; A lo tenía en pantalla como activo y aprieta Finalizar.
        cicloDAO.finalizarCiclo(cicloId);
        String fechaDeB = texto("SELECT fecha_fin FROM ciclos_lavadero WHERE id = " + cicloId);

        assertThrows(ConflictoConcurrenciaException.class, () -> cicloDAO.finalizarCiclo(cicloId));

        assertEquals(fechaDeB, texto("SELECT fecha_fin FROM ciclos_lavadero WHERE id = " + cicloId),
            "la fecha de fin del primero queda intacta");
    }

    // ── Lavadero: salidas ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Salidas: una salida ya derivada no se puede volver a Lavado")
    void salidaYaDerivadaNoSeRevierte() throws SQLException {
        int ingresoId = ingresoDeLavadero("TestConcSalida", "CLASIFICADO");
        int lineaId = insertarClasificacion(ingresoId, catalogoElementoId(1), 5);
        cicloDAO.lanzarTanda(List.of(new LanzamientoCiclo(1, config(), List.of(
            new LineaLanzamiento(lineaId, 5)))));
        cicloDAO.finalizarCiclo(escalar("SELECT MAX(id) FROM ciclos_lavadero"));

        ElementoLavadoPendiente pendiente = salidaDAO.obtenerLavadosPendientesDeListo().get(0);
        salidaDAO.marcarListo(List.of(new MarcaListo(pendiente, 5)));
        int salidaId = escalar("SELECT MAX(id) FROM salidas_lavadero");

        // B deriva la salida (le estampa destino) y commitea.
        ejecutarSQL("UPDATE salidas_lavadero SET destino = 'FUERA_DE_FLUJO', fecha_salida = NOW() "
            + "WHERE id = " + salidaId);

        // A la tenía en pantalla como "lista sin destino" y aprieta Volver a Lavado.
        assertThrows(ConflictoConcurrenciaException.class,
            () -> salidaDAO.volverALavado(List.of(salidaId)));

        assertEquals(1, escalar("SELECT COUNT(*) FROM salidas_lavadero WHERE id = " + salidaId),
            "el DELETE de A no borró la salida que B ya derivó");
        assertEquals("FUERA_DE_FLUJO",
            texto("SELECT destino FROM salidas_lavadero WHERE id = " + salidaId));
    }

    @Test
    @DisplayName("Salidas: no se puede marcar Listo más de lo que quedó sin marcar")
    void marcarListoSobreSaldoYaConsumido() {
        int ingresoId = ingresoDeLavadero("TestConcListo", "CLASIFICADO");
        int lineaId = insertarClasificacion(ingresoId, catalogoElementoId(1), 5);
        cicloDAO.lanzarTanda(List.of(new LanzamientoCiclo(1, config(), List.of(
            new LineaLanzamiento(lineaId, 5)))));
        cicloDAO.finalizarCiclo(escalar("SELECT MAX(id) FROM ciclos_lavadero"));

        ElementoLavadoPendiente pendiente = salidaDAO.obtenerLavadosPendientesDeListo().get(0);

        // B marca 4 de las 5 y commitea; A tenía las 5 en pantalla.
        salidaDAO.marcarListo(List.of(new MarcaListo(pendiente, 4)));

        assertThrows(ConflictoConcurrenciaException.class,
            () -> salidaDAO.marcarListo(List.of(new MarcaListo(pendiente, 5))));

        assertEquals(4, escalar("SELECT SUM(cantidad) FROM salidas_lavadero"),
            "queda sólo lo de B: la marca de A no se aplicó ni parcialmente");
    }

    // ── Correcciones: ortopedias ──────────────────────────────────────────────

    @Test
    @DisplayName("Correcciones (ortopedias): la segunda corrección con version vieja no aplica")
    void correccionOrtopediasChocaConVersionVieja() {
        Equipo equipo = equipoOrtopediaConMaterial(3);
        int materialId = equipo.getMateriales().get(0).getId();

        // B corrige la cantidad y commitea: la version del equipo pasa de 0 a 1.
        materialDAO.actualizarCantidad(equipo.getId(), materialId, 5, 0);

        // A tenía el mismo snapshot (version 0) cuando B ya había corregido.
        assertThrows(ConflictoConcurrenciaException.class,
            () -> materialDAO.actualizarCantidad(equipo.getId(), materialId, 9, 0));

        assertEquals(5, escalar("SELECT cantidad FROM equipo_materiales WHERE id = " + materialId),
            "queda la cantidad de B, no la de A");
        assertEquals(1, escalar("SELECT version FROM equipos WHERE id = " + equipo.getId()),
            "el bump de A no se commiteó: la guarda revirtió su transacción entera");
    }

    // ── Correcciones: otros ───────────────────────────────────────────────────

    @Test
    @DisplayName("Correcciones (otros): mismo choque, mismo resultado que en ortopedias")
    void correccionOtrosChocaConVersionVieja() {
        EquipoOtros equipo = equipoOtrosConMaterial(3);
        int materialId = equipo.getMateriales().get(0).getId();

        equipoOtrosDAO.actualizarCantidadMaterial(equipo.getId(), materialId, 5, 0);

        assertThrows(ConflictoConcurrenciaException.class,
            () -> equipoOtrosDAO.actualizarCantidadMaterial(equipo.getId(), materialId, 9, 0));

        assertEquals(5, escalar("SELECT cantidad FROM equipo_otros_materiales WHERE id = " + materialId),
            "queda la cantidad de B");
        assertEquals(1, escalar("SELECT version FROM equipo_otros WHERE id = " + equipo.getId()));
    }

    // ── Correcciones: eliminar equipo ─────────────────────────────────────────

    @Test
    @DisplayName("Correcciones: eliminar equipo con version vieja no borra")
    void eliminarEquipoConVersionViejaNoBorra() {
        Equipo equipo = equipoOrtopediaConMaterial(3);
        int materialId = equipo.getMateriales().get(0).getId();

        materialDAO.actualizarCantidad(equipo.getId(), materialId, 5, 0);   // B corrige y commitea

        // A intenta eliminar con el snapshot viejo (version 0).
        assertThrows(ConflictoConcurrenciaException.class,
            () -> equipoDAO.eliminarConVersion(equipo.getId(), 0));

        assertEquals(1, escalar("SELECT COUNT(*) FROM equipos WHERE id = " + equipo.getId()),
            "el DELETE guardado no encontró la version 0: el equipo sigue existiendo");
    }

    // ── Paso 5: la auditoría no queda huérfana ────────────────────────────────

    @Test
    @DisplayName("Correcciones: un conflicto no deja auditoría de una eliminación que no ocurrió")
    void conflictoDeCorreccionNoDejaAuditoriaHuerfana() {
        Equipo equipo = equipoOrtopediaConMaterial(3);
        int materialId = equipo.getMateriales().get(0).getId();

        materialDAO.actualizarCantidad(equipo.getId(), materialId, 5, 0);   // B corrige y commitea

        // A confirma "Eliminar equipo" con el snapshot viejo (version 0), vía el service completo:
        // es el camino que pasa por Paso 5 (snapshots después del DELETE).
        assertThrows(ConflictoConcurrenciaException.class,
            () -> correccionService.eliminarEquipo(equipo.getId(), 0, "motivo de prueba"));

        assertEquals(0, escalar("SELECT COUNT(*) FROM equipos_eliminados"),
            "el DELETE guardado nunca corrió: no hay snapshot de un equipo que sigue vivo");
        assertEquals(0, escalar("SELECT COUNT(*) FROM materiales_eliminados"));
        assertEquals(1, escalar("SELECT COUNT(*) FROM equipos WHERE id = " + equipo.getId()));
    }

    // ── Paso 9: fusión de clientes ────────────────────────────────────────────

    @Test
    @DisplayName("Fusión: mueve equipos de los dos tipos y bumpea su version")
    void fusionMueveEquiposYBumpeaVersion() {
        int origenId  = crearCliente("TestConcFusionOrigen");
        int destinoId = crearCliente("TestConcFusionDestino");
        Equipo equipo = equipoOrtopediaConMaterial(3, origenId);
        EquipoOtros equipoOtros = equipoOtrosConMaterial(3, origenId);

        fusionDAO.fusionar(origenId, "TestConcFusionOrigen", destinoId, "TestConcFusionDestino");

        assertEquals(destinoId, escalar("SELECT nro_cliente FROM equipos WHERE id = " + equipo.getId()));
        assertEquals(1, escalar("SELECT version FROM equipos WHERE id = " + equipo.getId()),
            "el agujero del Paso 9: la fusión también tiene que bumpear");
        assertEquals(destinoId,
            escalar("SELECT nro_cliente FROM equipo_otros WHERE id = " + equipoOtros.getId()));
        assertEquals(1, escalar("SELECT version FROM equipo_otros WHERE id = " + equipoOtros.getId()));
    }

    @Test
    @DisplayName("Fusión: si cambió el nombre del origen, aborta y no mueve nada")
    void fusionarClientesConNombreCambiadoAborta() {
        int origenId  = crearCliente("TestConcFusionRenombrado");
        int destinoId = crearCliente("TestConcFusionDestino2");
        Equipo equipo = equipoOrtopediaConMaterial(3, origenId);

        // B renombra el cliente origen mientras A tenía el diálogo de fusión abierto.
        ejecutarSinChecked("UPDATE clientes SET nombre = 'TestConcOtroNombre' WHERE id = " + origenId);

        assertThrows(ConflictoConcurrenciaException.class, () -> fusionDAO.fusionar(
            origenId, "TestConcFusionRenombrado", destinoId, "TestConcFusionDestino2"));

        assertEquals(origenId, escalar("SELECT nro_cliente FROM equipos WHERE id = " + equipo.getId()),
            "el equipo no se movió");
        assertEquals(1, escalar("SELECT COUNT(*) FROM clientes WHERE id = " + origenId),
            "el cliente origen sigue vivo");
    }

    // ── Paso 8: eliminar cliente ──────────────────────────────────────────────

    @Test
    @DisplayName("Eliminar cliente: si lo renombraron, el CAS no borra")
    void eliminarClienteRenombradoNoBorra() {
        int clienteId = crearCliente("TestConcClienteA");

        // B renombra el cliente mientras A tenía la grilla de Ajustes abierta.
        ejecutarSinChecked("UPDATE clientes SET nombre = 'TestConcClienteB' WHERE id = " + clienteId);

        assertThrows(ConflictoConcurrenciaException.class,
            () -> clienteDAO.eliminarConNombre(clienteId, "TestConcClienteA"));

        assertEquals("TestConcClienteB", texto("SELECT nombre FROM clientes WHERE id = " + clienteId),
            "el cliente sigue vivo, con el nombre que B le puso");
    }

    // ── Paso 4: scope por equipo_id y DELETE por clave sin filas ──────────────

    @Test
    @DisplayName("Correcciones: un material de otro equipo no bumpea ni escribe nada — un caso por lado")
    void materialAjenoAlEquipoNoBumpeaVersion() {
        Equipo equipoA = equipoOrtopediaConMaterial(3);
        Equipo equipoB = equipoOrtopediaConMaterial(3);
        int materialDeB = equipoB.getMateriales().get(0).getId();

        boolean aplico = materialDAO.actualizarCantidad(equipoA.getId(), materialDeB, 9, 0);

        assertFalse(aplico, "el WHERE ... AND equipo_id = ? no encontró el par equipo/material");
        assertEquals(0, escalar("SELECT version FROM equipos WHERE id = " + equipoA.getId()),
            "sin cambio de dato, el bump no se commitea");

        EquipoOtros otrosA = equipoOtrosConMaterial(3);
        EquipoOtros otrosB = equipoOtrosConMaterial(3);
        int materialOtrosDeB = otrosB.getMateriales().get(0).getId();

        int filas = equipoOtrosDAO.actualizarCantidadMaterial(otrosA.getId(), materialOtrosDeB, 9, 0);

        assertEquals(0, filas);
        assertEquals(0, escalar("SELECT version FROM equipo_otros WHERE id = " + otrosA.getId()));
    }

    @Test
    @DisplayName("Correcciones: el DELETE por clave que no encuentra filas es conflicto, no silencio")
    void deleteDeCeroFilasConVersionValidaEsConflicto() {
        Equipo equipo = equipoOrtopediaConMaterial(3);

        // version 0 es la correcta a la vista; el código de catálogo 999 no existe en este equipo.
        assertThrows(ConflictoConcurrenciaException.class,
            () -> materialDAO.eliminarMaterialesPorCodigo(equipo.getId(), 999, 0));

        assertEquals(0, escalar("SELECT version FROM equipos WHERE id = " + equipo.getId()),
            "el bump guardado que sí matcheó no quedó committeado");

        EquipoOtros equipoOtros = equipoOtrosConMaterial(3);

        assertThrows(ConflictoConcurrenciaException.class,
            () -> equipoOtrosDAO.eliminarMaterialesPorDescripcion(equipoOtros.getId(), "NoExiste", 0));

        assertEquals(0, escalar("SELECT version FROM equipo_otros WHERE id = " + equipoOtros.getId()));
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private Equipo equipoOrtopediaConMaterial(int cantidad) {
        return equipoOrtopediaConMaterial(cantidad, 1);
    }

    private Equipo equipoOrtopediaConMaterial(int cantidad, int nroCliente) {
        Equipo equipo = new Equipo();
        equipo.setNroCliente(nroCliente);
        equipo.setNroInstitucion(1);
        equipo.agregarMaterial(new Material(400, "Tornillera", cantidad));
        equipoDAO.guardarEquipo(equipo);
        return equipoDAO.obtenerPorId(String.valueOf(equipo.getId()));
    }

    private EquipoOtros equipoOtrosConMaterial(int cantidad) {
        return equipoOtrosConMaterial(cantidad, 1);
    }

    private EquipoOtros equipoOtrosConMaterial(int cantidad, int nroCliente) {
        EquipoOtros equipo = new EquipoOtros();
        equipo.setNroCliente(nroCliente);
        equipo.setTipoIngreso(TipoIngresoOtros.DETALLES);
        equipo.agregarMaterial(new MaterialOtros("TestConcMaterial", cantidad));
        equipoOtrosDAO.guardar(equipo);
        return equipoOtrosDAO.obtenerTodos().stream()
            .filter(e -> e.getId().equals(equipo.getId()))
            .findFirst().orElseThrow();
    }

    private int crearCliente(String nombre) {
        ejecutarSinChecked("INSERT INTO clientes (nombre) VALUES ('" + nombre + "')");
        return escalar("SELECT id FROM clientes WHERE nombre = '" + nombre + "'");
    }

    private int ingresoDeLavadero(String nombreCliente, String estado) {
        ejecutarSinChecked("INSERT INTO clientes (nombre) VALUES ('" + nombreCliente + "')");
        int clienteId = escalar("SELECT id FROM clientes WHERE nombre = '" + nombreCliente + "'");
        ejecutarSinChecked("INSERT INTO ingresos_lavadero (cliente_id, fecha_ingreso, estado) VALUES ("
            + clienteId + ", NOW(), '" + estado + "')");
        int ingresoId = escalar("SELECT MAX(id) FROM ingresos_lavadero");
        ejecutarSinChecked("INSERT INTO bolsas_lavadero (ingreso_id, peso_kg) VALUES (" + ingresoId + ", 5.00)");
        return ingresoId;
    }

    private int insertarClasificacion(int ingresoId, int elementoId, int cantidad) {
        ejecutarSinChecked("INSERT INTO elementos_clasificacion_lavadero (ingreso_id, elemento_id, cantidad) "
            + "VALUES (" + ingresoId + ", " + elementoId + ", " + cantidad + ")");
        return escalar("SELECT MAX(id) FROM elementos_clasificacion_lavadero");
    }

    private ConfiguracionCiclo config() {
        return new ConfiguracionCiclo(TipoLavado.SUCIO, jabon, new BigDecimal("1.50"), false, false, null);
    }

    // ── Helpers de lectura ────────────────────────────────────────────────────

    private void ejecutarSinChecked(String sql) {
        try {
            ejecutarSQL(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("Fixture fallida: " + sql, e);
        }
    }

    private int escalar(String sql) {
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new IllegalStateException("Consulta fallida: " + sql, e);
        }
    }

    private String texto(String sql) {
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        } catch (SQLException e) {
            throw new IllegalStateException("Consulta fallida: " + sql, e);
        }
    }

    private JabonCatalogo primerJabon() throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, nombre FROM catalogo_jabones ORDER BY id LIMIT 1")) {
            rs.next();
            return new JabonCatalogo(rs.getInt("id"), rs.getString("nombre"));
        }
    }

    private int catalogoElementoId(int offset) {
        return escalar("SELECT id FROM catalogo_elementos_lavadero LIMIT 1 OFFSET " + (offset - 1));
    }
}
