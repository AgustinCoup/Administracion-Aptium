package com.example.features.lavadero.dao;

import com.example.AbstractDAOTest;
import com.example.features.lavadero.model.JabonCatalogo;
import com.example.features.lavadero.model.TipoLavado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JabonPorTipoLavadoDAOTest extends AbstractDAOTest {

    private JabonPorTipoLavadoDAO dao;
    private CatalogoJabonesDAO jabonesDAO;

    @BeforeEach
    void setUp() {
        dao = new JabonPorTipoLavadoDAO();
        jabonesDAO = new CatalogoJabonesDAO();
    }

    /**
     * Los defaults los siembra la V26 y son datos de referencia: se restauran, no se borran. El
     * orden importa — primero las filas que apuntan a los jabones de prueba, después los jabones
     * (la FK es RESTRICT).
     */
    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM jabon_por_tipo_lavado");
        ejecutarSQL("DELETE FROM catalogo_jabones WHERE nombre LIKE 'TestJabon%'");
        ejecutarSQL("UPDATE catalogo_jabones SET activo = TRUE");
        ejecutarSQL(
            "INSERT INTO jabon_por_tipo_lavado (tipo_lavado, jabon_id) " +
            "SELECT 'SUCIO', id FROM catalogo_jabones WHERE nombre = 'Skip'");
        ejecutarSQL(
            "INSERT INTO jabon_por_tipo_lavado (tipo_lavado, jabon_id) " +
            "SELECT 'LIMPIO', id FROM catalogo_jabones WHERE nombre = 'Lider'");
    }

    @Test
    @DisplayName("Los defaults que sembró la V26: Sucio → Skip, Limpio → Lider")
    void obtenerDefaults_traeLosSeedsDeLaV26() {
        Map<TipoLavado, JabonCatalogo> defaults = dao.obtenerDefaults();

        assertEquals("Skip", defaults.get(TipoLavado.SUCIO).getNombre());
        assertEquals("Lider", defaults.get(TipoLavado.LIMPIO).getNombre());
    }

    @Test
    @DisplayName("Guardar sobre un tipo que ya tiene default lo reemplaza (upsert, no INSERT duplicado)")
    void guardar_sobreUnTipoQueYaTieneDefault_loReemplaza() {
        JabonCatalogo nuevo = jabonesDAO.agregar("TestJabon Upsert");

        dao.guardar(TipoLavado.SUCIO, nuevo.getId());

        Map<TipoLavado, JabonCatalogo> defaults = dao.obtenerDefaults();
        assertEquals(nuevo.getId(), defaults.get(TipoLavado.SUCIO).getId());
        // El otro tipo no se tocó: la PK es por tipo, no una fila global.
        assertEquals("Lider", defaults.get(TipoLavado.LIMPIO).getNombre());
    }

    @Test
    void guardar_sobreUnTipoSinDefault_loCrea() {
        JabonCatalogo nuevo = jabonesDAO.agregar("TestJabon Alta");
        dao.borrar(TipoLavado.LIMPIO);

        dao.guardar(TipoLavado.LIMPIO, nuevo.getId());

        assertEquals(nuevo.getId(), dao.obtenerDefaults().get(TipoLavado.LIMPIO).getId());
    }

    @Test
    @DisplayName("\"(sin default)\": borrar deja el tipo fuera del mapa, y es idempotente")
    void borrar_dejaElTipoSinDefault() {
        dao.borrar(TipoLavado.SUCIO);
        assertFalse(dao.obtenerDefaults().containsKey(TipoLavado.SUCIO));

        dao.borrar(TipoLavado.SUCIO);   // segunda vez: el resultado buscado, no un choque
        assertFalse(dao.obtenerDefaults().containsKey(TipoLavado.SUCIO));
    }

    /**
     * El {@code JOIN} no filtra por {@code activo} a propósito: quien decide no usar un default
     * dado de baja es {@code SelectorJabonAutomatico}, y Ajustes lo tiene que poder mostrar para
     * que alguien lo arregle. Si la consulta lo escondiera, el operador vería "sin default" y no
     * tendría forma de entender por qué la card no carga nada.
     */
    @Test
    void obtenerDefaults_devuelveElDefaultAunqueSuJabonEsteDadoDeBaja() {
        JabonCatalogo nuevo = jabonesDAO.agregar("TestJabon Retirado");
        dao.guardar(TipoLavado.SUCIO, nuevo.getId());
        jabonesDAO.darDeBaja(nuevo.getId());

        JabonCatalogo porDefecto = dao.obtenerDefaults().get(TipoLavado.SUCIO);

        assertNotNull(porDefecto, "el default no se esconde por estar dado de baja");
        assertEquals(nuevo.getId(), porDefecto.getId());
        assertFalse(porDefecto.isActivo(), "y llega con el estado puesto, para que la regla decida");
    }

    /**
     * La PK es texto libre, así que una fila basura —una corrección manual, una versión futura del
     * enum— no puede mapearse a {@code SUCIO} como hace {@code TipoLavado.desdeBD}: pisaría el
     * default verdadero según el orden en que la base devuelva las filas.
     */
    @Test
    void obtenerDefaults_ignoraUnTipoDesconocidoSinPisarElDefaultVerdadero() throws SQLException {
        ejecutarSQL(
            "INSERT INTO jabon_por_tipo_lavado (tipo_lavado, jabon_id) " +
            "SELECT 'TIBIO', id FROM catalogo_jabones WHERE nombre = 'Lider'");

        Map<TipoLavado, JabonCatalogo> defaults = dao.obtenerDefaults();

        assertEquals(2, defaults.size());
        assertEquals("Skip", defaults.get(TipoLavado.SUCIO).getNombre());
    }

    @Test
    void obtenerDefaults_sinNingunaFila_devuelveMapaVacio() {
        dao.borrar(TipoLavado.SUCIO);
        dao.borrar(TipoLavado.LIMPIO);

        assertTrue(dao.obtenerDefaults().isEmpty());
    }
}
