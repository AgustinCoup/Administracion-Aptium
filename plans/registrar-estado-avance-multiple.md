# Plan 1 — Registrar Estado: avanzar varios materiales de un ingreso a la vez

> **✅ CERRADO — 2026-09-25.** Paso 1 `8ac67d0` · Paso 2 `3de63e2` · Paso 3 `0dc4e92` · Paso 4 `417a35f`
> · Paso 5 `3655230` · Paso 6: el commit `docs:` que agrega este bloque.
> `mvn verify` verde (1582 tests). Cobertura de instrucciones: `PlanificadorAvanceMultiple` 100 %,
> `SuperposicionPreviews` 100 %, `ReglasSeleccionAcumulativa` 91 %, `SeleccionAcumulativaTabla` 95 %.
>
> **`/code-review high`: 0 CRITICAL, 0 HIGH. Hallazgos que no se tocaron:**
> - **MEDIUM — `pintar` con el buffer vivo superpone copias viejas sobre un snapshot nuevo**
>   (`RegistrarEstadoController.pintar`/`repintar`): un cambio ajeno en ese equipo no se ve, y
>   `estaTocado` puede bloquear filas con un motivo engañoso. No se toca porque Confirmar sigue
>   protegido por el CAS sobre `estado` (nada se escribe mal), los caminos habituales (F5, `componentShown`)
>   ya descartan el buffer antes, y antes de este plan el caso también dejaba la pantalla
>   inconsistente (el preview se perdía con el contador en pie). Si se decide arreglar: descartar el
>   buffer en `pintar` cuando cambia el snapshot, lo que cambia la asimetría documentada en CLAUDE.md
>   y requiere decisión de diseño.
> - **MEDIUM — el texto de Avanzar incluye la descripción del material sin acotar el largo**
>   (`AVANCE_BLOQUEADO_TOCADO`): puede llegar a 255 caracteres y empujar Confirmar/Cancelar fuera del
>   WrapLayout. No se toca: es una decisión de UI (truncar vs. tooltip) que conviene ver en pantalla.
> - **LOW — `refrescarEstadosEquipos()` redundante tras `reemplazarEquipo`** en `encolarConPreview`:
>   costo menor, sin efecto observable.


**Objetivo:** en la tabla de materiales de Registrar Estado se pueden seleccionar varios materiales
del equipo elegido. Si todos están en el mismo estado, **Avanzar** los pasa juntos al siguiente.
Si no, el botón queda deshabilitado y dice por qué. La selección con teclado y mouse vive en un
**helper reutilizable** de `ui/common`, porque el Plan 2
([`entrega-parcial-para-entregar.md`](entrega-parcial-para-entregar.md)) lo reusa tal cual. De paso
se corrige un bug existente que el avance múltiple agrandaría: **Cancelar no revierte los previews**.

**Rama:** `AvanceMultipleYEntregaParcial` (sale de `main`; la comparten este plan y el Plan 2)
· **Modo:** directo, un commit por paso, sin PRs
**Fecha de creación:** 2026-09-25 · **Revisión adversarial:** hecha el 2026-09-25 (3 HIGH, 5 MEDIUM,
12 LOW), aplicada en esta versión.
**Plan siguiente, que depende de éste:** [`entrega-parcial-para-entregar.md`](entrega-parcial-para-entregar.md).
Su Paso 4 usa el helper del **Paso 1** de acá, y su Paso 2 toca `RegistrarEstadoController` después
del **Paso 5** de acá.

---

## Decisiones tomadas con el usuario

| Tema | Decisión |
|---|---|
| Gestos de selección | **Ctrl+click** suma o quita una fila. **Ctrl+↓ / Ctrl+↑** *suman* la fila de abajo / de arriba de la última fila tocada, y **nunca quitan**. Un **click simple** reemplaza la selección por esa fila. |
| Shift+click, Shift+flecha, arrastre, Ctrl+A | **Se mantienen como en Swing.** No contradicen ninguna regla del pedido. |
| Pérdida de foco | La selección se vacía **salvo** que el foco vaya a **Avanzar** o que la pérdida sea **temporal** (`FocusEvent.isTemporary()`: diálogos modales, Alt+Tab, otra ventana). Cualquier otro destino la vacía, incluidos la tabla de equipos, Confirmar, Cancelar y el header, **también cuando el foco sale desde Avanzar**. |
| Selección con estados distintos, o con un material bloqueado | Avanzar queda **visible y deshabilitado, con el motivo en su texto**. |
| Material no persistido, o con un cambio ya en el buffer | **Se rechaza la operación entera** y el motivo **nombra el material** que la bloquea. |
| Diálogos | Primero **uno** que pregunta si pasan todos completos (Sí / No / Cancelar). Con **No** se abre en cascada el diálogo de cantidad existente, uno por material. |
| Cancelar uno de la cascada | **Aborta todo.** No entra nada al buffer. |
| Cancelar no revierte los previews (bug existente) | **Los previews se aplican sobre copias** de los equipos con cambios pendientes. Cancelar descarta las copias y repinta el snapshot intacto. |

### Decisiones de diseño tomadas por el plan (con su porqué)

| Tema | Decisión | Por qué |
|---|---|---|
| La multi-selección es **opt-in** en `PanelEquipoMaterial` | Método `habilitarSeleccionMultipleMateriales(JComponent... exentos)`. El constructor no cambia. | `PantallaCorrecciones` usa el mismo panel y opera por `getMaterialSeleccionadoIndex()` en cinco lugares (`PantallaCorrecciones:352, 392, 408, 494, 594`). Si no llama al método, sigue en `SINGLE_SELECTION` y no cambia en nada. |
| Los exentos se pasan como **componentes**, no como un predicado | El helper también les engancha un `FocusListener` | Cuando el foco ya pasó a Avanzar, la tabla no lo tiene. Un click después en Confirmar o en el header no dispara ningún `focusLost` en la tabla, y la selección sobreviviría, contra la regla. El helper tiene que escuchar también a los exentos, y un predicado no le da a quién escuchar. |
| La selección viaja como **objetos material**, y el movimiento se arma por **id** | `getMaterialesSeleccionados()` devuelve `List<MaterialRegistrableInterface>` vía `TableSelectionSupport.selectedItems`. `MovimientoMaterial` lleva `material.getId()` capturado **antes** de aplicar ningún preview. | `aplicarMovimientoPreview` parte filas, crea filas nuevas con `id = null` y `unificarEnMemoria` saca filas de la lista (`Equipo:181-200`). Después del primer preview, un índice ya no apunta al mismo material. |
| **Previews sobre copias** | `EquipoRegistrableInterface.copiarParaPreview()` (copia profunda). El controller guarda la copia de cada equipo con cambios en `equiposPendientes`, y `repintar()` superpone las copias al snapshot por `EquipoKey`. | Hoy `aplicarMovimientoPreview` modifica los objetos del `DatosOperativos`, que según su javadoc es "inmutable de raíz" y se reparte también a Para Entregar y a Lotes (`UiCoordinator:287-291`). `resetearCambios` repinta **ese mismo** snapshot ya modificado: tras Cancelar, la tabla sigue mostrando los estados "avanzados" con el contador en 0. Con copias, Cancelar vuelve al estado real **sin releer**, así que la asimetría "`componentShown` no relee con cambios pendientes" se conserva. |
| "Tocada por un preview" se detecta **comparando contra el snapshot original**, no sólo por id | Una fila está tocada si **no está persistida**, si **su id está en el buffer**, o si su **estado o cantidad difieren** de la fila con el mismo id en el equipo original | Un preview parcial le suma cantidad a otra fila persistida que ya estaba en el destino (`Equipo:104-108`, `EquipoOtros:167-171`), y `unificarEnMemoria` puede quedarse con la otra fila, no con la del buffer (`Equipo:188-199`). Esa fila queda persistida, fuera del buffer y con cantidad inflada. Si se avanzara completa, `aplicarMovimientos` fallaría con `"Cantidad inválida"` (`MaterialDAO:143-145`) y tiraría el equipo entero. Con las copias, la comparación sale gratis. |
| **Una sola ruta** para 1 y para N materiales | `PlanificadorAvanceMultiple` evalúa siempre la selección, también con un material. | Dos caminos se desincronizan. Con N = 1 la diferencia visible es mínima: el rechazo por "tocada" aparece en el texto del botón en vez de en un aviso al clickear. |
| Con **N = 1** no se hace la pregunta "¿todos completos?" | Va directo al diálogo de cantidad, como hoy. | Con un solo material, el diálogo de cantidad ya tiene el check "Todos": la pregunta sería una segunda confirmación de lo mismo. |
| Con **N > 1 y todas las cantidades en 1** no hay ningún diálogo | Pasan directo al buffer. | Es lo que ya hace hoy un material de cantidad 1: `CantidadDialogHelper.pedirCantidad` devuelve 1 sin abrir nada. La confirmación real es el botón Confirmar. |
| Dos formas de "no se puede", no una | **Oculto** si el estado mismo no se avanza a mano (`esAvanzableManualmente` falso, o estado final), igual que hoy. **Deshabilitado + motivo** si el problema es *la selección* o hay una escritura en curso. | Ocultar es lo que la pantalla ya hace con un material en `ESTERILIZANDO`: ahí no hay nada que el operador pueda cambiar. Cuando sí puede corregir la selección, el texto le dice qué corregir. |
| Si la pantalla se repintó **durante un diálogo**, se aborta **en ese mismo diálogo** | Se compara la referencia de `ultimoSnapshot` **después de cada diálogo** de la secuencia. | Un modal sigue despachando el EDT: el `done()` de una `TareaUI` en vuelo (un F5 justo antes, o el debounce de 150 ms de `RefrescadorPantallas`) puede correr `pintar` con el diálogo abierto. `pintar` siempre recibe un `DatosOperativos` nuevo (`LectorDatosOperativos.get`), así que la comparación por referencia alcanza. Sin el chequeo, los materiales capturados serían de un equipo que ya no está en la tabla: es el "buffer zombi" de `CLAUDE.md`, que entra por otro lado. Chequear después de cada diálogo evita hacerle tipear N cantidades al operador para descartarlas al final. |
| Avanzar se apaga **mientras se escribe** | El planificador recibe `escrituraEnCurso`, que el controller prende en el `antes` de Confirmar y apaga en el `despues`. | Hoy `antes` apaga Avanzar, pero cualquier cambio de selección llama a `actualizarTextoAvanzar` y lo vuelve a prender (`RegistrarEstadoController:172`). Lo que se avance en ese momento lo borra el `clear()` de `finalizarConfirmacion`, sin avisar. Con N materiales por click, el costo se multiplica. |
| El check "Todos" del diálogo de cantidad se extrae | `CantidadDialogHelper.pedirCantidadConTodos(parent, descripcion, disponible)`. Lo usan `RegistrarEstadoController` y `LotesController`. | Hoy la misma lambda está copiada en `RegistrarEstadoController:194-201` y en `LotesController:412-419`. Este plan toca la primera: es el momento de no dejar dos copias. |
| Sin grupo de refresco nuevo, sin guarda nueva | La selección no es "trabajo acumulado entre lecturas": un repintado la pierde, igual que hoy. | La guarda existente (`setGuardRefresco` → `descartarCambiosPendientes`) ya protege lo único que se acumula, que es el buffer. |
| Sin ruta de escritura nueva | Confirmar sigue en `AplicadorMovimientosPendientes` → `MaterialService`/`EquipoOtrosService.aplicarMovimientos`. | Ya persiste N movimientos por equipo en **una** transacción, con CAS sobre `estado` por material (`MaterialDAO:99-215`, `EquipoOtrosDAO:589+`). El Paso 5 lo **verifica con tests**, no lo reescribe. |

