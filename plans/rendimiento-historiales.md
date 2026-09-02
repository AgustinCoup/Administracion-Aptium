# Plan — Rendimiento de las pantallas de consulta

**Objetivo:** que abrir cualquier pantalla de consulta deje de hacer esperar. Dos fases:
**A)** borrar el costo real (N+1, subconsultas sin acotar, índices faltantes, fan-out del historial
de lavadero) y **B)** esconder el que quede, mostrando primero lo que importa —lo no
entregado/finalizado— mientras el resto del histórico sigue cargando por detrás.

**Rama:** `RendimientoHistoriales` (creada desde `RetoquesFinalesL` el 2026-09-02)
**Modo:** directo — un commit por paso, sin PRs (`gh` no está instalado)
**Fecha de creación:** 2026-09-02 · **Revisado adversarialmente:** 2026-09-02 (18 hallazgos aplicados)

> Árbol limpio al crear el plan. La rama arranca justo después de `40c8021`
> (cierre de `plans/historial-lavadero.md`).

---

## Decisiones tomadas con el usuario

| Tema | Decisión |
|---|---|
| Alcance | **Las dos fases, sí o sí.** No hay compuerta sobre *si* se construye la Fase B: se construye. |
| Medición | **Log de tiempos en `TareaUI`.** Es el envoltorio por el que ya pasa toda lectura de fondo, así que instrumentarlo mide **todas** las pantallas gratis, sobre la base real, y queda como instrumento permanente. |
| Corte del histórico | **No se acota nada de forma permanente.** Se descartó la ventana temporal y el "sólo activos con archivo bajo demanda": el usuario sigue viendo todo, sólo que en dos tiempos. Ninguna pantalla pierde datos. |
| Dónde duele hoy | **Historial de Lavadero** y **la pantalla Ver general del CDE** (`Ver Equipos` + `Estado de procesos`). |

> ⚠️ **Matiz agregado por la revisión, pendiente de confirmación del usuario.** La maquinaria de dos
> fases se construye para los cuatro grupos de consulta (decisión respetada), pero **se cablea por
> grupo sólo donde la medición muestre que la fase prioritaria es efectivamente más rápida**
> (ver Paso 8, tarea 6). Motivo: el predicado "activo" de equipos es un `OR` de `EXISTS`/`NOT EXISTS`
> correlacionados que MySQL no puede indexar, así que una vez muerto el N+1 la fase 1 podría tardar
> **igual o más** que la completa. Cablearla ahí sería un empeoramiento con cartel de carga incluido.
> Esto **no** es la compuerta que el usuario descartó (aquella era "quizá no construir la Fase B");
> es no enchufarla donde daña. Cualquier grupo que quede en una fase se anota en "Mutaciones aplicadas".

### Decisiones de diseño tomadas por el plan (con su porqué)

| Tema | Decisión | Por qué |
|---|---|---|
| La fase 2 relee **todo**, no el complemento | La segunda lectura trae el histórico completo y **pisa** el snapshot de la fase 1 | Mantiene intacta la invariante que hace barato todo esto: *un snapshot reemplaza al anterior*. **Verificado en el código:** `AbstractFilterController.recargarCache` (9-12), `EstadoProcesosController.pintar` (55-59), `VerCiclosController.pintar` (34-35) y `VerLotesController.pintar` (48-55) son reemplazo en bloque, y el único estado derivado del snapshot —el combo de autoclaves de `VerLotesController:53`— ya preserva su selección (`PantallaVerLotes.setEquiposFiltro` 201-213). Traer sólo el complemento obligaría a lógica de *merge* en cada controller. |
| Las dos fases viven **sólo** en `RefrescadorPantallas` | Ni los controllers ni las vistas se enteran de que hubo dos lecturas | Los cinco grupos ya comparten esa clase. Meter el concepto de "fase" en los controllers lo repetiría cinco veces. |
| API por **factorías**, agregadas antes de quitar el constructor | `enUnaFase(...)` / `enDosFases(...)` se agregan en el Paso 7 **sin** privatizar el constructor; el Paso 8 migra los cinco *call sites* y recién ahí lo cierra | `UiCoordinator` hace `new RefrescadorPantallas<>(...)` en 263, 281, 293, 301 y 312: privatizar el constructor en el Paso 7 rompería la compilación y obligaría al Paso 7 a tocar `UiCoordinator`, que es justo lo que lo volvería no paralelizable. |
| El grupo `operativo` **no** se hace en dos fases | Sigue en una sola lectura | Ya lee sólo la cola activa (`DatosOperativos`), acotada por definición. Su problema lo arregla la Fase A: arrastra el mismo N+1 vía `obtenerActivos()` y corre **en cada guardado**, donde no hay apertura de pantalla detrás de la cual esconderse. |
| Preservar selección **por identidad**, no por índice | El helper guarda el id, no el `rowIndex`, y hace la conversión vista↔modelo explícita en los extremos | Entre fase 1 y fase 2 las filas se corren de lugar. **Aclaración verificada:** las tablas de las pantallas Ver **no** tienen `RowSorter` (`setAutoCreateRowSorter`/`setRowSorter` sólo aparece en `PanelGestionClientes`), así que los `convertRowIndexToModel` de `VerEquiposController` 172/182 y `HistorialLavaderoController:97` son defensivos y hoy son la identidad. El helper igual hace la conversión: es gratis, es correcta, y deja de ser identidad el día que alguien haga las tablas ordenables. |
| El indicador de carga parcial es **obligatorio** | Mientras corre la fase 2 la pantalla dice que faltan datos; si la fase 2 falla, lo dice también | Sin él, una lista parcial se lee como completa: el usuario busca un equipo entregado, no lo ve y concluye que no existe. Es el único punto de la Fase B que es de **corrección**, no de percepción. |
| El fallo de la fase 2 **no** abre modal | Va sólo al indicador; el `JOptionPane` queda para `enUnaFase` y para una cadena que no alcanzó a pintar nada | `UiCoordinator.mostrarErrorDeRefresco` (345-352) es un `JOptionPane` **modal**. Con dos lecturas por apertura se duplica la probabilidad de que aparezca, y taparía datos parciales que son perfectamente usables. |
| El `MAX(fecha)` de movimientos pasa a **subconsulta correlacionada** | En vez de `LEFT JOIN (SELECT material_id, MAX(fecha) … GROUP BY material_id)` | La tabla derivada agrega **toda** la tabla de movimientos, que crece para siempre. La correlacionada pega contra `idx_mov_material` / `idx_otros_mov_material` (`V1:87`, `V2:53`) y resuelve cada fila con un *index range scan*. Corre igual en MySQL 8 y H2 2.2. Sin migración. |
| **No** se extrae una abstracción común entre `EquipoDAO` y `EquipoOtrosDAO` | Las dos consultas quedan con la misma *forma*, no con código compartido | Tablas, columnas y modelos distintos; sólo comparten el patrón. Es la lección que dejó el borrado de `IMaterialFilter`/`ICapacidadCalculator`. Lo que **sí** se comparte de verdad (preservar selección, indicador) va a `ui/common/`. |
| El fallo silencioso de `listar()` se arregla **junto con** su llamador del EDT | Propagar la excepción **y** pasar `abrirDetalleOtros` a `TareaUI`, en el mismo paso | Ver Paso 2, tarea 4. Propagar sin arreglar al llamador cambia un fallo silencioso por uno **invisible**: `VerEquiposController.abrirDetalleOtros` (179-189) llama `obtenerPorId` sincrónico en el EDT, sin `try/catch`, y hoy se apoya en que un error devuelve lista vacía → `null` → `return`. |

---

## Contexto compartido (leer una vez por sesión)

App de escritorio **Swing, Java 17, Maven**, sin framework de DI. Capas por feature:
`model → dao → service → view/controller`. Todo se cablea a mano en `AppContext` y `UiCoordinator`.
**MySQL 8** en producción (`mysql-connector-j` 8.3), **H2 2.2 en `MODE=MySQL`** en los tests.

### Reglas duras del repo que este plan debe respetar

1. **Ningún acceso a BD en el EDT.** `TareaUI` (`ui/common/`) es el único mecanismo de trabajo en
   fondo. No hay `new Thread()` ni `SwingWorker` fuera de esa clase. `EdtGuard` grita en el log si
   alguien vuelve a poner I/O en el EDT.
2. **El estado mutable de un controller se lee y escribe sólo en el EDT.**
3. **Una migración ya escrita no se toca.** La próxima libre es **`V21`** (V1–V20 presentes, sin huecos).
4. **Los services no tienen JDBC.** Validan y delegan.
5. **Lógica embebida en Swing → clase plana sin Swing, testeada en aislamiento.**
6. **Nada del lavadero se borra nunca.**
7. **Todo SQL corre igual en MySQL 8 y en H2 2.2 `MODE=MySQL`.** Nada de `GROUP_CONCAT`/`STRING_AGG`;
   subconsultas correlacionadas, CTE y funciones de ventana sí funcionan en ambos.
8. **Un DAO que falla propaga.** `DataAccessException`/`DatabaseException`, nunca lista vacía o a
   medias. Referencia: `SalidaLavaderoDAO`, `HistorialLavaderoDAO`.

### Diagnóstico medido — de dónde sale este plan

Relevado sobre el código el 2026-09-02 y verificado en revisión adversarial. **Ordenado por daño:**

| # | Hallazgo | Dónde | Por qué duele |
|---|---|---|---|
| 1 | **N+1**: una consulta extra **por equipo** | `EquipoOtrosDAO.listar()` 229-249 — el `cargarMateriales(conn, eq)` dentro del `while (rs.next())` | Con N equipos son N+1 consultas. Y **cada una** lleva adentro el hallazgo #2. Lo usan `obtenerTodos()` (pantallas de consulta) **y** `obtenerActivos()`, que corre **en cada guardado**. |
| 2 | Tabla derivada que agrega **toda** la tabla de movimientos, sin `WHERE` | `EquipoOtrosDAO.cargarMateriales()` 767-798 y `EquipoDAO.SQL_EQUIPOS_CON_MATERIALES` 46-64 (+ otra copia ~292) | `material_movimientos` y `otros_material_movimientos` crecen una fila por cambio de estado y **nunca se podan**. En `EquipoDAO` corre una vez por listado; en `EquipoOtrosDAO`, combinado con #1, **una vez por equipo**. |
| 3 | **Cero índices sobre `fecha_ingreso` y `estado`** | `equipos` (`V1:31-46`), `equipo_otros` (`V2:12-26`), `ingresos_lavadero` (`V7:1-6`; la columna `estado` la agrega **`V10:1-3`**) | ⚠️ **Leer la advertencia del Paso 4**: no arregla el `ORDER BY` de los listados. Paga otras consultas, y son reales. |
| 4 | Fan-out cartesiano para poblar filtros que no se muestran | `HistorialLavaderoDAO.SQL_RESUMEN` 68-77 | Multiplica ingresos × clasificación × ciclos para llenar dos `Set` que sólo alimentan los filtros "Elemento" y "Lavarropas #". Ya anotado como MEDIUM en `plans/historial-lavadero.md`. |
| 5 | Fallo silencioso | `EquipoOtrosDAO.listar()` — `catch (SQLException e) { log.error(...) }` y devuelve lo leído | La pantalla muestra una lista incompleta como si fuera completa. |

