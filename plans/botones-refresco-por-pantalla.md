# Botones de refresco por pantalla

**Objetivo:** que el operador pueda pedir explícitamente una relectura de la base desde cada
pantalla que muestra datos, sin salir y volver a entrar.

**Estado:** planificado, sin empezar. Revisado adversarialmente (2026-09-07); los hallazgos
están incorporados y los que cambiaron el diseño quedan marcados abajo.
**Rama sugerida:** `BotonesRefresco` (desde `main`).
**Modo:** commits directos sobre la rama; no hay `gh` CLI instalado en esta máquina, así que el
plan no asume PRs ni CI remoto. Verificación = `mvn test` local + smoke manual.

---

## Por qué

Hoy cada pantalla se relee **sólo al entrar**. Si el operador se queda mirando Lotes veinte
minutos mientras otro registra ingresos desde la otra PC, lo que ve está viejo y no tiene forma
de saberlo ni de arreglarlo salvo salir y volver a entrar. Con dos puestos contra la misma base
(ver §Concurrencia en `CLAUDE.md`) eso no es un detalle: es la diferencia entre ver el conflicto
antes de armar el lote o después de que la guarda lo rechace.

## Qué NO es

**No es un botón global.** Un único botón tendría que adivinar qué card está visible para
disparar el grupo correcto, o releer los cinco grupos en cada click — que es exactamente lo que
se sacó a propósito cuando se separaron los disparadores (`UiCoordinator:58-70`). Un botón por
pantalla dispara el runnable de **esa** pantalla.

**No es un camino de lectura nuevo.** El botón no lee datos: invoca la misma función de carga
que ya existe. Si un paso te tienta a escribir una query nueva, el paso está mal.

> ⚠️ **Corrección de la revisión.** La versión anterior de este plan decía que el botón invoca
> "el mismo runnable que hoy invoca el `componentShown`". **Eso es falso en dos pantallas** y era
> el error más peligroso del plan, porque sonaba a garantía:
> - **Registrar Estado** (`RegistrarEstadoController:75-80`): con cambios pendientes el
>   `componentShown` **no relee** — hace `resetearCambios()` y repinta local.
> - **Clasificación**: su método de carga **destruye el formulario** que el operador está
>   llenando (ver C1 abajo).
>
> La formulación correcta es: **el botón reusa la función de carga que ya existe, y cada pantalla
> define explícitamente qué le pasa a su trabajo en curso.** Eso es lo que resuelven los pasos.

## Decisiones tomadas (2026-09-07, con el usuario)

| Decisión | Elegido | Descartado |
|---|---|---|
| Alcance | Las 11 pantallas con datos de BD | Sólo las de consulta / sumar Correcciones y Auditoría |
| Con trabajo sin guardar | **Pide confirmación** antes de refrescar | Refrescar igual / deshabilitar el botón |
| Extras | F5 **y** cartelito "Actualizado hh:mm" | Sólo el botón |

---

## El mapa: qué dispara cada pantalla y qué pasa con lo pendiente

Es el corazón del plan. La última columna **no es documentación, es el texto del cartel**: cada
mensaje de confirmación tiene que decir la verdad de su pantalla. Un cartel que miente entrena al
operador a apretar "Sí" sin leer, y desactiva también los avisos verdaderos — el mismo argumento
que `CLAUDE.md` usa para no poner `version` en las tablas de detalle.

| # | Pantalla | Qué dispara el botón | Guard | Qué le pasa a lo pendiente |
|---|---|---|---|---|
| 1 | Registrar Estado | **descartar + releer** (ver paso 4) | **Sí** | Los movimientos armados **se descartan** |
| 2 | Equipos para Entregar | disparador `operativo` | No | — |
| 3 | Lotes | disparador `operativo` | **Sí** | Lo arrastrado **se conserva**; puede dejar de estar disponible en la base |
| 4 | Estado de Procesos | disparador `historialEquipos` | No | — |
| 5 | Ver Equipos | disparador `historialEquipos` | No | — |
| 6 | Ver Lotes | disparador `historialLotes` | No | — |
| 7 | Ver Ciclos | disparador `historialCiclos` | No | — |
| 8 | Historial Lavadero | disparador `historialLavadero` | No | — |
| 9 | Clasificación | `cargarIngresosSinClasificar()` | **Sí** | Los elementos cargados **se descartan** |
| 10 | Ciclos | `recargar()` ⚠️ **no** `abrirPantalla()` | **Sí** | La config tipeada **se conserva**; se rehacen disponibles y ocupados |
| 11 | Salidas | `cargarDatos()` | No | — |