---

## Contexto compartido (leer una vez por sesión)

App de escritorio **Swing, Java 17, Maven**, sin framework de DI. Capas por feature:
`model → dao → service → view/controller`. Todo se cablea a mano en `AppContext` y `UiCoordinator`.

### Reglas duras del repo que este plan debe respetar

1. **Ningún acceso a BD en el EDT.** `TareaUI` es el único mecanismo de trabajo en fondo. Este plan
   no agrega lecturas: todo lo nuevo es lógica sobre el snapshot ya pintado.
2. **El estado mutable de un controller se lee y escribe sólo en el EDT** (`cambiosPendientes`,
   `equiposPendientes`, `ultimoSnapshot`, `escrituraEnCurso`).
3. **`DatosOperativos` no se muta.** Es inmutable por contrato y lo comparten tres pantallas. Hoy se
   viola, y el Paso 3 lo corrige.
4. **Toda escritura que dependa de un dato leído lleva guarda y mira filas afectadas.** La de
   Registrar Estado ya existe: CAS sobre el `estado` del material dentro de `aplicarMovimientos`.
   **No sobre `version`** (ver "Por qué las tablas de detalle NO llevan columna `version`" en
   `CLAUDE.md`).
5. **Lógica de decisión embebida en Swing → clase plana sin Swing, con tests** (moldes:
   `ConstructorVistaCiclos`, `AgrupadorInstanciasSalida`, `SincronizadorVolumenFinal`).
6. **Los controllers orquestan y no calculan.** El texto del botón, el motivo del rechazo y los
   movimientos a armar salen de la clase plana, no del controller.
7. **Textos para el operador en `Constantes.Mensajes` / `Constantes.Textos`.** Sin números mágicos.
8. **Las dos asimetrías documentadas de `RegistrarEstadoController` se conservan:**
   - `componentShown` **no relee** con cambios pendientes: descarta el buffer y repinta local. Con
     las copias del Paso 3, "repinta local" pasa a mostrar el estado real, que es lo que siempre
     quiso decir.
   - El botón Actualizar / F5 es "descartar + releer" (`setGuardRefresco` → `descartarCambiosPendientes`).

### Estado del que parte este plan (verificado contra el código, 2026-09-25)

- `PanelEquipoMaterial` (`ortopedias/view/helpers/`) pone la tabla de materiales en
  `SINGLE_SELECTION` cuando `materialesEditable = true`. Lo construyen **dos** pantallas, las dos con
  `true`: `PantallaRegistrarEstado:51` y `PantallaCorrecciones:96`.
- `RegistrarEstadoController` lee la selección **por índice** (`panel.getMaterialSeleccionadoIndex()`,
  líneas 146 y 178). `PantallaRegistrarEstado.getMaterialSeleccionadoIndex()` y
  `pedirCantidadParaAvanzar` tienen **un solo** llamador, este controller.
- `actualizarTextoAvanzar()` (144-174) **oculta** el botón cuando el material no es avanzable a mano
  o está en estado final.
- `avanzarMaterialSeleccionado()` (176-234) rechaza con aviso un material **no persistido** y uno
  **ya en el buffer**. Este último chequeo va **después** del diálogo de cantidad: hoy el operador
  tipea la cantidad y recién después se entera.
- **Bug existente (H2 de la revisión).** `avanzarMaterialSeleccionado` llama a
  `equipo.aplicarMovimientoPreview(...)` sobre el objeto del snapshot, y `equiposPendientes` guarda
  **ese mismo** objeto. `resetearCambios` (255-262) vacía el buffer y llama a `repintar()` sobre
  `ultimoSnapshot`, que ya viene modificado. Resultado: después de Cancelar, la tabla sigue
  mostrando los estados avanzados con el contador en 0. Las filas `id = null` del preview siguen
  ahí, y avanzar una fila que quedó con el estado del preview arma un `estadoOrigen` falso, que en
  Confirmar da un `CONFLICTO_MATERIAL` falso. Como el snapshot se comparte (`UiCoordinator:287-291`),
  Para Entregar y Lotes ven los mismos objetos modificados si repintan sin releer.
- **Filas infladas (H1).** Un preview parcial le suma a una fila persistida del destino
  (`Equipo:104-108`); uno completo puede unificar y sobrevivir en **la otra** fila
  (`unificarEnMemoria`, `Equipo:188-199`). Pasa lo mismo en `EquipoOtros:167-171` y `:209-220`.
- `IEstadoValidator.esAvanzableManualmente` rechaza `ESTERILIZANDO`/`ESTERILIZADO` como origen o
  destino (`EstadoValidatorImpl:64-70`): a mano sólo se avanza `NUEVO → LAVANDO → LAVADO → EMPAQUETADO`.
- "Mismo estado" alcanza para que todos tengan el mismo siguiente: la tabla muestra materiales de
  **un** equipo, y `getSiguienteEstado` depende sólo del estado y de los flags del equipo.
- **REMITO sin filas reales:** `EquipoOtros.getMaterialesRegistrables()` devuelve **un** material
  sintético con `id = 0` (`EquipoOtros:121-131`), y `esPersistido()` es `id != null`, así que cuenta
  como persistido. Es una sola fila, o sea N = 1.
- `TableSelectionSupport` (`ui/common/dnd/`) ya tiene `selectedItems(JTable, IntFunction<T>)`, que
  convierte índices de vista a modelo. Se reusa, no se duplica.
- En una `JTable` con varias filas seleccionadas, **Tab recorre celdas dentro de la selección**: para
  salir de la tabla con teclado es **Ctrl+Tab**. Los smokes lo tienen en cuenta.
