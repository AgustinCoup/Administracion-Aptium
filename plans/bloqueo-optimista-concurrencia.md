# Plan — Bloqueo optimista para acceso concurrente

**Objetivo:** que dos operadores trabajando contra la misma base al mismo tiempo no puedan pisarse
en silencio. Cuando una escritura se apoya en datos que ya cambiaron, tiene que **fallar, avisar y
recargar** — nunca aplicarse sobre un estado distinto del que el operador vio.

**Rama:** `BloqueoOptimista`, creada desde `RetoquesFinalesL` (ver Paso 0)
**Modo:** directo — un commit por paso, sin PRs (`gh` no está instalado en esta máquina)
**Fecha de creación:** 2026-09-02
**Migración nueva:** V21 (la última aplicada es V20)

---

## Qué es el bloqueo optimista (y qué problema resuelve acá)

El problema se llama **lost update**. Dos operadores leen la misma fila, los dos deciden sobre lo
que leyeron, y el segundo `UPDATE` pisa al primero sin que nada falle: no hay excepción, no hay log,
la pantalla dice "guardado". Es el peor resultado posible — no rompe nada visible, sólo deja los
datos mal.

En esta app el hueco **no está adentro de la transacción**. Ahí ya hay `SELECT ... FOR UPDATE` y
relecturas (`MaterialDAO.aplicarMovimientos`, `LoteDAO.aplicarMovimientoLote`,
`SalidaLavaderoDAO.marcarListo`). El hueco está en el **tiempo de pensar del operador**:

```
Operador A          ── lee equipo 42 (material X en LAVADO) ────┐
                                                                │  (dos minutos tildando)
Operador B  ── lee equipo 42 ── avanza X a EMPAQUETADO ── commit┤
                                                                │
Operador A          ────────────────── confirma "X: LAVADO → EMPAQUETADO" ── commit ← se aplica igual
```

La transacción de A es impecable: abre, relee con `FOR UPDATE`, encuentra la fila, valida que la
cantidad alcanza, escribe. Lo que nunca chequea es que **la fila siga como cuando A la leyó**. El
resultado es un `material_movimientos` con `estado_origen = 'Lavado'` cuando en realidad salió de
`Empaquetado`, y un material que avanzó dos veces con un solo trabajo hecho.

### Optimista vs. pesimista

| | Pesimista | Optimista |
|---|---|---|
| Cuándo bloquea | En la lectura, y no suelta hasta el commit | Nunca bloquea |
| Cómo detecta | No hace falta: nadie más pudo tocarla | Al escribir: el `WHERE` no matchea → 0 filas afectadas |
| Costo | Un operador leyendo deja a los otros esperando | Cero mientras no haya choque |
| Cuándo conviene | Choques frecuentes, transacciones cortas | Choques raros, "transacción" que dura lo que tarda una persona |

Acá el candidato es **optimista** por una razón concreta: mantener un lock desde que la pantalla
carga hasta que el operador aprieta Confirmar significa tener locks abiertos por minutos, tomados
desde el EDT, sobre un pool de conexiones chico. Es exactamente lo que no se puede hacer.

### Cómo se implementa

La escritura lleva en el `WHERE` una condición que **sólo es verdadera si nadie tocó la fila** desde
que se leyó, y después se mira `executeUpdate()`:

```java
// Guarda sobre el campo consumido (CAS): el estado que el operador vio viaja con el movimiento
UPDATE equipo_materiales SET estado = ? WHERE id = ? AND estado = ?
                                                      └── el estado que leyó la pantalla

if (ps.executeUpdate() == 0) throw new ConflictoConcurrenciaException(...);
```

```java
// Columna version: cualquier cambio en el agregado invalida el token, mire el campo que mire
UPDATE equipos SET estado = ?, version = version + 1 WHERE id = ? AND version = ?
```

`0 filas afectadas` no significa "error de base": significa **"la realidad ya no es la que viste"**.
Se aborta la transacción entera, se recarga la pantalla y se le dice al operador qué pasó.

El repo ya hace esto a mano en tres lugares — `SalidaLavaderoDAO.SQL_ESTAMPAR_DESTINO`
(`AND destino IS NULL`), `SQL_BORRAR_SALIDA_SIN_DESTINO`, `LoteDAO.actualizarEstadoLoteAbierto`
(`AND fecha_fin IS NULL`) — y el javadoc de `BusinessException` ya describe el escenario textual:
*"típicamente porque ese estado cambió entre que la pantalla lo leyó y el usuario apretó el botón"*.
**Este plan no inventa un mecanismo: le pone nombre al que ya existe y lo aplica donde falta.**

---

## Decisiones tomadas con el usuario

| Tema | Decisión |
|---|---|
| Mecanismo | **Híbrido.** Guarda de fila (CAS sobre el campo consumido) como mecanismo primario; columna `version` sólo en los agregados que la UI edita desde un snapshot. |
| UX del conflicto | **Abortar + recargar + avisar.** Nada a medias, refresco de esa pantalla, y un diálogo que explica qué pasó y qué hacer. Sin reintento automático. |
| Alcance | **Los 4 flujos críticos:** Registrar Estado (ortopedias y otros), Lanzar Lote, Lanzar Tanda de lavadero, Salidas / derivación al CDE. ABMs de catálogo, clientes, instituciones, profesionales y ajustes quedan **fuera**. |
| Refresco entre clientes | **Al mostrar la pantalla** (`componentShown`), como ya hacen 10 de los 18 controllers. Sin polling ni hilos nuevos. |

### Decisiones de diseño tomadas por el plan (con su porqué)

| Tema | Decisión | Por qué |
|---|---|---|
| El mecanismo primario es la **guarda de campo**, no la columna `version` | En `equipo_materiales`, `equipo_otros_materiales`, `elementos_clasificacion_lavadero` y `salidas_lavadero` **no hay columna `version`** | Esas filas se **consumen por cantidad**: dos operadores sacando 3 y 4 unidades de una fila de 10 son las dos operaciones válidas y compatibles. Una `version` de fila las haría chocar a las dos por falso positivo, y el operador aprendería a ignorar el cartel. La guarda sobre el campo que se consume (`estado`, `destino`, saldo) detecta el choque **real** y deja pasar el concurrente legítimo. |
| La columna `version` se **agrega y se mantiene, pero NO se usa como guarda todavía** | El Paso 2 la crea y la incrementa en cada escritura; los Pasos 3-7 **no** la ponen en ningún `WHERE` | **Corrección posterior a la primera versión de este plan.** Guardar con la `version` del agregado produce el mismo falso positivo que motivó no ponerla en las tablas de detalle, un nivel más arriba: dos operadores avanzando **materiales distintos del mismo equipo** chocarían sin pisarse en nada. Y dentro de los 4 flujos en alcance no agrega detección real — la guarda de `estado` por material ya atrapa el conflicto genuino. Su valor está en **`Correcciones`** (edición de formulario completo, que reemplaza la fila entera desde un snapshot viejo), que está fuera de alcance. Se deja la columna **correcta y mantenida** para que ese día sea sólo agregar el `WHERE`, sin migración ni auditoría de rutas de nuevo. |
| La columna `version` va **sólo en `equipos` y `equipo_otros`** | No en `lotes`, ni en `ingresos_lavadero` | En esas dos hay una guarda natural que ya cubre el 100 % del caso y es más informativa que un número: `lotes.fecha_fin IS NULL` (ya implementada) y `ingresos_lavadero.estado` (la máquina de estados persistida). Agregarles `version` sería ceremonia sin cobertura extra. **`equipos`/`equipo_otros` son distintos**: son agregados cabecera + detalle, escritos desde cuatro pantallas, cuyo `estado` es **derivado** (se recalcula desde los materiales), así que el estado de la cabecera no sirve como guarda de nada. |
| La `version` se incrementa en **un solo lugar por agregado** | Dentro de `EquipoMaterialHelper.recalcularEstadoEquipo` y de su análogo de "otros" | Toda ruta que muta materiales de un equipo termina llamando a ese recálculo. Poniendo el `version = version + 1` ahí, la cobertura sale de un cambio de dos líneas en vez de veinte `UPDATE` desperdigados que alguien va a olvidar de tocar el día que agregue una ruta nueva. **Para ortopedias la premisa se verificó y se sostiene; para "otros" NO** — ver el hallazgo del Paso 2. |
| Para "otros" hay que **unificar el recalculador antes** de poner el bump | Extraer a `EquipoOtrosMaterialHelper.recalcularEstadoEquipo` y hacer que los dos DAOs deleguen | **Verificado, no asumido:** `recalcularEstadoEquipoOtros` está **duplicado** — `EquipoOtrosDAO:800` y `LoteDAO:848` tienen cada uno su propia copia del `CASE` de orden y su propio `UPDATE equipo_otros SET estado`. Ortopedias no tiene ese problema: `LoteDAO:552` delega en `EquipoMaterialHelper:38`, que es fuente única. Poner el bump en dos copias es garantizar que dentro de seis meses una lo tenga y la otra no, y un bump que a veces no ocurre es **peor que no tenerlo**: da falsos negativos silenciosos, que es justo lo que este plan viene a eliminar. |
| El conflicto es una **`BusinessException`**, no un tipo nuevo de la raíz | `ConflictoConcurrenciaException extends BusinessException` | Su javadoc ya describe este escenario palabra por palabra. Colgarlo de `ApplicationException` obligaría a tocar todos los `catch (BusinessException)` de los controllers para que el conflicto no se cuele como error técnico. Como subclase, el ruteo existente ya funciona y quien quiera distinguirlo lo hace con un `instanceof`. |
| En Registrar Estado el conflicto es **por equipo**, no por confirmación | Un conflicto en el equipo 42 no cancela los cambios del 43 | `AplicadorMovimientosPendientes` ya está construido así a propósito (una transacción por equipo, no corta ante el primer fallo, acumula `idsConError`) porque los equipos anteriores **ya commitearon**: cortar dejaría al operador sin saber cuáles pasaron. El plan no cambia esa semántica, sólo separa "falló" de "chocó" en el mensaje. |
| En Lanzar Lote y Lanzar Tanda el conflicto es **todo o nada** | Un solo material en conflicto aborta el lote/la tanda entera | Las dos son una transacción única por diseño, y por un motivo ya documentado: `CicloLavaderoDAO.lanzarTanda` explica que una tanda a medias deja una instancia con menos fracciones que su `total_partes`, y ese equipo **desaparece de Disponibles y de Salidas a la vez**. Un lote a medias tiene el problema espejo: materiales en `ESTERILIZANDO` apuntando a un lote cuya capacidad se calculó con otra composición. |
| Se agrega la guarda de **Clasificación** aunque no esté en los 4 críticos | `ClasificacionLavaderoDAO.guardar` pasa a exigir `estado = 'PENDIENTE'` | Hoy no tiene ninguna guarda: dos operadores clasificando el mismo ingreso insertan **las dos clasificaciones**, y el ingreso queda con el doble de ropa de la que entró. Es la línea de entrada de Lanzar Tanda (que sí está en alcance), el arreglo son dos líneas, y dejarlo afuera haría que el paso siguiente proteja un saldo ya corrupto. Va con su justificación en el commit. |
| **No** se toca `Correcciones` en este plan | `MaterialDAO.actualizarCantidad` / `actualizarCodigoCatalogo` siguen siendo escrituras ciegas | Está fuera del alcance acordado. Pero es el consumidor natural de la `version` del Paso 2, así que la infraestructura queda lista y el hueco queda **anotado** en `plans/hallazgos-arquitectura-pendientes.md` en vez de olvidado. |

