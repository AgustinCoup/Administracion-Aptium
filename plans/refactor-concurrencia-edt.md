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

**Fases 1, 2, 3 y 4: hechas.** 11 commits en `UXhotfix`, de `110d7c3` a `0064570`.
555 tests en verde (eran 521; +34 nuevos).

**Fase 5 (verificación manual): pendiente** — requiere la app con BD, no se puede
correr desde la sesión. La checklist está más abajo, sin tachar.

Piezas nuevas: `EdtGuard`, `TareaUI`, `DatosRefresco`, `LectorDatosRefresco`,
`RefrescadorPantallas`, `AgrupadorEntregas`, `ConstructorMaterialesDisponibles`,
`FiltroAuditorias`. Los 6 controllers pasaron de `cargarDatos()` a `pintar(DatosRefresco)`.

**Desvío respecto del plan:** el constructor muerto de `LotesController` con
`equipoContexto` se **eliminó** en vez de anotarse para el refactor-clean. Sostenerlo
obligaba a una segunda rama en el camino de pintado y bloqueaba la extracción de
`ConstructorMaterialesDisponibles`. Confirmado sin llamadores antes de borrarlo.

**Pendiente de verificar contra la app real** (no se pudo en la sesión, requiere BD):
el arranque, y la lista de WARNs que quedan del `EdtGuard`.

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

## Fase 5 — Verificación manual

Los tests no agarran nada de esto. Checklist a correr sobre la app real, con el
`EdtGuard` en modo `strict` (`-Daptium.edt.strict=true`):

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
11. **Log limpio**: cero excepciones, y los únicos WARNs de `EdtGuard` son los cuatro
    autocompletados documentados en la Fase 4 (`OrthopediaInputController` ×2,
    `VerEquiposController.abrirDetalleOtros`, `AutocompleteListener`). Cualquier otro
    sitio en la lista es un camino de lectura que se escapó del refresco global.
    Ojo con `strict`: si está activo, esos cuatro **lanzan** en vez de avisar, así que
    conviene correr la checklist sin `strict` y revisar el log, o aceptar que el
    autocompletado no funcione durante la corrida con `strict`.

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
  cuando el volumen lo pida.
- Constructor muerto de `LotesController` con `equipoContexto` → refactor-clean.
- Migrar los 2 `SwingWorker` de reportes a `TareaUI` — opcional, decidir en la Fase 4.
