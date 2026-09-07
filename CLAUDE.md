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

**Tres disparadores de relectura conviven.** Los dos primeros son "al entrar" y no están unificados
a propósito (unificarlos es un refactor transversal aparte); el tercero es a pedido del operador:

| Disparador | Dónde | Cómo |
|---|---|---|
| `componentShown` en el controller | Pantallas del CDE y las de consulta (`EquiposParaEntregarController`, `EstadoProcesosController`, `VerEquiposController`, `VerLotesController`, `VerCiclosController`, `HistorialLavaderoController`) y las del grupo `operativo` `RegistrarEstadoController` y `LotesController` | El `ComponentAdapter` del panel pide la relectura al mostrarse |
| `ActionListener` del botón de menú en `UiCoordinator` | Pantallas operativas de Lavadero (`ClasificacionController`, `CiclosController`, `SalidasLavaderoController`) | El listener del botón hace `navegador.show(...)` **y** llama al método de carga (`cargarIngresosSinClasificar()`, `abrirPantalla()`, `cargarDatos()`) — `UiCoordinator:198-201`, `:209-212`, `:227-230` |
| Botón "Actualizar" / **F5** en el `PanelHeader` | Las 11 pantallas que muestran datos de BD | `setAccionRefrescar(Runnable)` cablea **la función de carga que la pantalla ya tenía**; `setGuardRefresco(...)` interpone la confirmación donde hace falta. Plan: `plans/botones-refresco-por-pantalla.md` |

Las tres pantallas de Lavadero se muestran **sólo** desde esos tres listeners (no hay `navegador.show(CLASIFICACION_LAVADERO|CICLOS_LAVADERO|SALIDAS_LAVADERO)` en ningún otro lado), así que la segunda convención cubre el 100 % de sus rutas de entrada. `CiclosController.componentShown` **no** relee (sólo colapsa cards / configura DnD): la relectura va en `abrirPantalla()`, que resetea sólo las cards libres para no pisar lo que el operador tipea en otra card — ver su javadoc.

**El botón no agrega un camino de lectura.** Invoca el mismo `Disparador` o el mismo método de carga
que usa la pantalla al entrar; lo único propio del botón es *cuándo* se dispara y qué pasa con el
trabajo en curso. El `marcarActualizado()` del cartelito va dentro del `pintar()` de cada controller
(donde el repintado efectivamente ocurrió), nunca en el click: hay debounce de 150 ms en
`RefrescadorPantallas` y la lectura puede fallar.

**Qué le pasa a lo pendiente — es el texto del cartel, no documentación.** Un cartel que miente
entrena al operador a apretar "Sí" sin leer, y desactiva también los avisos verdaderos (mismo
argumento que "por qué las tablas de detalle no llevan `version`"). Por eso hay un
`Mensajes.REFRESCO_*` por pantalla y **no** se reusan los `GUARD_*_CAMBIOS`, que dicen "se perderán":

| Pantalla | Guarda | Qué pasa con el trabajo en curso |
|---|---|---|
| Lotes | sí, sin descarte | Lo arrastrado **se conserva** (`repintar()` descuenta el staging); puede haber dejado de estar disponible en la base |
| Ciclos | sí, sin descarte | La config tipeada **se conserva** — el botón va a `recargar()`, **no** a `abrirPantalla()`, que la resetea |
| Registrar Estado | sí, **con descarte** | Los movimientos armados **se descartan** (`descartarCambiosPendientes` antes de releer) |
| Clasificación | sí, **con descarte** | Los elementos del formulario **se descartan**: `PantallaClasificacionLavadero.refrescar()` reconstruye el `PanelElementosClasificacion` entero |
| Las otras 7 | no | No acumulan estado entre lecturas |

**La regla para decidir si una pantalla necesita guarda:** *toda pantalla que acumule estado en
memoria entre lecturas la necesita; el guard de Volver (`setGuardVolver`) es una pista de dónde
buscarla, no la lista.* La tentación es escribirla al revés — "sin guard de Volver ⇒ sin guarda de
refresco" — y es **falsa**: Clasificación no tiene guard de Volver y sí destruye el formulario al
refrescar. Antes de exponer cualquier método de carga como acción del usuario, mirar qué hace su
`pintar` con el estado en curso.

**Dos asimetrías que hay que preservar:**
- `RegistrarEstadoController.componentShown` **no relee** cuando hay cambios pendientes: descarta el
  buffer y repinta local. Por eso el botón es "descartar + releer" y no sólo "releer" — un repintado
  del grupo `operativo` con el buffer vivo deja el contador y los botones Confirmar/Cancelar
  mintiendo sobre movimientos que ya no se ven, y confirmarlos pasaría en silencio (la guarda de esa
  pantalla es CAS sobre `estado` del material, no sobre `version`).