### Lo que este plan NO resuelve (dicho explícitamente)

1. **Dos operadores que hacen el mismo trabajo físico.** Si A y B lavan la misma bolsa y los dos la
   registran, el bloqueo optimista detecta el segundo registro y lo rechaza — que es lo correcto —
   pero no hay nada que evite el trabajo duplicado antes de que pase.
2. **`obtenerSiguienteSecuencia` de lotes** (`SELECT MAX(secuencia) + 1`). Dos lotes lanzados en el
   mismo segundo pueden calcular la misma secuencia; lo que los salva hoy es el
   `UNIQUE (id_negocio)` de V1, que los hace fallar con un error técnico feo en vez de un mensaje
   claro. **Es un problema real de concurrencia pero no es un lost update**, es asignación de
   identidad, y se arregla con otra técnica (reintento sobre la violación de UNIQUE, o secuencia en
   tabla). Se anota en `hallazgos-arquitectura-pendientes.md`; no se resuelve acá.
3. **El nivel de aislamiento de la base.** Todo el plan asume el `REPEATABLE READ` por defecto de
   MySQL y no lo cambia. Las guardas funcionan igual bajo `READ COMMITTED`.

---

## Contexto compartido (leer una vez por sesión)

App de escritorio **Swing, Java 17, Maven**, sin framework de DI. Capas por feature:
`model → dao → service → view/controller`. Todo se cablea a mano en `AppContext` y `UiCoordinator`.
Base **MySQL/MariaDB** con **HikariCP** (`ConnectionPool`) y migraciones **Flyway** (V1→V20).
Tests: **JUnit 5 + Mockito + H2 en memoria modo MySQL** (`AbstractDAOTest`), ~970 tests en 94 clases.

### Reglas duras del repo que este plan debe respetar

1. **Ningún acceso a BD en el EDT.** `TareaUI` (`ui/common/`) es el único mecanismo de trabajo en
   fondo: `.leer` hace el I/O, `.pintar` vuelve al EDT, `.siFalla` maneja el error. `EdtGuard`
   delata al que lo viole.
2. **El estado mutable de un controller se lee y escribe sólo en el EDT.**
3. **Un controller declara en su constructor los services que usa.** No hay fachada.
4. **Los services no tienen JDBC.** Validan y delegan.
5. **Lógica de negocio embebida en Swing → clase plana sin Swing, testeada en aislamiento.**
6. **Una migración ya escrita no se toca.** Si hace falta otro cambio, migración nueva.
7. **Transacciones:** `try (TransactionalConnection tx = ...)`, `tx.commit()` explícito; el `close()`
   hace rollback si no se commiteó.
8. **Nada del lavadero se borra nunca** (salvo `salidas_lavadero` sin destino, que es un undo).

### Cómo se corren los smokes de concurrencia de este plan

Los smokes manuales necesitan **dos clientes escribiendo contra la misma base**, y en este entorno
eso es **dos máquinas distintas apuntando a la misma BD de desarrollo**, no dos procesos locales.
Se apuntan con las variables de entorno `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASS`,
que tienen precedencia sobre `config.properties`.

Consecuencias prácticas para todos los pasos:

- El smoke **no es reproducible en un `mvn test`**: verifica el diálogo y el refresco, no la guarda.
  La guarda se verifica en los tests de dos conexiones sobre H2, que sí corren solos.
- Coordinar los dos lados lleva tiempo, así que **cada paso deja su smoke para el final**, después
  de que sus tests estén en verde. Un smoke que falla por un bug que un test unitario habría
  atrapado es media hora tirada en dos máquinas.
- Si en el momento de ejecutar un paso no hay segunda máquina disponible, **el paso igual se cierra
  con sus tests**, y el smoke queda anotado como pendiente en el commit. Lo que **no** se puede
  cerrar sin smoke de dos clientes es el Paso 9.

### Compatibilidad H2 de la migración (crítico para el Paso 2)

`AbstractDAOTest` corre las migraciones sobre H2 en modo MySQL, con una instancia **compartida entre
clases de test**. La V21 tiene que ser SQL que H2 acepte:

- `ALTER TABLE t ADD COLUMN version INT NOT NULL DEFAULT 0;` → **OK en los dos motores**.
- **Sin `AFTER columna`** (MySQL-only). Precedente: V20 hace `ADD COLUMN` pelado a propósito.
- **Sin `CHECK`** — no hay uno solo en las 20 migraciones; el patrón del repo es validar en código.
- Una sentencia por línea, terminada en `;`.

### Mapa de los huecos concretos (verificado en el código, 2026-09-02)

| # | Dónde | Qué pasa hoy | Paso |
|---|---|---|---|
| 1 | `MaterialDAO.aplicarMovimientos:105-131` | Relee `FOR UPDATE` y valida la cantidad, **pero no el estado**. Un material ya avanzado por otro operador avanza de nuevo, y el `material_movimientos` queda con un `estado_origen` falso. | 3 |
| 2 | `EquipoOtrosDAO:392` | Mismo hueco en el camino de "otros". | 4 |
| 3 | `LoteDAO.aplicarMovimientoLote:585-593` | Trae `estado` en el `SELECT ... FOR UPDATE` y **lo usa sólo para el registro de movimiento**, nunca para decidir. Un material ya lanzado en otro lote (estado `ESTERILIZANDO`, `lote_id` poblado) se vuelve a mover al lote nuevo. | 5 |
| 4 | `ClasificacionLavaderoDAO.guardar:31-35` | `UPDATE ingresos_lavadero SET estado='CLASIFICADO' WHERE id = ?`, sin guarda y sin mirar filas afectadas. Dos clasificaciones del mismo ingreso se insertan las dos. | 6 |
| 5 | `CicloLavaderoDAO.lanzarTanda:199-212` | El saldo disponible se calcula **en la pantalla** (`SQL_DISPONIBLES`) y no se vuelve a mirar adentro de la transacción. Dos tandas lanzadas desde dos snapshots sobregiran la línea de clasificación — lo mismo que `detectarLineasSobregiradas()` sale a buscar después. | 6 |
| 6 | `SalidaLavaderoDAO.marcarListo:376-408` | **Ya está bien**: relee el saldo adentro de la transacción. Lo que falta es que el fallo salga como conflicto y no como una `BusinessException` genérica. | 7 |
| 7 | `SalidaLavaderoDAO.SQL_FINALIZAR_INGRESO:230` | Sin guarda de estado. Es benigno (idempotente) pero rompe la regla uniforme. | 7 |
| 8 | ~~`ClasificacionController`, `SalidasLavaderoController` sin refresco al entrar~~ | **Descartado tras verificar.** Las dos **sí** releen al entrar, por otro mecanismo: el `ActionListener` del botón de menú (`UiCoordinator:198-201` → `cargarIngresosSinClasificar()`; `UiCoordinator:227-230` → `cargarDatos()`). El repo tiene **dos convenciones de refresco al entrar** conviviendo, y las pantallas de Lavadero usan la segunda. | 8 (reducido) |