**Nota de implementación:** `Disparador` es la clase anidada privada de `UiCoordinator:326-339` y
su `solicitar()` es package-private. Los controllers lo reciben como `Runnable` y hacen `run()`;
la notación `operativo::solicitar` sólo es válida dentro de `UiCoordinator`.

**Quedan afuera** (y por qué): los menús no muestran datos; los formularios de ingreso no listan
nada que envejezca; `PantallaCorrecciones` y `PantallaAuditoria` cargan por búsqueda explícita,
que ya es un refresco a pedido; `PantallaAjustes` es ABM. (`PantallaVerCDEv1` existe pero no está
cableada en `UiCoordinator`: es pantalla muerta, por eso no está en el mapa.)

## Hallazgos de la revisión que cambiaron el diseño

**C1 — Clasificación necesita guard y no lo parecía.** `PantallaClasificacionLavadero:84-95`
(`refrescar`) hace `centerPanel.remove(panelElementos)` y construye un `PanelElementosClasificacion`
nuevo: **destruye el formulario que el operador está llenando**. Hoy no se nota porque
`cargarIngresosSinClasificar()` sólo corre desde el listener del menú, con el formulario vacío por
definición. **El botón y F5 vuelven ese camino alcanzable con el formulario lleno.**

**La regla tentadora es falsa.** "Una pantalla sin guard de Volver no necesita guard de refresco"
suena bien y es mentira: Clasificación no tiene guard de Volver y sí tiene qué perder. La regla
correcta, que va a `CLAUDE.md` en el paso 6: **toda pantalla que acumule estado en memoria entre
lecturas necesita guard; el guard de Volver es una pista de dónde buscarlo, no la lista.**

**Registrar Estado deja un buffer zombi.** Verificado: `pintar → repintar → actualizarEquipos`
llega a `fireTableDataChanged()` + `modeloMateriales.limpiar()`. `cambiosPendientes` **sobrevive**,
pero el preview visual **no** (se aplicó con `aplicarMovimientoPreview` sobre los objetos del
snapshot viejo), la selección se pierde, y ni el contador ni los botones Confirmar/Cancelar se
resincronizan. Queda "3 cambios pendientes" en pantalla, Confirmar encendido, y nada visible.
Confirmar ahí aplica tres movimientos que el operador ya no puede ver — y como la guarda de esa
pantalla es CAS sobre `estado` del material, si nadie más los tocó **pasan en silencio**. Por eso
la fila 1 del mapa es "descartar + releer" y no "releer": es la semántica que la pantalla ya
eligió para el `componentShown`, extendida con la relectura que hoy le falta.

**El timestamp no va en `UiCoordinator`.** Tres de los cuatro `crearRefrescadorHistorial*` pasan
una method reference pelada (`verLotes::pintar`, `verCiclos::pintar`, `historial::pintar`), no un
bloque donde meter nada. **`marcarActualizado()` va dentro del `pintar()` de cada controller** —
un lugar por pantalla, uniforme para las 8 de `RefrescadorPantallas` y las 3 de Lavadero.
Consecuencia buena: **ningún paso de cableado toca `UiCoordinator`**, así que los pasos 2 a 5 son
realmente paralelos.

**Dos controllers no tienen cancelación.** `ClasificacionController.cargarIngresosSinClasificar()`
y `SalidasLavaderoController.cargarDatos()` no pasan por `RefrescadorPantallas` (sin debounce) y
no guardan la `TareaUI.Ejecucion` en vuelo (sin cancelación). Hoy no importa: corren una vez por
click de menú. Con F5 mantenido apretado son N lecturas concurrentes donde gana la que **termine**
última, no la última lanzada. Hay que agregarles cancelación **antes** de exponer el atajo.

