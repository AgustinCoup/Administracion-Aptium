# Plan — Conexiones a la base y paginación de las pantallas de consulta

**Objetivo:** que la app deje de quedarse sin conexiones. Tres frentes, en este orden de urgencia:
**A)** que ninguna operación pueda bloquearse para siempre ni retener una conexión sin techo
(timeouts, keepalive, dimensionamiento del pool); **B)** que una lectura cancelada se **cancele de
verdad en MySQL** y que varias lecturas concurrentes no puedan consumir el pool entero; **C)** que las
pantallas de gran volumen dejen de traer el histórico completo, paginando en SQL donde el volumen no
tiene techo y en memoria donde sí lo tiene.

**Rama:** `ConexionesYPaginacion` (la crea el Paso 1)
**Modo:** directo — un commit por paso, sin PRs (`gh` no está instalado)
**Fecha de creación:** 2026-09-15
**Punto de partida:** `main` en `8b662b8`, árbol limpio.

> **Este plan absorbe `plans/rendimiento-historiales.md`**, que estaba pendiente casi entero. Ver
> "Qué se absorbe y qué se descarta" más abajo. El Paso 13 lo marca como superseded; **no se borra**.

---

## Decisiones tomadas con el usuario (2026-09-15)

| Tema | Decisión |
|---|---|
| Tipo de paginación | **Híbrido por pantalla.** SQL real (con filtros y orden movidos a SQL) en Historial de Lavadero y en el CDE (Ver Equipos + Estado de Procesos); en memoria en Ver Lotes y Ver Ciclos. |
| Plan previo | **Absorbido.** Los pasos todavía válidos de `rendimiento-historiales.md` se integran acá; aquél se marca superseded con la lista de qué migró. |
| Topología | **Varios puestos, todos remotos por Tailscale**, cada uno con su propio JAR y su propio pool. Con N puestos son N × `maximumPoolSize` conexiones potenciales contra el servidor. |
| Primer paso | **Crear la rama** (pedido explícito del usuario). |

---

## Diagnóstico — de dónde sale este plan

Relevado sobre el código el 2026-09-15. **Ordenado por daño, no por esfuerzo.**

### Disponibilidad — más grave que el rendimiento

| # | Hallazgo | Dónde | Por qué duele |
|---|---|---|---|
| 1 | **Sin `socketTimeout` en la URL JDBC** | `ConnectionPool.PARAMS_JDBC:55-56` | El default de Connector/J es **0 = infinito**. Si el túnel de Tailscale se corta a mitad de una lectura, el driver queda bloqueado en `read()` para siempre y esa conexión **nunca vuelve al pool**. No hace falta una consulta pesada: alcanza con que se caiga la red. Con 8-10 así, la app queda muerta hasta reiniciar. |
| 2 | **Sin `setQueryTimeout` en ninguna sentencia** | cero ocurrencias en `src/main/java` | Ninguna consulta tiene techo. Una que tarda cinco minutos ocupa una conexión cinco minutos. Y es lo único que hace que MySQL **mate la consulta del lado del servidor**: `socketTimeout` sólo abandona el lado del cliente. |
| 3 | **`keepaliveTime` de Hikari sin configurar** (default: apagado) con `minimumIdle=5` | `ConnectionPool.inicializarPool:290-304` | Cinco conexiones ociosas que el NAT/keepalive de Tailscale puede matar en silencio. La primera consulta que use una de ésas falla — o se cuelga, por el hallazgo #1. |
| 4 | **Un fallo del bloque `static` llega al usuario como "Ocurrió un error inesperado"** | `ConnectionPool:78-84` + `App.java` PASO 1/4 | ⚠️ **Corregido en revisión.** `ExceptionInInitializerError` **sí lleva la causa**; el problema es que es un `Error` y el `catch (Exception e)` de `App` no lo agarra: cae en el handler genérico. Y el primer toque de la clase es `log.info(ConnectionPool.getStats())`, que con el pool caído devuelve el string `"Pool no inicializado"` y **deja seguir**: la app llega a `inicializarEsquema()`, le pasa a Flyway un `DataSource` **null** y el usuario ve el mensaje de una `NullPointerException`. |
| 5 | **`connectionTimeout = 30 s`** | `ConnectionPool:293` | Es el tiempo que espera quien pide una conexión con el pool lleno. Ver #7: cuando el que pide es el EDT, son 30 segundos de app congelada. |

### Agotamiento del pool

| # | Hallazgo | Dónde | Por qué duele |
|---|---|---|---|
| 6 | **La cancelación de `TareaUI` es de aplicación, no de ejecución** | `TareaUI:41-45` (javadoc) y `Handle.cancelar:213-222` | `cancelar()` hace `worker.cancel(false)` y nada más. No hay `Statement.cancel()` ni cierre de conexión. F5 repetido en Historial lanza N consultas que **todas** siguen corriendo en MySQL y **todas** retienen una conexión. Es exactamente lo que reportó el usuario. |
| 7 | **`SwingWorker` tiene pool fijo de 10 hilos; HikariCP tiene 10 conexiones** | `TareaUI` usa `SwingWorker`; `ConnectionPool:291` | Coincidencia numérica en el peor lugar posible: 10 tareas de fondo pueden tomar las 10 conexiones y no dejar ninguna. |
| 8 | **Los 5 autocompletados sincrónicos piden conexión desde el EDT** | `AutocompleteListener`, `CatalogoLookup`, el de `catalogo_otros`, el de clientes de `LavaderoController` (excepción documentada en `CLAUDE.md`) | Con el pool lleno esperan hasta `connectionTimeout` **con el EDT bloqueado**. Ése es el mecanismo por el que "pool lleno" se convierte en "app colgada": sin esto, el pool lleno sólo demoraría refrescos. |
| 9 | **`obtenerHistorial()` toma 4 conexiones secuenciales; `findDetalle`, 3 más** | `HistorialLavaderoDAO` (`getConnection` en 216, 247 ×2, 262, 290, 314, 339) | No anida (no deadlockea), pero multiplica los checkouts, y cada uno paga el round-trip del hallazgo #10. |
| 10 | **`connectionTestQuery("SELECT 1")` desactiva la validación JDBC4** | `ConnectionPool:298` | Hikari lo recomienda **sólo** para drivers viejos. Connector/J 8.3 soporta `isValid()`. Además cada checkout paga un round-trip extra que, sobre Tailscale, no es gratis. |

### Rendimiento

| # | Hallazgo | Dónde | Por qué duele |
|---|---|---|---|
| 11 | **Cero índices sobre `fecha_ingreso` y sobre `estado`** | `equipos` (`V1:31-46`), `equipo_otros` (`V2:12-26`), `ingresos_lavadero` (`V7`, `V10`) | Verificado con `grep INDEX` sobre las 22 migraciones: los únicos índices que mencionan `estado` son compuestos de material (`V1:72`, `V2:38`). El Paso 4 de `rendimiento-historiales.md` nunca se ejecutó. |
| 12 | **El hotfix `cbf3cb3` mató el N+1 pero reintrodujo la tabla derivada `mv`** | `EquipoOtrosDAO.SQL_CABECERA` (el `LEFT JOIN (SELECT material_id, MAX(fecha) … GROUP BY material_id) mv`) | Esa derivada agrega `otros_material_movimientos` **entera**, sin `WHERE`, en cada listado. La tabla crece una fila por cambio de estado y nunca se poda. `EquipoDAO` tiene dos copias del mismo patrón. |
| 13 | **Sin `setFetchSize`; el ResultSet se materializa entero en RAM del cliente** | ningún DAO lo fija | Es el default de Connector/J. Con el histórico completo es memoria del puesto además de tiempo. |
| 15 | **El `queryTimeout` que el Paso 4 quiere poner choca con las guardas que esperan por diseño** | `ControlConcurrencia.esContencionDeLock` + los tres `SELECT … FOR UPDATE` de `CLAUDE.md` | ⚠️ **Descubierto en revisión, y es la trampa más cara del plan.** `innodb_lock_wait_timeout` es 50 s por default. Un `queryTimeout` de 30 s **gana siempre**, y Connector/J devuelve `ER_QUERY_INTERRUPTED` (1317), que `esContencionDeLock` **no reconoce** (sólo mapea 1213, 1205 y 50200). El choque entre operadores dejaría de salir como `ConflictoConcurrenciaException` ("alguien se te adelantó") y saldría como error técnico — y la rama 1205 quedaría muerta en producción. Ver el anti-patrón A12 y el Paso 4. |
| 16 | **El orden y el filtro por estado del CDE son sobre un valor derivado, no sobre la columna** | `CdeFilterStrategy` (filtra por `calcularEstado()`), `EquipoTableModel.actualizarDatos` (**ordena** por `calcularEstado().getOrden()`) | ⚠️ **Descubierto en revisión.** Paginar en SQL por `fecha_ingreso` y dejar que el `TableModel` reordene por estado **dentro de cada página** rompe el orden global: es el anti-patrón A4 aplicado al orden. **Lo que lo vuelve tratable:** `EquipoOtrosMaterialHelper.recalcularEstadoEquipo` y su gemelo de ortopedias persisten en la columna `estado` exactamente el mismo `MIN(orden)` que `calcularEstado()` computa en memoria, así que **la columna es el valor derivado**, ya mantenido y ya indexable (V23). Ver el Paso 10. |
| 14 | **Ningún `LIMIT` en ningún listado** | los cinco `obtenerTodos()` de los DAO grandes | Las pantallas de consulta traen el histórico completo por diseño. Es lo que ataca la Fase C. |

### Lo que **no** es un hallazgo

- Que las pantallas de consulta filtren en memoria sobre un snapshot: es una decisión de arquitectura
  deliberada y documentada. Lo que este plan hace es **cambiarla donde el volumen no tiene techo**, no
  declararla un error.
- Los autocompletados sincrónicos: son una excepción aceptada y documentada en `CLAUDE.md`. Este plan
  **no los vuelve asíncronos** (sin debounce darían resultados fuera de orden); les **acota el daño**
  con la reserva de conexiones del Paso 4.

---

## La corrección al planteo original del usuario

El usuario propuso "pestañas de 50 elementos" para aligerar la base. **Paginar no alivia la base
mientras el filtrado siga siendo en memoria**, y ésa es hoy la arquitectura
(`AbstractFilterController` + `FilterStrategy` sobre un snapshot completo). Dos consecuencias que este
plan trata como reglas duras:

1. **Paginar en memoria arregla el pintado, no la base.** Las seis tablas hacen `setRowCount(0)` + un
   `addRow` **por fila, en el EDT**. Con miles de filas eso congela la UI y **ninguna optimización de
   SQL lo toca**. Paginar en memoria lo arregla y es barato — pero la consulta sigue trayendo todo.
2. **Paginar en SQL sin mover los filtros a SQL da resultados incorrectos.** Filtrar en memoria una
   página de 50 filtra 50 de 5000, no las 50 primeras de las que matchean. Por eso los Pasos 8 y 10
   mueven filtros **y** orden a SQL, y por eso son los pasos caros.

Por eso el orden del plan es: **primero timeouts y cancelación** (son bugs de disponibilidad, son
baratos, y sin ellos el próximo corte de red se come cualquier mejora de rendimiento), **después
índices y consultas**, y **recién entonces paginación**.

---

## Qué se absorbe de `plans/rendimiento-historiales.md` y qué se descarta

| Paso de aquel plan | Destino |
|---|---|
| 1 — instrumentar `TareaUI` | **Absorbido → Paso 3**, ampliado con la salud del pool |
| 1.5 — sembrador sintético sobre MySQL local | **Absorbido → Paso 3**, tarea 4 (opcional, con sus tres guardas intactas) |
| 2 — matar el N+1 de `EquipoOtrosDAO` | **Ya hecho** a mano por el hotfix `cbf3cb3`. Queda pendiente su tarea 3 (propagar el fallo silencioso) → **Paso 6** |
| 3 — subconsultas correlacionadas de movimientos | **Absorbido → Paso 6** (y ahora incluye matar la `mv` que el hotfix reintrodujo) |
| 4 — índices | **Absorbido → Paso 5**, con la numeración corregida: **V23**, no V21 |
| 5 — fan-out de `SQL_RESUMEN` | **Ya hecho** (`0af8f43`) |
| 6 — volver a medir | **Absorbido → Paso 13** |
| 7, 8, 9 — carga en dos fases (`RefrescadorPantallas` con fase prioritaria + fase completa) | **DESCARTADOS.** Su propósito era *esconder* el costo de leer el histórico completo. La paginación en SQL **borra** ese costo en las dos pantallas donde dolía, así que la maquinaria de dos fases quedaría construida para esconder algo que ya no existe. En Ver Lotes y Ver Ciclos, que quedan con lectura completa, el volumen tiene techo y no la justifica. **Si el Paso 13 mide que alguna de esas dos sigue molestando, la Fase B de aquel plan es la respuesta y se reabre** |
| 10 — cierre | **Absorbido → Paso 13** |

> ⚠️ **`rendimiento-historiales.md` dice que la próxima migración libre es `V21`. Ya no lo es.**
> V21 (`version_optimistic_locking`) y V22 (`idx_ciclos_lavarropas_abiertos`) se escribieron después.
> **La próxima libre es `V23`.** Verificado: V1–V22 presentes, sin huecos.

---

## Contexto compartido (leer una vez por sesión)

App de escritorio **Swing, Java 17, Maven**, sin framework de DI. Capas por feature:
`model → dao → service → view/controller`. Todo se cablea a mano en `AppContext` y `UiCoordinator`.
**MySQL 8** en producción (`mysql-connector-j` 8.3.0, HikariCP 5.0.1), **H2 2.2.224 en `MODE=MySQL`**
en los tests. ~1130 tests.

### Reglas duras del repo que este plan debe respetar

1. **Ningún acceso a BD en el EDT.** `TareaUI` (`ui/common/`) es el único mecanismo de trabajo en
   fondo. No hay `new Thread()` ni `SwingWorker` fuera de esa clase (el único `new Thread()` que
   queda es el shutdown hook de `App.java`). `EdtGuard` grita en el log si alguien vuelve a poner I/O
   en el EDT, y con `-Daptium.edt.strict=true` lanza.
   **⚠️ Este plan agrega un ejecutor propio (el cancelador de sentencias del Paso 4). Es la primera
   excepción desde `TareaUI` y tiene que quedar justificada en su javadoc y en `CLAUDE.md`, no
   colada.**
2. **El estado mutable de un controller se lee y escribe sólo en el EDT.**
3. **Una migración ya escrita no se toca.** La próxima libre es **`V23`**.
4. **Los services no tienen JDBC.** Validan y delegan.
5. **Lógica embebida en Swing → clase plana sin Swing, testeada en aislamiento.** Referencias:
   `AgrupadorIngresosLote`, `ConstructorVistaCiclos`, `AgrupadorInstanciasSalida`, `GuardaRefresco`.
6. **Nada del lavadero se borra nunca.**
7. **Todo SQL corre igual en MySQL 8 y en H2 2.2 `MODE=MySQL`.** Nada de `GROUP_CONCAT`/`STRING_AGG`;
   subconsultas correlacionadas, CTE, funciones de ventana, `LIMIT ? OFFSET ?` sí funcionan en ambos.
8. **Un DAO que falla propaga.** `DataAccessException`/`DatabaseException`, nunca lista vacía o a
   medias. Referencia: `SalidaLavaderoDAO`, `HistorialLavaderoDAO`.
9. **Toda escritura que dependa de un dato leído antes lleva guarda**, y la guarda mira las filas
   afectadas (`ControlConcurrencia`). Este plan **no toca ninguna escritura**; si algún paso se ve
   tentado de hacerlo, es señal de que se salió de alcance.
10. **Cero SQL construida por concatenación de input del usuario.** Siempre `?`. La Fase C arma
    `WHERE` dinámicos: **los fragmentos son constantes del código y los valores van por `?`**, sin
    excepción.

### Cómo se despliega (importante para el Paso 2)

Cada puesto corre su propio fat JAR y **se autoactualiza desde GitHub Releases**, cada uno cuando
quiere. Consecuencias que este plan tiene que respetar:

- **Un cambio de configuración del pool llega a los puestos de forma escalonada**, no atómica.
  Mientras dure el escalonamiento conviven puestos con el pool viejo y con el nuevo. Por eso el
  Paso 2 sólo **baja** `maximumPoolSize`: nunca lo sube, que es lo único que podría empujar al
  servidor contra `max_connections`.
- **`DatabaseInitializer` aborta el arranque con `EsquemaDesactualizadoException` si la base está
  adelantada respecto del JAR.** Consecuencia directa para el Paso 5: **el día que se aplique la
  V23, todo puesto que todavía no se haya actualizado deja de arrancar.** No es un bug, es la
  protección funcionando — pero hay que decírselo al usuario antes, no después.
- `logback.xml` tiene un `RollingFileAppender` a nivel INFO en `${LOG_DIR}/app.log` con rotación
  diaria. La instrumentación del Paso 3 **viaja sola** en el próximo release y los tiempos reales se
  acumulan en cada puesto sin que nadie haga nada. Ésa es la validación final del plan.

### Archivos de referencia (leer antes de escribir código nuevo)

| Para | Leer |
|---|---|
| Pool, URL JDBC, arranque | `infrastructure/db/ConnectionPool.java` (entero, son 411 líneas) |
| Trabajo en fondo y cancelación | `ui/common/TareaUI.java` (`lanzar` 118-130, `done` 148-166, `Handle` 204-228) |
| Refresco por grupos | `app/ui/RefrescadorPantallas.java` (entero) · `app/ui/UiCoordinator.java` (58-75, 240-352) |
| Lectores de snapshot | `app/ui/LectorHistorialEquipos.java` · `LectorHistorialLotes.java` · `LectorDatosOperativos.java` |
| Filtrado en memoria | `common/util/AbstractFilterController.java` (19 líneas) · `common/util/FilterStrategy.java` |
| Guarda del EDT | `infrastructure/db/EdtGuard.java` |
| Transacciones | `infrastructure/db/TransactionalConnection.java` |
| DAO del historial de lavadero | `lavadero/dao/HistorialLavaderoDAO.java` (`SQL_RESUMEN` 74-80, `obtenerHistorial` 189, `agruparPorIngreso` 244) |
| Filtros del historial | `lavadero/controller/helpers/HistorialFilterStrategy.java` · `HistorialFilterCriteria` |
| DAOs de equipos | `equipos/ortopedias/dao/EquipoDAO.java` · `equipos/otros/dao/EquipoOtrosDAO.java` (`SQL_CABECERA` 49-73, `listar` ~242) |
| Filtros del CDE | `equipos/controller/VerEquiposController.java` (`aplicarFiltros` 113-140) · `equipos/ortopedias/controller/helpers/CdeFilterStrategy.java` |
| Pantallas a paginar | `PantallaVerEquipos` (dos tablas) · `PantallaVerCDEv2` (delega en `PanelEquipoMaterial`, que son **dos** tablas en un split) · `PantallaVerLotes` · `PantallaVerCiclos` · `PantallaHistorialLavadero` |
| Estado derivado y orden del CDE | `equipos/.../view/helpers/EquipoTableModel.java` (`actualizarDatos` **ordena** por `calcularEstado().getOrden()`) · `equipos/.../view/helpers/PanelEquipoMaterial.java` · `EquipoOtros.calcularEstado()` · `Equipo.calcularEstado()` · `EquipoOtrosMaterialHelper.recalcularEstadoEquipo` (la que persiste el mismo valor en la columna) |
| Guardas que esperan (chocan con `queryTimeout`) | `common/dao/ControlConcurrencia.java` (`esContencionDeLock`) · `CicloLavaderoDAO.SQL_BLOQUEAR_LINEA` · `SalidaLavaderoDAO.bloquearAfectados` |
| Arranque de la app | `app/App.java` (PASO 1/4: el primer toque de `ConnectionPool` es `getStats()`) |