Lo que **no** es un hallazgo: que las pantallas de consulta lean el histórico entero al abrirse. Es
una decisión de arquitectura deliberada (filtrado en memoria sobre un snapshot), está documentada, y
es lo que la Fase B vuelve tolerable sin romperla.

### Arquitectura que este plan usa como palanca

`RefrescadorPantallas<T>` (`app/ui/`) ya tiene todo lo que la Fase B necesita: lee en fondo vía
`TareaUI` y reparte en el EDT; **debounce** de 150 ms; y **cancelación de aplicación** (la query
vieja termina igual, pero su resultado se descarta y no se ejecutan `pintar`, `siFalla` ni `despues`).

**Dato verificado que evita sobre-ingeniería:** la fase 2 se lanza desde el `pintar` de la fase 1,
que corre en el EDT (`TareaUI.done` 148-166), y el `Timer` del debounce también dispara en el EDT
(`RefrescadorPantallas:73`). Por lo tanto **las dos fases de una misma cadena no pueden volver en
orden invertido** y `enVuelo` sigue siendo un campo mono-hilo. No hace falta ningún `AtomicReference`
ni token de generación adicional al que ya existe.

Los **cinco grupos** (`UiCoordinator.inicializar()` 58-75):

| Grupo | Cuándo | Snapshot | ¿Dos fases? |
|---|---|---|---|
| `operativo` | cada guardado | `DatosOperativos` (cola activa) | **No** — ya acotado |
| `historialEquipos` | al abrir `Ver Equipos` / `Estado de procesos` | `HistorialEquipos` | Sí, si mide bien |
| `historialLotes` | al abrir `Ver Lotes` | `HistorialLotes` | Sí, si mide bien |
| `historialCiclos` | al abrir `Ver Ciclos` | `List<CicloLavadero>` | Sí, si mide bien |
| `historialLavadero` | al abrir `Historial` | `List<IngresoHistorial>` | Sí, si mide bien |

⚠️ **Excepción al pipeline uniforme:** `VerEquiposController` **no extiende**
`AbstractFilterController`. Tiene sus propios `todosOrtopedia` / `todosOtros` / `cargado` (45-47) y su
`aplicarFiltros()` corta con `if (!cargado) return` (110). Además `PantallaVerEquipos` tiene **dos**
tablas (repintadas en 207 y 223). Los Pasos 8 y 9 tienen que tratarlo como caso especial.

### Archivos de referencia (leer antes de escribir código nuevo)

| Para | Leer |
|---|---|
| Trabajo en fondo, cancelación | `ui/common/TareaUI.java` (`lanzar` 118-130, `done` 148-166) |
| Refresco por grupos | `app/ui/RefrescadorPantallas.java` · `app/ui/UiCoordinator.java` (58-75, 240-352) |
| Lectores de snapshot | `app/ui/LectorHistorialEquipos.java` · `LectorHistorialLotes.java` · `LectorDatosOperativos.java` |
| Filtrado en memoria | `common/util/AbstractFilterController.java` · `common/util/FilterStrategy.java` |
| DAO con el N+1 a matar | `equipos/otros/dao/EquipoOtrosDAO.java` (`SQL_CABECERA` 46-52, `listar` 229-249, `cargarMateriales` 767-798) |
| DAO que ya resolvió el N+1 (la forma a copiar) | `equipos/ortopedias/dao/EquipoDAO.java` (`SQL_EQUIPOS_CON_MATERIALES` 46-64, `obtenerEquiposConJoin` 259-283) |
| I/O en el EDT a corregir | `equipos/controller/VerEquiposController.java` (`abrirDetalleOtros` 179-189) |
| Patrón correcto de detalle bajo demanda | `lavadero/controller/HistorialLavaderoController.java` (101-107) |
| Fan-out a partir | `lavadero/dao/HistorialLavaderoDAO.java` (`SQL_RESUMEN` 68-77, `SQL_BOLSAS` 80-81 — el patrón a imitar) |
| Migraciones (forma y numeración) | `db/migration/V20__salidas_lavadero_instancia.sql` |
| Tests de DAO con H2 | `src/test/java/com/example/AbstractDAOTest.java` |
| Tests del refrescador | `src/test/java/com/example/app/ui/RefrescadorPantallasTest.java` (5 tests) |
| Repintado de tabla | `PantallaVerEquipos` 207 **y** 223 · `PantallaVerCDEv2` 104 · `PantallaVerLotes` 179 · `PantallaVerCiclos` 122 · `PantallaHistorialLavadero` 169 |

### Comandos

```bash
mvn clean package                                  # target/aptium.jar
mvn test                                           # unitarios
mvn verify                                         # tests + cobertura JaCoCo
mvn test -Dtest=NombreDeClase
java -jar target/aptium.jar                        # smoke manual (SIN -Daptium.edt.strict=true)
```

---

## Grafo de dependencias

```
Pasos 1 + 1.5 (instrumentar TareaUI + sembrador + baseline)   ◄── BLOQUEANTES de todo
   │
   ├──► Paso 2 (N+1 + propagación + EDT) ──► Paso 3 (subconsultas de movimientos) ─┐
   ├──► Paso 4 (índices, V21) ───────────────────────────────────────────────────  ┤
   ├──► Paso 5 (fan-out de SQL_RESUMEN) ─────────────────────────────────────────  ┤
   │                                                                               │
   └──► Paso 7 (RefrescadorPantallas: AGREGA las factorías) ──┐  2,3,4,5 ──────────┴──► Paso 6 (medir)
                                                              │
                                                              ▼
                                            Paso 8 (alcance prioritario + migrar call sites)
                                                              │
                                                              ▼           ┌── Paso 9 puede empezar
                                                       Paso 10 (cierre) ◄─┘   apenas termine el 7
```

**Paralelismo real (corregido por la revisión):**

- Después del Paso 1: `{2 → 3}`, `{4}`, `{5}` y `{7}` son cuatro carriles que **no comparten un solo
  archivo** — siempre que el Paso 7 **sólo agregue** las factorías y no privatice el constructor.
- El Paso 9 depende del 7 (necesita `alCambiarCarga`) y **es paralelo al 8**: 8 toca `Lector*`,
  services/DAOs y `UiCoordinator`; 9 toca `ui/common/` y las vistas.
- El Paso 8 es el único que toca `UiCoordinator`, y por eso es el que cierra el constructor.

| Paso | Modelo | Archivos que toca (exclusivos salvo aviso) |
|---|---|---|
| 1 | Sonnet | `ui/common/TareaUI.java`, su test, este archivo |
| 2 | **Opus** | `equipos/otros/dao/EquipoOtrosDAO.java`, `equipos/controller/VerEquiposController.java`, tests |
| 3 | Sonnet | `equipos/otros/dao/EquipoOtrosDAO.java`, `equipos/ortopedias/dao/EquipoDAO.java`, tests |
| 4 | Sonnet | `db/migration/V21__*.sql` |
| 5 | Sonnet | `lavadero/dao/HistorialLavaderoDAO.java`, tests |
| 6 | Sonnet | este archivo |
| 7 | **Opus** | `app/ui/RefrescadorPantallas.java`, su test |
| 8 | **Opus** | `app/ui/Lector*.java`, `app/ui/UiCoordinator.java`, services/DAOs, tests |
| 9 | Sonnet | `ui/common/` (dos clases nuevas), las 5 pantallas de consulta, tests |
| 10 | Sonnet | `CLAUDE.md`, memoria, este archivo |

**Invariantes verificados después de CADA paso:**

- [ ] `mvn test` en verde
- [ ] Cero `new Thread()` / `SwingWorker` nuevos fuera de `TareaUI`
- [ ] Cero JDBC fuera de un DAO
- [ ] Cero SQL construida por concatenación de input del usuario (siempre `?`)
- [ ] Ningún DAO devuelve lista vacía o parcial ante un error

---

## FASE A — Borrar el costo

## Paso 1 — Instrumentar `TareaUI` y tomar el baseline

> **BLOQUEANTE.** Sin esto ningún paso posterior puede demostrar que sirvió.

### Contexto (autocontenido)

`TareaUI` es el único mecanismo de trabajo en fondo de la app. Su `doInBackground()` (136-145) ya
renombra el hilo con el nombre de la tarea y llama `leer.call()`. Toda lectura de toda pantalla pasa
por ahí, con un nombre propio (`"refresco-operativo"`, `"refresco-historial-equipos"`, …). Medir ahí
es gratis y cubre todo; no hace falta ningún arnés de prueba.

### Tareas

1. **`TareaUI.doInBackground()`** — cronometrar `leer.call()` con `System.nanoTime()` y loguear el
   tiempo junto al nombre de la tarea.
   - Nivel **INFO**: `log.info("Tarea '{}' leyó en {} ms", nombre, ms)`.
   - Medir **sólo `leer`**: es lo que ataca la Fase A. **Pero ver la tarea 5** — el pintado hay que
     descartarlo como sospechoso una vez, no ignorarlo.
   - El cronómetro va en el `finally`, junto al renombrado del hilo, para que también se registre una
     lectura que **falló** (un timeout de 30 s que explota es justamente lo que se quiere ver).
   - Javadoc: por qué la medición vive acá y no en cada DAO — es el único cuello por el que pasa
     todo, y así ninguna pantalla nueva se olvida de instrumentarse.

2. **Tests** — `TareaUITest`: la instrumentación no altera el valor devuelto ni el ruteo del error
   (esa es la garantía que importa; verificar el texto del log es frágil y no aporta).

3. **Tomar el baseline sobre la base real.** `mvn clean package && java -jar target/aptium.jar`,
   abrir cada pantalla de consulta y anotar los ms **en este archivo**.
   - ⚠️ **Tres corridas por pantalla, y se anota la mediana.** Una sola medición mezcla el
     calentamiento del JIT y el arranque del pool de conexiones con lo que se quiere medir, y ese
     ruido es del orden de la mejora esperada.
   - La primera apertura de la app siempre es la más lenta: descartarla.

   | Tarea | baseline (mediana de 3) | post Fase A | fase 1 | fase 2 | total Fase B |
   |---|---|---|---|---|---|
   | `refresco-operativo` | _(llenar)_ | | — | — | — |
   | `refresco-historial-equipos` | _(llenar)_ | | | | |
   | `refresco-historial-lotes` | _(llenar)_ | | | | |
   | `refresco-historial-ciclos` | _(llenar)_ | | | | |
   | `refresco-historial-lavadero` | _(llenar)_ | | | | |
   | `detalle-historial-lavadero` | _(llenar)_ | | — | — | — |