- `ClasificacionController` y `SalidasLavaderoController` guardan la `TareaUI.Ejecucion` en vuelo y
  la cancelan antes de lanzar otra (`cargaEnCurso`, calcado de `CiclosController`). No pasan por
  `RefrescadorPantallas`, así que no tienen debounce: sin eso, F5 mantenido apretado da N lecturas
  concurrentes y gana la que **termine** última, no la última lanzada.

`GuardaRefresco` (`ui/common/`) es la clase plana donde vive la decisión — sin Swing, porque
`JOptionPane` tira `HeadlessException` en los tests; `PanelHeader` la delega con un confirmador que
apunta a `JOptionPane`. Que 19 headers registren F5 no colisiona por la misma razón que ESC no
colisiona hoy: `WHEN_IN_FOCUSED_WINDOW` sólo dispara en componentes *showing* y el `CardLayout` deja
invisibles las cards que no son la actual.

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

## Concurrencia — bloqueo optimista

Hermana de la regla del EDT de arriba, pero de otra cosa: aquélla es sobre **hilos dentro de un
cliente**; ésta es sobre **dos operadores contra la misma base**. Plan completo:
`plans/bloqueo-optimista-concurrencia.md`.

**Regla dura:** toda escritura que dependa de un dato leído antes lleva **guarda**, y toda guarda
**mira las filas afectadas**. El `WHERE` incluye una condición que sólo es verdadera si nadie tocó
la fila desde que la pantalla la leyó; `0 filas afectadas` no es un error de base, es *"la realidad
ya no es la que viste"*: se aborta la transacción entera, se avisa y se recarga. Sin reintento
automático.

```java
// Guarda de campo (CAS): el estado que el operador vio viaja con el movimiento
UPDATE equipo_materiales SET estado = ? WHERE id = ? AND estado = ?
ControlConcurrencia.exigirFilaAfectada(ps.executeUpdate(), Mensajes.CONFLICTO_MATERIAL);
```

- **Helper:** `ControlConcurrencia` (`common/dao/`) — `exigirFilaAfectada` / `exigirFilasAfectadas`.
  Más de una fila no es conflicto pero sí un bug: loguea `warn` y sigue.
- **Excepción:** `ConflictoConcurrenciaException extends BusinessException` (`common/exception/`).
  Es subclase a propósito: los controllers ya rutean `BusinessException` como aviso al usuario, así
  que el conflicto llega bien sin tocar un `catch`; quien lo quiera distinguir usa `instanceof`.
- **Mensajes:** `Constantes.Mensajes.CONFLICTO_*`. Van al operador y dicen **qué cambió y qué
  hacer**, no qué falló.
- **La relectura que alimenta la guarda va `FOR UPDATE`.** Comparar contra un `SELECT` común no
  sirve: bajo el `REPEATABLE READ` de MySQL los dos operadores leen el mismo snapshot y la guarda
  los deja pasar a los dos. **H2 no lo delata** — corre en `READ COMMITTED`, donde cada sentencia ve
  un snapshot fresco.

**Por qué las tablas de detalle NO llevan columna `version`** — la decisión que más fácil se
revierte por error. `equipo_materiales`, `equipo_otros_materiales`, `elementos_clasificacion_lavadero`
y `salidas_lavadero` se **consumen por cantidad**: dos operadores sacando 3 y 4 unidades de una fila
de 10 son dos operaciones **válidas y compatibles**. Una `version` de fila las haría chocar a las
dos por **falso positivo**, y el operador aprendería a ignorar el cartel — que es peor que no tener
guarda, porque desactiva también los avisos verdaderos. La guarda va sobre el campo que se consume
(`estado`, `destino`, saldo), que detecta el choque **real** y deja pasar al concurrente legítimo.

**`equipos` y `equipo_otros` tienen `version` (V21), y la usan como guarda — pero sólo en
Correcciones.** La misma columna es asimétrica a propósito, y esa asimetría es la que hay que
preservar al tocar cualquiera de los dos lados:

- **Registrar Estado sigue sin usarla.** Ningún `WHERE` de `EquipoMaterialHelper`/
  `EquipoOtrosMaterialHelper` ni de `MaterialDAO.aplicarMovimientos`/`EquipoOtrosDAO.aplicarMovimientos`
  la lleva. Guardar con la `version` del agregado ahí reintroduce el falso positivo un nivel más
  arriba — dos operadores avanzando materiales **distintos del mismo equipo** chocarían sin
  pisarse en nada. La guarda real de esos flujos sigue siendo el `estado` de cada material.
