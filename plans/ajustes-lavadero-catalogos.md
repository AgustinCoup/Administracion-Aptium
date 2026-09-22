# Plan B — Ajustes de Lavadero: bajas de catálogo, ABM de lavarropas, jabón automático y copiar/pegar

**Objetivo:** un menú de configuración en **Ajustes** que permita (a) **dar de baja y reactivar**
elementos de catálogo de Lavadero, Ortopedias y Otros, (b) **agregar, dar de baja y reactivar
lavarropas**, y (c) **agregar jabones e insumos extra** y elegir el **jabón por defecto de cada tipo
de lavado**. Con eso, la card de Ciclos carga el jabón sola según el tipo, y se puede **copiar y
pegar** la configuración de un lavarropas en otro.

**Rama:** `PrimeraRevisionLavadero` · **Modo:** directo, un commit por paso, sin PRs
**Fecha de creación:** 2026-09-21
**Plan previo, obligatorio:** [`configuracion-ciclo-lavadero.md`](configuracion-ciclo-lavadero.md).
Este plan **no arranca** hasta que ése esté cerrado: usa `catalogo_insumos`, `InsumoCatalogo` y la
lista de insumos de la card, que aquél crea.

---

## Decisiones tomadas con el usuario

| Tema | Decisión |
|---|---|
| Tipo de baja | **Baja lógica** (columna `activo`), reactivable desde Ajustes. Nada se borra. |
| Elemento dado de baja en un ingreso nuevo | **Se rechaza con aviso.** En "Otros", que es texto libre y hoy crea la entrada si no existe, si el texto coincide con una dada de baja **se avisa y NO se crea**. |
| Alta de lavarropas | El operador **elige el número**. Se rechaza si ese número ya existe, **incluso si está dado de baja** — un lavarropas nuevo que reemplaza a uno retirado tiene que tener sus estadísticas aisladas del anterior. |
| Baja de lavarropas | No se puede si tiene un ciclo en curso o ropa cargada. |
| Reactivar un lavarropas | **Sí, se reactiva y recupera su historia.** Es la *misma* máquina, no un reemplazo: por eso reactivar es legítimo y reusar el número para una máquina nueva no lo es. |
| `lavarropas.capacidad_litros` | **Se borra.** |
| Jabones e insumos | Se pueden **agregar y dar de baja**, igual que el resto de los catálogos. |
| Jabón automático | Sucio → Skip, Limpio → Lider, **configurable en Ajustes**. Es sólo la carga por defecto: **una elección a mano siempre pesa más que la automática.** AUTO sigue al tipo; MANUAL no se toca; pegar cuenta como MANUAL; sin default configurado o con el default inactivo, no se toca nada. |
| Correcciones con un material ya dado de baja | **Se guarda igual si no se lo toca.** Sólo se rechaza *asignar* uno de baja. Corregir el nombre de un paciente no puede quedar bloqueado por un catálogo retirado. |
| Pegar con un jabón o insumo inactivo | **Se omite ese ítem y se avisa**, pegando todo lo demás. |

### Decisiones de diseño tomadas por el plan (con su porqué)

| Tema | Decisión | Por qué |
|---|---|---|
| **Ortopedias NO lleva columna nueva** | Usa la `catalogo_descripciones.vigente` que ya existe desde **V16** | La baja lógica de ortopedias ya está implementada *y cableada*: `obtenerDescripcionVigente` la respeta en Ingreso y en las diez rutas de Correcciones. Lo único que falta es la pantalla que la prenda y la apague. Agregar un `activo` paralelo daría dos fuentes de verdad. |
| La columna se llama `activo` en las tablas nuevas y `vigente` en `catalogo_descripciones` | **No se unifica** | Renombrar obliga a una migración sobre una tabla histórica y a tocar diez consultas y el javadoc de V16 —que explica en detalle por qué existe `vigente`— para cero cambio de comportamiento. La inconsistencia se **documenta** en `CLAUDE.md` para que nadie la "arregle" después. |
| Un controller por pestaña de Ajustes | `AjustesController` (clientes + actualizaciones), `CatalogosAjustesController`, `LavarropasAjustesController`, `JabonesInsumosAjustesController` | Regla de extensión del repo: *el alcance de un controller se ve en su firma*. Meter los cuatro catálogos, los lavarropas y los defaults en `AjustesController` le daría ocho services y convertiría su constructor en un cajón — exactamente lo que la regla existe para evitar. |
| El orden de bloqueo es **`lavarropas` → `ciclos_lavadero`**, el mismo en `lanzarTanda` y en la baja | El `FOR UPDATE` sobre `lavarropas` va **primero** en las dos | El `FOR UPDATE` de `SQL_CICLO_ACTIVO_DE_LAVARROPAS` es un **gap lock**, y dos gap locks entre sí son **compatibles** (lo dice su propio javadoc): no ordena nada al tomarse. `lanzarTanda` recién conflictúa sobre `ciclos_lavadero` en su `INSERT`, al final. Lo único que ordena las dos operaciones entre sí es el bloqueo **exclusivo** sobre la fila de `lavarropas`, y por eso va primero. Ver el bloque de concurrencia del Paso 2. |
| Excepción propia `LavarropasDeBajaException`, no reuso de `LavarropasOcupadoException` | Las dos extienden `ConflictoConcurrenciaException` directamente | "Está ocupado" y "lo dieron de baja" son dos cosas distintas para el operador, y un cartel que dice la que no es entrena a apretar "Sí" sin leer. La regla positiva del repo —*el staging se descarta **sólo** ante `SaldoConsumidoException`*— sigue intacta: ninguna de las dos la toca. |
| El rechazo de un texto de "Otros" dado de baja ocurre **al guardar**, no al tipear | Dentro de `obtenerOCrear`, en la transacción | Los cinco autocompletados de la app son **síncronos a propósito** (excepción aceptada en `CLAUDE.md`): son lookups de una fila sobre índice y volverlos asíncronos sin debounce da resultados fuera de orden. Meter una validación más en el `focusLost` agrega I/O sincrónico en el EDT sin comprar nada — el guardado igual tiene que rechazarlo, porque la baja puede ocurrir entre que se tipea y que se guarda. |
| La grilla de Ciclos **reusa** las cards que sobreviven a una reconstrucción | `reconstruirGrilla` sólo crea las nuevas y descarta las que se fueron | Recrearlas todas borraría la configuración que el operador está tipeando en cards que no cambiaron, que es justo el invariante que `recargar()` protege. Como todos los cableados de una card son *setters* (`setOnAccion`, `setTransferHandler`), volver a cablear el mapa entero es idempotente. |
| Ningún grupo de refresco nuevo | Ajustes ya tiene `setOnMutacion(operativo)` | Todo lo que estas pantallas cambian lo consumen pantallas que **leen al entrar** (Ciclos en `abrirPantalla()`, Clasificación en `cargarIngresosSinClasificar()`, los autocompletados en cada tecla). Y no se puede estar en Ajustes y en Ciclos a la vez: para ver el efecto hay que salir, que es exactamente cuando se relee. Agregar un grupo sería maquinaria para un caso que no existe. |
| `LavarropasTableModel` se **borra** | Medido: **cero llamadores** en `src/main` y `src/test` | Es la única clase que leía `capacidadLitros` para mostrarlo. La columna "Capacidad (L)" que el pedido menciona **no se está mostrando en ninguna pantalla hoy**. Borrar la columna no le saca nada a nadie. |

---

## Contexto compartido (leer una vez por sesión)

App de escritorio **Swing, Java 17, Maven**, sin framework de DI. Capas por feature:
`model → dao → service → view/controller`. Todo se cablea a mano en `AppContext` y `UiCoordinator`.

### Reglas duras del repo que este plan debe respetar

1. **Ningún acceso a BD en el EDT.** `TareaUI` es el único mecanismo de trabajo en fondo. La única
   excepción aceptada son los **cinco autocompletados síncronos** — y este plan **no agrega un
   sexto**.
2. **El estado mutable de un controller se lee y escribe sólo en el EDT.**
3. **Un controller declara en su constructor los services que usa.** `UiCoordinator` se los provee
   desde `AppContext`. No hay fachada intermedia.
4. **Los services no tienen JDBC.** Validan y delegan.
5. **Una migración ya escrita no se toca.**
6. **Toda escritura que dependa de un dato leído antes lleva guarda, y toda guarda mira las filas
   afectadas** (`ControlConcurrencia.exigirFilaAfectada`). `0 filas` no es un error de base: es
   *"la realidad ya no es la que viste"*.
7. **Las guardas que bloquean y esperan se toman TODAS antes de la primera lectura no bloqueante**
   (`REPEATABLE READ` de MySQL fija la vista ahí). **H2 no lo delata**: corre en `READ COMMITTED`.
8. **Ninguna operación mantiene dos conexiones abiertas a la vez** (el semáforo reparte 5 permisos).
9. **Lógica de negocio embebida en Swing → clase plana sin Swing, testeada en aislamiento.**
10. **`recargar()` de Ciclos NO resetea la configuración que se está tipeando.** Sólo
    `abrirPantalla()`, y sólo en las cards libres.
11. **Un cartel que miente entrena al operador a apretar "Sí" sin leer**, y desactiva también los
    avisos verdaderos. Cada aviso nuevo lleva su propio `Mensajes.*`.

### Estado del que parte este plan (verificado contra el código, 2026-09-21)

```
catalogo_descripciones(codigo PK, descripcion, volumen, vigente)   -- V1 + V16.  YA tiene baja lógica
catalogo_otros(id PK, descripcion UNIQUE)                          -- V2. Se crea sola con texto libre
catalogo_elementos_lavadero(id PK, nombre UNIQUE, categoria)       -- V9 + V11
catalogo_jabones(id PK, nombre UNIQUE)                             -- V12.  seeds: Skip, Lider
catalogo_insumos(id PK, nombre UNIQUE, activo)                     -- V24 (plan A)
lavarropas(numero PK, capacidad_litros)                            -- V10. seeds 1..13
```

- **`Constantes.Lavadero.CANTIDAD_LAVARROPAS = 13`** la usan exactamente dos lugares:
  `PantallaCiclos.construirGrillaDeCards()` (que arma las cards **en el constructor**) y la
  validación de rango de `CicloLavaderoService.validar` (~52).
- **`equipo_otros_materiales` guarda `descripcion` como snapshot**, además del `catalogo_otros_id`.
  El historial de "Otros" **no hace `JOIN` al catálogo**: un texto dado de baja sigue mostrándose
  solo. Es un problema menos.
- `AjustesController` hoy maneja sólo `Cliente` + auto-actualización, y `PantallaAjustes` tiene
  `PanelGestionClientes` en el centro y el botón de actualizaciones al sur.
- `plans/hallazgos-arquitectura-pendientes.md` **#9c** dice que los ABM quedaron sin guarda *"por no
  tener ruta alcanzable desde la UI"*, y **#10** marca `CatalogoDAO.guardarDescripcion` como la
  única entrada de código muerto que **necesitaría guarda el día que deje de estar muerta**. Este
  plan crea rutas de UI nuevas: tiene que actualizar los dos puntos.

### Archivos de referencia

| Para | Leer |
|---|---|
| Baja lógica ya funcionando | `V16__catalogo_ortopedias.sql` + `CatalogoDAO.obtenerDescripcionVigente` + `IngresoOrtopediaController` (~60, 70) |
| Guardas `FOR UPDATE` y su orden | `CicloLavaderoDAO.SQL_CICLO_ACTIVO_DE_LAVARROPAS` y su javadoc (es el más largo del repo por un motivo) |
| CAS + filas afectadas | `common/dao/ControlConcurrencia.java` |
| Panel ABM existente | `features/ajustes/view/PanelGestionClientes.java` |
| Controller de ABM con `TareaUI` | `AjustesController.mutar(...)` |
| Armado de la grilla de cards | `PantallaCiclos.construirGrillaDeCards()` |
| Descarte de staging + aviso | `ConstructorVistaCiclos.descartarStagingDeLavarropasOcupados` + `CiclosController.avisarStagingDescartado` |
| Flag anti-callback programático | `PantallaHistorialLavadero` (`silenciandoCallback`) |
| Clase plana con regla de negocio testeable | `SincronizadorVolumenFinal`, `ConstructorVistaCiclos` |

### Comandos

```bash
mvn clean package && java -jar target/aptium.jar
mvn test        # ~1309 @Test en 120 clases antes del plan A
mvn verify
mvn test -Dtest=NombreDeClase
```

---

## Grafo de dependencias

```
Paso 1 (V26: activo, jabon_por_tipo_lavado)
   │
   ├──► Paso 2 (lavarropas: modelo, DAO/ABM, guarda en lanzarTanda)  ──► Paso 3 (grilla dinámica)
   │
   ├──► Paso 4 (bajas de catálogo: Lavadero, jabones, insumos)   ─┐  4 y 5 en PARALELO
   └──► Paso 5 (bajas de catálogo: Ortopedias y Otros)           ─┘  (features distintas)
                                                                  │
                    1 + 3 + 4 ──────────────────────────────────────► Paso 7 (jabón automático AUTO/MANUAL)
                                                                                 │
                    2 + 3 + 4 + 5 + 7 ───────────────────────────────────────────┴──► Paso 6 (Ajustes: pestañas)
                                                                                 │
                                                                                 ▼
                                                              Paso 8 (copiar / pegar)   ← cambio 5
                                                                                 │
                                                                                 ▼
                                                              Paso 9 (revisión, docs, cierre)
```

| Paso | Modelo sugerido | Archivos que toca |
|---|---|---|
| 1 | **Opus**, alto | `db/migration/V26__*.sql`, test de migración, `DatabaseInitializerTest` (el pin de versión máxima) |
| 2 | **Opus**, alto | `db/migration/V27__*.sql`, `lavadero/model/Lavarropas`, `LavarropasDAO`, `LavarropasService`, `CicloLavaderoDAO`, `ConstructorVistaCiclos` (sólo el `new LavarropasItem`), `common/exception/`, tests |
| 3 | **Opus**, alto | `PantallaCiclos`, `CiclosController`, `ConstructorVistaCiclos`, `LavarropasItem`, `Constantes`, tests |
| 4 | Sonnet | `lavadero/dao/Catalogo*DAO`, `lavadero/service/`, `ClasificacionLavadero*`, tests |
| 5 | Sonnet | `catalogo/dao/`, `catalogo/service/`, `EquipoOtrosDAO`, tests |
| 6 | Sonnet | `features/ajustes/view/`, `features/ajustes/controller/`, `AppContext`, `UiCoordinator` |
| 7 | **Opus**, alto | `LavarropasCard`, helper plano nuevo, `CiclosController`, `DatosCiclos`, DAO/service de defaults, **`AppContext`** (constructor largo incluido) y **`UiCoordinator`** — `CiclosController` gana un service más en la firma |
| 8 | Sonnet | `LavarropasCard`, `CiclosController`, `Constantes`, tests |
| 9 | Sonnet | `CLAUDE.md`, `hallazgos-arquitectura-pendientes.md`, memoria, este archivo |