4. **Anotar el tamaño de las tablas** — es lo que hace interpretables los ms:
   ```sql
   SELECT 'equipos' t, COUNT(*) n FROM equipos
   UNION ALL SELECT 'equipo_otros',               COUNT(*) FROM equipo_otros
   UNION ALL SELECT 'material_movimientos',       COUNT(*) FROM material_movimientos
   UNION ALL SELECT 'otros_material_movimientos', COUNT(*) FROM otros_material_movimientos
   UNION ALL SELECT 'ingresos_lavadero',          COUNT(*) FROM ingresos_lavadero
   UNION ALL SELECT 'elementos_ciclo_lavadero',   COUNT(*) FROM elementos_ciclo_lavadero;
   ```
   ⚠️ **Si `equipo_otros` tiene menos de ~500 filas**, los ms del baseline son demasiado chicos para
   sostener conclusiones: anotarlo así y apoyarse en el análisis de complejidad (N+1 es N+1
   independientemente del reloj). Decírselo al usuario, no esconderlo detrás de un número lindo.

5. **⚠️ Descartar (o confirmar) el pintado como sospechoso — una sola vez, en este paso.**
   Todo el plan asume que el problema es el I/O. Puede no serlo: las cinco pantallas hacen
   `setRowCount(0)` + un `addRow` **por fila, en el EDT** (`PantallaVerEquipos` 207/223,
   `PantallaVerCDEv2` 104, `PantallaVerLotes` 179, `PantallaVerCiclos` 122,
   `PantallaHistorialLavadero` 169). Con miles de filas eso congela la UI y **ninguna optimización
   de SQL lo arregla**.
   - Cronometrar `pintar` igual que `leer` —una línea más en el mismo `TareaUI`— y anotar los dos
     números.
   - **Si el pintado supera el 30 % del total en alguna pantalla, hay que decírselo al usuario antes
     de seguir**: la Fase B **duplica** los pintados, así que en ese escenario empeoraría ese
     componente mientras mejora el otro, y el plan necesitaría un paso nuevo (pintado por lotes o
     un `TableModel` propio sobre la lista, sin `addRow` fila por fila).
   - Nota: hoy ninguna de esas tablas tiene `RowSorter`, así que **no** está el caso patológico de
     re-ordenar en cada `addRow`. Verificarlo sigue siendo barato.

### Verificación

```bash
mvn test -Dtest=TareaUITest
mvn test
mvn clean package && java -jar target/aptium.jar   # y leer el log
```

### Criterio de salida

- [ ] El log muestra un tiempo por cada tarea de fondo, con su nombre
- [ ] La tabla de baseline está llena con **medianas de 3 corridas**, y los `COUNT(*)` anotados
- [ ] El tiempo de **pintado** está medido y anotado; si supera el 30 % del total en alguna pantalla,
      está avisado al usuario antes de arrancar la Fase A
- [ ] Commit: `feat: TareaUI mide y loguea el tiempo de cada lectura de fondo`

---

## Paso 1.5 — Sembrador sintético sobre MySQL local

> **Agregado el 2026-09-02** tras una objeción del usuario: el plan asumía poder medir contra la base
> real y nunca decía cómo se consigue eso. **También es bloqueante**, junto con el Paso 1.

### Contexto (autocontenido)

Todo este plan promete mejoras de rendimiento, y no hay forma honesta de verificar una promesa así
sin datos con volumen. La máquina de desarrollo no tiene los datos de producción, y copiarlos no es
una opción liviana: `equipos.paciente` guarda **nombres de pacientes**.

**Tres preguntas distintas, tres instrumentos distintos.** Confundirlas fue el error del plan original:

| Pregunta | ¿Sirve H2? | Instrumento |
|---|---|---|
| ¿El N+1 existe y el arreglo lo mata? | **Sí** — es estructural (1 consulta vs N+1), no depende del motor | Test H2 que **cuenta consultas**, no milisegundos |
| ¿Cuánto tarda con volumen, y cuánto ganamos? | **No** | **Este paso**: MySQL local sembrado |
| ¿Cuánto tarda en la máquina real? | **No** | `app.log` — ver la nota al final |

⚠️ **H2 no sirve para las preguntas de plan de ejecución** (¿usa el índice? ¿el predicado "activo" es
más caro que la consulta completa?). H2 en `MODE=MySQL` imita la sintaxis, no el optimizador. Para el
Paso 4 y para la compuerta del Paso 8 hace falta MySQL 8 de verdad.

**Nota que ahorra un viaje a producción:** `logback.xml` ya tiene un `RollingFileAppender` a nivel
**INFO** que escribe `${LOG_DIR}/app.log` con rotación diaria, y la app se auto-actualiza desde
GitHub Releases. O sea: la instrumentación del Paso 1 **viaja sola** en el próximo release y los
tiempos reales se acumulan en la máquina de producción sin que nadie haga nada. Esa es la validación
final del plan; el sembrador es lo que permite trabajar **antes** de eso.

### Tareas

1. **MySQL 8 local, base dedicada.** Documentar el arranque en el propio archivo del sembrador:
   ```bash
   docker run -d --name aptium-perf -e MYSQL_ROOT_PASSWORD=perf \
       -e MYSQL_DATABASE=aptium_perf -p 3307:3306 mysql:8
   ```
   Puerto **3307** a propósito: que no colisione con un MySQL local que ya esté en uso.
   Flyway crea el esquema solo al conectar la app o el sembrador.

2. **`src/test/java/.../perf/SembradorRendimiento.java`** — clase de test, **no** parte de `mvn test`:
   - `@EnabledIfSystemProperty(named = "aptium.perf", matches = "true")`, de modo que `mvn test` y
     `mvn verify` **la saltean siempre**. Sólo corre con `mvn test -Dtest=SembradorRendimiento -Daptium.perf=true`.
   - **⚠️ Tres guardas de seguridad, obligatorias. Un sembrador que se equivoca de base destruye
     datos de producción:**
     1. abortar si el nombre de la base **no termina en `_perf`**;
     2. abortar si `DB_HOST` no es `localhost`/`127.0.0.1`;
     3. abortar si alguna tabla ya tiene filas que **no** fueron sembradas por él (marcar lo sembrado
        con un prefijo reconocible en los campos de texto, p. ej. `PERF-`).
     Las tres fallan con un mensaje explícito, no con un `assert` mudo.

3. **La forma de los datos va en constantes al tope de la clase**, para poder escalarla:
   ```java
   static final int FACTOR = Integer.getInteger("aptium.perf.factor", 1);   // 1x, 2x, 5x
   static final int EQUIPOS_OTROS       = 3_000 * FACTOR;
   static final int EQUIPOS_ORTOPEDIA   = 3_000 * FACTOR;
   static final int MATERIALES_POR_EQUIPO = 4;
   static final int MOVIMIENTOS_POR_MATERIAL = 3;
   static final int PCT_ACTIVOS         = 30;     // % que todavía no está entregado
   static final int INGRESOS_LAVADERO   = 2_000 * FACTOR;
   ```
   - **Los valores de arriba son una suposición documentada, no un dato.** Si el usuario puede correr
     una consulta de agregados en producción (sólo `COUNT(*)`, ningún dato personal sale), ajustarlos
     y **anotar de dónde salieron**. Si no, dejarlos como están y decir en el commit que son supuestos.
   - `PCT_ACTIVOS` es **el número que decide la compuerta del Paso 8**: si en producción los activos
     son la mayoría del total, la fase prioritaria no puede ganar por construcción.

4. **Insertar por lotes** (`addBatch`/`executeBatch`, autocommit apagado). Sembrar 3.000 equipos con
   12.000 materiales fila por fila tarda minutos y no es el punto del ejercicio.

5. **Correr el baseline sobre esta base**, no sobre la de desarrollo: apuntar la app a `aptium_perf`
   (variables de entorno `DB_HOST/DB_PORT/DB_NAME`) y llenar la tabla del Paso 1.
   - **A `FACTOR=1` y a `FACTOR=5`.** La segunda corrida es la que responde "¿cuándo vuelve a
     molestar?", que es información que ninguna otra parte del plan da.

6. **Test estructural aparte, ese sí en H2 y en `mvn test`**: contar las consultas que ejecuta
   `listar()`. Un `Connection` envuelto que incrementa un contador por `prepareStatement`, o el
   contador de HikariCP. Es lo que convierte "el N+1 murió" en un criterio **objetivo y permanente**,
   independiente del reloj y de la máquina.

### Verificación

```bash
mvn test                                                        # el sembrador NO corre
mvn test -Dtest=SembradorRendimiento -Daptium.perf=true         # siembra 1x
mvn test -Dtest=SembradorRendimiento -Daptium.perf=true -Daptium.perf.factor=5
```

### Criterio de salida

- [ ] `mvn test` normal **no** ejecuta el sembrador (verificado mirando la salida)
- [ ] Las tres guardas de seguridad existen y se probó que **abortan** (al menos la del nombre de base)
- [ ] La tabla del Paso 1 tiene baseline a `FACTOR=1` **y** a `FACTOR=5`
- [ ] Está escrito si la forma de los datos salió de producción o es un supuesto
- [ ] Commit: `test: sembrador sintético para medir rendimiento sobre MySQL local`

---

## Paso 2 — Matar el N+1 de `EquipoOtrosDAO` (y su llamador en el EDT)

> Depende del Paso 1. **Es el hallazgo #1: el más caro de todos.** Es también el paso con más
> superficie de rotura del plan: leer entero antes de escribir.

### Contexto (autocontenido)

`EquipoOtrosDAO.listar(filtro, descripcion, params)` (229-249) hace:

```java
while (rs.next()) {
    EquipoOtros eq = mapearEquipo(rs);
    cargarMateriales(conn, eq);      // ← una consulta MÁS, por cada equipo
    lista.add(eq);
}
```

Con N equipos son **N+1 consultas**, y `cargarMateriales` (767-798) lleva adentro un
`LEFT JOIN (SELECT material_id, MAX(fecha) … GROUP BY material_id)` que agrega la tabla **entera** de
movimientos.

`EquipoDAO` ya resolvió exactamente este problema —su comentario lo dice:
`// Query con materiales incluidos — resuelve el N+1 para listados masivos`—. Su
`SQL_EQUIPOS_CON_MATERIALES` (46-64) trae equipo+materiales en una consulta y `obtenerEquiposConJoin`
(259-283) las pliega con un `LinkedHashMap<Integer, Equipo>`. **Ese es el patrón a copiar.**

**Verificado que el patrón encaja:** `EquipoOtros.agregarMaterial` (56-58) es un `add` puro sin
efectos colaterales, y `calcularEstado()` (79-90) lee la lista al final; su rama "REMITO sin
materiales" corresponde exactamente al caso `mat_id IS NULL ⇒ el equipo entra igual` que
`obtenerEquiposConJoin` (273-276) ya implementa. No hay ninguna particularidad de `tipo_ingreso` que
rompa el plegado.