---

## Grafo de dependencias

```
Paso 0 (rama)
   └─► Paso 1 (infraestructura de conflicto)
          └─► Paso 2 (V21 + version en los dos agregados)
                 ├─► Paso 2b (guard de versiones mixtas del JAR) ── paralelo a 3-7
                 ├─► Paso 3 (Registrar Estado — ortopedias)   ┐
                 ├─► Paso 4 (Registrar Estado — otros)        ├── paralelizables entre sí
                 ├─► Paso 5 (Lanzar Lote)                     │   (no comparten archivos)
                 ├─► Paso 6 (Clasificación + Lanzar Tanda)    │
                 └─► Paso 7 (Salidas + derivación al CDE)     ┘
                            └─► Paso 8 (auditoría de refresco) ─► Paso 9 (tests + docs)
```

**Serial obligatorio:** 0 → 1 → 2, y todo → 8 → 9.

**Paralelo real: 2b, 3, 4, 5, 6 y 7 no comparten un solo archivo entre sí** (verificado archivo por
archivo contra las tablas "Archivos" de cada paso).

**Condición de despliegue:** el Paso 2b tiene que estar cerrado **antes de que la V21 llegue a
producción**, sin importar en qué orden se hagan los demás.

> **El Paso 8 NO es paralelizable, aunque la primera versión de este plan decía que sí.** Audita
> `ClasificacionController` y `SalidasLavaderoController`, que son **los mismos dos archivos** que
> tocan los Pasos 6 y 7. Además su auditoría sólo tiene sentido después de que esos pasos
> definieran cómo se maneja el conflicto en esas pantallas. Va **después**, no en otra ventana.

### Invariantes verificados después de **cada** paso

```bash
mvn clean package      # compila y arma el fat JAR
mvn test               # los ~970 tests siguen en verde
```

- Ningún `catch` nuevo se traga un conflicto: `ConflictoConcurrenciaException` siempre llega al
  usuario o a un `log.warn` explícito.
- Ningún acceso a BD nuevo en el EDT (`EdtGuard` en el log del smoke manual).
- Ninguna migración anterior a V21 modificada: `git diff --stat src/main/resources/db/migration/`
  no debe listar nada más que `V21__*.sql`.

---

## Paso 0 — Crear la rama

**Contexto:** el árbol de trabajo está limpio y la rama actual es `RetoquesFinalesL`, que tiene el
Historial de Lavadero recién cerrado (`40c8021`). Se ramifica desde ahí y no desde `main` porque
este plan toca `SalidaLavaderoDAO`, `CicloLavaderoDAO` y los controllers de lavadero tal como
quedaron después de ese trabajo.

**Tareas:**
1. `git status --short` → tiene que salir vacío. Si no, parar y preguntar antes de seguir.
2. `git checkout -b BloqueoOptimista`
3. Commitear **este archivo** de plan como primer commit de la rama:
   `docs: plan de bloqueo optimista para acceso concurrente`

**Verificación:** `git branch --show-current` → `BloqueoOptimista`; `git log --oneline -1` muestra el
commit del plan.

**Rollback:** `git checkout RetoquesFinalesL && git branch -D BloqueoOptimista`.

---

## Paso 1 — Infraestructura de conflicto

**Contexto para arrancar en frío:** no hay ninguna clase que represente "otro usuario tocó esto".
Los tres lugares que ya detectan la condición
(`SalidaLavaderoDAO.marcarListo`, `volverALavado`, `asignarDestino`) lanzan `BusinessException` con
un texto armado a mano. Este paso les da un tipo y un mensaje común, **sin cambiar el comportamiento
de nada**: es un paso de andamio, y su valor es que los cinco pasos siguientes no cada uno invente
lo suyo.

**Archivos:**
- **Nuevo** `common/exception/ConflictoConcurrenciaException.java`
- **Nuevo** `common/dao/ControlConcurrencia.java`
- `common/constants/Constantes.java` (agregar mensajes)
- **Nuevo** `src/test/java/com/example/common/dao/ControlConcurrenciaTest.java`

**Tareas:**
1. `ConflictoConcurrenciaException extends BusinessException`, con javadoc que explique la
   diferencia con su padre: la `BusinessException` dice *"esto no se puede hacer"*; el conflicto
   dice *"esto se podía hacer cuando lo leíste y ya no"*. Dos constructores, igual que el padre.
2. `ControlConcurrencia`, clase final con constructor privado y sólo estáticos:
   - `exigirFilaAfectada(int filasAfectadas, String mensajeUsuario)` → si es 0, lanza
     `ConflictoConcurrenciaException`; si es > 1, `log.warn` (una guarda por PK jamás debería tocar
     más de una fila: si toca más, la condición está mal escrita y hay que enterarse) y sigue.
   - `exigirFilasAfectadas(int esperadas, int reales, String mensajeUsuario)` para los batches.
3. Mensajes en `Constantes.Mensajes`, redactados para el operador, **diciendo qué hacer**:
   - `CONFLICTO_GENERICO`: *"Otro usuario modificó estos datos mientras trabajabas. La pantalla se
     actualizó: revisá y volvé a confirmar."*
   - `CONFLICTO_MATERIAL`, `CONFLICTO_LOTE`, `CONFLICTO_TANDA`, `CONFLICTO_SALIDA` — variantes que
     nombran qué cambió.
4. Tests de `ControlConcurrencia`: 0 filas lanza y el mensaje llega intacto; 1 fila no lanza;
   N filas no lanza; la excepción **es** una `BusinessException` (`assertThat(...).isInstanceOf(...)`).

**Verificación:** `mvn test -Dtest=ControlConcurrenciaTest` y `mvn clean package`.
**Exit criteria:** compila, tests nuevos en verde, **`git diff` no toca ningún DAO** — este paso no
cambia una sola escritura.
**Commit:** `feat: tipo y helper de conflicto de concurrencia`
**Rollback:** `git revert` del commit; nadie depende todavía de estas clases.

---

## Paso 2 — V21: columna `version` en los dos agregados de equipo

**Contexto para arrancar en frío:** `equipos` y `equipo_otros` son agregados cabecera + detalle. Su
columna `estado` es **derivada** — se recalcula a partir del estado de los materiales. Por eso el
estado de la cabecera no sirve de guarda: puede quedar igual aunque los materiales de abajo hayan
cambiado enteros. La `version` es el token que sí lo detecta.

> **Hallazgo verificado al escribir el plan (2026-09-02) — leer antes de tocar nada.**
> El recálculo **no** está unificado en los dos lados:
>
> | Agregado | Estado |
> |---|---|
> | `equipos` (ortopedias) | ✅ **Fuente única.** `EquipoMaterialHelper.recalcularEstadoEquipo:38` es `static` y compartido; `LoteDAO:552` es un one-liner que delega en él (`LoteDAO.java:553`). |
> | `equipo_otros` | ❌ **Duplicado.** `EquipoOtrosDAO:800` y `LoteDAO:848` son dos implementaciones separadas, cada una con su propia copia del `CASE ... orden_minimo` y su propio `UPDATE equipo_otros SET estado = ? WHERE id = ?`. |
>
> **Trampa de nombres — leer antes de hacer `grep`.** Los dos métodos **no se llaman igual**:
> `EquipoOtrosDAO:800` es `private void recalcularEstadoEquipo` (sin sufijo, colisiona de nombre con
> el de ortopedias) y `LoteDAO:848` es `private void recalcularEstadoEquipoOtros`. Buscar
> `recalcularEstadoEquipoOtros` devuelve **sólo la copia de `LoteDAO`** y da la impresión de que ese
> es el punto central. No lo es: `LoteDAO.procesarEquiposOtrosAfectados:560-566` llama a su propia
> copia y **nunca entra a `EquipoOtrosDAO`**.
>
> Por eso la tarea 2 de este paso **empieza extrayendo el recalculador de "otros"** a
> `EquipoOtrosMaterialHelper` (la clase ya existe y ya aloja `unificarMaterialesDuplicados`, que
> tiene exactamente esta forma: `EquipoOtrosDAO:838` delega en ella). Es refactor puro, sin cambio
> de comportamiento, y es **condición para que el bump sea confiable**.

Este paso **agrega y mantiene** la columna, pero todavía **no la usa como guarda de nada**. Separar
las dos cosas es a propósito: si el bump quedara mal, se ve acá con los tests existentes y no
mezclado con el cambio de comportamiento del Paso 3.