- Los bindings Ctrl+↓/↑ de `JTable` están en `WHEN_ANCESTOR_OF_FOCUSED_COMPONENT` como
  `selectNextRowChangeLead` / `selectPreviousRowChangeLead`. FlatLaf los hereda de Basic.
- **No hay tests de `RegistrarEstadoController`** (el controller es Swing). Lo testeable se extrae.
- ~1503 tests en verde al arrancar.

### Archivos de referencia

| Para | Leer |
|---|---|
| Controller a modificar | `features/equipos/common/controller/RegistrarEstadoController.java` |
| Panel compartido con Correcciones | `features/equipos/ortopedias/view/helpers/PanelEquipoMaterial.java` |
| Pantalla | `features/equipos/ortopedias/view/PantallaRegistrarEstado.java` |
| Preview en memoria (split/merge) | `Equipo.aplicarMovimientoPreview` + `unificarEnMemoria`, `EquipoOtros.aplicarMovimientoPreview` |
| Contrato del snapshot | `app/ui/DatosOperativos.java` (javadoc), `UiCoordinator:280-295` |
| Escritura existente con CAS | `MaterialDAO.aplicarMovimientos`, `EquipoOtrosDAO.aplicarMovimientos` |
| Loop de confirmación testeable | `common/controller/helpers/AplicadorMovimientosPendientes` + su test |
| Selección por ítems | `ui/common/dnd/TableSelectionSupport` + `TableSelectionSupportTest` |
| Diálogo de cantidad | `ui/dialogs/CantidadDialogHelper` |
| Swing testeable headless | `PanelHeaderTest`, `LavarropasCardTest` |

### Comandos

```bash
mvn test                                  # ~1503 tests antes de este plan
mvn verify                                # + JaCoCo
mvn test -Dtest=NombreDeClase
mvn clean package && java -jar target/aptium.jar   # smoke manual (SIN -Daptium.edt.strict=true)
```

---

## Grafo de dependencias

```
Paso 1 (helper de selección, ui/common/seleccion)        ─┐
Paso 2 (PlanificadorAvanceMultiple, clase plana)          ─┤  1, 2 y 3 en PARALELO
Paso 3 (previews sobre copias — arregla Cancelar)         ─┤  (no comparten archivos salvo Constantes)
                                                          │
        1 ──► Paso 4 (vista: PanelEquipoMaterial opt-in, pantalla, diálogos)
                                                          │
        2 + 3 + 4 ──► Paso 5 (controller + verificación de la escritura)
                                                          │
                                                          ▼
                                          Paso 6 (revisión, docs, cierre)
```

| Paso | Modelo sugerido | Archivos que toca |
|---|---|---|
| 1 | **Opus**, medio | `ui/common/seleccion/ReglasSeleccionAcumulativa` (nuevo), `ui/common/seleccion/SeleccionAcumulativaTabla` (nuevo), tests |
| 2 | **Opus**, alto | `equipos/common/controller/helpers/PlanificadorAvanceMultiple` (nuevo), `Constantes.Textos`, test |
| 3 | **Opus**, alto | `EquipoRegistrableInterface`, `Equipo`, `EquipoOtros`, `Material`, `MaterialOtros` (copia), `equipos/common/controller/helpers/SuperposicionPreviews` (nuevo), `RegistrarEstadoController` (sólo `repintar`, el encolado del preview y `resetearCambios`), tests |
| 4 | Sonnet, medio | `PanelEquipoMaterial`, `PantallaRegistrarEstado`, `CantidadDialogHelper`, `LotesController` y `RegistrarEstadoController` (sólo la llamada al diálogo de cantidad), `equipos/common/model/RespuestaAvanceCompleto` (nuevo), `Constantes`, tests |
| 5 | **Opus**, alto | `RegistrarEstadoController`, tests de `MaterialDAO`/`EquipoOtrosDAO`, `ConcurrenciaOptimistaTest` |
| 6 | Sonnet, medio | `CLAUDE.md`, memoria, este archivo |

**Invariantes verificados después de CADA paso:**
- [ ] `mvn test` en verde
- [ ] `PantallaCorrecciones` sin cambios de comportamiento: su tabla de materiales sigue en `SINGLE_SELECTION`
- [ ] Cero `new Thread()` / `SwingWorker` / JDBC nuevos fuera de su lugar
- [ ] Cero rutas de escritura nuevas: Confirmar sigue siendo `aplicarMovimientos`
- [ ] Ningún texto para el operador escrito literal fuera de `Constantes`
- [ ] (desde el Paso 3) Ningún objeto de `DatosOperativos` se modifica

---

## Paso 1 — El helper de selección acumulativa

### Contexto (autocontenido)

Hace falta un comportamiento de selección que Swing no trae, y que van a compartir **dos**
pantallas: ésta y la tabla de materiales de Para Entregar (Plan 2). Tiene dos partes:

- **Reglas** (clase plana, sin Swing): dada la selección actual y la última fila tocada, qué
  selección resulta de Ctrl+↓ / Ctrl+↑, y si una pérdida de foco la conserva.
- **Instalación** (Swing): cablea esas reglas a una `JTable` concreta y a sus componentes exentos:
  modo de selección, `InputMap`/`ActionMap` y `FocusListener`s.

Lo que **no** hace falta programar, porque `DefaultListSelectionModel` en
`MULTIPLE_INTERVAL_SELECTION` ya lo hace: Ctrl+click (alterna la fila), click simple (reemplaza),
Shift+click, Shift+flecha, arrastre y Ctrl+A. El usuario decidió dejarlos como en Swing.

Lo que **sí** cambia: en `JTable`, Ctrl+↓ / Ctrl+↑ están ligados en
`WHEN_ANCESTOR_OF_FOCUSED_COMPONENT` a `selectNextRowChangeLead` / `selectPreviousRowChangeLead`,
que **mueven el foco de fila sin tocar la selección**. Hay que pisar esos bindings, más las variantes
del teclado numérico (`ctrl KP_DOWN` / `ctrl KP_UP`).

**"La última fila tocada"** es el *lead* del `ListSelectionModel` (`getLeadSelectionIndex()`). Tras
un Ctrl+click que **deseleccionó** una fila, el lead es esa fila, y Ctrl+↓ suma la de abajo de ella.

**Regla de foco, con dos listeners.** La "zona" es la tabla más sus componentes exentos. La selección
se vacía cuando el foco **sale de la zona** en forma **no temporal**:
- `focusLost` en la tabla con destino fuera de la zona → vaciar;
- `focusLost` en un exento con destino fuera de la zona → vaciar. Sin este segundo listener, después
  de pasar por Avanzar ningún click posterior vaciaría la tabla, que ya no tiene el foco.

### Tareas

1. **`ui/common/seleccion/ReglasSeleccionAcumulativa`** — clase `final`, sin imports de `javax.swing`
   ni de `java.awt`:
   - `record Seleccion(SortedSet<Integer> filas, int ancla)`, inmutable (copia defensiva en el
     constructor compacto; `ancla = -1` = sin fila tocada).
   - `static Seleccion sumarSiguiente(Seleccion actual, int cantidadFilas)` y `sumarAnterior(...)`:
     - selección **vacía** → devuelve la misma. Sin selección, Ctrl+flecha no inventa un punto de
       partida;
     - `ancla` en el borde (última fila para ↓, 0 para ↑) → devuelve la misma;
     - si no: la fila `ancla ± 1` **se agrega** (si ya estaba, no pasa nada) y pasa a ser el ancla.
       **Nunca saca filas.**
   - `static boolean conservarAlPerderFoco(boolean perdidaTemporal, boolean destinoEnLaZona)` →
     `perdidaTemporal || destinoEnLaZona`. Es trivial, pero es **la regla** y lleva nombre y test,
     para que nadie la reescriba adentro de un listener.
2. **`ui/common/seleccion/SeleccionAcumulativaTabla`** — utilidad `final`:
   - `static void instalar(JTable tabla, JComponent... exentos)`:
     - `TableSelectionSupport.enableMultiSelection(tabla)`;
     - pisa `ctrl DOWN`, `ctrl KP_DOWN`, `ctrl UP` y `ctrl KP_UP` en el `InputMap` de
       `WHEN_ANCESTOR_OF_FOCUSED_COMPONENT` con dos acciones propias. Los nombres de acción van en
       constantes (`ACCION_SUMAR_SIGUIENTE`, `ACCION_SUMAR_ANTERIOR`, visibles para el test), no
       como literales sueltos;
     - cada acción lee la selección del modelo (`getSelectedRows()` + `getLeadSelectionIndex()`), le
       pide la nueva a `ReglasSeleccionAcumulativa` y aplica `addSelectionInterval(fila, fila)` sobre
       la **única** fila nueva, así el lead queda en ella. Después hace
       `scrollRectToVisible(getCellRect(fila, 0, true))`;
     - un `FocusListener` en la tabla **y otro en cada exento**, con la misma decisión:
       `clearSelection()` salvo `conservarAlPerderFoco(e.isTemporary(), enLaZona(e.getOppositeComponent()))`.
       `enLaZona(null)` es `false` (el foco se fue a otra aplicación; esa pérdida suele ser temporal,
       y la regla la cubre por ese lado).
   - Javadoc con el **por qué** de los exentos: sin ellos, el click en Avanzar vaciaría la selección
     antes de usarla, y el diálogo modal la vaciaría otra vez. Y el del listener en los exentos.
   - **No** mover `TableSelectionSupport` de paquete: lo usan los DnD de Lotes.