- **Correcciones sí la usa como guarda**, en las diez rutas de `EquipoCorreccionService`/
  `EquipoOtrosCorreccionService` y en la fusión de clientes (`FusionClientesDAO`, que mueve
  `equipos`/`equipo_otros` de un cliente a otro). Ahí el mismo falso positivo se **acepta a
  propósito**: dos operadores corrigiendo materiales distintos del mismo equipo van a chocar,
  porque Correcciones reemplaza la fila entera desde un snapshot del formulario (no consume por
  cantidad) y es una pantalla de uso esporádico y auditado, donde "otro tocó esto mientras lo
  mirabas" es información que el operador quiere ver, no ruido.
- **Eliminar** (`EquipoDAO.eliminarConVersion`, `EquipoOtrosDAO.eliminarEquipo`,
  `ClienteDAO.eliminarConNombre`) es CAS de una sola sentencia sobre `equipos`/`equipo_otros`/
  `clientes` — no bumpea nada, porque la fila desaparece y no queda token que invalidar.

El bump vive en un solo lugar por agregado para los caminos derivados (los dos
`recalcularEstadoEquipo` de los helpers), más `bumpVersionConGuarda` para las rutas de
Correcciones (bump + CAS en una sentencia) y el `UPDATE ... version = version + 1` explícito de
`FusionClientesDAO`. Detalle completo, incluida la auditoría de qué ruta bumpea y cuál no, en el
javadoc de `EquipoOtrosMaterialHelper.recalcularEstadoEquipo`.

`lotes` e `ingresos_lavadero` tampoco llevan `version`: ya tienen una guarda natural más informativa
que un número (`lotes.fecha_fin IS NULL`, y la máquina de estados persistida del ingreso).

**Un JAR anterior a la V21 no puede escribir en una base ya migrada.** `DatabaseInitializer` compara
después de migrar la máxima versión aplicada contra la máxima que el JAR trae, y aborta el arranque
con `EsquemaDesactualizadoException` si la base está adelante. Hace falta el chequeo explícito
porque **no sale gratis de Flyway**: `ignoreFutureMigrations` está en `true` por defecto, así que ve
la migración desconocida y arranca igual. Cada máquina se autoactualiza cuando quiere, y un cliente
viejo escribe sin guardas y sin bumpear `version` — el mismo bug, reintroducido por el despliegue.
Compara **máximos**, no continuidad: una migración atrasada que se aplica después (el
`outOfOrder(true)` que existe porque dos ramas se pisaron los números) no es una base adelantada.

**Dónde hay guarda hoy:** Registrar Estado (ortopedias y otros), Lanzar Lote, Clasificación de
Lavadero, Lanzar Tanda, Salidas + derivación al CDE, las diez rutas de Correcciones (ortopedias y
otros), fusionar clientes y eliminar cliente. **Qué quedó afuera:** el resto de los ABM (catálogo,
instituciones, profesionales, y el resto de Ajustes) — sin ruta alcanzable desde la UI, no sin
superficie de escritura; anotado en `plans/hallazgos-arquitectura-pendientes.md`. `obtenerSiguienteSecuencia`
de `LoteDAO` no es un caso de esta regla: no hay dato leído por el operador que se esté pisando,
es asignación de identidad, y se resuelve con reintento sobre la violación de `UNIQUE`
(`LoteDAO.lanzarLote`, ver su javadoc).

**La única guarda que no lanza** es `SalidaLavaderoDAO.SQL_FINALIZAR_INGRESO`
(`AND estado <> 'FINALIZADO'`): finalizar es idempotente, y que otro lo haya finalizado es el
resultado buscado, no un choque. Está documentado en su javadoc — es la excepción, no el patrón.

**Tests:** `ConcurrenciaOptimistaTest` (`infrastructure/db/`) tiene un caso por flujo con la misma
forma — *A lee → B modifica y commitea → A escribe → conflicto, y el estado final es exactamente el
de B*. Verifica **la guarda**, que es idéntica en H2 y MySQL, no el comportamiento del lock, que no
lo es: un test de deadlock pasaría en H2 y mentiría sobre producción.

## Tests

JUnit 5 (Jupiter) + Mockito + H2 en memoria. ~970 tests en 94 clases de `src/test/java`,
reflejando la estructura de paquetes de `src/main/java` (un `*Test.java` por
DAO/Service/Controller/helper relevante).

Para lógica de negocio embebida en clases de Swing (diálogos, paneles), el
patrón del repo es extraerla a una clase plana sin dependencias de Swing y
testearla en aislamiento — ver `AgrupadorIngresosLote`, `DuplicadoHighlighter`,
`SincronizadorVolumenFinal`, `ConstructorVistaCiclos` y `AgrupadorInstanciasSalida` como ejemplos.