**Archivos:**
- **Nuevo** `src/main/resources/db/migration/V21__version_optimistic_locking.sql`
- `features/equipos/ortopedias/dao/EquipoMaterialHelper.java`
- `features/equipos/otros/dao/EquipoOtrosMaterialHelper.java` (recibe el recalculador extraído)
- `features/equipos/otros/dao/EquipoOtrosDAO.java`, `features/lotes/dao/LoteDAO.java` (pasan a delegar)
- `features/equipos/ortopedias/model/Equipo.java`, `features/equipos/otros/model/EquipoOtros.java`
- `features/equipos/ortopedias/dao/EquipoDAO.java` (mapeo de lectura)

**Tareas:**
1. **Migración V21**, con comentario de cabecera que explique el porqué (estilo V19/V20):
   ```sql
   ALTER TABLE equipos ADD COLUMN version INT NOT NULL DEFAULT 0;
   ALTER TABLE equipo_otros ADD COLUMN version INT NOT NULL DEFAULT 0;
   ```
   Sin `AFTER`, sin `CHECK`. Las filas existentes arrancan en 0, que es correcto: nadie tiene un
   snapshot viejo de una app que todavía no leía la columna.
2. **Unificar primero el recalculador de "otros"** (ver el hallazgo de arriba). Mover el cuerpo de
   `EquipoOtrosDAO.recalcularEstadoEquipo:800` a
   `EquipoOtrosMaterialHelper.recalcularEstadoEquipo(Connection, int)` estático, y hacer que
   `EquipoOtrosDAO:800` y `LoteDAO:848` deleguen. **Commit aparte del resto del paso**
   (`refactor: unificar el recálculo de estado de equipo_otros`): es refactor sin cambio de
   comportamiento y tiene que poder revertirse solo. Las dos copias tienen diferencias menores —
   `LoteDAO:864` chequea `rs.getObject("orden_minimo") != null` y `EquipoOtrosDAO:816` no. **Quedarse
   con la versión defensiva** (la de `LoteDAO`) y dejar el porqué en el javadoc; unificar hacia la
   más laxa sería introducir un `getInt` sobre `NULL` en un camino que hoy no lo tiene.
3. **Bump en un solo lugar por agregado.** En `EquipoMaterialHelper.recalcularEstadoEquipo`, el
   `UPDATE equipos SET estado = ? WHERE id = ?` pasa a
   `UPDATE equipos SET estado = ?, version = version + 1 WHERE id = ?`. Ídem en el helper de "otros"
   recién unificado.
4. **Terminar de verificar la premisa.** Con `grep -n "UPDATE equipo" -r src/main/java`, confirmar
   que toda ruta que escribe sobre `equipos` / `equipo_materiales` / `equipo_otros` /
   `equipo_otros_materiales` termina llamando al recálculo. Dos ya se sabe que hay que mirar de
   cerca:
   - `LoteDAO.acumularVolumenEquipoOtros:923` (`UPDATE equipo_otros SET volumen_equipo = ...`)
     escribe fuera del recálculo. Verificar si el `procesarEquiposOtrosAfectados` de la misma
     transacción corre **después**; si sí, el bump ya lo cubre y alcanza con documentarlo.
   - `EquipoOtrosDAO:162` (`UPDATE equipo_otros SET remito_id = ...`) y `:628`
     (`SET remito_cantidad = ...`).

   Para cada ruta que no bumpee: o se la hace llamar al recálculo, o se documenta en el javadoc del
   helper por qué esa columna no invalida un snapshot. **No dejarlo implícito** — un bump con
   agujeros da falsos negativos, que es exactamente lo que este plan viene a eliminar.
5. `version` como campo `final int` en `Equipo` y `EquipoOtros`, leído en los mapeos de los DAOs.
   Getter, sin setter (los modelos del repo son inmutables).
6. Tests: los de `EquipoMaterialHelper`, `MaterialDAO`, `LoteDAO` y `EquipoOtrosDAO` que ya existen
   siguen en verde (la V21 corre sola sobre H2), **más** un test nuevo por agregado que verifica que
   dos escrituras seguidas sobre el mismo equipo dejan `version = 2`, **y uno que verifica que la
   escritura por el camino de `LoteDAO` bumpea igual que la del camino de `EquipoOtrosDAO`** — que es
   la regresión concreta que la duplicación habría dejado pasar.

**Verificación:**
```bash
mvn test -Dtest=EquipoMaterialHelperTest+MaterialDAOTest+LoteDAOTest+EquipoOtrosDAOTest
mvn clean package
```
Y un arranque real contra la base de desarrollo para confirmar que Flyway aplica la V21 sin ruido.

**Exit criteria:** el recálculo de "otros" tiene una sola implementación; la V21 aplica en MySQL y en
H2; toda escritura sobre un equipo incrementa su `version` **por cualquiera de los dos caminos**;
ninguna guarda usa todavía la columna; suite completa en verde.
**Commits:** `refactor: unificar el recálculo de estado de equipo_otros` +
`feat: columna version en equipos y equipo_otros (V21)`
**Rollback:** una migración ya escrita **no se toca ni se borra** (regla del repo). Si la V21 tiene
que revertirse después de haberse aplicado en algún lado, se escribe una V22 que haga el `DROP
COLUMN`. Antes de aplicarla en ningún lado, `git revert` alcanza.

---

## Paso 2b — Cortar la ventana de versiones mixtas del JAR

**Contexto para arrancar en frío:** este paso existe por una condición del despliegue, no por el
código: **cada máquina se actualiza cuando quiere.** La app se autoactualiza contra GitHub Releases
(`features/actualizaciones/`), así que después de que la V21 corra en la base puede haber clientes
con el JAR viejo operando durante días.

**Un cliente viejo escribe sin guardas y sin incrementar `version`.** Puede pisar en silencio a uno
actualizado — exactamente el bug que este plan viene a cerrar, reintroducido por el despliegue. Y no
hay protección gratis: **Flyway 8.5.13** (`pom.xml:112`) tiene `ignoreFutureMigrations = true` por
defecto, así que `DatabaseInitializer.inicializar()` ve la V21 aplicada que no conoce, **no la
considera un error**, y la app arranca y opera normalmente.

**Archivos:** `infrastructure/db/DatabaseInitializer.java`, el arranque (`App` / `AppController`),
`common/constants/Constantes.java`, tests.

**Tareas:**
1. Después del `migrate()`, comparar la última versión aplicada en `flyway_schema_history` contra la
   última que **este JAR trae** en `classpath:db/migration`. Flyway lo da hecho:
   `flyway.info().current()` vs `flyway.info().all()` resueltas localmente. Si la base está
   **adelante**, abortar el arranque.
2. El diálogo tiene que decir **qué hacer**, no qué falló: *"Esta versión de la aplicación es más
   vieja que la base de datos. Actualizá desde Ajustes → Buscar actualizaciones antes de seguir
   trabajando."* Mismo patrón que los errores de arranque que ya terminan la app.
3. **Verificar antes de codear que este chequeo no rompe `outOfOrder(true)`**
   (`DatabaseInitializer:21`), que existe porque las ramas `Lavadero` y `RefactorVolúmenes` se
   pisaron los números. Una migración atrasada que se aplica después **no** es una base adelantada:
   el chequeo compara **máximos**, y tiene que seguir dejando pasar ese caso. Es la trampa concreta
   de este paso.
4. Test: `flyway_schema_history` con una V99 sintética y las migraciones locales hasta V21 → el
   arranque aborta; con V21 en las dos puntas → arranca. `AbstractDAOTest` ya sabe insertar filas
   sintéticas en esa tabla (`:60-65`), copiar ese patrón.

**Verificación:** `mvn test` + prueba manual: aplicar la V21 con el JAR nuevo, después arrancar el
JAR anterior contra la misma base y confirmar que se niega con el mensaje correcto.
**Exit criteria:** un JAR anterior a la V21 no puede escribir en una base ya migrada.
**Commit:** `feat: la app se niega a arrancar contra una base más nueva que el JAR`
**Rollback:** `git revert`; es aditivo y no lo consume ningún otro paso.

> **Este paso es condición de despliegue, no de desarrollo.** Puede hacerse en cualquier momento
> después del Paso 2, pero **tiene que estar cerrado antes de que la V21 llegue a producción.**

---

## Paso 3 — Registrar Estado (ortopedias)

**Contexto para arrancar en frío:** `RegistrarEstadoController` (`equipos/common/controller/`)
mantiene un buffer en memoria — `cambiosPendientes: Map<EquipoKey, List<MovimientoMaterial>>` — que
el operador va llenando y recién manda al confirmar (`confirmarCambios:263`, vía
`AplicadorMovimientosPendientes.aplicarTodos`, una transacción por equipo). `MovimientoMaterial`
(`equipos/ortopedias/model/`) hoy lleva `materialId`, `cantidad` y `estadoDestino` — **no lleva de
dónde venía**, así que el DAO no tiene con qué comparar aunque quiera.