**El header no es campo en 8 de las 11 vistas.** Sólo `PantallaLotes:27`,
`PantallaRegistrarEstado:28` y `PantallaCiclos:22` lo guardan — justo las tres que ya usan
`setGuardVolver`. Las otras ocho lo declaran local en el constructor. Promoverlo es mecánico pero
hay que contarlo como trabajo.

## Invariantes (se verifican después de CADA paso)

1. `mvn test` pasa completo.
2. **Ningún acceso a BD en el EDT.** El botón invoca una función que ya pasa por `TareaUI` o por
   un `Disparador`. Ninguna llamada a DAO/Service desde el `ActionListener`.
3. **Cero rutas de lectura nuevas.** Aumenta la cantidad de lugares que *invocan* una carga, no la
   cantidad de lugares que *leen*.
4. Las pantallas que ya declaran `setGuardVolver` siguen bloqueando la navegación igual que antes.
5. El botón es opt-in: una pantalla que no lo pide se ve exactamente igual que hoy.

---

# Paso 1 — Infraestructura: `GuardaRefresco` + `PanelHeader` + F5

**Depende de:** nada. **Habilita:** pasos 2, 3, 4 y 5 (que son paralelos entre sí).
**Modelo sugerido:** el más fuerte disponible — define la API que consumen los otros cuatro.

## Contexto para arrancar en frío

`PanelHeader` (`src/main/java/com/example/ui/common/PanelHeader.java`) es el header estándar: un
botón "Volver" a la izquierda, título centrado, estilo corporativo. Lo instancian 19 pantallas.
Ya tiene un guard de navegación: `setGuardNavegacion(Supplier<Boolean>, String, Runnable)`
reemplaza los listeners del botón Volver por uno que pregunta antes de navegar.

`Hotkeys` (`src/main/java/com/example/ui/common/Hotkeys.java`) es donde viven los atajos.
`registrarVolver` bindea ESC con `WHEN_IN_FOCUSED_WINDOW` y dispara `doClick()`, así respeta
cualquier guard puesto encima. F5 calca ese patrón.

**Por qué F5 en 19 headers no colisiona** (dejalo escrito en el javadoc, es lo primero que
alguien va a "arreglar"): los bindings `WHEN_IN_FOCUSED_WINDOW` sólo disparan en componentes
*showing*, y el `CardLayout` deja invisibles todas las cards menos la actual. Es exactamente por
eso que ESC ya funciona hoy en los 19 headers sin pisarse.

## Tareas

1. **Clase plana `GuardaRefresco`** en `src/main/java/com/example/ui/common/`, sin Swing:
   ```java
   GuardaRefresco(Supplier<Boolean> hayPendientes, String mensaje,
                  Runnable onDescartarConfirmado, Predicate<String> confirmador)
   boolean debeRefrescar()   // sin guard → true; con pendientes → consulta al confirmador
   ```
   Toda la lógica de decisión vive acá. Es la convención del repo para lógica embebida en Swing
   (`AgrupadorIngresosLote`, `DuplicadoHighlighter`) y es lo único que hace testeable el paso:
   los tests corren headless y `JOptionPane.showConfirmDialog` tira `HeadlessException`.
   El tercer parámetro (`onDescartarConfirmado`) **no es opcional**: dos de las tres pantallas con
   guard necesitan descartar al confirmar, no sólo seguir.
2. En `PanelHeader`, botón "Actualizar" **oculto por defecto**, en la fila del botón Volver.
   Respetar `aplicarEstiloCorporativo`: sin relleno, sin borde, texto blanco, cursor de mano.
3. `public void setAccionRefrescar(Runnable accion)` — cablea el listener, hace visible el botón y
   registra F5. ⚠️ **No copies el early-return de `setGuardNavegacion:118-120`**
   (`if (navegador == null) return;`): es correcto para navegación y sería un bug silencioso acá,
   porque el refresco no tiene nada que ver con el `CardLayout`. El método de al lado invita a
   copiarlo.