> ⚠️ **Los números de línea de esta tabla se sacaron a propósito donde el símbolo alcanza.** La
> revisión encontró siete referencias corridas 12-15 líneas respecto del código actual. **Buscar por
> nombre de símbolo, no por número de línea**; los que quedan citados en los pasos se verificaron el
> 2026-09-15 y se re-verifican antes de editar.
| Migraciones (forma y numeración) | `db/migration/V22__idx_ciclos_lavarropas_abiertos.sql` |
| Tests de DAO con H2 | `src/test/java/com/example/AbstractDAOTest.java` |
| Tests del refrescador | `src/test/java/com/example/app/ui/RefrescadorPantallasTest.java` |
| Runbook de la conexión remota | `docs/conexion-remota-mysql-tailscale.md` · `README-DEPLOY.md` |

### Comandos

```bash
mvn clean package                                  # target/aptium.jar
mvn test                                           # unitarios
mvn verify                                         # tests + cobertura JaCoCo
mvn test -Dtest=NombreDeClase
mvn test -Dtest=NombreDeClase#nombreDelMetodo
java -jar target/aptium.jar                        # smoke manual (SIN -Daptium.edt.strict=true)
```

---

## Grafo de dependencias

```
Paso 1 (rama)
   │
   ▼
Paso 2 (timeouts + pool + arranque diagnosticable)   ◄── BLOQUEANTE de todo. Desplegable solo.
   │
   ├──► Paso 3 (instrumentación) ──► Paso 4 (cancelación real + techo de concurrencia)
   │
   ├──► Paso 5 (índices V23)                                    ─┐
   │                                                             │
   ├──► Paso 6 (subconsultas de movimientos + fallo silencioso) ─┤
   │                                                             │
   └──► Paso 7 (infra de paginación)                             │
            │                                                    │
            ├──► Paso 8 (Historial: datos) ──► Paso 9 (Historial: UI)
            │                                                    │
            ├──► Paso 10 (CDE: datos) ──► Paso 11 (CDE: UI + disolver el grupo)
            │                                                    │
            └──► Paso 12 (paginación en memoria: Lotes y Ciclos)  │
                                                                 │
                            Paso 13 (medir, revisar, cerrar) ◄────┴── depende de todos
```

**Paralelismo real.** Después del Paso 2 hay cuatro carriles que **no comparten un solo archivo**:

- **A:** 3 → 4 — `ui/common/TareaUI.java` y `infrastructure/db/`
- **B:** 5 — `db/migration/V23__*.sql` únicamente
- **C:** 6 — `equipos/*/dao/`
- **D:** 7 → {8→9, 10→11, 12} — `ui/common/` nuevo, después por feature

Dentro del carril D, los tres sub-carriles (Historial, CDE, memoria) son paralelos entre sí **salvo
que el 11 y el 12 tocan los dos `UiCoordinator`**. Serializar esos dos, o hacerlos en la misma sesión.

⚠️ El Paso 6 y el Paso 10 tocan los dos `EquipoOtrosDAO.java` y `EquipoDAO.java`. **Hacer el 6 antes
del 10**, o el 10 rehace trabajo.

| Paso | Modelo | Archivos que toca (exclusivos salvo aviso) |
|---|---|---|
| 1 | Sonnet | este archivo, `plans/rendimiento-historiales.md` |
| 2 | **Opus** | `infrastructure/db/ConnectionPool.java` (+ una clase nueva), `App.java`, tests |
| 3 | Sonnet | `ui/common/TareaUI.java`, su test, este archivo |
| 4 | **Opus** | `ui/common/TareaUI.java`, `infrastructure/db/` (clase nueva), tests |
| 5 | Sonnet | `db/migration/V23__*.sql` |
| 6 | Sonnet | `equipos/ortopedias/dao/EquipoDAO.java`, `equipos/otros/dao/EquipoOtrosDAO.java`, `equipos/controller/VerEquiposController.java`, tests |
| 7 | **Opus** | `common/paginacion/` (nuevo), `ui/common/PanelPaginacion.java` (nuevo), tests |
| 8 | **Opus** | `lavadero/dao/HistorialLavaderoDAO.java`, `lavadero/service/`, tests |
| 9 | Sonnet | `lavadero/controller/HistorialLavaderoController.java`, `PantallaHistorialLavadero`, `UiCoordinator` |
| 10 | **Opus** | `equipos/*/dao/`, `equipos/*/service/`, tests |
| 11 | **Opus** | `equipos/controller/`, `PantallaVerEquipos`, `PantallaVerCDEv2`, `UiCoordinator`, `app/ui/Lector*` |
| 12 | Sonnet | `lotes/controller/`, `lavadero/controller/VerCiclosController.java`, sus pantallas |
| 13 | **Opus** | `CLAUDE.md`, memoria, los dos archivos de plan |

**Invariantes verificados después de CADA paso:**

- [ ] `mvn test` en verde
- [ ] Cero `new Thread()` / `SwingWorker` nuevos fuera de `TareaUI` (el ejecutor del Paso 4 es la
      única excepción, y está justificada por escrito)
- [ ] Cero JDBC fuera de un DAO
- [ ] Cero SQL construida por concatenación de input del usuario
- [ ] Ningún DAO devuelve lista vacía o parcial ante un error
- [ ] Ninguna migración existente modificada
- [ ] **Ninguna operación mantiene dos conexiones abiertas a la vez** (desde el Paso 4: es el
      invariante del que depende la aritmética del semáforo; hoy se cumple, pero por accidente)
- [ ] **Ninguna escritura tocada** — ni su SQL, ni su guarda, ni el tipo de excepción que propaga

---

## Catálogo de anti-patrones — leer antes de cada paso

| # | Anti-patrón | Por qué está acá |
|---|---|---|
| A1 | **Poner `socketTimeout` más chico que la consulta legítima más lenta** | Convierte un problema de disponibilidad en uno de corrección: consultas buenas empiezan a morir y el operador aprende a reintentar todo. `socketTimeout` es el **respaldo de red**, no el techo de la consulta; el techo es `setQueryTimeout`, y tiene que ser el **menor** de los dos. |
| A2 | **Llamar `Statement.cancel()` desde el EDT** | En Connector/J, `cancel()` **abre una conexión nueva** para mandar `KILL QUERY`. Es I/O. Hacerlo en el EDT reintroduce exactamente lo que `EdtGuard` existe para impedir, y en `-Daptium.edt.strict=true` lanza. El Paso 4 lo despacha a un ejecutor daemon de un hilo. |
| A3 | **Paginar en memoria y llamarlo alivio de la base** | La consulta sigue trayendo todo. Arregla el pintado, que es real y vale, pero **no es lo que el usuario pidió**. Si un paso pagina en memoria, tiene que decir en su commit que arregla el pintado y nada más. |
| A4 | **Filtrar en memoria una página traída con `LIMIT`** | Da resultados incorrectos: filtra 50 de 5000 en vez de las 50 primeras de las que matchean. Si una pantalla pagina en SQL, **todos** sus filtros y su orden van a SQL. No hay mitad de camino. |
| A5 | **Recalcular el `COUNT(*)` en cada cambio de página** | El total sólo cambia cuando cambian los **filtros**. Pasar de la página 3 a la 4 no necesita contar de nuevo. Contar en cada página duplica las consultas y desperdicia justamente lo que este plan vino a ahorrar. |
| A6 | **Declarar que un índice se usa sin un `EXPLAIN`** | Que MySQL elija la tabla conductora que uno espera es *probable*, no *garantizado*. `LIKE '%x%'` no usa índice **nunca**, y un `ORDER BY` multi-tabla tampoco. Los criterios de salida piden `EXPLAIN`, no una creencia. |
| A7 | **Medir una sola vez** | Una sola corrida mezcla el calentamiento del JIT y el arranque del pool con lo que se quiere medir, y ese ruido es del orden de la mejora esperada. **Tres corridas, se anota la mediana, se descarta la primera apertura de la app.** |
| A8 | **Subir `maximumPoolSize` "por las dudas"** | Con N puestos remotos son N × pool contra un único MySQL. Subirlo mueve el agotamiento del pool local al agotamiento de `max_connections` del servidor, que es peor: ahí fallan **todos** los puestos a la vez. La dirección correcta es bajarlo y acotar la concurrencia de lecturas. |
| A9 | **Romper la invariante "un snapshot reemplaza al anterior" sin poner otra explícita en su lugar** | Es lo que hace barato todo el pipeline de refresco. La Fase C la reemplaza por "una página reemplaza a la página anterior **del mismo criterio**"; si cambian los criterios, se vuelve a la página 1. Eso hay que escribirlo, no asumirlo. |
| A10 | **Tocar una escritura** | Este plan es de lectura. Las guardas de concurrencia (`ControlConcurrencia`, los `FOR UPDATE`, los CAS) están fuera de alcance. Si un paso necesita tocar una escritura, parar y preguntar. |
| A11 | **Usar `rendimiento-historiales.md` como fuente de la numeración de migraciones** | Dice V21 y **V21 y V22 ya existen**. La próxima libre es V23. |
| A12 | **Poner `queryTimeout` en una conexión transaccional** | Hallazgo #15. Las guardas `FOR UPDATE` del lavadero **bloquean y esperan por diseño**, y el `innodb_lock_wait_timeout` de 50 s es su desenlace más probable. Un `queryTimeout` de 30 s lo tapa y devuelve un código (1317) que `ControlConcurrencia.esContencionDeLock` no reconoce: el aviso accionable al operador se convierte en una traza. **El techo de consulta va sólo en conexiones con `autoCommit = true`.** |
| A13 | **Indexar el registro de sentencias por `Thread`** | Hallazgo de revisión. `SwingWorker` tiene pool fijo de 10 hilos y `RefrescadorPantallas.refrescarAhora()` cancela `enVuelo` **incondicionalmente**, aunque esa tarea ya haya terminado (el campo nunca se pone en `null`). Un hilo reutilizado hace que cancelar una tarea muerta mate la consulta **viva de otra pantalla**. El registro va por **token de tarea**. |
| A14 | **Poner el semáforo de concurrencia alrededor de `leer`** | `TareaUI` envuelve tareas que **no tocan la base**: `ajustes-descargar-actualizacion` y `arranque-descargar-actualizacion` bajan el fat JAR y tardan minutos. Un semáforo alrededor de `leer` las hace consumir permisos de *conexión* que no usan, y deja esperando a una escritura. El permiso va alrededor del **checkout de conexión**. |
| A15 | **Colgar el reset de página de `aplicarFiltros()`** | `AbstractFilterController.recargarCache` llama a `aplicarFiltros()` **en cada refresco**. Si el reset vive ahí, cada F5 manda a la página 1 y se rompe "el refresco conserva la página". El reset cuelga del **callback de cambio de filtro** de la pantalla. |

---

# FASE A — Que nada se cuelgue para siempre

## Paso 1 — Crear la rama y registrar la absorción del plan previo

> Pedido explícito del usuario: esto es lo primero.

### Tareas

1. Con el árbol limpio y parado en `main` (`8b662b8`):
   ```bash
   git checkout main
   git pull
   git status                      # tiene que decir "nothing to commit, working tree clean"
   git checkout -b ConexionesYPaginacion
   ```
   ⚠️ **Si el árbol no está limpio, parar y preguntarle al usuario.** No stashear por cuenta propia:
   este repo tiene 20 ramas y un `.claude/settings.local.json` que cambia seguido.

2. Agregar al tope de `plans/rendimiento-historiales.md` un bloque de estado:
   ```markdown
   > **⚠️ SUPERSEDED (2026-09-15) por `plans/conexiones-y-paginacion.md`.**
   > Los Pasos 2 y 5 ya están aplicados en `main`. Los Pasos 1, 1.5, 3, 4, 6 y 10 migraron al plan
   > nuevo (ver su sección "Qué se absorbe"). Los Pasos 7, 8 y 9 —la carga en dos fases— quedaron
   > **descartados**: escondían el costo de leer el histórico completo, y la paginación en SQL lo
   > borra. Si el Paso 13 del plan nuevo mide que Ver Lotes o Ver Ciclos siguen molestando, este
   > plan es la respuesta y se reabre.
   > **Su numeración de migraciones quedó obsoleta: decía V21, y la próxima libre es V23.**
   ```
   **No borrar nada más de ese archivo**: su diagnóstico y su catálogo de anti-patrones siguen siendo
   la mejor documentación del problema de rendimiento.

3. Escribir este archivo (`plans/conexiones-y-paginacion.md`) si todavía no está commiteado.

### Verificación

```bash
git branch --show-current     # ConexionesYPaginacion
mvn -q test -Dtest=DatabaseInitializerTest
```

### Criterio de salida

- [ ] La rama `ConexionesYPaginacion` existe y arranca de `main` limpio
- [ ] `rendimiento-historiales.md` tiene el bloque de superseded, con la corrección de V21→V23
- [ ] Commit: `docs: plan de conexiones y paginación; rendimiento-historiales queda superseded`

---

## Paso 2 — Timeouts, keepalive, dimensionamiento del pool y arranque diagnosticable

> **BLOQUEANTE de todo lo demás. Y es el paso que se puede desplegar solo**, antes que cualquier otro:
> arregla los hallazgos #1 a #5 y #10, que son de disponibilidad, no de rendimiento.

### Contexto (autocontenido)

`ConnectionPool` (`infrastructure/db/`) es un singleton estático que en un bloque `static {}`
(78-84) carga configuración, crea la base si no existe y levanta HikariCP. Todos los DAO piden
conexión por `ConnectionPool.getConnection()` (336-346), que primero llama
`EdtGuard.verificarFueraDelHiloUi()`.

**Producción es MySQL remoto sobre Tailscale, con varios puestos, cada uno con su propio pool.**
Eso cambia tres cosas respecto de una base local:

1. **Una conexión ociosa puede morir en silencio.** El NAT/keepalive del túnel corta TCP inactivo. La
   conexión sigue en el pool, aparentemente sana, hasta que alguien la usa.
2. **Un corte a mitad de lectura deja el socket colgado.** Sin `socketTimeout`, para siempre.
3. **El servidor ve N × `maximumPoolSize`.** Hoy son 10 por puesto.

Lo que hay hoy (`inicializarPool` 276-318):

```java
config.setMaximumPoolSize(10);
config.setMinimumIdle(5);
config.setConnectionTimeout(30000);
config.setIdleTimeout(600000);
config.setMaxLifetime(1800000);
config.setConnectionTestQuery("SELECT 1");
config.setLeakDetectionThreshold(60000);
```

y `PARAMS_JDBC` (55-56) es `serverTimezone=UTC&connectionTimeZone=LOCAL&sslMode=REQUIRED`.

### Tareas

1. **`PARAMS_JDBC` — agregar los dos timeouts de red.** Es la constante compartida por la URL sin
   base y la del pool, así que las dos quedan cubiertas (ése es justamente el motivo por el que
   existe la constante; está en su javadoc).
   ```java
   /** 5 s para establecer el socket. Estrictamente menor que connectionTimeout de Hikari. */
   static final int CONNECT_TIMEOUT_MS = 5_000;
   /** 60 s sin un byte del servidor ⇒ se corta. Respaldo de red, NO techo de consulta. */
   static final int SOCKET_TIMEOUT_MS  = 60_000;
   /** Techo de consulta. Lo aplica ConexionesSupervidas (Paso 4). Menor que SOCKET_TIMEOUT_MS. */
   static final int TIMEOUT_CONSULTA_S = 30;

   private static final String PARAMS_JDBC =
       "serverTimezone=UTC&connectionTimeZone=LOCAL&sslMode=REQUIRED"
       + "&connectTimeout=" + CONNECT_TIMEOUT_MS
       + "&socketTimeout="  + SOCKET_TIMEOUT_MS;
   ```
   - ⚠️ **Las tres constantes viven acá desde el Paso 2, no en el Paso 4.** `TIMEOUT_CONSULTA_S`
     todavía no lo usa nadie, y está bien: el test de la tarea 5 lo necesita, y el Paso 2 es
     "desplegable solo". Ponerla en `ConexionesSupervisadas` haría que el Paso 2 no compile su
     propio test.
   - ⚠️ **Anti-patrón A1.** `socketTimeout` **no** es el techo de la consulta: es el respaldo para
     cuando la red murió. Tiene que ser **mayor** que el `queryTimeout`, porque si no, mata consultas
     legítimas antes de que el mecanismo que sabe cancelarlas bien llegue a actuar. 60 s > 30 s.
     **Esa relación va escrita en el comentario y atada por el test de la tarea 5**, porque el día que
     alguien baje uno de los dos números sin mirar el otro, la invierte.
   - ⚠️ **Segunda relación, del mismo tipo:** `CONNECT_TIMEOUT_MS` (socket + handshake TLS) tiene que
     ser **estrictamente menor** que el `connectionTimeout` de Hikari de la tarea 2, con margen. Con
     `sslMode=REQUIRED` y `minimumIdle` bajo, muchos checkouts crean una conexión física nueva, y si
     los dos números son iguales cualquier handshake lento sale como *"Connection is not available,
     request timed out"* con el pool vacío. 5 s < 10 s. **También va atada por test.**
   - Javadoc: por qué `socketTimeout` importa **especialmente** acá (Tailscale) y qué pasaba sin él
     (conexión retenida para siempre, pool agotado, app muerta hasta reiniciar).

2. **Configuración del pool.** Valores nuevos, cada uno con su porqué en un comentario:
   ```java
   config.setMaximumPoolSize(8);         // baja de 10: con N puestos remotos son N×8 contra el servidor
   config.setMinimumIdle(2);             // baja de 5: menos conexiones ociosas que el túnel pueda matar
   config.setConnectionTimeout(10000);   // baja de 30 s: los autocompletados piden desde el EDT
   config.setKeepaliveTime(120000);      // NUEVO: pinga las ociosas cada 2 min para que el NAT no las mate
   config.setIdleTimeout(300000);        // baja de 10 a 5 min
   config.setMaxLifetime(1800000);       // sin cambio
   config.setLeakDetectionThreshold(60000); // sin cambio
   // setConnectionTestQuery ELIMINADO: fijarlo obliga a Hikari a usar el camino legacy en vez
   // del isValid() de JDBC4, que Connector/J 8.3 soporta. NO se ahorra un round-trip (isValid()
   // también manda un COM_PING, y Hikari saltea la validación entera dentro de su
   // aliveBypassWindow de 500 ms): se ahorra el parseo de una query y se habilita validationTimeout.
   ```
   ⚠️ **Esa última línea de comentario importa.** La primera redacción de este plan decía que se
   ahorraba un round-trip; es falso. Si queda escrito así, el Paso 13 le va a atribuir a este cambio
   una mejora que no produjo.
   - ⚠️ **Anti-patrón A8: `maximumPoolSize` sólo baja, nunca sube.** Y dejar escrito el vínculo con
     el Paso 4: el semáforo de lecturas se dimensiona **a partir de** este número (8 − 3 = 5). Si
     alguien cambia uno, tiene que mirar el otro.
   - `keepaliveTime` tiene que ser menor que `maxLifetime` (Hikari lo exige) y el mínimo que acepta
     es 30 s.
   - ⚠️ **HikariCP 5.0.1 trata `minimumIdle` menor que `maximumPoolSize` como pool dinámico.**
     Verificar en el log de arranque que no emita un warning por la combinación elegida.

3. **Verificar `max_connections` del servidor contra N puestos.** No es opcional: es lo que dice si
   8 por puesto entra.
   ```sql
   SHOW VARIABLES LIKE 'max_connections';
   SHOW STATUS LIKE 'Threads_connected';
   SHOW STATUS LIKE 'Max_used_connections';
   ```
   - `Max_used_connections` es el pico histórico desde el último arranque de MySQL: es **el dato**,
     mucho más informativo que el cálculo teórico.
   - Anotar los tres números **en este archivo** y confirmar que `N × 8 + margen < max_connections`.
   - Si no entra, **preguntarle al usuario** antes de bajar más el pool: por debajo de ~6 empiezan a
     competir las transacciones con las lecturas.
   - **Y en el mismo viaje, el tamaño de las tablas** — movido acá desde el Paso 3 porque el Paso 5
     lo necesita antes (ver su advertencia sobre `CREATE INDEX` y `socketTimeout`):
     ```sql
     SELECT 'equipos' t, COUNT(*) n FROM equipos
     UNION ALL SELECT 'equipo_otros',               COUNT(*) FROM equipo_otros
     UNION ALL SELECT 'material_movimientos',       COUNT(*) FROM material_movimientos
     UNION ALL SELECT 'otros_material_movimientos', COUNT(*) FROM otros_material_movimientos
     UNION ALL SELECT 'ingresos_lavadero',          COUNT(*) FROM ingresos_lavadero
     UNION ALL SELECT 'elementos_ciclo_lavadero',   COUNT(*) FROM elementos_ciclo_lavadero
     UNION ALL SELECT 'lotes',                      COUNT(*) FROM lotes
     UNION ALL SELECT 'ciclos_lavadero',            COUNT(*) FROM ciclos_lavadero;
     ```