`MaterialDAO.aplicarMovimientos:105` ya relee la fila con `FOR UPDATE` y ya **trae** `estadoActual`
del `ResultSet`: sólo lo usa para escribir `material_movimientos.estado_origen`. El arreglo es hacer
que ese valor también decida.

**Archivos:**
- `features/equipos/ortopedias/model/MovimientoMaterial.java`
- `features/equipos/ortopedias/dao/MaterialDAO.java`
- `features/equipos/common/controller/RegistrarEstadoController.java`
- `features/equipos/common/controller/helpers/AplicadorMovimientosPendientes.java`
- Tests de los cuatro.

**Tareas:**
1. `MovimientoMaterial` gana `estadoOrigenEsperado` (`EstadoEquipo`) — el estado que la pantalla
   mostraba cuando el operador tildó. Constructor nuevo con el campo; **el viejo se elimina**, no se
   deja como sobrecarga: un movimiento sin estado esperado es exactamente el bug que este paso
   cierra, y dejar el constructor viejo es dejar la puerta para reintroducirlo. El compilador marca
   los llamadores.
2. En `aplicarMovimientos`, después de leer `estadoActual` y antes de escribir nada:
   ```java
   if (!estadoActual.equalsIgnoreCase(movimiento.getEstadoOrigenEsperado().getNombre())) {
       throw new ConflictoConcurrenciaException(Constantes.Mensajes.CONFLICTO_MATERIAL);
   }
   ```
   Va **antes** de la validación de cantidad, no después: si el estado cambió, el saldo que el
   operador vio es de otra fila conceptual y el mensaje de "cantidad inválida" sería engañoso.
   El `throw` adentro del `try (TransactionalConnection)` dispara el rollback de ese equipo —
   ninguno de sus movimientos se aplica.
3. **La `version` del equipo NO se usa como guarda acá.** Es deliberado y está argumentado en la
   tabla de decisiones: guardar con ella haría chocar a dos operadores que avanzan materiales
   **distintos** del mismo equipo, que es un falso positivo — el mismo motivo por el que las tablas
   de detalle no la llevan. La protección de este flujo es la guarda de `estado` de la tarea 2, que
   es precisa. **No agregar `version` a `EquipoKey` ni a la firma de `aplicarMovimientos`:** ese
   parámetro no tendría ningún consumidor y sería código muerto desde el día uno.
4. `AplicadorMovimientosPendientes.Operacion` hoy devuelve `boolean` y la excepción rompería el loop
   entero. Extender `Resultado` para separar `idsConError` de `idsConConflicto`: el controller
   captura la `ConflictoConcurrenciaException` en el `Operacion` de cada equipo y sigue con el
   siguiente. **La semántica de "no corta ante el primer fallo" se preserva tal cual.**
5. `finalizarConfirmacion` arma el mensaje: los equipos con error técnico y los que chocaron se
   nombran por separado (un choque no es un error, y el operador tiene que entender que su trabajo
   sobre ese equipo hay que rehacerlo con datos frescos). Después del mensaje, `solicitarRefresco`.
6. Tests: `AplicadorMovimientosPendientesTest` con un equipo que choca en el medio de tres (los
   otros dos se aplican, el conflicto se reporta aparte); `MaterialDAOTest` con **dos conexiones H2
   reales** — A lee, B avanza y commitea, A intenta y recibe `ConflictoConcurrenciaException`, y la
   base queda con el cambio de B intacto.

**Verificación:** `mvn test -Dtest=MaterialDAOTest+AplicadorMovimientosPendientesTest+RegistrarEstadoControllerTest`
+ `mvn clean package` + smoke de dos máquinas contra la misma BD: avanzar el mismo material desde las dos.
**Exit criteria:** el segundo operador ve el diálogo de conflicto, su cambio **no** se aplicó, la
pantalla quedó refrescada, y `material_movimientos` no tiene ningún `estado_origen` falso.
**Commit:** `feat: bloqueo optimista en Registrar Estado (ortopedias)`

---

## Paso 4 — Registrar Estado (otros)

**Contexto para arrancar en frío:** mismo flujo, otra rama del polimorfismo.
`RegistrarEstadoController` maneja los dos tipos a través de `EquipoRegistrableInterface`
(discrimina con `getTipo()`), y el camino de "otros" termina en `EquipoOtrosDAO:392`, con el mismo
`SELECT ... FOR UPDATE` que trae `estado` y no lo usa para decidir. Leer el Paso 3 completo antes de
tocar nada: **este paso es su espejo y tiene que quedar simétrico**, mismos nombres, mismo orden de
chequeos, mismo mensaje.

**Archivos:** `features/equipos/otros/dao/EquipoOtrosDAO.java`, `.../model/MaterialOtros.java` (si
lleva su propio tipo de movimiento), tests.

**Tareas:**
1. Guarda de `estadoOrigenEsperado` en el equivalente de `aplicarMovimientos`, idéntica al Paso 3.
2. **Sin guarda de `version`**, igual que en el Paso 3 y por el mismo motivo. La columna se mantiene
   incrementada (Paso 2) pero no entra en ningún `WHERE`.
3. Revisar los otros dos `UPDATE` de estado del archivo (`:326` sobre
   `equipo_otros_materiales`, `:337-344` sobre `equipo_otros`). El de `:337` **ya** tiene
   `AND estado = ?`: le falta mirar las filas afectadas en vez de descartarlas.
4. **Bypass conocido del bump de `version`** (detectado en la revisión del plan, no lo busques de
   nuevo): esa misma rama `:337-344` — el camino REMITO sin materiales reales de
   `entregarClienteCompleto` — escribe `equipo_otros.estado` **sin pasar por el recálculo**, así que
   con el Paso 2 tal cual **no incrementa `version`**. Un equipo entregado por ese camino queda con
   una `version` que miente. Hacerla bumpear explícitamente
   (`SET estado = ?, version = version + 1 WHERE id = ? AND estado = ?`) y dejar el comentario de por
   qué acá hay un bump a mano y no en el helper: **no hay materiales que recalcular**, así que el
   helper no aplica.
5. Tests de concurrencia con dos conexiones, espejo de los del Paso 3, **más uno que verifique que
   la entrega por el camino REMITO deja la `version` incrementada**.

**Verificación:** `mvn test -Dtest=EquipoOtrosDAOTest` + `mvn clean package` + smoke con un ingreso
de tipo REMITO y otro de tipo DETALLES.
**Exit criteria:** ortopedias y otros se comportan igual ante el mismo choque.
**Commit:** `feat: bloqueo optimista en Registrar Estado (otros)`

---

## Paso 5 — Lanzar Lote

**Contexto para arrancar en frío:** `LotesController` arma el lote en un **staging enteramente en
memoria** (`ReconciliadorPendientes` + `EstadoStaging`, `lotes/controller/helpers/`): mueve ítems
entre "disponibles" y "pendientes" con DnD, sin tocar la base hasta el `lanzarLote`. Ese staging
puede quedar viejo de dos maneras: alguien avanzó el material a otro estado, o alguien lo lanzó en
**otro lote**. En los dos casos `LoteDAO.aplicarMovimientoLote:585` lo encuentra igual (busca por
`id` + `equipo_id`) y lo mueve.

`lanzarLote` es **una sola transacción** para todos los movimientos, así que un `throw` adentro
revierte el lote entero — incluida la fila de `lotes` recién insertada. Eso es lo que se quiere.

**Archivos:**
- `features/lotes/model/LoteMovimiento.java`
- `features/lotes/dao/LoteDAO.java` (`aplicarMovimientoLote`, `aplicarMovimientoLoteOtros`)
- `features/lotes/controller/LotesController.java`
- Tests.

**Tareas:**
1. `LoteMovimiento` gana `estadoOrigenEsperado`. Los `MaterialLoteItem` del staging ya vienen de una
   lectura de disponibles: propagar el estado desde ahí. Si `MaterialLoteItem` no lo trae, agregarlo
   (es un value object plano de `lotes/view/helpers/`).
2. En `aplicarMovimientoLote`, después del `SELECT ... FOR UPDATE`:
   - `estadoActual` distinto del esperado → `ConflictoConcurrenciaException`.
   - **Además** exigir `lote_id IS NULL` en el `SELECT`, o traerlo y chequearlo. Un material con
     `lote_id` poblado ya está en otro lote y no puede entrar a este, aunque su estado engañe.
   - El mensaje nombra el material y el cliente: el operador tiene que poder encontrarlo en pantalla.
3. Ídem en `aplicarMovimientoLoteOtros` (`:766`).
4. `LotesController`: al capturar el conflicto, **descartar el staging entero** y recargar
   disponibles de la base. Ofrecer rearmar no sirve — la mitad de los ítems puede haber cambiado y
   el operador no tiene cómo saber cuáles.
5. Tests de dos conexiones: (a) el material se avanzó por otra vía; (b) el material se lanzó en otro
   lote primero. En los dos casos verificar que **no quedó una fila huérfana en `lotes`**.

