# Plan — Hallazgo #6: concurrencia ad-hoc y trabajo de BD en el EDT

Plan escrito el 2026-07-23. Ejecutar **después** de este chat, en 3 chats nuevos:
Fases 1-3 / Fases 4-5 / Fase 6.

Las **Fases 1-5 sacan el trabajo de BD del EDT** (el hallazgo #6 propiamente dicho). La
**Fase 6 reduce ese trabajo** filtrando en SQL. Son dos refactors con riesgos distintos —
1-5 no cambian qué datos ve el usuario, la 6 sí — y por eso se verifican por separado. La
Fase 6 no arranca hasta que 1-5 estén en verde y con la checklist manual pasada.

Diagnóstico de origen: [hallazgos-arquitectura-pendientes.md](hallazgos-arquitectura-pendientes.md#6--concurrencia-ad-hoc-y-trabajo-de-bd-en-el-edt).

---

## Estado de ejecución (2026-07-23)

**Fases 1 a 5: hechas.** 11 commits en `UXhotfix`, de `110d7c3` a `0064570`.
555 tests en verde (eran 521; +34 nuevos).

**Fase 5: pasada por el usuario contra la app real (2026-07-23). Funciona todo.**
Con eso el hallazgo #6 propiamente dicho — sacar el trabajo de BD del EDT — está
cerrado. La Fase 6 (*reducir* ese trabajo) queda habilitada para arrancar.

Piezas nuevas: `EdtGuard`, `TareaUI`, `DatosRefresco`, `LectorDatosRefresco`,
`RefrescadorPantallas`, `AgrupadorEntregas`, `ConstructorMaterialesDisponibles`,
`FiltroAuditorias`. Los 6 controllers pasaron de `cargarDatos()` a `pintar(DatosRefresco)`.

**Desvío respecto del plan:** el constructor muerto de `LotesController` con
`equipoContexto` se **eliminó** en vez de anotarse para el refactor-clean. Sostenerlo
obligaba a una segunda rama en el camino de pintado y bloqueaba la extracción de
`ConstructorMaterialesDisponibles`. Confirmado sin llamadores antes de borrarlo.

**Pendiente de verificar contra la app real** (no se pudo en la sesión, requiere BD):
el arranque, y la lista de WARNs que quedan del `EdtGuard`.

### Verificación manual iniciada el 2026-08-27 (rama `ConexionConCDE`)

Al correr el punto 2 de la checklist (guardar un ingreso de ortopedia) apareció un WARN
del `EdtGuard` **no previsto**: `IngresoOrtopediaController.guardar` →
`EquipoService.guardarEquipo` → `EquipoDAO.guardarEquipo` → `TransactionalConnection.begin`.
Es la **transacción de alta completa corriendo en el EDT**.

Causa: el inventario de las Fases 1-4 midió solo los `cargarDatos()` de refresco y los
`new Thread()` de Correcciones/Ajustes. **Las escrituras del flujo principal**
(alta de ingreso, confirmar cambios de estado, lanzar/finalizar/marcar-fallo de lote,
entregar completo) nunca entraron: son llamadas síncronas al service en el `ActionListener`,
sin `TareaUI`. Estaban antes del refactor y siguen igual. Correcciones y Ajustes sí se
migraron en la Fase 4 — quedó la mitad de las escrituras en un modelo y la otra mitad en
otro, que es justo lo que #6 venía a eliminar.

Consecuencia: el **punto 11** de la Fase 5 no puede pasar como está escrito (hay más de 4
WARNs), y en `strict` el guardado **lanza excepción**, así que la checklist no se puede
completar. Se agrega la **Fase 4b** (abajo) para cerrar esto antes de retomar la Fase 5.

### Lista de trabajo medida para la Fase 4

`grep "new Thread("` → **18** ocurrencias fuera de `App.java`:

| Archivo | Cantidad | Nota |
|---|---|---|
| `CorreccionsController` | 12 | 10 operaciones + `cargarEquiposNuevos` + autocompletado de descripción |
| `AjustesController` | 5 | 2 de ellas leen `obtenerTodosLosClientes()` |
| `App.java` | 1 | shutdown hook, **no se toca** |

Accesos síncronos a BD que van a seguir gritando en el `EdtGuard` y **no** estaban en
el inventario original de la Fase 1 — son autocompletados, no `cargarDatos()`:

- `OrthopediaInputController:59` — `catalogoService.obtenerDescripcion()` dentro del
  `CatalogoLookup` que consume `GestorValidacionFormulario`: corre en cada validación de campo.
- `OrthopediaInputController:69` — ídem en el listener `onNumeroChanged` del panel de materiales.
- `AutocompleteListener` (profesionales, instituciones, clientes) — hay que revisar si
  busca en el EDT en cada tecla.

Estos son lookups de una fila y sobre índice, así que el costo es bajo, pero rompen la
regla 1 y el guard los va a marcar. Decidir en la Fase 4 si se migran a `TareaUI` o si
se documenta la excepción.

### Resultado de la Fase 4 (2026-07-23)

`grep "new Thread(\|SwingWorker"` fuera de `TareaUI` → **1** ocurrencia: el shutdown hook
de `App.java`. Las dos verificaciones de la fase pasan.

Se migró más de lo mínimo: además de los 17 `new Thread()`, entraron los **3 `SwingWorker`
de reportes** (`VerLotes`, y los dos de `VerEquipos`), que el plan dejaba como opcionales.
Con eso `TareaUI` es el único punto de la app que toca `SwingWorker`, que es la regla 2.

En vez de traducir cada bloque uno a uno, las 9 correcciones se unificaron en
`CorreccionsController.aplicarCorreccion(...)` y las 3 mutaciones de clientes en
`AjustesController.mutar(...)`: era el mismo boilerplate copiado, y tenerlo repetido es
exactamente lo que dejó que se desincronizaran los `catch`.

**Autocompletados síncronos: se documentan como excepción, no se migran.** Son
`OrthopediaInputController:59` y `:69`, `VerEquiposController.abrirDetalleOtros()`
(`obtenerPorId` al doble click) y el `searchFunction.apply()` de `AutocompleteListener`.
Razón: son lookups de una fila sobre índice, no son la fuente del congelamiento, y
volverlos asíncronos **agrega** riesgo — el de `AutocompleteListener` corre dentro de un
`DocumentListener`, uno por tecla, así que async sin debounce trae resultados fuera de
orden y popup parpadeando. Es un refactor distinto (hace falta debounce, no solo cambiar
de hilo) y va con la lógica de la Fase 6 (*reducir* el trabajo), no con la de 1-5
(*mover* el trabajo). Consecuencia asumida: el `EdtGuard` va a seguir marcando estos
sitios; la Fase 5 debe verificar que la lista de WARNs sea **exactamente** esa y nada más.

---

## Decisiones tomadas (2026-07-23)

| Decisión | Elección | Alternativa descartada |
|---|---|---|
| Mecanismo | **Helper propio sobre `SwingWorker`** (`TareaUI`) | `SwingWorker` crudo en ~25 sitios |
| Refresco global | **Una lectura compartida** por refresco, repartida a todas las pantallas | Cada controller lee lo suyo, async e independiente |
| Alcance | **Todo**: los 5 `cargarDatos()` síncronos **y** los ~20 `new Thread()` | Solo sacar la BD del EDT |

---

## Estado medido (2026-07-23, sobre el working tree actual)

**Trabajo de BD en el EDT — 5 sitios, todos disparados por `UiCoordinator.crearRefrescador()`:**

| Controller | Método | Queries |
|---|---|---|
| `CDEViewController` | [`cargarDatos()`:48](../src/main/java/com/example/features/equipos/ortopedias/controller/CDEViewController.java#L48) | `equipos` + `otros` |
| `RegistrarEstadoController` | [`cargarEquipos()`:102](../src/main/java/com/example/features/equipos/ortopedias/controller/RegistrarEstadoController.java#L102) | `equipos` + `otros` |
| `EquiposParaEntregarController` | [`cargarDatos()`:98](../src/main/java/com/example/features/equipos/ortopedias/controller/EquiposParaEntregarController.java#L98) | `equipos` + `otros` |
| `LotesController` | [`cargarDatos()`:189](../src/main/java/com/example/features/lotes/controller/LotesController.java#L189) | `volumenes` + `autoclaves` + `lotesActivos` + `equipos` + `otros` |
| `VerLotesController` | [`cargarDatos()`:51](../src/main/java/com/example/features/lotes/controller/VerLotesController.java#L51) | `autoclaves` + `todosLosLotes` |

**13 queries en serie sobre el EDT por cada guardado**, de las cuales `equipoService.obtenerTodos()`
y `equipoOtrosService.obtenerTodos()` se repiten **4 veces cada una** — la misma tabla completa,
sin paginar, leída 4 veces. `VerEquiposController` suma 2 más por su cuenta (ya async).

**Concurrencia ad-hoc — ~20 `new Thread()`:**
- `CorreccionsController`: **10** bloques con el mismo boilerplate copiado
  (`mostrarCargando(true)` → thread → `invokeLater` éxito / `invokeLater` error ×2 catch).
- `AjustesController`: **5**, mismo patrón.
- `VerEquiposController` [:98](../src/main/java/com/example/features/equipos/controller/VerEquiposController.java#L98):
  1, y **se traga el error** — el `catch` solo loguea, la UI queda con datos viejos sin avisar.
- `PantallaAuditoria` [:219](../src/main/java/com/example/features/equipos/ortopedias/view/PantallaAuditoria.java#L219):
  1, **dentro de una view** (la view habla con el service directo).
- `App.java:52`: shutdown hook — **legítimo, no se toca**.

**Lo único que ya está bien:** los 2 `SwingWorker` de generación de reportes
(`VerLotesController:96`, `VerEquiposController:207` y `:229`).

**Sin cobertura:** no hay tests de controllers (`src/test/.../lotes/controller/` solo tiene
`helpers/`). Ningún test de la suite actual detecta un fallo de EDT ni un refresco fuera de orden.

---

## Arquitectura objetivo

```
                       ┌─────────────────────────────────────┐
   guardado / listener │  RefrescadorPantallas               │
   ────────────────────▶  · debounce 150 ms (coalesce)       │
                       │  · cancela/invalida el anterior     │
                       └──────────────┬──────────────────────┘
                                      │ TareaUI
                    fuera del EDT ────┤
                                      ▼
                       ┌─────────────────────────────────────┐
                       │  LectorDatosRefresco.leer()         │
                       │  6 queries, UNA vez  →  DatosRefresco│
                       └──────────────┬──────────────────────┘
                                      │
                    en el EDT ────────┤ un solo invokeLater
                                      ▼
              cde.pintar(d)  registrar.pintar(d)  entregar.pintar(d)
              lotes.pintar(d)  verLotes.pintar(d)  verEquipos.pintar(d)
```

**Tres reglas que quedan establecidas:**
1. **Ningún acceso a BD en el EDT.** Verificado por un detector en runtime (Fase 1), no por
   disciplina.
2. **Ningún `new Thread()` en la app** salvo el shutdown hook. Todo trabajo en fondo va por
   `TareaUI`.
3. **Los controllers no leen: pintan.** Reciben un snapshot ya leído, lo transforman y lo
   vuelcan al panel. Eso los vuelve sincrónicos y testeables con un record fabricado.

---

## Fase 1 — Red de seguridad y mecanismo base

Nada de esto cambia comportamiento; es la infraestructura para que las fases siguientes sean
verificables.

### 1.1 `EdtGuard` — detector de acceso a BD en el EDT

Sin esto, la única verificación de las fases 2-4 sería "abrir la app y mirar". Con esto, cada
violación deja un WARN con stack trace apuntando al culpable.

- `ConnectionPool.getConnection()` consulta un hook estático antes de entregar la conexión:
  ```java
  // ConnectionPool (infrastructure) — sin imports de AWT/Swing
  private static BooleanSupplier detectorHiloUi = () -> false;
  public static void setDetectorHiloUi(BooleanSupplier d) { detectorHiloUi = d; }
  ```
- `App.main` lo cablea: `ConnectionPool.setDetectorHiloUi(EventQueue::isDispatchThread)`.
- Al detectar violación: `log.warn` con `new Throwable()` para el stack. Escalable a excepción
  con `-Daptium.edt.strict=true` (útil mientras se ejecuta este refactor; **no** activar en prod).

> **Por qué el hook y no llamar a `EventQueue.isDispatchThread()` directo:** el hallazgo #5 ya
> estableció que las capas de abajo no dependen de la UI. Un `BooleanSupplier` inyectado deja
> a `infrastructure` sin saber que existe Swing, y de paso hace el guard testeable sin EDT.
> Si al implementarlo esto se siente sobre-ingeniería, la alternativa aceptable es importar
> `java.awt.EventQueue` (JDK, no nuestra capa de UI) y documentar la excepción.

**Test:** `ConnectionPoolTest` (o nuevo `EdtGuardTest`) — con el detector devolviendo `true`
y `strict` activo, `getConnection()` lanza; con `false`, no.

### 1.2 `TareaUI<T>` — el único mecanismo de trabajo en fondo

Ubicación: `ui/common/TareaUI.java` (junto a `RestriccionesCampo`, que #5 puso ahí).
Envuelve `SwingWorker<T,Void>`.

```java
TareaUI.<DatosRefresco>nueva()
    .leer(lector::leer)                  // fuera del EDT
    .pintar(d -> aplicar(d))             // en el EDT
    .siFalla(e -> panel.mostrarError(e)) // en el EDT
    .antes(() -> panel.mostrarCargando(true))
    .despues(() -> panel.mostrarCargando(false))  // siempre, éxito o error
    .lanzar();
```

Requisitos:
- `siFalla` **obligatorio** o con default que al menos loguea a ERROR — el patrón actual de
  tragarse el error (`VerEquiposController`) no debe poder reproducirse por omisión.
- `lanzar()` devuelve un handle cancelable (para 1.3 y para el refrescador).
- Ninguna excepción de `leer()` se pierde: se rutea a `siFalla` en el EDT.
- Hilo con nombre (`Thread.currentThread().setName(...)`) para que los logs sigan siendo legibles.

**Test:** `TareaUITest` con `CountDownLatch` — verifica que (a) `leer` corre fuera del EDT,
(b) `pintar` corre en el EDT, (c) una excepción en `leer` llega a `siFalla` y no a `pintar`,
(d) `despues` corre en ambos caminos, (e) una tarea cancelada no pinta.

**Verificación de fase:** `mvn test` en verde. La app se comporta idéntico (nadie usa `TareaUI`
todavía), pero al arrancar el log debe **llenarse de WARNs del `EdtGuard`** — esa es exactamente
la lista de trabajo de la Fase 2. Anotarla.

---

## Fase 2 — Refresco global con lectura compartida

### 2.1 `DatosRefresco` + `LectorDatosRefresco`

```java
public record DatosRefresco(
    List<Equipo>           equipos,          // equipoService.obtenerTodos()
    List<EquipoOtros>      equiposOtros,     // equipoOtrosService.obtenerTodos()
    List<Autoclave>        autoclaves,
    Map<Integer,Integer>   volumenesCatalogo,
    Map<String,Lote>       lotesActivos,
    List<Lote>             todosLosLotes
) {}
```

`LectorDatosRefresco` es una clase plana que recibe los 5 services por constructor y expone
`DatosRefresco leer()`. **13 queries → 6.** Todas las pantallas pasan a ver el mismo instante
de la BD (hoy cada una ve uno distinto).

Ubicación: `app/ui/` — es la única pieza fuera de `UiCoordinator` que ve varios services de
features distintas, y `UiCoordinator` la construye desde `AppContext`, respetando la regla de
extensión del `CLAUDE.md`.

**Test:** `LectorDatosRefrescoTest` con services mockeados — verifica que llama cada query
exactamente una vez y arma el record completo.

### 2.2 Convertir cada controller: `cargarDatos()` → `pintar(DatosRefresco)`

Un sub-paso (y un commit) por controller, de menor a mayor riesgo:

| Orden | Controller | Trabajo |
|---|---|---|
| 1 | `CDEViewController` | Trivial: `pintar(d)` concatena `d.equipos()` + `d.equiposOtros()` y llama `recargarCache`. |
| 2 | `VerLotesController` | Trivial: usa `d.autoclaves()` y `d.todosLosLotes()`. |
| 3 | `RegistrarEstadoController` | Filtro `!= ENTREGADO` sobre el snapshot. Sin I/O. |
| 4 | `EquiposParaEntregarController` | **Extraer la transformación**: las ~45 líneas que arman `filasInstituciones` / `materialesPorDestino` / `volumenPorDestino` salen a una clase plana (ej. `AgrupadorEntregas`) que recibe `(List<Equipo>, List<EquipoOtros>, IEstadoValidator)` y devuelve un record con las 3 estructuras. `pintar()` queda en 4 líneas. |
| 5 | `VerEquiposController` | Entra al refresco global (hoy va por su cuenta). `pintar(d)` setea `todosOrtopedia`/`todosOtros`/`cargado` y llama `aplicarFiltros()`. Los filtros del usuario **deben sobrevivir** al refresco. |
| 6 | `LotesController` | El más delicado — ver 2.3. |

Para cada uno: el método `pintar()` resultante es 100% EDT y sin I/O → se le puede escribir un
test con un `DatosRefresco` fabricado. Donde la transformación se extrae a clase plana (4 y 6),
el test va sobre la clase plana, siguiendo el patrón ya establecido en el repo
(`AgrupadorIngresosLote`, `ReconciliadorPendientes`, `SincronizadorVolumenFinal`).

### 2.3 `LotesController` — cuidados específicos

Es el único con estado mutable compartido que otras interacciones leen:
`volumenesCatalogo`, `lotesActivos`, `clientesPorEquipo`, `equiposOtrosPorId`,
`ingresoOrtopediaPorEquipo`, `materialesDisponibles`.

- Ese estado lo leen el **DnD** ([:468-472](../src/main/java/com/example/features/lotes/controller/LotesController.java#L468))
  y los **tooltips** ([:166-167](../src/main/java/com/example/features/lotes/controller/LotesController.java#L166),
  que resuelven perezosamente en cada hover). **Regla: solo se escribe en el EDT, dentro de
  `pintar()`.** Nunca desde el hilo de fondo.
- `construirMaterialesDisponibles()` + `aplicarPendientesEnDisponibles()` (~70 líneas puras)
  se extraen a una clase plana testeable; hoy dependen de campos del controller, así que hay que
  pasarles lo que necesitan por parámetro.
- Preservar la selección de autoclave (`autoclaveSeleccion`), que ya se guarda y restaura.
- **Verificar antes de tocar:** el constructor sobrecargado con `equipoContexto`
  ([:128](../src/main/java/com/example/features/lotes/controller/LotesController.java#L128)) no
  tiene ningún llamador en `src/main` (el único `new LotesController` es el de `UiCoordinator`).
  Si es código muerto, **no** lo cargues en este refactor: anotalo para el refactor-clean (paso 4
  del plan de sesiones) y seguí. Si está vivo, ese camino lee un subconjunto y necesita su propia
  rama en `pintar()`.

### 2.4 `RefrescadorPantallas`

Reemplaza a `UiCoordinator.crearRefrescador()`. Clase propia en `app/ui/`:

- **`solicitar()`** — reinicia un `javax.swing.Timer` no-repetitivo de 150 ms. Ráfagas de
  refrescos (típico: una corrección dispara `cargarEquiposNuevos()` **y**
  `notificarCambiosAplicados()`) colapsan en uno solo. `Timer` de Swing dispara en el EDT, así
  que no agrega un modelo de concurrencia más.
- Al disparar: cancela/invalida la `TareaUI` en vuelo (token de generación, **no** solo
  `cancel(true)` — interrumpir un JDBC en curso no es confiable; el token garantiza que un
  resultado viejo nunca pinte) y lanza una nueva.
- Un único `pintar` reparte a los 6 controllers dentro del mismo bloque EDT → las pantallas
  quedan coherentes entre sí, cosa que hoy no está garantizada.
- Los `componentShown` de `CDEView`, `VerLotes` y `VerEquipos` pasan a llamar `solicitar()`.
  Sí, mostrar una pantalla relee las 6 queries; es una sola pasada en fondo y no bloquea nada.
  A cambio, desaparece todo camino de lectura alternativo.

**Riesgo a vigilar — arranque:** hoy 6 controllers hacen I/O en su constructor, dentro del EDT
(vienen de `SwingUtilities.invokeLater(this::inicializarVista)`). Al sacarlo, la UI aparece
**vacía y después se puebla**. Los constructores dejan de llamar `cargarDatos()` y
`UiCoordinator.inicializar()` termina con un `refrescador.solicitar()`. Verificar que ningún
panel explote con listas vacías en el primer pintado.

**Verificación de fase:** `mvn test` verde, y al arrancar la app **los WARNs del `EdtGuard`
anotados en la Fase 1 desaparecen** salvo los que correspondan a las escrituras de la Fase 4.

---

## Fase 3 — Cortar el `new Thread()` de la view

`PantallaAuditoria` habla con `correccionService` directo desde la view y lanza su propio hilo
([:219](../src/main/java/com/example/features/equipos/ortopedias/view/PantallaAuditoria.java#L219)).
Es el único caso así en la app y rompe la separación view/controller que el resto respeta.

- La carga y el filtrado se mueven a `CorreccionsController` (que ya inicializa esa pantalla vía
  `inicializarPantallaAuditoria`), usando `TareaUI`.
- `PantallaAuditoria.inicializar(correccionService)` pierde el parámetro service: pasa a recibir
  callbacks, como el resto de las views.
- El filtrado (`aplicarFiltros` sobre `auditoriasCargadas`) es lógica pura sobre una lista →
  candidato a clase plana + test.

Fase corta y aislada; puede ir junto con la 2 o con la 4 según cómo venga el contexto del chat.

---

## Fase 4 — Migrar los ~20 `new Thread()` a `TareaUI`

Mecánico, pero es lo que deja **un solo** modelo de concurrencia.

- **`CorreccionsController` (10 sitios)** — todos son la misma forma:
  `mostrarCargando(true)` → operación de escritura → éxito: mensaje + `cargarEquiposNuevos()` +
  `notificarCambiosAplicados()`; error: `ValidationException` / `DatabaseException` →
  `mostrarError`. Con `TareaUI` (`antes`/`despues` para el `mostrarCargando`) cada bloque baja de
  ~15 líneas a ~5. Ojo: **el doble disparo de refresco** (`cargarEquiposNuevos()` +
  `notificarCambiosAplicados()`) queda absorbido por el debounce de 2.4.
- **`AjustesController` (5 sitios)** — igual, con `JOptionPane` en el `siFalla`.
- **`VerEquiposController` (1)** — desaparece: pasa a `pintar(d)` en la Fase 2. **Su error
  tragado se arregla solo** al no existir más ese catch.
- **`App.java:52`** — shutdown hook, **no se toca**.

**Verificación de fase:**
`grep -rn "new Thread(" src/main/java` → **una sola** ocurrencia (`App.java`).
`grep -rn "SwingWorker" src/main/java` → solo `TareaUI` y los 2 reportes (o los reportes también
migran a `TareaUI` si el helper les queda cómodo; decidir al llegar, no es obligatorio).

---

## Fase 4b — Escrituras del flujo principal fuera del EDT

**Agregada el 2026-08-27.** La Fase 4 migró las escrituras de *corrección/ajuste* pero dejó
síncronas las del flujo principal. Esta fase las lleva a `TareaUI`, con el mismo patrón ya
probado en `CorreccionsController.aplicarCorreccion(...)` y `AjustesController.mutar(...)`.

### Por qué importa

- En `-Daptium.edt.strict=true` (lo que exige la Fase 5) el guardado **lanza excepción**: la
  app no es usable en el modo con el que hay que verificarla.
- Objetivo declarado de #6: *un solo* modelo de concurrencia. Con la mitad de las escrituras
  síncronas, no se cumple.
- Riesgo práctico de congelamiento: **bajo** hoy (son `INSERT`/`UPDATE` acotados sobre BD
  local, no lecturas de histórico sin techo). Esto es corrección de consistencia y de
  "ejecutable en strict", no un bug de rendimiento urgente.

### Sitios a migrar (7, en 4 controllers)

| # | Sitio | Operación | Notas de migración |
|---|---|---|---|
| 1 | [`IngresoOrtopediaController.guardar():124`](../src/main/java/com/example/features/equipos/ortopedias/controller/IngresoOrtopediaController.java#L124) | `equipoService.guardarEquipo(equipo)` | Validación de formulario y `construir()` quedan en el EDT. Solo la llamada al service va a fondo. `ValidationException` → `siFalla`. Éxito → `manejarResultadoGuardado(...)` en `pintar`. |
| 2 | [`OtrosInputController.persistir():109`](../src/main/java/com/example/features/equipos/otros/controller/OtrosInputController.java#L109) | `equipoOtrosService.guardarEquipo(equipo)` | Ídem #1. El armado del `EquipoOtros` (REMITO/DETALLES) queda en el EDT. |
| 3 | [`RegistrarEstadoController.confirmarCambios():279/281`](../src/main/java/com/example/features/equipos/common/controller/RegistrarEstadoController.java#L279) | `materialService.aplicarMovimientos` / `equipoOtrosService.aplicarMovimientos` en **loop** | El loop entero (todas las entradas de `cambiosPendientes`) va en **una** `TareaUI.leer`, devolviendo el acumulado de éxitos/errores. No lanzar una tarea por equipo. `pintar` arma el mensaje final y dispara el refresco. |
| 4 | [`LotesController.lanzarLote():470`](../src/main/java/com/example/features/lotes/controller/LotesController.java#L470) | `loteService.lanzarLote(...)` | **Delicado.** Los diálogos (`DialogoVolumen...`, confirmaciones) y la lectura de estado mutable (`volumenesPorIngreso`, `pendientesPorAutoclave`) quedan en el EDT. A fondo va solo `lanzarLote(...)`. `pendientesPorAutoclave.remove(...)`, `solicitarRefresco.run()` y `onEstadosActualizadosListener` van en `pintar` (EDT). Respeta la regla dura del plan: campos del controller se escriben solo en el EDT. |
| 5 | [`LotesController.finalizarLote():573`](../src/main/java/com/example/features/lotes/controller/LotesController.java#L573) | `loteService.finalizarLote(...)` | Confirmación en EDT; service a fondo; refresco en `pintar`. |
| 6 | [`LotesController.marcarFallo():589`](../src/main/java/com/example/features/lotes/controller/LotesController.java#L589) | `loteService.marcarLoteFallo(...)` | Ídem #5. |
| 7 | [`EquiposParaEntregarController:145/146`](../src/main/java/com/example/features/equipos/common/controller/EquiposParaEntregarController.java#L145) | `equipoOtrosService.entregarClienteCompleto` / `materialService.entregarInstitucionCompleta` | La selección de la fila y la confirmación quedan en EDT; el `entregar*` va a fondo; refresco en `pintar`. |

**Fuera de alcance de 4b** (anotar, no tocar): las escrituras de Lavadero
(`LavaderoController`, `ClasificacionController`, `CiclosController`, `SalidasLavaderoController`).
Esa feature es posterior a #6 y de otra rama; algunas ya usan un `ejecutar(...)` propio.
Si se quieren revisar, va como hallazgo aparte.

### Patrón a aplicar (idéntico a `aplicarCorreccion`)

```java
TareaUI.<Boolean>nueva()
    .nombre("guardar-ingreso-ortopedia")
    .leer(() -> equipoService.guardarEquipo(equipo))   // fondo
    .pintar(exito -> manejarResultadoGuardado(exito, ...))   // EDT
    .siFalla(e -> panel.mostrarAdvertencia(describirError(e)))   // EDT
    .antes(()  -> panel.getBtnGuardar().setEnabled(false))
    .despues(() -> panel.getBtnGuardar().setEnabled(true))
    .lanzar();
```

- **Botón deshabilitado mientras corre** (`antes`/`despues`), para que no haya doble submit.
- `ValidationException` viaja por `siFalla`: el `try/catch` síncrono actual desaparece.
- Éxito/refresco/navegación: **siempre** en `pintar`, nunca en `leer`.
- Si un controller repite el patrón (Lotes tiene 3), extraer un helper privado local
  al estilo `aplicarCorreccion` / `mutar`. No un helper compartido nuevo entre controllers.

### TDD

El repo testea la lógica de Swing extrayéndola a clases planas. Acá la lógica ya está en
el service (con tests). Lo que 4b cambia es el *cableado*. Cubrir con:

- Test de que `confirmarCambios` acumula bien éxitos y errores de un loop mixto
  (si esa acumulación se extrae a una clase plana al pasarla a `TareaUI.leer`).
- Los tests de service existentes no cambian.
- El resto es verificación manual (Fase 5).

### Verificación de fase

- `mvn test` verde.
- `grep -rn "Service\.\(guardar\|aplicarMovimientos\|lanzarLote\|finalizarLote\|marcarLoteFallo\|entregar\)" src/main/java/**/{controller,view}` → toda ocurrencia está dentro de un `TareaUI.leer(...)` o de un helper que lo envuelve.
- Arrancar con `-Daptium.edt.strict=true`, guardar un ingreso de ortopedia y uno de otros:
  **no lanza**, guarda, refresca.
- Recién con esto: retomar la **Fase 5** completa (11 puntos + 6.4) en `strict`.

### Resultado de la Fase 4b (2026-08-27)

**Ejecutada. 961 tests en verde** (eran 956; +5 nuevos). Commiteada en `14354a2`.

Los 7 sitios migrados a `TareaUI` con el patrón de `aplicarCorreccion` / `mutar`:
validación + armado del objeto + diálogos en el EDT, solo la llamada al service en
`.leer(...)`, éxito/refresco/navegación en `.pintar(...)`, `ValidationException` por
`.siFalla(...)`, botón deshabilitado en `.antes/.despues`.

- **1-2** (`IngresoOrtopediaController`, `OtrosInputController`): el `try/catch` síncrono
  desaparece; `ValidationException` → `mostrarErrorGuardado` / `mostrarErrorPersistencia`
  en `siFalla`; cualquier otra excepción → error genérico (antes se propagaba).
- **3** (`RegistrarEstadoController.confirmarCambios`): el loop entero en una `TareaUI.leer`.
  La acumulación éxitos/errores se extrajo a la clase plana
  `AplicadorMovimientosPendientes` (+ `AplicadorMovimientosPendientesTest`, 5 tests). El
  despacho al service y el armado de mensajes quedan en el controller. Buffer copiado para
  el hilo de fondo; se limpia en el EDT dentro de `finalizarConfirmacion`.
- **4-6** (`LotesController` lanzar/finalizar/marcar-fallo): helper privado local
  `ejecutarAccionDeLote`. Los diálogos y la lectura de `autoclaveSeleccionado` /
  `pendientesPorAutoclave` quedan en el EDT; a fondo va solo la llamada al service;
  `remove(...)` / refresco / listener en `pintar`. **Regla dura respetada.**
- **7** (`EquiposParaEntregarController`): loop de entregas a fondo, resultado en el record
  local `ResultadoEntregas`; mensajes/refresco/notificación en `pintar`. Botón nuevo
  `PantallaEquiposParaEntregar.setEntregarInstitucionEnabled`.

**Desvío respecto del plan — arranque de Lavadero.** Con `-Daptium.edt.strict=true` la app
no llegaba a abrir: `CiclosController` (Lavadero) leía el catálogo de jabones + 3 queries
de `cargarDatos()` **en su constructor**, sobre el EDT, durante `UiCoordinator.inicializar()`.
El plan marcó Lavadero fuera de alcance de 4b, pero esto bloqueaba la verificación entera.
Fix mínimo (decisión del usuario "extender 4b a arranque de Lavadero"): el constructor de
`CiclosController` ya no hace I/O — `abrirPantalla()` carga todo cuando el operador entra,
igual que la Fase 2. Los otros 4 controllers de Lavadero ya tenían el constructor limpio.

**Verificación:**
- `mvn test` verde (961).
- El grep de escrituras: las 7 ocurrencias están dentro de `TareaUI.leer(...)` o de un
  helper que lo envuelve. Único resto: `ClasificacionController:77` (escritura de Lavadero,
  fuera de alcance).
- Arranque con `-Daptium.edt.strict=true`: **abre sin lanzar**.
- Smoke sin `strict` (recomendado por el punto 11 por los autocompletados): guardado de
  ortopedia, otros REMITO y otros DETALLES → **ningún WARN de `EdtGuard` desde los 5
  controllers migrados**, cero `IllegalStateException`. Los 19 WARNs del log son todos de
  las excepciones documentadas en la Fase 4 (`AutocompleteListener` de
  clientes/profesionales/instituciones, `CatalogoLookup` de `OrthopediaInputController`,
  autocompletado de `catalogo_otros`).

**Hallazgo nuevo, fuera de alcance (para la rama de Lavadero):** la pantalla Ciclos sigue
con lecturas y escrituras síncronas sobre el EDT (`cargarDatos()`, `refrescarDisponiblesYCards()`,
`lanzarCiclo()`, `finalizarCiclo()`); `ClasificacionController.guardar()` idem. En `strict`,
abrir Ciclos o guardar una clasificación lanza. Es el "hallazgo Lavadero" que 4b anticipó dejar afuera.

---

## Fase 5 — Verificación manual

> **Actualizada el 2026-08-27.** La versión original se escribió antes de 4b, del hallazgo de
> Lavadero y del plan de fracciones. Esta es la checklist **única** que queda por correr en todo el
> proyecto: absorbe el smoke de GUI de las fracciones de equipo (#7), que estaba fuera de su
> blueprint.
>
> **Correr SIN `-Daptium.edt.strict=true`.** En `strict` los 5 autocompletados por tecla —excepción
> aceptada— lanzan, y sin campo de cliente no se puede ni cargar un ingreso. El modo de verificación
> es **leer los WARN de `EdtGuard` en el log**: cualquier WARN que **no** venga de uno de esos cinco
> es un camino de I/O que se escapó.
>
> Los cinco esperados: `AutocompleteListener` (clientes / profesionales / instituciones), el
> `CatalogoLookup` de `OrthopediaInputController` (×2), el de `catalogo_otros`, y el de clientes de
> `LavaderoController`. Además `VerEquiposController.abrirDetalleOtros` (lookup al doble click).

Los tests no agarran nada de esto. Checklist a correr sobre la app real:

1. **Arranque** — la app abre; ninguna pantalla queda vacía después del primer refresco.
2. **Guardar un ingreso de ortopedia** → las 6 pantallas quedan coherentes; el guardado no
   congela la UI.
3. **Guardar un ingreso de otros (REMITO y DETALLES)** → ídem.
4. **Registrar estado** de un material → avanza y se refleja en Lotes y Entregar.
5. **Lotes**: seleccionar autoclave, arrastrar material (DnD), lanzar, finalizar, marcar fallo.
   Con un refresco global disparado en el medio, el DnD sigue consistente.
6. **Correcciones**: las 10 operaciones (modificar cantidad/código, agregar/eliminar material,
   eliminar equipo, y las 4 de "otros"). Verificar el spinner de `mostrarCargando` y los mensajes
   de error (forzar uno con un código inválido).
7. **Auditoría**: abrir, filtrar, verificar el contador.
8. **Ajustes**: alta, edición, fusión de clientes.
9. **Doble guardado rápido** (dos altas seguidas) → el debounce colapsa y **no** hay parpadeo ni
   datos fuera de orden. Este es el bug que hoy existe y nadie ve.
10. **Ver Equipos**: aplicar filtros, disparar un refresco global desde otra pantalla, volver →
    los filtros del usuario siguen aplicados.
11. **Lavadero — Ingreso y Clasificación** (hallazgo #8): cargar un ingreso (el botón Guardar se
    apaga y se reenciende), clasificarlo, forzar un error de validación y ver que aparece el
    mensaje y no una excepción.
12. **Lavadero — Ciclos** (hallazgo #8): repartir elementos con DnD, lanzar un ciclo, lanzar todos,
    finalizar. Que los botones de card y los globales se apaguen mientras corre la escritura y
    vuelvan aunque falle. Salir de Ciclos y volver a entrar: **las cards tienen que estar sin
    configurar** (Paso 8.6 del plan de Salidas).
13. **Lavadero — Salidas**: marcar Listo, derivar al CDE con el cliente original y a nombre de
    APTIUM, mandar algo fuera de flujo, y mover filas entre las dos tablas con DnD. Lo derivado
    tiene que aparecer **en el acto** en las pantallas del CDE (grupo de refresco `operativo`).
14. **Fracciones de equipo** (smoke de #7, ver `fracciones-de-equipo-persistidas.md`): repartir un
    `Equipo*` de cantidad 1 entre 3 lavarropas y verificar los cuatro invariantes:
    - "Lanzar" individual **bloqueado** para las cards del grupo repartido; sólo entra por "Lanzar Todos".
    - La línea de clasificación descuenta **1** unidad, no 3.
    - El equipo **no** aparece en Salidas hasta que las 3 partes tienen ciclo finalizado.
    - Al aparecer es **una sola fila** (mostrando los 3 lavarropas), y derivarlo crea **un solo**
      elemento en el ingreso del CDE.
15. **Log limpio**: cero excepciones, y los únicos WARNs de `EdtGuard` son los del encabezado de
    esta fase. Cualquier otro sitio es un camino de I/O que se escapó.

Recién con esto en verde: `/code-review ultra` (paso 6 del plan de sesiones).

---

## Riesgos y trampas conocidas

| Riesgo | Mitigación |
|---|---|
| Estado mutable de `LotesController` escrito desde el hilo de fondo → DnD y tooltips leen basura | Regla dura: todo campo del controller se escribe **solo** en `pintar()`, en el EDT |
| Resultados fuera de orden (dos refrescos rápidos) | Token de generación en `RefrescadorPantallas`; un resultado viejo se descarta, no se pinta |
| `cancel(true)` no interrumpe un JDBC en curso | Por eso el token: la cancelación es de *aplicación*, no de *ejecución*. La query vieja termina y su resultado se tira |
| Diálogos (`JOptionPane`, `NuevoClienteDialog`, `mostrarDialogoFusion`) invocados fuera del EDT | Van en `pintar`/`siFalla` de `TareaUI`, que ya corren en el EDT |
| Pantallas vacías durante el primer refresco | Verificación manual #1; si molesta, un estado "Cargando…" en los paneles |
| 5 pantallas leyendo en paralelo agotarían el pool (max 10) | No aplica: la lectura compartida usa **un** hilo y **una** conexión por refresco |
| Regresión futura (alguien vuelve a poner I/O en el EDT) | `EdtGuard` lo grita en el log desde el primer arranque |

---

# Fase 6 — Filtrar en SQL lo que hoy se filtra en Java

**Paso separado y verificable por sí solo. No empezar hasta tener 1-5 en verde y la
verificación manual pasada.**

Las fases 1-5 sacan el costo del EDT pero **no lo eliminan**: el refresco por guardado sigue
leyendo el histórico completo, solo que en fondo. La curva sigue siendo lineal con el volumen
acumulado; lo que cambia es el modo de falla (de *congelamiento* a *retraso*). Esta fase ataca
la curva.

**Por qué va separado:** mover trabajo de hilo no cambia *qué datos ves*; cambiar el `WHERE` sí.
Son dos clases de riesgo distintas y una regresión acá es silenciosa (una pantalla que deja de
mostrar algo). Mezclarlas haría imposible saber cuál de los dos cambios rompió qué.

## Antes de escribir una línea (para la sesión que arranque en frío)

Precondición cumplida: fases 1-5 en verde, checklist manual pasada el 2026-07-23.

Dos cosas se resuelven **primero**, porque cambian el alcance de la fase:

**Ambas resueltas el 2026-07-23. No hace falta volver a preguntar:**

1. **`CDEView` va al grupo histórico** (decisión del usuario). Lee bajo demanda, con el
   histórico completo. **Pero su carga por defecto muestra los equipos NO entregados**: el
   filtro por `ENTREGADO` arranca excluido y el usuario puede sacarlo a mano. Es un default
   de la vista, **no** un `WHERE`. Que los datos lleguen completos es justamente lo que hace
   que sacar el filtro funcione. Ver si conviene reusar el patrón de
   `PantallaVerEquipos.aplicarFiltroInicial()`, que ya hace algo así.

2. **No hay bug en "Equipos para entregar".** Un equipo entregado por completo no aparece,
   y el compensador está en `AgrupadorEntregas`, un nivel más abajo de lo que decía la
   sospecha: `esEntregable(calcularEstado())` es solo una compuerta gruesa, y sí deja pasar
   `ENTREGADO`. Lo que filtra de verdad es el conteo por material —
   [`materialesDe()`](../src/main/java/com/example/features/equipos/ortopedias/controller/helpers/AgrupadorEntregas.java#L92)
   descuenta lo ya entregado y saltea el grupo si `todosEntregados()`; si no queda ningún
   material, `agrupar()` hace `continue` y el equipo nunca crea un destino.

   **Lo que la Fase 6 tiene que sacar de acá:** el predicado real de esta pantalla no es
   `esEntregable`, es *"tiene al menos un material con cantidad pendiente"*. Por suerte
   coincide exacto con el `WHERE` operativo propuesto: `calcularEstado()` es el mínimo de los
   materiales, así que da `ENTREGADO` **si y solo si** están todos entregados — el mismo
   corte que hace `todosEntregados()`. El filtro operativo es seguro para esta pantalla.
   Lo que **no** hay que hacer es escribir el `WHERE` a partir de `esEntregable`: sería
   demasiado permisivo y traería de vuelta los equipos ya entregados.

   Rama `REMITO` sin filas: no es alcanzable en la práctica.
   [`EquipoOtrosMaterialHelper`](../src/main/java/com/example/features/equipos/otros/dao/EquipoOtrosMaterialHelper.java#L22)
   crea filas de material la primera vez que el remito se mueve, así que un remito sin filas
   sigue en `NUEVO`, que no es entregable. Igual conviene un caso de test que lo fije.

## 6.1 El movimiento: partir el snapshot en dos

`DatosRefresco` tiene hoy dos clases de consumidor con necesidades opuestas:

| Grupo | Pantallas | Qué necesita | Cuándo |
|---|---|---|---|
| **Operativo** | `RegistrarEstado`, `EquiposParaEntregar`, `Lotes` | Solo la cola activa | En cada guardado |
| **Histórico** | `VerEquipos`, `VerLotes`, `CDEView` (ver 6.3) | Todo, con filtros de fecha/estado del usuario | Solo cuando el usuario las mira |

El arreglo no es agregarle un `WHERE` a la consulta compartida — eso rompería al grupo
histórico. Es **partirla**:

- `DatosOperativos` — equipos y otros **no ENTREGADO**, más autoclaves/volúmenes/lotes activos.
  Es lo único que dispara el refresco global. **El guardado deja de tocar el histórico.**
- Las pantallas históricas **se bajan del refresco global** y leen bajo demanda: en
  `componentShown` y cuando cambian sus propios filtros. Que es exactamente cuando el usuario
  las está mirando — hoy se recargan en cada guardado aunque estén ocultas.

Ese segundo punto solo es posible porque la Fase 2 ya centralizó las lecturas: hoy están
enredadas en 6 `cargarDatos()` distintos.

## 6.2 La traducción a SQL

`calcularEstado()` es el estado **mínimo** de los materiales
([`Equipo.java:112`](../src/main/java/com/example/features/equipos/ortopedias/model/Equipo.java#L112)),
no una columna. Así que el `WHERE` no puede ser `e.estado <> 'ENTREGADO'`. "No entregado" se
traduce a *"tiene al menos un material sin entregar"*:

```sql
EXISTS (SELECT 1 FROM equipo_materiales em2
        WHERE em2.equipo_id = e.id AND em2.estado <> 'ENTREGADO')
OR NOT EXISTS (SELECT 1 FROM equipo_materiales em3 WHERE em3.equipo_id = e.id)
```

El segundo término no es opcional: un equipo sin materiales calcula `NUEVO`, no `ENTREGADO`.

Para `EquipoOtros` hay una rama más: si es `REMITO` sin filas reales, o no tiene materiales,
el estado vive en la columna `e.estado`
([`EquipoOtros.java:79-82`](../src/main/java/com/example/features/equipos/otros/model/EquipoOtros.java#L79)).

**Test primero, y es barato:** con H2 en memoria, para cada equipo del set de prueba
`dao.obtenerActivos()` debe devolver exactamente los mismos ids que
`dao.obtenerTodos().stream().filter(e -> e.calcularEstado() != ENTREGADO)`. Un test de
equivalencia contra la implementación vieja, con casos borde: equipo sin materiales, equipo
mixto (un material entregado y otro no), REMITO sin filas, REMITO con filas.

## 6.3 Tres trampas verificadas en el código

1. **`CDEView` deja filtrar por `ENTREGADO`.** El combo se llena con `EstadoEquipo.values()`
   **completo** ([`PantallaVerCDEv2.java:80-83`](../src/main/java/com/example/features/equipos/ortopedias/view/PantallaVerCDEv2.java#L80)),
   así que incluye `ENTREGADO`. Si esa pantalla pasa a comer del snapshot operativo, ese filtro
   queda vacío para siempre — regresión silenciosa, nadie la ve hasta que un usuario la busca.
   **Por eso la puse en el grupo histórico**, no en el operativo: es una pantalla de consulta.
   Confirmar con el usuario antes de ejecutar.

2. **`esEntregable` incluye `ENTREGADO`.** Es `orden >= ESTERILIZADO`
   ([`EstadoValidatorImpl.java:43`](../src/main/java/com/example/features/equipos/ortopedias/service/EstadoValidatorImpl.java#L43)),
   y `ESTERILIZADO` < `ENTREGADO`, así que un material ya entregado **pasa el filtro** de
   `EquiposParaEntregarController`. O bien hay algo que lo compensa aguas arriba, o los equipos
   ya entregados siguen apareciendo en "Equipos para entregar". **Verificar eso primero, contra
   la app real.** Si es un bug, se arregla como bug aparte y con su propio test; lo que **no**
   hay que hacer es congelarlo dentro de un `WHERE` nuevo y que quede enterrado.

3. **El `WHERE` por estado que ya existe no sirve.** `EquipoDAO` tiene un
   `WHERE e.estado = ?` (línea 401), pero es sobre la **columna** `estado` del equipo, que para
   ortopedias no es el estado calculado. Usarlo por parecido daría resultados sutilmente
   distintos.

## Resultado de la Fase 6 (2026-07-23)

**Ejecutada. 577 tests en verde** (eran 574 al terminar 1-5; +22 nuevos contando los
que reemplazaron a los borrados). Tres commits: `71d6cfa`, `8143d24` y el de la medición.

**Medición (lo que pedía 6.4).** Con 3 ingresos activos y 25 entregados:

| | Sentencias JDBC por guardado |
|---|---|
| Antes de la Fase 6 | **34** |
| Después | **8** |
| Después, con **cero** entregados | **8** |

La última fila es la que importa: el costo de un guardado dejó de depender del volumen
acumulado. Está fijado como test (`CostoDelRefrescoTest`), no anotado acá, porque un
número en un documento se desactualiza y la propiedad no.

Lo que sigue creciendo es el N+1 de `equipo_otros` (una query de materiales por ingreso),
pero ahora acotado a la cola activa. Bajarlo a una sola query con JOIN, como ya hace
`EquipoDAO`, es el siguiente escalón y no hizo falta para esta fase.

**Piezas nuevas:** `DatosOperativos` / `LectorDatosOperativos`, `HistorialEquipos` /
`LectorHistorialEquipos`, `HistorialLotes` / `LectorHistorialLotes`.
`DatosRefresco` y `LectorDatosRefresco` se eliminaron.
`RefrescadorPantallas` pasó a ser genérico sobre el tipo de snapshot: un solo mecanismo
(debounce + token de generación + `TareaUI`), tres instancias con disparadores distintos.

**Desvíos respecto del plan:**

1. **El grupo histórico se partió en dos**, no en uno. `VerLotes` no necesita nada de lo
   que necesitan `VerEquipos` y `CDEView`; con un solo record, abrir "Ver Lotes" habría
   seguido leyendo el histórico entero de equipos — justo lo que la fase venía a evitar.

2. **Las pantallas históricas leen en `componentShown` y nada más.** El plan decía
   "y cuando cambian sus propios filtros", pero sus filtros siguen siendo de Java sobre la
   lista ya cargada: releer al filtrar sería trabajo extra sin ningún cambio de resultado.
   Recién aplicaría si los filtros bajaran al `WHERE`, que es el escalón de paginar.

3. **`RegistrarEstadoController` perdió su filtro Java por `ENTREGADO`.** Con el `WHERE`
   nuevo es redundante, y dos definiciones de "activo" conviviendo son exactamente como se
   desincronizan sin que nadie lo note. Los tests de equivalencia son el guard.

4. **`AgrupadorEntregas` no se tocó**, pero se le agregaron dos tests que fijan de qué
   depende: un REMITO entregado sin filas reales **sí** genera fila si le llega, así que
   quien lo excluye es la consulta. Si alguien vuelve a alimentar esa pantalla con el
   histórico, el test explica por qué reaparecen los entregados.

**Pendiente:** la checklist manual de la Fase 5, completa (6.4 la exige de nuevo: es la
misma superficie), más la comprobación específica de entregar un equipo por completo.

## 6.4 Verificación de la fase

- Los tests de equivalencia de 6.2 en verde (es el corazón: garantizan que el `WHERE` nuevo
  selecciona exactamente lo mismo que el filtro Java viejo).
- Volver a correr la checklist manual de la Fase 5 completa — es la misma superficie.
- Comprobación específica: entregar un equipo por completo y verificar que desaparece de las
  pantallas operativas **y que sigue apareciendo** en `VerEquipos` filtrando por `ENTREGADO`.
- Medir: contar queries y filas leídas por guardado, antes y después. Si el número no baja de
  forma clara, la fase no valió la pena y conviene revertirla.

## Fuera de alcance (anotar, no ejecutar)

- **Paginar** las pantallas históricas. La Fase 6 saca el histórico del *camino del guardado*,
  pero `VerEquipos` abierta sigue leyendo todo. Es el siguiente escalón, y recién hace falta
  cuando el volumen lo pida. Ahí también bajarían los filtros al `WHERE`.
- **El N+1 de `EquipoOtrosDAO`**: una query de materiales por ingreso, contra la única query
  con JOIN que ya usa `EquipoDAO`. La Fase 6 lo acotó a la cola activa; eliminarlo es un
  cambio aparte y de riesgo distinto (toca el mapeo, no el filtro).
- Constructor muerto de `LotesController` con `equipoContexto` → refactor-clean.
- Migrar los 2 `SwingWorker` de reportes a `TareaUI` — opcional, decidir en la Fase 4.

---

# Hallazgo derivado — Lavadero (Ciclos + Clasificación) todavía fuera del modelo EDT

**Descubierto el 2026-08-27**, durante la verificación de la Fase 4b. La feature Lavadero es
posterior a las Fases 1-6 y de otra rama; nunca pasó por el inventario de #6. `TareaUI` y
`EdtGuard` ya existían cuando se escribió, pero dos de sus controllers no los usan.

**Es un hallazgo aparte, no una fase de este plan.** Se resuelve en su propia sesión (ver
recomendación de modelo/prompt más abajo, o donde quede registrada). No bloquea la Fase 5:
el smoke de 4b se corre sin `strict` y las pantallas afectadas no están en el camino de
ortopedia/otros.

## Estado medido (2026-08-27, sobre `ConexionConCDE` tras el commit de 4b)

| Controller | Qué hace en el EDT | Ya migrado |
|---|---|---|
| `SalidasLavaderoController` | — | ✅ **es la implementación de referencia**: record `DatosSalidas` + `.leer()` para lecturas, helper privado `ejecutar(String, Runnable, Runnable)` para escrituras |
| `VerCiclosController` | — | ✅ patrón Fase 2 (`componentShown` → `solicitarRefresco`) |
| `LavaderoController` | `clienteService.buscarClientes` en `AutocompleteListener` ([:46](../src/main/java/com/example/features/lavadero/controller/LavaderoController.java#L46)) | excepción documentada (autocompletado por tecla — misma clase que los 4 de la Fase 4) |
| **`CiclosController`** | **lecturas:** `cargarDatos()` ([:152](../src/main/java/com/example/features/lavadero/controller/CiclosController.java#L152), `:154`, `:157`), `actualizarTodasLasCards()` (`:177`, una por ciclo activo), `refrescarDisponiblesYCards()` (`:379`), `cargarJabonesUnaVez()` (`:146`). **escrituras:** `resolverInstancias()` → `crearInstanciaEquipo` (`:453`), `ejecutarLanzamiento()` → `lanzarCiclo` (`:495`), `ejecutarFinalizacion()` → `finalizarCiclo` (`:543`) | ❌ nada |
| **`ClasificacionController`** | **lectura:** `cargarIngresosSinClasificar()` ([:46-47](../src/main/java/com/example/features/lavadero/controller/ClasificacionController.java#L46)). **escritura:** `guardar()` → `clasificacionLavaderoService.guardar` (`:77`, hoy con `try/catch` síncrono) | ❌ nada |

En `-Daptium.edt.strict=true`: abrir la pantalla Ciclos, abrir Clasificación, lanzar/finalizar
un ciclo o guardar una clasificación **lanzan** `IllegalStateException`. En modo normal son WARNs
del `EdtGuard`.

**Nota sobre `CiclosController` en 4b:** su constructor ya se sacó del EDT (I/O movido a
`abrirPantalla()`), lo mínimo para que la app arranque en `strict`. El resto — que
`abrirPantalla()`/`cargarDatos()` y las 3 escrituras corran en fondo — es este hallazgo.

## Alcance del arreglo

1. **`CiclosController` — lecturas.** `cargarDatos()` + `actualizarTodasLasCards()` +
   `refrescarDisponiblesYCards()` + jabones a **una** `TareaUI.leer` que devuelva un record
   (ej. `DatosCiclos`), y un `pintar()` puro que vuelque a las cards. Ojo con `actualizarTodasLasCards()`,
   que hoy interleava `obtenerElementosDeCiclo(id)` por cada ciclo activo: esas lecturas van
   dentro del mismo `leer`. La lógica pura de armado (`staging.aplicarSobreDisponibles`, el mapeo
   a `LavarropasItem`, la decisión activo/staging por card) es candidata a clase plana + test,
   como `AgrupadorEntregas` / `ConstructorMaterialesDisponibles`.
2. **`CiclosController` — escrituras.** `lanzarCiclo()` / `lanzarTodos()` / `finalizarCiclo()` /
   `finalizarTodos()`: los diálogos de confirmación y la lectura del staging quedan en el EDT;
   `resolverInstancias` + `ejecutarLanzamiento` (o `ejecutarFinalizacion`) van a `.leer`. Helper
   privado local al estilo `SalidasLavaderoController.ejecutar(...)` — **no** uno compartido.
   Cuidado con `lanzarTodos()`: hoy es un loop de `ejecutarLanzamiento` seguido de `cargarDatos()`;
   el loop entero va en una sola tarea, como se hizo con `RegistrarEstadoController.confirmarCambios`
   en 4b (ver `AplicadorMovimientosPendientes`).
3. **`ClasificacionController`.** `cargarIngresosSinClasificar()` → `.leer`; `guardar()` → patrón
   de 4b (`aplicarCorreccion`): validación de formulario en EDT, `clasificacionLavaderoService.guardar`
   en `.leer`, `ValidationException` por `.siFalla`, navegación/éxito en `.pintar`, botón
   deshabilitado en `.antes/.despues`. El `try/catch` síncrono desaparece.
4. **Regla dura** (igual que Lotes): el estado mutable de `CiclosController` (`staging`,
   `ciclosActivos`, `elementosDisponibles`, `lavarropasItems`, `nextInstanciaId`, `lavarropasArrastre`)
   se lee y escribe **solo en el EDT**. El DnD y los diálogos lo tocan; nada de eso puede
   moverse al hilo de fondo.
5. **Fuera de alcance de este hallazgo:** `LavaderoController` autocompletado (excepción
   documentada), y el `AtomicInteger nextInstanciaId` en memoria que ya está anotado como
   defecto aparte en `hallazgos-arquitectura-pendientes.md`.

## Verificación

- `mvn test` verde (los tests de `CicloLavaderoService` / `ClasificacionLavaderoService` **no se tocan**).
- `grep -rn "Service\.\(obtener\|guardar\|lanzar\|finalizar\|crear\)" src/main/java/**/lavadero/controller` → toda ocurrencia dentro de `TareaUI.leer(...)` o de un helper que lo envuelve (salvo el autocompletado de `LavaderoController`).
- Arrancar con `-Daptium.edt.strict=true`, abrir Lavadero → Clasificación (clasificar un ingreso), Lavadero → Ciclos (repartir con DnD, lanzar un ciclo, finalizarlo): **no lanza**.
- Con eso, la checklist de la Fase 5 se puede correr entera en `strict` incluyendo Lavadero.

## Resultado (2026-08-27)

**Cerrado. 970 tests en verde** (eran 961; +9 nuevos). Smoke manual pasado el 2026-08-27:
clasificar un ingreso, y en Ciclos repartir con DnD, lanzar y finalizar — sin WARNs de
`EdtGuard` desde ninguno de los dos controllers. Commiteado en `95c9e33`.

- **`CiclosController` — lecturas.** `cargarDatos()`, `actualizarTodasLasCards()`,
  `refrescarDisponiblesYCards()` y `cargarJabonesUnaVez()` colapsaron en un único
  `recargar()`: una `TareaUI.leer` que devuelve el record `DatosCiclos` (ciclos activos,
  disponibles, lavarropas, los elementos de cada ciclo activo y —sólo la primera vez— el
  catálogo de jabones) y un `pintar()` puro. `refrescarDisponiblesYCards()` desapareció:
  hacía lo mismo que `cargarDatos()` menos una query, y mantener dos caminos de lectura era
  la mitad del problema.
- **Clase plana + test.** `ConstructorVistaCiclos` (+ `ConstructorVistaCiclosTest`, 9 tests)
  toma `DatosCiclos` + `StagingCiclos` y devuelve `VistaCiclos` (disponibles ya descontados,
  `LavarropasItem`s, una `VistaCard` por card, y los dos flags de los botones globales).
  **Corre en el EDT**, no en fondo: toca el staging.
- **`CiclosController` — escrituras.** Helper privado local `ejecutar(nombre, escritura,
  alTerminar)`. `lanzarCiclo`/`lanzarTodos` entran los dos por `lanzar(List<Integer>)` y
  `finalizarCiclo`/`finalizarTodos` por `finalizar(List<Integer>)`: la tanda entera va en
  **una** tarea, como `confirmarCambios` en 4b. Diálogos, validación de config de cada card y
  lectura del staging quedan en el EDT y se congelan en los records `LanzamientoPendiente` /
  `LineaLanzamiento` — que todavía llevan el `instanciaId` **de staging**; el de la base lo
  asigna `crearInstancias()` ya en fondo. `ResultadoEscritura(exitosos, fallidos)` vuelve al
  EDT, donde recién ahí se hace `staging.limpiarLavarropas(...)` / `resetConfiguracion()`.
  **Regla dura respetada:** ningún campo del controller se toca fuera del EDT.
- **Anti doble-lanzamiento.** Antes era imposible por ser síncrono. Ahora los tres botones
  globales y los de cada card se apagan en `.antes` (nuevo `LavarropasCard.deshabilitarAccion()`)
  y el refresco va en `.despues` —no en `pintar`— para que también los reencienda si falló.
- **Orden de pintado.** `recargar()` cancela la carga anterior en vuelo: dos arrastres
  seguidos no pueden pintar fuera de orden.
- **`ClasificacionController`.** `cargarIngresosSinClasificar()` → `.leer` con el record
  `DatosClasificacion`; `guardar()` al patrón de 4b. El `try/catch` síncrono desapareció:
  `ValidationException` → `siFalla`, cualquier otra excepción → error genérico (antes se
  propagaba), botón Guardar apagado en `.antes/.despues`.
- Se corrigió el javadoc de `SalidasLavaderoController`, que citaba a `CiclosController` como
  deuda: ya no lo es.

**Verificación hecha:**
- `mvn test` verde (970).
- El grep de la sección: las 13 ocurrencias en `**/lavadero/controller` están todas dentro de
  un `TareaUI.leer(...)` o de un helper que lo envuelve. Cero excepciones.

**Verificación pendiente:** el smoke manual de Clasificación y de Ciclos (ver más abajo).

### Hallazgo derivado — `LavaderoController.guardar()` escribía en el EDT ✅ HECHO (2026-08-27)

La tabla de "Estado medido" de arriba dejó pasar uno: `LavaderoController.guardar()` llamaba a
`lavaderoService.registrarIngreso` en forma síncrona sobre el EDT. Apareció en el log del smoke
(WARN de `EdtGuard` con `TransactionalConnection.begin` ← `IngresoLavaderoDAO.guardar` ←
`LavaderoService.registrarIngreso`) — no es el autocompletado, es una escritura de la misma
clase que las 7 de la Fase 4b.

**Arreglado con el mismo patrón**, a pedido del usuario apenas cerró el smoke de #8: validación
y armado del `IngresoLavadero` en el EDT, `registrarIngreso` en `.leer`, `ValidationException`
por `.siFalla` (sigue usando `mostrarAdvertencia` y el fallback "Error de validación." de
antes), cualquier otra excepción → error genérico, éxito/limpieza/navegación/refresco en
`.pintar`, botón Guardar apagado en `.antes/.despues`. El `try/catch` síncrono desapareció.
Se documentó en el javadoc de la clase que el autocompletado de clientes queda como excepción
aceptada.

### Nota de método: el smoke de Lavadero no se puede correr en `strict`

Con `-Daptium.edt.strict=true` los autocompletados documentados **lanzan** en vez de avisar,
así que los campos de cliente de Ingreso de Lavadero y del CDE quedan inutilizables y no se
puede ni cargar el ingreso que después habría que clasificar. Es el punto 11 de la Fase 5
llevado a la práctica: el smoke de esta feature se corre **sin** `strict` y se verifica
leyendo los WARNs de `EdtGuard` en el log, igual que hizo 4b.
