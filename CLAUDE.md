# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Instrucciones del usuario

Explicar todos los cambios minimizando el consumo de tokens.
Al buscar debilidades arquitectónicas: ser crítico y explicar *por qué* son problemas. Si el código no tiene debilidades graves, decirlo — es una respuesta válida.
Al añadir nuevas funcionalidades, priorizar la preservación de la buena arquitectura y el código limpio y legible.
Ante cualquier duda de diseño o sobre cómo proceder con un cambio, preguntar.

## Build y ejecución

```bash
mvn clean package                                        # genera target/aptium.jar (fat JAR)
mvn test                                                 # tests unitarios
mvn verify                                               # tests + reporte de cobertura JaCoCo
mvn test -Dtest=NombreDeClase                            # un solo test
mvn test -Dtest=NombreDeClase#nombreDelMetodo            # un método específico
```

Configuración de BD (precedencia descendente):
1. Variables de entorno: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASS`
2. Archivo `config.properties` (buscado en `/etc/aptium/`, `C:\Aptium\`, raíz del proyecto)
3. Defaults hardcodeados (solo desarrollo, emite warning en log)

Ver `config.example.properties` como referencia.

## Arquitectura

Aplicación de escritorio Swing (Java 17) para gestión de equipos médicos y lotes de esterilización. Sin framework de DI — todo se cablea manualmente en el arranque.

**Flujo de arranque** (`App.main`):
1. `ConnectionPool` — HikariCP singleton, crea la BD si no existe
2. `DatabaseInitializer` — ejecuta schema.sql + seeds
3. `AppContext.createDefault()` — instancia todos los DAOs, Services y Strategies
4. `AppController` → `UiCoordinator` → `PantallaPrincipal` (CardLayout)

Si cualquier paso falla, aparece un diálogo de error y la app termina.

**Capas por feature** (en `features/`):
```
model → dao (DAO<T,ID>) → service → view/controller
```
Features: `equipos/ortopedias`, `equipos/otros`, `lavadero`, `lotes`, `autoclaves`, `catalogo`, `clientes`, `instituciones`, `profesionales`, `ajustes`, `actualizaciones`.

**Clases clave:**
- `AppContext` — único lugar donde se construyen dependencias (new DAO, new Service, new Strategy)
- `UiCoordinator` — único punto de la UI que ve el `AppContext` completo: instancia todos los controllers pasándole a cada uno **solo los services de su alcance**, cablea listeners, y crea un `Runnable` global de refresh que todos disparan al guardar datos
- **Regla de extensión:** un controller declara en su constructor los services que usa. No hay fachada intermedia — si necesita algo nuevo, se agrega un parámetro y `UiCoordinator` lo provee desde `AppContext`. Así el alcance de cada controller es visible en su firma y el compilador lo hace cumplir.
- `Constantes` — todas las constantes de la app (nombres de pantallas para CardLayout, anchos de columnas, etc.)
- `AptiumException` y subclases — jerarquía de excepciones del dominio

**Navegación UI:** `PantallaPrincipal` usa `CardLayout`; los nombres de los paneles están en `Constantes.Pantallas.*`.

## Ortopedias vs. Otros

Son dos tipos de equipo con modelos, tablas y flujos distintos pero comparten la misma máquina de estados. `RegistrarEstadoController` los maneja polimórficamente mediante `EquipoRegistrableInterface` (discrimina con `getTipo()`).

| | Ortopedias | Otros |
|---|---|---|
| Modelo | `Equipo` / `Material` | `EquipoOtros` / `MaterialOtros` |
| Tablas | `equipos`, `equipo_materiales` | `equipo_otros`, `equipo_otros_materiales` |
| Catálogo | `catalogo_descripciones` (códigos fijos) | `catalogo_otros` (crece con el uso) |
| Materiales | Identificados por código numérico | Texto libre → se auto-crea entrada en `catalogo_otros` |
| Tipo de ingreso | Único | DETALLES (por ítem) o REMITO (bulto con ID `ddmmaaaa-{id}`) |
| Datos extra | nroProfesional, pacienteNombre, nroInstitucion | Solo cliente |
| De dónde nace | Carga manual | Carga manual **o derivación desde Lavadero** |

**Un ingreso "Otros" puede nacer de dos lugares.** Además de la carga manual, la pantalla de Salidas
de Lavadero deriva ropa ya lavada al CDE: entra con `requiereLavado = false` (ya se lavó, así que
`calcularSiguienteEstado(NUEVO, false, true)` lo manda directo a EMPAQUETADO) y puede quedar a nombre
del **cliente original** o de **APTIUM**, según lo que se elija al derivar. Ver "Lavadero → CDE".

## Máquina de estados (`EstadoEquipo`)

```
NUEVO → LAVANDO → LAVADO → EMPAQUETADO → ESTERILIZANDO → ESTERILIZADO → ENTREGADO
```

Los equipos pueden saltear LAVANDO y/o EMPAQUETADO según los flags `requiereLavado` / `requiereEmpaque`. La lógica de transición válida está en `IEstadoValidator` / `EstadoValidatorImpl`.

## Lavadero — ciclo de vida de un ingreso

```
PENDIENTE → CLASIFICADO → LAVADO → FINALIZADO
```
(`EstadoIngresoLavadero`, persistido en `ingresos_lavadero.estado`)

| Paso | Pantalla | Qué lo dispara |
|---|---|---|
| `PENDIENTE` | Ingreso | se registra la ropa cruda (bolsas + peso) |
| `CLASIFICADO` | Clasificación | se detalla qué hay dentro (`elementos_clasificacion_lavadero`) |
| `LAVADO` | Ciclos | **todas** las cantidades clasificadas pasaron por un ciclo finalizado |
| `FINALIZADO` | Salidas | **todas** tienen destino asignado (`SalidaLavaderoDAO`, `SQL_FINALIZAR_INGRESO`) |

**"Listo" no es un estado del ingreso.** Secado y doblado se registran **por cantidad** en
`salidas_lavadero`, no en `ingresos_lavadero`: marcar Listo crea la fila de salida con
`destino = NULL`, y `NULL` significa "lista, sin destino todavía" — un estado legítimo, no un dato
faltante. Un mismo ingreso puede tener parte de su ropa lista y parte todavía en un lavarropas.

## Lavadero — Historial

Pantalla de **consulta de sólo lectura** (botón "Historial" del menú de Lavadero, hoy grilla 2×3).
Tabla maestra de ingresos con filtros; **doble clic → `DetalleHistorialDialog`** con la trazabilidad
del ingreso (elemento → lavarropas → fecha de lavado → fecha listo → destino). No muta nada.

- **"Fuera del flujo" = ingresos `FINALIZADO`.** El combo de estados entra con `PENDIENTE`,
  `CLASIFICADO`, `LAVADO` marcados y `FINALIZADO` desmarcado. Al entrar (`componentShown`) se
  resetean los filtros al default **sin notificar** (`silenciandoCallback`) y se relee de BD.
- **El detalle se lee bajo demanda por `TareaUI`**, no en el snapshot maestro: traerlo para todos
  los ingresos en cada refresco costaría O(historia completa).
- Es el **quinto grupo de refresco** (`historial lavadero` en `UiCoordinator`): nadie más consume
  esos datos.
- `HistorialLavaderoDAO` cruza clasificación + ciclos + instancias + salidas (aparte de
  `IngresoLavaderoDAO`, igual que `SalidaLavaderoDAO`). `cantBolsas` y los agregados de
  elementos/lavarropas van en consultas separadas: meterlos en el `LEFT JOIN` maestro infla los
  `COUNT`. El detalle se ancla en `elementos_clasificacion_lavadero` (no en salidas) y se reparte
  entre tres consultas sin solaparse; un equipo repartido en N lavarropas es **1** línea
  (`AgrupadorLineasHistorial`, que a diferencia de `AgrupadorInstanciasSalida` no descarta
  instancias incompletas ni marcadas). Texto de lavarropas compartido en `TextoLavarropas`.
- Plan: `plans/historial-lavadero.md`.

## Lavadero → CDE

Es el **único punto donde una feature escribe en las tablas de otra**, y está concentrado en una
sola clase: `DerivadorIngresoCDE` (`lavadero/dao/derivadores/`). Crea un `equipo_otros` con
`requiereLavado = false` vía `EquipoOtrosDAO.guardar(Connection, ...)` **dentro de la transacción de
la derivación**: si la creación del ingreso falla, `salidas_lavadero` queda intacta.

**`AccionSalida` ≠ `DestinoSalida`.** La acción es lo que elige el operador; el destino es lo que se
persiste, y no son 1:1 — `CDE_CLIENTE` y `CDE_APTIUM` guardan el mismo `CDE_OTROS`, y lo que las
diferencia queda en el `nro_cliente` del ingreso creado. La misma clase derivadora sirve a las dos:
sólo cambia el `AsignadorClienteCDE` que recibe.

`SalidasLavaderoController` es la única pantalla de Lavadero cableada al grupo de refresco
`operativo` (y no a `refrescarEquipos`), porque lo que deriva tiene que aparecer en el acto en las
pantallas del CDE.

## Lavadero — fracciones de equipo

Un `Equipo*` de la clasificación de lavadero se puede repartir entre varios lavarropas. Esa identidad se
persiste: tabla `instancias_equipo_ciclo (id, elemento_clasificacion_id, total_partes)` + columna
`instancia_equipo_id` (nullable) en `elementos_ciclo_lavadero` y en `salidas_lavadero` (migraciones
`V19`/`V20`).

**Lanzar es todo o nada.** `CicloLavaderoDAO.lanzarTanda` es la **única** escritura de lanzamiento:
crea las instancias y todos los ciclos de la tanda en una sola transacción. No existe crear una
instancia por separado — si existiera, un fallo a mitad de camino dejaría un equipo con menos
fracciones que su `total_partes`, que Disponibles ya cuenta como consumido y Salidas nunca acepta
como completo (o sea, desaparecido de las dos pantallas). Las líneas viajan con el `instanciaStagingId`
que les puso el controller; el id real de la base lo resuelve el DAO adentro de la transacción.

**Invariante:** un equipo repartido en N lavarropas consume **1** unidad de su línea de
clasificación (no N), genera **1** fila de Salidas y **1** elemento en el ingreso del CDE, y no aparece
en Salidas hasta que las N partes pasaron por un ciclo finalizado. El saldo se calcula
`SUM(cantidad donde instancia IS NULL) + COUNT(DISTINCT instancia_equipo_id)`. La agrupación de
fracciones para Salidas vive en la clase plana `AgrupadorInstanciasSalida`.
`CicloLavaderoDAO.detectarLineasSobregiradas()` delata bases de desarrollo con datos previos a esta
persistencia. Detalle completo: `plans/fracciones-de-equipo-persistidas.md`.

## Patrones e interfaces clave

**Strategies:**
- `IEstadoValidator` (`equipos/ortopedias/service/`) — decide si un material puede avanzar de estado y cuál es el próximo
- `FilterStrategy<T,C>` (`common/util/`) — filtrado genérico de listas

> `IMaterialFilter` e `ICapacidadCalculator` **ya no existen** (borradas en `a370e61`): eran
> interfaces con una sola implementación y ningún segundo caso a la vista. La lógica de filtrado
> vive en `IEstadoValidator` y la de volúmenes en el value object `OcupacionAutoclave`
> (`lotes/model/`). **Regla que dejó:** una strategy sin estado y sin variantes es un tipo del
> dominio, no un service inyectado.

**Validación con builder:**
```java
ValidationException.Builder builder = ValidationException.builder()
    .addErrorIf(condicion, "Mensaje de error");