**Verificación:** `mvn test -Dtest=LoteDAOTest+LotesControllerTest` + `mvn clean package` + smoke:
dos máquinas contra la misma BD, mismo material en el staging de las dos, lanzar en las dos.
**Exit criteria:** el segundo lote falla entero, `lotes` no tiene fila huérfana, `equipo_materiales`
no tiene ningún material apuntando a un lote inexistente.
**Commit:** `feat: bloqueo optimista al lanzar lotes`

---

## Paso 6 — Lavadero: clasificación y lanzar tanda

**Contexto para arrancar en frío:** dos huecos encadenados.

**(a) Clasificación.** `ClasificacionLavaderoDAO.guardar` inserta las líneas y marca el ingreso
`CLASIFICADO`, sin ninguna guarda y **sin mirar filas afectadas** (además de tragarse el `SQLException`
y devolver `false`). Dos operadores clasificando el mismo ingreso `PENDIENTE` insertan los dos juegos
de líneas: el ingreso queda con el doble de ropa de la que entró, y todo lo que viene después —
saldos, tandas, salidas — trabaja sobre ese número inflado.

**(b) Lanzar tanda.** `CicloLavaderoDAO.lanzarTanda` crea instancias y ciclos en una sola transacción,
pero el saldo disponible se calculó en la pantalla con `SQL_DISPONIBLES` y **nunca se relee adentro**.
Dos tandas lanzadas desde dos snapshots sobregiran la línea — que es exactamente la basura que
`detectarLineasSobregiradas()` sale a buscar a posteriori. Ese método es el detector; este paso es la
prevención, y por eso **se queda**: es la red para los datos que ya existen.

Ojo con la **invariante de fracciones**: un equipo repartido en N lavarropas consume **1** unidad de
su línea de clasificación, no N. La fórmula está en `SQL_DISPONIBLES`
(`SUM(cantidad donde instancia IS NULL) + COUNT(DISTINCT instancia_equipo_id)`) y la relectura tiene
que usar **esa misma fórmula**, no una propia — si divergen, el bloqueo optimista pasa a rechazar
tandas legítimas.

**Archivos:**
- `features/lavadero/dao/ClasificacionLavaderoDAO.java`
- `features/lavadero/dao/CicloLavaderoDAO.java`
- `features/lavadero/controller/ClasificacionController.java`, `CiclosController.java`
- Tests.

**Tareas:**
1. `ClasificacionLavaderoDAO.guardar`:
   `UPDATE ingresos_lavadero SET estado = 'CLASIFICADO' WHERE id = ? AND estado = 'PENDIENTE'` +
   `ControlConcurrencia.exigirFilaAfectada(...)`. Va **antes** del batch de inserts, no después: si
   el ingreso ya no está `PENDIENTE`, no hay que insertar nada. Y dejar de tragarse la excepción —
   la firma `boolean` con `return false` en el `catch` es un fallo silencioso; que propague.
2. `lanzarTanda`: extraer el saldo por línea de clasificación a una constante `SQL_SALDO_DE_LINEA`
   **derivada de `SQL_DISPONIBLES`** (mismo `CASE`/`COUNT DISTINCT`, filtrada por `ecl.id = ?`, sin
   el filtro de estado del ingreso). Adentro de la transacción, antes de `crearInstancias`, releer el
   saldo de cada línea de la tanda y compararlo con lo que la tanda pretende consumir. Si no alcanza
   → `ConflictoConcurrenciaException` y la tanda entera se cae.
3. Que el chequeo y el consumo se intercalen sobre **la misma conexión**, para que dos líneas de la
   misma tanda que apuntan a la misma clasificación se vean entre sí. Es el mismo razonamiento que
   ya está escrito en el javadoc de `SalidaLavaderoDAO.marcarListo` — **citarlo ahí**.
4. Controllers: capturar el conflicto, mostrar el mensaje, refrescar. En `CiclosController` el
   staging de la tanda (`StagingCiclos`) se descarta entero, por el mismo motivo que en Lotes.
5. Tests de dos conexiones: (a) doble clasificación del mismo ingreso — la segunda falla y **no
   inserta ninguna línea**; (b) dos tandas sobre el mismo saldo — la segunda falla y no deja ni
   instancias ni ciclos; (c) **caso de fracciones**: dos tandas que reparten el mismo equipo,
   verificando que la fórmula de saldo no rechace un reparto legítimo dentro de una misma tanda.

**Verificación:** `mvn test -Dtest=ClasificacionLavaderoDAOTest+CicloLavaderoDAOTest` +
`mvn clean package` + smoke **sin `-Daptium.edt.strict=true`** (los autocompletados síncronos de
Lavadero lanzan en strict; leer los WARN del log en su lugar).
**Exit criteria:** ninguna de las dos operaciones deja datos a medias;
`detectarLineasSobregiradas()` sobre la base de prueba devuelve vacío después del smoke.
**Commit:** `feat: bloqueo optimista en clasificación y lanzamiento de tandas`

---

## Paso 7 — Salidas y derivación al CDE

**Contexto para arrancar en frío:** este es el flujo que **ya está casi bien**, y el paso es en
buena medida de consistencia. `SalidaLavaderoDAO.marcarListo:376` relee el saldo adentro de la
transacción y lo documenta; `SQL_ESTAMPAR_DESTINO` y `SQL_BORRAR_SALIDA_SIN_DESTINO` llevan
`AND destino IS NULL`; `SQL_SIGUE_SIN_DESTINO` relee antes de escribir. Lo que falta:

- Esas detecciones salen como `BusinessException` genérica, así que el controller no puede
  distinguir "no se puede" de "alguien se te adelantó" y el operador lee un mensaje de negocio
  cuando lo que le pasó fue un choque.
- `SQL_FINALIZAR_INGRESO:230` no tiene guarda de estado.
- La derivación al CDE (`DerivadorIngresoCDE`) es **el único punto donde una feature escribe en las
  tablas de otra**, y es irreversible: crea un `equipo_otros` real. Una doble derivación crea dos
  ingresos en el CDE. La protege el `AND destino IS NULL` del estampado, que corre **dentro de la
  misma transacción** que la creación del equipo — verificar que sigue siendo así y **dejarlo escrito
  en un test**, que hoy no existe.

**Archivos:** `features/lavadero/dao/SalidaLavaderoDAO.java`,
`features/lavadero/dao/derivadores/DerivadorIngresoCDE.java` (probablemente sin cambios),
`features/lavadero/controller/SalidasLavaderoController.java`, tests.

**Tareas:**
1. Cambiar las `BusinessException` de choque por `ConflictoConcurrenciaException` en `marcarListo`,
   `volverALavado` y `asignarDestino`. **Las que no son choque se quedan como están** — "la cantidad
   tiene que ser positiva" es una validación, no un conflicto. Revisar una por una.
2. `SQL_FINALIZAR_INGRESO` → `... WHERE id = ? AND estado <> 'FINALIZADO'`, sin exigir fila afectada
   (que ya esté finalizado es un no-op legítimo, no un choque). **Documentar esa asimetría en el
   javadoc**, porque es la única guarda del plan que no lanza.
3. `SalidasLavaderoController`: el conflicto muestra su mensaje y dispara el refresco del grupo
   `operativo` (esta pantalla es la única de Lavadero cableada a ese grupo, porque lo que deriva
   tiene que aparecer en el acto en el CDE).
4. Test de doble derivación: dos conexiones intentan derivar la misma salida; la segunda falla y
   **`equipo_otros` tiene exactamente una fila nueva**. Es el test que faltaba.

**Verificación:** `mvn test -Dtest=SalidaLavaderoDAOTest+DerivadorIngresoCDETest` +
`mvn clean package` + smoke de derivación doble.
**Exit criteria:** una salida no puede derivarse dos veces, y el mensaje de choque se distingue del
de regla de negocio.
**Commit:** `feat: conflictos de concurrencia explícitos en salidas de lavadero`

---

## Paso 8 — Auditoría del refresco al entrar (reducido tras verificación)

> **Este paso se achicó al escribir el plan.** La versión original decía que
> `ClasificacionController` y `SalidasLavaderoController` no releían al entrar porque no tienen
> `componentShown`. **Es falso.** Las dos releen — por el otro mecanismo:
>
> | Mecanismo | Dónde se usa | Ejemplo |
> |---|---|---|
> | `componentShown` en el controller | Pantallas del CDE y las de consulta | `EquiposParaEntregarController:58` |
> | `ActionListener` del botón de menú en `UiCoordinator` | Pantallas operativas de Lavadero | `UiCoordinator:198-201` (`cargarIngresosSinClasificar()`), `:227-230` (`cargarDatos()`), `:209-212` (`ciclosController.abrirPantalla()`) |
>
> El segundo no es un descuido: `CiclosController.abrirPantalla()` documenta en su javadoc por qué
> existe — resetea sólo las cards **libres** y recién después carga, porque `recargar()` a secas
> también corre después de lanzar/finalizar y "resetear ahí borraría lo que el operador está
> tipeando en otra card". Es exactamente el problema de "no pisar el staging", resuelto con un
> punto de entrada explícito en vez de con un evento de Swing.

