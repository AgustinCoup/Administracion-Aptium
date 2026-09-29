package com.example.features.lavadero.dao;

import com.example.AbstractDAOTest;
import com.example.common.eliminacion.ArchivoIngresosDAO;
import com.example.common.eliminacion.Bloqueo;
import com.example.common.eliminacion.EliminacionBloqueadaException;
import com.example.common.eliminacion.IngresoArchivado;
import com.example.common.eliminacion.ModuloIngreso;
import com.example.common.eliminacion.ResumenIngresoLavadero;
import com.example.common.exception.ConflictoConcurrenciaException;
import com.example.common.exception.DatabaseException;
import com.example.common.exception.ResourceNotFoundException;
import com.example.features.catalogo.dao.CatalogoOtrosDAO;
import com.example.features.clientes.dao.ClienteDAO;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.otros.dao.EliminadorEquipoOtros;
import com.example.features.equipos.otros.dao.EquipoOtrosDAO;
import com.example.features.lavadero.dao.derivadores.AsignadorClienteAptium;
import com.example.features.lavadero.dao.derivadores.AsignadorClienteCDE;
import com.example.features.lavadero.dao.derivadores.ConstructorIngresoCDE;
import com.example.features.lavadero.dao.derivadores.DerivadorFueraDeFlujo;
import com.example.features.lavadero.dao.derivadores.DerivadorIngresoCDE;
import com.example.features.lavadero.dao.derivadores.DerivadorSalidas;
import com.example.features.lavadero.model.AccionSalida;
import com.example.features.lavadero.model.ConfiguracionCiclo;
import com.example.features.lavadero.model.ElementoClasificacion;
import com.example.features.lavadero.model.ElementoCicloItem;
import com.example.features.lavadero.model.ElementoLavadoPendiente;
import com.example.features.lavadero.model.EstadoIngresoLavadero;
import com.example.features.lavadero.model.IngresoHistorial;
import com.example.features.lavadero.model.InsumoCatalogo;
import com.example.features.lavadero.model.JabonCatalogo;
import com.example.features.lavadero.model.LanzamientoCiclo;
import com.example.features.lavadero.model.LineaLanzamiento;
import com.example.features.lavadero.model.MarcaListo;
import com.example.features.lavadero.model.SalidaLista;
import com.example.features.lavadero.model.TipoLavado;
import com.example.features.lotes.dao.LoteDAO;
import com.example.features.lotes.model.Lote;
import com.example.features.lotes.model.LoteMovimiento;
import com.example.infrastructure.db.ConnectionPool;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Los escenarios se arman con los DAOs reales del circuito —clasificar, lanzar, finalizar, marcar
 * Listo, derivar—, no con filas sueltas: lo que se borra tiene que ser exactamente lo que ese
 * circuito deja escrito.
 */
class EliminadorIngresoLavaderoTest extends AbstractDAOTest {

    private static final String MOTIVO = "Se cargó dos veces";
    private static final String PUESTO = "operador@PC-1";
    /** Los ids de instancia del staging son locales a la tanda: cualquiera sirve. */
    private static final int STAGING_ID = 1;

    private final ClasificacionLavaderoDAO clasificacionDAO = new ClasificacionLavaderoDAO();
    private final CicloLavaderoDAO cicloDAO = new CicloLavaderoDAO();
    private final SalidaLavaderoDAO salidaDAO = new SalidaLavaderoDAO();
    private final HistorialLavaderoDAO historialDAO = new HistorialLavaderoDAO();
    private final EquipoOtrosDAO equipoOtrosDAO = new EquipoOtrosDAO(new CatalogoOtrosDAO());
    private final LoteDAO loteDAO = new LoteDAO();
    private final EliminadorIngresoLavadero eliminador = new EliminadorIngresoLavadero(
        new EliminadorEquipoOtros(new ArchivoIngresosDAO()), new ArchivoIngresosDAO());

    private final DerivadorSalidas cdeCliente = new DerivadorIngresoCDE(AccionSalida.CDE_CLIENTE,
        new ConstructorIngresoCDE(), AsignadorClienteCDE.CLIENTE_ORIGINAL, equipoOtrosDAO);
    private final DerivadorSalidas cdeAptium = new DerivadorIngresoCDE(AccionSalida.CDE_APTIUM,
        new ConstructorIngresoCDE(), new AsignadorClienteAptium(new ClienteDAO()), equipoOtrosDAO);