**Invariantes verificados después de CADA paso:**
- [ ] `mvn test` en verde (o `mvn -q compile` si el paso todavía no cierra la app)
- [ ] Cero `new Thread()` / `SwingWorker` nuevos · cero JDBC fuera de un DAO
- [ ] Cero autocompletados síncronos nuevos
- [ ] Ninguna operación abre dos conexiones a la vez
- [ ] Toda escritura nueva sobre un dato leído antes tiene guarda, y la guarda mira filas afectadas

---

## Paso 1 — `V26`: la columna `activo`, los defaults de jabón y el `DROP` de la capacidad

> ⚠️ **Corrección de numeración (aplicada al ejecutar el paso, 2026-09-22).** El plan escribió
> `V25`/`V26`, pero el plan A terminó partiéndose en **V24 + V25**, así que la V25 ya existe
> (`V25__drop_booleanos_y_litros_totales_ciclo.sql`) y una migración ya escrita no se toca. Todo
> el plan corre un número: **este paso es la `V26`** (`V26__ajustes_catalogos_y_lavarropas.sql`,
> test `MigracionV26Test`) y **el `DROP COLUMN capacidad_litros` del Paso 2 es la `V27`**. El
> texto de abajo conserva la redacción original; leer `V25`→`V26` y `V26`→`V27`.

### Contexto (autocontenido)

`catalogo_descripciones` **no se toca**: ya tiene `vigente` desde V16 y toda la lógica de ortopedias
la respeta. Las otras cuatro tablas de catálogo y `lavarropas` sí necesitan la columna. Patrón:
`ALTER` separados, sin `AFTER` (H2 en tests, MySQL en producción).

### Tareas

1. **`src/main/resources/db/migration/V25__ajustes_catalogos_y_lavarropas.sql`**:

   ```sql
   -- Baja lógica de catálogos. `catalogo_descripciones` NO entra: ya tiene `vigente` (V16),
   -- y agregarle un `activo` paralelo daría dos fuentes de verdad sobre lo mismo.
   -- La diferencia de nombre queda a propósito: renombrar `vigente` obligaría a tocar diez
   -- consultas y el comentario de V16 para cero cambio de comportamiento.
   ALTER TABLE catalogo_elementos_lavadero ADD COLUMN activo BOOLEAN NOT NULL DEFAULT TRUE;
   ALTER TABLE catalogo_jabones            ADD COLUMN activo BOOLEAN NOT NULL DEFAULT TRUE;
   ALTER TABLE catalogo_otros              ADD COLUMN activo BOOLEAN NOT NULL DEFAULT TRUE;

   -- Alta/baja de lavarropas.
   -- OJO: el DROP de `capacidad_litros` NO va acá, va en la V26 del Paso 2, junto con el
   -- cambio del DAO que la selecciona. Ver la nota de abajo.
   ALTER TABLE lavarropas ADD COLUMN activo BOOLEAN NOT NULL DEFAULT TRUE;

   -- Jabón por defecto de cada tipo de lavado. La PK es el name() del enum TipoLavado
   -- (LIMPIO / SUCIO), que es lo que ya se persiste en ciclos_lavadero.tipo_lavado.
   -- RESTRICT: un jabón configurado como default no se puede borrar del catálogo.
   CREATE TABLE jabon_por_tipo_lavado (
       tipo_lavado VARCHAR(20) PRIMARY KEY,
       jabon_id    INT NOT NULL,
       FOREIGN KEY (jabon_id) REFERENCES catalogo_jabones(id) ON DELETE RESTRICT
   );

   -- Se resuelven por nombre: los ids son AUTO_INCREMENT y no se pueden hardcodear.
   -- Si en alguna base esos nombres ya no existen, estos INSERT no insertan nada y la
   -- migración NO falla: quedarse sin default es un estado legítimo ("no hay default
   -- configurado, no se toca nada"), y abortar el arranque por eso sería desproporcionado.
   INSERT INTO jabon_por_tipo_lavado (tipo_lavado, jabon_id)
   SELECT 'SUCIO',  id FROM catalogo_jabones WHERE nombre = 'Skip';
   INSERT INTO jabon_por_tipo_lavado (tipo_lavado, jabon_id)
   SELECT 'LIMPIO', id FROM catalogo_jabones WHERE nombre = 'Lider';
   ```

   ⚠️ **Por qué el `DROP COLUMN capacidad_litros` se fue a una V26 del Paso 2.** Este paso toca
   **sólo** `db/migration/` y su test. `LavarropasDAO.obtenerTodos()` sigue diciendo
   `SELECT numero, capacidad_litros FROM lavarropas`: apenas Flyway corra la V25 en el H2 compartido
   de `AbstractDAOTest`, ese `SELECT` explota — y como ese método **se traga el `SQLException` y
   devuelve lista vacía**, no falla ruidosamente: falla como `LavarropasDAOTest` en rojo más
   cualquier test que dependa de que haya lavarropas. El criterio de salida *"`mvn test` en verde"*
   sería inalcanzable, y el agente de la Sesión 1 se pondría a arreglar el DAO —que es trabajo del
   Paso 2— o, peor, a mover el `DROP` por su cuenta. **La columna se dropea en el mismo paso que
   deja de leerla.**

2. **`MigracionV25Test`** — misma forma que `MigracionV24Test` del plan A (H2 propio, **sin tocar
   `ConnectionPool.setDataSourceForTesting`**), migrando con `.target("24")`, insertando datos, y
   migrando a 25. Aserciones:
   - las cuatro columnas `activo` existen y **todas las filas preexistentes quedan en `TRUE`**
     (el `DEFAULT TRUE` no basta: hay que verificar que no haya `NULL` ni `FALSE`);
   - `jabon_por_tipo_lavado` tiene dos filas y apuntan a Skip y Lider;
   - caso del borde: con `catalogo_jabones` sin 'Skip', la migración **corre igual** y deja una sola
     fila. Éste es el test que documenta la decisión de no abortar.

### Verificación

```bash
mvn test -Dtest=MigracionV25Test
mvn test
```

### Criterio de salida

- [x] `mvn test` en verde con la V26 aplicada
- [x] `catalogo_descripciones` no fue tocada
- [x] **La V26 NO dropea `capacidad_litros`** — eso es la V27 del Paso 2
- [x] El test cubre el caso de un seed ausente sin que la migración falle
- [x] `DatabaseInitializerTest.sanityMaximoLocal` movido a 26 (el pin se mueve con cada migración)
- [x] Commit: `feat: V26 baja logica de catalogos, ABM de lavarropas y jabon por defecto`

---

## Paso 2 — Lavarropas: modelo, ABM y la guarda que impide lanzar en uno dado de baja

> Depende del Paso 1. **Paralelo con los Pasos 4 y 5.**

### Contexto (autocontenido)

`lanzarTanda` (`CicloLavaderoDAO`) es la única escritura de lanzamiento. Ya toma dos guardas
bloqueantes, en orden fijo: `exigirLavarropasLibres` (sobre `ciclos_lavadero`) y luego
`exigirSaldoSuficiente` (sobre `elementos_clasificacion_lavadero`). Las dos usan
`SELECT … FOR UPDATE` y se toman **antes** de cualquier lectura no bloqueante.

**El punto delicado de este paso:** "ropa cargada" (el *staging*) vive **en la memoria de cada
cliente**, no en la base. Otra máquina puede tener ropa asignada a un lavarropas y la baja no se
entera. Por eso la verificación que realmente importa **no** es la de la pantalla de Ajustes, sino
la de `lanzarTanda`: un lavarropas inactivo tiene que hacer fallar la tanda.

### Concurrencia — el orden de bloqueo, que no es opinable

Dos operaciones compiten por las mismas filas: **lanzar una tanda** y **dar de baja un lavarropas**.
Las dos tocan `ciclos_lavadero` y `lavarropas`. Si una las tomara en orden inverso a la otra,
dos operadores simultáneos se trabarían y MySQL abortaría a uno con un deadlock que sale como
error técnico.

**Y acá hay una trampa que invita a razonar al revés.** El `FOR UPDATE` de
`SQL_CICLO_ACTIVO_DE_LAVARROPAS` **no ordena nada al tomarse**: sobre un lavarropas libre no matchea
ninguna fila, así que lo que toma es un **gap lock**, y dos gap locks entre sí son **compatibles**.
Lo dice su propio javadoc (`CicloLavaderoDAO`):

> *"No serializa en el caso perfectamente simultáneo […]: dos gap locks entre sí son compatibles, así
> que dos lanzamientos que lean a la vez pasan los dos la guarda y **chocan recién en el `INSERT`**,
> donde cada uno espera el hueco del otro."*

O sea que el punto donde `lanzarTanda` realmente conflictúa sobre `ciclos_lavadero` es su
**`INSERT`**, al final de la transacción. Poner el `FOR UPDATE` de `lavarropas` *después* del de
ciclos deja este entrelazado, sobre el lavarropas #5:

```
T1 darDeBaja        T2 lanzarTanda
1. gap ciclos #5    →  ok
                    2. gap ciclos #5  →  ok (gap vs gap: compatibles, pasa)
                    3. X lavarropas #5  →  lo toma
4. UPDATE lavarropas #5  →  ESPERA el X de T2
                    5. INSERT ciclos  →  insert-intention vs el gap de T1  →  ESPERA a T1
```

Deadlock. **H2 no lo reproduce** — corre en `READ COMMITTED` y no toma gap locks —, así que todos los
tests pasan.

**El orden correcto, en las dos, es: `lavarropas` → `ciclos_lavadero` →
`elementos_clasificacion_lavadero`.**

| Operación | Secuencia |
|---|---|
| `lanzarTanda` | 1. **`exigirLavarropasActivos`** (`lavarropas … FOR UPDATE`, números ascendentes) · 2. `exigirLavarropasLibres` (`ciclos_lavadero … fecha_fin IS NULL FOR UPDATE`, ascendentes) · 3. `exigirSaldoSuficiente` · 4. escribe |
| `darDeBaja(numero)` | 1. `SELECT activo FROM lavarropas WHERE numero = ? FOR UPDATE` · 2. `ciclos_lavadero … fecha_fin IS NULL FOR UPDATE` para ese número · 3. CAS `UPDATE lavarropas SET activo = FALSE WHERE numero = ? AND activo = TRUE` |

Con el bloqueo **exclusivo** de `lavarropas` como primer paso de las dos, la que llega segunda espera
ahí y nunca llega a tomar nada de `ciclos_lavadero`: el `INSERT` de `lanzarTanda` no puede quedar
esperando a una transacción que a su vez lo esté esperando a él. Se conserva `ciclos` antes de
`elementos_clasificacion`, que es lo que exige el javadoc de `SQL_CICLO_ACTIVO_DE_LAVARROPAS`.

Las tres son lecturas **bloqueantes**, así que no hay ninguna lectura no bloqueante antes: se cumple
la regla 7 del contexto compartido. Este orden hay que sostenerlo por razonamiento y dejarlo escrito
en los javadoc de las dos operaciones — ningún test lo va a defender.

**Verificado al escribir el plan:** ninguna otra operación del repo bloquea `lavarropas`.
`finalizarCiclo` toca sólo `ciclos_lavadero` (por id) y `SalidaLavaderoDAO.bloquearAfectados` toca
salidas y clasificación. No hay un tercer participante que cierre un ciclo.

### Un lavarropas de baja con un ciclo abierto: la grilla NO se arma con `activo = TRUE`

La guarda del lanzamiento impide **crear** un ciclo en un lavarropas inactivo, pero no dice nada de
los que ya estaban abiertos cuando se dio la baja — ni de una base tocada por SQL, que es como se
llega ahí en desarrollo y en cualquier corrección manual en producción. **El smoke del Paso 3 lo
produce a mano** (`UPDATE lavarropas SET activo = FALSE WHERE numero = 13`).

Si la grilla se armara sólo con los activos, ese ciclo **no tendría card, y por lo tanto no tendría
botón Finalizar**. Nunca cierra; el ingreso nunca llega a `LAVADO`; y la ropa desaparece de las dos
pantallas: de Disponibles porque `SQL_DISPONIBLES` la cuenta como consumida, y de Salidas porque
nunca hubo un ciclo finalizado. Ropa física irrecuperable desde la UI y sin un solo cartel.

Es **textualmente** el desenlace que el javadoc de `SQL_CICLO_ACTIVO_DE_LAVARROPAS` declara
inaceptable —*"queda invisible y sin forma de finalizarse, y la ropa que se llevó no vuelve a
aparecer ni en Disponibles ni en Salidas"*—, así que introducirlo por otra puerta no es una decisión
que este plan pueda tomar.

**La grilla se arma con `activos ∪ los que tienen un ciclo sin finalizar`.** Esa card se dibuja en
**modo activo**, que ya oculta `panelConfig` (así que no se le puede cargar nada nuevo ni copiar ni
pegar), y **desaparece sola** en la relectura que sigue a finalizar el ciclo. El descarte de staging
por baja sigue aplicando sin cambios a los que no tienen ciclo abierto.

### Tareas

0. **`src/main/resources/db/migration/V27__lavarropas_sin_capacidad.sql`** — una sola línea
   (era la "V26" antes de la corrección de numeración del Paso 1):
   ```sql
   -- Se dropea acá y no en la V26 porque es este paso el que deja de leerla: LavarropasDAO,
   -- Lavarropas, LavarropasItem, ConstructorVistaCiclos.mapearLavarropas y LavarropasTableModel
   -- (que se borra). Dropearla un paso antes deja la suite en rojo sin que haya nada que
   -- arreglar en ese paso — y peor, el fallo es silencioso: obtenerTodos() se come el
   -- SQLException y devuelve lista vacía.
   ALTER TABLE lavarropas DROP COLUMN capacidad_litros;
   ```
   El borrado del código que la lee va en este mismo paso (tareas 3 y 4), no en el Paso 3:
   lo del Paso 3 es `LavarropasItem` y `LavarropasTableModel`, que son de vista. **Si el Paso 3 se
   ejecuta en otra sesión, este paso tiene que dejar `ConstructorVistaCiclos.mapearLavarropas`
   compilando** — o sea que el `new LavarropasItem(...)` de ahí pierde el argumento acá.

1. **`common/exception/LavarropasDeBajaException.java`** — `extends ConflictoConcurrenciaException`,
   hermana de `LavarropasOcupadoException`. Javadoc: por qué es un tipo aparte y no un reuso (el
   cartel dice otra cosa), y que **no** dispara el descarte del staging — esa regla es positiva y
   sólo la cumple `SaldoConsumidoException`.