### Tareas

1. **`SQL_CABECERA_CON_MATERIALES`** — `SQL_CABECERA` más el `LEFT JOIN` a `equipo_otros_materiales`
   (alias `m`) y a `lotes`, con las columnas de material prefijadas (`mat_id`, `mat_descripcion`,
   `mat_cantidad`, `mat_estado`, `lote_id_negocio`, `ultimo_movimiento`), igual que `EquipoDAO`.
   - Si `SQL_CABECERA` queda sin usos, borrarla. No dejar constantes muertas.

2. **Reescribir `listar()`** con plegado por `LinkedHashMap<Integer, EquipoOtros>`:
   - una fila por (equipo × material); `mat_id` nulo ⇒ equipo sin materiales, se agrega igual;
   - **⚠️ `listar()` tiene CINCO llamadores, no cuatro.** Todos necesitan `m.id` al final del
     `ORDER BY` o el orden de los materiales dentro del equipo queda indefinido:

     | Línea | Método | `ORDER BY` hoy | Acción |
     |---|---|---|---|
     | 253 | `obtenerTodos()` | `eo.fecha_ingreso DESC, eo.id DESC` | agregar `, m.id` |
     | 263 | `obtenerActivos()` | `eo.fecha_ingreso DESC, eo.id DESC` | agregar `, m.id` |
     | 272 | `obtenerEquiposNuevos()` | `eo.id DESC` | agregar `, m.id` |
     | **278** | **`obtenerPorId()`** | **NINGUNO** (`"WHERE eo.id = ?"`) | **agregar `ORDER BY m.id` entero** |
     | 876 | `obtenerEntreFechas()` | `eo.fecha_ingreso, eo.id` | agregar `, m.id` |

     El de la línea 278 es el que un agente se saltea: buscando la cadena `ORDER BY` no aparece.

   - **✅ Alternativa recomendada, estrictamente mejor: NO poner `m.id` en el `ORDER BY`.**
     En vez de eso, ordenar los materiales **en memoria** después del plegado (listas de 3-10
     elementos: `list.sort(comparingInt(MaterialOtros::getId))`).
     Motivo: con `m.id` el `ORDER BY` queda **multi-tabla** (`eo.fecha_ingreso DESC, eo.id DESC, m.id`)
     y MySQL no puede cubrirlo con ningún índice ⇒ filesort del join entero, siempre. Sin él queda
     `eo.fecha_ingreso DESC, eo.id DESC`, de una sola tabla, y en InnoDB un índice secundario incluye
     la PK implícitamente: `idx_otros_fecha_ingreso` del **Paso 4** lo cubre **entero** y el filesort
     desaparece. El orden de los materiales sale igual de determinista.
     Si se toma esta alternativa, **aplicar lo mismo a `EquipoDAO`** (que hoy tiene `…, e.id DESC, em.id`
     en 332 y 344) y **actualizar la justificación del Paso 4**, que dice que los índices no pagan los
     listados: con este cambio sí los pagan.
   - `cargarMateriales(Connection, EquipoOtros)` queda **sin ningún uso** (verificado: sólo lo llamaba
     `listar`): borrarla.