    private JabonCatalogo jabon;

    @BeforeEach
    void leerSemillas() {
        jabon = new JabonCatalogo(escalar("SELECT MIN(id) FROM catalogo_jabones"),
            texto("SELECT nombre FROM catalogo_jabones WHERE id = (SELECT MIN(id) FROM catalogo_jabones)"));
    }

    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM ingresos_eliminados");
        ejecutarSQL("DELETE FROM salidas_lavadero");
        ejecutarSQL("DELETE FROM insumos_ciclo_lavadero");
        ejecutarSQL("DELETE FROM elementos_ciclo_lavadero");
        ejecutarSQL("DELETE FROM instancias_equipo_ciclo");
        ejecutarSQL("DELETE FROM ciclos_lavadero");
        ejecutarSQL("DELETE FROM elementos_clasificacion_lavadero");
        ejecutarSQL("DELETE FROM bolsas_lavadero");
        ejecutarSQL("DELETE FROM ingresos_lavadero");
        ejecutarSQL("DELETE FROM equipo_otros");   // CASCADE: materiales, movimientos, volúmenes
        ejecutarSQL("DELETE FROM lotes");
        ejecutarSQL("DELETE FROM clientes WHERE nombre LIKE 'TestElimLav%'");
    }

    // ── resumir ───────────────────────────────────────────────────────────────

    @Test
    void resumir_inexistente_resourceNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> eliminador.resumir(987654));
    }

    @Test
    void resumir_informaDerivadosYBloqueos() {
        int ingreso = ingreso("TestElimLavResumen");
        int[] lineas = clasificar(ingreso, 5, 3);
        lavarYMarcarListo(1, lineas[0], 5);
        int derivado = derivar(cdeCliente, ingreso);
        lanzar(2, lineas[1], 3);   // la otra línea queda en un ciclo sin finalizar

        ResumenIngresoLavadero resumen = eliminador.resumir(ingreso);

        assertEquals(ModuloIngreso.LAVADERO, resumen.ingreso().modulo());
        assertEquals("TestElimLavResumen", resumen.clienteNombre());
        assertEquals(0, new BigDecimal("7.50").compareTo(resumen.pesoTotalKg()));
        assertEquals(estadoEnBase(ingreso), resumen.estado());
        assertEquals(List.of(5, 3), resumen.elementos().stream().map(ResumenIngresoLavadero.LineaElemento::cantidad).toList());
        assertEquals(1, resumen.derivados().size());
        ResumenIngresoLavadero.DerivadoCde cde = resumen.derivados().get(0);
        assertEquals(derivado, cde.equipoOtrosId());
        assertEquals(5, cde.unidades());
        assertEquals(Set.of(derivado), resumen.idsDerivados());
        assertEquals(List.of(new Bloqueo.CicloEnCurso(2)), resumen.bloqueos());
    }

    // ── eliminar: lo que se borra en cada etapa ───────────────────────────────

    @Test
    void eliminar_pendiente_borraIngresoYBolsasYArchiva() {
        int ingreso = ingreso("TestElimLavPendiente");
        ResumenIngresoLavadero resumen = eliminador.resumir(ingreso);
        assertEquals(EstadoIngresoLavadero.PENDIENTE, resumen.estado());
        assertFalse(resumen.estaBloqueado());

        eliminar(resumen);

        assertEquals(0, contar("ingresos_lavadero WHERE id = " + ingreso));
        assertEquals(0, contar("bolsas_lavadero WHERE ingreso_id = " + ingreso));
        assertEquals(1, contar("ingresos_eliminados"));
        assertEquals("LAVADERO", texto("SELECT modulo FROM ingresos_eliminados"));
        assertEquals("PENDIENTE", texto("SELECT estado FROM ingresos_eliminados"));
        assertEquals("TestElimLavPendiente", texto("SELECT cliente_nombre FROM ingresos_eliminados"));
        assertEquals(ingreso, escalar("SELECT ingreso_id_original FROM ingresos_eliminados"));
        JSONObject snapshot = snapshot("LAVADERO");
        assertEquals(ingreso, snapshot.getJSONObject("ingreso").getInt("id"));
        assertEquals(2, snapshot.getJSONArray("bolsas").length());
        assertTrue(snapshot.getJSONArray("lineas").isEmpty());
    }

    @Test
    void eliminar_clasificado_borraLineas() {
        int ingreso = ingreso("TestElimLavClasif");
        clasificar(ingreso, 4, 6);

        eliminar(eliminador.resumir(ingreso));

        assertEquals(0, contar("elementos_clasificacion_lavadero WHERE ingreso_id = " + ingreso));
        JSONArray lineas = snapshot("LAVADERO").getJSONArray("lineas");
        assertEquals(2, lineas.length());
        assertFalse(lineas.getJSONObject(0).isNull("elemento_nombre"), "la línea se archiva con el nombre del elemento");
    }

    @Test
    void eliminar_conCicloEnCurso_rechazaYNoBorraNada() {
        int ingreso = ingreso("TestElimLavEnCurso");
        int linea = clasificar(ingreso, 5)[0];
        lanzar(3, linea, 5);
        ResumenIngresoLavadero resumen = eliminador.resumir(ingreso);
        assertEquals(List.of(new Bloqueo.CicloEnCurso(3)), resumen.bloqueos(), "el resumen lo avisa antes");

        EliminacionBloqueadaException e = assertThrows(EliminacionBloqueadaException.class, () -> eliminar(resumen));

        assertEquals(List.of(new Bloqueo.CicloEnCurso(3)), e.getBloqueos());
        assertEquals(1, contar("ingresos_lavadero WHERE id = " + ingreso));
        assertEquals(1, contar("elementos_ciclo_lavadero"));
        assertEquals(1, contar("ciclos_lavadero"));
        assertEquals(0, contar("ingresos_eliminados"));
    }

    @Test
    void eliminar_finalizadoConSalidasFueraDeFlujo_borraTodo() {
        int ingreso = ingreso("TestElimLavFinalizado");
        int linea = clasificar(ingreso, 5)[0];
        lavarYMarcarListo(1, linea, 5);
        salidaDAO.derivar(new DerivadorFueraDeFlujo(), listasDe(ingreso));
        ResumenIngresoLavadero resumen = eliminador.resumir(ingreso);
        assertEquals(EstadoIngresoLavadero.FINALIZADO, resumen.estado());
        assertTrue(resumen.derivados().isEmpty(), "fuera de flujo no crea nada en el CDE");

        eliminar(resumen);

        assertEquals(0, contar("salidas_lavadero"));
        assertEquals(0, contar("elementos_ciclo_lavadero"));
        assertEquals(0, contar("elementos_clasificacion_lavadero"));
        assertEquals(0, contar("ingresos_lavadero"));
        assertEquals(0, contar("ciclos_lavadero"), "el ciclo finalizado quedó vacío y se borra");
        JSONObject snapshot = snapshot("LAVADERO");
        assertEquals("FUERA_DE_FLUJO", snapshot.getJSONArray("salidas").getJSONObject(0).getString("destino"));
        assertEquals(1, snapshot.getJSONArray("tandas").length());
        assertEquals(1, snapshot.getJSONArray("ciclos").length());
    }

    // ── eliminar: el derivado al CDE ──────────────────────────────────────────

    @Test
    void eliminar_conDerivadoPropio_borraElEquipoOtrosYArchivaDosFilasEnlazadas() {
        int ingreso = ingreso("TestElimLavDerivado");
        int linea = clasificar(ingreso, 5)[0];
        lavarYMarcarListo(1, linea, 5);
        int derivado = derivar(cdeCliente, ingreso);

        eliminar(eliminador.resumir(ingreso));

        assertEquals(0, contar("equipo_otros WHERE id = " + derivado));
        assertEquals(0, contar("equipo_otros_materiales WHERE equipo_otros_id = " + derivado));
        assertEquals(0, contar("salidas_lavadero"));
        assertEquals(2, contar("ingresos_eliminados"));
        int padre = escalar("SELECT id FROM ingresos_eliminados WHERE modulo = 'LAVADERO'");
        assertEquals(0, contar("ingresos_eliminados WHERE modulo = 'LAVADERO' AND archivo_padre_id IS NOT NULL"));
        assertEquals(padre, escalar("SELECT archivo_padre_id FROM ingresos_eliminados WHERE modulo = 'OTROS'"));
        assertEquals(derivado, escalar("SELECT ingreso_id_original FROM ingresos_eliminados WHERE modulo = 'OTROS'"));
        assertEquals(derivado, snapshot("LAVADERO").getJSONArray("salidas").getJSONObject(0).getInt("equipo_otros_id"),
            "las salidas se archivan antes del SET NULL que dispara el borrado del derivado");
        assertEquals(ingreso, snapshot("OTROS").getJSONArray("salidas_lavadero").getJSONObject(0)
            .getInt("ingreso_lavadero_id"));
    }

    @Test
    void eliminar_conDerivadoEntregado_loBorraIgual() {
        int ingreso = ingreso("TestElimLavEntregado");
        int linea = clasificar(ingreso, 5)[0];
        lavarYMarcarListo(1, linea, 5);
        int derivado = derivar(cdeCliente, ingreso);
        ejecutarSinChecked("UPDATE equipo_otros_materiales SET estado = 'Entregado' WHERE equipo_otros_id = " + derivado);
        ejecutarSinChecked("UPDATE equipo_otros SET estado = 'Entregado' WHERE id = " + derivado);

        eliminar(eliminador.resumir(ingreso));

        assertEquals(0, contar("equipo_otros WHERE id = " + derivado));
        assertEquals("Entregado", texto("SELECT estado FROM ingresos_eliminados WHERE modulo = 'OTROS'"));
    }

    @Test
    void eliminar_conDerivadoEnLoteEnCurso_rechaza() {
        int ingreso = ingreso("TestElimLavLote");
        int linea = clasificar(ingreso, 5)[0];
        lavarYMarcarListo(1, linea, 5);
        int derivado = derivar(cdeCliente, ingreso);
        Lote lote = lanzarLote(derivado);
        ResumenIngresoLavadero resumen = eliminador.resumir(ingreso);
        List<Bloqueo> esperados = List.of(new Bloqueo.DerivadoEnLoteEnCurso(derivado, lote.getIdNegocio()));
        assertEquals(esperados, resumen.bloqueos());

        EliminacionBloqueadaException e = assertThrows(EliminacionBloqueadaException.class, () -> eliminar(resumen));

        assertEquals(esperados, e.getBloqueos());
        assertEquals(1, contar("equipo_otros WHERE id = " + derivado));
        assertEquals(1, contar("ingresos_lavadero WHERE id = " + ingreso));
        assertEquals(0, contar("ingresos_eliminados"));
    }

    @Test
    void eliminar_conDerivadoCompartido_rechazaYNombraAlOtroIngreso() {
        int ingresoA = ingreso("TestElimLavCompartidoA");
        int ingresoB = ingreso("TestElimLavCompartidoB");
        lavarYMarcarListo(1, clasificar(ingresoA, 5)[0], 5);
        lavarYMarcarListo(2, clasificar(ingresoB, 3)[0], 3);
        int derivado = derivar(cdeAptium, ingresoA, ingresoB);   // APTIUM: los dos caen en el mismo
        ResumenIngresoLavadero resumen = eliminador.resumir(ingresoA);
        List<Bloqueo> esperados = List.of(new Bloqueo.DerivadoCompartido(derivado, List.of(ingresoB)));
        assertEquals(esperados, resumen.bloqueos());

        EliminacionBloqueadaException e = assertThrows(EliminacionBloqueadaException.class, () -> eliminar(resumen));

        assertEquals(esperados, e.getBloqueos());
        assertTrue(e.getMessage().contains("#" + derivado), e.getMessage());
        assertTrue(e.getMessage().contains("#" + ingresoB), e.getMessage());
        assertEquals(1, contar("equipo_otros WHERE id = " + derivado));
        assertEquals(2, contar("salidas_lavadero WHERE equipo_otros_id = " + derivado));
        assertEquals(0, contar("ingresos_eliminados"));
    }

    @Test
    void eliminar_variosBloqueos_losInformaTodos() {
        int ingreso = ingreso("TestElimLavVarios");
        int[] lineas = clasificar(ingreso, 5, 3);
        lavarYMarcarListo(1, lineas[0], 5);
        int derivado = derivar(cdeCliente, ingreso);
        Lote lote = lanzarLote(derivado);
        lanzar(4, lineas[1], 3);
        List<Bloqueo> esperados = List.of(new Bloqueo.CicloEnCurso(4),
            new Bloqueo.DerivadoEnLoteEnCurso(derivado, lote.getIdNegocio()));
        ResumenIngresoLavadero resumen = eliminador.resumir(ingreso);

        EliminacionBloqueadaException e = assertThrows(EliminacionBloqueadaException.class, () -> eliminar(resumen));

        assertEquals(esperados, e.getBloqueos());
        assertEquals(esperados, resumen.bloqueos(), "el resumen y la transacción los informan igual y en el mismo orden");
    }

    // ── eliminar: ciclos y fracciones ─────────────────────────────────────────

    @Test
    void eliminar_cicloCompartidoConOtroIngreso_elCicloQuedaConLoDelOtro() {
        int ingresoA = ingreso("TestElimLavCicloA");
        int ingresoB = ingreso("TestElimLavCicloB");
        int lineaA = clasificar(ingresoA, 5)[0];
        int lineaB = clasificar(ingresoB, 2)[0];
        cicloDAO.lanzarTanda(List.of(new LanzamientoCiclo(1, config(), List.of(
            new LineaLanzamiento(lineaA, 5), new LineaLanzamiento(lineaB, 2)))));
        int ciclo = escalar("SELECT MAX(id) FROM ciclos_lavadero");
        cicloDAO.finalizarCiclo(ciclo);

        eliminar(eliminador.resumir(ingresoA));

        assertEquals(1, contar("ciclos_lavadero WHERE id = " + ciclo), "el ciclo sigue: tiene ropa de B");
        assertEquals(1, contar("elementos_ciclo_lavadero WHERE ciclo_id = " + ciclo));
        assertEquals(lineaB, escalar("SELECT elemento_clasificacion_id FROM elementos_ciclo_lavadero"));
        assertEquals(1, contar("ingresos_lavadero WHERE id = " + ingresoB));
    }

    @Test
    void eliminar_cicloQueQuedaVacio_seBorraConSusInsumos() {
        int ingreso = ingreso("TestElimLavInsumos");
        int linea = clasificar(ingreso, 5)[0];
        InsumoCatalogo suavizante = new InsumoCatalogo(escalar("SELECT id FROM catalogo_insumos WHERE nombre = 'Suavizante'"),
            "Suavizante", true);
        cicloDAO.lanzarTanda(List.of(new LanzamientoCiclo(1, new ConfiguracionCiclo(TipoLavado.SUCIO, jabon,
            new BigDecimal("1.50"), List.of(suavizante)), List.of(new LineaLanzamiento(linea, 5)))));
        int ciclo = escalar("SELECT MAX(id) FROM ciclos_lavadero");
        cicloDAO.finalizarCiclo(ciclo);

        eliminar(eliminador.resumir(ingreso));

        assertEquals(0, contar("ciclos_lavadero WHERE id = " + ciclo));
        assertEquals(0, contar("insumos_ciclo_lavadero WHERE ciclo_id = " + ciclo));
        JSONArray insumos = snapshot("LAVADERO").getJSONArray("insumos_ciclos");
        assertEquals(1, insumos.length());
        assertEquals("Suavizante", insumos.getJSONObject(0).getString("insumo_nombre"));
    }

    @Test
    void eliminar_conFraccionesDeEquipo_borraInstanciasYSalidasDeInstancia() {
        int ingreso = ingreso("TestElimLavFracciones");
        int linea = clasificar(ingreso, 1)[0];
        cicloDAO.lanzarTanda(List.of(
            new LanzamientoCiclo(1, config(), List.of(new LineaLanzamiento(linea, 1, STAGING_ID, 2))),
            new LanzamientoCiclo(2, config(), List.of(new LineaLanzamiento(linea, 1, STAGING_ID, 2)))));
        finalizarActivo(1);
        finalizarActivo(2);
        marcarListo(ingreso);
        assertEquals(1, contar("salidas_lavadero WHERE instancia_equipo_id IS NOT NULL"), "precondición");

        eliminar(eliminador.resumir(ingreso));

        assertEquals(0, contar("instancias_equipo_ciclo"));
        assertEquals(0, contar("salidas_lavadero"));
        assertEquals(0, contar("elementos_ciclo_lavadero"));
        assertEquals(0, contar("ciclos_lavadero"), "los dos ciclos quedaron vacíos");
        JSONObject snapshot = snapshot("LAVADERO");
        assertEquals(1, snapshot.getJSONArray("instancias").length());
        assertEquals(2, snapshot.getJSONArray("tandas").length());
        assertFalse(snapshot.getJSONArray("salidas").getJSONObject(0).isNull("instancia_equipo_id"));
    }

    // ── eliminar: guardas ─────────────────────────────────────────────────────

    @Test
    void eliminar_derivadosDistintosALosVistos_conflicto() {
        int ingreso = ingreso("TestElimLavVistos");
        lavarYMarcarListo(1, clasificar(ingreso, 5)[0], 5);
        int derivado = derivar(cdeCliente, ingreso);
        EstadoIngresoLavadero estado = eliminador.resumir(ingreso).estado();

        assertThrows(ConflictoConcurrenciaException.class,
            () -> eliminador.eliminar(ingreso, estado, Set.of(), MOTIVO, PUESTO));
        assertThrows(ConflictoConcurrenciaException.class,
            () -> eliminador.eliminar(ingreso, estado, Set.of(derivado, derivado + 1), MOTIVO, PUESTO));

        assertEquals(1, contar("ingresos_lavadero WHERE id = " + ingreso));
        assertEquals(1, contar("equipo_otros WHERE id = " + derivado));
        assertEquals(0, contar("ingresos_eliminados"));
    }

    @Test
    void eliminar_yaEliminado_conflicto() {
        int ingreso = ingreso("TestElimLavDosVeces");
        ResumenIngresoLavadero resumen = eliminador.resumir(ingreso);
        eliminar(resumen);

        assertThrows(ConflictoConcurrenciaException.class, () -> eliminar(resumen));

        assertEquals(1, contar("ingresos_eliminados"));
    }

    /** Atomicidad a través de los dos eliminadores: falla el archivo del derivado y no queda nada. */
    @Test
    void eliminar_fallaElArchivoDelDerivado_noBorraNada() {
        int ingreso = ingreso("TestElimLavAtomico");
        lavarYMarcarListo(1, clasificar(ingreso, 5)[0], 5);
        int derivado = derivar(cdeCliente, ingreso);
        ArchivoIngresosDAO archivoRoto = new ArchivoIngresosDAO() {
            @Override
            public int archivar(Connection conn, IngresoArchivado copia) throws SQLException {
                int id = super.archivar(conn, copia);
                if (copia.modulo() == ModuloIngreso.OTROS) {
                    throw new SQLException("disco lleno");
                }
                return id;
            }
        };
        EliminadorIngresoLavadero conArchivoRoto = new EliminadorIngresoLavadero(
            new EliminadorEquipoOtros(archivoRoto), archivoRoto);
        ResumenIngresoLavadero resumen = conArchivoRoto.resumir(ingreso);

        assertThrows(DatabaseException.class, () -> conArchivoRoto.eliminar(ingreso, resumen.estado(),
            resumen.idsDerivados(), MOTIVO, PUESTO));

        assertEquals(1, contar("ingresos_lavadero WHERE id = " + ingreso));
        assertEquals(1, contar("equipo_otros WHERE id = " + derivado));
        assertEquals(1, contar("salidas_lavadero WHERE equipo_otros_id = " + derivado));
        assertEquals(1, contar("ciclos_lavadero"));
        assertEquals(0, contar("ingresos_eliminados"), "ni la fila LAVADERO, que ya se había escrito");
    }

    // ── "No puede volver a verse" ─────────────────────────────────────────────

    /**
     * El borrado es físico justamente para que las consultas existentes no tengan que filtrar nada:
     * Disponibles (que lee la clasificación sin pasar por el ingreso), las dos listas de Salidas y el
     * Historial dejan de devolverlo. Un ingreso de control del mismo cliente sigue apareciendo.
     */
    @Test
    void eliminar_despues_disponiblesYSalidasYHistorialNoLoMuestran() {
        int borrado = ingresoConRopaEnCadaPantalla("TestElimLavVisto", 1, 2);
        int control = ingresoConRopaEnCadaPantalla("TestElimLavControl", 3, 4);
        assertTrue(apareceEnTodas(borrado), "precondición: se ve en todas las pantallas");

        eliminar(eliminador.resumir(borrado));

        assertFalse(disponibles().contains(borrado), "Disponibles");
        assertFalse(pendientesDeListo().contains(borrado), "Salidas: pendientes de Listo");
        assertFalse(listasSinDestino().contains(borrado), "Salidas: listas sin destino");
        assertFalse(historial().contains(borrado), "Historial");
        assertTrue(historialDAO.findDetalle(borrado).isEmpty(), "Detalle del Historial");
        assertTrue(apareceEnTodas(control), "el ingreso de control no se tocó");
    }

    // ── Escenarios ────────────────────────────────────────────────────────────

    private void eliminar(ResumenIngresoLavadero resumen) {
        eliminador.eliminar(resumen.ingreso().id(), resumen.estado(), resumen.idsDerivados(), MOTIVO, PUESTO);
    }

    /** Un ingreso {@code PENDIENTE} de 7,5 kg en dos bolsas, de un cliente nuevo. */
    private int ingreso(String cliente) {
        ejecutarSinChecked("INSERT INTO clientes (nombre) VALUES ('" + cliente + "')");
        int clienteId = escalar("SELECT id FROM clientes WHERE nombre = '" + cliente + "'");
        ejecutarSinChecked("INSERT INTO ingresos_lavadero (cliente_id, fecha_ingreso, peso_total_kg) "
            + "VALUES (" + clienteId + ", NOW(), 7.50)");
        int ingreso = escalar("SELECT MAX(id) FROM ingresos_lavadero");
        ejecutarSinChecked("INSERT INTO bolsas_lavadero (ingreso_id, peso_kg) VALUES (" + ingreso + ", 5.00)");
        ejecutarSinChecked("INSERT INTO bolsas_lavadero (ingreso_id, peso_kg) VALUES (" + ingreso + ", 2.50)");
        return ingreso;
    }

    /** Clasifica con elementos distintos del catálogo, uno por cantidad; devuelve las líneas en orden. */
    private int[] clasificar(int ingreso, int... cantidades) {
        List<ElementoClasificacion> elementos = new java.util.ArrayList<>();
        for (int i = 0; i < cantidades.length; i++) {
            elementos.add(new ElementoClasificacion(
                escalar("SELECT id FROM catalogo_elementos_lavadero WHERE activo = TRUE ORDER BY id LIMIT 1 OFFSET " + i), cantidades[i]));
        }
        clasificacionDAO.guardar(ingreso, elementos);
        return idsDe("SELECT id FROM elementos_clasificacion_lavadero WHERE ingreso_id = " + ingreso + " ORDER BY id");
    }

    private void lanzar(int lavarropas, int linea, int cantidad) {
        cicloDAO.lanzarTanda(List.of(new LanzamientoCiclo(lavarropas, config(), List.of(
            new LineaLanzamiento(linea, cantidad)))));
    }

    private void finalizarActivo(int lavarropas) {
        cicloDAO.finalizarCiclo(escalar("SELECT id FROM ciclos_lavadero WHERE fecha_fin IS NULL AND lavarropas_numero = "
            + lavarropas));
    }

    private void lavarYMarcarListo(int lavarropas, int linea, int cantidad) {
        lanzar(lavarropas, linea, cantidad);
        finalizarActivo(lavarropas);
        marcarListo(escalar("SELECT ingreso_id FROM elementos_clasificacion_lavadero WHERE id = " + linea));
    }

    /** Todo lo lavado y pendiente de Listo del ingreso, entero. */
    private void marcarListo(int ingreso) {
        List<MarcaListo> marcas = salidaDAO.obtenerLavadosPendientesDeListo().stream()
            .filter(p -> p.ingresoId() == ingreso)
            .map(p -> new MarcaListo(p, p.cantidadPendiente()))
            .toList();
        salidaDAO.marcarListo(marcas);
    }

    private List<SalidaLista> listasDe(int... ingresos) {
        List<Integer> buscados = Arrays.stream(ingresos).boxed().toList();
        return salidaDAO.obtenerListasSinDestino().stream()
            .filter(s -> buscados.contains(s.ingresoId())).toList();
    }

    /** Deriva al CDE todo lo listo de esos ingresos; devuelve el {@code equipo_otros} creado. */
    private int derivar(DerivadorSalidas derivador, int... ingresos) {
        List<SalidaLista> salidas = listasDe(ingresos);
        salidaDAO.derivar(derivador, salidas);
        return escalar("SELECT equipo_otros_id FROM salidas_lavadero WHERE id = " + salidas.get(0).salidaId());
    }

    /** Un lote en curso con todos los materiales del derivado. */
    private Lote lanzarLote(int equipoOtrosId) {
        List<LoteMovimiento> movimientos = equipoOtrosDAO.obtenerPorId(equipoOtrosId).getMateriales().stream()
            .map(m -> new LoteMovimiento(m.getId(), equipoOtrosId, m.getCantidad(), true,
                EstadoEquipo.desdeBD(texto("SELECT estado FROM equipo_otros_materiales WHERE id = " + m.getId()))))
            .toList();
        return loteDAO.lanzarLote("E01", 120, 45, movimientos, Map.of(equipoOtrosId, 10));
    }

    /**
     * Tres líneas: una sin lanzar (Disponibles), una lavada sin marcar (Salidas: pendientes) y una
     * marcada Listo (Salidas: listas sin destino).
     */
    private int ingresoConRopaEnCadaPantalla(String cliente, int lavarropasListo, int lavarropasPendiente) {
        int ingreso = ingreso(cliente);
        int[] lineas = clasificar(ingreso, 4, 3, 2);
        lavarYMarcarListo(lavarropasListo, lineas[2], 2);
        lanzar(lavarropasPendiente, lineas[1], 3);
        finalizarActivo(lavarropasPendiente);
        return ingreso;
    }

    private boolean apareceEnTodas(int ingreso) {
        return disponibles().contains(ingreso) && pendientesDeListo().contains(ingreso)
            && listasSinDestino().contains(ingreso) && historial().contains(ingreso)
            && !historialDAO.findDetalle(ingreso).isEmpty();
    }

    private List<Integer> disponibles() {
        return cicloDAO.obtenerElementosDisponiblesParaCiclo().stream().map(ElementoCicloItem::getIngresoId).toList();
    }

    private List<Integer> pendientesDeListo() {
        return salidaDAO.obtenerLavadosPendientesDeListo().stream().map(ElementoLavadoPendiente::ingresoId).toList();
    }

    private List<Integer> listasSinDestino() {
        return salidaDAO.obtenerListasSinDestino().stream().map(SalidaLista::ingresoId).toList();
    }

    private List<Integer> historial() {
        return historialDAO.obtenerHistorial().stream().map(IngresoHistorial::id).toList();
    }

    private ConfiguracionCiclo config() {
        return new ConfiguracionCiclo(TipoLavado.SUCIO, jabon, new BigDecimal("1.50"), List.of());
    }

    // ── Lectura ───────────────────────────────────────────────────────────────

    private EstadoIngresoLavadero estadoEnBase(int ingreso) {
        return EstadoIngresoLavadero.desdeBD(texto("SELECT estado FROM ingresos_lavadero WHERE id = " + ingreso));
    }

    private JSONObject snapshot(String modulo) {
        return new JSONObject(texto("SELECT snapshot FROM ingresos_eliminados WHERE modulo = '" + modulo + "'"));
    }

    private int contar(String tablaYCondicion) {
        return escalar("SELECT COUNT(*) FROM " + tablaYCondicion);
    }

    private int[] idsDe(String sql) {
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            List<Integer> ids = new java.util.ArrayList<>();
            while (rs.next()) {
                ids.add(rs.getInt(1));
            }
            return ids.stream().mapToInt(Integer::intValue).toArray();
        } catch (SQLException e) {
            throw new IllegalStateException("Consulta fallida: " + sql, e);
        }
    }

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
}