4. **Arranque diagnosticable (hallazgo #4).** ⚠️ **Leer el hallazgo #4 corregido antes de escribir:
   el diagnóstico obvio es el equivocado.** `ExceptionInInitializerError` **sí lleva la causa**; el
   problema es que es un `Error` y el `catch (Exception e)` de `App` no lo agarra, y que el primer
   toque de la clase es `log.info(ConnectionPool.getStats())`, que con el pool caído devuelve
   `"Pool no inicializado"` y **deja seguir** hasta que Flyway recibe un `DataSource` null.
   - Envolver el cuerpo del bloque `static` en `try { … } catch (Throwable t) { fallaDeArranque = t; }`
     con un `private static volatile Throwable fallaDeArranque`.
   - Agregar `public static void verificarArranque()`, que lanza con la causa original si
     `fallaDeArranque != null`, **y llamarla como PRIMERA sentencia del PASO 1/4 de `App.main`**,
     antes del `getStats()`. Sin eso el arreglo no se dispara nunca: es el punto exacto donde la
     versión anterior de este plan fallaba.
   - Guardar también `getConnection()`, `validarConexion()`, `getStats()` **y `getDataSource()`**
     (es el que usa `DatabaseInitializer`):
     ```java
     if (fallaDeArranque != null) {
         throw new SQLException("El pool de conexiones no pudo inicializarse", fallaDeArranque);
     }
     ```
   - **Por qué así y no moviendo la inicialización a un método explícito llamado desde `App.main`:**
     eso es más limpio pero cambia el orden de arranque de toda la app y de los tests
     (`aptium.testing`, `setDataSourceForTesting`), y este paso quiere ser desplegable solo. Anotarlo
     como deuda con esa razón, no dejarlo sin decir.

5. **Tests** (`ConnectionPoolTest`, nuevo o existente):
   - `PARAMS_JDBC` contiene `socketTimeout` y `connectTimeout` (test sobre la constante: barato y
     evita que un merge los borre sin que nadie se entere);
   - **`SOCKET_TIMEOUT_MS > TIMEOUT_CONSULTA_S × 1000`** — la relación del anti-patrón A1 convertida
     en test, que es la única forma de que sobreviva a un cambio distraído;
   - **`CONNECT_TIMEOUT_MS < connectionTimeout` de Hikari** — la segunda relación, misma razón;
   - con `fallaDeArranque` seteada por reflexión, `getConnection()`, `getStats()` y `getDataSource()`
     lanzan con **`getCause()` igual a la causa original**.

6. **Actualizar `docs/conexion-remota-mysql-tailscale.md`** con una sección corta: qué timeouts tiene
   la app ahora, qué síntoma corresponde a cada uno, y los tres números de la tarea 3.

### Verificación

```bash
mvn test -Dtest=ConnectionPoolTest
mvn test
mvn clean package && java -jar target/aptium.jar
```

Smoke manual, **los dos**:
- Arrancar con la base alcanzable: el log muestra el pool inicializado con los valores nuevos y
  **sin** warning de Hikari.
- Arrancar con la base **inalcanzable** (apagar Tailscale o poner un `DB_HOST` falso): tiene que salir
  el diálogo de error **con la causa real**, no un `NoClassDefFoundError`.

### Criterio de salida

- [ ] `PARAMS_JDBC` lleva `connectTimeout` y `socketTimeout`, y las **tres** constantes
      (`CONNECT_TIMEOUT_MS`, `SOCKET_TIMEOUT_MS`, `TIMEOUT_CONSULTA_S`) viven en `ConnectionPool`
- [ ] El pool arranca con 8/2/10 s/keepalive 2 min, sin `connectionTestQuery` y sin warning de Hikari
- [ ] Los tres números de `max_connections` **y los `COUNT(*)` de las ocho tablas** están anotados
      en este archivo, y `N × 8 + margen` entra
- [ ] `verificarArranque()` es la **primera** sentencia del PASO 1/4 de `App.main`
- [ ] Arrancar sin base da un diálogo con la causa original, **no** con una NPE de Flyway (probado a
      mano apagando Tailscale)
- [ ] Los **dos** tests de relación (`socketTimeout > queryTimeout`, `connectTimeout < connectionTimeout`)
      existen y pasan
- [ ] Commit: `fix: timeouts de red, keepalive y dimensionamiento del pool para la conexión remota`

---

## Paso 3 — Instrumentación: tiempo de lectura, tiempo de pintado y salud del pool

> Depende del Paso 2. **Bloqueante de la Fase C**: sin esto ningún paso posterior puede demostrar que
> sirvió. Absorbe los Pasos 1 y 1.5 de `rendimiento-historiales.md`.

### Contexto (autocontenido)

`TareaUI` es el único mecanismo de trabajo en fondo. Su `doInBackground()` (136-145) ya renombra el
hilo con el nombre de la tarea y llama `leer.call()`; su `done()` (148-166) corre `pintar` en el EDT.
Toda lectura de toda pantalla pasa por ahí con un nombre propio (`"refresco-operativo"`,
`"refresco-historial-equipos"`, …). Medir ahí es gratis y cubre todo.

`ConnectionPool.getStats()` ya existe y devuelve total/activas/idle/esperando. Hoy lo llama **un**
lugar: `App.main`, una vez, en el PASO 1/4 — o sea que nadie lo mira durante la sesión, que es cuando
el pool se llena.

### Tareas

1. **Cronometrar `leer` en `doInBackground()`** con `System.nanoTime()`, en el `finally` junto al
   renombrado del hilo — así también se registra una lectura que **falló** (un timeout que explota es
   justamente lo que se quiere ver). Nivel INFO:
   `log.info("Tarea '{}' leyó en {} ms", nombre, ms)`.

2. **Cronometrar `pintar` en `done()`** y loguearlo aparte. **No es opcional.** Las seis tablas hacen
   `setRowCount(0)` + un `addRow` por fila **en el EDT**; con miles de filas eso congela la UI y
   ninguna optimización de SQL lo toca. Es la hipótesis que la Fase C tiene que confirmar o descartar
   **antes** de invertir en paginación SQL.

3. **Loguear la salud del pool junto con el tiempo de lectura**, sólo cuando hay presión:
   ```java
   if (hayEsperando || activas >= umbral) log.warn("Pool bajo presión: {}", ConnectionPool.getStats());
   ```
   - Es la única forma de ver *desde producción* si el pool se está llenando, que es el problema que
     el usuario reportó y que hoy es invisible en el log.
   - ⚠️ **La ventana real de NPE no es la obvia.** Con `testDataSource`, `dataSource` es `null` (el
     bloque estático se saltea con `aptium.testing`) y `getStats()` ya retorna temprano con
     `"Pool no inicializado"`. El caso que **sí** explota es el pool **cerrado** por `shutdown()`:
     ahí `dataSource != null` pero `getHikariPoolMXBean()` es `null`. Blindar **ése**.

4. **(Opcional, si el usuario quiere medir con volumen) Sembrador sintético** — absorbido tal cual del
   Paso 1.5 de `rendimiento-historiales.md`, incluidas sus **tres guardas de seguridad**
   (nombre de base terminado en `_perf`, host `localhost`, tablas vacías o marcadas con prefijo
   `PERF-`). `@EnabledIfSystemProperty(named = "aptium.perf", matches = "true")` para que `mvn test`
   lo saltee siempre. **Un sembrador que se equivoca de base destruye datos de producción.**

5. **Tomar el baseline.** `mvn clean package && java -jar target/aptium.jar`, abrir cada pantalla de
   consulta y llenar esta tabla **en este archivo**.
   - ⚠️ **Anti-patrón A7: tres corridas por pantalla, se anota la mediana, se descarta la primera
     apertura de la app.**

   | Tarea | leer (mediana de 3) | pintar | filas | post Fase C |
   |---|---|---|---|---|
   | `refresco-operativo` | _(llenar)_ | | | — |
   | `refresco-historial-equipos` | _(llenar)_ | | | |
   | `refresco-historial-lotes` | _(llenar)_ | | | |
   | `refresco-historial-ciclos` | _(llenar)_ | | | |
   | `refresco-historial-lavadero` | _(llenar)_ | | | |
   | `detalle-historial-lavadero` | _(llenar)_ | | | — |

6. **Traer acá los `COUNT(*)` que tomó el Paso 2** (tarea 3) — son los que hacen interpretables los
   ms. No volver a correrlos: si el Paso 2 no los anotó, ése es el bug.

7. **⚠️ Compuerta explícita, y hay que decírsela al usuario:**
   - **Si `pintar` supera el 30 % del total en alguna pantalla**, esa pantalla gana más con
     paginación (que corta el pintado a 50 filas) que con SQL. Es un argumento **a favor** de la
     Fase C, no en contra — anotarlo.
   - **Si alguna de las dos pantallas elegidas para paginación SQL resulta tener menos de ~500 filas**,
     decirlo: el costo de mover sus filtros a SQL (Pasos 8 y 10) no se justifica por rendimiento, y
     conviene preguntarle al usuario si igual la quiere por UX. **No esconderlo detrás de un número
     lindo.**

8. **Tests** (`TareaUITest`): la instrumentación no altera el valor devuelto ni el ruteo del error.
   Verificar el texto del log es frágil y no aporta.

### Verificación

```bash
mvn test -Dtest=TareaUITest
mvn test
mvn clean package && java -jar target/aptium.jar   # y leer el log
```

### Criterio de salida

- [ ] El log muestra tiempo de `leer` **y** de `pintar` por cada tarea, con su nombre
- [ ] El log avisa cuando el pool está bajo presión, y **no** explota en los tests (`getStats` blindado)
- [ ] La tabla de baseline está llena con medianas de 3, y los `COUNT(*)` anotados
- [ ] La compuerta de la tarea 7 está evaluada y, si aplica, comunicada al usuario
- [ ] Commit: `feat: TareaUI mide lectura y pintado, y avisa cuando el pool está bajo presión`

---

## Paso 4 — Cancelación real y techo de lecturas concurrentes

> Depende de los Pasos 2 y 3. **Es el hallazgo #6, el que el usuario reportó explícitamente.**
> Es el paso más delicado del plan: leer entero antes de escribir.

### Contexto (autocontenido)

Hoy `TareaUI.Handle.cancelar()` (213-222) hace:

```java
cancelada.set(true);
actual.cancel(false);   // "Sin interrumpir: interrumpir una query JDBC en curso no es confiable"
```

El javadoc de la clase (41-45) lo dice sin rodeos: *"La cancelación es de aplicación, no de ejecución:
una query JDBC ya lanzada termina igual, pero su resultado se descarta."* Eso era una decisión
razonable cuando las consultas eran baratas. Con el histórico de producción **no lo es**: F5 repetido
en Historial deja N consultas corriendo en MySQL, cada una reteniendo una conexión.

**El comentario del código tiene razón a medias:** interrumpir el *hilo* no es confiable. Lo que sí es
confiable es `Statement.cancel()`, que en Connector/J manda un `KILL QUERY` al servidor.

⚠️ **Y `Statement.cancel()` hace I/O: abre una conexión nueva para mandar el `KILL`.** Por eso
**no puede correr en el EDT** (anti-patrón A2) — y `cancelar()` se llama desde el EDT, que es donde
vive `RefrescadorPantallas.refrescarAhora()` (85-95).

**El otro problema del paso:** `SwingWorker` tiene un pool fijo de 10 hilos y el pool de conexiones
tiene 8 (después del Paso 2). Diez tareas de fondo pueden tomar las ocho conexiones y no dejar
ninguna para los cinco autocompletados sincrónicos **que piden desde el EDT** — que es cómo "pool
lleno" se convierte en "app congelada".

### Diseño (decidido por el plan, con su porqué)

| Decisión | Por qué |
|---|---|
| El punto de enganche es **`ConnectionPool.getConnection()`**, no los DAO | Es el único cuello por el que pasa todo el JDBC **de la aplicación**. Poner `setQueryTimeout` y el registro en cada DAO son ~70 sitios que se olvidan solos. ⚠️ **Dos rutas quedan afuera a propósito y hay que decirlo:** Flyway (usa `getDataSource()`, no `getConnection()`) y la creación de la base (`DriverManager` directo). Que Flyway quede exento es **lo correcto** — ver el Paso 5 |
| Se envuelve la `Connection` con un **`java.lang.reflect.Proxy`** | Delegar `java.sql.Connection` a mano son ~50 métodos de ruido puro. El proxy despacha **por nombre de método**, así que cubre todos los overloads de `prepareStatement`/`createStatement` y además `prepareCall` |
| `setQueryTimeout` es de 30 s **y sólo en conexiones con `autoCommit = true`** | ⚠️ **Anti-patrón A12, hallazgo #15.** Las guardas `FOR UPDATE` del lavadero esperan por diseño, y con `innodb_lock_wait_timeout = 50 s` un techo de 30 s las tapa y devuelve un código que `ControlConcurrencia.esContencionDeLock` no reconoce. `TransactionalConnection` pone `autoCommit = false` en su constructor, así que la condición es exacta y no hace falta ningún flag nuevo. Las escrituras quedan acotadas por `socketTimeout` (60 s), que es **mayor** que los 50 s del lock wait: el 1205 sigue llegando |
| El registro es **por token de tarea**, no por hilo | ⚠️ **Anti-patrón A13.** `SwingWorker` reutiliza sus 10 hilos y `RefrescadorPantallas.refrescarAhora()` cancela `enVuelo` aunque ya haya terminado (nunca lo pone en `null`): con registro por hilo, cancelar una tarea muerta mataría la consulta viva de otra pantalla. El token viaja en un `ThreadLocal` que `TareaUI` pone al entrar a `doInBackground` y saca en el `finally` |
| El registro es `Map<Token, Set<Statement>>`, **no** `Map<_, Statement>` | Un DAO transaccional tiene varios `PreparedStatement` abiertos a la vez sobre la misma conexión, y un hilo puede tener dos conexiones. Con un solo slot, el segundo pisa al primero y el `close()` del interno desregistra al externo |
| El `cancel()` se despacha a un **ejecutor daemon de un hilo** | Anti-patrón A2. Un hilo alcanza: cancelar es raro y rápido. Daemon para que no impida el cierre de la app |
| El techo de concurrencia es un **`Semaphore` dentro de `ConnectionPool.getConnection()`**, no alrededor de `leer` | ⚠️ **Anti-patrón A14.** `TareaUI` envuelve tareas que no tocan la base: `ajustes-descargar-actualizacion` y `arranque-descargar-actualizacion` bajan el fat JAR y tardan **minutos**. Un semáforo alrededor de `leer` las haría consumir permisos de conexión que no usan y dejaría esperando a `registrar-estado-confirmar`. Alrededor del checkout, el permiso mide exactamente lo que escasea |
| El permiso se toma con **`tryAcquire(timeout)`**, no `acquire()` | Un `acquire()` bloqueado no es interrumpible (`Handle.cancelar()` usa `cancel(false)`), así que la saturación saldría como cuelgue silencioso. Con timeout sale como `SQLException` visible, igual que un `connectionTimeout` agotado |
| El semáforo deja **3 permisos de reserva** (8 conexiones − 5 concurrentes) | Dos para los autocompletados del EDT, uno para la transacción de una escritura concurrente. El número se **deriva** de `maximumPoolSize`, no se escribe dos veces |
| **Invariante que el semáforo necesita y que hay que escribir:** *ninguna operación mantiene dos conexiones abiertas a la vez* | Hoy se cumple, pero por accidente: `HistorialLavaderoDAO.obtenerHistorial()` toma cuatro conexiones **secuencialmente**. Si alguna vez se anidan dos, 5 permisos × 2 = 10 > 8 y el pool se agota **con el semáforo puesto**. Va al javadoc y a la lista de invariantes por paso |

### Tareas

1. **`infrastructure/db/TokenTarea.java`** (clase nueva, mínima): identidad opaca de una tarea de
   fondo, más el `ThreadLocal` que la transporta. Vive en `infrastructure/db` y no en `ui/common`
   porque quien la **lee** es el proxy JDBC; `TareaUI` sólo la pone y la saca.

2. **`infrastructure/db/ConexionesSupervisadas.java`** (clase nueva, plana):
   - `static Connection envolver(Connection real)` — devuelve un `Proxy` que despacha **por nombre de
     método**:
     - `prepareStatement` / `createStatement` / `prepareCall` (todos los overloads): llama al real;
       **si `real.getAutoCommit()` es `true`**, hace `stmt.setQueryTimeout(ConnectionPool.TIMEOUT_CONSULTA_S)`
       (anti-patrón A12); registra la sentencia contra el token vigente **si hay uno**; devuelve la
       sentencia envuelta en otro proxy que **desregistra en `close()`**;
     - `close`: desregistra **las sentencias de esta conexión** (no "todo lo del token": un hilo puede
       tener dos conexiones abiertas) y cierra el real;
     - `unwrap`: ⚠️ **devolver el proxy, no el real**, o se escapa una conexión sin supervisión;
     - `equals` / `hashCode` / `toString`: manejarlos explícitamente. Un `java.lang.reflect.Proxy` que
       los delega ciegamente hace que `Objects.equals(conn, conn)` se comporte mal;
     - cualquier otro método: delega.
   - `static void cancelarDe(TokenTarea token)` — cancela **todas** las sentencias vivas de ese token.
     **No lanza**: si ya terminaron, si ya se cerraron o si el driver no soporta `cancel`, loguea
     `debug` y sigue. Cancelar es best-effort por definición.
   - Registro: `ConcurrentHashMap<TokenTarea, Set<Statement>>` (con `Set` concurrente).
     ⚠️ **No puede retener tokens muertos**: la entrada del token se borra cuando `TareaUI` lo saca
     del `ThreadLocal`, además del desregistro por statement y por conexión. **Test explícito de que
     el mapa queda vacío después de N tareas.**
   - ⚠️ **`setQueryTimeout` en H2 2.x es a nivel SESIÓN**, no de `Statement`: emite
     `SET QUERY_TIMEOUT` sobre la conexión y persiste al cerrar la sentencia. Acá es inocuo (el valor
     es siempre el mismo), pero **el test tiene que leerlo de vuelta con `getQueryTimeout()`**, no
     asumirlo, y va una línea de javadoc para que nadie lo vuelva a depurar dentro de un año.
   - Javadoc: por qué acá y no en cada DAO; por qué proxy y no delegación; **por qué las conexiones
     transaccionales quedan exentas** (A12); qué pasa si `cancel()` falla; y que **Flyway y la
     creación de la base no pasan por acá, a propósito**.

3. **`ConnectionPool.getConnection()`**:
   - toma el permiso del semáforo con `tryAcquire`, y devuelve
     `ConexionesSupervisadas.envolver(...)`, **tanto para el pool como para el `testDataSource`**.
     Que los tests pasen por el mismo camino es la mitad del valor.
   - el permiso se **libera en el `close()` de la conexión envuelta** — que es la única razón por la
     que el semáforo puede vivir acá. ⚠️ **Si una conexión se pierde sin cerrar, el permiso se pierde
     con ella.** Por eso `leakDetectionThreshold` (60 s) se queda: es lo que lo delata.
   - `PERMISOS_CONEXION = MAX_POOL - RESERVA_EDT` con `RESERVA_EDT = 3`, **derivado** de
     `maximumPoolSize`, con los dos números y el comentario en un solo lugar (anti-patrón A8).
   - ⚠️ **El `tryAcquire` va con un timeout MENOR que el `connectionTimeout` de Hikari**, para que la
     saturación del semáforo se distinga en el log de la del pool. Si no, los dos fallos dan el mismo
     mensaje y no se sabe cuál de los dos techos se tocó.

4. **`TareaUI` — cancelación de ejecución:**
   - `doInBackground()` crea un `TokenTarea`, lo pone en el `ThreadLocal` y lo saca en el `finally`
     (junto con el renombrado del hilo, que ya está ahí).
   - `Handle` guarda ese token; `cancelar()` sigue haciendo lo de hoy **y además**:
     ```java
     TokenTarea t = token;
     if (t != null) CANCELADOR.execute(() -> ConexionesSupervisadas.cancelarDe(t));
     ```
   - `CANCELADOR` = `Executors.newSingleThreadExecutor` con `ThreadFactory` daemon y nombre
     `"cancelador-sql"`.
   - ⚠️ **Actualizar el javadoc de la clase**, que hoy afirma lo contrario ("la cancelación es de
     aplicación, no de ejecución"). Un javadoc que miente sobre la concurrencia es peor que no tenerlo.
   - ⚠️ **La excepción de una consulta cancelada no puede llegar al usuario.** Connector/J tira
     `MySQLStatementCancelledException`; H2, un `JdbcSQLTimeoutException`. Como la tarea ya está
     `cancelada`, `done()` retorna antes de `pintar`/`siFalla` — **verificar que ésa sea la secuencia
     real y no una suposición**, porque si el fallo llega primero el usuario ve un cartel de error por
     un refresco que él mismo reemplazó.

5. **Tests:**
   - `ConexionesSupervisadasTest`:
     - toda sentencia de una conexión con `autoCommit = true` sale con `queryTimeout` seteado,
       **leído de vuelta con `getQueryTimeout()`**;
     - una conexión con `autoCommit = false` sale **sin** `queryTimeout` (el test de A12: es lo que
       protege las guardas del lavadero);
     - el registro queda vacío después de cerrar N conexiones;
     - dos conexiones abiertas en el mismo token: cerrar una **no** desregistra las sentencias de la
       otra;
     - `cancelarDe` de un token sin sentencias no lanza;
     - ⚠️ **el test que la revisión pidió agregar:** cancelar un `Handle` **cuya tarea ya terminó**
       no toca la sentencia viva de otra tarea que heredó el mismo hilo. Es el único que detecta el
       anti-patrón A13, y ninguno de los otros lo haría.
   - `TareaUITest`:
     - cancelar una tarea en vuelo **invoca la cancelación** (con un doble del cancelador, no con una
       consulta real: el test no puede depender de que H2 cancele igual que MySQL);
     - (regresión) la cancelación **no** dispara `siFalla` ni `pintar`;
     - una tarea sin JDBC (p. ej. una descarga simulada) **no toma ningún permiso** — el test de A14.
   - `ConnectionPoolTest`: con el semáforo saturado, el checkout N+1 falla con un mensaje que lo
     distingue del `connectionTimeout`; el permiso se libera en `close()` aunque la operación falle.
   - Test de integración con H2 que corre `PERMISOS_CONEXION + 2` operaciones concurrentes y verifica
     que ninguna falla por falta de conexión.

6. **`CLAUDE.md`** — actualizar la sección de concurrencia: `TareaUI` ahora cancela de verdad, existe
   `ConexionesSupervisadas`, y el ejecutor `cancelador-sql` es la **segunda** excepción a "no hay
   `new Thread()` fuera de `TareaUI`", con su motivo (`cancel()` hace I/O y `cancelar()` se llama
   desde el EDT).

### Verificación

```bash
mvn test -Dtest=ConexionesSupervisadasTest
mvn test -Dtest=TareaUITest
mvn test
mvn clean package && java -jar target/aptium.jar
```

**Smoke manual — éste es el que prueba el paso**, y necesita mirar MySQL, no la app:
1. Abrir Historial de Lavadero y mantener **F5** apretado ~5 segundos.
2. En el servidor, `SHOW PROCESSLIST;` (o `SHOW FULL PROCESSLIST`).
3. Tiene que quedar **a lo sumo una** consulta de historial corriendo, no una por F5.
4. En el log de la app, ninguna línea de error de cara al usuario por las canceladas.

### Criterio de salida

- [ ] Toda sentencia de una conexión **autocommit** sale con `queryTimeout` seteado, y las
      **transaccionales NO** — las dos cosas verificadas por test (anti-patrón A12)
- [ ] `SHOW PROCESSLIST` después de la ráfaga de F5 muestra a lo sumo una consulta viva (smoke hecho)
- [ ] El registro es por **token**, no por hilo, y existe el test de "cancelar una tarea terminada no
      mata la consulta de otra" (anti-patrón A13)
- [ ] El semáforo está en el **checkout de conexión**, no alrededor de `leer`, y una tarea sin JDBC
      no toma permiso (anti-patrón A14)
- [ ] La cancelación **no** corre en el EDT (verificable con `-Daptium.edt.strict=true` en el test)
- [ ] El javadoc de `TareaUI` ya no dice que la cancelación es sólo de aplicación
- [ ] El semáforo deriva su tamaño de `maximumPoolSize`; los dos números viven en un solo lugar
- [ ] El invariante "ninguna operación mantiene dos conexiones a la vez" está escrito en el javadoc
      y agregado a la lista de invariantes por paso
- [ ] `CLAUDE.md` documenta `ConexionesSupervisadas`, la exención transaccional y el `cancelador-sql`
- [ ] Commit: `fix: una lectura cancelada se cancela en MySQL y las lecturas no agotan el pool`

---

# FASE B — Borrar el costo de las consultas

## Paso 5 — Índices que faltan (migración `V23`)

> Depende sólo del Paso 2. Independiente de los Pasos 4, 6 y 7. **Hallazgo #11.**
> Absorbe el Paso 4 de `rendimiento-historiales.md`, **con la numeración corregida**.

### Contexto (autocontenido)

Verificado con `grep INDEX` sobre las 22 migraciones: **no existe ningún índice sobre
`equipos.fecha_ingreso`, `equipos.estado`, `equipo_otros.fecha_ingreso`, `equipo_otros.estado`,
`ingresos_lavadero.fecha_ingreso` ni `ingresos_lavadero.estado`.** Los únicos que mencionan `estado`
son compuestos de material (`V1:72`, `V2:38`).

**Lo que paga cada índice:**

| Índice | Consulta que acelera |
|---|---|
| `equipos.fecha_ingreso`, `equipo_otros.fecha_ingreso` | el `ORDER BY` de todos los listados y `obtenerEntreFechas(...)`. **Y es el que hace viable la paginación del Paso 10**: sin él, `ORDER BY fecha_ingreso DESC LIMIT ? OFFSET ?` es un filesort del histórico entero en cada página |
| `equipos.estado`, `equipo_otros.estado` | `obtenerEquiposNuevos()` y el filtro por estado que el Paso 10 mueve a SQL |
| `ingresos_lavadero.estado` | `CicloLavaderoDAO:80` (`WHERE il.estado = 'CLASIFICADO'`) y el filtro de estados del Paso 8 |
| `ingresos_lavadero.fecha_ingreso` | el `ORDER BY` de `SQL_RESUMEN`, que desde `0af8f43` **ya es de una sola tabla**. Es el que hace viable la paginación del Paso 8 |

Los `JOIN` por FK ya están cubiertos: InnoDB crea el índice al declarar la `FOREIGN KEY`.

⚠️ **Este paso vale mucho más ahora que en el plan viejo.** Allá pagaba consultas sueltas; acá es el
prerrequisito de rendimiento de toda la Fase C: en InnoDB todo índice secundario incluye la PK
implícitamente, así que `idx_otros_fecha_ingreso` cubre entero un `ORDER BY fecha_ingreso DESC, id DESC`
— que es exactamente el orden que van a usar los Pasos 8 y 10.

⚠️ **Anti-patrón A6: no afirmar que el índice se usa. Comprobarlo con `EXPLAIN`.**

### Tareas

1. **`src/main/resources/db/migration/V23__indices_consulta.sql`**:
   ```sql
   CREATE INDEX idx_equipos_fecha_ingreso      ON equipos            (fecha_ingreso);
   CREATE INDEX idx_equipos_estado             ON equipos            (estado);
   CREATE INDEX idx_otros_fecha_ingreso        ON equipo_otros       (fecha_ingreso);
   CREATE INDEX idx_otros_estado               ON equipo_otros       (estado);
   CREATE INDEX idx_ingresos_lav_fecha_ingreso ON ingresos_lavadero  (fecha_ingreso);
   CREATE INDEX idx_ingresos_lav_estado        ON ingresos_lavadero  (estado);
   ```
   - Encabezado comentado con **qué consulta paga cada índice** (la tabla de arriba) y una línea
     diciendo que son el prerrequisito de la paginación de los Pasos 8 y 10.
   - Sintaxis idéntica a `V10`/`V17`/`V19`/`V20`/`V22`, que ya corren en MySQL y en H2.
   - ⚠️ **V21 y V22 ya existen. Ésta es V23.** No tocar ninguna existente (anti-patrón A11).

2. **⚠️ El `socketTimeout` del Paso 2 se aplica a Flyway, y eso puede envenenar el esquema en todos
   los puestos.** `DatabaseInitializer` toma el `DataSource` del pool, así que Flyway hereda
   `PARAMS_JDBC` y por lo tanto `socketTimeout = 60 s`. Un `CREATE INDEX` que tarde más de 60 s de
   silencio del servidor se **aborta del lado del cliente**, Flyway deja una fila fallida en
   `flyway_schema_history` y **todo arranque posterior falla** hasta un `repair` manual — en todos los
   puestos, no en el que la aplicó.
   - **Mirar los `COUNT(*)` que tomó el Paso 2** (tarea 3) antes de desplegar. Con decenas de miles de
     filas, `CREATE INDEX` en InnoDB es de segundos y no hay problema.
   - Si alguna tabla es lo bastante grande como para acercarse a los 60 s: **crear el índice a mano en
     el servidor** y dejar que Flyway registre la migración (`CREATE INDEX` ya aplicado = no-op sólo
     si se usa la forma que el motor tolera; verificarlo), o correr la migración una vez con una
     conexión dedicada sin `socketTimeout`. **Decidirlo antes, no cuando falle.**

3. **⚠️ Avisarle al usuario antes de desplegar esta migración.** `DatabaseInitializer` aborta el
   arranque con `EsquemaDesactualizadoException` si la base está adelantada respecto del JAR: el día
   que un puesto aplique la V23, **todo puesto que todavía no se haya actualizado deja de arrancar**.
   Es la protección funcionando, pero es una interrupción operativa y la decide el usuario, no el plan.

4. Correr la suite. ⚠️ Sólo fallan los tests que extienden `AbstractDAOTest` si la migración está mal;
   los unitarios con Mockito no tocan Flyway. La BD H2 es compartida entre clases
   (`AbstractDAOTest:30`, `DB_CLOSE_DELAY=-1`), así que la migración corre en la primera clase de la
   corrida: mirar específicamente un `*DAOTest`.

### Verificación

```bash
mvn test -Dtest=EquipoDAOTest
mvn test -Dtest=HistorialLavaderoDAOTest
mvn test
mvn clean package && java -jar target/aptium.jar   # el log de Flyway debe mostrar V23
```

### Criterio de salida

- [ ] `mvn test` en verde con al menos un `*DAOTest` corriendo (prueba que V23 corre en H2)
- [ ] La app arranca y Flyway registra V23 (prueba que corre en MySQL)
- [ ] Ninguna migración existente modificada; el archivo es **V23**, no V21
- [ ] El riesgo de `CREATE INDEX` vs `socketTimeout` está evaluado contra los `COUNT(*)` reales, y la
      decisión (aplicar por Flyway o a mano) está escrita
- [ ] **`EXPLAIN` pegado en el commit** de un listado de `equipo_otros` ordenado por `fecha_ingreso`,
      mostrando el índice y **sin** `Using filesort`. Si dice filesort, el índice no paga lo que este
      paso promete: **anotarlo en "Mutaciones aplicadas" en vez de declarar la victoria**
- [ ] El usuario está avisado del corte de arranque para los puestos desactualizados
- [ ] Commit: `perf: índices de fecha y estado para las consultas de listado (V23)`

---

## Paso 6 — Sacar las subconsultas que agregan toda la tabla de movimientos

> Depende del Paso 2. Independiente de los Pasos 4, 5 y 7. **Hallazgo #12.**
> **Hacerlo antes del Paso 10**, que toca los mismos archivos.
> Absorbe el Paso 3 de `rendimiento-historiales.md` más la tarea pendiente de su Paso 2.

### Contexto (autocontenido)

Las consultas de listado traen el último movimiento de cada material con una **tabla derivada que
agrega la tabla entera de movimientos, sin `WHERE`**:

```sql
LEFT JOIN (
  SELECT material_id, MAX(fecha) AS fecha
  FROM otros_material_movimientos GROUP BY material_id
) mv ON mv.material_id = m.id
```

Ambas tablas de movimientos reciben una fila por cada cambio de estado y **nunca se podan**, así que
ese costo crece para siempre aunque el listado devuelva tres equipos.

⚠️ **El hotfix `cbf3cb3` mató el N+1 de `EquipoOtrosDAO` (bien) pero introdujo esta derivada en
`SQL_CABECERA`** — donde antes vivía dentro de `cargarMateriales`, por equipo. El cambio fue una
mejora clara, pero dejó el agregado global en la ruta principal.

Existen `idx_mov_material (material_id)` (`V1:87`) e `idx_otros_mov_material (material_id)` (`V2:53`).

`EquipoDAO` tiene **dos copias** del mismo patrón (`SQL_EQUIPOS_CON_MATERIALES` ~46-64 y otra ~292).

### Tareas

1. En **`EquipoOtrosDAO.SQL_CABECERA`** y en las dos de **`EquipoDAO`**, reemplazar la tabla derivada
   por una **subconsulta correlacionada** en el `SELECT`:
   ```sql
   (SELECT MAX(mm.fecha) FROM otros_material_movimientos mm WHERE mm.material_id = m.id)
       AS ultimo_movimiento
   ```
   Con el índice, eso es un *index range scan* de una entrada por material en vez de una agregación
   completa por listado. Corre igual en MySQL 8 y H2 2.2. Sin migración.
   - Quitar el `LEFT JOIN (…) mv` / `(…) mm` correspondiente.
   - Comentario corto en cada consulta explicando **por qué** (la derivada agregaba la tabla entera).

2. **⚠️ Sacar `em.id` / `m.id` de los `ORDER BY` de listado y ordenar los materiales en memoria.**
   Un `ORDER BY` multi-tabla (`e.fecha_ingreso DESC, e.id DESC, em.id`) **no lo cubre ningún índice de
   una sola tabla** ⇒ filesort del join entero, para siempre — y eso anula el Paso 5 y hace inviable
   la paginación del Paso 10. Sin esa clave queda `fecha_ingreso DESC, id DESC`, de una sola tabla, y
   como en InnoDB todo índice secundario incluye la PK, `idx_*_fecha_ingreso` lo cubre entero.
   - Después del plegado, ordenar cada lista de materiales en memoria
     (`list.sort(comparingInt(Material::getId))`). Son listas de 3-10 elementos.
   - **En `EquipoDAO` son CUATRO sitios**, y **dos de ellos no tienen el desempate que el plan
     asumía.** Verificado el 2026-09-15:

     | Método | `ORDER BY` hoy | Tiene que quedar |
     |---|---|---|
     | `obtenerTodosLosEquipos()` | `e.fecha_ingreso DESC, e.id DESC, em.id` | `e.fecha_ingreso DESC, e.id DESC` |
     | `obtenerActivos()` | `e.fecha_ingreso DESC, e.id DESC, em.id` | `e.fecha_ingreso DESC, e.id DESC` |
     | `obtenerEquiposNuevos()` | `e.fecha_ingreso DESC, em.id` ← **sin `e.id`** | `e.fecha_ingreso DESC, e.id DESC` |
     | `obtenerEntreFechas()` | `e.fecha_ingreso, em.id` ← **ASC y sin `e.id`** | `e.fecha_ingreso, e.id` |

     ⚠️ En los dos últimos, sacar `em.id` sin **agregar `e.id`** deja un orden sin desempate único.
     No rompe nada hoy (no se paginan), pero deja un orden no determinista y contradice lo que este
     paso promete. Agregarlo.
   - ⚠️ **No tocar el `ORDER BY em.id` de la carga de materiales de UN equipo** (`WHERE em.equipo_id = ?`):
     ahí es correcto y barato.
   - En `EquipoOtrosDAO`, el hotfix dejó `ORDER BY " + orden + ", m.id"` en `listar()`: sacar el `m.id`.
   - **El plegado no depende de que las filas de un equipo sean contiguas**: usa
     `LinkedHashMap.get(id)`, así que ninguno de los llamadores necesita cambiar su `ORDER BY`.

3. **Arreglar el fallo silencioso que quedó pendiente del Paso 2 del plan viejo.** `EquipoOtrosDAO.listar()`
   todavía tiene `catch (SQLException e) { log.error(...) }` y devuelve la lista a medias: la pantalla
   muestra una lista incompleta como si fuera completa. Pasa a
   `throw new DatabaseException("Error al obtener " + descripcion, e)` (regla dura #8).

4. **⚠️ Arreglar el llamador que rompe con eso — obligatorio, en este mismo paso.**
   `VerEquiposController.abrirDetalleOtros()` (~179-189) llama `obtenerPorId` **síncrono en el EDT**,
   sin `try/catch`, y hoy se apoya en que un fallo devuelve lista vacía → `null` → `return` silencioso.
   Con la tarea 3 sale un `DatabaseException` que **nadie atrapa**: traza en consola, cero aviso.
   - Pasarlo a `TareaUI`, calcado de `HistorialLavaderoController` (~101-107).
   - Esto además cierra una violación de EDT preexistente que `plans/historial-lavadero.md` ya había
     documentado como "no copiar este patrón".
   - ⚠️ **`obtenerPorId` está implementado sobre `listar()`, y uno de sus llamadores es una ruta de
     escritura con guarda de `version`.** Descubierto en revisión:
     `EquipoOtrosCorreccionService.cargarYValidarNuevo` lo llama, y hoy un fallo de lectura ahí se
     convierte en `ValidationException("El equipo no existe")`; con la tarea 3 se convertiría en
     `DatabaseException`. **Eso es tocar una escritura de las diez rutas de Correcciones que
     `CLAUDE.md` marca como a preservar — anti-patrón A10.**
   - **Los cinco llamadores de `listar()`, y qué hacer con cada uno:**

     | Llamador | Hoy | Acción |
     |---|---|---|
     | `obtenerTodos()` | pantallas de consulta, vía `TareaUI` | nada: `siFalla` ya rutea |
     | `obtenerActivos()` | grupo `operativo`, vía `TareaUI` | nada |
     | `obtenerEquiposNuevos()` | vía `TareaUI` | verificar y anotar |
     | `obtenerEntreFechas()` | reportes, vía `TareaUI` (verificado) | nada |
     | `obtenerPorId()` | `VerEquiposController.abrirDetalleOtros` (EDT, a arreglar) **y `EquipoOtrosCorreccionService.cargarYValidarNuevo` (escritura)** | ⚠️ **Parar y preguntarle al usuario.** Cambiar el tipo de excepción de una ruta de Correcciones no es alcance de este plan |

   - Actualizar el javadoc de `listar()`, que hoy documenta la lista parcial como **comportamiento
     intencional** ("es el comportamiento histórico de estos listados"). Si no se toca, queda
     contradiciendo al código.

5. **No extraer una clase compartida entre `EquipoDAO` y `EquipoOtrosDAO`.** Tablas, columnas y
   modelos distintos; sólo comparten la forma, y eso lo garantiza el test, no una abstracción. Es la
   lección que dejó el borrado de `IMaterialFilter`/`ICapacidadCalculator` (`a370e61`).

6. **Tests:**
   - `ultimo_movimiento` da **exactamente** el mismo valor que antes (material con 3 movimientos en
     fechas distintas, en las dos DAO);
   - los materiales siguen saliendo ordenados por id **sin** `m.id`/`em.id` en ningún `ORDER BY` de
     listado (incluido `obtenerPorId`, que no tiene `ORDER BY`);
   - `listar()` **propaga** `DatabaseException` en vez de lista parcial;
   - `obtenerActivos()` devuelve exactamente lo mismo que antes del cambio.

### Verificación

```bash
mvn test -Dtest=EquipoDAOTest
mvn test -Dtest=EquipoOtrosDAOTest
mvn test -Dtest=EquipoOtrosCorreccionServiceIntegrationTest
mvn test
mvn clean package && java -jar target/aptium.jar
```

Smoke: en `Ver Equipos`, doble clic sobre un equipo "otros" abre el detalle con sus materiales; y
con la base caída, muestra un **error visible**, no una traza en consola.

### Criterio de salida

- [ ] No queda ningún `GROUP BY material_id` sin `WHERE` en los DAO de equipos
- [ ] Los cuatro `ORDER BY` de listado de `EquipoDAO` y el de `EquipoOtrosDAO.listar()` quedaron sin
      la clave de material; la carga de materiales de un equipo quedó intacta
- [ ] `listar()` propaga el error; no hay lista parcial
- [ ] `abrirDetalleOtros` corre por `TareaUI` y muestra un error visible si la lectura falla
- [ ] Commit: `perf: el último movimiento sale por índice y los listados propagan sus errores`

---

# FASE C — Paginación

## Paso 7 — Infraestructura de paginación

> Depende del Paso 2. **Paralelo a los Pasos 3-6.** Es la base de los Pasos 8 a 12: nada de UI ni de
> SQL todavía.

### Contexto (autocontenido)

Hoy toda pantalla de consulta recibe una `List<T>` completa y filtra en memoria
(`AbstractFilterController` — 19 líneas: guarda un `cache` y llama `aplicarFiltros()`). Las seis
tablas repintan con `setRowCount(0)` + un `addRow` por fila.

Este paso crea **una sola** forma de representar "una página" y **un solo** componente de UI para
recorrerla, que después usan las cinco pantallas. Cero copiar y pegar es requisito, no aspiración: la
alternativa es cinco implementaciones que se desincronizan.

### Diseño (decidido por el plan, con su porqué)

| Decisión | Por qué |
|---|---|
| `LIMIT ? OFFSET ?` y **no** keyset/cursor | El usuario pidió **pestañas numeradas** de 50. Keyset sólo sabe "siguiente/anterior": no puede saltar a la página 7 ni decir cuántas hay. Con `ORDER BY` indexado (Paso 5) y volúmenes de miles —no millones— el OFFSET profundo no duele. **Escribir en el javadoc que keyset es la salida de emergencia** si alguna vez duele, para que quien la necesite no tenga que redescubrirla |
| El total viaja **en la página** | La UI necesita saber cuántas pestañas dibujar. Devolverlo aparte obliga a coordinar dos llamadas |
| El total se recalcula **sólo cuando cambian los criterios** | Anti-patrón A5. Cambiar de página no cambia el total |
| `Pagina<T>` es un **`record` inmutable** | Regla del repo; y es lo que permite que el controller lo trate igual que trataba al snapshot |
| El tamaño de página es una **constante en `Constantes`**, no un parámetro de usuario | YAGNI. 50 es lo que pidió el usuario; si algún día se vuelve configurable, cambia un lugar |
| `PanelPaginacion` **no sabe de datos**: emite "quiero la página N" | Es la misma separación que `TareaUI` hace entre `leer` y `pintar`. Un componente de UI que dispara lecturas es lo que este repo evita en todos lados |

### Tareas

1. **`common/paginacion/Pagina.java`** — `record` plano, sin Swing, sin JDBC:
   ```java
   public record Pagina<T>(List<T> contenido, int numeroPagina, int tamanioPagina, long totalFilas) {
       public int totalPaginas() { … }   // ceil(total / tamanio), mínimo 1
       public boolean estaVacia()  { … }
       public static <T> Pagina<T> unica(List<T> todo) { … }  // para paginación en memoria (Paso 12)
   }
   ```
   - `contenido` se copia con `List.copyOf` en el constructor compacto (inmutabilidad, regla del repo).
   - ⚠️ **`totalPaginas()` de una lista vacía es 1, no 0.** Una UI que dibuja "página 1 de 0" es un
     bug visible. Test explícito.

2. **`common/paginacion/CriteriosPagina.java`** — `record (int numeroPagina, int tamanioPagina)` con
   una factoría `primera()`. Es lo que viaja del controller al service.

3. **`common/paginacion/PaginadorEnMemoria.java`** — clase plana que parte una `List<T>` ya filtrada
   en una `Pagina<T>`. La usa el Paso 12. **Su javadoc dice explícitamente que NO alivia la base**
   (anti-patrón A3) y para qué sirve entonces: cortar el pintado a 50 filas.

4. **`ui/common/PanelPaginacion.java`** — componente Swing reusable:
   - muestra las pestañas/botones de página y "mostrando X-Y de Z";
   - expone `setAlCambiarPagina(IntConsumer)`; **no lee nada**;
   - `mostrar(Pagina<?> p)` actualiza el estado visible;
   - con una sola página, **se oculta entero** (`setVisible(false)`): una barra de paginación sobre
     12 filas es ruido;
   - con muchas páginas, ventana deslizante de números (p. ej. `« 1 … 5 6 [7] 8 9 … 40 »`), no 40
     botones.
   - ⚠️ **Al cambiar de página no se toca el scroll de la tabla**: la tabla se repinta desde arriba.
     Es lo esperado y evita el bug de "cambié de página y quedé a mitad".

5. **`Constantes`** — `TAMANIO_PAGINA = 50` en la sección que corresponda, con un comentario que diga
   de dónde sale el número (pedido del usuario).

6. **Tests** (`PaginaTest`, `PaginadorEnMemoriaTest`):
   - `totalPaginas()` con 0, 1, 49, 50, 51 filas (los bordes son donde se rompe);
   - `Pagina` es inmutable: mutar la lista de origen después de construirla no la cambia;
   - `PaginadorEnMemoria` con la última página incompleta;
   - pedir una página **más allá del total** devuelve una página vacía con el total correcto, **no**
     lanza: el usuario puede tener la página 7 abierta cuando otro operador borra filas.
   - `PanelPaginacion` queda sin test (es Swing): la convención del repo es extraer la lógica a la
     clase plana, y eso es `Pagina`/`PaginadorEnMemoria`.

### Verificación

```bash
mvn test -Dtest=PaginaTest
mvn test -Dtest=PaginadorEnMemoriaTest
mvn test
```

### Criterio de salida

- [ ] `Pagina`, `CriteriosPagina` y `PaginadorEnMemoria` son clases planas sin Swing ni JDBC
- [ ] `PanelPaginacion` no lee datos: sólo emite el número de página pedido
- [ ] Los bordes (0, 1, 49, 50, 51 filas; página fuera de rango) están testeados
- [ ] `PaginadorEnMemoria` dice en su javadoc que no alivia la base
- [ ] Commit: `feat: infraestructura de paginación (Pagina, CriteriosPagina, PanelPaginacion)`

---

## Paso 8 — Historial de Lavadero: capa de datos paginada

> Depende de los Pasos 5 y 7. Sin UI: este paso termina con un DAO y un service que devuelven
> `Pagina<IngresoHistorial>` y con tests, y **nadie los llama todavía**.

### Contexto (autocontenido)

`HistorialLavaderoDAO.obtenerHistorial()` (189) hace **cuatro** consultas secuenciales, cada una con
su propia conexión: `leerResumen()` (una fila por ingreso desde `0af8f43`), `agruparPorIngreso()` dos
veces (elementos y lavarropas) y `contarBolsas()`. Cruza todo en memoria por `ingreso_id`.

El filtrado vive **entero en memoria**, en `HistorialFilterStrategy` (leer el archivo completo, son
~75 líneas). Cinco filtros:

| Filtro | Semántica hoy | Traducción a SQL |
|---|---|---|
| Cliente | `contains` insensible a mayúsculas sobre `clienteNombre` | `LOWER(c.nombre) LIKE ?` con `%x%`. ⚠️ **No usa índice.** Es aceptable: los predicados selectivos son fecha y estado, que sí lo usan |
| Estados | lista vacía pasa; si no, `estado.name()` en la lista | `il.estado IN (?, ?, …)` armado con **tantos `?` como elementos**, nunca concatenando valores |
| Fechas | `fechaIngreso.toLocalDate()` en `[desde, hasta]`, extremo nulo = abierto | `il.fecha_ingreso >= ?` / `< ?`. ⚠️ **`hasta` es inclusivo por día**: en SQL va `< hasta + 1 día`, no `<= hasta`, o se pierden los ingresos de ese mismo día después de medianoche |
| Fecha nula | pasa **sólo si los dos extremos son nulos** | ⚠️ **`ingresos_lavadero.fecha_ingreso` ES nullable** — verificado en `V7__lavadero.sql` (`TIMESTAMP DEFAULT CURRENT_TIMESTAMP`, sin `NOT NULL`); lo mismo `equipos.fecha_ingreso` y `equipo_otros.fecha_ingreso`. Así que esta rama **hay que traducirla, no borrarla**. En SQL, `NULL >= ?` es `NULL` y no pasa, que coincide cuando hay algún extremo; sin extremos no hay `WHERE` y pasa. La traducción es directa, pero **tiene que estar escrita y testeada**, no deducida |
| Elemento | algún nombre de `elementos()` contiene el texto | `EXISTS (SELECT 1 FROM elementos_clasificacion_lavadero ecl JOIN catalogo_elementos_lavadero cel ON … WHERE ecl.ingreso_id = il.id AND LOWER(cel.nombre) LIKE ?)` |
| Lavarropas | `lavarropas().contains(n)` | `EXISTS (…)` sobre `elementos_ciclo_lavadero` + `ciclos_lavadero` |

⚠️ **Regla de oro de este paso (anti-patrón A4): si un solo filtro queda en memoria, la paginación da
resultados incorrectos.** Los cinco van a SQL o ninguno.

⚠️ **La regla del `fechaIngreso == null`:** hoy un ingreso sin fecha pasa el filtro **sólo si los dos
extremos son nulos**. En SQL, `NULL >= ?` es `NULL` (no pasa), así que la semántica coincide cuando
hay algún extremo — pero **hay que verificar si la columna es realmente nullable** y, si no lo es,
borrar esa rama del `FilterStrategy` en vez de traducirla.

### Tareas

1. **`HistorialLavaderoDAO.obtenerPagina(FiltroHistorial filtro, CriteriosPagina criterios)`**:
   - un `record FiltroHistorial` con los cinco filtros (mismos campos que `HistorialFilterCriteria`,
     pero en la capa de datos; **no reusar el de UI**: el de UI tiene tipos de Swing y este paso no
     puede depender de la UI);
   - un método privado que arma `WHERE` + parámetros a partir del filtro. **Fragmentos constantes,
     valores por `?`** (regla dura #10). Devuelve `(String where, List<Object> params)`.
   - ⚠️ **Sub-paso obligatorio primero: partir `SQL_RESUMEN` en dos constantes.** Hoy **ya termina en
     su propio `ORDER BY`**, así que concatenarle un `WHERE` después produce SQL inválido. Separar
     cuerpo (`SELECT … FROM … JOIN …`) y orden (`ORDER BY il.fecha_ingreso DESC, il.id DESC`), y
     dejar el javadoc diciendo por qué están separadas.
   - el resumen paginado: `cuerpo` + `where` + `orden` + `LIMIT ? OFFSET ?`;
   - **los tres agregados se acotan a los ids de la página**: `WHERE ecl.ingreso_id IN (…)` con tantos
     `?` como filas trajo la página (≤ 50). ⚠️ **Ése es el verdadero ahorro de este paso**: hoy esas
     tres consultas barren la historia entera. Si quedan sin acotar, la paginación ahorra el
     transporte pero no el trabajo del servidor.
   - **una sola conexión para las cuatro consultas.** Hoy son cuatro checkouts (hallazgo #9); con el
     `SELECT 1` eliminado en el Paso 2 el costo bajó, pero cuatro siguen siendo tres de más. Pasar la
     `Connection` a los métodos privados, como ya hacen `DerivadorIngresoCDE` y los helpers de equipos.

2. **`contarHistorial(FiltroHistorial filtro)`** — `SELECT COUNT(*)` sobre `ingresos_lavadero JOIN
   clientes` con el **mismo `WHERE`**, armado por el **mismo método privado**.
   - ⚠️ **Que el `WHERE` lo arme un solo método no es estilo: es corrección.** Si el conteo y la
     página usan `WHERE` distintos, la UI dice "127 resultados" y muestra otra cosa, y nadie lo nota
     hasta que un operador cuenta a mano.

3. **`HistorialLavaderoService`** — expone `obtenerPagina(filtro, criterios)` devolviendo
   `Pagina<IngresoHistorial>`, y `contar(filtro)`. Valida y delega; **cero JDBC** (regla dura #4).
   - Dejar `obtenerHistorial()` **en su lugar** hasta que el Paso 9 migre al llamador. Borrarlo acá
     rompe `UiCoordinator` y vuelve este paso no paralelizable.

4. **Tests** (`HistorialLavaderoDAOTest`, H2) — el paso vive o muere acá:
   - la página 1 de 50 trae 50 y el total es el correcto;
   - la última página incompleta;
   - **cada uno de los cinco filtros por separado** da exactamente el mismo conjunto que
     `HistorialFilterStrategy` sobre el listado completo — ése es el test que prueba que la traducción
     a SQL no cambió la semántica, y es el más importante del paso;
   - los cinco filtros **combinados**;
   - `hasta` inclusivo: un ingreso de las 23:59 del día `hasta` **entra**;
   - `contarHistorial` y `obtenerPagina` coinciden para el mismo filtro;
   - los agregados (elementos, lavarropas, bolsas) de la página 2 corresponden a los ingresos de la
     página 2, no a los de la 1;
   - un filtro que no matchea nada devuelve página vacía con total 0 y **no** lanza.
   - ⚠️ **Antes de tocar nada, correr los 4 tests existentes de `obtenerHistorial()`**: son la red de
     seguridad y tienen que seguir pasando **sin modificarse**.

### Verificación

```bash
mvn test -Dtest=HistorialLavaderoDAOTest
mvn test -Dtest=HistorialLavaderoServiceTest
mvn test
```

### Criterio de salida

- [ ] Los cinco filtros están en SQL; **ninguno** quedó en memoria
- [ ] El `WHERE` de la página y el del conteo salen del **mismo** método
- [ ] Los agregados están acotados a los ids de la página
- [ ] `obtenerPagina` usa **una sola** conexión para sus cuatro consultas
- [ ] Existe el test que compara filtro-SQL contra `HistorialFilterStrategy`, para los cinco
- [ ] Los 4 tests existentes de `obtenerHistorial()` pasan **sin modificarse**
- [ ] `EXPLAIN` de la consulta paginada pegado en el commit, mostrando `idx_ingresos_lav_fecha_ingreso`
- [ ] Commit: `feat: HistorialLavaderoDAO devuelve páginas con los filtros resueltos en SQL`

---

## Paso 9 — Historial de Lavadero: UI paginada

> Depende del Paso 8. Toca `UiCoordinator`: **serializar con el Paso 11**, o hacerlos juntos.

### Contexto (autocontenido)

`HistorialLavaderoController` extiende `AbstractFilterController<IngresoHistorial>`: recibe el
snapshot completo en `pintar(List<IngresoHistorial>)`, lo guarda en `cache` y filtra en memoria en
cada cambio de filtro. Su grupo de refresco es `refresco-historial-lavadero`
(`UiCoordinator:310-316`), y es el **único** consumidor de esos datos — por eso es la pantalla más
fácil de migrar y por eso va primera.

Comportamiento que hay que preservar (de `CLAUDE.md`): al entrar (`componentShown`) se resetean los
filtros al default **sin notificar** (`silenciandoCallback`) y se relee; el combo de estados entra con
`PENDIENTE`, `CLASIFICADO`, `LAVADO` marcados y `FINALIZADO` **des**marcado; doble clic abre
`DetalleHistorialDialog`, que lee el detalle bajo demanda por `TareaUI`.

### Diseño (decidido por el plan, con su porqué)

| Decisión | Por qué |
|---|---|
| **Cambiar un filtro vuelve a la página 1** | Anti-patrón A9. Quedarse en la página 7 después de filtrar muestra una página vacía sobre un resultado que sí tiene filas, y el operador concluye que no hay nada |
| El total se pide **sólo** cuando cambian los filtros | Anti-patrón A5 |
| `HistorialLavaderoController` **deja de extender `AbstractFilterController`** | Su `cache` ya no existe: no hay lista completa que cachear. Dejar la herencia con un `cache` vacío es peor que sacarla — invita a que alguien lo vuelva a llenar |
| El refresco (F5 / botón) **conserva la página y los filtros actuales** | El botón "Actualizar" existe para ver datos frescos de lo que estoy mirando, no para mandarme al principio. La guarda de refresco de esta pantalla no aplica: Historial no acumula estado |
| Sigue pasando por `RefrescadorPantallas` | Mantiene el debounce de 150 ms, que con paginación importa **más**: el usuario puede clickear tres pestañas seguidas |

### Tareas

1. **`PantallaHistorialLavadero`** — agregar el `PanelPaginacion` debajo de la tabla y cablear
   `setAlCambiarPagina`.

2. **`HistorialLavaderoController`**:
   - deja de extender `AbstractFilterController`; guarda `FiltroHistorial filtroActual` y
     `int paginaActual` (estado mutable de controller ⇒ **sólo EDT**, regla dura #2);
   - `aplicarFiltros()` pasa a pedir la página 1 con los filtros nuevos;
   - `alCambiarPagina(n)` pide la página n **con los mismos filtros**;
   - `pintar(Pagina<IngresoHistorial>)` vuelca contenido + estado del `PanelPaginacion`;
   - borrar `HistorialFilterStrategy` y `HistorialFilterCriteria` **si quedan sin uso** — verificar
     con `grep`, no asumir. Regla del repo: no dejar clases muertas. **Su test se borra junto**, y el
     commit lo dice.
     ⚠️ **Sólo si el Paso 8 dejó el test de equivalencia**, que es lo que preserva la semántica que
     ese `FilterStrategy` documentaba. Si no está, no se borra nada.

3. **`UiCoordinator`** — el refrescador de `historial lavadero` pasa a leer una **página**. El lector
   ya no es `service::obtenerHistorial` sino un lambda que toma el filtro y la página **actuales del
   controller**.
   - ⚠️ **`RefrescadorPantallas` lee con un `Supplier<T>` sin parámetros**, y el filtro y la página
     viven en el controller. El lector tiene que **leerlos en el momento de lanzar**, no capturarlos
     al construirse. Capturarlos congela la primera página para siempre.
   - ⚠️ **Y ese `Supplier` corre en el hilo de fondo**, mientras filtro y página son estado de
     controller que sólo se toca en el EDT (regla dura #2). **Resolverlo explícitamente**: el
     controller publica un snapshot inmutable de `(filtro, página)` cuando cambia —en el EDT— y el
     lector lee esa referencia `volatile`. **No** leer los campos del controller desde el hilo de
     fondo, y **no** llamar al panel de Swing desde ahí.

4. **Tests:**
   - cambiar un filtro pide la página 1 (no la actual);
   - cambiar de página conserva el filtro;
   - el refresco conserva filtro y página;
   - el snapshot `(filtro, página)` que consume el lector es el último publicado por el EDT.

5. **Smoke manual:** entrar a Historial (filtros al default, sin FINALIZADO), pasar tres páginas,
   filtrar por cliente (vuelve a la página 1), F5 (conserva página y filtro), doble clic en una fila
   de la página 3 (abre el detalle correcto).

### Verificación

```bash
mvn test -Dtest=HistorialLavaderoControllerTest
mvn test
mvn clean package && java -jar target/aptium.jar   # SIN -Daptium.edt.strict=true
```

### Criterio de salida

- [ ] La pantalla trae 50 filas por página; el log muestra `refresco-historial-lavadero` mucho más rápido
- [ ] Cambiar un filtro vuelve a la página 1; cambiar de página conserva el filtro; F5 conserva las dos
- [ ] Ninguna lectura de filtro/página ocurre fuera del EDT (smoke corrido **con**
      `-Daptium.edt.strict=true` para este punto, sabiendo que los autocompletados de otras pantallas
      van a lanzar y que eso es esperado)
- [ ] `HistorialFilterStrategy` y su test quedaron borrados, o está escrito por qué no
- [ ] Commit: `feat: el Historial de Lavadero se lee y se muestra de a 50`

---

## Paso 10 — CDE: capa de datos paginada (Ver Equipos + Estado de Procesos)

> Depende de los Pasos 5, 6 y 7. **Hacer el Paso 6 antes**: tocan los mismos archivos.
> Sin UI. Es el paso más grande del plan.

### Contexto (autocontenido)

⚠️ **El acoplamiento que define este paso:** `UiCoordinator.crearRefrescadorHistorialEquipos()`
(268-283) crea **un** `RefrescadorPantallas<HistorialEquipos>` cuyo `repartir` alimenta **dos**
pantallas:

```java
Consumer<HistorialEquipos> repartir = datos -> {
    cde.pintar(datos);          // EstadoProcesosController  → PantallaVerCDEv2
    verEquipos.pintar(datos);   // VerEquiposController      → PantallaVerEquipos
};
```

`HistorialEquipos` es un record `(List<Equipo> equipos, List<EquipoOtros> equiposOtros)` leído por
`LectorHistorialEquipos`, que llama a los dos `obtenerTodos()`.

Y las dos pantallas muestran **el histórico completo**, no la cola activa:

- `VerEquiposController` tiene **dos tablas** (ortopedias y otros, `PantallaVerEquipos` 207 y 223),
  **no extiende** `AbstractFilterController` (tiene sus propios `todosOrtopedia`/`todosOtros`/`cargado`)
  y filtra por estados, cliente, profesional, paciente, institución, tipo de ingreso y rango de fechas
  (`aplicarFiltros` ~113-140).
- `EstadoProcesosController` concatena las dos listas y las manda a `PantallaVerCDEv2.actualizarTabla`,
  que delega en un **`PanelEquipoMaterial`: un `JSplitPane` con DOS tablas** (equipos arriba,
  materiales del equipo seleccionado abajo). Extiende `AbstractFilterController` y filtra por cliente,
  institución y estados (`CdeFilterStrategy`). Su javadoc dice explícitamente: *"Come del histórico
  completo, no de la cola activa: la pantalla deja filtrar por ENTREGADO"*.

**Consecuencia inevitable: no se puede paginar una de las dos sin la otra.** Si Ver Equipos pasa a
páginas, el snapshot `HistorialEquipos` deja de existir y Estado de Procesos se queda sin datos. Por
eso los Pasos 10 y 11 tratan a las dos juntas.

### ⚠️ Tres hechos verificados en revisión que contradicen la primera versión de este paso

1. **Estado de Procesos SÍ muestra materiales.** La primera redacción decía "es una tabla plana" y
   apostaba a que el `UNION ALL` no necesitaba los materiales. Es falso: `PanelEquipoMaterial` carga
   los materiales del equipo seleccionado **desde el objeto en memoria**
   (`modeloMateriales.cargarMateriales(eq)`), sin ir a la base. **La consecuencia es buena, no mala:**
   la página tiene que traer los 50 equipos **con sus materiales**, y entonces seleccionar una fila
   sigue sin costar una consulta. Es exactamente lo que hace el enfoque de dos viajes de la tarea 3.

2. **El filtro por estado del CDE es sobre un valor DERIVADO**, no sobre la columna:
   `CdeFilterStrategy` filtra por `eq.calcularEstado().getNombre()`, que es el **mínimo** de los
   estados de los materiales con dos ramas de excepción (REMITO sin filas, sin materiales).

3. **Y la tabla se ORDENA por ese valor derivado**, no por fecha: `EquipoTableModel.actualizarDatos`
   ordena por `calcularEstado().getOrden()` ascendente ("más atrasado primero"). O sea: **el orden de
   pantalla de Estado de Procesos no es `fecha_ingreso DESC`**. Paginar por fecha y dejar que el
   `TableModel` reordene **dentro de cada página de 50** rompe el orden global — es el anti-patrón A4
   aplicado al orden.

**Lo que vuelve tratables el 2 y el 3, y es el hallazgo que hace viable este paso:**
`EquipoOtrosMaterialHelper.recalcularEstadoEquipo` (y su gemelo de ortopedias) calcula
`MIN(CASE estado … END)` sobre los materiales y lo **persiste en la columna `estado` del agregado**,
con `version = version + 1`. Es **el mismo valor** que `calcularEstado()` computa en memoria. Por lo
tanto la columna `estado` —ya mantenida, y ya indexada por la V23— sirve para filtrar **y** para
ordenar, sin replicar la lógica de dominio en SQL.

⚠️ **Eso es una hipótesis fuerte y no se acepta sin probarla.** Las ramas de excepción de
`calcularEstado()` (REMITO sin materiales, equipo sin materiales) pueden divergir del fallback a
`NUEVO` que hace el helper cuando el `MIN` es `NULL`. **La tarea 2 lo convierte en un test, y si el
test falla, este paso se frena y se pregunta.**

### Diseño (decidido por el plan, con su porqué)

| Decisión | Por qué |
|---|---|
| **Ver Equipos: dos paginadores, uno por tabla.** Cada uno pagina su DAO | Son dos tablas visualmente separadas con dos modelos distintos. Un paginador compartido obligaría a unir en SQL dos consultas de esquemas distintos para después volver a separarlas en la UI: trabajo extra para un resultado peor |
| **Estado de Procesos: `UNION ALL` de las dos tablas, pero sólo para elegir los 50 ids de la página** | Es la única forma honesta de ordenar y paginar una lista que mezcla las dos fuentes. La unión selecciona sólo lo que hace falta para **ordenar y paginar** (`id`, `tipo`, `estado`, `fecha_ingreso`); el detalle de esos 50 —incluidos los materiales— viene en dos consultas por `IN (…)`, una por tabla |
| **Estado de Procesos sigue trayendo materiales** — con la página | ⚠️ **Corrige la primera versión de este plan, que apostaba a que no los mostraba.** Los muestra. Traerlos acotados a los 50 de la página es barato y **preserva** que seleccionar una fila no dispare ninguna consulta, que es como funciona hoy |
| **El filtro y el orden por estado usan la COLUMNA `estado`, no `calcularEstado()`** | La columna ya es ese valor: `recalcularEstadoEquipo` lo persiste. Replicar el `MIN(CASE …)` en la consulta de listado duplicaría lógica de dominio en SQL, que es justo lo que el repo evita. **Con un test que pruebe la equivalencia fila por fila** (tarea 2) |
| El `ORDER BY` de la unión es `orden_estado ASC, fecha_ingreso DESC, tipo ASC, id DESC` | `orden_estado` **primero** porque es el orden que la pantalla muestra hoy (`EquipoTableModel` ordena por `getOrden()` ascendente) y hay que preservarlo. `tipo` e `id` desempatan para que el orden sea **total y determinista**: sin eso, dos filas iguales pueden alternar entre páginas y una fila aparece dos veces o ninguna |
| **`EquipoTableModel` deja de ordenar** en la ruta del CDE | Si sigue ordenando, reordena **dentro de la página** y rompe el orden global (anti-patrón A4 aplicado al orden). El orden pasa a venir de SQL, que es el único lugar desde el que puede ser global |
| El `UNION ALL` va en un **DAO propio** (`CdeConsultaDAO`), no en `EquipoDAO` ni en `EquipoOtrosDAO` | Cruza dos features. Meterlo en cualquiera de los dos DAO le da a esa feature conocimiento de la otra. Es la misma razón por la que `DerivadorIngresoCDE` vive solo |
| **No** se extrae una abstracción común entre `EquipoDAO` y `EquipoOtrosDAO` | Misma lección que el Paso 6 |

### Tareas

1. **Releer los "tres hechos verificados" del Contexto.** La tarea cero de la primera versión de este
   plan (*"verificar si `PantallaVerCDEv2` muestra materiales"*) **ya está respondida: sí los
   muestra**, y el diseño de arriba ya lo incorpora. No volver a investigarlo.

2. **⚠️ Tarea cero real, y es una compuerta: probar que la columna `estado` equivale a
   `calcularEstado()`.** Test en H2 que, sobre un conjunto sembrado que incluya **los casos de
   excepción** (equipo sin materiales, REMITO sin materiales, materiales en estados mezclados),
   compare `equipo.getEstado()` (la columna) contra `equipo.calcularEstado()` para **cada** fila, en
   las dos tablas.
   - **Si el test pasa**, todo el diseño de arriba se sostiene y el paso sigue.
   - **Si el test falla**, el paso se frena: filtrar y ordenar por la columna daría un resultado
     distinto del que la pantalla muestra hoy. **Parar y preguntarle al usuario** si se corrige el
     helper (que es tocar una escritura, anti-patrón A10) o si se replica `calcularEstado()` en SQL.
   - Este test se queda en la suite para siempre: es lo que impide que la divergencia vuelva.

3. **`EquipoDAO.obtenerPagina(FiltroEquipos, CriteriosPagina)` y `contar(FiltroEquipos)`**, y lo mismo
   en **`EquipoOtrosDAO`**:
   - `record FiltroEquipos` con estados, cliente, profesional, paciente, institución, tipo de ingreso
     y rango de fechas. Los campos que no apliquen a "otros" (profesional, paciente, institución) se
     **ignoran** en ese DAO — y eso se documenta, porque hoy la UI los aplica a los dos y en "otros"
     siempre dan vacío, lo que los hace aparecer cuando el campo está en blanco. **Preservar esa
     semántica exactamente**, no "arreglarla": es el comportamiento que el operador conoce.
   - `WHERE` y conteo salen del **mismo** método privado (misma razón que el Paso 8).
   - ⚠️ **El plegado equipo+materiales choca con `LIMIT`.** Una fila por (equipo × material) significa
     que `LIMIT 50` corta **materiales**, no equipos. **La salida son dos viajes**: primero los 50 ids
     ordenados y paginados, después el detalle de esos 50 con `IN (…)`.
     ⚠️ **La alternativa "elegante" NO existe en MySQL:**
     `WHERE eo.id IN (SELECT id FROM … ORDER BY … LIMIT ? OFFSET ?)` falla con
     `ERROR 1235: This version of MySQL doesn't yet support 'LIMIT & IN/ALL/ANY/SOME subquery'`.
     Sólo funciona envuelta en una tabla derivada (`IN (SELECT * FROM (SELECT … LIMIT ?) t)`), que es
     más frágil y no más rápida. **Documentarlo en el javadoc** — es exactamente el tipo de cosa que
     alguien "simplifica" de vuelta y rompe sin que ningún test de caso lo note.
     **Test obligatorio:** un equipo con 12 materiales en el borde de página aparece **una vez, con
     sus 12 materiales**.

4. **`features/equipos/dao/CdeConsultaDAO.java`** (nuevo) — mismo patrón de dos viajes:
   - viaje 1: `UNION ALL` de `equipos` y `equipo_otros` proyectando sólo `id`, `tipo`, el
     `orden_estado` (el `CASE` sobre la columna `estado`, **compartido como constante SQL** con
     `recalcularEstadoEquipo` si es posible, o duplicado con un comentario que apunte al original) y
     `fecha_ingreso`; `WHERE` de los filtros; `ORDER BY orden_estado, fecha_ingreso DESC, tipo, id DESC`;
     `LIMIT ? OFFSET ?`;
   - viaje 2: el detalle **con materiales** de esos ids, una consulta por tabla, reusando las de
     `EquipoDAO`/`EquipoOtrosDAO`;
   - se reordena en memoria según el orden del viaje 1 (son 50 elementos).
   - `contar` con el **mismo** `WHERE`, del mismo método.

5. **Services** — `EquipoService`, `EquipoOtrosService` y un `CdeConsultaService` exponen las páginas.
   Cero JDBC. Dejar los `obtenerTodos()` en su lugar: los borra el Paso 11.

6. **Tests** — la misma forma que el Paso 8, en las tres DAO:
   - equivalencia filtro-SQL contra los filtros en memoria actuales (`aplicarFiltros` de
     `VerEquiposController` y `CdeFilterStrategy`), **filtro por filtro**;
   - bordes de página con equipos de muchos materiales;
   - la unión ordena estable: recorrer todas las páginas devuelve cada equipo **exactamente una vez**
     — test con fechas repetidas a propósito;
   - conteo y página coinciden;
   - `hasta` inclusivo, igual que en el Paso 8.

### Verificación

```bash
mvn test -Dtest=EquipoDAOTest
mvn test -Dtest=EquipoOtrosDAOTest
mvn test -Dtest=CdeConsultaDAOTest
mvn test
```

### Criterio de salida

- [ ] **La compuerta de la tarea 2 pasó**: la columna `estado` equivale a `calcularEstado()` para
      todas las filas, incluidos los casos de excepción — y el test queda en la suite
- [ ] Todos los filtros **y el orden** de las dos pantallas están en SQL; nada quedó en memoria
- [ ] `EquipoTableModel` ya no reordena en la ruta del CDE
- [ ] La página de Estado de Procesos trae los materiales, y seleccionar una fila **no** dispara
      ninguna consulta (como hoy)
- [ ] Un equipo con muchos materiales en el borde de página sale una vez y completo (test)
- [ ] Recorrer todas las páginas de la unión devuelve cada equipo exactamente una vez (test con
      fechas repetidas)
- [ ] `EXPLAIN` de las tres consultas paginadas pegado en el commit
- [ ] Commit: `feat: capa de datos paginada para Ver Equipos y Estado de Procesos`

---

## Paso 11 — CDE: disolver el grupo `historialEquipos` y cablear las dos pantallas

> Depende del Paso 10. Toca `UiCoordinator`: **serializar con el Paso 9**, o hacerlos juntos.

### Contexto (autocontenido)

Leer el contexto del Paso 10 antes que éste: el acoplamiento del grupo `refresco-historial-equipos`
es el problema que este paso resuelve.

### Diseño (decidido por el plan, con su porqué)

| Decisión | Por qué |
|---|---|
| El grupo `historialEquipos` **se disuelve en dos grupos**, uno por pantalla | El grupo existía para que las dos pantallas vieran el mismo snapshot y quedaran coherentes entre sí. Con paginación no hay snapshot común: cada una pide su página con sus filtros. Un grupo que reparte dos páginas distintas a dos pantallas distintas no es un grupo, es dos |
| **Se pierde la coherencia entre las dos pantallas.** Aceptado | Nunca se miran juntas: son dos cards del `CardLayout`, sólo una está visible. La coherencia que el grupo garantizaba era invisible. **Escribirlo en `CLAUDE.md`**, que hoy documenta cinco grupos |
| `VerEquiposController` **pierde el flag `cargado`** | Existía porque `aplicarFiltros()` podía correr antes del primer snapshot. Con páginas, cada `aplicarFiltros()` **es** una lectura: no hay estado "todavía no cargué" que proteger |
| `LectorHistorialEquipos` y el record `HistorialEquipos` se **borran** si quedan sin uso | No dejar clases muertas. Verificar con `grep`, no asumir |

### Tareas

1. **`PantallaVerEquipos`** — un `PanelPaginacion` por tabla (dos), cada uno con su propio
   `setAlCambiarPagina`.
2. **`PantallaVerCDEv2`** — un `PanelPaginacion` debajo de su tabla única.
3. **`VerEquiposController`** — dos pares `(filtro, página)`, uno por tabla; `aplicarFiltros()` pide la
   página 1 de las dos; sin `cargado`. Mismo patrón de snapshot inmutable `volatile` que el Paso 9
   (tarea 3): el estado se publica en el EDT y el lector de fondo lee la referencia.
   - ⚠️ **`aplicarFiltroInicial()` de `PantallaVerCDEv2` tiene que pasar a ser un filtro de la
     consulta — y hay un javadoc que dice explícitamente lo contrario. Hay que refutarlo por escrito,
     no ignorarlo.** El javadoc de `aplicarFiltroInicial()` (y el de `EstadoProcesosController` que lo
     repite) dice: *"Es un default de la vista, **no** un `WHERE`: los datos llegan completos, así que
     destildar ENTREGADO en el combo los trae de vuelta. **Si el filtro viviera en la consulta, esa
     opción del combo quedaría vacía para siempre y nadie lo notaría.**"*
     **Por qué el argumento deja de aplicar bajo paginación:** el combo no se puebla del snapshot —
     se puebla de `EstadoEquipo.values()` — y destildar ENTREGADO ahora **dispara una consulta nueva**,
     que los trae. El riesgo que el javadoc describía era el de un combo poblado de datos; no es éste.
     **Por qué hay que cambiarlo igual:** como filtro de vista sobre una página de 50, mostraría 8
     filas de 50 y el operador creería que hay 8 — anti-patrón A4 disfrazado de default.
     **Los dos javadoc se reescriben en el mismo commit.** Dejarlos contradiciendo al código es peor
     que el bug que evitaban.
4. **`EstadoProcesosController`** — deja de extender `AbstractFilterController`; pide páginas del
   `CdeConsultaService`.
5. **`UiCoordinator`** — reemplazar `crearRefrescadorHistorialEquipos()` por dos refrescadores,
   `refresco-ver-equipos` y `refresco-cde`, cada uno con su lector de página.
6. **Limpieza** — borrar `LectorHistorialEquipos`, `HistorialEquipos`, `CdeFilterStrategy`,
   `CdeFilterCriteria` y sus tests **si quedan sin uso**. Verificar con `grep`. Los mismos dos
   cuidados del Paso 9: sólo si existe el test de equivalencia del Paso 10, y el borrado va en el
   mensaje del commit.
7. **`CLAUDE.md`** — la tabla de grupos de refresco pasa de cinco a seis, y la sección de disparadores
   de relectura tiene que decir qué hace el botón "Actualizar" en una pantalla paginada (conserva
   filtros y página).
8. **Tests:** cambiar un filtro vuelve a la página 1 en las dos tablas de Ver Equipos; las dos tablas
   paginan independientemente; el default de Estado de Procesos filtra en SQL y el total refleja el
   filtro, no el universo.

### Verificación

```bash
mvn test
mvn clean package && java -jar target/aptium.jar
```

**Smoke manual:** Ver Equipos (las dos tablas paginan solas, los filtros vuelven a página 1, doble
clic abre el detalle correcto en la página 3); Estado de Procesos (entra sin entregados y el contador
lo refleja, se puede sacar el filtro y aparecen, el botón Actualizar conserva la página).

### Criterio de salida

- [ ] Las dos pantallas del CDE paginan; ningún filtro quedó en memoria
- [ ] `aplicarFiltroInicial()` es un filtro de consulta, no de vista
- [ ] El grupo `historialEquipos` ya no existe; hay dos grupos nuevos
- [ ] `LectorHistorialEquipos`, `HistorialEquipos` y los `FilterStrategy` del CDE están borrados, o
      está escrito por qué no
- [ ] `CLAUDE.md` refleja los seis grupos y el comportamiento del botón en pantallas paginadas
- [ ] Commit: `feat: Ver Equipos y Estado de Procesos se leen de a 50`

---

## Paso 12 — Paginación en memoria: Ver Lotes y Ver Ciclos

> Depende del Paso 7. Paralelo a los Pasos 8-11, **salvo que toca `UiCoordinator`**: coordinar con 9 y 11.

### Contexto (autocontenido)

`VerLotesController` y `VerCiclosController` extienden `AbstractFilterController` y filtran en memoria
sobre snapshots completos (`HistorialLotes` y `List<CicloLavadero>`). Sus volúmenes tienen **techo
natural**: los lotes y los ciclos crecen por jornada de trabajo, no por ítem procesado.

⚠️ **Anti-patrón A3: esto NO alivia la base.** Las dos siguen leyendo todo. Lo que arregla es el
**pintado**: hoy `PantallaVerLotes:179` y `PantallaVerCiclos:122` hacen un `addRow` por fila en el EDT.
**El mensaje del commit tiene que decirlo así**, no vender un ahorro que no existe.

### Tareas

1. Agregar `PanelPaginacion` a las dos pantallas.
2. Los controllers siguen con `AbstractFilterController` (el cache completo sigue existiendo y es
   correcto); lo único nuevo es que después de `filterStrategy.filter(...)` pasan el resultado por
   `PaginadorEnMemoria` y pintan **una página**.
3. **Cambiar un filtro vuelve a la página 1**, igual que en las paginadas por SQL. La regla tiene que
   ser la misma en las cinco pantallas o el operador aprende dos comportamientos distintos.
   ⚠️ **Anti-patrón A15: el reset NO puede vivir dentro de `aplicarFiltros()`.**
   `AbstractFilterController.recargarCache` llama a `aplicarFiltros()` **en cada refresco**, así que
   el reset ahí mandaría a la página 1 en cada F5 y haría imposible la tarea 4. El reset cuelga del
   **callback de cambio de filtro** de la pantalla (`setOnFiltrosChanged`), que es lo único que
   dispara sólo cuando el operador tocó un filtro.
   **Esto vale igual para los Pasos 9 y 11**, donde el controller ya no extiende
   `AbstractFilterController` pero la trampa es la misma: `pintar` no debe resetear la página.
4. El refresco conserva filtros y página.
5. **Tests:** filtrar vuelve a la página 1; cambiar de página no relee de la base (verificable con un
   doble del service que cuente llamadas — es lo que distingue este paso del 9 y del 11 y hay que
   dejarlo probado); la última página incompleta se pinta bien.

### Verificación

```bash
mvn test -Dtest=VerLotesControllerTest
mvn test -Dtest=VerCiclosControllerTest
mvn test
mvn clean package && java -jar target/aptium.jar
```

### Criterio de salida

- [ ] Las dos pantallas muestran 50 filas por página
- [ ] Cambiar de página **no** dispara una lectura (test con doble contador)
- [ ] El tiempo de `pintar` de las dos bajó respecto del baseline del Paso 3 (número anotado)
- [ ] El mensaje del commit dice que esto arregla el pintado y **no** la carga de la base
- [ ] Commit: `perf: Ver Lotes y Ver Ciclos pintan de a 50 (paginación en memoria)`

---

## Paso 13 — Medir, revisar y cerrar

> Depende de todos.

### Tareas

1. **Volver a medir**, en las mismas pantallas y el mismo orden que el Paso 3, **tres corridas,
   mediana** (anti-patrón A7), y llenar la columna "post Fase C".
   - **Umbral explícito:** se considera que un paso movió la aguja si la mediana bajó **≥ 30 %** o
     **≥ 300 ms**, lo que se alcance primero. Por debajo de eso no se declara mejora.
   - Lo que no mejoró se anota en "Mutaciones aplicadas" **con la hipótesis**. Un paso que no movió la
     aguja es información.
   - ⚠️ **La fila `refresco-historial-equipos` del baseline ya no existe:** el Paso 11 disolvió ese
     grupo en `refresco-ver-equipos` y `refresco-cde`. Comparar **cada una de las dos** contra el
     mismo número de baseline, y decir que es así — no sumarlas, que daría una mejora inventada por
     dividir el trabajo en dos lecturas que ahora no ocurren juntas.

2. **Verificar que el problema original se fue**, que es distinto de "mejoró":
   - F5 mantenido en Historial + `SHOW PROCESSLIST` ⇒ a lo sumo una consulta viva (repetir el smoke
     del Paso 4 ahora que hay paginación);
   - abrir las cinco pantallas de consulta en ráfaga ⇒ el log **no** muestra "Pool bajo presión";
   - cortar Tailscale a mitad de una lectura ⇒ la app muestra un error **en ≤ 60 s** y sigue usable,
     no se cuelga. **Éste es el test que valida el Paso 2 y es el que no se puede automatizar.**

3. **`/code-review high`** sobre el diff completo de la rama contra `8b662b8`. Aplicar CRITICAL y
   HIGH; anotar los MEDIUM que se decida no tocar.

4. **`mvn verify`** y revisar JaCoCo: `Pagina`, `PaginadorEnMemoria`, `ConexionesSupervisadas` y los
   métodos nuevos de DAO en 80 %+. Las clases Swing quedan sin cubrir: es la convención del repo.

5. **Documentación:**
   - `CLAUDE.md`: sección nueva sobre paginación (qué pantalla pagina en SQL y cuál en memoria, y
     **por qué no todas igual**), la regla "cambiar un filtro vuelve a la página 1", los seis grupos
     de refresco, `ConexionesSupervisadas` y la excepción del `cancelador-sql`;
   - `docs/conexion-remota-mysql-tailscale.md`: los timeouts y qué síntoma corresponde a cada uno;
   - `README-DEPLOY.md`: el aviso de que la V23 corta el arranque de los puestos desactualizados;
   - memoria del proyecto: una entrada nueva con los números de antes y después.

6. **Cerrar los dos planes:** marcar éste como CERRADO arriba de todo con los SHA de cada paso, y
   confirmar el bloque de superseded en `rendimiento-historiales.md`.

7. **Preguntarle al usuario** si la rama se mergea a `main` directo y si quiere desplegar el Paso 2
   por separado y antes que el resto (es lo que yo recomendaría: es el arreglo de disponibilidad y no
   depende de nada).

### Criterio de salida

- [ ] La columna "post Fase C" está llena con medianas de 3
- [ ] Las tres verificaciones del punto 2 están hechas, incluido el corte de red a mano
- [ ] `/code-review high` corrido; CRITICAL y HIGH aplicados
- [ ] `mvn verify` en verde y las clases nuevas en 80 %+
- [ ] `CLAUDE.md`, los dos docs y la memoria actualizados
- [ ] Los dos planes cerrados
- [ ] Commit: `docs: cierre del plan de conexiones y paginación`

---

## Sesiones de ejecución

Diez sesiones. **El orden de abajo es secuencial y es el que hay que seguir si trabajás solo**, que es
el caso normal. El grafo de dependencias permite paralelizar, y donde aplica está anotado en la
sesión: si alguna vez corrés dos agentes en worktrees separados, las Sesiones 4 y 5 son
independientes de la 3 y entre sí.

**Reglas que valen para todas:**

- **Un commit por paso**, con el mensaje que dice su criterio de salida. No cerrar la sesión con
  trabajo a medias en el árbol.
- **Terminar con `mvn test` en verde** y los invariantes por paso verificados.
- **No entrar en el último 20 % de la ventana de contexto** con un paso a medio hacer. Las sesiones
  marcadas 🔴 son las que tienen riesgo real de llegar ahí; cada una dice dónde está su corte limpio.
- **`/fast` sólo donde lo dice.** Es Opus con salida más rápida, no un modelo menor: sirve para
  edición mecánica, no para diseño de concurrencia ni para SQL nuevo.
- **Leer siempre, antes de tocar nada:** del plan, "Contexto compartido" y el "Catálogo de
  anti-patrones" (A1-A15). Son la mitad del valor del plan y son lo que evita repetir los 12
  hallazgos que la revisión ya encontró.

| Sesión | Pasos | Modelo | Esfuerzo | Fast | Riesgo |
|---|---|---|---|---|---|
| 1 | 1 + 2 | **Opus 5** | alto | no | — |
| 2 | 3 | Sonnet 5 | medio | sí | — |
| 3 | 4 | **Opus 5** | alto | **no** | 🔴 |
| 4 | 5 + 6 | Sonnet 5 | medio | sí | — |
| 5 | 7 | **Opus 5** | alto | no | — |
| 6 | 8 + 9 | **Opus 5** | alto | no | 🔴 |
| 7 | 10 | **Opus 5** | alto | **no** | 🔴 |
| 8 | 11 | **Opus 5** | alto | no | 🔴 |
| 9 | 12 | Sonnet 5 | medio | sí | — |
| 10 | 13 | **Opus 5** | alto | no | — |

---

### Sesión 1 — Pasos 1 y 2: rama y timeouts

**Opus 5 · effort alto · sin fast mode**

> Es el arreglo de disponibilidad y **se despliega solo**, antes que todo el resto. El Paso 1 es
> trivial y va acá para no gastarle una sesión a crear una rama.

```
Ejecutá los Pasos 1 y 2 de plans/conexiones-y-paginacion.md (crear la rama ConexionesYPaginacion;
después timeouts de red, keepalive, dimensionamiento del pool y arranque diagnosticable).

Leé antes del plan: "Contexto compartido", "Diagnóstico" y el "Catálogo de anti-patrones" completo.
Leé ConnectionPool.java entero (son ~411 líneas) y el PASO 1/4 de app/App.java.

El árbol tiene sin commitear el archivo del plan. Ese es el primer commit del Paso 1: no lo stashees
ni lo borres.

Cinco cosas que el plan corrige respecto de lo que parece obvio, y que la revisión adversarial ya
verificó contra el código:

- Las TRES constantes de timeout (CONNECT_TIMEOUT_MS, SOCKET_TIMEOUT_MS, TIMEOUT_CONSULTA_S) viven
  en ConnectionPool desde este paso, aunque la tercera todavía no la use nadie. Si la ponés en
  ConexionesSupervisadas, que se crea recién en el Paso 4, este paso no compila su propio test.
- socketTimeout (60 s) tiene que ser MAYOR que queryTimeout (30 s), y connectTimeout (5 s)
  estrictamente MENOR que el connectionTimeout de Hikari (10 s). Las dos relaciones van atadas por
  test, no sólo por comentario: es la única forma de que sobrevivan a un cambio distraído.
- El diagnóstico "sale NoClassDefFoundError sin el mensaje original" es FALSO.
  ExceptionInInitializerError sí lleva la causa. El problema real son dos: es un Error y el
  catch(Exception) de App no lo agarra, y el primer toque de la clase es getStats(), que con el pool
  caído devuelve "Pool no inicializado" y DEJA SEGUIR hasta que Flyway recibe un DataSource null.
  Por eso verificarArranque() tiene que ser la PRIMERA sentencia del PASO 1/4 de App.main.
- maximumPoolSize sólo BAJA (anti-patrón A8). Son varios puestos remotos, cada uno con su pool: N×8
  va contra max_connections del servidor.
- Sacar connectionTestQuery NO ahorra un round-trip (isValid() también manda un COM_PING, y Hikari
  saltea la validación dentro de su aliveBypassWindow de 500 ms). Escribí la justificación correcta
  o el Paso 13 le va a atribuir una mejora que no produjo.

La tarea 3 necesita que corras consultas contra el MySQL de producción (max_connections,
Threads_connected, Max_used_connections y los COUNT(*) de las ocho tablas). Si no tenés acceso en
esta sesión, pedímelas y anotá los números cuando los tengas: el Paso 5 los necesita.

Cerrá con los dos smokes: arrancar con base alcanzable, y arrancar con Tailscale apagado (tiene que
salir un diálogo con la causa real, no una NPE).
```

---

### Sesión 2 — Paso 3: instrumentación y baseline

**Sonnet 5 · effort medio · fast mode ok**

> Bloqueante de toda la Fase C: sin baseline ningún paso posterior puede demostrar que sirvió.
> Es la sesión con más trabajo manual tuyo (tres corridas por pantalla).

```
Ejecutá el Paso 3 de plans/conexiones-y-paginacion.md (cronometrar leer y pintar en TareaUI,
loguear la salud del pool bajo presión, y tomar el baseline).

Leé antes del plan: "Contexto compartido" y el "Catálogo de anti-patrones". Leé TareaUI.java al
menos hasta done().

Tres cosas puntuales:

- Cronometrar PINTAR no es opcional. Las seis tablas hacen setRowCount(0) + un addRow por fila en el
  EDT, y si eso es más del 30 % del total, la paginación gana por ahí y no por SQL. Es la hipótesis
  que la Fase C necesita confirmar antes de invertir en el Paso 10, que es el más caro del plan.
- El blindaje de getStats() va para el caso del pool CERRADO (dataSource != null pero el MXBean sí
  es null), no para el de testDataSource: en ése getStats() ya retorna temprano con "Pool no
  inicializado". El plan lo tenía al revés en su primera versión.
- Anti-patrón A7: tres corridas por pantalla, se anota la MEDIANA, se descarta la primera apertura
  de la app. Una sola medición mezcla el JIT y el arranque del pool con lo que se quiere medir.

El sembrador sintético (tarea 4) es OPCIONAL y sólo si te lo pido. Si lo hacés, sus tres guardas de
seguridad no se negocian: un sembrador que se equivoca de base destruye datos de producción.

Cuando tengas los números, evaluá la compuerta de la tarea 7 y decímela explícitamente: si el
pintado pasa el 30 %, o si alguna de las dos pantallas que van a paginación SQL tiene menos de ~500
filas. No la escondas detrás de un número lindo.
```

---

### Sesión 3 — Paso 4: cancelación real y techo de concurrencia 🔴

**Opus 5 · effort alto · sin fast mode**

> **La sesión más delicada del plan.** Es diseño de concurrencia con un proxy JDBC de por medio, y
> cuatro de los hallazgos de la revisión adversarial caen acá. Corte limpio: no hay — es un solo
> commit. Si vas corto de contexto, arrancá de nuevo en vez de cerrar a medias.

```
Ejecutá el Paso 4 de plans/conexiones-y-paginacion.md (cancelación de ejecución real y techo de
lecturas concurrentes).

Leé antes del plan: "Contexto compartido", el "Catálogo de anti-patrones" COMPLETO (sobre todo A2,
A12, A13 y A14, que son de este paso), el hallazgo #15 del Diagnóstico, y el Paso 4 entero incluida
su tabla de Diseño. Leé además TareaUI.java entero, RefrescadorPantallas.java entero,
TransactionalConnection.java, ControlConcurrencia.java y EdtGuard.java.

Este paso tiene cuatro trampas, y las cuatro las encontró la revisión adversarial verificando contra
el código. Ninguna es opinable:

1. queryTimeout SOLO en conexiones con autoCommit = true. innodb_lock_wait_timeout es 50 s por
   default, así que un techo de 30 s le gana SIEMPRE a las tres guardas FOR UPDATE del lavadero y
   devuelve ER_QUERY_INTERRUPTED (1317), que ControlConcurrencia.esContencionDeLock NO reconoce
   (sólo mapea 1213, 1205 y 50200). El choque entre operadores dejaría de salir como
   "alguien se te adelantó" y saldría como error técnico. TransactionalConnection ya pone
   autoCommit=false en su constructor, así que la condición es exacta y no hace falta ningún flag.
   NO agregues 1317 a esContencionDeLock: eso es tocar una escritura (A10). Si te parece que hace
   falta, pará y preguntame.

2. El registro de sentencias va por TOKEN DE TAREA, no por Thread. SwingWorker reusa sus 10 hilos y
   RefrescadorPantallas.refrescarAhora() cancela enVuelo incondicionalmente aunque esa tarea ya haya
   terminado (el campo nunca se pone en null). Con registro por hilo, cancelar una tarea muerta
   mataría la consulta VIVA de otra pantalla que heredó ese hilo. Y el valor es un Set<Statement>,
   no un Statement: un DAO transaccional tiene varios abiertos a la vez y un hilo puede tener dos
   conexiones.

3. El semáforo va DENTRO de ConnectionPool.getConnection(), no alrededor de leer(). TareaUI envuelve
   tareas que no tocan la base — ajustes-descargar-actualizacion y arranque-descargar-actualizacion
   bajan el fat JAR y tardan minutos. Alrededor de leer consumirían permisos de conexión que no usan
   y dejarían esperando a registrar-estado-confirmar. Usá tryAcquire con timeout, no acquire():
   cancel(false) no interrumpe una espera, así que un acquire bloqueado es un cuelgue silencioso.

4. cancel() de Connector/J ABRE UNA CONEXIÓN NUEVA para mandar KILL QUERY: es I/O, y cancelar() se
   llama desde el EDT. Va despachado al ejecutor daemon "cancelador-sql". Es la segunda excepción de
   la app a "no hay new Thread() fuera de TareaUI" y tiene que quedar justificada en su javadoc y en
   CLAUDE.md, no colada.

Dos detalles que ahorran una depuración larga: el proxy tiene que despachar POR NOMBRE de método
(para cubrir todos los overloads y prepareCall), manejar unwrap devolviendo el proxy y no el real, y
manejar equals/hashCode/toString explícitamente. Y setQueryTimeout en H2 2.x es a nivel SESIÓN, no
de Statement: el test tiene que leerlo de vuelta con getQueryTimeout(), no asumirlo.

Actualizá el javadoc de TareaUI, que hoy afirma lo contrario de lo que el paso deja.

El test que NO puede faltar: cancelar un Handle cuya tarea YA TERMINÓ no toca la sentencia viva de
otra tarea que heredó el mismo hilo. Es el único que detecta A13; ninguno de los otros lo haría.

Cerrá con el smoke que prueba el paso: F5 mantenido en Historial ~5 s, y después SHOW PROCESSLIST en
el servidor. Tiene que quedar a lo sumo UNA consulta de historial corriendo.
```

---

### Sesión 4 — Pasos 5 y 6: índices y subconsultas de movimientos

**Sonnet 5 · effort medio · fast mode ok**

> Dos carriles independientes entre sí y del Paso 4. Van juntos porque los dos son SQL acotado y
> cortos. Corte limpio entre los dos: son commits separados.

```
Ejecutá los Pasos 5 y 6 de plans/conexiones-y-paginacion.md (migración V23 con los índices de fecha
y estado; después sacar las tablas derivadas que agregan toda la tabla de movimientos).

Leé antes del plan: "Contexto compartido" y el "Catálogo de anti-patrones". Leé EquipoDAO.java y
EquipoOtrosDAO.java.

Paso 5 — tres cosas:
- La migración es V23, NO V21. V21 y V22 ya existen. El plan viejo (rendimiento-historiales.md) dice
  V21 y está obsoleto en ese punto (anti-patrón A11).
- socketTimeout del Paso 2 SE APLICA A FLYWAY. Un CREATE INDEX que tarde más de 60 s se aborta del
  lado del cliente, Flyway deja una fila fallida en flyway_schema_history y TODO arranque posterior
  falla en TODOS los puestos hasta un repair manual. Mirá los COUNT(*) que tomó el Paso 2 antes de
  desplegar; si alguna tabla es grande, decidilo conmigo antes, no cuando falle.
- No declares que el índice se usa: pegá el EXPLAIN en el commit (anti-patrón A6). Si dice
  "Using filesort", el índice no está pagando lo que el paso promete: anotalo en "Mutaciones
  aplicadas" en vez de declarar la victoria.

Paso 6 — cuatro cosas:
- El hotfix cbf3cb3 mató el N+1 de EquipoOtrosDAO pero REINTRODUJO la tabla derivada mv en
  SQL_CABECERA. Es eso lo que hay que sacar, más las DOS copias del mismo patrón en EquipoDAO.
- Los cuatro ORDER BY de listado de EquipoDAO están en la tabla del paso, y DOS de ellos
  (obtenerEquiposNuevos y obtenerEntreFechas) no tienen e.id. Sacar em.id sin AGREGAR e.id deja un
  orden sin desempate único. No toques el ORDER BY em.id de la carga de materiales de UN equipo.
- obtenerPorId está implementado sobre listar(), y uno de sus llamadores es
  EquipoOtrosCorreccionService.cargarYValidarNuevo — una RUTA DE ESCRITURA con guarda de version.
  Cambiarle el tipo de excepción es tocar una escritura (A10). PARÁ Y PREGUNTAME antes de hacerlo.
- Arreglá VerEquiposController.abrirDetalleOtros en el mismo paso: hoy llama obtenerPorId síncrono
  en el EDT y se apoya en que un fallo devuelva null. Con la propagación, nadie lo atrapa.

Un commit por paso.
```

---

### Sesión 5 — Paso 7: infraestructura de paginación

**Opus 5 · effort alto · sin fast mode**

> Sesión corta pero de diseño: lo que salga de acá lo usan cinco pantallas. Es barato hacerlo bien
> ahora y caro corregirlo en el Paso 12.

```
Ejecutá el Paso 7 de plans/conexiones-y-paginacion.md (Pagina, CriteriosPagina, PaginadorEnMemoria,
PanelPaginacion).

Leé antes del plan: "Contexto compartido", "La corrección al planteo original del usuario" y el
"Catálogo de anti-patrones". Leé AbstractFilterController.java (son 19 líneas) y FilterStrategy.java.

Es infraestructura: NO toques ningún controller, ninguna pantalla ni ningún DAO en este paso.

Cuatro cosas que el plan decide y conviene no re-litigar:
- LIMIT/OFFSET y no keyset, porque el usuario pidió pestañas NUMERADAS y keyset no sabe saltar a la
  página 7 ni cuántas hay. Escribí en el javadoc que keyset es la salida de emergencia si el OFFSET
  profundo alguna vez duele, para que quien la necesite no tenga que redescubrirla.
- El total viaja EN la página. La UI necesita saber cuántas pestañas dibujar.
- totalPaginas() de una lista vacía es 1, no 0. Una UI que dibuja "página 1 de 0" es un bug visible.
- Pedir una página más allá del total devuelve página vacía con el total correcto y NO lanza: el
  operador puede tener la página 7 abierta cuando otro borra filas.

PanelPaginacion no lee datos: emite "quiero la página N" y nada más. Es la misma separación que
TareaUI hace entre leer y pintar.

Testeá los bordes (0, 1, 49, 50, 51 filas) — es donde esto se rompe. PanelPaginacion queda sin test:
es Swing, y la convención del repo es que la lógica testeable vive en la clase plana.

El javadoc de PaginadorEnMemoria tiene que decir explícitamente que NO alivia la base (anti-patrón
A3): sirve para cortar el pintado a 50 filas y nada más.
```

---

### Sesión 6 — Pasos 8 y 9: Historial de Lavadero 🔴

**Opus 5 · effort alto · sin fast mode**

> Es la primera pantalla que pasa a paginación SQL, y la más fácil de las dos: un solo grupo de
> refresco y un solo consumidor. **Corte limpio después del Paso 8** (la capa de datos es un commit
> entero y autónomo); si vas corto de contexto, cerrá ahí.

```
Ejecutá los Pasos 8 y 9 de plans/conexiones-y-paginacion.md (HistorialLavaderoDAO paginado con los
filtros en SQL; después la UI paginada).

Leé antes del plan: "Contexto compartido", "La corrección al planteo original del usuario", el
"Catálogo de anti-patrones" y los Pasos 8 y 9 enteros. Leé HistorialLavaderoDAO.java entero,
HistorialFilterStrategy.java entero (son ~75 líneas y es el contrato que tenés que preservar),
HistorialLavaderoController.java y RefrescadorPantallas.java.

Paso 8 — la regla de oro: los CINCO filtros van a SQL o ninguno. Si uno queda en memoria, la
paginación da resultados incorrectos (filtra 50 de 5000, no las 50 primeras de las que matchean).
Es el anti-patrón A4 y es el que arruina el paso entero sin que ningún test obvio lo note.

Cuatro detalles verificados que te ahorran el viaje:
- SQL_RESUMEN YA TERMINA en su propio ORDER BY. Concatenarle un WHERE después da SQL inválido:
  partila en cuerpo y orden como primer sub-paso.
- ingresos_lavadero.fecha_ingreso ES nullable (V7: TIMESTAMP DEFAULT CURRENT_TIMESTAMP, sin NOT
  NULL). Así que la rama "fecha nula pasa sólo si los dos extremos son nulos" de cumpleFechas hay
  que TRADUCIRLA, no borrarla.
- "hasta" es inclusivo por día: en SQL va < hasta + 1 día, no <= hasta, o se pierden los ingresos de
  ese mismo día después de medianoche.
- Los tres agregados (elementos, lavarropas, bolsas) tienen que acotarse a los ids de la página con
  IN (...). Ese es el verdadero ahorro del paso: hoy barren la historia entera. Si quedan sin acotar,
  ahorrás el transporte pero no el trabajo del servidor.

El WHERE de la página y el del conteo salen del MISMO método privado. No es estilo: si divergen, la
UI dice "127 resultados" y muestra otra cosa, y nadie lo nota hasta que alguien cuenta a mano.

El test que prueba el paso: cada uno de los cinco filtros, por separado y combinados, da exactamente
el mismo conjunto que HistorialFilterStrategy sobre el listado completo. Y los 4 tests existentes de
obtenerHistorial() tienen que seguir pasando SIN MODIFICARSE.

Paso 9 — dos trampas:
- RefrescadorPantallas lee con un Supplier SIN parámetros, y ese Supplier corre en el HILO DE FONDO,
  mientras filtro y página son estado de controller que sólo se toca en el EDT. El controller publica
  un snapshot inmutable (filtro, página) cuando cambia, en el EDT, y el lector lee esa referencia
  volatile. NO leas los campos del controller desde el hilo de fondo ni llames al panel de Swing
  desde ahí.
- Cambiar un filtro vuelve a la página 1; cambiar de página conserva el filtro; F5 conserva las dos.
  El reset cuelga del callback de cambio de filtro, NO de pintar (anti-patrón A15).

Borrá HistorialFilterStrategy y su test si quedan sin uso — pero SÓLO si dejaste el test de
equivalencia del Paso 8, que es lo que preserva la semántica que ese FilterStrategy documentaba.
Verificá con grep, no asumas.

Un commit por paso.
```

---

### Sesión 7 — Paso 10: capa de datos del CDE 🔴

**Opus 5 · effort alto · sin fast mode**

> **El paso más grande del plan**, y el que la revisión adversarial reescribió entero. Va solo.
> No hay corte limpio intermedio: si vas corto de contexto, cerrá sin commitear y arrancá de nuevo.

```
Ejecutá el Paso 10 de plans/conexiones-y-paginacion.md (capa de datos paginada para Ver Equipos y
Estado de Procesos). SIN UI: este paso termina con DAOs, services y tests, y nadie los llama todavía.

Leé antes del plan: "Contexto compartido", el "Catálogo de anti-patrones", y el Paso 10 ENTERO
incluida su sección "Tres hechos verificados en revisión". Leé EquipoDAO.java, EquipoOtrosDAO.java,
VerEquiposController.aplicarFiltros, CdeFilterStrategy.java, PantallaVerCDEv2.java,
PanelEquipoMaterial.java, EquipoTableModel.java, EquipoOtros.calcularEstado(),
Equipo.calcularEstado() y EquipoOtrosMaterialHelper.recalcularEstadoEquipo.

ANTES DE ESCRIBIR UNA LÍNEA, hacé la compuerta de la tarea 2: un test en H2 que compare la columna
estado contra calcularEstado() para CADA fila, en las dos tablas, incluyendo los casos de excepción
(equipo sin materiales, REMITO sin materiales, materiales mezclados). Si falla, PARÁ Y PREGUNTAME:
todo el diseño del paso depende de que la columna sea ese valor.

Tres cosas que la revisión ya verificó y NO hay que volver a investigar:
- Estado de Procesos SÍ muestra materiales: PantallaVerCDEv2 delega en PanelEquipoMaterial, que es un
  split de DOS tablas y carga los materiales del equipo seleccionado DESDE MEMORIA. La consecuencia
  es buena: la página trae los 50 equipos CON sus materiales y seleccionar una fila sigue sin costar
  una consulta.
- El filtro por estado del CDE es sobre calcularEstado(), un valor DERIVADO, no sobre la columna.
- Y EquipoTableModel.actualizarDatos ORDENA por calcularEstado().getOrden(). O sea que el orden de
  pantalla NO es fecha_ingreso DESC. Si paginás por fecha y dejás que el TableModel reordene dentro
  de cada página, rompés el orden global: es A4 aplicado al orden.

Dos cosas que MySQL no perdona:
- IN (SELECT ... LIMIT ?) NO EXISTE en MySQL: falla con ERROR 1235. Usá dos viajes (primero los 50
  ids ordenados y paginados, después el detalle de esos ids con IN). Documentá por qué en el javadoc:
  es lo primero que alguien va a "simplificar" de vuelta.
- El plegado equipo+materiales choca con LIMIT: una fila por (equipo × material) hace que LIMIT 50
  corte MATERIALES, no equipos. Test obligatorio: un equipo con 12 materiales en el borde de página
  aparece UNA vez y con sus 12 materiales.

El UNION ALL va en un DAO propio (CdeConsultaDAO), no dentro de EquipoDAO ni de EquipoOtrosDAO:
cruza dos features, igual que DerivadorIngresoCDE. Y NO extraigas una abstracción común entre los dos
DAO de equipos: tablas y modelos distintos, sólo comparten la forma.

Preservá exactamente la semántica rara que ya existe: los filtros de profesional, paciente e
institución se aplican hoy también a "otros", donde siempre dan vacío, y por eso los "otros"
aparecen cuando esos campos están en blanco. No lo "arregles": es el comportamiento que el operador
conoce.

Pegá el EXPLAIN de las tres consultas paginadas en el commit.
```

---

### Sesión 8 — Paso 11: UI del CDE y disolución del grupo 🔴

**Opus 5 · effort alto · sin fast mode**

> Toca UiCoordinator y borra clases. **Serializar con la Sesión 6**, que toca el mismo archivo.
> Corte limpio: no hay; es un commit.

```
Ejecutá el Paso 11 de plans/conexiones-y-paginacion.md (cablear las dos pantallas del CDE y disolver
el grupo de refresco historialEquipos).

Leé antes del plan: "Contexto compartido", el "Catálogo de anti-patrones", el CONTEXTO DEL PASO 10
(el acoplamiento del grupo es lo que este paso resuelve) y el Paso 11 entero. Leé
UiCoordinator.crearRefrescadorHistorialEquipos, VerEquiposController.java,
EstadoProcesosController.java, PantallaVerEquipos.java y PantallaVerCDEv2.java.

El grupo refresco-historial-equipos alimenta DOS pantallas desde un snapshot. Con paginación no hay
snapshot común, así que se disuelve en dos grupos. Se pierde la coherencia entre las dos pantallas y
está ACEPTADO: son dos cards del CardLayout, nunca se miran juntas, la coherencia era invisible.
Escribilo en CLAUDE.md, que hoy documenta cinco grupos y van a ser seis.

La trampa de este paso: aplicarFiltroInicial() de PantallaVerCDEv2 tiene que pasar a ser un filtro de
la CONSULTA, y hay un javadoc que dice explícitamente lo contrario ("Es un default de la vista, no un
WHERE... si el filtro viviera en la consulta, esa opción del combo quedaría vacía para siempre").
No lo ignores: el plan lo refuta por escrito (el combo se puebla de EstadoEquipo.values(), no del
snapshot, y destildar ENTREGADO ahora dispara una consulta que los trae). Reescribí ESE javadoc y el
de EstadoProcesosController que lo repite, en el mismo commit. Dejarlos contradiciendo al código es
peor que el bug que evitaban.

Ver Equipos tiene DOS tablas, así que lleva DOS paginadores independientes. Y pierde el flag
"cargado": existía porque aplicarFiltros() podía correr antes del primer snapshot, y ahora cada
aplicarFiltros() ES una lectura.

Mismo patrón de snapshot inmutable volatile que el Paso 9: el estado (filtro, página) se publica en
el EDT y el lector de fondo lee la referencia. Nada de leer campos del controller desde el hilo de
fondo.

EquipoTableModel deja de reordenar en la ruta del CDE: el orden ahora viene de SQL, que es el único
lugar desde el que puede ser global.

Borrá LectorHistorialEquipos, HistorialEquipos, CdeFilterStrategy y CdeFilterCriteria con sus tests
SI quedan sin uso. Verificá con grep. El borrado va en el mensaje del commit.
```

---

### Sesión 9 — Paso 12: paginación en memoria

**Sonnet 5 · effort medio · fast mode ok**

```
Ejecutá el Paso 12 de plans/conexiones-y-paginacion.md (paginación en memoria en Ver Lotes y Ver
Ciclos).

Leé antes del plan: "Contexto compartido", el anti-patrón A3 y el A15, y el Paso 12 entero. Leé
AbstractFilterController.java (19 líneas), VerLotesController.java y VerCiclosController.java.

Esto NO alivia la base: las dos pantallas siguen leyendo todo. Lo que arregla es el PINTADO, que hoy
es un addRow por fila en el EDT. El mensaje del commit tiene que decirlo así — no vendas un ahorro
que no existe (anti-patrón A3).

Los controllers SIGUEN extendiendo AbstractFilterController: el cache completo existe y es correcto.
Lo único nuevo es que después de filterStrategy.filter(...) pasan el resultado por PaginadorEnMemoria
y pintan una página.

La trampa: el reset de página NO puede vivir dentro de aplicarFiltros(). AbstractFilterController.
recargarCache llama a aplicarFiltros() en CADA refresco, así que el reset ahí mandaría a la página 1
en cada F5 y haría imposible "el refresco conserva la página". Colgalo del callback de cambio de
filtro (setOnFiltrosChanged).

Cambiar un filtro vuelve a la página 1, igual que en las pantallas paginadas por SQL: la regla tiene
que ser la misma en las cinco o el operador aprende dos comportamientos distintos.

El test que distingue este paso del 9 y del 11: cambiar de página NO dispara una lectura. Verificalo
con un doble del service que cuente llamadas.

Anotá el tiempo de pintado de las dos contra el baseline del Paso 3.
```

---

### Sesión 10 — Paso 13: medir, revisar y cerrar

**Opus 5 · effort alto · sin fast mode**

```
Cerrá plans/conexiones-y-paginacion.md ejecutando su Paso 13.

1. Volvé a medir las mismas pantallas que el Paso 3, en el mismo orden, tres corridas, mediana
   (anti-patrón A7). Umbral: una mejora se declara sólo si bajó >= 30 % o >= 300 ms. Lo que no
   mejoró se anota en "Mutaciones aplicadas" CON la hipótesis: un paso que no movió la aguja es
   información.
   OJO: la fila refresco-historial-equipos del baseline YA NO EXISTE (el Paso 11 disolvió ese grupo).
   Compará cada una de las dos nuevas contra el mismo número de baseline y decí que es así. No las
   sumes: daría una mejora inventada.

2. Verificá que el problema ORIGINAL se fue, que es distinto de "mejoró". Los tres:
   - F5 mantenido en Historial + SHOW PROCESSLIST ⇒ a lo sumo una consulta viva;
   - abrir las cinco pantallas de consulta en ráfaga ⇒ el log NO dice "Pool bajo presión";
   - cortar Tailscale a mitad de una lectura ⇒ error en <= 60 s y la app sigue usable, no se cuelga.
     Este último es el que valida el Paso 2 y es el único que no se puede automatizar.

3. /code-review high sobre el diff completo de la rama contra 8b662b8. Aplicá CRITICAL y HIGH; anotá
   los MEDIUM que decidas no tocar.

4. mvn verify y revisá JaCoCo: Pagina, PaginadorEnMemoria, ConexionesSupervisadas y los métodos
   nuevos de DAO en 80 %+. Las clases Swing quedan sin cubrir: es la convención del repo.

5. Documentación: CLAUDE.md (paginación por pantalla y por qué no todas igual, la regla del reset de
   página, los seis grupos de refresco, ConexionesSupervisadas, la exención transaccional del
   queryTimeout y el cancelador-sql), docs/conexion-remota-mysql-tailscale.md (los timeouts y qué
   síntoma es cuál), README-DEPLOY.md (el corte de arranque que provoca la V23), y la memoria del
   proyecto con los números de antes y después.

6. Marcá este plan como CERRADO arriba de todo con los SHA de cada paso, y confirmá el bloque de
   superseded en plans/rendimiento-historiales.md.

7. Preguntame si la rama se mergea a main directo.
```

---

## Protocolo de mutación del plan

- **Dividir un paso** → agregarlo como `Paso N.5` con su propio contexto y criterio de salida.
- **Saltear un paso** → dejar escrito *por qué* en "Mutaciones aplicadas"; no borrarlo.
- **Cambiar una decisión de la tabla de arriba** → tacharla (`~~...~~`) y escribir la nueva con fecha.
  Las decisiones tomadas con el usuario **no se cambian sin preguntarle**.

---

## Mutaciones aplicadas

**2026-09-15 — Revisión adversarial aplicada antes de ejecutar un solo paso.** 4 CRITICAL y 8 HIGH,
todos verificados contra el código y corregidos en el plan. Los que cambiaron una decisión, no sólo
una redacción:

| # | Qué decía el plan | Qué era cierto | Dónde se corrigió |
|---|---|---|---|
| 1 | `setQueryTimeout` global de 30 s | Tapa el `innodb_lock_wait_timeout` de 50 s y rompe las tres guardas `FOR UPDATE` del lavadero: el conflicto entre operadores dejaría de salir como aviso accionable | Hallazgo #15, anti-patrón A12, Paso 4 (exención transaccional) |
| 2 | "Verificar si Estado de Procesos muestra materiales; si los muestra, parar" | **Los muestra** (`PanelEquipoMaterial` es un split de dos tablas). La respuesta estaba a un archivo de distancia y el plan la dejó como tarea | Paso 10, Contexto y Diseño reescritos |
| 3 | Filtrar por `WHERE estado IN (?)` | El filtro **y el orden** del CDE son sobre `calcularEstado()`, un valor derivado; y `EquipoTableModel` **ordena** por él. Lo que lo salva: `recalcularEstadoEquipo` ya persiste ese valor en la columna | Hallazgo #16, Paso 10 (compuerta de equivalencia) |
| 4 | Registro de sentencias por `Thread` | `SwingWorker` reusa hilos y `RefrescadorPantallas` cancela tareas ya terminadas: cancelaría la consulta de otra pantalla | Anti-patrón A13, Paso 4 (token de tarea) |
| 5 | El test del Paso 2 lee `TIMEOUT_CONSULTA_S` de una clase que crea el Paso 4 | El Paso 2 no compilaría | Paso 2 (las tres constantes viven en `ConnectionPool`) |
| 6 | "Sale `NoClassDefFoundError` sin el mensaje original" | `ExceptionInInitializerError` sí lleva la causa; el problema es que es un `Error` y que `getStats()` deja seguir hasta una NPE de Flyway | Hallazgo #4, Paso 2 (`verificarArranque()`) |
| 7 | (no lo veía) | `socketTimeout` se aplica a Flyway: un `CREATE INDEX` largo envenena `flyway_schema_history` en **todos** los puestos | Paso 5, tarea 2 |
| 8 | Semáforo alrededor de `leer` | Estrangularía las descargas del auto-update, que tardan minutos y no tocan la base | Anti-patrón A14, Paso 4 (semáforo en el checkout) |
| 9 | `IN (SELECT … LIMIT ?)` "MySQL 8 la soporta" | MySQL la rechaza con `ERROR 1235` | Paso 10, tarea 3 |
| 10 | Reset de página en `aplicarFiltros()` | `recargarCache` lo llama en cada refresco ⇒ cada F5 mandaría a la página 1 | Anti-patrón A15, Paso 12 tarea 3 |

También se corrigieron: siete referencias de línea corridas (ahora se cita por símbolo), la
justificación falsa de sacar `connectionTestQuery`, el choque entre `connectTimeout` y
`connectionTimeout`, dos `ORDER BY` de `EquipoDAO` sin desempate, `SQL_RESUMEN` que ya termina en su
`ORDER BY`, el llamador de escritura de `obtenerPorId`, `fecha_ingreso` confirmada nullable,
`setQueryTimeout` a nivel sesión en H2, y la fila de baseline que desaparece con el Paso 11.