4. `public void setGuardRefresco(Supplier<Boolean> hayPendientes, String mensaje, Runnable onDescartar)`
   — arma la `GuardaRefresco` con un confirmador que llama a `JOptionPane.showConfirmDialog`
   (`TITULO_CAMBIOS_SIN_CONFIRMAR`, `YES_NO_OPTION`, `WARNING_MESSAGE`). Guardá la guarda en un
   campo que el listener consulta; **no** reconstruyas el listener ni borres los existentes como
   hace `setGuardNavegacion:122-125`.
5. `public void marcarActualizado()` — label a `"Actualizado " + HH:mm`, oculto hasta la primera
   llamada. `DateTimeFormatter` como constante estática.
6. `Hotkeys.registrarRefrescar(JButton)` calcado de `registrarVolver`.
7. Textos a `Constantes` (`Botones` y `Mensajes`). Los mensajes por pantalla salen en sus pasos.

## Verificación

- `mvn test`
- **`GuardaRefrescoTest`** (el test que importa, sin una línea de Swing): sin guard → refresca;
  guard `true` + confirmador que dice que no → no refresca y no corre el descarte; guard `true` +
  confirmador que dice que sí → refresca y corre el descarte una vez; guard `false` → refresca y
  **el confirmador nunca se invoca**.
- `PanelHeaderTest` sólo para lo que corre headless (visibilidad del botón, que `setAccionRefrescar`
  lo muestre, formato del label), con reflexión al estilo `LavarropasCardTest:84-85`. **Ningún test
  abre un `JOptionPane` real.**

## Criterio de salida

La API existe, `GuardaRefrescoTest` cubre las cuatro ramas, ninguna pantalla la usa, la app se ve
idéntica.

---

# Paso 2 — Las 5 pantallas de consulta

**Depende de:** paso 1. **Paralelo con:** pasos 3, 4 y 5 (archivos disjuntos).

## Contexto para arrancar en frío

Filas 4 a 8 del mapa: Estado de Procesos, Ver Equipos, Ver Lotes, Ver Ciclos, Historial Lavadero.
Sólo lectura, sin estado acumulado: van sin guard. Es el caso fácil a propósito — valida la infra
del paso 1 de punta a punta.

Cada controller ya recibe su `Disparador` como `Runnable` y lo usa en su `componentShown`
(`EstadoProcesosController:45`, `VerEquiposController:72`, `VerLotesController:43`,
`VerCiclosController:29`, `HistorialLavaderoController:54`). El botón usa **ese mismo campo**.

## Tareas

1. En las cinco vistas: promover el `PanelHeader` de variable local a campo y exponer
   `setAccionRefrescar(Runnable)` delegando en él (patrón de `PantallaLotes:147`).
2. En los cinco controllers: cablear el botón al mismo runnable del `componentShown`.
3. Agregar `pantalla.marcarActualizado()` **al final del `pintar()` de cada controller**. Ahí es
   donde el repintado efectivamente ocurre. **No** marques en el click: hay debounce de 150 ms y
   la lectura puede fallar.
   *(Que dos pantallas del mismo grupo se pinten juntas estando una oculta no es un problema: el
   repintado ocurrió en las dos y el snapshot es compartido y coherente por diseño.)*

## Verificación

- `mvn test`; log sin WARNs de `EdtGuard`.
- Smoke: abrir cada una, click en Actualizar, el timestamp cambia. Con la app abierta en dos PCs,
  modificar en una y refrescar en la otra.

## Criterio de salida

Las cinco de consulta refrescan a pedido y muestran la hora del último pintado.

---

# Paso 3 — Lotes y Equipos para Entregar

**Depende de:** paso 1. **Paralelo con:** 2, 4 y 5.

## Contexto para arrancar en frío

Filas 2 y 3. Comparten el `Disparador operativo` con Registrar Estado, pero el cableado es por
vista y controller, así que no se pisan con el paso 4.