3. **Tests:**
   - `ReglasSeleccionAcumulativaTest`: `sumarSiguiente_agregaLaFilaDeAbajoYMueveElAncla`,
     `sumarSiguiente_filaYaSeleccionada_noQuitaNadaYMueveElAncla`,
     `sumarSiguiente_enLaUltimaFila_noCambia`, `sumarAnterior_enLaPrimeraFila_noCambia`,
     `sumarSiguiente_sinSeleccion_noCambia`,
     `sumarSiguiente_anclaDeseleccionadaPorCtrlClick_sumaLaDeAbajoDelAncla`,
     `sumarSiguiente_nuncaQuitaFilasNoContiguas` (selección {1, 4}, ancla 1 → {1, 2, 4}),
     `conservarAlPerderFoco_*` (las cuatro combinaciones).
   - `SeleccionAcumulativaTablaTest` (headless, como `PanelHeaderTest`), con una tabla de 5 filas y
     un botón exento: `instalar_ponerSeleccionMultiple`, `ctrlAbajo_estaLigadoALaAccionPropia` (el
     `InputMap` resuelve `ctrl DOWN` a `ACCION_SUMAR_SIGUIENTE`),
     `accionSumarSiguiente_sumaSinQuitar` (invocando la acción del `ActionMap`),
     `focoDeTablaANoExento_vacia`, `focoDeTablaAExento_conserva`,
     `focoDeExentoANoExento_vacia`, `focoDeExentoATabla_conserva`, `focoPerdidoTemporal_conserva`,
     `focoPerdidoConOpuestoNull_noExplota`. Para el foco, invocar los `getFocusListeners()` de la
     tabla o del botón con un `FocusEvent` armado a mano: no hace falta foco real.

### Verificación

```bash
mvn test -Dtest='ReglasSeleccionAcumulativaTest,SeleccionAcumulativaTablaTest'
mvn test
```

### Criterio de salida

- [ ] Los dos tests nuevos en verde; `mvn test` en verde
- [ ] `ReglasSeleccionAcumulativa` no importa nada de `javax.swing` ni de `java.awt`
- [ ] Ninguna pantalla usa todavía el helper (lo cablea el Paso 4)
- [ ] Commit: `feat: helper de seleccion acumulativa para tablas`

---

## Paso 2 — `PlanificadorAvanceMultiple`: la regla de qué se puede avanzar

### Contexto (autocontenido)

Hoy la decisión de "¿se puede avanzar?" está repartida entre `actualizarTextoAvanzar()` y
`avanzarMaterialSeleccionado()` de `RegistrarEstadoController`, calculada adentro del controller y
para un solo material. Este paso la saca a una clase plana, la generaliza a N materiales y la testea.
El controller (Paso 5) sólo la consulta.