**Contexto para arrancar en frío:** entonces no hay nada que agregar; hay algo que **verificar y
dejar escrito**. El riesgo real que queda es angosto: una pantalla a la que se llegue **sin pasar
por el botón de menú** (navegación entre pantallas, vuelta desde un diálogo) no releería.

**Archivos:** ninguno de producción, salvo que la auditoría encuentre un hueco. `CLAUDE.md` (nota).

**Tareas:**
1. Recorrer las cuatro pantallas en alcance y confirmar, **con el número de línea**, por dónde relee
   cada una al entrar. Anotar la tabla resultante.
2. Buscar rutas de navegación que **salteen** el botón de menú: `grep -n "getNavegador().show"` en
   los controllers. `ClasificacionController` recibe `navegador` y `contenedor` en su constructor, o
   sea que navega por su cuenta — verificar a dónde y si el destino relee.
3. Sólo si aparece un hueco real: taparlo con el mecanismo que ya usa esa pantalla (botón de menú si
   es de Lavadero, `componentShown` si es del CDE). **No unificar las dos convenciones acá** — es un
   refactor transversal que no tiene nada que ver con concurrencia y merece su propio plan.
4. Nota en `CLAUDE.md` documentando que **las dos convenciones conviven** y cuál va en cada zona.
   Hoy no está escrito en ningún lado, y es justo lo que me hizo escribir mal este paso.

**Verificación:** `mvn clean package` (probablemente sin cambios de código) + smoke navegando entre
pantallas con datos cambiando por detrás.
**Exit criteria:** está escrito por dónde relee cada pantalla en alcance, y no queda ninguna ruta de
entrada que muestre datos viejos.
**Commit:** `docs: convenciones de refresco al entrar` (o `fix:` si la auditoría encontró un hueco)

---

## Paso 9 — Suite de concurrencia y documentación

**Contexto para arrancar en frío:** los pasos 3-7 dejaron cada uno sus tests. Este paso los junta en
un lugar donde se lea la **regla** y no los casos sueltos, y cierra la documentación. Es también el
paso donde se verifica que el plan hizo lo que dijo.

`AbstractDAOTest` levanta H2 con `maximumPoolSize = 5`, así que dos conexiones simultáneas son
posibles sin infraestructura nueva. **H2 no es MySQL**: el aislamiento y el comportamiento de
`FOR UPDATE` difieren. Los tests tienen que verificar **la guarda** (0 filas afectadas → excepción),
que es idéntica en los dos motores, y no el comportamiento del lock, que no lo es. Dejarlo escrito
en el javadoc de la clase base de estos tests, o alguien va a escribir un test de deadlock que pasa
en H2 y miente sobre producción.

**Archivos:**
- **Nuevo** `src/test/java/com/example/infrastructure/db/ConcurrenciaOptimistaTest.java`
- `CLAUDE.md` (sección nueva)
- `plans/bloqueo-optimista-concurrencia.md` (este archivo: tabla de cierre)
- `plans/hallazgos-arquitectura-pendientes.md` (los tres huecos que quedan)

**Tareas:**
1. Suite transversal, un caso por flujo protegido, todos con la misma forma:
   *A lee → B modifica y commitea → A escribe → conflicto, y el estado final es exactamente el de B.*
   La última mitad es la que importa: verificar que **no quedó nada de A**.
2. Sección en `CLAUDE.md`, **"Concurrencia — bloqueo optimista"**, hermana de la de EDT, con:
   - la regla dura: *toda escritura que dependa de un dato leído antes lleva guarda, y toda guarda
     mira las filas afectadas*;
   - dónde vive el helper (`ControlConcurrencia`) y la excepción;
   - **por qué las tablas de detalle no tienen `version`** (falsos positivos en consumo por cantidad);
   - **por qué la `version` de `equipos`/`equipo_otros` existe, se mantiene, y NO se usa como guarda**
     — es lo que más fácil se "arregla" por error: alguien la ve mantenida y sin usar y le agrega el
     `WHERE`, reintroduciendo el falso positivo. Dejar escrito cuál es su consumidor previsto
     (`Correcciones`) y qué condición habría que cumplir para activarla;
   - que un JAR anterior a la V21 no puede escribir en una base migrada (Paso 2b), y por qué eso no
     sale gratis de Flyway;
   - qué quedó afuera y por qué.
3. Tabla de cierre al principio de este archivo, con paso → commit → mensaje (formato de
   `plans/historial-lavadero.md`).
4. Anotar en `hallazgos-arquitectura-pendientes.md`: (a) `Correcciones` sigue escribiendo a ciegas
   y ya tiene la `version` disponible; (b) `obtenerSiguienteSecuencia` de lotes; (c) las pantallas de
   ABM fuera de alcance.
5. `mvn verify` y reportar la cobertura JaCoCo de las clases nuevas.

**Verificación:** `mvn verify` en verde; `git log --oneline RetoquesFinalesL..HEAD` coincide con la
tabla de cierre.
**Exit criteria:** un agente que llegue frío a `CLAUDE.md` entiende la regla sin abrir este plan.
**Commit:** `docs: bloqueo optimista para acceso concurrente`

---

## Protocolo de mutación del plan

- **Un paso se parte** si al abrirlo aparece que toca más de ~6 archivos: se numera `5a`, `5b`, y se
  anota acá por qué.
- **Un paso se saltea** sólo si al leer el código resulta que el hueco no existe. Se anota el hallazgo
  con el número de línea que lo demuestra — un paso salteado sin evidencia es un paso olvidado.
- **Aparece un hueco nuevo** → se anota en la tabla de huecos y se decide si entra o va a
  `hallazgos-arquitectura-pendientes.md`. **No se agranda un paso en curso.**
- **La V21 ya aplicada no se modifica nunca.** Cambio de esquema posterior = V22.

---

## Plan de sesiones

Seis sesiones. El corte está donde cambia el **tipo de trabajo**, no donde se acaba el contexto: las
sesiones de diseño (1 y 6) son las que deciden cosas que las demás sólo aplican, y ahí es donde
conviene gastar razonamiento. Las de ejecución trabajan sobre decisiones ya tomadas.

Cada prompt es **autosuficiente**: incluye el archivo del plan, la rama y el paso. No hace falta
contexto de la sesión anterior.

---

### Sesión 1 — Fundaciones (Pasos 0, 1, 2)

**Modelo:** Opus 5 · **Esfuerzo:** alto · **Fast mode:** no

Es la sesión que decide la forma de todo lo demás: dónde vive el bump de `version`, qué API tiene el
helper, cómo se redactan los mensajes. Una decisión mala acá se paga en las cinco sesiones
siguientes. Además el Paso 2 arranca con un refactor (unificar el recálculo de "otros", que hoy está
duplicado) y termina de auditar qué rutas de escritura quedan fuera del bump — trabajo de lectura de
código, no de tipeo.

```
Leé plans/bloqueo-optimista-concurrencia.md y ejecutá los Pasos 0, 1 y 2 (crear la rama
BloqueoOptimista, infraestructura de conflicto, y la migración V21 con la columna version).

El Paso 2 tiene un hallazgo ya verificado en el plan: recalcularEstadoEquipoOtros está DUPLICADO
entre EquipoOtrosDAO:800 y LoteDAO:848. Unificalo en EquipoOtrosMaterialHelper ANTES de poner el
bump de version, en un commit propio. Quedate con la variante defensiva (la de LoteDAO, que chequea
rs.getObject("orden_minimo") != null).

Después completá la tarea 4 del paso: auditá las rutas que escriben equipo_otros fuera del recálculo
(LoteDAO:923 acumularVolumenEquipoOtros, EquipoOtrosDAO:162 y :628). Para cada una, o la hacés pasar
por el recálculo o documentás en el javadoc por qué esa columna no invalida un snapshot. Si aparece
una que no encaja en ninguna de las dos, paráme y decidimos.

Ojo con una cosa que ya está decidida y no hay que re-litigar: la columna version se CREA y se
MANTIENE incrementada, pero NO se usa como guarda en ningún WHERE de este plan. El motivo está en la
tabla de decisiones. No la agregues a EquipoKey ni a ninguna firma.

Al terminar, mvn clean package y mvn test en verde, y git diff no debe tocar ninguna migración
anterior a la V21.
```

**Si sobra tiempo en esta sesión, seguir con el Paso 2b** (el guard de versiones mixtas del JAR):
depende sólo del Paso 2, comparte contexto de Flyway con él, y es condición para poder desplegar.

---

### Sesión 1b — Guard de versiones mixtas del JAR (Paso 2b)

**Modelo:** Sonnet 5 · **Esfuerzo:** medio · **Fast mode:** no

Existe porque **cada máquina se actualiza cuando quiere**: sin este guard, un cliente con el JAR
viejo escribe sin guardas contra una base ya migrada durante días. La trampa está en el
`outOfOrder(true)` de `DatabaseInitializer:21`, que hace que "hay una migración que no conozco" y
"la base está adelantada" **no** sean lo mismo.