**Lotes es la única pantalla del plan donde lo pendiente sobrevive de verdad**, y está verificado:
`repintar()` reconstruye los disponibles descontando el staging
(`LotesController:191-195`, `constructorDisponibles.construir(..., pendientesPorAutoclave)`),
`pendientesPorAutoclave` no se limpia en el camino de `pintar`, y hasta la selección de autoclave
se preserva explícitamente (`:175`, `:222`). Por eso el `componentShown` ya refresca sin
preguntar nada (`:152-158`).

`EquiposParaEntregarController` no declara `setGuardVolver`: no acumula estado, va sin guard.

## Tareas

1. Header a campo + `setAccionRefrescar` en las dos vistas.
2. Cablear ambos botones al disparador `operativo` que ya reciben.
3. `setGuardRefresco` **sólo en Lotes**, reusando el `this::tieneCambiosPendientes` que ya pasa a
   `setGuardVolver` (`:109`); `onDescartar` va en `null` — acá no se descarta nada.
4. Mensaje nuevo en `Constantes.Mensajes.REFRESCO_LOTES`. **No reuses `GUARD_LOTES_CAMBIOS`**, que
   dice "se perderán": acá **no** se pierden. Tiene que decir que lo arrastrado se mantiene pero
   puede haber dejado de estar disponible en la base.
5. `marcarActualizado()` en el `pintar()` de los dos controllers.

## Verificación

- `mvn test`; log sin WARNs de `EdtGuard`.
- Smoke, el que importa: arrastrar materiales a un autoclave **sin lanzar**, Actualizar, decir que
  sí, y confirmar que **lo arrastrado sigue ahí y el autoclave sigue seleccionado**. Repetir
  diciendo que no: no pasa nada.

## Criterio de salida

Lotes refresca conservando el staging y el cartel lo dice con precisión.

---

# Paso 4 — Registrar Estado (cambio de semántica)

**Depende de:** paso 1. **Paralelo con:** 2, 3 y 5.
**Modelo sugerido:** el más fuerte disponible.
**Commit propio:** es el único paso que cambia comportamiento existente, no sólo agrega un botón.

## Contexto para arrancar en frío

Leé la sección "Registrar Estado deja un buffer zombi" de arriba antes de tocar nada; es el
resultado de la auditoría y la razón de que este paso exista aparte.

Resumen operativo: un repintado del grupo operativo deja `cambiosPendientes` vivo pero invisible,
con el contador y los botones mintiendo. La pantalla **ya resolvió** ese problema para el
`componentShown` (`:75-80`): con pendientes, descarta y repinta local en vez de releer. El botón
extiende esa misma decisión, agregándole la relectura.

## Tareas

1. Header a campo + `setAccionRefrescar` en `PantallaRegistrarEstado`.
2. Cablear el botón a `() -> { descartarCambiosPendientes(); solicitarRefresco.run(); }`, detrás
   del guard. Reusá el `descartarCambiosPendientes` que ya usa `setGuardVolver` (`:99-103`); si el
   método privado que hace falta es `resetearCambios()`, usá ese — **verificá cuál deja el
   contador y los botones sincronizados** y usá ese, no el que "parece".
3. `setGuardRefresco` con el supplier `() -> !cambiosPendientes.isEmpty()` que ya existe, y con el
   descarte como `onDescartar`.
4. `Constantes.Mensajes.REFRESCO_REGISTRAR_ESTADO`: **dice que los movimientos sin confirmar se
   descartan**. Es la verdad y es lo mismo que ya pasa hoy al volver a entrar a la pantalla.
5. `marcarActualizado()` en `pintar()`.

## Verificación

- `mvn test`
- Smoke, el que importa: armar 2-3 movimientos sin confirmar, apretar Actualizar, decir que sí, y
  verificar que **el contador vuelve a cero, Confirmar/Cancelar se apagan y la tabla se repuebla
  desde la base**. Decir que no: el buffer queda intacto y no se releyó nada.
- Regresión: entrar y salir de la pantalla con y sin pendientes se comporta igual que antes.

## Criterio de salida

Registrar Estado refresca sin dejar buffer zombi, y el estado visible coincide siempre con el
buffer real.

---