**Qué es una fila "tocada por un preview"** (bloquea la operación y se nombra): no persistida, **o**
con su id en el buffer, **o** con estado o cantidad **distintos** de la fila con el mismo id en el
**equipo original** (el del snapshot, que el Paso 3 deja intacto). El tercer caso existe porque un
preview puede inflar una fila persistida que no está en el buffer (ver "Filas infladas" en "Estado
del que parte"). Mientras no hay cambios en un equipo, el original y el visible son el mismo objeto,
y el tercer caso nunca se da.

Las reglas, **en este orden** (el orden decide qué motivo ve el operador cuando fallan varias):

1. Selección vacía → **sin selección** (botón oculto, texto `BOTON_SELECCIONE_MATERIAL`, como hoy).
2. `escrituraEnCurso` → **bloqueado** con `AVANCE_BLOQUEADO_GUARDANDO`.
3. Algún material **tocado por un preview** → **bloqueado**, nombrando el **primero** en el orden de
   la tabla.
4. Estados **distintos** → **bloqueado** con el motivo de estados distintos.
5. Estado común **no avanzable a mano** o **final** → **no avanzable** (botón oculto, como hoy).
6. Si no → **avanzable**, con el siguiente estado y los materiales en el orden de la tabla.

3 va antes que 4 porque nombra un material concreto, que es lo más accionable. Además, una fila
recién partida por un preview está siempre en otro estado que sus hermanas: con 4 primero, el
operador leería "estados distintos" en vez de "esta fila ya tiene un cambio sin confirmar".

### Tareas

1. **`features/equipos/common/controller/helpers/PlanificadorAvanceMultiple`** — recibe
   `IEstadoValidator` en el constructor (igual que `AgrupadorEntregas`):
   - `EvaluacionAvance evaluar(EntradaAvance entrada)`, con
     `record EntradaAvance(EquipoRegistrableInterface original, EquipoRegistrableInterface visible,
     List<MaterialRegistrableInterface> seleccion, Set<Integer> idsEnBuffer, boolean escrituraEnCurso)`.
     `visible == null` equivale a selección vacía; `original == visible` cuando el equipo no tiene
     cambios.
   - `sealed interface EvaluacionAvance` con cuatro `record`: `SinSeleccion`,
     `Bloqueado(String motivo)`, `NoAvanzable`, `Avanzable(EstadoEquipo siguiente,
     List<MaterialRegistrableInterface> materiales)`. Cada uno expone lo que la pantalla necesita,
     sin que el controller calcule: `botonVisible()`, `botonHabilitado()` y `textoBoton()`.
     `Avanzable.textoBoton()` usa `BOTON_PASAR_A` con 1 material y `BOTON_PASAR_N_A` con N;
     `Bloqueado.textoBoton()` es el motivo.
   - `List<MovimientoMaterial> movimientosCompletos(Avanzable avance)`: un movimiento por material,
     con cantidad = `getCantidad()`, origen = estado común y destino = `siguiente`.
   - `List<MovimientoMaterial> movimientosConCantidades(Avanzable avance, Map<Integer, Integer>
     cantidadPorMaterialId)`: lanza `IllegalArgumentException` si falta un id o si una cantidad está
     fuera de `1..getCantidad()`. Es un bug del llamador, no un error del operador: el spinner ya
     acota.
   - Javadoc de la clase: por qué "mismo estado" alcanza (un equipo, sus flags), por qué "tocada" se
     compara contra el original, y por qué los movimientos se arman **antes** de aplicar ningún
     preview.
2. **`Constantes.Textos`**, sólo textos nuevos:
   - `BOTON_PASAR_N_A = "Pasar %d a %s"`;
   - `AVANCE_BLOQUEADO_ESTADOS_DISTINTOS` (p. ej. `"Seleccione materiales en el mismo estado"`);
   - `AVANCE_BLOQUEADO_TOCADO` (`"«%s» ya tiene un cambio sin confirmar"`);
   - `AVANCE_BLOQUEADO_GUARDANDO` (`"Guardando…"`).
   Si al cablear el Paso 5 no entran en el `WrapLayout`, se acortan ahí; no se truncan por código.
3. **`PlanificadorAvanceMultipleTest`** (Mockito para los modelos, como `AgrupadorEntregasTest`):
   `sinEquipo_esSinSeleccion`, `seleccionVacia_esSinSeleccion`, `escrituraEnCurso_bloqueado`,
   `unMaterialAvanzable_textoPasarA`, `tresMaterialesMismoEstado_textoPasarNA`,
   `estadosDistintos_bloqueadoConMotivo`, `unoNoPersistido_bloqueadoNombrandoElPrimero`,
   `unoEnBuffer_bloqueadoNombrandoElPrimero`,
   `filaQueRecibioCantidadDeUnPreview_bloqueada` (mismo id que en el original, con otra cantidad),
   `filaQueCambioDeEstadoPorUnificacion_bloqueada`,
   `noPersistidoYEstadosDistintos_ganaElNoPersistido` (fija el orden),
   `estadoEsterilizando_noAvanzable`, `estadoFinal_noAvanzable`,
   `remitoSintetico_idCero_esAvanzable`,
   `movimientosCompletos_unoPorMaterialConSuCantidadYElEstadoComun`,
   `movimientosConCantidades_respetaLasCantidadesPorId`,
   `movimientosConCantidades_faltaUnId_lanza`, `movimientosConCantidades_cantidadFueraDeRango_lanza`,
   `movimientos_conservanElOrdenDeLaSeleccion`.

### Verificación

```bash
mvn test -Dtest=PlanificadorAvanceMultipleTest
mvn test
```

### Criterio de salida

- [ ] Test en verde, cobertura de `PlanificadorAvanceMultiple` ≥ 90 %
- [ ] La clase no importa nada de Swing
- [ ] `RegistrarEstadoController` todavía no la usa (Paso 5)
- [ ] Commit: `feat: planificador de avance de varios materiales`

---

## Paso 3 — Previews sobre copias: Cancelar vuelve al estado real

### Contexto (autocontenido)

Es la corrección de un bug que ya existe, y va antes del avance múltiple porque éste lo multiplica.
Hoy `aplicarMovimientoPreview` modifica los objetos del snapshot compartido (`DatosOperativos`, que es
inmutable por contrato y lo reciben Registrar Estado, Para Entregar y Lotes). `resetearCambios`
repinta **ese mismo** snapshot ya modificado: Cancelar deja la tabla mostrando los estados avanzados,
con el contador en 0.

La solución es **copy-on-write por equipo**:
- el primer avance sobre un equipo guarda en `equiposPendientes` una **copia profunda**, y el preview
  se aplica **en la copia**;
- `repintar()` arma la lista a pintar desde el snapshot, **reemplazando por `EquipoKey`** los equipos
  que tienen copia. Si un equipo del buffer ya no está en el snapshot, su copia no se pinta;
- `resetearCambios()` vacía las copias y repinta: aparece el snapshot intacto, **sin releer**. La
  asimetría de `componentShown` se conserva tal cual.

La superposición es lógica pura y va a una clase plana. La copia es del modelo.

### Tareas

1. **`EquipoRegistrableInterface.copiarParaPreview()`** → copia profunda: equipo y **cada** material
   son objetos nuevos, con ids, cantidades, estados, `ultimoMovimiento` y flags idénticos.
   - En `Equipo` y en `EquipoOtros`, apoyado en un constructor de copia o un `copiar()` de
     `Material`/`MaterialOtros`.
   - `EquipoOtros` copia también `tipoIngreso`, `remitoCantidad`, `remitoId` y `volumenEquipo`, más
     todo lo que lea `getMaterialesRegistrables()`, incluido el material sintético del REMITO.
   - Javadoc: existe porque el snapshot es inmutable y compartido.
2. **`equipos/common/controller/helpers/SuperposicionPreviews`** (sin Swing):
   `static List<EquipoRegistrableInterface> superponer(List<EquipoRegistrableInterface> snapshot,
   Map<EquipoKey, EquipoRegistrableInterface> copias)`. Conserva el orden del snapshot, reemplaza por
   clave y descarta las copias sin equipo en el snapshot.
3. **`RegistrarEstadoController`**, sólo lo mínimo:
   - al encolar un avance: `equiposPendientes.computeIfAbsent(key, k -> equipo.copiarParaPreview())`,
     y el preview va **sobre la copia**. Los materiales a mover se resuelven **en la copia, por id**:
     la selección viene de la tabla, que desde ahora muestra la copia, así que son sus objetos;
   - `repintar()` pinta `SuperposicionPreviews.superponer(...)`;
   - `resetearCambios()` queda igual (vacía el buffer y las copias, y repinta), y ahora sí muestra
     el estado real;
   - un método `equipoOriginal(EquipoKey)` que lo busca en `ultimoSnapshot`, para el planificador del
     Paso 5.
4. **Tests:**
   - `EquipoTest.copiarParaPreview_esIndependiente` (un preview sobre la copia no cambia el
     original, ni sus materiales), `copiarParaPreview_conservaIdsYUltimoMovimiento`;
     `EquipoOtrosTest` con lo mismo, más `copiarParaPreview_remitoSinFilas_conservaElSintetico`. Si
     los tests no existen, crearlos en el paquete espejo.
   - `EquipoTest.aplicarMovimientoPreview_dosMaterialesDistintosSeguidos_cadaUnoTerminaEnSuDestino`
     y `…_dosFilasMismoCodigoMismoEstado_noPierdeCantidad` (y sus equivalentes en `EquipoOtrosTest`):
     fijan que dos previews encadenados, que es lo que hará el avance múltiple, no se pisan.
   - `SuperposicionPreviewsTest`: `sinCopias_devuelveElSnapshot`, `reemplazaPorClaveConservandoElOrden`,
     `copiaDeEquipoQueYaNoEsta_seDescarta`, `ortopediaYOtrosConMismoId_noSeConfunden` (el motivo de
     `EquipoKey`).
5. **Smoke:** avanzar un material parcial y **Cancelar** → la tabla vuelve exactamente al estado
   anterior, sin la fila partida. Avanzar, ir a Lotes y volver → Lotes muestra el estado real.

### Verificación

```bash
mvn test -Dtest='EquipoTest,EquipoOtrosTest,SuperposicionPreviewsTest'
mvn test
```

### Criterio de salida

- [ ] Tests en verde; `mvn test` en verde
- [ ] `grep` de `aplicarMovimientoPreview` en `RegistrarEstadoController`: se llama sólo sobre copias
- [ ] Smoke de Cancelar
- [ ] Commit: `fix: los previews de registrar estado no mutan el snapshot compartido`

> **Hecho (0dc4e92).** `equipoOriginal(EquipoKey)` **no** se agregó: sin llamador era código muerto. Lo
> agrega el Paso 5 junto con su primer uso. Se sumó `EquipoTableModel.reemplazarEquipo` (la tabla
> tiene que mostrar la copia tras el primer avance sin reordenar ni perder la selección).

---

## Paso 4 — La vista: multi-selección opt-in y los diálogos

### Contexto (autocontenido)

`PanelEquipoMaterial` es compartido con Correcciones, que **no** puede cambiar. La multi-selección se
prende con un método explícito que sólo llama Registrar Estado. La pantalla gana la API que el
controller va a necesitar en el Paso 5: la selección como lista, la pregunta "¿todos completos?" y el
diálogo de cantidad.

Requiere el Paso 1 commiteado.

### Tareas

1. **`PanelEquipoMaterial`**:
   - `public void habilitarSeleccionMultipleMateriales(JComponent... exentos)` →
     `SeleccionAcumulativaTabla.instalar(tablaMateriales, exentos)`. Javadoc: es opt-in porque
     Correcciones opera por índice y en selección simple.
   - `public List<MaterialRegistrableInterface> getMaterialesSeleccionados()` →
     `TableSelectionSupport.selectedItems(tablaMateriales, modeloMateriales::getMaterialAt)`.
   - `getMaterialSeleccionadoIndex()` y `getMaterialSeleccionado()` **se quedan** (Correcciones los usa).
2. **`PantallaRegistrarEstado`**:
   - después de `crearPanelBotones()` (el botón tiene que existir):
     `panelTablas.habilitarSeleccionMultipleMateriales(btnAvanzar)`;
   - `getMaterialesSeleccionados()` delega. `getMaterialSeleccionadoIndex()` **se queda por ahora**:
     su único llamador es el controller, que deja de usarlo en el Paso 5, y es ahí donde se borra;
   - `RespuestaAvanceCompleto preguntarAvanceCompleto(int cantidadMateriales, String estadoDestino)`
     con `JOptionPane.YES_NO_CANCEL_OPTION`: Sí → `TODOS_COMPLETOS`, No → `ELEGIR_CANTIDADES`,
     Cancelar **o cerrar el diálogo** → `CANCELAR`. El `enum RespuestaAvanceCompleto` va en
     **`features/equipos/common/model/`**: lo devuelve la vista y lo consume el controller, así que
     no puede vivir en el paquete de ninguno de los dos;
   - `pedirCantidadParaAvanzar(String descripcion, int disponible)` pierde el parámetro `BiConsumer` y
     pasa a llamar `CantidadDialogHelper.pedirCantidadConTodos`. Su única llamada
     (`RegistrarEstadoController:191`) se adapta **en este paso** (se borran la lambda y los dos
     argumentos), así el controller compila. Es el único cambio del controller acá.
3. **`CantidadDialogHelper.pedirCantidadConTodos(JPanel parent, String descripcion, int disponible)`**:
   encapsula el cableado del check "Todos": al tildarlo pone el spinner al máximo y lo deshabilita, y
   al destildarlo lo rehabilita. `LotesController.pedirCantidad` pasa a llamarlo, y su lambda
   desaparece.
4. **`Constantes.Mensajes.AVANCE_MULTIPLE_PREGUNTA`** (p. ej. `"¿Pasar los %d materiales completos a
   %s?\n\nSí: todas sus unidades.\nNo: elegir la cantidad de cada uno."`) y `TITULO_AVANCE_MULTIPLE`.
5. **Tests:**
   - `PanelEquipoMaterialTest` (nuevo, headless):
     - `porDefecto_materialesEnSeleccionSimple`, que **fija que Correcciones no cambia**;
     - `habilitarSeleccionMultiple_ponerSeleccionMultiple`;
     - `getMaterialesSeleccionados_devuelveLosObjetosDeLasFilasSeleccionadas`.
   - El diálogo usa `JOptionPane` y no corre headless: **no** forzar un test. El check "Todos" en las
     dos pantallas lo cubre el smoke del Paso 5. Anotarlo en el commit.

### Verificación

```bash
mvn test -Dtest=PanelEquipoMaterialTest
mvn test
```

### Criterio de salida

- [ ] `mvn test` en verde (el controller todavía usa el índice y compila)
- [ ] `PantallaCorrecciones` no se tocó (`git diff --stat` no la lista)
- [ ] Cero copias de la lambda del check "Todos"
- [ ] Commit: `feat: seleccion multiple opt-in en la tabla de materiales de registrar estado`

---

## Paso 5 — El controller, y la prueba de que Confirmar ya escribe N movimientos

### Contexto (autocontenido)

Es el paso donde se junta todo, y donde vive lo que puede salir mal sin que nada deje de compilar:
el buffer, los previews y el repintado durante los diálogos.

Requiere los Pasos 2, 3 y 4 commiteados.

**El flujo de Avanzar, completo.** Esto es orquestación; las decisiones son del planificador:

```
evaluacion = planificador.evaluar(new EntradaAvance(original, visible, seleccion,
                                                    idsEnBuffer(key), escrituraEnCurso))
si no es Avanzable                  → advertencia con el motivo y salir   (defensa: el botón ya estaba apagado)
snapshotAntes = ultimoSnapshot
si N == 1                           → cantidad = pedirCantidadParaAvanzar;  null → salir
si N > 1 y alguna cantidad > 1      → preguntarAvanceCompleto:
                                         TODOS_COMPLETOS   → movimientosCompletos
                                         ELEGIR_CANTIDADES → cascada: un diálogo por material;
                                                             el primer null aborta TODO (nada al buffer)
                                         CANCELAR          → salir
si N > 1 y todas las cantidades 1   → movimientosCompletos, sin diálogo
DESPUÉS DE CADA DIÁLOGO: si ultimoSnapshot != snapshotAntes → advertencia AVANCE_PANTALLA_RELEIDA y salir
movimientos = planificador.movimientos…(…)        ← ids capturados ACÁ, antes de cualquier preview
copia = equiposPendientes.computeIfAbsent(key, copiarParaPreview)
por cada movimiento: buffer.put(materialId, movimiento)
por cada movimiento: copia.aplicarMovimientoPreview(material de la copia con ese id, cantidad, siguiente)
repintar, contador, Confirmar/Cancelar encendidos
```

El chequeo del snapshot compara **referencias**: `pintar` siempre asigna un `DatosOperativos` nuevo.
`resetearCambios` no puede correr con un diálogo abierto, así que el snapshot no cambia por otro lado.

**La escritura no cambia.** `AplicadorMovimientosPendientes.aplicarTodos` ya manda la **lista** de
movimientos de cada equipo a `aplicarMovimientos`, que los aplica en **una** transacción y lanza
`ConflictoConcurrenciaException` si **cualquiera** choca: un conflicto en uno revierte los N de ese
equipo. Es lo correcto (el operador los pidió juntos), pero hoy **no hay un test** que lo fije con
N > 1. Este paso lo agrega.

### Tareas

1. **`RegistrarEstadoController`**:
   - `private final PlanificadorAvanceMultiple planificador = new PlanificadorAvanceMultiple(estadoValidator);`
     El constructor **no cambia de firma**: el planificador es un tipo del dominio sin estado, no un
     service inyectado (regla de `CLAUDE.md`).
   - `private boolean escrituraEnCurso`: se prende en el `antes` de `confirmarCambios` y se apaga en
     el `despues`, antes de `sincronizarBotonesConBuffer`.
   - `actualizarTextoAvanzar()` queda en tres líneas: evaluar y volcar `botonVisible`,
     `botonHabilitado` y `textoBoton` al panel.
   - `avanzarMaterialSeleccionado()` pasa a ser `avanzarSeleccion()`, con el flujo de arriba, partido en
     métodos de < 50 líneas: `pedirCantidades(Avanzable, DatosOperativos snapshotAntes) →
     Optional<List<MovimientoMaterial>>` (vacío = canceló o se repintó) y `encolarConPreview(...)`.
   - Borrar el uso de `getMaterialSeleccionadoIndex()` y el método en `PantallaRegistrarEstado`.
   - Borrar `MATERIAL_CAMBIOS_PENDIENTES` y `MATERIAL_CAMBIO_PENDIENTE_DUP` de `Constantes` **si quedan
     sin llamador** (`grep` en `src/`).
   - `Constantes.Mensajes.AVANCE_PANTALLA_RELEIDA`: dice qué pasó y qué hacer (p. ej. `"La pantalla
     se actualizó mientras elegía las cantidades. Vuelva a seleccionar los materiales."`).
   - **No tocar** `componentShown`, `setGuardRefresco`, `setGuardVolver` ni `finalizarConfirmacion`.
2. **Tests de la escritura** (verifican, no reescriben):
   - `MaterialDAOTest.aplicarMovimientos_tresMaterialesDelMismoEquipo_escribeTresMovimientosEnUnaTransaccion`;
   - `MaterialDAOTest.aplicarMovimientos_variosMateriales_unoEnConflicto_noEscribeNinguno`: el segundo
     de tres lleva un `estadoOrigenEsperado` viejo → `ConflictoConcurrenciaException`, los tres siguen
     en su estado original y no hay filas nuevas en `material_movimientos`;
   - los mismos dos en `EquipoOtrosDAOTest`;
   - `ConcurrenciaOptimistaTest.registrarEstadoAvanceMultipleChocaEnUnoYNoAplicaNinguno`, con la
     forma del resto: A lee tres materiales → B avanza **uno** y commitea → A confirma los tres →
     conflicto, y el estado final es exactamente el de B (uno avanzado por B, dos intactos, y en la
     tabla sólo el movimiento de B).
3. **Smoke manual** (sin `-Daptium.edt.strict=true`: los autocompletados síncronos lanzan en strict):
   1. Un equipo con 3 materiales en `NUEVO`: Ctrl+click en dos y Ctrl+↓ suma el tercero; el botón dice
      "Pasar 3 a Lavando". Sí → los tres pasan completos; el contador dice 3.
   2. Mismo arranque, No → tres diálogos de cantidad; cancelar el segundo → **nada** en el buffer.
   3. Un material en `NUEVO` y otro en `LAVADO` seleccionados → botón deshabilitado con el motivo.
   4. Avanzar uno parcial y seleccionar la fila partida, o la fila del destino que recibió la
      cantidad → botón deshabilitado nombrando ese material.
   5. Seleccionar dos y hacer click en la tabla de equipos → la selección de materiales se vacía.
      Seleccionar dos y **Ctrl+Tab** hasta Avanzar → se conservan. Desde Avanzar, click en
      Confirmar → se vacían.
   6. Avanzar dos, **Cancelar** → la tabla vuelve exactamente al estado real.
   7. **Correcciones:** Ctrl+click no selecciona dos filas; Editar y Eliminar siguen funcionando.
   8. Lotes: el check "Todos" del diálogo de cantidad sigue funcionando.
   9. Confirmar → los N quedan en la base, y Ver Equipos lo muestra. Mientras guarda, cambiar la
      selección no vuelve a prender Avanzar.

### Verificación

```bash
mvn test -Dtest='MaterialDAOTest,EquipoOtrosDAOTest,ConcurrenciaOptimistaTest,PlanificadorAvanceMultipleTest'
mvn test
```

### Criterio de salida

- [ ] `mvn test` en verde con los tests nombrados arriba
- [ ] `RegistrarEstadoController` no calcula: no tiene `if` sobre estados ni formatea textos del botón
- [ ] Ninguna referencia a `getMaterialSeleccionadoIndex` fuera de `PanelEquipoMaterial` y `PantallaCorrecciones`
- [ ] Los 9 puntos del smoke manual
- [ ] Commit: `feat: avanzar varios materiales de un equipo a la vez`

---

## Paso 6 — Revisión, cobertura, documentación y cierre

### Tareas

1. `/code-review high` sobre el diff del plan (`main..HEAD` en la rama). Aplicar CRITICAL y HIGH;
   anotar acá el MEDIUM que se decida no tocar, con el motivo.
2. `mvn verify` + JaCoCo: `ReglasSeleccionAcumulativa`, `PlanificadorAvanceMultiple` y
   `SuperposicionPreviews` ≥ 90 %; `SeleccionAcumulativaTabla` ≥ 80 %.
3. **`CLAUDE.md`:**
   - En "Dos asimetrías que hay que preservar": los previews van **sobre copias** y el
     `DatosOperativos` no se muta nunca (antes Cancelar no revertía). Además, el avance múltiple
     **aborta si la pantalla se repintó con un diálogo abierto**, con el porqué: un modal despacha el
     `done()` de una `TareaUI` en vuelo.
   - En "Patrones e interfaces clave": el helper `ui/common/seleccion/` (qué gestos agrega, la regla
     de foco con los exentos escuchados y que es **opt-in**), y que `PanelEquipoMaterial` lo prende
     sólo en Registrar Estado porque Correcciones opera por índice.
   - "Tests": actualizar el número; sumar `PlanificadorAvanceMultiple`, `SuperposicionPreviews` y
     `ReglasSeleccionAcumulativa` a la lista de ejemplos de lógica extraída de Swing.
4. **Memoria:** `project-registrar-estado-avance-multiple.md` (`type: project`) con lo que no se
   deduce del código:
   - la cascada aborta todo (decisión del usuario);
   - la regla de foco (Avanzar + pérdidas temporales) y por qué se escucha también a los exentos;
   - el chequeo de snapshot después de cada diálogo, con su porqué;
   - que los previews iban sobre el snapshot compartido y Cancelar no revertía;
   - que el Plan 2 reusa el helper.

   Enlazar `[[project-architecture]]`, `[[project-bloqueo-optimista]]` y
   `[[project-botones-refresco-por-pantalla]]`. Agregar una línea nueva en `MEMORY.md`.
5. Marcar este plan como **✅ CERRADO**, con los SHAs de cada paso.

### Criterio de salida

- [ ] `mvn verify` en verde, cobertura en las clases nuevas según el punto 2
- [ ] `CLAUDE.md` y memoria actualizados
- [ ] Commit: `docs: avance multiple en registrar estado`

---

## Catálogo de anti-patrones para este plan

| Anti-patrón | Por qué está mal acá |
|---|---|
| Identificar la selección por índice | `aplicarMovimientoPreview` parte filas, agrega filas con `id = null` y `unificarEnMemoria` saca filas de la lista: después del primer preview, el índice 2 ya no es el mismo material. |
| Aplicar el preview sobre el objeto del snapshot | `DatosOperativos` es inmutable por contrato y lo comparten tres pantallas. Es el bug de hoy: Cancelar no revierte y Lotes ve estados que no existen. |
| Detectar "ya tiene un cambio" sólo por id en el buffer | Un preview infla una fila persistida que no está en el buffer. Avanzarla completa hace fallar el equipo entero con `"Cantidad inválida"`. Se compara contra el original. |
| Aplicar el preview de cada material apenas se arma su movimiento | El preview del primero puede cambiar la lista que el segundo todavía necesita leer. Se arman **todos** los movimientos, se encola **todo**, y recién después se aplican los previews. |
| Prender la multi-selección en el constructor de `PanelEquipoMaterial` | Correcciones cambia de comportamiento sin que nadie lo note: opera por `getMaterialSeleccionadoIndex()`, que con varias filas devuelve sólo la primera, y **Eliminar** borraría el material equivocado. |
| Saltear el material cancelado en la cascada | El usuario eligió abortar todo. Además, saltear deja un avance a medias que el operador tiene que encontrar en la tabla para deshacer. |
| Chequear el snapshot sólo al final de la cascada | El operador tipea N cantidades para que se descarten todas. Se chequea después de cada diálogo. |
| Poner el `if` de estados, o el `String.format` del botón, en el controller | Los controllers orquestan. Esa lógica sin tests es justo la que se rompe sin que nada deje de compilar. |
| Confiar en que nada repinta mientras hay un modal abierto | Un modal sigue despachando el EDT: el `done()` de una `TareaUI` en vuelo corre igual. |
| Dejar que un cambio de selección prenda Avanzar durante Confirmar | Lo que se avance mientras se guarda lo borra el `clear()` de `finalizarConfirmacion`, sin avisar. |
| Escribir un camino de escritura nuevo para "avance múltiple" | `aplicarMovimientos` ya recibe una **lista** y la aplica en una transacción con CAS por material. |
| Guardar con la `version` del equipo | Dos operadores avanzando materiales **distintos** del mismo equipo chocarían por falso positivo. |
| Vaciar la selección en **todo** `focusLost` | El click en Avanzar la vaciaría antes de usarla, y el diálogo modal la vaciaría otra vez. |
| Escuchar el foco sólo en la tabla | Después de pasar por Avanzar, la tabla ya no tiene el foco: ningún click posterior la vacía. |
| Listar a mano los diálogos exentos en vez de usar `isTemporary()` | Cada diálogo nuevo, y Alt+Tab, sería un caso más que alguien tiene que acordarse de agregar. |
| Dejar Ctrl+↓ en su binding de Swing | `selectNextRowChangeLead` mueve el foco de fila **sin seleccionar**: el operador ve moverse el recuadro y cree que sumó una fila. |
| Releer de la base en `resetearCambios` "para arreglar Cancelar" | Cambia la asimetría documentada y sigue mutando el snapshot compartido. Las copias arreglan las dos cosas. |
| Copiar otra vez la lambda del check "Todos" | Ya hay dos copias (Registrar Estado y Lotes). Se extrae a `CantidadDialogHelper.pedirCantidadConTodos`. |

---

## Plan de sesiones

Seis pasos, **seis sesiones**. Cada una arranca en frío.

| Sesión | Pasos | Modelo | Effort | Fast mode | Por qué |
|---|---|---|---|---|---|
| 1 | Paso 1 | **Opus 5** | medio | ❌ no | Poco código, pero los bindings de `JTable` (`WHEN_ANCESTOR_OF_FOCUSED_COMPONENT`, las variantes `KP_*`), la semántica del lead y el listener en los exentos son fáciles de hacer casi bien. Y el Plan 2 lo hereda. |
| 2 | Paso 2 | **Opus 5** | **alto** | ❌ no | Es la regla de negocio del plan. El **orden** de los chequeos y la detección de filas tocadas por comparación deciden qué ve el operador, y un test lo fija. |
| 3 | Paso 3 | **Opus 5** | **alto** | ❌ no | Corrige un bug vivo que toca el invariante de inmutabilidad del snapshot compartido. Una copia que no es profunda de verdad vuelve a mutar el original, y compila igual. |
| 4 | Paso 4 | **Sonnet 5** | medio | ✅ sí | Swing declarativo con la API ya definida. La única trampa (opt-in, no tocar Correcciones) tiene su test. |
| 5 | Paso 5 | **Opus 5** | **alto** | ❌ no | Buffer, copias, previews encadenados y repintado durante un modal: lo que se rompe sin que nada deje de compilar. Más la verificación de que la escritura existente cubre N. |
| 6 | Paso 6 | **Sonnet 5** | medio | ➖ opcional | Cierre: abre con `/code-review high`, que es el que trae el juicio. |

**Paralelizar:**
- Las **Sesiones 1, 2 y 3** no comparten archivos: el 2 toca `Constantes.Textos`, y el 3 toca modelos
  y `RegistrarEstadoController` (sólo `repintar`/encolado). Con `git worktree` se pueden correr a la
  vez, y hay que mergear las tres antes de la Sesión 4.
- El **Paso 1 del Plan 2** tampoco depende de nada de acá. Ojo: el Paso 5 de este plan y el Paso 1
  del Plan 2 agregan tests a los **mismos** tres archivos (`MaterialDAOTest`, `EquipoOtrosDAOTest` y
  `ConcurrenciaOptimistaTest`), así que al mergear hay que esperar conflictos, aunque sean triviales.
- Para una sola persona, secuencial sale igual de bien.

---

### Sesión 1 — Paso 1: el helper de selección

**Opus 5 · effort medio · sin fast mode**

```
Ejecutá el Paso 1 de plans/registrar-estado-avance-multiple.md (helper de selección acumulativa
en ui/common/seleccion: ReglasSeleccionAcumulativa + SeleccionAcumulativaTabla, con tests).

Antes de escribir leé del plan: "Decisiones tomadas con el usuario", el Paso 1 entero y el
"Catálogo de anti-patrones". Después leé ui/common/dnd/TableSelectionSupport.java y
src/test/java/com/example/ui/common/PanelHeaderTest.java (cómo se testea Swing headless acá).

Lo que no puede salir mal:
1. ReglasSeleccionAcumulativa no importa NADA de javax.swing ni java.awt: es la parte testeable;
2. Ctrl+↓/↑ SUMAN y NUNCA QUITAN. Hay que pisar el binding de JTable en
   WHEN_ANCESTOR_OF_FOCUSED_COMPONENT (selectNextRowChangeLead mueve el foco sin seleccionar),
   incluidas ctrl KP_DOWN / ctrl KP_UP;
3. la selección se vacía cuando el foco sale de la ZONA (tabla + exentos) en forma no temporal.
   Hace falta un FocusListener en la tabla Y OTRO EN CADA EXENTO: si no, después de pasar por el
   botón exento ningún click vacía la tabla. getOppositeComponent() puede ser null;
4. click, Ctrl+click, Shift y arrastre quedan como en Swing: no los reprogrames.

Ninguna pantalla usa el helper todavía. Terminá con mvn test en verde y el commit del criterio
de salida.
```

### Sesión 2 — Paso 2: el planificador

**Opus 5 · effort alto · sin fast mode**

```
Ejecutá el Paso 2 de plans/registrar-estado-avance-multiple.md (PlanificadorAvanceMultiple,
clase plana en equipos/common/controller/helpers, más sus textos en Constantes y su test).

Leé antes del plan: "Decisiones tomadas con el usuario", "Estado del que parte este plan" (sobre
todo "Filas infladas"), el Paso 2 entero y el "Catálogo de anti-patrones". Después leé
RegistrarEstadoController (actualizarTextoAvanzar y avanzarMaterialSeleccionado: es la lógica
que sacás de ahí), Equipo.aplicarMovimientoPreview + unificarEnMemoria,
EstadoValidatorImpl.esAvanzableManualmente y AgrupadorEntregasTest (el molde de test con mocks).

Lo que no puede salir mal:
1. el ORDEN de los chequeos: vacío → escritura en curso → tocada por un preview → estados
   distintos → no avanzable → avanzable. Hay un test que lo fija;
2. "tocada" = no persistida, O id en el buffer, O estado/cantidad distintos de la fila con el
   mismo id en el equipo ORIGINAL. El tercer caso es la fila que un preview infló sin estar en el
   buffer; sin él, avanzarla completa hace fallar el equipo entero en aplicarMovimientos;
3. "no avanzable" OCULTA el botón, como hoy; "bloqueado" lo deja visible y DESHABILITADO con el
   motivo;
4. el REMITO sin filas trae un material sintético con id 0, que cuenta como persistido.

No toques RegistrarEstadoController: lo cablea el Paso 5. Terminá con mvn test en verde y el commit.
```

### Sesión 3 — Paso 3: previews sobre copias

**Opus 5 · effort alto · sin fast mode**

```
Ejecutá el Paso 3 de plans/registrar-estado-avance-multiple.md (copiarParaPreview en los modelos,
SuperposicionPreviews, y RegistrarEstadoController aplicando los previews sobre copias). Es la
corrección de un bug existente: hoy Cancelar no revierte los previews.

Leé antes del plan: la fila "Previews sobre copias" de las decisiones, "Estado del que parte"
(el punto "Bug existente"), el Paso 3 y el catálogo de anti-patrones. Después leé el javadoc de
app/ui/DatosOperativos.java, UiCoordinator:280-295 (a quién se reparte el snapshot) y del
CLAUDE.md "Dos asimetrías que hay que preservar".

Lo que no puede salir mal:
1. la copia es PROFUNDA: equipo y cada material son objetos nuevos. Un test aplica un preview a
   la copia y verifica que el original no cambió. Una copia superficial compila y vuelve a mutar
   el snapshot;
2. EquipoOtros copia también lo del REMITO (tipo, remitoCantidad, remitoId), que alimenta el
   material sintético;
3. resetearCambios sigue SIN releer: descarta las copias y repinta el snapshot intacto. La
   asimetría de componentShown no cambia;
4. la superposición es por EquipoKey (tipo + id): ortopedias y otros tienen autoincrementales
   independientes.

Cerrá con el smoke de Cancelar, mvn test en verde y el commit.
```

### Sesión 4 — Paso 4: la vista

**Sonnet 5 · effort medio · fast mode sí**

```
Ejecutá el Paso 4 de plans/registrar-estado-avance-multiple.md (multi-selección opt-in en
PanelEquipoMaterial, API nueva de PantallaRegistrarEstado, diálogo "¿todos completos?" y
CantidadDialogHelper.pedirCantidadConTodos). Los Pasos 1 a 3 ya están commiteados.

Leé antes del plan: "Decisiones de diseño tomadas por el plan", el Paso 4 y el catálogo de
anti-patrones.

Tres cosas:
1. la multi-selección es OPT-IN: PantallaCorrecciones NO se toca y su tabla sigue en
   SINGLE_SELECTION (opera por índice en cinco lugares; con varias filas, Eliminar borraría el
   material equivocado). Hay un test que lo fija;
2. el exento es btnAvanzar: habilitarSeleccionMultipleMateriales va DESPUÉS de crearPanelBotones();
3. RespuestaAvanceCompleto va en features/equipos/common/model (la devuelve la vista y la consume
   el controller); getMaterialSeleccionadoIndex() NO se borra en este paso.

La lambda del check "Todos" desaparece de RegistrarEstadoController y de LotesController.
Terminá con mvn test en verde y el commit.
```

### Sesión 5 — Paso 5: el controller y la verificación de la escritura

**Opus 5 · effort alto · sin fast mode**

```
Ejecutá el Paso 5 de plans/registrar-estado-avance-multiple.md (RegistrarEstadoController con
PlanificadorAvanceMultiple, la cascada de diálogos, el flag de escritura en curso, y los tests que
prueban que Confirmar ya escribe N movimientos). Los Pasos 1 a 4 ya están commiteados.

Leé antes del plan: "Contexto compartido" (sobre todo la regla 8, las dos asimetrías), el Paso 5
entero con su pseudocódigo, y el "Catálogo de anti-patrones". Después leé del CLAUDE.md "Dos
asimetrías que hay que preservar" y "Por qué las tablas de detalle NO llevan columna version".

Lo que no puede salir mal, y nada lo va a delatar al compilar:
1. los movimientos se arman con los ids ANTES de aplicar ningún preview; los previews van todos
   DESPUÉS de encolar todo, y SOBRE LA COPIA (Paso 3), nunca sobre el snapshot;
2. cancelar cualquier diálogo de la cascada aborta TODO: nada entra al buffer;
3. después de CADA diálogo, si ultimoSnapshot cambió de referencia se aborta con
   AVANCE_PANTALLA_RELEIDA sin tocar el buffer (un modal despacha el done() de una TareaUI);
4. escrituraEnCurso se prende en el antes y se apaga en el despues de Confirmar: si no, un
   cambio de selección vuelve a prender Avanzar mientras se guarda;
5. NO se escribe ningún camino nuevo: Confirmar sigue en aplicarMovimientos. Lo que agregás son
   TESTS que prueban que ya cubre N movimientos y que un conflicto en uno revierte los N;
6. no toques componentShown, las guardas de refresco/volver ni finalizarConfirmacion.

El caso nuevo de ConcurrenciaOptimistaTest tiene la forma del resto: A lee → B modifica y
commitea → A escribe → conflicto, y el estado final es exactamente el de B.

Cerrá con los 9 puntos del smoke manual (SIN -Daptium.edt.strict=true; para salir de la tabla
con teclado es Ctrl+Tab), mvn test en verde y el commit.
```

### Sesión 6 — Paso 6: cierre

**Sonnet 5 · effort medio · fast mode opcional**

```
Ejecutá el Paso 6 de plans/registrar-estado-avance-multiple.md (revisión, cobertura,
documentación y cierre). Los Pasos 1 a 5 ya están commiteados.

Empezá por /code-review high sobre main..HEAD. Aplicá CRITICAL y HIGH; los MEDIUM que no toques
van anotados en el plan con el motivo. Después mvn verify con los umbrales del punto 2.

En CLAUDE.md y en la memoria van las decisiones que NO se deducen del código: la cascada aborta
todo, la regla de foco (Avanzar + pérdidas temporales, escuchando también al exento), el chequeo
de snapshot después de cada diálogo, y que los previews van sobre copias porque el snapshot es
compartido. Marcá el plan como CERRADO con los SHAs y commiteá.
```