builder.throwIfHasErrors();
```

**Transacciones:** `TransactionalConnection` (try-with-resources, commit/rollback manual). No hay framework de transacciones.

**Concurrencia — regla dura:** ningún acceso a BD corre en el EDT. `TareaUI` (`ui/common/`) es el
**único** mecanismo de trabajo en fondo de la app: `.leer` hace el I/O, `.pintar` vuelve al EDT,
`.siFalla` maneja el error, `.antes`/`.despues` apagan y reencienden el botón. No hay `new Thread()`
ni `SwingWorker` fuera de esa clase (el único `new Thread()` que queda es el shutdown hook de
`App.java`). `EdtGuard` (`infrastructure/db/`) grita en el log si alguien vuelve a poner I/O en el
EDT, y con `-Daptium.edt.strict=true` lanza en vez de avisar.

*Excepción aceptada:* los **cinco autocompletados por tecla** (`AutocompleteListener` de
clientes/profesionales/instituciones, el `CatalogoLookup` de ortopedias, el de `catalogo_otros` y el
de clientes de `LavaderoController`) siguen siendo síncronos: son lookups de una fila sobre índice y
volverlos asíncronos sin debounce trae resultados fuera de orden. Consecuencia práctica: **en
`strict` esos campos lanzan**, así que los smokes manuales se corren sin `strict`, leyendo los WARNs
del log.

**Estado mutable de un controller:** se lee y escribe **sólo en el EDT** (`pintar`, diálogos, DnD).
Nada de eso puede tocarse desde el hilo de fondo.

**Jerarquía de excepciones:** `AptiumException` → `BusinessException`, `DataAccessException`, `ValidationException` (con builder), `ResourceNotFoundException`, `DatabaseException`.

## Tests

JUnit 5 (Jupiter) + Mockito + H2 en memoria. ~970 tests en 94 clases de `src/test/java`,
reflejando la estructura de paquetes de `src/main/java` (un `*Test.java` por
DAO/Service/Controller/helper relevante).

Para lógica de negocio embebida en clases de Swing (diálogos, paneles), el
patrón del repo es extraerla a una clase plana sin dependencias de Swing y
testearla en aislamiento — ver `AgrupadorIngresosLote`, `DuplicadoHighlighter`,
`SincronizadorVolumenFinal`, `ConstructorVistaCiclos` y `AgrupadorInstanciasSalida` como ejemplos.