# Paso 5 — Las 3 pantallas de Lavadero

**Depende de:** paso 1. **Paralelo con:** 2, 3 y 4. **Es el paso más grande de los cuatro.**

## Contexto para arrancar en frío

Estas tres **no usan `Disparador`**: son la segunda convención de refresco documentada en
`CLAUDE.md`. El `ActionListener` del botón del menú hace `navegador.show(...)` **y** llama al
método de carga (`UiCoordinator:198-201`, `:209-212`, `:227-230`). Cada una carga con su propia
`TareaUI`, así que el `marcarActualizado()` va en el `.pintar` de esa tarea.

- **Clasificación** → `cargarIngresosSinClasificar()` (público). **Con guard** — ver C1: su
  `refrescar` destruye y reconstruye el `PanelElementosClasificacion`.
- **Ciclos** → `recargar()`, hoy **privado** (`:192`). `abrirPantalla()` (`:175-182`) llama
  `resetConfiguracion()` sobre toda card sin ciclo activo; `recargar()` no toca la config. **Con
  guard**: `:142` ya pasa un supplier a `setGuardVolver`.
- **Salidas** → `cargarDatos()` (público). Sin guard: su único estado mutable es `arrastreOrigen`
  (`:63`), un flag transitorio de DnD; las acciones escriben en el acto, no hay staging.

## Tareas

1. Header a campo + `setAccionRefrescar` en las tres vistas.
2. `recargar()` pasa a `public` con javadoc que explique la diferencia con `abrirPantalla()` y por
   qué el botón usa ésta. **Cablear el botón a `abrirPantalla()` es el error clásico de este
   plan**: le borra al operador la config que está tipeando.
3. **Cancelación en `ClasificacionController` y `SalidasLavaderoController`**: campo
   `TareaUI.Ejecucion` + `cancelar()` antes de lanzar, calcado de `CiclosController:90`/`:193`.
   Sin esto, F5 mantenido apretado da N lecturas concurrentes y gana la que termine última.
4. `setGuardRefresco` en Clasificación (`() -> !panel.getPanelElementos().getFilas().isEmpty()`,
   con descarte) y en Ciclos (supplier existente, sin descarte).
5. Mensajes propios: Clasificación **dice que los elementos cargados se descartan**; Ciclos dice
   que la config se conserva y que cambian los disponibles y los lavarropas ocupados.
6. `marcarActualizado()` en el `.pintar` de las tres cargas.

## Verificación

- `mvn test`
- Smoke Ciclos: tipear config en una card libre, arrastrarle elementos, Actualizar, confirmar →
  **la config sigue escrita**. Si se borró, el botón está cableado a `abrirPantalla()`.
- Smoke Clasificación: cargar elementos sin guardar, Actualizar → aparece el cartel; que sí →
  formulario limpio; que no → todo intacto.
- Smoke cancelación: F5 mantenido apretado en Clasificación y Salidas → los datos que quedan son
  los de la última lectura, sin parpadeos de contenido viejo.
- Verificar que entrar por el menú sigue reseteando las cards libres de Ciclos como antes.
- Los smokes de Lavadero se corren **sin** `-Daptium.edt.strict=true` (los autocompletados
  síncronos lanzan en strict); leer los WARNs del log.

## Criterio de salida

Las once pantallas del mapa tienen botón, F5 y timestamp.

---

# Paso 6 — Documentación y smoke final

**Depende de:** 2, 3, 4 y 5.

## Tareas

1. `CLAUDE.md`, §"Refresco al entrar a una pantalla": la tabla de dos convenciones pasa a
   describir **tres** disparadores de relectura (`componentShown`, listener del menú, botón
   explícito). Dejar dicho que el botón reusa la función de carga de cada pantalla y no agrega un
   camino de lectura.
2. Documentar la regla correcta: **toda pantalla que acumule estado en memoria entre lecturas
   necesita guard de refresco; el guard de Volver es una pista de dónde buscarlo, no la lista.**
   Con el contraejemplo que la motiva: Clasificación no tiene guard de Volver y sí necesita el de
   refresco.
