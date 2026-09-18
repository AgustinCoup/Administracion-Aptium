package com.example.features.equipos.dao;

import com.example.features.equipos.model.FiltroEquipos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El armado del {@code WHERE} en aislamiento: sin base, sin JDBC. Lo que se prueba acá es la forma
 * del SQL y la correspondencia entre marcadores y parámetros — lo que se prueba contra H2, en
 * {@code EquipoDAOPaginacionTest} y {@code CdeConsultaDAOTest}, es que el resultado coincida con el
 * filtrado en memoria de hoy.
 */
class FiltroEquiposSqlTest {

    @Test
    @DisplayName("sin filtros no hay WHERE, ni JOIN, ni parámetros")
    void sinFiltros_condicionVacia() {
        FiltroEquiposSql.Condicion condicion = FiltroEquiposSql.paraOrtopedias(FiltroEquipos.sinFiltros());

        assertEquals("", condicion.sql());
        assertEquals("", condicion.joins());
        assertTrue(condicion.parametros().isEmpty());
    }

    @Nested
    @DisplayName("los JOIN siguen al filtro")
    class JoinsSegunFiltro {

        /**
         * Medido con {@code EXPLAIN} sobre 3 000 equipos (MySQL 8.0.43): con los tres
         * {@code LEFT JOIN} puestos y sin ningún predicado que los use, el optimizador deja de
         * considerar {@code idx_equipos_fecha_ingreso} y resuelve el {@code ORDER BY} con
         * {@code type: ALL} + {@code Using filesort} sobre la tabla entera. Sin ellos, el mismo
         * {@code ORDER BY … LIMIT 50} sale por {@code Backward index scan} tocando 50 filas.
         */
        @Test
        @DisplayName("sin filtros de texto no se une ninguna tabla de nombres")
        void sinFiltrosDeTexto_ningunJoin() {
            FiltroEquipos soloEstados = new FiltroEquipos(
                List.of("Nuevo"), null, null, null, null, List.of(), null, null);

            assertEquals("", FiltroEquiposSql.paraOrtopedias(soloEstados).joins());
            assertEquals("", FiltroEquiposSql.paraOtros(soloEstados).joins());
        }

        @Test
        @DisplayName("cada filtro de texto trae exactamente su JOIN, y ninguno más")
        void cadaFiltroTraeSuJoin() {
            assertEquals("LEFT JOIN clientes c ON e.nro_cliente = c.id ",
                FiltroEquiposSql.paraOrtopedias(conCliente("x")).joins());

            FiltroEquipos soloProfesional = new FiltroEquipos(
                List.of(), null, "x", null, null, List.of(), null, null);
            assertEquals("LEFT JOIN profesionales p ON e.nro_profesional = p.id ",
                FiltroEquiposSql.paraOrtopedias(soloProfesional).joins());

            FiltroEquipos soloInstitucion = new FiltroEquipos(
                List.of(), null, null, null, "x", List.of(), null, null);
            assertEquals("LEFT JOIN instituciones i ON e.nro_institucion = i.id ",
                FiltroEquiposSql.paraOrtopedias(soloInstitucion).joins());
        }

        /** El filtro de paciente es sobre una columna de {@code equipos}: no necesita ningún JOIN. */
        @Test
        @DisplayName("el filtro de paciente no trae JOIN: la columna es de equipos")
        void pacienteNoNecesitaJoin() {
            FiltroEquipos soloPaciente = new FiltroEquipos(
                List.of(), null, null, "pérez", null, List.of(), null, null);

            FiltroEquiposSql.Condicion condicion = FiltroEquiposSql.paraOrtopedias(soloPaciente);
            assertEquals("", condicion.joins());
            assertTrue(condicion.sql().contains("e.paciente"));
        }