```
Leé plans/bloqueo-optimista-concurrencia.md y ejecutá el Paso 2b en la rama BloqueoOptimista
(la app se niega a arrancar contra una base más nueva que el JAR).

Contexto ya verificado, no lo re-investigues: Flyway 8.5.13 (pom.xml:112) tiene
ignoreFutureMigrations=true por defecto, así que hoy DatabaseInitializer.inicializar() ve una V21
que no conoce y arranca igual, sin avisar. Por eso hace falta el chequeo explícito.

Lo más importante: DatabaseInitializer:21 usa outOfOrder(true) a propósito (las ramas Lavadero y
RefactorVolúmenes se pisaron los números de migración). Una migración ATRASADA que se aplica después
NO es una base adelantada. Compará máximos, y escribí un test que cubra ese caso o vas a romper el
arranque de cualquiera que tenga una migración pendiente fuera de orden.
```

---

### Sesión 2 — Registrar Estado, los dos tipos (Pasos 3 y 4)

**Modelo:** Sonnet 5 · **Esfuerzo:** medio · **Fast mode:** sí

Las decisiones ya están tomadas en el plan; el trabajo es aplicarlas con cuidado en dos ramas
simétricas del mismo flujo. Van juntas a propósito: hacerlas en sesiones distintas es la forma más
segura de que queden asimétricas.

```
Leé plans/bloqueo-optimista-concurrencia.md y ejecutá los Pasos 3 y 4 en la rama BloqueoOptimista
(bloqueo optimista en Registrar Estado, ortopedias y otros).

El Paso 4 es el espejo del 3: mismos nombres, mismo orden de chequeos, mismo mensaje. Hacé el 3
completo primero y recién después el 4, copiando su forma.

Hay un punto abierto en la tarea 3 del Paso 3: si la version del equipo viaja en EquipoKey o como
parámetro de aplicarMovimientos. Elegí una y dejá escrito el porqué en el javadoc.

Los tests de concurrencia van con dos conexiones H2 reales, no con mocks: el punto es que la guarda
falle de verdad. Un commit por paso.
```

---

### Sesión 3 — Lanzar Lote (Paso 5)

**Modelo:** Sonnet 5 · **Esfuerzo:** medio · **Fast mode:** sí

Sesión sola porque `LoteDAO` tiene 948 líneas y el staging en memoria de `LotesController` es lo más
enredado del plan. Independiente de la Sesión 2: puede correr en paralelo si hay dos ventanas.

```
Leé plans/bloqueo-optimista-concurrencia.md y ejecutá el Paso 5 en la rama BloqueoOptimista
(bloqueo optimista al lanzar lotes).

Ojo con el hueco (3) de la tabla: hoy LoteDAO.aplicarMovimientoLote trae `estado` en el SELECT FOR
UPDATE y sólo lo usa para el registro de movimiento. Además de la guarda de estado hace falta
chequear lote_id IS NULL — un material ya lanzado en otro lote se cuela igual.

Verificá en los tests que un lote que falla no deja fila huérfana en `lotes`.
```

---

### Sesión 4 — Lavadero completo (Pasos 6 y 7)

**Modelo:** Opus 5 · **Esfuerzo:** alto · **Fast mode:** no

La única sesión de ejecución que necesita razonamiento fuerte: la fórmula de saldo del Paso 6 tiene
que coincidir exactamente con `SQL_DISPONIBLES`, incluida la invariante de fracciones (un equipo
repartido en N lavarropas consume 1 unidad, no N). Si divergen, el bloqueo rechaza tandas legítimas
— un bug peor que el que viene a arreglar. El Paso 7 va con ella porque comparte todo ese contexto.

```
Leé plans/bloqueo-optimista-concurrencia.md y ejecutá los Pasos 6 y 7 en la rama BloqueoOptimista
(clasificación y lanzamiento de tandas; salidas y derivación al CDE).

Crítico en el Paso 6: la relectura de saldo tiene que derivarse de SQL_DISPONIBLES, con la misma
fórmula de fracciones (SUM(cantidad donde instancia IS NULL) + COUNT(DISTINCT instancia_equipo_id)).
No escribas una fórmula propia. Leé plans/fracciones-de-equipo-persistidas.md antes de tocar
CicloLavaderoDAO.

El Paso 7 es mayormente de consistencia: el flujo ya releía bien. Revisá una por una las
BusinessException de SalidaLavaderoDAO y convertí sólo las que son choque, no las validaciones.

El smoke de Lavadero se corre SIN -Daptium.edt.strict=true (los autocompletados síncronos lanzan);
leé los WARN del log. Un commit por paso.
```

---

### Sesión 5 — Auditoría del refresco al entrar (Paso 8)

**Modelo:** Sonnet 5 · **Esfuerzo:** medio · **Fast mode:** no

Cambió de forma respecto de la primera versión del plan: era "escribir dos `ComponentAdapter`" (tarea
de Haiku) y resultó ser **una auditoría**, porque las dos pantallas ya releen por otro mecanismo. Una
auditoría que concluye "no hay nada que hacer" sólo vale si el que la hizo entendió las dos
convenciones — y el riesgo acá es un agente que "arregle" algo que no está roto y meta un refresco
duplicado que le borre el staging al operador.

**Va después de la Sesión 4, no en paralelo:** toca los mismos dos controllers que los Pasos 6 y 7.

```
Leé plans/bloqueo-optimista-concurrencia.md y ejecutá el Paso 8 en la rama BloqueoOptimista
(auditoría del refresco al entrar).

Leé primero el recuadro del Paso 8: este paso probablemente NO necesita cambios de código.
ClasificacionController y SalidasLavaderoController YA releen al entrar, por el ActionListener del
botón de menú en UiCoordinator (:198-201 y :227-230), no por componentShown. El repo tiene dos
convenciones conviviendo y las pantallas de Lavadero usan la segunda.

Tu trabajo es verificar por dónde relee cada pantalla en alcance (con número de línea), buscar rutas
de navegación que salteen el botón de menú, y documentar la convención en CLAUDE.md.

Agregá un componentShown SÓLO si encontrás una ruta de entrada concreta que muestre datos viejos.
Un refresco duplicado le borra al operador lo que está armando — mirá el javadoc de
CiclosController.abrirPantalla(), que explica exactamente ese riesgo.
```

---

### Sesión 6 — Cierre (Paso 9)

**Modelo:** Opus 5 · **Esfuerzo:** alto · **Fast mode:** no

Escribir la regla en `CLAUDE.md` para que un agente frío la aplique bien es trabajo de redacción
técnica, y es lo que hace que el plan sobreviva a su propia ejecución. Además esta sesión **revisa
adversarialmente** lo que hicieron las cinco anteriores: buscar la guarda que quedó sin `exigirFila`,
el `catch` que se traga un conflicto, la asimetría entre ortopedias y otros.

```
Leé plans/bloqueo-optimista-concurrencia.md y ejecutá el Paso 9 en la rama BloqueoOptimista
(suite de concurrencia, sección de CLAUDE.md, cierre del plan y hallazgos pendientes).

Antes de escribir nada, revisá adversarialmente los Pasos 3 a 8 con git diff RetoquesFinalesL..HEAD.
Buscá específicamente: guardas que no miran filas afectadas, catch que se traguen un
ConflictoConcurrenciaException, y asimetrías entre el camino de ortopedias y el de otros. Si
encontrás algo, arreglalo antes de documentar.

La sección de CLAUDE.md tiene que explicar POR QUÉ las tablas de detalle no llevan columna version
(falsos positivos en el consumo por cantidad). Es la decisión que más fácil se revierte por error.

Cerrá con mvn verify y reportá la cobertura JaCoCo de las clases nuevas.
```

---

### Resumen

| Sesión | Pasos | Modelo | Esfuerzo | Por qué ese modelo |
|---|---|---|---|---|
| 1 | 0, 1, 2 | **Opus 5** | Alto | Decide la forma de todo el resto; el Paso 2 es verificación de código, no tipeo |
| 1b | 2b | Sonnet 5 | Medio | Condición de despliegue; la trampa del `outOfOrder` es concreta y está señalada |
| 2 | 3, 4 | Sonnet 5 | Medio | Decisiones ya tomadas; dos ramas simétricas del mismo flujo |
| 3 | 5 | Sonnet 5 | Medio | Aplicación mecánica sobre un archivo grande |
| 4 | 6, 7 | **Opus 5** | Alto | La fórmula de saldo con fracciones no admite una aproximación |
| 5 | 8 | Sonnet 5 | Medio | Auditoría cuyo resultado esperado es "no hay nada que hacer"; el riesgo es arreglar lo que no está roto |
| 6 | 9 | **Opus 5** | Alto | Revisión adversarial + redacción de la regla para agentes fríos |

**Paralelizables: sólo las sesiones 2 y 3** (no comparten un archivo). La 4 tampoco comparte
archivos con ellas, pero conviene no correrla en paralelo con nada: es la que más contexto de
dominio necesita. **La 5 va después de la 4** — toca los mismos dos controllers.