3. Documentar la tabla de "qué pasa con lo pendiente" del mapa: es la que hay que actualizar si
   alguien cambia el `pintar` de una de esas pantallas.
4. Actualizar `MEMORY.md` con el puntero a este plan.
5. Smoke completo de las once con dos instancias contra la misma base.

## Criterio de salida

Un agente que lea `CLAUDE.md` en frío entiende las tres convenciones, sabe dónde buscar si una
pantalla necesita guard, y no reintroduce un refresco global.

---

## Grafo de dependencias

```
Paso 1 (GuardaRefresco + PanelHeader + F5)
   ├── Paso 2 (5 de consulta)      ─┐
   ├── Paso 3 (Lotes + Entregar)   ─┤
   ├── Paso 4 (Registrar Estado)   ─┼── Paso 6 (docs + smoke)
   └── Paso 5 (3 de Lavadero)      ─┘
```

**Los pasos 2 a 5 son paralelos de verdad.** La versión anterior de este plan los serializaba
porque los tres tocaban `UiCoordinator.inicializar()` — pero eso era consecuencia de poner el
timestamp ahí. Moviéndolo al `pintar()` de cada controller (hallazgo I2), **ningún paso de
cableado toca `UiCoordinator`**: cada uno vive en su vista y su controller, en archivos disjuntos.
Vale la pena no por velocidad sino porque cada paso queda revisable en aislamiento.

Si se ejecutan en serie, el orden recomendado es 2 → 3 → 5 → 4: dejar Registrar Estado para el
final, cuando el mecanismo ya está probado en pantallas donde no cambia semántica.

## Anti-patrones a vigilar durante la ejecución

| Anti-patrón | Por qué es tentador | Por qué está mal |
|---|---|---|
| Botón que llama al DAO/Service directo | "Es una lectura sola" | Rompe la regla dura del EDT y crea una segunda ruta de lectura que va a divergir de la del `componentShown` |
| Botón de Ciclos cableado a `abrirPantalla()` | Es el método público | Le borra al operador la config que está tipeando |
| Asumir que el botón puede disparar el runnable del `componentShown` sin mirar | Es la promesa que hacía la v1 de este plan | Falso en Registrar Estado (con pendientes no relee) y peligroso en Clasificación (destruye el formulario) |
| Un mensaje de confirmación genérico para las cuatro | Menos constantes | En dos pantallas lo pendiente se conserva y en dos se descarta. Un cartel que miente entrena a ignorarlo |
| Documentar "sin guard de Volver ⇒ sin guard de refresco" | Es una regla linda | Clasificación la refuta; escribirla reintroduce el bug la próxima vez |
| Marcar el timestamp en el click | Es una línea | Hay debounce de 150 ms y la lectura puede fallar: diría "actualizado" sobre datos viejos |
| Exponer F5 en Clasificación/Salidas sin cancelación | "El debounce ya lo cubre" | Esas dos **no** pasan por `RefrescadorPantallas`: no tienen ni debounce ni cancelación |
| Copiar el `if (navegador == null) return` a `setAccionRefrescar` | Está en el método de al lado | El refresco no tiene nada que ver con el `CardLayout`; sería un botón muerto sin aviso |
| Sumar el botón a los menús "porque queda parejo" | Consistencia visual | No tienen datos que refrescar; un botón que no hace nada enseña que el botón no sirve |

## Riesgo conocido, aceptado sin paso propio

**Refresco a mitad de un drag.** El DnD en sí no se rompe (los `TransferHandler` se instalan sobre
las instancias de `JTable`, que el repintado no recrea, y `configurarDnD()` está protegido por el
flag `dndConfigurado`). Lo que queda expuesto es un refresco que aterrice entre el inicio del
arrastre y el drop: el transferable lleva ítems capturados de una generación ya descartada
(`CiclosController:309-314` → `staging.quitar` en `:410`; misma forma en
`LotesController:424-429`). Requiere apretar F5 con la otra mano mientras se arrastra. Si aparece
en el smoke, la mitigación es una línea: ignorar el refresco mientras el flag de arrastre esté
activo.