3. **Arreglar el fallo silencioso** (hallazgo #5): el `catch (SQLException e)` que loguea y devuelve
   la lista a medias pasa a `throw new DatabaseException("Error al obtener " + descripcion, e)`.

4. **⚠️ Arreglar el llamador que rompe con eso — obligatorio, en este mismo paso.**
   `VerEquiposController.abrirDetalleOtros()` (179-189) hace:
   ```java
   EquipoOtros equipoConMateriales = equipoOtrosService.obtenerPorId(equipo.getId());
   if (equipoConMateriales == null) return;
   ```
   Es un `MouseListener`: corre **síncrono en el EDT**, sin `try/catch`, y hoy se apoya en que un
   fallo devuelve lista vacía → `null` → `return` silencioso. Con la tarea 3 sale un
   `DatabaseException` que **nadie atrapa**: traza en consola, cero aviso al usuario.
   - Pasarlo a `TareaUI`, calcado de `HistorialLavaderoController` (101-107): `.leer` hace el I/O,
     `.pintar` construye el `DetalleOtrosDialog`, `.siFalla` muestra el error.
   - Esto además **cierra una violación de EDT preexistente** que `plans/historial-lavadero.md` ya
     había documentado como "no copiar este patrón".
   - Revisar los **otros** llamadores de `obtenerTodos`/`obtenerActivos`/`obtenerPorId`: los que van
     por `TareaUI` ya rutean a `siFalla`. Cualquiera que quede sincrónico se anota en
     "Mutaciones aplicadas" **con su corrección**, no sólo con su nombre.

5. **Tests** (`EquipoOtrosDAOTest`, H2):
   - un equipo con 3 materiales se lee con sus 3 materiales, **en orden de id**;
   - un equipo **sin** materiales aparece en el listado (caso REMITO sin mover — hoy funciona);
   - dos equipos con materiales no se mezclan entre sí;
   - `obtenerPorId` devuelve los materiales **en orden de id** (el test del quinto llamador);
   - `obtenerActivos()` devuelve exactamente lo mismo que antes del cambio;
   - un fallo de SQL **propaga** `DatabaseException` en vez de lista parcial.

### Verificación

```bash
mvn test -Dtest=EquipoOtrosDAOTest
mvn test -Dtest=EquipoOtrosCorreccionServiceIntegrationTest
mvn test
mvn clean package && java -jar target/aptium.jar
```

Smoke: en `Ver Equipos`, doble clic sobre un equipo "otros" abre el detalle con sus materiales.

### Criterio de salida

- [ ] `listar()` ejecuta **una sola** consulta, sin importar cuántos equipos devuelva — probado con el
      **contador de consultas** del Paso 1.5, no a ojo
- [ ] Los **cinco** llamadores tienen `m.id` al final del `ORDER BY`, `obtenerPorId` incluido
- [ ] `listar()` propaga el error; no hay lista parcial
- [ ] `abrirDetalleOtros` corre por `TareaUI` y muestra un error visible si la lectura falla
- [ ] `refresco-historial-equipos` bajó respecto del baseline (anotar el número)
- [ ] Commit: `perf: EquipoOtrosDAO lee equipos y materiales en una sola consulta`

---

## Paso 3 — Sacar las subconsultas que agregan toda la tabla de movimientos

> Depende del Paso 2 (comparten `EquipoOtrosDAO`). **Hallazgo #2.**

### Contexto (autocontenido)

Las consultas de listado traen el último movimiento de cada material así:

```sql
LEFT JOIN (
  SELECT material_id, MAX(fecha) AS ultimo_movimiento
  FROM material_movimientos GROUP BY material_id
) mm ON em.id = mm.material_id
```

Esa tabla derivada **agrega la tabla entera de movimientos**, sin `WHERE`. Ambas tablas de
movimientos reciben una fila por cada cambio de estado y **nunca se podan**, así que ese costo crece
para siempre aunque el listado devuelva tres equipos.

Existen `idx_mov_material (material_id)` (`V1:87`) e `idx_otros_mov_material (material_id)` (`V2:53`).

### Tareas

1. En **`EquipoDAO.SQL_EQUIPOS_CON_MATERIALES`** y en la consulta equivalente de **`EquipoOtrosDAO`**
   (la del Paso 2), reemplazar la tabla derivada por una **subconsulta correlacionada** en el `SELECT`:
   ```sql
   (SELECT MAX(mm.fecha) FROM material_movimientos mm WHERE mm.material_id = em.id)
       AS ultimo_movimiento
   ```
   Con el índice, eso es un *index range scan* de una entrada por material en vez de una agregación
   completa por listado.
   - Quitar el `LEFT JOIN (…) mm` correspondiente.
   - Comentario corto en cada consulta explicando **por qué** (la derivada agregaba toda la tabla).
2. Aplicar lo mismo a la otra copia de `EquipoDAO` (~292).
3. **No** extraer una clase o constante compartida entre los dos DAOs: son tablas distintas. Lo único
   común es la forma, y eso lo garantiza el test, no una abstracción.

### Verificación

```bash
mvn test -Dtest=EquipoDAOTest
mvn test -Dtest=EquipoOtrosDAOTest
mvn test
```

### Criterio de salida

- [ ] No queda ningún `GROUP BY material_id` sin `WHERE` en los DAOs de equipos
- [ ] `ultimo_movimiento` da **exactamente** el mismo valor que antes (test explícito con un material
      con 3 movimientos en fechas distintas)
- [ ] Commit: `perf: el último movimiento sale por índice y no agregando la tabla entera`

---

## Paso 4 — Índices que faltan (migración `V21`)

> Independiente de los Pasos 2, 3 y 5. **Hallazgo #3.**

### Contexto (autocontenido) — leer la advertencia antes que la tabla

⚠️ **Corrección de la revisión adversarial: estos índices NO eliminan el filesort de los listados,
y el plan original decía lo contrario.** El `ORDER BY` real de los listados es
`e.fecha_ingreso DESC, e.id DESC, em.id` (`EquipoDAO` 332 y 344): mezcla direcciones y su última
clave es de **otra tabla**. Ningún índice de una sola tabla lo cubre; siempre va a haber filesort. Y
el Paso 2 agrega `m.id` del lado de `EquipoOtros`, con el mismo efecto.

**Lo que los índices sí pagan, que es real y vale la migración:**

| Índice | Consulta que acelera |
|---|---|
| `equipos.fecha_ingreso`, `equipo_otros.fecha_ingreso` | `obtenerEntreFechas(...)` — los reportes por rango |
| `equipos.estado`, `equipo_otros.estado` | `obtenerEquiposNuevos()` (`WHERE eo.estado = ?`) |
| `ingresos_lavadero.estado` | `CicloLavaderoDAO:80` (`WHERE il.estado = 'CLASIFICADO'`) y el alcance prioritario del Paso 8 |
| `ingresos_lavadero.fecha_ingreso` | el `ORDER BY` de `SQL_RESUMEN`, que **después del Paso 5 sí es de una sola tabla** |

Los `JOIN` por FK ya están cubiertos: InnoDB crea el índice al declarar la `FOREIGN KEY`.

**Regla dura: una migración ya escrita no se toca.** Esto va en `V21`. Verificado: V1–V20 presentes
sin huecos, y `ingresos_lavadero.estado` existe desde **`V10:1-3`**.

### Tareas

1. **`src/main/resources/db/migration/V21__indices_historial.sql`**:
   ```sql
   CREATE INDEX idx_equipos_fecha_ingreso      ON equipos            (fecha_ingreso);
   CREATE INDEX idx_equipos_estado             ON equipos            (estado);
   CREATE INDEX idx_otros_fecha_ingreso        ON equipo_otros       (fecha_ingreso);
   CREATE INDEX idx_otros_estado               ON equipo_otros       (estado);
   CREATE INDEX idx_ingresos_lav_fecha_ingreso ON ingresos_lavadero  (fecha_ingreso);
   CREATE INDEX idx_ingresos_lav_estado        ON ingresos_lavadero  (estado);
   ```
   - Encabezado comentado con **qué consulta paga cada índice** (la tabla de arriba), y una línea
     diciendo que **no** apuntan al `ORDER BY` de los listados y por qué.
   - Sintaxis idéntica a `V10`/`V17`/`V19`/`V20`, que ya corren en MySQL y en H2.

2. Correr la suite. ⚠️ **No es cierto que "falla todo" si `V21` está mal**: sólo fallan los tests que
   extienden `AbstractDAOTest`; los unitarios con Mockito no tocan Flyway. Y la BD H2 es compartida
   entre clases (`AbstractDAOTest:30`, `DB_CLOSE_DELAY=-1`), así que la migración corre en la primera
   clase de la corrida. Sigue siendo buena señal, pero mirar específicamente un `*DAOTest`.

### Verificación

```bash
mvn test -Dtest=EquipoDAOTest        # una clase que sí levanta el esquema
mvn test
mvn clean package && java -jar target/aptium.jar   # el log de Flyway debe mostrar V21
```

### Criterio de salida

- [ ] `mvn test` en verde, con al menos un `*DAOTest` corriendo (prueba que `V21` corre en H2)
- [ ] La app arranca y Flyway registra `V21` (prueba que corre en MySQL)
- [ ] No se modificó ninguna migración existente
- [ ] **No** se usa "el listado bajó" como evidencia de este paso: eso lo produjo el Paso 2
- [ ] Commit: `perf: índices de fecha y estado para consultas por rango y por estado (V21)`

---

## Paso 5 — Partir el fan-out de `SQL_RESUMEN`

> Independiente de los Pasos 2, 3 y 4. **Hallazgo #4** — y una de las dos pantallas que el usuario
> señaló como lentas.

### Contexto (autocontenido)

`HistorialLavaderoDAO.SQL_RESUMEN` (68-77) engancha `elementos_clasificacion_lavadero` →
`elementos_ciclo_lavadero` → `ciclos_lavadero` con `LEFT JOIN`. Eso multiplica las filas: un ingreso
con 6 líneas clasificadas lavadas en ~1,5 ciclos rinde ~9 filas que después se pliegan a **un**
`IngresoHistorial`.

Las dos columnas que justifican esos `JOIN` —`cel.nombre` y `cl.lavarropas_numero`— **no se muestran
en la tabla**: sólo llenan `Set<String> elementos` y `Set<Integer> lavarropas`, que resuelven en
memoria los filtros opcionales "Elemento:" y "Lavarropas #:".

`cantBolsas` **ya** está resuelto así (`SQL_BOLSAS` 80-81: `GROUP BY ingreso_id` limpio, cruzado en
memoria por id). Este paso le da el mismo trato a los otros dos agregados.

⚠️ **Corrección de la revisión: `HistorialLavaderoDAOTest` tiene 12 tests, pero sólo CUATRO tocan
`obtenerHistorial()`/`SQL_RESUMEN`** (líneas 87, 101, 113, 125). Los otros 8 son de `findDetalle`,
que este paso no toca. La red de seguridad real es de 4 tests, no de 12 — y le falta un caso.

### Tareas

1. **Agregar primero el test que falta**, antes de tocar el SQL: **ingreso clasificado pero sin
   ningún ciclo** ⇒ `elementos` no vacío y `lavarropas` vacío. Es el caso que la partición puede
   romper (los `INNER JOIN` de la consulta de lavarropas lo dejan afuera, que es lo correcto, pero
   hoy nadie lo verifica). Tiene que pasar **antes** y **después** del cambio.
2. **`SQL_RESUMEN` vuelve a ser una fila por ingreso**: sólo `ingresos_lavadero JOIN clientes`, con
   `ORDER BY il.fecha_ingreso DESC, il.id DESC`.
3. **`SQL_ELEMENTOS_POR_INGRESO`**:
   ```sql
   SELECT DISTINCT ecl.ingreso_id, cel.nombre
   FROM elementos_clasificacion_lavadero ecl
   JOIN catalogo_elementos_lavadero cel ON cel.id = ecl.elemento_id
   ```
4. **`SQL_LAVARROPAS_POR_INGRESO`**:
   ```sql
   SELECT DISTINCT ecl.ingreso_id, cl.lavarropas_numero
   FROM elementos_clasificacion_lavadero ecl
   JOIN elementos_ciclo_lavadero eci ON eci.elemento_clasificacion_id = ecl.id
   JOIN ciclos_lavadero cl           ON cl.id = eci.ciclo_id
   ```
5. Cruzar las tres en memoria por `ingreso_id`, igual que con las bolsas. **Sin repetir el cruce tres
   veces**: si queda copiado, extraerlo a un método privado.
6. **Actualizar el Javadoc de `SQL_RESUMEN`**, que hoy explica el plegado de filas repetidas: con el
   cambio deja de ser cierto. Reescribirlo, no borrarlo.

**Nota que evita un frenazo innecesario:** el cambio altera el **orden de inserción** de los
`LinkedHashSet` `elementos`/`lavarropas` (302-303). Es inofensivo y está verificado: los tests
comparan con `Set.of(...)` (119-120, independiente del orden), los filtros son campos de **texto
libre** (`PantallaHistorialLavadero` 53-54, no combos poblados del snapshot) y ninguna columna de la
tabla los muestra (169-181).

### Verificación

```bash
mvn test -Dtest=HistorialLavaderoDAOTest
mvn test
```

### Criterio de salida

- [ ] Existe el test de "clasificado sin ciclos" y pasaba **antes** del cambio de SQL
- [ ] `SQL_RESUMEN` devuelve exactamente una fila por ingreso
- [ ] Los 4 tests de `obtenerHistorial()` pasan **sin modificarse**
- [ ] El Javadoc de `SQL_RESUMEN` describe la consulta actual
- [ ] Commit: `perf: el resumen del historial de lavadero deja de multiplicar filas`

---

## Paso 6 — Volver a medir y dejar el número escrito

> Depende de los Pasos 2, 3, 4 y 5.

### Tareas

1. `mvn clean package && java -jar target/aptium.jar`, abrir las mismas pantallas que en el Paso 1,
   en el mismo orden, **tres corridas, anotando la mediana**, y llenar la columna "post Fase A".
2. **Umbral explícito:** se considera que un paso movió la aguja si la mediana bajó **≥ 30 %** o
   **≥ 300 ms**, lo que se alcance primero. Por debajo de eso, con tres corridas, no se distingue de
   ruido y **no se declara mejora**.
3. Si alguna pantalla no mejoró, anotarlo en "Mutaciones aplicadas" con la hipótesis. Un paso que no
   movió la aguja es información: saber cuál sigue cara cambia a qué grupo conviene darle prioridad
   en el Paso 8.
4. Sin cambios de código.

### Criterio de salida

- [ ] Las columnas baseline y post-Fase-A están llenas, con medianas de 3
- [ ] Cada mejora declarada supera el umbral; las que no, están anotadas como "sin cambio medible"
- [ ] Commit: `docs: números de rendimiento después de la Fase A`

---

## FASE B — Esconder el costo que quede

## Paso 7 — `RefrescadorPantallas` en dos fases

> Sólo depende del Paso 1. **Paralelo con toda la Fase A.** Es el corazón del plan.

### Contexto (autocontenido)

Leer `app/ui/RefrescadorPantallas.java` **entero** y `ui/common/TareaUI.java` al menos hasta
`lanzar()` (118-130) y `done()` (148-166).

Hoy `refrescarAhora()` cancela la ejecución en vuelo → lanza **una** `TareaUI` → reparte. La Fase B
lo convierte en una **cadena de dos lecturas**: primero el alcance prioritario, después el histórico
completo, que **pisa** al primero.

**Dos hechos verificados que acotan el diseño (no sobre-ingenierizar):**

- La fase 2 se lanza desde el `pintar` de la fase 1, que corre en el EDT; el `Timer` del debounce
  también dispara en el EDT. **Las dos fases de una cadena no pueden volver invertidas** y `enVuelo`
  sigue siendo mono-hilo. No hace falta `AtomicReference` ni un token de generación nuevo.
- `UiCoordinator` hace `new RefrescadorPantallas<>(...)` en **263, 281, 293, 301 y 312**. Por eso
  este paso **agrega** las factorías y **deja el constructor público**; lo cierra el Paso 8, que es
  el que migra esos cinco *call sites*. Privatizarlo acá rompería la compilación y volvería este
  paso no paralelizable.

### Tareas

1. **Dos factorías estáticas**, sin tocar el constructor existente:
   ```java
   public static <T> RefrescadorPantallas<T> enUnaFase(
       String nombre, Supplier<T> lector, Consumer<T> repartir, Consumer<Throwable> alFallar)

   public static <T> RefrescadorPantallas<T> enDosFases(
       String nombre, Supplier<T> lectorPrioritario, Supplier<T> lectorCompleto,
       Consumer<T> repartir, Consumer<Throwable> alFallar, Consumer<Boolean> alCambiarCarga)
   ```
   - `alCambiarCarga` recibe `true` al arrancar la fase 2 y `false` al terminar (bien o mal). Corre
     **en el EDT**. Es lo que alimenta el indicador del Paso 9.
   - Mantener el constructor *package-private* con `debounceMs` que ya usan los tests.

2. **La cadena.** `refrescarAhora()` en dos fases:
   - lanza la prioritaria; en su `pintar`: reparte el parcial, `alCambiarCarga(true)` y **lanza la
     completa**;
   - en el `pintar` de la completa: reparte y `alCambiarCarga(false)`;
   - si la completa falla: `alCambiarCarga(false)` **y** el error va **sólo al indicador**, no al
     `JOptionPane` modal (ver tarea 5).

3. **Cancelar la cadena como una unidad.** `enVuelo` deja de ser una `Ejecucion` suelta: una
   `solicitar()` nueva aborta **las dos** fases, incluida la segunda si todavía no se lanzó.
   - Clase privada anidada (p. ej. `Cadena`) con `cancelar()` idempotente y un `boolean cancelada`
     que la fase 1 consulta **antes** de lanzar la fase 2.
   - ⚠️ Evita el bug clásico: el usuario entra, sale y vuelve a entrar; la fase 1 vieja termina, no
     está cancelada "todavía", y dispara una fase 2 huérfana que después pisa el resultado nuevo.

4. **⚠️ Caso que la revisión encontró faltando: `solicitar()` durante la fase 2 hace *retroceder* la
   pantalla.** Hoy un refresco nunca muestra menos que antes; con la cadena, cancelar en fase 2 y
   relanzar desde fase 1 repinta sólo lo activo **y reaparece el cartel de carga** sobre datos que ya
   estaban completos. Y `componentShown` dispara `solicitar()` en **cada** `CardLayout.show`.
   - **Decisión:** si la cadena anterior ya llegó a repartir un snapshot **completo**, la cadena
     nueva **salta directo a la fase 2** (una sola lectura, sin cartel). La fase 1 sólo tiene sentido
     cuando no hay nada bueno en pantalla.
   - **Por qué es correcto, y no sólo cómodo:** al reentrar, el controller conserva su caché y la
     tabla **ya está mostrando el snapshot completo anterior**. No hay espera percibida que esconder:
     los datos viejos se ven al instante y se reemplazan cuando llega la lectura. Mostrar una fase 1
     ahí sería *sacar* filas de la pantalla para volver a agregarlas.
   - `boolean tuvoSnapshotCompleto`: campo del refrescador, se pone en `true` al primer reparto
     completo exitoso y **nunca se resetea**. Si la fase 2 falla, sigue en `false` y la próxima
     cadena vuelve a hacer las dos fases — que es lo que se quiere.
   - ⚠️ **Consecuencia que hay que tener presente y no esconder:** con esto, la Fase B mejora la
     **primera apertura de cada pantalla por sesión de app**, no el uso del día. Es exactamente el
     momento que el usuario reportó como molesto, así que vale — pero si después de la Fase A esa
     primera apertura ya baja a pocos cientos de ms, el beneficio restante es chico. Es la otra
     razón de ser de la compuerta por grupo del Paso 8.

5. **Fallo de la fase 1** ⇒ ir igual a la fase 2 (es la que tiene los datos de verdad) y rutear el
   error de la fase 1 sólo al log. Documentarlo en el Javadoc.

6. **Tests** (`RefrescadorPantallasTest` — los 5 existentes tienen que seguir pasando sin tocarse):
   - dos fases ⇒ `repartir` se llama **dos veces**, primero con el parcial y después con el completo;
   - `alCambiarCarga` recibe `true` y después `false`, exactamente una vez cada uno;
   - `solicitar()` durante la **fase 1** cancela la cadena: no se lanza la fase 2 vieja;
   - `solicitar()` **después** de un snapshot completo ⇒ una sola lectura, `alCambiarCarga` **nunca**
     se llama (el test de la tarea 4);
   - la fase 2 falla ⇒ `alCambiarCarga(false)` y el snapshot parcial **queda**;
   - la fase 1 falla ⇒ la fase 2 corre igual;
   - `enUnaFase` se comporta idénticamente a hoy.

### Verificación

```bash
mvn test -Dtest=RefrescadorPantallasTest
mvn test
```

### Criterio de salida

- [ ] El constructor público **sigue existiendo**: `UiCoordinator` compila sin tocarse
- [ ] Los 5 tests previos pasan sin modificarse
- [ ] Existen los dos tests de cancelación: durante fase 1, y "ya había snapshot completo"
- [ ] `alCambiarCarga(false)` se llama también cuando la fase 2 falla
- [ ] Ni un controller ni una vista fueron modificados
- [ ] Commit: `feat: RefrescadorPantallas puede leer en dos fases`

---

## Paso 8 — Alcance prioritario por grupo

> Depende del Paso 7. **Paralelo con el Paso 9.** Es el paso que cierra el constructor público.

### Contexto (autocontenido)

Cada grupo tiene un lector (`app/ui/Lector*.java`) que devuelve el snapshot completo. Este paso le
agrega la variante **prioritaria**: lo mismo, pero sólo lo que todavía está en juego.

⚠️ **Corrección de la revisión: sólo DOS de los cuatro predicados existen ya. Los otros dos hay que
escribirlos**, y el criterio de salida original ("ningún predicado nuevo") era incumplible:

| Grupo | Prioritario = | ¿Existe? |
|---|---|---|
| `historialEquipos` | equipos con algo sin entregar | **Sí** — `SQL_WHERE_ACTIVOS` en `EquipoDAO` (75-79) y `EquipoOtrosDAO` (60-65). **Reusar, no reescribir.** |
| `historialLavadero` | ingresos no `FINALIZADO` | **Sí** — es el filtro por defecto que ya aplica `PantallaHistorialLavadero` |
| `historialLotes` | lotes activos | **No.** `LoteDAO` tiene `obtenerLotesFinalizados()` (48), `obtenerTodosLosLotes()` (65) y `obtenerLotesEnRango()` (86). **Hay que escribir `obtenerLotesActivos()`.** |
| `historialCiclos` | ciclos no finalizados | **No.** `CicloLavaderoDAO.obtenerCiclosActivosPorLavarropas()` (102) devuelve `Map<Integer, CicloLavadero>` — otro tipo y otra semántica (uno por lavarropas, no todos). **No forzarlo**: escribir un método nuevo que devuelva `List<CicloLavadero>`. |

### Tareas

1. **Escribir los dos predicados que faltan**, cada uno **una sola vez, en su DAO**, con nombre
   propio (`obtenerLotesActivos()`, `obtenerCiclosNoFinalizados()`). Respetar las capas: el service
   valida y delega, cero JDBC; un DAO que falla propaga.
2. **Reusar** los dos que existen. Una segunda definición del mismo concepto de negocio se
   desincroniza sola.
3. **`LectorHistorialEquipos`** y los demás — un `enum Alcance { PRIORITARIO, COMPLETO }` como
   parámetro, **no** una segunda clase copiada: una clase por alcance duplicaría el cableado de los
   services. Que las cuatro queden con la misma forma.
4. **`UiCoordinator`** — migrar los cinco *call sites* a las factorías: `operativo` a `enUnaFase`,
   los grupos de consulta a `enDosFases`. Recién ahora **privatizar el constructor** de
   `RefrescadorPantallas` (lo dejó abierto el Paso 7).
   - **Actualizar el comentario de los cinco grupos** (62-70): cuáles van en dos fases y por qué
     `operativo` no.
5. **Caso especial `VerEquiposController`**: no extiende `AbstractFilterController`, tiene su propio
   `cargado` (47) y `aplicarFiltros()` corta con `if (!cargado) return` (110). Verificar que con dos
   fases `cargado` pase a `true` en la fase 1 y que el segundo `pintar` no rompa nada.
6. **⚠️ Compuerta por grupo (ver el matiz de la sección de decisiones).** Medir fase 1 y fase 2 de
   cada grupo con el log del Paso 1 y llenar las tres columnas nuevas de la tabla.
   - Un grupo se queda cableado en dos fases **sólo si la fase 1 es ≥ 30 % más rápida que la
     completa**. Si no, ese grupo vuelve a `enUnaFase` y se anota en "Mutaciones aplicadas".
   - Sospechoso principal: `historialEquipos`. `SQL_WHERE_ACTIVOS` es un `OR` de `EXISTS`/`NOT EXISTS`
     correlacionados que MySQL no puede resolver por índice — es *full scan* más una subconsulta por
     fila. Muerto el N+1, la "prioritaria" puede salir **más cara** que la completa.
7. **Tests**: por cada lector, el alcance prioritario devuelve un subconjunto del completo, y un
   registro terminado (entregado / `FINALIZADO` / lote finalizado) **está** en el completo y **no**
   en el prioritario.

### Verificación

```bash
mvn test
mvn clean package && java -jar target/aptium.jar
```

Smoke (**sin** `-Daptium.edt.strict=true`): abrir cada pantalla de consulta y ver en el log **dos**
lecturas por apertura, con sus tiempos. Volver a entrar sin cerrar la app ⇒ **una sola** lectura
(la optimización de la tarea 4 del Paso 7).

### Criterio de salida

- [ ] Los dos predicados nuevos existen una sola vez, en su DAO, con test de "prioritario ⊂ completo"
- [ ] Los dos que ya existían se reusaron sin reescribirse
- [ ] El constructor de `RefrescadorPantallas` es privado y todo compila
- [ ] Las columnas `fase 1` / `fase 2` / `total` de la tabla están llenas para los cuatro grupos
- [ ] Todo grupo cableado en dos fases supera el umbral del 30 %; los que no, están en "Mutaciones"
- [ ] El comentario de los grupos en `UiCoordinator` describe la situación actual
- [ ] Commit: `feat: las pantallas de consulta leen primero lo que sigue en juego`

---

## Paso 9 — Preservar selección + indicador de carga parcial

> Depende del Paso 7 (necesita `alCambiarCarga`). **Paralelo con el Paso 8.**

### Contexto (autocontenido)

Con dos fases cada pantalla se repinta **dos veces** por apertura. Sin cuidado eso produce dos
defectos, y el segundo es de corrección:

1. La fila seleccionada salta o se pierde cuando llega la fase 2.
2. **Una lista parcial se lee como completa.** El usuario busca un equipo entregado, no lo ve y
   concluye que no existe. Es lo más importante del paso.

Las pantallas repintan con el mismo patrón (`setRowCount(0)` + `addRow`): `PantallaVerEquipos`
**207 y 223** (⚠️ **dos** tablas), `PantallaVerCDEv2` 104, `PantallaVerLotes` 179, `PantallaVerCiclos`
122, `PantallaHistorialLavadero` 169. **Cinco pantallas, seis tablas.**

⚠️ **Las tablas NO son ordenables hoy** — verificado: `setAutoCreateRowSorter`/`setRowSorter` sólo
aparece en `PanelGestionClientes`. Los `convertRowIndexToModel` de `VerEquiposController` 172/182 y
`HistorialLavaderoController:97` son defensivos y hoy devuelven el mismo índice. El helper **igual**
hace la conversión en los dos extremos: es gratis, es correcta, y es lo único que evita un bug latente
el día que alguien agregue un sorter. Lo que **no** hay que hacer es escribir tests que instalen un
`RowSorter` para "probarlo": se testea el método puro (id → índice de modelo), que es donde está la
lógica.

> **Regla de este paso: cero copiar y pegar.** Si el mismo bloque aparece en dos pantallas, va a
> `ui/common/`. Seis copias de "guardar la selección" es exactamente lo que este plan no quiere.

### Tareas

1. **`ui/common/SeleccionPreservada.java`** — contrato **explícito** sobre vista vs. modelo:
   ```java
   /**
    * @param tabla      la tabla a preservar; puede tener RowSorter activo
    * @param idDeFila   dado un índice de MODELO, el id de esa fila
    * @param repintado  repuebla el modelo
    */
   public static void alrededorDe(JTable tabla, IntUnaryOperator idDeFila, Runnable repintado)
   ```
   - antes: `getSelectedRow()` (vista) → `convertRowIndexToModel` → `idDeFila` → guarda el **id**;
   - corre `repintado`;
   - después: busca el id en el modelo nuevo → `convertRowIndexToView` → `setRowSelectionInterval`.
     Si el id ya no está, no selecciona nada.
   - **En vez de restaurar el scroll, `scrollRectToVisible` de la fila re-seleccionada.** Restaurar
     la posición es incorrecto cuando la lista crece de N a 5N: el mismo píxel es otra fila.
   - La lógica de verdad —dado un id y la lista nueva, qué índice de modelo corresponde— va en un
     **método estático puro** y **ese** es el que se testea sin Swing, como manda el repo.

2. **`ui/common/IndicadorCargaParcial.java`** — `JLabel` o panel chico con
   *"Cargando el resto del histórico…"* y un estado de error
   *"No se pudo cargar el histórico completo"*.
   - `setCargando(boolean)` / `setError(...)`, invocables desde el EDT.
   - Ubicación: donde `PantallaHistorialLavadero` ya pone su `lblHint` (`BorderLayout.SOUTH`).
     ⚠️ **Verificar que `SOUTH` esté libre en las otras cuatro pantallas**; si alguna lo usa, meterlo
     en el mismo panel que lo ocupa en vez de pelear por la posición.

3. Cablear en las cinco pantallas (**seis tablas**: `PantallaVerEquipos` necesita las dos):
   - cada `actualizarX(lista)` envuelve su repintado con `SeleccionPreservada.alrededorDe(...)`;
   - el `Consumer<Boolean>` del Paso 8 termina en `indicador.setCargando(...)`.
   - Ni la pantalla ni el indicador conocen un service o un DAO.

4. **Tests**:
   - `SeleccionPreservadaTest` — el método puro: id presente ⇒ su índice; id ausente ⇒ "sin
     selección"; lista vacía ⇒ "sin selección"; **el id que estaba en la fila 0 y ahora está en la 7
     ⇒ devuelve 7** (el test que justifica todo el paso).
   - `IndicadorCargaParcialTest` — los tres estados (oculto / cargando / error), como el repo ya
     testea componentes chicos (`PanelMaterialesTest`, `ImprimirEquiposDialogTest`).

### Verificación

```bash
mvn test -Dtest=SeleccionPreservadaTest
mvn test
mvn clean package && java -jar target/aptium.jar
```

Smoke manual (**sin** `-Daptium.edt.strict=true`):

1. Abrir `Ver Equipos`: aparecen primero los activos y el cartel; después llega el resto y se va.
2. Seleccionar una fila en la lista parcial → al llegar la fase 2 **sigue seleccionada la misma
   fila**, aunque cambió de posición. Probar en **las dos** tablas de esa pantalla.
3. `Historial de Lavadero`: doble clic durante la fase 1 abre el detalle sin romper nada.
4. Entrar, salir y volver a entrar → la segunda vez la tabla muestra los datos completos al instante
   y **no** aparece el cartel (es la optimización de la tarea 4 del Paso 7).
5. Log: **ningún WARN de `EdtGuard`** atribuible a estas pantallas.

### Criterio de salida

- [ ] La selección sobrevive al segundo pintado, con la fila cambiada de posición
- [ ] El indicador aparece durante la fase 2 y desaparece al terminar, **también si falla**
- [ ] Las seis tablas usan **la misma** clase compartida; cero copias del bloque
- [ ] Commit: `feat: la carga en dos fases no pierde la selección ni miente sobre lo que falta`

---

## Paso 10 — Cobertura, documentación y cierre

### Tareas

1. `mvn verify` y JaCoCo. **≥ 80 %** en `SeleccionPreservada` (el método puro), `RefrescadorPantallas`
   e `IndicadorCargaParcial`. Las pantallas Swing quedan sin cubrir — convención del repo.
2. **`CLAUDE.md`**:
   - En "Patrones e interfaces clave": los grupos de consulta leen en **dos fases** (prioritario y
     completo), la segunda pisa a la primera, y eso vive **sólo** en `RefrescadorPantallas`; una
     cadena nueva sobre un snapshot ya completo salta directo a la fase 2.
   - Nota de rendimiento: los listados de equipos traen materiales en **una** consulta (no N+1) y el
     último movimiento sale por índice, no agregando la tabla entera.
   - `TareaUI` loguea el tiempo de cada lectura — es la herramienta para la próxima vez.
   - Si algún grupo quedó en una fase, decir **cuál y por qué**.
3. **Memoria** — `project-rendimiento-historiales.md` (`type: project`): los cinco hallazgos, los
   números de antes y después, la decisión de que la fase 2 relee todo, y la compuerta por grupo.
   Enlazar `[[project-architecture]]` y `[[project-historial-lavadero]]`. Línea en `MEMORY.md`.
4. Marcar el plan como **✅ CERRADO** con los SHAs de cada paso.
5. Preguntar al usuario si `RendimientoHistoriales` se mergea a `RetoquesFinalesL` o va directo.

### Criterio de salida

- [ ] `mvn verify` en verde y cobertura ≥ 80 % en las clases planas nuevas
- [ ] `CLAUDE.md` describe la lectura en dos fases y cualquier grupo que haya quedado en una
- [ ] Memoria e índice actualizados
- [ ] Commit: `docs: rendimiento de las pantallas de consulta`

---

## Catálogo de anti-patrones para este plan

| Anti-patrón | Por qué está mal acá |
|---|---|
| Empezar por la Fase B | Se construiría la maquinaria sobre un N+1 patológico: la fase 2 tardaría lo mismo que hoy y el tirón de cada guardado no se arreglaría. |
| Saltear el Paso 1 o el 1.5 | Sin baseline con volumen no hay forma de saber si un paso sirvió. Son los dos pasos bloqueantes. |
| Medir una sola vez | El ruido de JIT y del pool es del orden de la mejora esperada. Tres corridas, mediana. |
| Medir con H2 si la pregunta es de índices o de plan de ejecución | H2 en `MODE=MySQL` imita la sintaxis, **no el optimizador**. Para el Paso 4 y la compuerta del Paso 8 hace falta MySQL 8 real. H2 **sí** sirve para contar consultas (el N+1). |
| Apuntar el sembrador a cualquier base | Un sembrador equivocado de base destruye producción. Tres guardas: nombre terminado en `_perf`, host local, y tablas sin datos ajenos. |
| Dejar el sembrador corriendo en `mvn test` | Insertaría miles de filas en cada build. Va detrás de `@EnabledIfSystemProperty`. |
| Presentar la forma de los datos sembrados como si fuera un dato de producción | Son un supuesto hasta que alguien corra los `COUNT(*)` reales. Decirlo en el commit. |
| Buscar los llamadores de `listar()` por la cadena `ORDER BY` | `obtenerPorId` (278) **no tiene** `ORDER BY`. Son cinco llamadores, no cuatro. |
| Propagar la excepción de `listar()` sin tocar `abrirDetalleOtros` | Cambia un fallo silencioso por uno **invisible**: corre en el EDT sin `try/catch` (`VerEquiposController` 179-189). |
| Privatizar el constructor de `RefrescadorPantallas` en el Paso 7 | `UiCoordinator` lo usa en 263, 281, 293, 301 y 312: rompe la compilación y arruina el paralelismo. Lo cierra el Paso 8. |
| Dar por hecho que los cuatro predicados "activo" ya existen | Sólo existen los de equipos y lavadero. Lotes y ciclos hay que escribirlos. |
| Forzar `obtenerCiclosActivosPorLavarropas()` como alcance prioritario | Devuelve `Map<Integer, CicloLavadero>` — uno por lavarropas, no todos los activos. Entregaría un snapshot con menos ciclos de los que hay. |
| Que la fase 2 traiga sólo el complemento | Rompe "un snapshot reemplaza al anterior" y empuja lógica de merge a **cada** controller. |
| Preservar la selección por índice de fila | Entre fases las filas se corren. Va por id, con conversión vista↔modelo explícita (hoy es la identidad —no hay `RowSorter`— pero deja de serlo si alguien agrega uno). |
| Asumir que el problema es sólo el I/O | El pintado corre en el EDT, fila por fila. Hay que descartarlo con una medición en el Paso 1 antes de dar por buena toda la premisa del plan. |
| Poner `m.id` / `em.id` en el `ORDER BY` "porque es lo que hay hoy" | Vuelve el `ORDER BY` multi-tabla y garantiza filesort para siempre. Ordenar los materiales en memoria deja que el índice del Paso 4 cubra el listado entero. |
| Prometer que la Fase B mejora todas las aperturas | Mejora la **primera de cada pantalla por sesión**. En las siguientes la tabla ya muestra datos completos y no hay nada que esconder. |
| Restaurar la posición del scroll | Cuando la lista crece de N a 5N, el mismo píxel es otra fila. Va `scrollRectToVisible` de la fila re-seleccionada. |
| No mostrar que faltan datos | Una lista parcial se lee como completa. Es corrección, no estética. |
| Dejar el indicador colgado si la fase 2 falla | `alCambiarCarga(false)` va también en el camino de error. |
| Abrir el `JOptionPane` modal cuando falla la fase 2 | Taparía datos parciales usables, y con dos lecturas por apertura se duplica la chance. El error de la fase 2 va sólo al indicador. |
| No cancelar la fase 2 cuando llega un pedido nuevo | El caso entrar-salir-entrar deja una fase 2 huérfana que pisa el resultado nuevo. Tiene test propio. |
| Relanzar desde la fase 1 cuando ya había snapshot completo | La pantalla **retrocede** y reaparece el cartel sobre datos completos. `componentShown` dispara en cada `CardLayout.show`. |
| Copiar el bloque de preservar selección en cada pantalla | Son **seis tablas**. Va a `ui/common/`. |
| Tratar `VerEquiposController` como los demás | No extiende `AbstractFilterController`, tiene `cargado` propio y **dos** tablas. |
| Extraer una clase común entre `EquipoDAO` y `EquipoOtrosDAO` | Tablas, columnas y modelos distintos; sólo comparten la forma. Es la lección de `IMaterialFilter`/`ICapacidadCalculator`. |
| Decir que los índices del Paso 4 arreglan el `ORDER BY` de los listados | Es multi-tabla (`e.fecha_ingreso DESC, e.id DESC, em.id`): siempre filesort. Pagan otras consultas. |
| Usar "el listado bajó" como evidencia del Paso 4 | Esa mejora la produjo el Paso 2. |
| Creer que un error en `V21` hace fallar toda la suite | Sólo fallan los tests que extienden `AbstractDAOTest`. |
| Modificar una migración existente | Regla dura. Van en `V21`. |
| Usar `GROUP_CONCAT` / `STRING_AGG` | Se comportan distinto entre H2 (tests) y MySQL (producción). |
| Tocar un controller o una vista en el Paso 7 | La Fase B es barata **porque** aguas abajo nada cambia. |
| Sobre-ingenierizar la concurrencia del Paso 7 | Las dos fases no pueden volver invertidas: ambas se encadenan en el EDT. No hace falta token de generación nuevo. |

---

## Plan de sesiones

Diez pasos, **seis sesiones**.

| Sesión | Pasos | Modelo | Effort | Fast mode | Por qué |
|---|---|---|---|---|---|
| 1 | Pasos 1 + 1.5 | Sonnet 5 | medio | ➖ | Instrumentación chica, pero incluye levantar el MySQL local, escribir el sembrador con sus guardas y tomar el baseline a 1x y 5x. |
| 2 | Pasos 2 + 3 | **Opus 5** | **alto** | ❌ | El N+1, los cinco llamadores y el arreglo del EDT. Es el paso con más superficie de rotura. |
| 3 | Pasos 4 + 5 | Sonnet 5 | medio | ✅ | Migración mecánica + una partición ya escrita en el plan, con test previo. |
| 4 | Pasos 6 + 7 | **Opus 5** | **alto** | ❌ | El Paso 7 es diseño de concurrencia: cadena, cancelación, retroceso. Ahí se gana o se pierde la Fase B. |
| 5 | Pasos 8 + 9 | Sonnet 5 | medio | ➖ | Cableado y UI con el diseño decidido; el smoke manual es largo. |
| 6 | Paso 10 | Sonnet 5 | medio | ➖ | Cobertura, docs y cierre. |

**Paralelizar (opcional):** el Paso 7 no comparte archivos con los Pasos 2-6, así que la Sesión 4
podría arrancar en un `git worktree` en paralelo con las Sesiones 2-3. Para una sola persona,
secuencial suele salir mejor.

---

### Sesión 1 — Pasos 1 y 1.5: instrumentar, sembrar y medir

**Sonnet 5 · effort medio**

```
Ejecutá los Pasos 1 y 1.5 de plans/rendimiento-historiales.md, en ese orden (medir el tiempo
de cada lectura de fondo en TareaUI; después el sembrador sintético sobre MySQL local y el
baseline).

Leé antes del plan: "Contexto compartido" y "Catálogo de anti-patrones".

Son los dos pasos bloqueantes: sin baseline con volumen, ningún paso posterior puede demostrar
que sirvió. No sigas de largo a optimizar nada.

Cuatro cosas que importan:
1. En el Paso 1, medí también el PINTADO, no sólo la lectura. Corre en el EDT fila por fila y
   ninguna optimización de SQL lo arregla; si pasa del 30% del total, avisá antes de seguir.
2. El sembrador NO puede correr en mvn test (va detrás de @EnabledIfSystemProperty) y necesita
   TRES guardas de seguridad: nombre de base terminado en _perf, host local, y ninguna tabla
   con datos ajenos. Un sembrador equivocado de base destruye producción.
3. Tomá el baseline a FACTOR=1 y a FACTOR=5. La corrida de 5x es la que responde "cuándo
   vuelve a molestar", y ninguna otra parte del plan da ese dato.
4. Escribí también el test que CUENTA CONSULTAS (ese sí en H2 y en mvn test): es lo que
   convierte "el N+1 murió" en un criterio objetivo, independiente del reloj.

Los valores de forma de los datos son un SUPUESTO documentado, no un dato de producción.
Decílo así en el commit; si el usuario te pasa los COUNT(*) reales, ajustalos y anotá el origen.

Corré la app SIN -Daptium.edt.strict=true. Un commit por paso.
```

### Sesión 2 — Pasos 2 y 3: el N+1, sus llamadores y las subconsultas

**Opus 5 · effort alto · sin fast mode**

```
Ejecutá los Pasos 2 y 3 de plans/rendimiento-historiales.md, en ese orden (matar el N+1 de
EquipoOtrosDAO, propagar sus errores y arreglar su llamador del EDT; después sacar las
subconsultas que agregan toda la tabla de movimientos).

Leé antes del plan: "Contexto compartido", "Diagnóstico medido" y "Catálogo de anti-patrones".
El archivo a calcar es EquipoDAO: ya resolvió este N+1 (SQL_EQUIPOS_CON_MATERIALES +
obtenerEquiposConJoin). No inventes otro patrón.

Cuatro cosas que son la razón de que esta sesión exista:
1. listar() tiene que quedar en UNA consulta, sin importar cuántos equipos devuelva;
2. listar() tiene CINCO llamadores y todos necesitan m.id al final del ORDER BY. El quinto
   (obtenerPorId, línea 278) NO tiene ORDER BY: si lo buscás por esa cadena no aparece. La
   tabla del Paso 2 los lista por línea;
3. el catch que se traga el SQLException se arregla acá, Y en el mismo paso hay que pasar
   VerEquiposController.abrirDetalleOtros (179-189) a TareaUI: hoy llama obtenerPorId
   sincrónico en el EDT sin try/catch y se apoya en que un error devuelve null. Propagar sin
   eso cambia un fallo silencioso por uno invisible;
4. NO extraigas una clase compartida entre EquipoDAO y EquipoOtrosDAO: son tablas distintas y
   sólo comparten la forma.

Terminá con mvn test en verde y un commit por paso.
```

### Sesión 3 — Pasos 4 y 5: índices y fan-out

**Sonnet 5 · effort medio · fast mode ok**

```
Ejecutá los Pasos 4 y 5 de plans/rendimiento-historiales.md (migración V21 con los índices de
fecha y estado; después partir el fan-out de HistorialLavaderoDAO.SQL_RESUMEN).

Leé antes del plan: "Contexto compartido" y "Catálogo de anti-patrones".

Ojo con dos cosas que el plan corrige respecto de lo que parece obvio:
- Los índices del Paso 4 NO arreglan el ORDER BY de los listados (es multi-tabla, siempre
  filesort). Pagan otras consultas, que están listadas en el paso. No uses "el listado bajó"
  como evidencia: esa mejora la produjo el Paso 2.
- En el Paso 5, HistorialLavaderoDAOTest tiene 12 tests pero sólo CUATRO tocan SQL_RESUMEN.
  Antes de tocar el SQL tenés que AGREGAR el test que falta: ingreso clasificado pero sin
  ningún ciclo (elementos no vacío, lavarropas vacío). Tiene que pasar antes y después.

V21 es una migración NUEVA: no toques ninguna existente. Imitá SQL_BOLSAS, que ya resuelve el
mismo problema para las bolsas.

Un commit por paso, con los mensajes de los criterios de salida.
```

### Sesión 4 — Pasos 6 y 7: medir de nuevo y la cadena de dos fases

**Opus 5 · effort alto · sin fast mode**

```
Ejecutá los Pasos 6 y 7 de plans/rendimiento-historiales.md (llenar la columna "post Fase A";
después RefrescadorPantallas en dos fases).

Leé antes del plan: "Contexto compartido", las "Decisiones de diseño tomadas por el plan" y el
"Catálogo de anti-patrones". Leé RefrescadorPantallas.java entero y TareaUI.java al menos
hasta lanzar() y done().

El Paso 7 es diseño de concurrencia y es donde se gana o se pierde la Fase B:
- AGREGÁ las factorías enUnaFase/enDosFases pero DEJÁ el constructor público: UiCoordinator lo
  usa en cinco lugares y lo migra el Paso 8. Privatizarlo acá rompe la compilación;
- la fase 2 relee TODO y pisa a la fase 1; no traigas sólo el complemento;
- la cadena se cancela como unidad: una solicitud durante la fase 1 no puede dejar una fase 2
  huérfana que después pise el resultado nuevo;
- si ya hubo un snapshot COMPLETO, una cadena nueva salta directo a la fase 2. Sin esto la
  pantalla retrocede y reaparece el cartel sobre datos completos, y componentShown dispara en
  cada CardLayout.show;
- alCambiarCarga(false) va también en el camino de error, y el error de la fase 2 va SÓLO al
  indicador, no al JOptionPane modal;
- si la fase 1 falla, la fase 2 corre igual.

No sobre-ingenierices: las dos fases se encadenan en el EDT, así que no pueden volver
invertidas y no hace falta ningún token de generación nuevo.

NO toques ningún controller ni ninguna vista en el Paso 7.

Terminá con un commit por paso.
```

### Sesión 5 — Pasos 8 y 9: alcance prioritario y UI

**Sonnet 5 · effort medio**

```
Ejecutá los Pasos 8 y 9 de plans/rendimiento-historiales.md (alcance prioritario por grupo;
después preservar selección y el indicador de carga parcial).

Leé antes del plan: "Contexto compartido" y "Catálogo de anti-patrones". Los Pasos 1 a 7 ya
están commiteados.

Cuidado con cinco cosas:
- Sólo DOS de los cuatro predicados "activo" existen (equipos: SQL_WHERE_ACTIVOS; lavadero:
  estado <> FINALIZADO). Los de lotes y ciclos hay que escribirlos. NO fuerces
  obtenerCiclosActivosPorLavarropas(): devuelve un Map de uno por lavarropas, no todos.
- El Paso 8 es el que migra los cinco call sites de UiCoordinator y recién ahí privatiza el
  constructor de RefrescadorPantallas.
- Compuerta por grupo: un grupo queda en dos fases sólo si la fase 1 es >= 30% más rápida que
  la completa. El sospechoso es historialEquipos, porque SQL_WHERE_ACTIVOS es un OR de EXISTS
  que MySQL no indexa. Lo que quede en una fase se anota en "Mutaciones aplicadas".
- VerEquiposController es caso especial: no extiende AbstractFilterController, tiene su propio
  flag "cargado" y su pantalla tiene DOS tablas. Son cinco pantallas pero SEIS tablas.
- CERO copiar y pegar: preservar selección e indicador van a ui/common/ y las seis tablas usan
  la misma clase. La selección va por ID y con conversión explícita vista<->modelo, porque las
  tablas tienen RowSorter activo.

Cerrá con el smoke manual de 6 puntos del Paso 9, SIN -Daptium.edt.strict=true.

Un commit por paso.
```

### Sesión 6 — Paso 10: cobertura, docs y cierre

**Sonnet 5 · effort medio**

```
Cerrá plans/rendimiento-historiales.md.

1. /code-review high sobre el diff completo de la rama RendimientoHistoriales contra su punto
   de partida. Aplicá lo CRITICAL y lo HIGH; anotá lo MEDIUM que decidas no tocar.
2. Ejecutá el Paso 10: mvn verify y revisá JaCoCo (SeleccionPreservada, RefrescadorPantallas e
   IndicadorCargaParcial en 80%+). Las clases Swing quedan sin cubrir, es la convención del repo.
3. Actualizá CLAUDE.md y la memoria del proyecto, incluyendo los números de antes y después y
   cualquier grupo que haya quedado en una sola fase.
4. Marcá el plan como CERRADO arriba de todo, con los SHAs de cada paso.
5. Preguntá al usuario si esta rama se mergea a RetoquesFinalesL o va directo.

Terminá con el commit del criterio de salida.
```

---

## Protocolo de mutación del plan

- **Dividir un paso** → agregarlo como `Paso N.5` con su propio contexto y criterio de salida.
- **Saltear un paso** → dejar escrito *por qué* en "Mutaciones aplicadas"; no borrarlo.
- **Cambiar una decisión de la tabla de arriba** → tacharla (`~~...~~`) y escribir la nueva con
  fecha. Las decisiones tomadas con el usuario **no se cambian sin preguntarle**.

---

## Mutaciones aplicadas

_(vacío al crear el plan — la revisión adversarial se aplicó antes del primer commit)_