2. **`Constantes.Mensajes`** — tres mensajes nuevos, que dicen **qué cambió y qué hacer**:
   - `CONFLICTO_LAVARROPAS_DE_BAJA` — *"El lavarropas #N fue dado de baja mientras armabas la tanda.
     La ropa sigue disponible: repartila en otro lavarropas."*
   - `STAGING_DESCARTADO_POR_BAJA` (con `%s` = lista de números) — para la relectura; **no se reusa
     `STAGING_DESCARTADO_POR_OCUPACION`**, que dice que otro operador lo ocupó y sería falso.
   - `CONFLICTO_LAVARROPAS_EN_USO` — para la baja rechazada por ciclo activo.
   Más los de alta: `LAVARROPAS_YA_EXISTE` y `LAVARROPAS_YA_EXISTE_DE_BAJA` (*"El lavarropas #N ya
   existe pero está dado de baja. Reactivalo desde esta misma pantalla en vez de crearlo de nuevo."*).

3. **`lavadero/model/Lavarropas`** — `(int numero, boolean activo)`. Se va `capacidadLitros` y su
   getter.

4. **`lavadero/dao/LavarropasDAO`**:
   - `List<Lavarropas> obtenerDibujables()` — **no es "los activos"**, y el nombre tiene que decirlo:
     ```sql
     SELECT numero, activo FROM lavarropas
     WHERE activo = TRUE
        OR EXISTS (SELECT 1 FROM ciclos_lavadero c
                    WHERE c.lavarropas_numero = lavarropas.numero AND c.fecha_fin IS NULL)
     ORDER BY numero
     ```
     Es el que alimenta la grilla, y el `OR EXISTS` **no es defensivo, es obligatorio**: ver el
     bloque "Un lavarropas de baja con un ciclo abierto" de arriba.
   - `List<Lavarropas> obtenerTodos()` — sin filtro, `ORDER BY numero`. Es el que alimenta Ajustes.
   - `void agregar(int numero)`:
     ```java
     // INSERT primero; si choca con la PK, se lee `activo` SÓLO para armar el mensaje.
     // Al revés (SELECT y después INSERT) hay una ventana entre las dos en la que otro
     // operador crea el mismo número, y el rechazo se convierte en un error técnico.
     ```
     Al capturar la violación de PK (`SQLIntegrityConstraintViolationException`, o `SQLState`
     que empiece con `"23"` como red de seguridad), hacer una **segunda lectura secuencial** de
     `activo` y lanzar `BusinessException` con `LAVARROPAS_YA_EXISTE` o
     `LAVARROPAS_YA_EXISTE_DE_BAJA` según corresponda. ⚠️ La segunda lectura va **después** de
     cerrar la conexión del `INSERT` (una conexión por vez).
   - `void darDeBaja(int numero)` — en `TransactionalConnection`, con la secuencia de la tabla de
     arriba. El ciclo activo → `BusinessException(CONFLICTO_LAVARROPAS_EN_USO)`. El CAS →
     `ControlConcurrencia.exigirFilaAfectada(..., CONFLICTO_LAVARROPAS_DE_BAJA)`: 0 filas significa
     que otro ya lo dio de baja.
     ⚠️ **Y su `catch (SQLException)` tiene que pasar por `ControlConcurrencia.esContencionDeLock`
     ANTES de traducir a `DatabaseException`**, lanzando `ConflictoConcurrenciaException` con
     `CONFLICTO_GENERICO`. `darDeBaja` abre dos `FOR UPDATE`: **bloquea y espera**, hasta el
     `innodb_lock_wait_timeout` de 50 s, y además puede comerse un deadlock. Es la regla dura del
     `CLAUDE.md` para toda guarda que espera, y `lanzarTanda` ya la cumple. Sin esto, el choque
     entre dos operadores sale como *"error al dar de baja"* en vez de *"alguien se te adelantó"* —
     y recordá que **no alcanza con `catch (SQLTransactionRollbackException)`**: el *lock wait
     timeout* (1205) viaja con `SQLSTATE HY000` como `SQLException` pelada, y con guardas que
     esperan el timeout es el desenlace **más** probable.
   - `void reactivar(int numero)` — CAS `… SET activo = TRUE WHERE numero = ? AND activo = FALSE`,
     con `exigirFilaAfectada`. Sin transacción: es una sola sentencia.
   - Manejo de errores **como `SalidaLavaderoDAO`**: `DatabaseException`, nunca lista vacía. Hoy
     `obtenerTodos()` traga el `SQLException` y devuelve vacío — con la grilla armada desde la base,
     eso pinta una pantalla de Ciclos **sin ningún lavarropas** y sin decir por qué. Arreglarlo es
     parte de este paso.

5. **`CicloLavaderoDAO`**:
   - Bajar `SQL_CICLO_ACTIVO_DE_LAVARROPAS` a visibilidad **de paquete** (`static final String`, sin
     `private`) para que `LavarropasDAO` —que vive en el mismo paquete `lavadero/dao/`— use la misma
     constante en vez de una copia que puede derivar. Dejarlo dicho en su javadoc.
   - `SQL_LAVARROPAS_ACTIVO = "SELECT activo FROM lavarropas WHERE numero = ? FOR UPDATE"`, con
     javadoc explicando el `FOR UPDATE` (sin él, una baja que commitea entre la lectura y el
     `INSERT` deja arrancar un ciclo en una máquina retirada) y el orden respecto de
     `SQL_CICLO_ACTIVO_DE_LAVARROPAS`.
   - `exigirLavarropasActivos(conn, tanda)` — números **ascendentes**, igual que
     `exigirLavarropasLibres`. Sin fila → el lavarropas no existe; con `activo = FALSE` → está de
     baja. Los dos casos lanzan `LavarropasDeBajaException` (el primero es teóricamente imposible:
     no hay `DELETE` sobre `lavarropas`).
   - En `lanzarTanda`, llamarlo **primero de todo**, antes de `exigirLavarropasLibres`. No es
     cosmético: ver el bloque de concurrencia de arriba.

6. **`LavarropasService`** — `obtenerDibujables()`, `obtenerTodos()`, y las tres mutaciones con
   `ValidationException.builder()`: `numero >= 1` en el alta (el techo se lo pone la base, no una
   constante). Cero JDBC.

7. **`CicloLavaderoService.validar`** — borrar la validación de rango contra
   `Constantes.Lavadero.CANTIDAD_LAVARROPAS` y dejar sólo `numero < 1`. **La existencia y el estado
   del lavarropas los verifica la guarda transaccional, no el service**: cualquier chequeo previo
   sería una ventana TOCTOU con forma de validación. Borrar `CANTIDAD_LAVARROPAS` de `Constantes`
   queda para el Paso 3, que es quien saca su otro uso.

8. **Tests**:
   - `LavarropasDAOTest`: alta con número libre; alta con número existente activo → mensaje A; alta
     con número existente de baja → mensaje B (**el test que distingue los dos carteles**); baja con
     ciclo activo → rechazo y el lavarropas sigue activo; baja limpia; **segunda baja → conflicto**;
     reactivar uno de baja; **reactivar uno activo → conflicto**; `obtenerDibujables()` no trae los
     de baja **sin ciclo** pero **sí los de baja con un ciclo sin finalizar** (el test que sostiene
     el bloque "Un lavarropas de baja con un ciclo abierto"), y `obtenerTodos()` los trae a todos;
     contención de lock → `ConflictoConcurrenciaException` y no `DatabaseException`; un fallo de SQL sale como `DatabaseException` y no como lista vacía.
   - `CicloLavaderoDAOTest`: lanzar una tanda sobre un lavarropas dado de baja →
     `LavarropasDeBajaException` y **cero filas escritas** (ni ciclo, ni elementos, ni insumos).
   - `ConcurrenciaOptimistaTest`: caso nuevo con la forma canónica del archivo — *A arma la tanda →
     B da de baja el lavarropas y commitea → A lanza → conflicto, y el estado final es exactamente
     el de B*.

### Verificación

```bash
mvn test -Dtest=LavarropasDAOTest
mvn test -Dtest=CicloLavaderoDAOTest
mvn test -Dtest=ConcurrenciaOptimistaTest
mvn -q compile      # PantallaCiclos y ConstructorVistaCiclos todavía no compilan: Paso 3
```

### Criterio de salida

- [ ] `lanzarTanda` rechaza un lavarropas inactivo **dentro** de su transacción, y no deja nada escrito
- [ ] El orden **`lavarropas` → `ciclos_lavadero`** es el mismo en `lanzarTanda` y en `darDeBaja`, y
      el javadoc de las dos explica **por qué el de `lavarropas` va primero** (el de ciclos es un gap
      lock y no ordena nada al tomarse)
- [ ] Alta duplicada distingue "ya existe" de "ya existe pero está de baja"
- [ ] `darDeBaja` rutea la contención de lock por `esContencionDeLock` antes de `DatabaseException`
- [ ] `obtenerDibujables()` incluye los inactivos **con ciclo abierto**, y hay un test que lo fija
- [ ] La V27 dropea `capacidad_litros` y en el mismo paso deja de leerse en los cuatro lugares
- [ ] `DatabaseInitializerTest.sanityMaximoLocal` movido a 27
- [ ] `LavarropasDAO` ya no devuelve lista vacía ante un fallo de SQL
- [ ] `CicloLavaderoService` ya no valida contra `CANTIDAD_LAVARROPAS`
- [ ] Commit: `feat: ABM de lavarropas con guarda en el lanzamiento de tandas`

---

## Paso 3 — La grilla de Ciclos se arma desde la base y se puede reconstruir

> Depende del Paso 2.

### Contexto (autocontenido)

`PantallaCiclos.construirGrillaDeCards()` corre **en el constructor** y crea
`CANTIDAD_LAVARROPAS = 13` cards, repartidas en `LAVARROPAS_POR_FILA = 3` columnas independientes
con `BoxLayout` ("masonry": cada columna es su propio eje vertical, así que expandir una card sólo
empuja a las de su columna). `CiclosController` captura `pantalla.getAllCards()` en **su**
constructor y cablea por card: `setOnAccion`, `setOnConfiguracionChanged` y el `TransferHandler` del
DnD.

`ConstructorVistaCiclos.construir(datos, numerosDeCard, staging)` combina lo leído con el staging y
ya descarta el staging de los lavarropas que volvieron **ocupados**, devolviendo la lista de
afectados para que el controller avise. Ése es exactamente el patrón que hay que extender.

### Tareas

1. **`PantallaCiclos`**:
   - El constructor arma el contenedor de la grilla **vacío** y lo mete en el `JSplitPane`. Ninguna
     card nace en el constructor.
   - `public void reconstruirGrilla(List<Integer> numeros)`:
     - **reusa** la `LavarropasCard` de cada número que ya existía en el mapa; crea sólo las nuevas;
       descarta las que ya no están.
     - **repuebla el `LinkedHashMap` desde cero, en el orden de `numeros`** (mapa nuevo, `put`
       ascendente, reusando las instancias que sobreviven) — no `put` al final del mapa viejo. El
       orden de inserción es parte del contrato de `getAllCards()`, y la tarea 4 lo usa para decidir
       si hay que reconstruir.
     - Rehace las columnas y los `Box.createVerticalStrut` / `createVerticalGlue`.
     - `revalidate()` + `repaint()`.
     - Javadoc: **por qué reusa** — recrear todas las cards borraría la configuración que el operador
       está tipeando en cards que no cambiaron, y ése es el mismo invariante que hace que
       `recargar()` no llame a `resetConfiguracion()`.
   - `getAllCards()` sigue devolviendo el mapa (inmodificable) del estado actual.
   - `Constantes.Lavadero.LAVARROPAS_POR_FILA` **se queda** (es layout).
     `CANTIDAD_LAVARROPAS` **se borra** — el Paso 2 ya sacó su otro uso.

2. **`ConstructorVistaCiclos`**:
   - `descartarStagingDeLavarropasOcupados` → `descartarStagingNoLanzable(datos, numerosVigentes,
     staging)`, que descarta el staging de (a) los lavarropas con ciclo activo y (b) **los que ya no
     están en `numerosVigentes`**. En los dos casos, si lo descartado era la fracción de un equipo
     repartido, se deshace el reparto **entero**, incluidas las cards que siguen libres — exactamente
     como hoy.
   - `VistaCiclos` pasa a llevar **dos** listas: `stagingDescartadoPorOcupacion` y
     `stagingDescartadoPorBaja`. Un solo campo obligaría a un cartel que cubra los dos casos, y
     "otro operador lo ocupó" es falso cuando lo que pasó fue una baja. Javadoc con ese motivo.
   - `mapearLavarropas` pierde `getCapacidadLitros()`.
   - Un lavarropas dado de baja **con un ciclo todavía abierto** entra igual en `numerosVigentes`
     —lo trae `obtenerDibujables()` del Paso 2— y por lo tanto **tiene card, en modo activo**. No se
     trata como baja y su staging no se descarta (no puede tener: la card activa no acepta carga).
     Es el único caso en que un número inactivo se dibuja, y **no es defensivo**: sin él, ese ciclo
     queda sin botón Finalizar y la ropa se pierde de Disponibles y de Salidas. Ver el bloque
     correspondiente del Paso 2.
   - El descarte por baja aplica sólo a los que **no** están en `numerosVigentes`, que con
     `obtenerDibujables()` son exactamente los inactivos sin ciclo abierto.

3. **`LavarropasItem`** — se va `capacidadLitros`. **Borrar `LavarropasTableModel`**: cero
   llamadores, era su único lector.

4. **`CiclosController`**:
   - `cards` deja de ser `final`; `inicializarEventos()` se parte en dos: lo global (botones, guards,
     `ComponentListener`) en el constructor, y `cablearCards()` con lo de cada card. Todos esos
     cableados son *setters*, así que volver a correrlos sobre el mapa entero es **idempotente** —
     dejarlo escrito, porque la tentación es cablear sólo las nuevas y eso obliga a llevar un
     registro que se puede desincronizar.
   - En `pintar(DatosCiclos datos)`, **en este orden**:
     ```java
     ciclosActivos = datos.ciclosActivos();
     List<Integer> numeros = datos.lavarropas().stream().map(Lavarropas::getNumero).sorted().toList();
     VistaCiclos vista = ConstructorVistaCiclos.construir(datos, numeros, staging);  // descarta staging
     if (!new TreeSet<>(numeros).equals(new TreeSet<>(cards.keySet()))) {            // sólo si cambió
         pantalla.reconstruirGrilla(numeros);
         cards = pantalla.getAllCards();
         cablearCards();
         configurarDnDCards();
     }
     aplicarCatalogos(datos);   // setJabones + setInsumos + setPegarHabilitado, sobre el mapa YA reconstruido
     // …y recién ahora se vuelca la vista sobre las cards
     ```

     **Tres cosas de ese orden, y ninguna es cosmética:**

     - **El descarte va antes de reconstruir.** Si la card desaparece primero, el staging queda
       huérfano y la ropa no vuelve a Disponibles.
     - **`aplicarCatalogos` va DESPUÉS del re-cableado, y hay que moverlo hasta ahí.** Hoy el bloque
       que hace `card.setJabones(...)` está al **principio** de `pintar` (`CiclosController` ~289-293),
       o sea antes de todo esto. Si se deja donde está, **una card creada en ese mismo `pintar`
       nunca recibe `setJabones` ni `setInsumos`**: se agrega el lavarropas #14 en Ajustes, se entra
       a Ciclos, la card #14 aparece y su **combo de jabón está vacío** — no se puede configurar ni
       lanzar hasta el refresco siguiente. En el Paso 7, donde los catálogos se releen siempre, el
       síntoma se vuelve **intermitente** (falla el primer pintado, anda el segundo), que es la peor
       forma de un bug. El método `aplicarCatalogos(DatosCiclos)` es nuevo y existe para que los tres
       `set*` sobre todas las cards vivan en **un solo lugar** — incluido el `setPegarHabilitado` del
       Paso 8, que si no queda desperdigado y con el mismo problema.
     - **La comparación va por conjunto (`TreeSet`), no por lista.** `cards` es un `LinkedHashMap` y
       `getAllCards()` devuelve una vista de **ese** mapa: si `reconstruirGrilla` reusa el mapa y le
       hace `put` de los nuevos al final, el `keySet()` queda desordenado (`[1..14, 7]` después de
       agregar el #14 y reponer un #7) y una comparación por lista daría `true` **en cada `pintar`** —
       reconstruyendo la grilla y re-creando los `TransferHandler` en cada F5. No pierde datos, pero
       parpadea y trabaja de más en el flujo más delicado de la app. (La tarea 1 pide además que
       `reconstruirGrilla` repueble el mapa desde cero en orden ascendente; el `TreeSet` es el
       cinturón por si alguien cambia eso.)
   - `avisarStagingDescartado` pasa a recibir las dos listas y muestra el `Mensajes.*` que
     corresponda a cada una (o los dos, si hubo de los dos tipos). `lavarropasYaAvisados` sigue
     aplicándose **sólo** a la lista de ocupación: es el cartel del choque el que ya los nombró.
   - El `Consumer<ConflictoConcurrenciaException>` de `lanzar` — donde hoy dice
     `if (choque instanceof LavarropasOcupadoException)` — pasa a un predicado privado nombrado:
     ```java
     /** Los choques cuyo cartel ya nombró los lavarropas de la tanda: el aviso del descarte
      *  que trae la relectura sería el mismo modal por segunda vez. */
     private static boolean nombraLosLavarropasDeLaTanda(ConflictoConcurrenciaException choque) {
         return choque instanceof LavarropasOcupadoException
             || choque instanceof LavarropasDeBajaException;
     }
     ```
     ⚠️ La regla del descarte de staging **no se toca**: sigue siendo `instanceof
     SaldoConsumidoException` y nada más.
   - `abrirDialogoSubdivisionUnidad` filtra `lavarropasItems` por "no ocupado"; ahora
     `lavarropasItems` ya viene sólo con activos. No hay que cambiarlo, pero **verificarlo**.

5. **`DatosCiclos`** — el `@param lavarropas` cambia de *"todos los lavarropas configurados"* a
   *"los lavarropas que la pantalla dibuja: los activos **más** los inactivos que todavía tienen un
   ciclo sin finalizar, que necesitan card para poder finalizarlo"*. **No** decir "los activos": es
   justo la simplificación que pierde ropa.

6. **`CiclosController.leerDatos`** — `lavarropasService.obtenerDibujables()`.

7. **Tests**:
   - `ConstructorVistaCiclosTest`: quitar las aserciones de capacidad; casos nuevos — staging en un
     lavarropas que ya no está en `numerosVigentes` se descarta y aparece en
     `stagingDescartadoPorBaja`; una fracción de equipo repartido en un lavarropas dado de baja
     deshace el reparto entero e incluye **todas** las cards afectadas; ocupación y baja a la vez
     llenan **cada lista con lo suyo**, sin mezclarse.
   - `LavarropasDAOTest`: quitar la aserción de `capacidadLitros == 13`.
   - La reconstrucción de la grilla en sí no se testea (es Swing); se cubre en el smoke.

### Verificación

```bash
mvn test -Dtest=ConstructorVistaCiclosTest
mvn test
mvn clean package && java -jar target/aptium.jar
```

Smoke manual (**sin** `-Daptium.edt.strict=true`):
1. Ciclos muestra 13 cards, igual que antes (los seeds siguen activos).
2. Con la base a mano (`UPDATE lavarropas SET activo = FALSE WHERE numero = 13`), salir y volver a
   entrar a Ciclos → quedan 12 y la grilla no tiene huecos.
3. Cargar ropa en el #12, dar de baja el #12 desde otra sesión/SQL, F5 → la ropa vuelve a
   Disponibles y aparece el cartel de **baja** (no el de ocupación).
4. Repartir un equipo entre #10 y #11, dar de baja el #11, F5 → **las dos** cards quedan vacías y el
   cartel nombra las dos.
5. Configurar el #1 a medias, dar de baja el #13, F5 → la configuración del #1 **sigue ahí** (la card
   se reusó).

### Criterio de salida

- [ ] Los 5 puntos del smoke pasan, el 5 en particular
- [ ] `Constantes.Lavadero.CANTIDAD_LAVARROPAS` ya no existe
- [ ] `LavarropasTableModel` borrado; `grep -rn "capacidadLitros\|capacidad_litros"` sólo devuelve V10 y V27
- [ ] La regla "el staging se descarta sólo ante `SaldoConsumidoException`" quedó intacta
- [ ] Commit: `feat: la grilla de ciclos se arma desde la base y se reconstruye`

---

## Paso 4 — Baja lógica de los catálogos de Lavadero (elementos, jabones, insumos)

> Depende del Paso 1. **Paralelo con los Pasos 2, 3 y 5.**

### Contexto (autocontenido)

**La regla, que gobierna todo el paso:** las consultas de **CARGA** (lo que alimenta un combo, una
lista o un autocompletado donde el operador *elige* algo nuevo) filtran por `activo = TRUE`. Los
**joins históricos** (Ver Ciclos, Historial, Salidas, Lotes, Ver Equipos) **no filtran nunca**: un
elemento dado de baja tiene que seguir mostrando su nombre en los registros viejos. Una fila que
aparece sin nombre, o que desaparece de un historial, **no se parece a un error y nadie la reporta**.

Clasificación de cada consulta que toca estos tres catálogos (**verificada contra el código**):

| Consulta | Dónde | Clase | Qué hacer |
|---|---|---|---|
| `CatalogoElementosLavaderoDAO.findAll` | combo de Clasificación | **CARGA** | agregar `findActivos()`; `findAll()` queda para Ajustes |
| `CatalogoElementosLavaderoDAO.buscarPorNombre` | rechazo de alta duplicada | **ninguna** | ⚠️ **NO filtrar.** Si filtrara, se podría dar de alta un nombre que ya existe dado de baja → violación de `UNIQUE` con forma de error técnico |
| `CatalogoJabonesDAO.findAll` | combo de jabón de la card | **CARGA** | `findActivos()` + `findAll()` |
| `CatalogoInsumosDAO.findAll` | combo de insumos de la card (plan A) | **CARGA** | `findActivos()` + `findAll()` |
| `CicloLavaderoDAO` — `SQL_ELEMENTOS_DE_CICLO`, `SQL_DISPONIBLES`, `SQL_LINEAS_SOBREGIRADAS` | ciclos en curso / disponibles / diagnóstico | **HISTÓRICO** | no tocar |
| `CicloLavaderoDAO` — `SQL_ACTIVOS`, `SQL_FINALIZADOS`, `SQL_TODOS`, `SQL_INSUMOS_*` | Ver Ciclos y la card | **HISTÓRICO** | no tocar |
| `HistorialLavaderoDAO` (5 joins) · `SalidaLavaderoDAO` (4 joins) | consulta y salidas | **HISTÓRICO** | no tocar |

> Nótese que `SQL_DISPONIBLES` es histórico y no de carga: es ropa **ya clasificada**, esperando un
> ciclo. Dar de baja el elemento no puede hacerla desaparecer de la cola — quedaría ropa física
> imposible de procesar.

### Tareas

1. **`CatalogoElementosLavaderoDAO`**:
   - `findActivos()` — `WHERE activo = TRUE ORDER BY nombre`.
   - `findAll()` — sin filtro; el `ElementoCatalogo` pasa a llevar `boolean activo` para que Ajustes
     pueda mostrar el estado.
   - `darDeBaja(int id)` / `reactivar(int id)` — CAS con `ControlConcurrencia.exigirFilaAfectada` y
     `Mensajes.CONFLICTO_CATALOGO`.
   - `agregar(...)` — si el `UNIQUE` choca, distinguir "ya existe" de "ya existe pero está dado de
     baja", igual que el alta de lavarropas del Paso 2 (mismo patrón, mensajes propios).
   - Errores como `DatabaseException`, nunca lista vacía. Hoy `findAll()` traga el `SQLException`:
     con la regla nueva, eso deja el combo de Clasificación vacío sin decir por qué.

2. **`CatalogoJabonesDAO`** y **`CatalogoInsumosDAO`** — el mismo juego:
   `findActivos()`, `findAll()`, `agregar(nombre)`, `darDeBaja(id)`, `reactivar(id)`.
   ⚠️ **`darDeBaja` de un insumo choca con las tandas en vuelo, y tiene que pasar por
   `ControlConcurrencia.esContencionDeLock`.** El `INSERT` en `insumos_ciclo_lavadero` que hace
   `lanzarTanda` toma un *shared lock* por FK sobre la fila de `catalogo_insumos` y lo sostiene
   hasta el commit de la tanda; el `UPDATE … SET activo = FALSE` se queda esperando detrás, hasta
   los 50 s del `innodb_lock_wait_timeout`. Sin el chequeo, eso sale como error técnico en vez de
   como *"alguien está usando ese insumo en este momento, probá de nuevo"*. **Lo mismo vale para
   jabones** (`ciclos_lavadero.jabon_id`, FK RESTRICT desde V12) y para **elementos de lavadero**
   (`elementos_clasificacion_lavadero.elemento_id`). Es la misma regla que el javadoc de
   `lanzarTanda` ya aplica, heredada por FK. Anotado también en los riesgos del plan A.
   `JabonCatalogo` gana `boolean activo`; `InsumoCatalogo` ya lo tiene (plan A).
   ⚠️ `JabonCatalogo` es una clase **sin `equals`** y sus objetos se comparan por referencia en el
   combo. Agregarle un campo no cambia eso, pero el Paso 7 va a comparar jabones **por `getId()`**:
   dejarlo escrito en su javadoc.

3. **Services** — `ClasificacionLavaderoService.obtenerCatalogo()` pasa a `findActivos()`;
   `CatalogoJabonesService.obtenerTodos()` → `obtenerActivos()` para la card, más
   `obtenerTodosIncluidosDeBaja()` para Ajustes. Ídem insumos. Los nombres tienen que decir cuál es
   cuál: un `obtenerTodos()` que en realidad filtra es la forma más barata de reintroducir el bug.
   Altas/bajas/reactivaciones con `ValidationException.builder()` (nombre no vacío, id > 0).

4. **Guarda al clasificar.** El combo sólo ofrece activos, pero una baja puede ocurrir entre que la
   pantalla pintó y que el operador guarda. En la transacción de
   `ClasificacionLavaderoDAO.guardar`, antes de insertar las líneas, verificar que todos los
   `elemento_id` estén activos:
   ```sql
   SELECT id FROM catalogo_elementos_lavadero WHERE id = ? AND activo = TRUE
   ```
   Sin fila → `BusinessException(Mensajes.ELEMENTO_DE_BAJA)`, con el nombre del elemento en el
   mensaje. Es el *"se rechaza con aviso"* del pedido, en el único lugar donde no se puede esquivar.

5. **Alta de elemento desde Clasificación** (`ClasificacionController.agregarElementoCatalogo`):
   el rechazo por nombre ya dado de baja llega solo desde el DAO con su mensaje propio, y
   `mostrarFallo` ya rutea `BusinessException` como aviso al usuario. **Verificarlo**, no asumirlo.

6. **Tests**:
   - `CatalogoElementosLavaderoDAOTest` / `CatalogoJabonesDAOTest` / `CatalogoInsumosDAOTest`:
     `findActivos()` no trae los de baja y `findAll()` sí; baja + segunda baja → conflicto;
     reactivar; alta con nombre de uno dado de baja → mensaje específico; **`buscarPorNombre`
     encuentra uno dado de baja** (el test que atrapa el `WHERE activo` puesto de más).
   - `CicloLavaderoDAOTest` / `HistorialLavaderoDAOTest` / `SalidaLavaderoDAOTest`: un elemento dado
     de baja **sigue apareciendo con su nombre** en un ciclo, un historial y una salida ya
     existentes. Son los tests que atrapan el filtro puesto donde no va — y son los únicos que lo
     harían.
   - `ClasificacionLavaderoServiceTest` / DAO: guardar con un elemento dado de baja → rechazo y
     **cero filas escritas**.

### Verificación

```bash
mvn test -Dtest=Catalogo*Test
mvn test -Dtest=Clasificacion*Test
mvn test -Dtest=HistorialLavaderoDAOTest
mvn test
```

### Criterio de salida

- [ ] La tabla de clasificación CARGA/HISTÓRICO de arriba está implementada consulta por consulta
- [ ] Existe un test por cada join histórico que prueba que un elemento de baja **sigue visible**
- [ ] `buscarPorNombre` no filtra, y hay un test que lo fija
- [ ] Clasificar con un elemento de baja se rechaza dentro de la transacción
- [ ] Commit: `feat: baja logica de los catalogos de lavadero`

---

## Paso 5 — Baja lógica de Ortopedias y Otros

> Depende del Paso 1. **Paralelo con los Pasos 2, 3 y 4.**

### Contexto (autocontenido)

**Ortopedias ya está casi hecho.** `catalogo_descripciones.vigente` existe desde V16 y
`obtenerDescripcionVigente` la respeta en los dos flujos de carga:
`IngresoOrtopediaController` (el `CatalogoLookup` de la línea ~60 y la resolución de la ~70) y las
diez rutas de `EquipoCorreccionService` (~119, ~305, ~380). Los joins de `EquipoDAO`, `MaterialDAO`
y `LoteDAO` resuelven la descripción **sin** filtrar, que es lo correcto. Lo único que falta es la
pantalla que prenda y apague `vigente`, y **auditar** que ninguna consulta de carga se haya quedado
usando `obtenerDescripcion` (sin "Vigente") por error.

Sobre la decisión del usuario para Correcciones: **un equipo que ya tiene un material dado de baja
se guarda igual**. El comportamiento actual de `EquipoCorreccionService` hay que **verificarlo**
contra esa decisión: si hoy rechaza el guardado por encontrar un código no vigente en un material
que el operador no tocó, eso es un cambio de este paso; si sólo lo rechaza al *asignar* uno nuevo,
no hay nada que hacer y se deja escrito que se verificó.

**Otros** es texto libre: `CatalogoOtrosDAO.obtenerOCrear(conn, descripcion)` hace
`INSERT IGNORE` + `SELECT id`, dentro de la transacción de `EquipoOtrosDAO.guardar`. Dato importante:
**`equipo_otros_materiales` guarda `descripcion` como snapshot**, además del `catalogo_otros_id`, así
que el historial de Otros **no hace `JOIN` al catálogo** y una baja no le toca nada.

### Tareas

1. **`CatalogoDAO`** — auditar y completar:
   - Correr `grep -rn "obtenerDescripcion\b\|obtenerTodasLasDescripciones\|obtenerVolumen"
     src/main/java` y **clasificar cada método por sus llamadores** con la regla CARGA/HISTÓRICO del
     Paso 4. Dejar la tabla resultante en el mensaje de commit y en `CLAUDE.md` (Paso 9). Lo que ya
     se sabe: `obtenerDescripcionVigente` es CARGA y ya filtra; `obtenerVolumen`/`obtenerVolumenes`
     son **históricos** (un material ya cargado con un código retirado sigue necesitando su volumen
     para el lote) y **no deben filtrar**.
   - `List<ItemCatalogo> obtenerTodosConEstado()` — `(codigo, descripcion, vigente)`, para Ajustes.
   - `darDeBaja(int codigo)` / `reactivar(int codigo)` — CAS sobre `vigente`, con
     `exigirFilaAfectada`.
   - ⚠️ **No cablear `guardarDescripcion`.** Es el upsert que
     `plans/hallazgos-arquitectura-pendientes.md` **#10** marca como *"la única entrada de esta lista
     que necesitaría guarda el día que deje de estar muerta"*. Este plan **no** agrega edición de
     descripciones ni de volúmenes: sólo prende y apaga `vigente`. Dejarlo escrito para que #10 siga
     siendo cierto después de este plan.

2. **`CatalogoOtrosDAO`**:
   - `buscarPorDescripcionParcial` (el autocompletado) → `AND activo = TRUE`. **Sigue siendo
     síncrono**: no se toca su forma.
   - `obtenerOCrear(Connection conn, String descripcion)` — el cambio central:
     ```java
     // 1. INSERT IGNORE (no-op si ya existe, activa o no)
     // 2. SELECT id, activo WHERE descripcion = ?
     // 3. si activo = FALSE  ->  BusinessException(Mensajes.MATERIAL_OTROS_DE_BAJA)
     //    La transacción de EquipoOtrosDAO.guardar se revierte sola al no commitear.
     ```
     El orden importa: dejar el `INSERT IGNORE` **antes** del `SELECT` conserva la atomicidad que ya
     tenía (sin ventana entre chequeo y creación), y como es un no-op cuando la fila existe, **una
     descripción dada de baja nunca se re-crea**. Invertirlo a SELECT-primero reintroduce la carrera
     que ese `INSERT IGNORE` vino a cerrar. Javadoc con este razonamiento.
   - `obtenerTodosConEstado()`, `darDeBaja(id)`, `reactivar(id)` para Ajustes, con CAS.
   - `obtenerIdPorDescripcion` — **no filtra** (es lookup exacto, no carga).

3. ⚠️ **Hay un SEGUNDO `obtenerOCrear` sobre `catalogo_otros`, y NO se toca.**
   `LoteDAO.obtenerOCrearCatalogoOtros` (privado, ~1031-1048) crea entradas al **partir un material
   "Otros" para meterlo en un lote**. Sumalo a la tabla de clasificación:

   | Consulta | Dónde | Clase | Qué hacer |
   |---|---|---|---|
   | `LoteDAO.obtenerOCrearCatalogoOtros` | split de un material "Otros" al armar un lote | **HISTÓRICO** | **No tocar.** Es material **ya cargado** que se parte en dos filas; rechazarlo por una baja de catálogo rompe el armado del lote de algo que ya está en el CDE |

   La tentación —y es fuerte, porque los dos métodos se llaman casi igual y hacen casi lo mismo— es
   **unificarlos**. Hacerlo mete la regla de carga en un camino histórico: armar un lote con un
   material cuya descripción se dio de baja empezaría a fallar dentro de la transacción del lote.
   Es exactamente el error que este plan define como el caro.

   Nota aparte, que hay que dejar escrita porque el javadoc nuevo de `CatalogoOtrosDAO.obtenerOCrear`
   va a afirmar lo contrario: **el de `LoteDAO` es `SELECT`-primero-`INSERT`-después**, o sea que
   tiene justo la carrera que el otro cierra con `INSERT IGNORE`. **No es trabajo de este plan
   arreglarlo** (es un camino histórico, con la descripción ya en el snapshot de
   `equipo_otros_materiales`), pero sí decirlo, para que nadie lea el javadoc nuevo como si
   describiera a los dos.

3. **`CatalogoOtrosService` / `CatalogoService`** — exponer lo nuevo, sin JDBC.

4. **`Constantes.Mensajes`** — `MATERIAL_OTROS_DE_BAJA` (*"«X» está dado de baja y no se puede
   cargar. Elegí otra descripción o reactivalo desde Ajustes."*) y `ELEMENTO_DE_BAJA` si el Paso 4
   no lo creó primero — **coordinar**: los dos pasos tocan `Mensajes`, es su único archivo compartido
   y el único punto de conflicto si se ejecutan en paralelo.

5. **Tests**:
   - `CatalogoOtrosDAOTest`: guardar un equipo con una descripción dada de baja → rechazo, **la fila
     del catálogo sigue de baja** y **el equipo no se creó**; con una descripción nueva → se crea
     activa; el autocompletado no ofrece las de baja pero `obtenerIdPorDescripcion` sí las encuentra.
   - `CatalogoDAOTest`: baja/reactivación con CAS; `obtenerDescripcionVigente` devuelve `null` para
     una de baja (ya existía); **`obtenerVolumen` la sigue devolviendo** — el test que fija que el
     volumen es histórico.
   - `EquipoOtrosDAOTest`: el guardado completo con descripción de baja no deja nada escrito.
   - `EquipoCorreccionServiceTest` / `EquipoOtrosCorreccionServiceTest`: **guardar un equipo que ya
     tenía un material con código no vigente, sin tocar ese material, funciona.** Es el test que fija
     la decisión del usuario.

### Verificación

```bash
mvn test -Dtest=CatalogoDAOTest
mvn test -Dtest=CatalogoOtrosDAOTest
mvn test -Dtest=EquipoOtrosDAOTest
mvn test -Dtest=Equipo*CorreccionServiceTest
mvn test
```

### Criterio de salida

- [ ] La tabla CARGA/HISTÓRICO de `CatalogoDAO` está hecha y escrita, método por método
- [ ] `obtenerOCrear` rechaza una descripción de baja **sin re-crearla** y revierte la transacción
- [ ] Correcciones guarda un equipo con un material ya de baja que no se tocó
- [ ] `CatalogoDAO.guardarDescripcion` **sigue sin llamador**
- [ ] Cero autocompletados nuevos y cero autocompletados vueltos asíncronos
- [ ] Commit: `feat: baja logica de los catalogos de ortopedias y otros`

---

## Paso 6 — Ajustes en pestañas

> Depende de los Pasos 2, 4, 5 y **7** (necesita los services, incluido
> `JabonPorTipoLavadoService`). El Paso 3 no es estrictamente necesario
> para compilar, pero sí para que la pestaña de lavarropas tenga efecto visible.

### Contexto (autocontenido)

`PantallaAjustes` hoy es un `BorderLayout` con `PanelGestionClientes` al centro y el botón de
actualizaciones al sur. `AjustesController` maneja clientes + actualizaciones, y su
`componentShown` limpia la búsqueda y recarga. Todo el acceso a base va por `TareaUI`
(`AjustesController.mutar` es el molde: escribe en fondo, recarga y notifica).

### Tareas

1. **`PantallaAjustes`** — al centro un `JTabbedPane` con cuatro pestañas:
   `Clientes` · `Catálogos` · `Lavarropas` · `Jabones e insumos`. El botón de actualizaciones queda
   donde está (al sur, fuera de las pestañas): no pertenece a ninguna.
   - `Catálogos` contiene un `JTabbedPane` anidado con `Lavadero` / `Ortopedias` / `Otros`.
   - Getters por panel, como hoy `getPanelClientes()`.

2. **`ajustes/view/PanelCatalogoSimple.java`** — un panel ABM genérico, calcado de
   `PanelGestionClientes` (tabla + buscador con `RowFilter` + barra de botones):
   columnas `{ "Nombre", "Estado" }`, botones **Agregar · Dar de baja · Reactivar**, y
   `setOnAgregar/ setOnDarDeBaja / setOnReactivar` + `getSeleccionado()`.
   Lo sirve a **cinco** listas (elementos de lavadero, ortopedias, otros, jabones, insumos): es el
   caso en que la repetición es real y no especulativa. Ortopedias necesita además una columna
   `Código`, así que el panel recibe la definición de columnas o se hace un
   `PanelCatalogoOrtopedias` aparte — **decidir al escribirlo, con el criterio de que la variante
   más simple gane**; si el genérico se llena de flags, son dos paneles.
   **Cero I/O, cero services.**

3. **`ajustes/view/PanelGestionLavarropas.java`** — tabla `{ "N°", "Estado" }` + botones
   **Agregar · Dar de baja · Reactivar**. El alta pide el número con un diálogo chico
   (`RestriccionesCampo.soloNumeros`).

4. **`ajustes/view/PanelJabonesEInsumos.java`** — dos `PanelCatalogoSimple` lado a lado (jabones e
   insumos) más, abajo, **dos combos de jabón por defecto**, uno por `TipoLavado`, con un botón
   Guardar. Los combos ofrecen sólo jabones **activos**, más una opción *"(sin default)"* que borra
   la fila.

5. **Controllers nuevos**, uno por pestaña, cada uno con **sus** services en la firma:
   - `CatalogosAjustesController(panelesDeCatalogo…, ClasificacionLavaderoService, CatalogoService, CatalogoOtrosService, Runnable onMutacion)`
   - `LavarropasAjustesController(PanelGestionLavarropas, LavarropasService, Runnable onMutacion)`
   - `JabonesInsumosAjustesController(PanelJabonesEInsumos, CatalogoJabonesService, CatalogoInsumosService, JabonPorTipoLavadoService, Runnable onMutacion)`
     — **el Paso 7 ya corrió** (ver el grafo de dependencias), así que `JabonPorTipoLavadoService`
     existe y esta pestaña se cablea **entera**, defaults incluidos. No hay nada que dejar
     deshabilitado: un combo que no guarda nada no se cablea, y ésa era la única alternativa.
   - Todos copian `AjustesController.mutar(...)`: escritura por `TareaUI`, recarga, `onMutacion`.
   - **Carga por pestaña: `addChangeListener` sobre el `JTabbedPane`, no `componentShown`.**
     Decidido acá y no en la ejecución. `JTabbedPane` no garantiza `setVisible(false)` sobre las
     pestañas no seleccionadas de forma uniforme, y `componentShown` sólo dispara cuando la
     visibilidad **cambia**; encima `AjustesController` ya tiene su propio `componentShown` sobre la
     pantalla entera, así que serían dos mecanismos conviviendo. Y dejarlo "a verificar en el smoke"
     no sirve: **una pestaña que carga de más —al entrar a Ajustes en vez de al seleccionarse— se ve
     exactamente igual que una que carga bien**, así que el smoke no puede distinguirlas sin
     instrumentar.
     - `PantallaAjustes` expone `setOnPestanaSeleccionada(Consumer<Integer>)` (o un `Runnable` por
       pestaña), y cada controller registra **el suyo**. La pregunta "¿ya cargué?" queda del lado
       del controller, que es donde se puede responder.
     - El `JTabbedPane` **anidado** de Catálogos lleva su propio `ChangeListener`: al entrar a
       "Catálogos" carga **sólo la sub-pestaña seleccionada** (Lavadero, que es la primera), y las
       otras dos al seleccionarse. El plan tiene que decirlo porque si no, "entrar a Catálogos"
       termina cargando los tres.
     - No dejarlo cargando todo al entrar a Ajustes: son cinco catálogos completos que casi siempre
       no se miran.
     - Cada controller recarga además después de **cada mutación suya** (ya lo hace `mutar`), así que
       el `ChangeListener` es sólo el disparo de entrada.
   - `BusinessException` → aviso al usuario con su propio mensaje (ya es el ruteo de
     `AjustesController.mutar`); cualquier otra cosa → error genérico.

6. **`AppContext`** — los DAOs/services nuevos. Son **cinco lugares por service** y **ningún test
   atrapa el que falte** (`new AppContext(` sólo aparece en `createDefault`): el parámetro en el
   constructor explícito largo, la cadena de `|| … == null` que rechaza dependencias faltantes, la
   asignación del campo, la construcción en `createDefault()` y el getter. Un parámetro agregado sin
   sumarlo a la guarda de `null` compila, pasa toda la suite y explota con un `NullPointerException`
   diferido recién cuando alguien abre la pantalla.

7. **`UiCoordinator`** — instanciar los tres controllers nuevos y pasarles `operativo` como
   `onMutacion`, igual que `ajustesController.setOnMutacion(operativo)`.
   **No** se crea un grupo de refresco nuevo: ver la decisión de diseño correspondiente.

### Verificación

```bash
mvn -q compile && mvn test
mvn clean package && java -jar target/aptium.jar
```

Smoke manual:
1. Ajustes → las cuatro pestañas abren y cada una carga **al seleccionarse**, no antes.
2. Dar de baja un elemento de lavadero → volver a Lavadero → Clasificar: **ya no está en el combo**.
3. Reactivarlo → vuelve a aparecer.
4. Dar de baja una descripción de Otros, ir a cargar un ingreso "Otros" y tipearla → **se rechaza al
   guardar, con su mensaje**, y el equipo no se crea.
5. Dar de baja un código de ortopedias → Ingreso ya no lo acepta; un equipo viejo que lo tiene
   **sigue mostrando su descripción** en Ver Equipos y se puede corregir y guardar.
6. Agregar el lavarropas #14 → Ciclos muestra 14 cards. Dar de baja el #14 → vuelve a 13.
6b. **La card del #14 tiene el combo de jabón y el de insumos poblados en el PRIMER pintado**, y se
   puede configurar y lanzar sin salir y volver a entrar. Contar cards no alcanza: este punto es el
   único que atrapa que `aplicarCatalogos` haya quedado antes del re-cableado.
6c. Con un ciclo en curso en el #12, dar de baja el #12 **por SQL** (saltando la guarda) y entrar a
   Ciclos → la card del #12 **sigue estando, en modo OCUPADO**, y su botón Finalizar funciona. Al
   finalizar, la card desaparece sola en la relectura.
7. Intentar agregar el #13 (existe, activo) y un número dado de baja → **dos mensajes distintos**.
8. Intentar dar de baja un lavarropas con ciclo en curso → rechazo con su mensaje.
9. Log: ningún WARN de `EdtGuard` desde los controllers nuevos.

### Criterio de salida

- [ ] Los 9 puntos del smoke pasan
- [ ] Cada controller nuevo declara **sólo** los services de su alcance
- [ ] Ninguna pestaña hace I/O en el EDT
- [ ] Commit: `feat: ajustes en pestanas con ABM de catalogos y lavarropas`

---

## Paso 7 — Jabón automático por tipo de lavado (AUTO / MANUAL)

> Depende de los Pasos 1, 3 y 4. **Se ejecuta ANTES del Paso 6**, aunque tenga número mayor: el
> Paso 6 necesita `JabonPorTipoLavadoService` para cablear los combos de "jabón por defecto", y este
> paso no necesita nada de Ajustes (los defaults iniciales ya los sembró la V26). La dependencia es
> unidireccional 6 → 7; los números quedan como están y el orden lo manda este grafo, como en el
> resto de los planes del repo.

### Contexto (autocontenido)

Hoy `LavarropasCard.setJabones` **deja el combo sin selección** después de llenarlo, y su javadoc
(~280-284) dice que el jabón *"es una elección explícita del operador como el tipo de lavado, no un
default que se lleva puesto sin mirar"*. Este paso lo cambia: hay que **reescribir ese javadoc**, no
borrarlo.

Y los catálogos hoy se leen **una sola vez** por sesión (`jabonesCargados`, y `insumosCargados` del
plan A). Con Ajustes editándolos, eso deja a Ciclos mostrando un catálogo viejo hasta reiniciar la
app.

**La regla del usuario, escrita en positivo:** *una elección a mano siempre pesa más que la
automática.*

### Tareas

1. **`lavadero/model/OrigenJabon.java`** — `enum { AUTO, MANUAL }`.

2. **`lavadero/dao/JabonPorTipoLavadoDAO`** + **`service/JabonPorTipoLavadoService`**:
   - `Map<TipoLavado, JabonCatalogo> obtenerDefaults()` — `JOIN catalogo_jabones`.
     ⚠️ **El join no filtra por `activo`** — el default puede estar inactivo, y eso lo tiene que
     decidir la regla (no tocar nada), no la consulta. Si la consulta lo escondiera, Ajustes no
     podría mostrar "el default de Sucio es un jabón dado de baja", que es justo lo que el operador
     necesita ver para arreglarlo.
   - `guardar(TipoLavado, int jabonId)` — upsert (`INSERT … ON DUPLICATE KEY UPDATE` en MySQL; en H2
     modo MySQL funciona igual, **verificarlo con un test**; si no, `DELETE` + `INSERT` en una
     transacción).
   - `borrar(TipoLavado)` — la opción "(sin default)".
   - Guarda: estas escrituras son ABM de Ajustes con **una sola fila por tipo**. No hay dato leído
     por el operador que se esté pisando en el sentido del bloqueo optimista: el upsert **es** la
     intención completa. Sin guarda, y **con esa decisión escrita en el javadoc** — es una excepción
     razonada, no un olvido.

3. **`lavadero/controller/helpers/SelectorJabonAutomatico.java`** — **clase plana, sin Swing**, que
   es donde vive toda la regla:
   ```java
   /**
    * Qué jabón corresponde poner cuando cambia el tipo de lavado.
    * Devuelve vacío cuando NO hay que tocar nada.
    */
   public static Optional<JabonCatalogo> alCambiarTipo(
       TipoLavado tipoNuevo, JabonCatalogo jabonActual, OrigenJabon origen,
       Map<TipoLavado, JabonCatalogo> defaults)
   ```
   | Situación | Resultado |
   |---|---|
   | `origen == MANUAL` y `jabonActual != null` | vacío — **la elección a mano manda** |
   | `jabonActual == null` **o** `origen == AUTO` | el default de `tipoNuevo`… |
   | …y no hay default para ese tipo | vacío |
   | …y el default existe pero `!activo` | vacío |
   | `tipoNuevo == null` (se deseleccionó) | vacío |

   Con su test unitario: un caso por fila, más el caso del pedido
   (*Sucio → Skip AUTO → cambio a Limpio ⇒ Lider*) y su inverso
   (*Sucio → el operador elige Lider a mano → cambio a Limpio ⇒ sigue Lider*).

4. **`LavarropasCard`**:
   - Campo `private OrigenJabon origenJabon = OrigenJabon.AUTO;` — arranca AUTO **con el jabón en
     `null`**, para que la primera elección de tipo lo complete.
   - Campo `private boolean aplicandoCambioProgramatico = false;` — el `setSelectedItem` del código
     dispara el mismo `ActionListener` que un click del operador, y hay que distinguirlos. Es el
     patrón `silenciandoCallback` de `PantallaHistorialLavadero`.
   - `ActionListener` de `cmbJabon`: si **no** está el flag, `origenJabon = MANUAL`.
   - `public void setJabonAutomatico(JabonCatalogo j)` — setea con el flag puesto y deja
     `origenJabon = AUTO`.
   - `public void setJabonManual(JabonCatalogo j)` — para el pegado del Paso 8; deja `MANUAL`.
   - `public OrigenJabon getOrigenJabon()`.
   - `public void setOnTipoLavadoChanged(Runnable r)` — canal **aparte** de
     `onConfiguracionChanged`; el controller lo usa para correr `SelectorJabonAutomatico`.
   - `resetConfiguracion()` — además de lo que ya hace, `origenJabon = AUTO`.
   - **`setJabones` cambia de comportamiento:** en vez de dejar el combo sin selección, **conserva
     la selección actual si su `getId()` sigue estando en la lista nueva**; si no está, la limpia y
     vuelve a `AUTO`. La comparación es **por `getId()`**: dos lecturas del catálogo dan objetos
     `JabonCatalogo` distintos para el mismo jabón, y `JabonCatalogo` no tiene `equals`.
     **Reescribir el javadoc de ~280-284**, que hoy afirma lo contrario.

5. **`CiclosController`**:
   - Un service más en la firma: `JabonPorTipoLavadoService`. Campo
     `private Map<TipoLavado, JabonCatalogo> defaultsJabon = Map.of();` — estado de controller, sólo
     EDT.
   - **Borrar `jabonesCargados` e `insumosCargados`.** `leerDatos` trae jabones, insumos y defaults
     **en cada lectura**, y todo lo que se vuelca sobre las cards sigue pasando por el
     **`aplicarCatalogos(datos)`** que creó el Paso 3 — que corre **después** del re-cableado de la
     grilla. Con los catálogos releídos siempre, dejarlo antes hace que el bug de "card nueva con
     los combos vacíos" se vuelva **intermitente**: falla el primer pintado y anda el segundo. Ahora se puede, porque `setJabones` y `setInsumos` ya no destruyen lo
     elegido — y es lo que hace que un cambio en Ajustes se vea al volver a Ciclos. Son tres
     consultas chicas, **secuenciales** como el resto de `leerDatos`.
   - `DatosCiclos` gana `Map<TipoLavado, JabonCatalogo> defaultsJabon`; los `@param` de `jabones` e
     `insumos` pierden la frase *"o lista vacía si esta carga no lo pidió"*.
   - En `cablearCards()`:
     ```java
     card.setOnTipoLavadoChanged(() ->
         SelectorJabonAutomatico.alCambiarTipo(
                 card.getTipoLavado(), card.getJabon(), card.getOrigenJabon(), defaultsJabon)
             .ifPresent(card::setJabonAutomatico));
     ```

6. **`AppContext` y `UiCoordinator`** — `CiclosController` gana un service en la firma, así que los
   dos dejan de compilar si no se tocan en **este** paso (el Paso 6, que también los toca, corre
   después). En `AppContext` son los **cinco lugares** de siempre para `JabonPorTipoLavadoDAO` y su
   service — parámetro del constructor explícito largo, guarda de `null`, asignación,
   `createDefault()` y getter —; en `UiCoordinator`, el argumento nuevo del `new CiclosController(…)`.
   Ningún test construye `AppContext`, así que el lugar que falte no lo delata nada hasta que alguien
   abra Ciclos.

7. **Tests**:
   - `SelectorJabonAutomaticoTest` — la tabla entera.
   - `LavarropasCardTest`: elegir jabón en el combo deja `MANUAL`; `setJabonAutomatico` deja `AUTO` y
     **no** dispara el marcado a MANUAL (el test del flag); `resetConfiguracion` vuelve a `AUTO`;
     `setJabones` con un catálogo que todavía contiene el jabón elegido **lo conserva**, y con uno
     que no lo contiene **lo limpia**.
   - `JabonPorTipoLavadoDAOTest`: defaults iniciales; upsert; borrado; un default cuyo jabón está
     **inactivo** se sigue devolviendo (es Ajustes quien lo tiene que mostrar).

### Verificación

```bash
mvn test -Dtest=SelectorJabonAutomaticoTest
mvn test -Dtest=LavarropasCardTest
mvn test -Dtest=JabonPorTipoLavadoDAOTest
mvn clean package && java -jar target/aptium.jar
```

Smoke manual:
1. Card libre: elegir **Sucio** → el jabón se completa solo con **Skip**.
2. Cambiar a **Limpio** → pasa a **Lider** (seguía AUTO).
3. Elegir **Skip** a mano; cambiar a **Sucio** y volver a **Limpio** → **sigue Skip** en las dos.
4. Ajustes → cambiar el default de Sucio a otro jabón → volver a Ciclos → elegir Sucio en una card
   nueva → aparece el jabón nuevo.
5. Ajustes → dar de baja el jabón que es default de Limpio → Ciclos → elegir Limpio → **no se toca
   nada** y el combo no ofrece ese jabón.
6. Con una card configurada a medias, **F5** → la configuración sigue, el jabón elegido sigue, y el
   origen no cambió (probarlo cambiando el tipo después del F5).

### Criterio de salida

- [ ] Los 6 puntos del smoke pasan, el 3 y el 6 en particular
- [ ] Toda la regla vive en `SelectorJabonAutomatico`, sin Swing, con test propio
- [ ] El javadoc de `setJabones` describe el comportamiento nuevo
- [ ] `jabonesCargados` / `insumosCargados` ya no existen
- [ ] Commit: `feat: jabon automatico por tipo de lavado, configurable en ajustes`

---

## Paso 8 — Copiar y pegar la configuración de una card *(cambio 5)*

> Depende del Paso 7 (el pegado tiene que dejar el jabón en MANUAL) y del plan A (los insumos).

### Contexto (autocontenido)

El portapapeles es **estado de controller**: se toca sólo en el EDT, es un valor inmutable, y
**sobrevive a una reconstrucción de la grilla** (Paso 3) justamente porque no vive en ninguna card.

Los botones van **dentro de `panelConfig`**, que `setModoActivo` oculta y `setModoStaging` muestra:
así "una card ocupada no se puede copiar ni pegar" sale gratis, sin una sola condición.

### Tareas

1. **`lavadero/model/ConfiguracionCopiada.java`** — `record (TipoLavado tipo, JabonCatalogo jabon,
   BigDecimal litrosJabon, List<InsumoCatalogo> insumos)`, con `List.copyOf` en el constructor
   compacto. Cualquier campo puede ser `null` / vacío: se copia **lo que hay**, no lo que debería
   haber. Copiar una card a medio configurar es un caso normal.

2. **`Constantes.Botones`** — `COPIAR`, `PEGAR`. **`Mensajes`** — `PEGADO_OMITIO_INACTIVOS`
   (con `%s` = lista de nombres): *"No se pegaron estos ítems porque fueron dados de baja: %s.
   Completá la configuración a mano."*

3. **`LavarropasCard`**:
   - Una fila al pie de `panelConfig` con dos botones chicos (`FONT_CONFIG`), `setOnCopiar(Runnable)`
     y `setOnPegar(Runnable)`, más `setPegarHabilitado(boolean)`.
   - `public ConfiguracionCopiada copiarConfiguracion()` — arma el record desde los getters. **No**
     incluye los elementos cargados.
   - `public void pegarConfiguracion(ConfiguracionCopiada c)` — **en este orden**:
     1. el **tipo** (dispara `onTipoLavadoChanged`, que puede poner el jabón automático);
     2. el **jabón**, con `setJabonManual(...)` — así pisa al automático que acaba de correr y queda
        `MANUAL`. **Al revés, el automático del tipo pisaría el jabón pegado**: es el orden el que
        hace correcta esta operación, no un flag;
     3. los **mL** (vacío si `litrosJabon == null`);
     4. los **insumos** (reemplazan la lista, no se suman).
   - **No toca** los elementos cargados ni el estado de la tabla.
   - Javadoc con el porqué del orden.

4. **`CiclosController`**:
   - `private ConfiguracionCopiada portapapeles = null;` — sólo EDT.
   - En `cablearCards()`:
     ```java
     card.setOnCopiar(() -> { portapapeles = card.copiarConfiguracion(); actualizarBotonesPegar(); });
     card.setOnPegar(() -> pegarEn(card));
     ```
   - `actualizarBotonesPegar()` — `setPegarHabilitado(portapapeles != null)` en todas las cards.
     Se llama al copiar, al final de `pintar` y después de cada reconstrucción de la grilla (las
     cards nuevas nacen con el botón apagado).
   - `pegarEn(card)` — **el filtrado de inactivos vive acá, no en la card**: el controller es quien
     conoce los catálogos vigentes (`datos.jabones()` / `datos.insumos()` de la última lectura), la
     card no. Arma una `ConfiguracionCopiada` depurada, junta los nombres omitidos y, si hay alguno,
     muestra `PEGADO_OMITIO_INACTIVOS`. **La comparación es por `getId()` / `id()`.**
   - `card.actualizarBtnAccion()` después de pegar.

5. **Tests**:
   - `LavarropasCardTest`: copiar y pegar en otra card deja los cuatro campos; el jabón pegado queda
     **`MANUAL`**; cambiar el tipo después de pegar **no lo pisa** (el test que fija la razón de ser
     del orden); pegar **no toca** los ítems de la tabla; pegar una config con `litrosJabon == null`
     deja el campo vacío.
   - Test de la depuración de inactivos — si queda como método estático o clase plana, mejor; si
     queda en el controller, probarla a través de un helper extraído. **La regla del repo es que la
     lógica de negocio no se queda adentro de Swing.**

### Verificación

```bash
mvn test -Dtest=LavarropasCardTest
mvn clean package && java -jar target/aptium.jar
```

Smoke manual:
1. Configurar la card #1 (Sucio, Skip a mano, 50 mL, Suavizante). "Pegar" está apagado en todas.
2. "Copiar" en #1 → "Pegar" se enciende en todas las cards libres.
3. "Pegar" en #5 → los cuatro campos aparecen; los **elementos cargados de #5 no cambian**.
4. En #5, cambiar el tipo a Limpio → **el jabón NO cambia** (quedó MANUAL).
5. Una card con ciclo activo **no muestra** ninguno de los dos botones.
6. Ajustes → dar de baja el Suavizante → volver a Ciclos → pegar de nuevo → se pega todo menos el
   Suavizante, **con el cartel** que lo nombra.
7. Agregar un lavarropas desde Ajustes (la grilla se reconstruye) → el portapapeles **sigue
   cargado** y la card nueva puede pegar.

### Criterio de salida

- [ ] Los 7 puntos del smoke pasan, el 4 y el 7 en particular
- [ ] El pegado no toca los elementos cargados
- [ ] El filtrado de inactivos no vive dentro de una clase de Swing
- [ ] Commit: `feat: copiar y pegar la configuracion entre cards de lavarropas`

---

## Paso 9 — Revisión, cobertura, documentación y cierre

### Tareas

1. `/code-review high` sobre el diff completo del plan. Aplicar CRITICAL y HIGH; anotar el MEDIUM
   que se decida no tocar, con el motivo.
2. `mvn verify` + JaCoCo: `SelectorJabonAutomatico`, `ConstructorVistaCiclos`, los services nuevos y
   la depuración de inactivos del pegado, todos ≥ 80 %.
3. **`CLAUDE.md`** — este plan toca varias secciones:
   - **Lavadero**: los catálogos tienen baja lógica; la regla **CARGA filtra / HISTÓRICO no filtra**,
     escrita como regla y no como lista de archivos; y la asimetría `activo` vs
     `catalogo_descripciones.vigente`, **con el motivo**, para que nadie la "unifique".
   - **Lavadero — ciclo de vida** / tabla de Ciclos: la grilla se arma desde los lavarropas
     **activos** y se reconstruye reusando las cards que sobreviven; el jabón tiene origen
     AUTO/MANUAL y una elección a mano siempre gana; se puede copiar y pegar configuración.
   - **Concurrencia — bloqueo optimista**:
     - "Dónde hay guarda hoy" suma: ABM de lavarropas (alta, baja con `FOR UPDATE` sobre el ciclo
       activo, reactivación), bajas/reactivaciones de los cuatro catálogos, y el rechazo de
       lavarropas inactivo en `lanzarTanda`.
     - Las **tres guardas del lavadero que no son CAS** pasan a ser **cuatro**: sumar
       `SQL_LAVARROPAS_ACTIVO`.
     - Un párrafo nuevo sobre el **orden de bloqueo `lavarropas` → `ciclos_lavadero`**, compartido
       por `lanzarTanda` y `darDeBaja`, con el motivo: el `FOR UPDATE` sobre `ciclos_lavadero` es un
       gap lock y no ordena nada al tomarse, así que lo único que serializa las dos operaciones es
       el bloqueo exclusivo sobre `lavarropas`. Invertirlo da un deadlock que H2 no delata.
     - La nota sobre `ConflictoConcurrenciaException`: hoy enumera dos subtipos; ahora son **tres**
       (`SaldoConsumidoException`, `LavarropasOcupadoException`, `LavarropasDeBajaException`), y
       **la regla positiva sigue siendo la misma**: el staging se descarta sólo ante el primero.
   - **"Qué quedó afuera"**: los ABM de catálogo **dejan de estar afuera**. Lo que queda afuera
     ahora es la **edición** de descripciones y volúmenes de ortopedias
     (`CatalogoDAO.guardarDescripcion`, todavía sin llamador), y los ABM de instituciones y
     profesionales.
   - **Constantes**: `Constantes.Lavadero.CANTIDAD_LAVARROPAS` ya no existe; la cantidad sale de la
     base. `LAVARROPAS_POR_FILA` se queda (es layout).
   - **Tests**: actualizar el número.
4. **`plans/hallazgos-arquitectura-pendientes.md`**:
   - **#9c** ("Los ABM quedaron fuera") — marcarlo como **resuelto parcialmente**, con la fecha y la
     rama: los catálogos ahora tienen ruta de UI **y** guarda. Dejar anotado qué sigue afuera.
   - **#10** — `CatalogoDAO.guardarDescripcion` **sigue muerto** después de este plan; confirmar que
     la entrada sigue siendo exacta en vez de borrarla.
5. **Memoria** — `project-ajustes-lavadero-catalogos.md` (`type: project`) con lo que no se deduce
   del código: la regla CARGA/HISTÓRICO, el orden de bloqueo `lavarropas` → `ciclos_lavadero` y por
   qué (el `FOR UPDATE` sobre ciclos es un gap lock y no ordena nada al tomarse), que Ortopedias ya
   tenía `vigente` desde V16, y que "una elección a mano siempre pesa más que la automática" está
   escrita en positivo en `SelectorJabonAutomatico`. Enlazar
   `[[project-bloqueo-optimista]]`, `[[project-lavadero-ciclos]]`, `[[project-architecture]]` y
   `[[project-configuracion-ciclo-insumos]]`. Línea nueva en `MEMORY.md`.
6. Marcar este plan como **✅ CERRADO**, con los SHAs de cada paso.

### Criterio de salida

- [ ] `mvn verify` en verde, cobertura ≥ 80 % en las clases planas nuevas
- [ ] Las seis secciones de `CLAUDE.md` actualizadas
- [ ] `hallazgos-arquitectura-pendientes.md` refleja el estado nuevo de #9c y #10
- [ ] Memoria e índice actualizados
- [ ] Commit: `docs: ajustes de lavadero, catalogos y jabon automatico`

---

## Catálogo de anti-patrones para este plan

| Anti-patrón | Por qué está mal acá |
|---|---|
| Agregarle `activo` a `catalogo_descripciones` | Ya tiene `vigente` desde V16, respetada por Ingreso y por las diez rutas de Correcciones. Dos columnas = dos fuentes de verdad. |
| Renombrar `vigente` a `activo` "por consistencia" | Migración sobre una tabla histórica + diez consultas + el javadoc de V16, para cero cambio de comportamiento. La inconsistencia se documenta, no se arregla. |
| Filtrar por `activo` en un **join histórico** | Un elemento dado de baja desaparece de Ver Ciclos, del Historial, de Salidas o de Ver Equipos. **Una fila que no está no se parece a un error y nadie la reporta.** |
| Filtrar por `activo` en `buscarPorNombre` / `obtenerIdPorDescripcion` | Dejaría dar de alta un nombre que ya existe dado de baja → violación de `UNIQUE` con forma de error técnico. |
| Filtrar por `activo` en `SQL_DISPONIBLES` | Es ropa **ya clasificada** esperando un ciclo. Esconderla deja ropa física imposible de procesar. |
| Filtrar por `activo` en `obtenerVolumen`/`obtenerVolumenes` | Un material ya cargado con un código retirado sigue necesitando su volumen para armar el lote. |
| Invertir el orden de bloqueo en una de las dos operaciones | `lanzarTanda` y `darDeBaja` compiten por `ciclos_lavadero` y `lavarropas`. Órdenes distintos = deadlock cruzado. **H2 no lo delata**: no toma gap locks. |
| Suponer que el `FOR UPDATE` de `SQL_CICLO_ACTIVO_DE_LAVARROPAS` ordena la operación | Es un **gap lock**: no conflictúa al tomarse (dos gap locks son compatibles, lo dice su javadoc), conflictúa recién contra el `INSERT`. Lo que ordena `lanzarTanda` respecto de `darDeBaja` es el bloqueo exclusivo sobre `lavarropas`, y **por eso va primero**. Es el error que se comete leyendo el código sin leer el javadoc. |
| Tomar el `FOR UPDATE` de `lavarropas` después de una lectura no bloqueante | Bajo `REPEATABLE READ` la vista se fija en la primera lectura no bloqueante: la guarda leería un estado anterior a su propio bloqueo. |
| Confiar en la pantalla de Ajustes para impedir lanzar en un lavarropas de baja | El *staging* vive en la memoria de **cada cliente**. La única verificación que sirve es la de `lanzarTanda`, adentro de la transacción. |
| Reusar `LavarropasOcupadoException` para el lavarropas de baja | El cartel diría "otro operador lo ocupó", que es falso. Un cartel que miente entrena a apretar "Sí" sin leer. |
| Reusar `STAGING_DESCARTADO_POR_OCUPACION` para el descarte por baja | Lo mismo, en el aviso de la relectura. |
| Agregar `LavarropasDeBajaException` al descarte de staging | La regla es **positiva**: se descarta **sólo** ante `SaldoConsumidoException`. Acá la ropa sigue disponible; lo que se perdió es el destino. |
| Recrear todas las cards en `reconstruirGrilla` | Borra la configuración que el operador está tipeando en cards que no cambiaron. Reusar las que sobreviven. |
| Reconstruir la grilla **antes** de descartar el staging | La card desaparece primero y el staging queda huérfano: la ropa no vuelve a Disponibles. |
| Dibujar la grilla sólo con `activo = TRUE` | Un ciclo abierto en un lavarropas inactivo se queda **sin card, o sea sin botón Finalizar**: nunca cierra, el ingreso nunca llega a `LAVADO`, y la ropa desaparece de Disponibles *y* de Salidas. Es textualmente el escenario que `SQL_CICLO_ACTIVO_DE_LAVARROPAS` existe para impedir. La grilla es **activos ∪ los que tienen ciclo sin finalizar**. |
| Reconstruir la grilla sin volver a aplicar los catálogos sobre el mapa nuevo | Las cards nuevas nacen con los combos de jabón e insumos **vacíos** y no se pueden configurar hasta el próximo refresco. El bloque de `setJabones` está hoy al principio de `pintar`: hay que **moverlo** después del re-cableado, no dejarlo donde está. |
| Comparar `numeros` con `cards.keySet()` como listas | Si el mapa se repuebla desordenado, la comparación da `true` en cada `pintar` y la grilla se reconstruye en cada F5. Comparar como conjuntos. |
| Unificar `LoteDAO.obtenerOCrearCatalogoOtros` con `CatalogoOtrosDAO.obtenerOCrear` | Parecen el mismo método y no lo son: uno es **carga** (rechaza bajas), el otro es **histórico** (parte un material ya cargado para meterlo en un lote, y no puede rechazarlas). Unificarlos rompe el armado de lotes. |
| Hacer el pegado antes el jabón y después el tipo | El automático del tipo pisa el jabón pegado. **El orden es lo que hace correcta la operación.** |
| Comparar `JabonCatalogo` por referencia o por `equals` | No tiene `equals`, y dos lecturas del catálogo dan objetos distintos para el mismo jabón. **Siempre por `getId()`.** Lo mismo con `InsumoCatalogo`, por `id()`. |
| Olvidar el flag anti-callback al setear el combo desde el código | `setSelectedItem` dispara el mismo `ActionListener` que un click: el jabón automático se marcaría **MANUAL** solo, y el tipo dejaría de arrastrarlo. Es el bug más sutil del Paso 7. |
| Volver asíncrono un autocompletado, o agregar uno nuevo | Son una excepción **aceptada y acotada** en `CLAUDE.md` — cinco, no seis. Sin debounce dan resultados fuera de orden. |
| Validar el lavarropas contra un `SELECT` previo en el service | Ventana TOCTOU con forma de validación. La existencia y el estado los verifica la guarda transaccional. |
| Cablear `CatalogoDAO.guardarDescripcion` | Es el upsert que `hallazgos` #10 marca como el único que necesitaría guarda el día que deje de estar muerta. Este plan sólo prende y apaga `vigente`. |
| Engordar `AjustesController` con los cuatro catálogos | El alcance de un controller se ve en su firma. Un constructor de ocho services es el cajón que la regla existe para evitar. |
| Cargar los cinco catálogos al entrar a Ajustes | Casi siempre no se miran. Cada pestaña carga al seleccionarse. |
| Crear un grupo de refresco para Ajustes | Todo lo que cambia lo consumen pantallas que **leen al entrar**, y no se puede estar en dos pantallas a la vez. |
| Que `MigracionV26Test` toque `ConnectionPool.setDataSourceForTesting` | Pisa el `DataSource` global de `AbstractDAOTest` y el resto de la suite lee la base equivocada. |

---

## Plan de sesiones

Nueve pasos, **siete sesiones**. Cada sesión arranca en frío.

| Sesión | Pasos | Modelo | Effort | Fast mode | Por qué |
|---|---|---|---|---|---|
| 1 | Paso 1 | **Opus 5** | **alto** | ❌ no | Migración con un `DROP` y seeds resueltos por nombre. Irreversible. |
| 2 | Paso 2 | **Opus 5** | **alto** | ❌ no | El orden de bloqueo entre dos operaciones concurrentes, que **H2 no delata**. Es donde se compra o se pierde la corrección de todo el plan. |
| 3 | Paso 3 | **Opus 5** | **alto** | ❌ no | Grilla reconstruible + descarte de staging + el aviso que no puede mentir. Toca el invariante de refresco de Ciclos. |
| 4 | Pasos 4 + 5 | **Sonnet 5** | medio | ➖ opcional | Mecánico y repetitivo, pero con **una** decisión por consulta (CARGA vs HISTÓRICO) que el plan ya tabuló. Van juntos porque comparten `Constantes.Mensajes`. |
| 5 | **Paso 7** | **Opus 5** | **alto** | ❌ no | La regla AUTO/MANUAL y el flag anti-callback. El bug más sutil del plan vive acá. **Va antes que el Paso 6**: el 6 necesita `JabonPorTipoLavadoService` y el 7 no necesita nada de Ajustes. |
| 6 | **Paso 6** | **Sonnet 5** | medio | ✅ sí | Swing declarativo con `PanelGestionClientes` y `AjustesController.mutar` como moldes calcables. Con el 7 ya hecho, la pestaña de jabones se cablea entera. |
| 7 | Pasos 8 + 9 | **Sonnet 5** | medio | ➖ opcional | El copiar/pegar es corto y su única sutileza (el orden) ya está decidida; después el cierre, que abre con el `/code-review`. |

**Paralelizar (opcional):** las Sesiones 2-3 y la 4 no comparten archivos salvo
`Constantes.Mensajes`. Con `git worktree` se podrían correr a la vez, acordando de antemano quién
escribe qué constantes. Para una sola persona, secuencial sale mejor.

---

### Sesión 1 — Paso 1: la migración V25

**Opus 5 · effort alto · sin fast mode**

```
Ejecutá el Paso 1 de plans/ajustes-lavadero-catalogos.md (migración V25 + MigracionV25Test).

El plan plans/configuracion-ciclo-lavadero.md ya está cerrado: la V24 existe.

Antes de escribir nada leé del plan: "Decisiones de diseño tomadas por el plan" y el
"Catálogo de anti-patrones". Después leé V16__catalogo_ortopedias.sql.

Tres cosas de este paso:
1. catalogo_descripciones NO se toca: ya tiene `vigente` desde V16, y agregarle un `activo`
   paralelo daría dos fuentes de verdad;
2. los INSERT de jabon_por_tipo_lavado resuelven el jabón POR NOMBRE, y si no lo encuentran
   NO insertan nada y la migración NO falla — quedarse sin default es un estado legítimo.
   Hay un test que fija ese caso;
3. MigracionV25Test no puede llamar a ConnectionPool.setDataSourceForTesting.

Terminá con mvn test en verde y el commit del criterio de salida.
```

### Sesión 2 — Paso 2: ABM de lavarropas y la guarda del lanzamiento

**Opus 5 · effort alto · sin fast mode**

```
Ejecutá el Paso 2 de plans/ajustes-lavadero-catalogos.md (Lavarropas: modelo, DAO con ABM,
LavarropasDeBajaException, y la guarda que impide lanzar una tanda en un lavarropas de baja).

Leé antes del plan: "Contexto compartido", la sección "Concurrencia — el orden de bloqueo,
que no es opinable" del Paso 2, y el "Catálogo de anti-patrones". Después leé el javadoc de
CicloLavaderoDAO.SQL_CICLO_ACTIVO_DE_LAVARROPAS: es el más largo del repo por un motivo.

Lo que esta sesión tiene que dejar bien y nada más lo va a delatar:
1. lanzarTanda y darDeBaja bloquean en EL MISMO ORDEN: LAVARROPAS PRIMERO, ciclos_lavadero
   después. El FOR UPDATE de SQL_CICLO_ACTIVO_DE_LAVARROPAS es un GAP LOCK y no ordena nada
   al tomarse (su propio javadoc lo dice: dos gap locks son compatibles); lanzarTanda recién
   conflictúa sobre ciclos en su INSERT, al final. Poner el bloqueo de lavarropas después
   del de ciclos da un deadlock cruzado que H2 NO reproduce. Leé el bloque "Concurrencia —
   el orden de bloqueo" del Paso 2, que tiene el entrelazado paso por paso;
2. el chequeo real de "no lanzar en un lavarropas de baja" va DENTRO de la transacción de
   lanzarTanda, no en la pantalla de Ajustes: el staging vive en la memoria de cada cliente
   y la baja no se entera de lo que otra máquina tiene cargado;
3. LavarropasDeBajaException NO dispara el descarte del staging. Esa regla es positiva y la
   cumple sólo SaldoConsumidoException.

El alta duplicada tiene que distinguir "ya existe" de "ya existe pero está dado de baja",
con dos mensajes distintos y un test por cada uno.

Después de este paso PantallaCiclos y ConstructorVistaCiclos no compilan: es lo esperado.
Terminá con los tests de DAO y de ConcurrenciaOptimistaTest en verde, y el commit.
```

### Sesión 3 — Paso 3: la grilla dinámica

**Opus 5 · effort alto · sin fast mode**

```
Ejecutá el Paso 3 de plans/ajustes-lavadero-catalogos.md (PantallaCiclos.reconstruirGrilla,
ConstructorVistaCiclos con descarte por baja, CiclosController, borrado de LavarropasItem.
capacidadLitros y de LavarropasTableModel).

Leé antes del plan: "Contexto compartido" (reglas 10 y 11) y el "Catálogo de anti-patrones".
Los Pasos 1 y 2 ya están commiteados.

Tres cosas:
1. reconstruirGrilla REUSA las cards que sobreviven. Recrearlas todas borraría la config que
   el operador está tipeando en cards que no cambiaron — el mismo invariante que hace que
   recargar() no llame a resetConfiguracion();
2. el descarte del staging va ANTES de reconstruir la grilla. Al revés, la card desaparece
   primero y la ropa no vuelve a Disponibles;
3. el aviso del descarte por BAJA es un Mensajes.* propio. Reusar el de ocupación diría "otro
   operador lo ocupó", que es falso, y un cartel que miente desactiva también los verdaderos.
   Por eso VistaCiclos lleva DOS listas, no una.

No toques la regla "el staging se descarta sólo ante SaldoConsumidoException".
Cerrá con los 5 puntos del smoke manual (sin -Daptium.edt.strict=true) y el commit.
```

### Sesión 4 — Pasos 4 y 5: bajas lógicas de los cinco catálogos

**Sonnet 5 · effort medio**

```
Ejecutá los Pasos 4 y 5 de plans/ajustes-lavadero-catalogos.md (baja lógica de los catálogos
de Lavadero, y de Ortopedias y Otros).

Leé antes del plan: "Contexto compartido" y el "Catálogo de anti-patrones". Los Pasos 1 a 3
ya están commiteados.

La regla que gobierna los dos pasos: las consultas de CARGA (combos, listas, autocompletados
donde el operador ELIGE algo nuevo) filtran por activo; los joins HISTÓRICOS (Ver Ciclos,
Historial, Salidas, Lotes, Ver Equipos) NO filtran nunca. El Paso 4 trae la tabla consulta
por consulta para Lavadero; en el Paso 5 hay que producirla para CatalogoDAO clasificando
cada método por sus llamadores, y dejarla escrita.

Tres trampas concretas:
- buscarPorNombre y obtenerIdPorDescripcion NO filtran (si filtraran, un alta duplicada sobre
  un nombre dado de baja saldría como violación de UNIQUE);
- SQL_DISPONIBLES es HISTÓRICO, no de carga: es ropa ya clasificada esperando un ciclo;
- obtenerVolumen/obtenerVolumenes son HISTÓRICOS.

Ortopedias ya tiene `vigente` desde V16 y ya la respetan Ingreso y las diez rutas de
Correcciones: no agregues ninguna columna, sólo el ABM y la auditoría.

En Otros, obtenerOCrear conserva el INSERT IGNORE ANTES del SELECT: es un no-op si la fila
existe, así que una descripción dada de baja nunca se re-crea, y no hay ventana entre chequeo
y creación.

Correcciones tiene que seguir guardando un equipo que YA tenía un material dado de baja, si
no se lo toca. Es decisión del usuario y va con test.

Los dos pasos tocan Constantes.Mensajes: hacelos en la misma sesión para no cruzarte.
Un commit por paso.
```

### Sesión 5 — Paso 7: el jabón automático

> ⚠️ **El Paso 7 va antes que el Paso 6**, aunque tenga número mayor. La dependencia es
> unidireccional 6 → 7: el 6 necesita `JabonPorTipoLavadoService` para cablear los combos de "jabón
> por defecto", y el 7 no necesita nada de Ajustes (los defaults iniciales los sembró la V26).

**Opus 5 · effort alto · sin fast mode**

```
Ejecutá el Paso 7 de plans/ajustes-lavadero-catalogos.md (OrigenJabon, JabonPorTipoLavadoDAO
y su service, SelectorJabonAutomatico, y los cambios de LavarropasCard y CiclosController).

OJO: el Paso 7 va ANTES que el Paso 6, aunque tenga número mayor — mirá el grafo de
dependencias del plan. Los Pasos 1 a 5 ya están commiteados; el 6 todavía NO.

La regla del usuario, escrita en positivo: UNA ELECCIÓN A MANO SIEMPRE PESA MÁS QUE LA
AUTOMÁTICA. Toda la regla va en SelectorJabonAutomatico, clase plana SIN Swing, con la tabla
de casos del plan como test.

El bug más sutil del plan está acá: setSelectedItem dispara el MISMO ActionListener que un
click del operador. Sin un flag anti-callback (patrón silenciandoCallback de
PantallaHistorialLavadero), el jabón que puso el automático se marcaría MANUAL solo, y a
partir de ahí el tipo dejaría de arrastrarlo. Hay un test que lo fija.

setJabones cambia de comportamiento: conserva la selección si su getId() sigue en la lista
nueva. La comparación es POR ID — JabonCatalogo no tiene equals y dos lecturas dan objetos
distintos. Y hay que REESCRIBIR su javadoc (~280-284), que hoy dice que el jabón es "elección
explícita, sin default": con este paso deja de ser cierto.

Borrá jabonesCargados e insumosCargados: ahora los tres catálogos se releen en cada lectura,
que es lo que hace que un cambio en Ajustes se vea al volver a Ciclos. Son consultas chicas y
van SECUENCIALES, como el resto de leerDatos. Todo lo que se vuelca sobre las cards sigue
pasando por el aplicarCatalogos(datos) que creó el Paso 3, DESPUÉS del re-cableado de la
grilla: dejarlo antes hace que las cards nuevas nazcan con los combos vacíos, y con los
catálogos releídos siempre el síntoma se vuelve intermitente.

CiclosController gana un service en la firma, así que AppContext (los CINCO lugares: parámetro
del constructor largo, guarda de null, asignación, createDefault y getter) y UiCoordinator se
tocan en ESTE paso, no en el 6. Ningún test construye AppContext: el lugar que falte no lo
delata nada hasta que alguien abra Ciclos.

Cerrá con los 6 puntos del smoke manual y el commit.
```

### Sesión 6 — Paso 6: Ajustes en pestañas

**Sonnet 5 · effort medio · fast mode recomendado**

```
Ejecutá el Paso 6 de plans/ajustes-lavadero-catalogos.md (PantallaAjustes con JTabbedPane,
los paneles nuevos, los tres controllers nuevos, AppContext y UiCoordinator).

Leé antes del plan: "Contexto compartido" (regla 3) y el "Catálogo de anti-patrones". Los
Pasos 1 a 5 Y EL 7 ya están commiteados (el 7 va antes que el 6: mirá el grafo). Calcá
PanelGestionClientes (panel ABM) y AjustesController.mutar (escritura por TareaUI + recarga
+ onMutacion).

Cuatro cosas:
- un controller POR PESTAÑA, cada uno con sólo sus services en la firma. No engordes
  AjustesController: un constructor de ocho services es el cajón que la regla evita;
- la carga por pestaña va con addChangeListener sobre el JTabbedPane, NO con componentShown.
  Está decidido en el plan y no es para revisar en el smoke: una pestaña que carga de más (al
  entrar a Ajustes en vez de al seleccionarse) se ve EXACTAMENTE IGUAL que una que carga bien,
  así que el smoke no puede distinguirlas. El JTabbedPane anidado de Catálogos lleva el suyo:
  al entrar carga sólo la sub-pestaña seleccionada, no las tres;
- no crees ningún grupo de refresco: todo lo que cambia lo consumen pantallas que leen al
  entrar, y no se puede estar en Ajustes y en Ciclos a la vez;
- JabonPorTipoLavadoService YA EXISTE (Paso 7), así que la pestaña de jabones e insumos se
  cablea entera, defaults incluidos. No dejes nada deshabilitado.

En AppContext son cinco lugares por service y ningún test atrapa el que falte.

Cerrá con los 9 puntos del smoke manual (incluidos 6b y 6c) y el commit.
```

### Sesión 7 — Pasos 8 y 9: copiar/pegar y cierre

**Sonnet 5 · effort medio**

```
Ejecutá el Paso 8 de plans/ajustes-lavadero-catalogos.md (copiar y pegar la configuración
entre cards) y después cerralo con el Paso 9.

Los Pasos 1 a 7 ya están commiteados.

Del Paso 8, lo único que de verdad se puede romper:
- el pegado aplica el TIPO PRIMERO y el JABÓN DESPUÉS, con setJabonManual. Al revés, el jabón
  automático del tipo pisa el jabón pegado. Es el orden el que hace correcta la operación;
- el portapapeles vive en CiclosController (sólo EDT, valor inmutable) y por eso sobrevive a
  una reconstrucción de la grilla;
- los botones van DENTRO de panelConfig, que setModoActivo oculta: así "una card ocupada no se
  copia ni se pega" sale gratis, sin una condición;
- el filtrado de jabones/insumos dados de baja lo hace el CONTROLLER (que conoce los catálogos
  vigentes), no la card, y la comparación es por id. Se omite el ítem y se avisa.

Después:
1. /code-review high sobre el diff completo del plan;
2. mvn verify + JaCoCo;
3. actualizá CLAUDE.md (las SEIS secciones que lista el Paso 9),
   plans/hallazgos-arquitectura-pendientes.md (#9c y #10) y la memoria;
4. marcá el plan como CERRADO con los SHAs.

Un commit por paso.
```

---

## Protocolo de mutación del plan

- **Dividir un paso** → agregarlo como `Paso N.5` con su propio contexto y criterio de salida.
- **Saltear un paso** → dejar escrito *por qué* en "Mutaciones aplicadas" al final; no borrarlo.
- **Cambiar una decisión de la tabla de arriba** → tacharla (`~~...~~`) y escribir la nueva con
  fecha. Las decisiones tomadas con el usuario **no se cambian sin preguntarle**.

---

## Riesgos y preguntas abiertas

### Riesgos

| Riesgo | Mitigación |
|---|---|
| **El orden de bloqueo es la parte frágil y H2 no lo delata.** Un deadlock cruzado entre `lanzarTanda` y `darDeBaja` pasaría todos los tests y aparecería en producción como "error al lanzar". | Está escrito en los dos javadoc y en `CLAUDE.md`, y los `ConcurrenciaOptimistaTest` verifican **la guarda** (idéntica en H2 y MySQL), no el comportamiento del lock. Si se quiere más red, un smoke de dos instancias contra el MySQL real. |
| La grilla dinámica toca el flujo más delicado de la app (staging, DnD, fracciones de equipo) | Paso 3 con Opus y effort alto, smoke de 5 puntos con los casos de fracción repartida. |
| Dar de baja un elemento de catálogo que está en uso en una clasificación en curso | La FK es `RESTRICT` y **nada se borra**: la baja lógica no rompe ninguna referencia. La ropa clasificada sigue en `SQL_DISPONIBLES`, que es histórico. Verificado en el Paso 4. |
| `Constantes.Mensajes` lo tocan cuatro pasos | Sesiones 4 a 7 secuenciales; si se paraleliza, acordar de antemano quién escribe qué constantes. |
| Ajustes pasa a escribir en catálogos que antes nadie tocaba desde la UI | Todas las bajas/reactivaciones llevan CAS; las altas se apoyan en `UNIQUE`. Documentado en `CLAUDE.md` y en `hallazgos` #9c. |

### Preguntas abiertas — **para el usuario**

1. **¿Confirmás que la columna se llame `activo` en las tablas nuevas y siga llamándose `vigente`
   en `catalogo_descripciones`?** El plan decide **no unificar** (renombrar cuesta una migración
   sobre una tabla histórica, diez consultas y el javadoc de V16, para cero cambio de
   comportamiento) y documentar la asimetría. Si preferís la consistencia de nombre, es un paso más
   y hay que decidirlo **antes** del Paso 1.

2. **¿Quién puede entrar a Ajustes?** Hoy no hay usuarios ni permisos en la app, así que cualquier
   operador puede dar de baja un lavarropas o un catálogo entero. Con el ABM de clientes eso ya era
   así, pero este plan multiplica la superficie. Si hace falta algún tipo de traba (una contraseña
   en la pantalla, por ejemplo), es trabajo aparte y conviene saberlo ahora.
   *Supuesto del plan: no hace falta.*

3. **¿La pestaña de catálogos de Ortopedias tiene que dejar editar la descripción o el volumen?**
   El plan **sólo prende y apaga `vigente`**. Editar descripciones abriría
   `CatalogoDAO.guardarDescripcion`, que es un upsert hoy muerto y que
   `hallazgos-arquitectura-pendientes.md` #10 marca como el único que necesitaría guarda el día que
   se cablee. Si lo querés, es un paso adicional con su propia guarda.
   *Supuesto del plan: no.*

4. **Un lavarropas dado de baja y después reactivado, ¿tiene que aparecer marcado de alguna forma
   en los filtros de Ver Ciclos y del Historial?** Hoy esos filtros muestran todos los números
   históricos (y tienen que seguir haciéndolo). El plan **no agrega ninguna marca visual** de "este
   lavarropas ya no está".
   *Supuesto del plan: no hace falta.*

5. **Al dar de baja un lavarropas, ¿hace falta avisarle al operador que otros puestos pueden tener
   ropa cargada en él?** La baja no puede verlo (el staging es memoria de cada cliente) y esos
   puestos se enteran recién en su próxima relectura, con el cartel de
   `STAGING_DESCARTADO_POR_BAJA`. El plan **no** agrega una advertencia previa en Ajustes.
   *Supuesto del plan: alcanza con el aviso del otro lado.*