        /**
         * Es la invariante que hace que separar los {@code JOIN} del {@code WHERE} sea seguro: un
         * {@code WHERE} que nombra un alias sin su {@code JOIN} no compila en el servidor, y ningún
         * test de caso lo detectaría salvo el que ejecute justo ese filtro.
         */
        @Test
        @DisplayName("todo alias que aparece en el WHERE tiene su JOIN en la misma condición")
        void ningunAliasSinSuJoin() {
            FiltroEquipos todos = new FiltroEquipos(
                List.of("Nuevo"), "a", "b", "c", "d", List.of("REMITO"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2));

            for (FiltroEquiposSql.Condicion condicion : List.of(
                    FiltroEquiposSql.paraOrtopedias(todos),
                    FiltroEquiposSql.paraOtros(todos),
                    FiltroEquiposSql.paraOtrosEnUnionCde(todos))) {
                for (String alias : List.of("c.nombre", "p.nombre", "i.nombre")) {
                    if (condicion.sql().contains(alias)) {
                        String tabla = alias.substring(0, 1);
                        assertTrue(condicion.joins().contains(" " + tabla + " ON "),
                            "el WHERE usa " + alias + " pero la condición no une esa tabla: "
                                + condicion.joins() + " | " + condicion.sql());
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("un marcador por valor, nunca el valor en la sentencia")
    void cadaValorViajaPorUnMarcador() {
        FiltroEquipos filtro = new FiltroEquipos(
            List.of("Nuevo", "Lavando"), "clínica", "dr", "pérez", "hospital",
            List.of(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        FiltroEquiposSql.Condicion condicion = FiltroEquiposSql.paraOrtopedias(filtro);

        assertEquals(condicion.parametros().size(), contarMarcadores(condicion.sql()),
            "cada ? tiene que tener su parámetro y viceversa: " + condicion.sql());
        assertFalse(condicion.sql().contains("clínica"),
            "ningún valor del operador puede aparecer en la sentencia");
        assertFalse(condicion.sql().contains("hospital"));
    }

    @Nested
    @DisplayName("comodines")
    class Comodines {

        /**
         * {@code contains} de Java no conoce comodines. Si el {@code %} tipeado no se escapara,
         * buscar "50%" traería cualquier cliente que empiece con "50" — un resultado plausible, que
         * es lo que lo hace difícil de detectar mirando la pantalla.
         */
        @Test
        @DisplayName("el % tipeado por el operador se busca literal, no como comodín")
        void porcentajeTipeado_seEscapa() {
            FiltroEquipos filtro = conCliente("50%");

            FiltroEquiposSql.Condicion condicion = FiltroEquiposSql.paraOrtopedias(filtro);

            assertEquals("%50!%%", condicion.parametros().get(0));
            assertTrue(condicion.sql().contains("ESCAPE '!'"),
                "el patrón escapa con ! y la cláusula tiene que declararlo");
        }

        @Test
        @DisplayName("el guión bajo y el propio ! también se escapan")
        void guionBajoYEscape_seEscapan() {
            assertEquals("%a!_b%",  FiltroEquiposSql.paraOrtopedias(conCliente("a_b")).parametros().get(0));
            assertEquals("%a!!b%",  FiltroEquiposSql.paraOrtopedias(conCliente("a!b")).parametros().get(0));
        }

        @Test
        @DisplayName("el texto se compara en minúsculas, igual que el contains de la pantalla")
        void textoEnMinusculas() {
            assertEquals("%clínica%",
                FiltroEquiposSql.paraOrtopedias(conCliente("CLÍNICA")).parametros().get(0));
        }
    }

    @Nested
    @DisplayName("fechas")
    class Fechas {

        /**
         * La columna es un {@code TIMESTAMP}, no un {@code DATE}: con {@code <= hasta} se perderían
         * los equipos ingresados ese mismo día después de medianoche, que son casi todos.
         */
        @Test
        @DisplayName("hasta es inclusivo por día, o sea < hasta + 1 en SQL")
        void hastaEsExclusivoDelDiaSiguiente() {
            FiltroEquipos filtro = new FiltroEquipos(List.of(), null, null, null, null, List.of(),
                null, LocalDate.of(2026, 1, 31));

            FiltroEquiposSql.Condicion condicion = FiltroEquiposSql.paraOrtopedias(filtro);

            assertTrue(condicion.sql().contains("e.fecha_ingreso < ?"), condicion.sql());
            assertEquals(java.sql.Timestamp.valueOf(LocalDate.of(2026, 2, 1).atStartOfDay()),
                condicion.parametros().get(0));
        }

        @Test
        @DisplayName("desde arranca a las 00:00 del día")
        void desdeEsElComienzoDelDia() {
            FiltroEquipos filtro = new FiltroEquipos(List.of(), null, null, null, null, List.of(),
                LocalDate.of(2026, 1, 1), null);

            assertEquals(java.sql.Timestamp.valueOf(LocalDate.of(2026, 1, 1).atStartOfDay()),
                FiltroEquiposSql.paraOrtopedias(filtro).parametros().get(0));
        }
    }

    @Nested
    @DisplayName("lo que NO aplica a 'otros'")
    class NoAplicaAOtros {

        /**
         * El filtrado en memoria de Ver Equipos aplicaba profesional, paciente e institución
         * <b>sólo</b> a la grilla de ortopedias. Preservarlo es el punto: es el comportamiento que
         * el operador conoce.
         */
        @Test
        @DisplayName("profesional, paciente e institución no filtran la grilla de 'otros'")
        void tresCamposIgnorados() {
            FiltroEquipos filtro = new FiltroEquipos(
                List.of(), null, "dr", "pérez", "hospital", List.of(), null, null);

            FiltroEquiposSql.Condicion condicion = FiltroEquiposSql.paraOtros(filtro);

            assertEquals("", condicion.sql(),
                "los tres campos no aplican a equipo_otros: la condición tiene que quedar vacía");
            assertEquals("", condicion.joins());
            assertTrue(condicion.parametros().isEmpty());
        }

        @Test
        @DisplayName("el tipo de ingreso no filtra la grilla de ortopedias")
        void tipoIngresoIgnoradoEnOrtopedias() {
            FiltroEquipos filtro = new FiltroEquipos(
                List.of(), null, null, null, null, List.of("REMITO"), null, null);

            assertEquals("", FiltroEquiposSql.paraOrtopedias(filtro).sql());
            assertTrue(FiltroEquiposSql.paraOtros(filtro).sql().contains("eo.tipo_ingreso IN (?)"));
        }
    }

    @Nested
    @DisplayName("la asimetría de Estado de Procesos")
    class AsimetriaDelCde {

        /**
         * El filtrado en memoria pasaba {@code getDescripcionSecundaria()} —cadena vacía para
         * "otros"— por {@code containsIgnoreCase}, que con filtro no vacío da falso siempre. O sea
         * que con el campo institución escrito, ningún "otros" entra.
         */
        @Test
        @DisplayName("con institución escrita, ningún 'otros' entra en la unión")
        void institucionConTexto_excluyeATodosLosOtros() {
            FiltroEquipos filtro = new FiltroEquipos(
                List.of(), null, null, null, "hospital", List.of(), null, null);

            FiltroEquiposSql.Condicion condicion = FiltroEquiposSql.paraOtrosEnUnionCde(filtro);

            assertTrue(condicion.sql().contains("1 = 0"),
                "la condición imposible es la traducción literal de containsIgnoreCase(\"\", x)");
        }

        @Test
        @DisplayName("con institución en blanco, los 'otros' entran normalmente")
        void institucionVacia_noAgregaNada() {
            FiltroEquipos filtro = new FiltroEquipos(
                List.of("Nuevo"), null, null, null, "   ", List.of(), null, null);

            FiltroEquiposSql.Condicion condicion = FiltroEquiposSql.paraOtrosEnUnionCde(filtro);

            assertFalse(condicion.sql().contains("1 = 0"),
                "un campo en blanco no es un filtro puesto");
            assertEquals(FiltroEquiposSql.paraOtros(filtro).sql(), condicion.sql());
        }

        @Test
        @DisplayName("la condición imposible se suma al resto del WHERE, no lo reemplaza")
        void seCombinaConLosOtrosFiltros() {
            FiltroEquipos filtro = new FiltroEquipos(
                List.of("Nuevo"), "clínica", null, null, "hospital", List.of(), null, null);

            FiltroEquiposSql.Condicion condicion = FiltroEquiposSql.paraOtrosEnUnionCde(filtro);

            assertTrue(condicion.sql().startsWith(" WHERE "), condicion.sql());
            assertTrue(condicion.sql().endsWith(" AND 1 = 0"), condicion.sql());
            assertEquals(condicion.parametros().size(), contarMarcadores(condicion.sql()),
                "la condición imposible no lleva parámetro y no puede correr los índices");
        }
    }

    @Test
    @DisplayName("marcadores() emite tantos ? como valores")
    void marcadores() {
        assertEquals("?", FiltroEquiposSql.marcadores(1));
        assertEquals("?, ?, ?", FiltroEquiposSql.marcadores(3));
    }

    private static FiltroEquipos conCliente(String cliente) {
        return new FiltroEquipos(List.of(), cliente, null, null, null, List.of(), null, null);
    }

    private static long contarMarcadores(String sql) {
        return sql.chars().filter(c -> c == '?').count();
    }
}
