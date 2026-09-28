# Plan 2 — Para Entregar: entregar ALGUNOS materiales listos

**Objetivo:** en Equipos para Entregar se elige qué materiales listos entregar de una institución
(ortopedias) o de un cliente (otros), no necesariamente todos. Se entregan **filas enteras**. Un
REMITO se entrega siempre entero. De paso se corrige un **bug de concurrencia existente**: hoy la
entrega escribe lo que esté `ESTERILIZADO` en la base **en ese momento**, sin guarda contra lo que
el operador vio.

**Rama:** `AvanceMultipleYEntregaParcial` (la misma del Plan 1) · **Modo:** directo, un commit por
paso, sin PRs
**Fecha de creación:** 2026-09-25 · **Revisión adversarial:** hecha el 2026-09-25, aplicada en esta versión.
**Plan previo:** [`registrar-estado-avance-multiple.md`](registrar-estado-avance-multiple.md). Este
plan **reusa su helper de selección** (`ui/common/seleccion/`, Paso 1 de aquél) **sin redefinirlo**,
y su Paso 2 toca `RegistrarEstadoController` **después** del Paso 5 de aquél. El **Paso 1 de este
plan no depende de nada del Plan 1** y puede correr en paralelo.

> **✅ CERRADO — 2026-09-28.** Paso 1 `e0d3d1b` · Paso 2 `ac12e52` · Paso 3 `2742523` · Paso 4
> `2d5c7a0` · Paso 5: el commit `docs:` que agrega este bloque.
> `mvn verify` verde (1637 tests, 0 fallas/errores). Cobertura de instrucciones:
> `PlanificadorEntrega` 100 %, `AgrupadorEntregas` 98,3 %, `AplicadorPorPartes` 100 %.
>
> **`/code-review high` sobre `34b6576..2d5c7a0`: 0 CRITICAL, 0 HIGH.** Un hallazgo (limpieza
> incondicional del buffer de Registrar Estado tras una `DatabaseException` en una parte) se
> verificó a fondo y resultó ser exactamente la decisión de diseño explícita del Paso 2, no una
> regresión — detalle en la Tarea 1 del Paso 5, abajo. El resto de los hallazgos son MEDIUM/LOW de
> reuse y eficiencia, anotados con su motivo en el mismo lugar.

---

## Decisiones tomadas con el usuario

| Tema | Decisión |
|---|---|
| Unidad de entrega | **Filas enteras** de material. No se parte la cantidad de una fila. |
| REMITO | Se muestra como **una sola fila** (p. ej. `Remito 25092026-14 · Elementos × 30`). Seleccionarla es entregarlo entero. |
| Alcance del botón | **Varias** instituciones o clientes seleccionados: se entrega **todo** lo listo de todos, como hoy. **Uno** con materiales seleccionados: **sólo ésos**. **Uno** sin selección: **todo**. La confirmación lista siempre exactamente qué se entrega. |
| Teclas y foco | **Idénticos al Plan 1.** Ctrl+click, Ctrl+↓/↑ que suman, click que reemplaza, Shift y arrastre estándar. La selección se conserva si el foco va al botón **Entregar** o si la pérdida es temporal. |
| Columna Ingreso | **Se agrega.** Ortopedias: paciente + fecha de ingreso. Otros: fecha de ingreso, o el ID del remito. |
| Columna Entregado | **Se quita**: hoy dice siempre "No" (ver "Estado del que parte"). |
| Equipos incompletos | **Se muestran.** Las filas `ESTERILIZADO` de un equipo que todavía tiene otros materiales en proceso aparecen en la tabla, **marcadas como de un ingreso incompleto**, y se pueden entregar. Hoy el camino viejo las entregaba sin mostrarlas. |
| Error técnico en un destino | El loop por partes, generalizado y compartido con Registrar Estado, **lo cuenta como error y sigue** con los demás; al final se relee y se informa lo que sí pasó. Esto **también cambia Registrar Estado**: hoy un fallo técnico en el equipo 2 de 3 oculta que el 1 ya se escribió. |

### Decisiones de diseño tomadas por el plan (con su porqué)

| Tema | Decisión | Por qué |
|---|---|---|
| **Una sola ruta de escritura**, por id | `MaterialDAO.entregarMateriales(List<FilaAEntregar>)` y `EquipoOtrosDAO.entregar(List<FilaAEntregar>, List<RemitoAEntregar>)`. "Todo" es la misma llamada con todas las filas del destino. **`entregarInstitucionCompleta` y `entregarClienteCompleto` se borran.** | Dos caminos son dos guardas que mantener. Y al viejo no se le puede poner guarda: no recibe qué vio el operador. |
| La guarda es CAS sobre **`estado`**, más **`cantidad`** como defensa, en un solo `UPDATE` | `… WHERE id = ? AND equipo_id = ? AND estado = 'Esterilizado' AND cantidad = ?` + `ControlConcurrencia.exigirFilaAfectada` | `estado` detecta el choque real: otro ya lo entregó. `cantidad` es **defensiva**: hoy ninguna ruta de la UI cambia la cantidad de una fila `ESTERILIZADO`, porque Correcciones sólo modifica equipos en `NUEVO` y los lotes toman `EMPAQUETADO`. Pero cuesta una columna en un `WHERE` y fija el contrato "se entrega exactamente la fila que se vio". No da falsos positivos, por lo mismo. **No** va sobre `version` (política de `CLAUDE.md`). |
| Sin `SELECT … FOR UPDATE` previo | El CAS es un único `UPDATE` | La regla "la relectura que alimenta la guarda va `FOR UPDATE`" es para guardas que **comparan en Java** lo leído. Un `UPDATE` con la condición en el `WHERE` hace una *lectura actual* y bloquea la fila en el mismo acto, también bajo el `REPEATABLE READ` de MySQL. |
| Filas ordenadas por `(equipoId, materialId)` antes de escribir | El DAO ordena su entrada | Dos entregas concurrentes con filas en común bloquean en el mismo orden, y no se cruzan en un deadlock. **H2 no lo delata.** La revisión verificó que el orden es compatible con `aplicarMovimientos` y con `LoteDAO`. |
| La contención de locks es **conflicto**, no error | `SQLException` con `ControlConcurrencia.esContencionDeLock(e)` → `ConflictoConcurrenciaException(CONFLICTO_ENTREGA)` | Si dos entregas chocan sobre la misma fila, MySQL puede cortar la espera con 1205 o 1213. Eso es "otro se te adelantó", y tiene que salir como tal, no como error técnico. Es la misma regla que ya siguen las guardas del lavadero. |
| El conflicto aborta **el destino entero** | Una transacción por institución/cliente | La confirmación se lee por destino ("Institución X: …"), y el resultado se informa por destino, como hoy. Una entrega a medias de un destino no se parece a ninguna de las dos cosas que el operador confirmó. |
| REMITO sin filas reales: CAS sobre el encabezado, mínimo | `UPDATE equipo_otros SET estado = 'Entregado', version = version + 1 WHERE id = ? AND estado = 'Esterilizado' AND NOT EXISTS (SELECT 1 FROM equipo_otros_materiales WHERE equipo_otros_id = ?)` | Con los flujos actuales **no se llega** a un REMITO sin filas en `ESTERILIZADO`: todo movimiento de un remito, sea por `aplicarMovimientos` o por `LoteDAO`, lo materializa en filas (`materializarRemitoSplit` siempre inserta). La rama existe **sólo por datos viejos**, y se mantiene simple. 0 filas afectadas ahora **es** conflicto (hoy es un "skip"). El bump manual se queda porque esta rama no pasa por `recalcularEstadoEquipo`. |
| `MaterialEntregaItem` pasa a `record`, y dice **qué** entregar | `(String ingreso, String material, int cantidad, List<FilaAEntregar> filas, List<RemitoAEntregar> remitos)` — exactamente una de las dos listas no vacía | El controller no calcula: arma la solicitud concatenando lo que ya traen los ítems. Un ítem agrupa por código (o descripción) **todas** las filas `ESTERILIZADO` de ese código en ese equipo. Pueden ser **varias**: `unificarMaterialesDuplicados` agrupa también por `lote_id`, así que conviven filas del mismo código que vienen de lotes distintos. |
| Los incompletos se **marcan** | `ingreso` lleva el sufijo `Textos.INGRESO_INCOMPLETO` (p. ej. `" · incompleto"`) cuando el equipo no está todo listo | Sin marca, el operador entrega medio equipo sin enterarse: la tabla le mostraría como "lo listo" algo que no está completo. Es el mismo argumento que "un cartel que miente". |
| Un REMITO aparece **sólo completo** | Si alguna de sus filas no está `ESTERILIZADO`, el remito no se muestra | Se entrega siempre entero. Mostrar un remito incompleto obligaría a entregarlo por partes o a mostrar una fila que no se puede entregar. |
| La decisión de qué entregar y el texto de la confirmación van a una clase plana | `PlanificadorEntrega` (junto a `AgrupadorEntregas`) | Hoy `construirMensajeConfirmacion` y el filtrado `!m.isEntregado()` están en el controller. Además la confirmación actual **fusiona por nombre** (`resumen.merge(item.getMaterial(), …)`): dos "Tornillo" de ingresos distintos salen como uno. Con selección parcial eso ya no alcanza. |
| La confirmación se muestra con **scroll** | `JTextArea` no editable dentro de un `JScrollPane` de tamaño acotado (constantes en `Estilos.Dimensiones`), en el `JOptionPane` | "Varios destinos = todo" con una línea por ítem puede dar cientos de líneas. Un `JOptionPane` con un `String` así crece más que la pantalla y deja los botones afuera. |
| El loop por partes se **generaliza** | `AplicadorMovimientosPendientes` pasa a ser `AplicadorPorPartes<K, V>`, con tres resultados: ok, conflicto (`ConflictoConcurrenciaException`) y error (`false` **o** `DatabaseException`, logueada) | Entregas necesita exactamente el mismo loop. Otras `RuntimeException` (bugs, validaciones) **siguen propagando**: no son "una parte que falló". |
| Se relee **siempre** después de entregar | `solicitarRefresco` aunque nada haya salido bien | Hoy sólo relee si hubo alguna exitosa. Un conflicto significa justamente que la pantalla quedó vieja ("se avisa y se recarga"). |
| El texto del botón no cambia | Sigue "Entregar Institución" | La fuente de verdad es la confirmación, que lista exactamente lo que se entrega. |

---

## Contexto compartido (leer una vez por sesión)

App de escritorio **Swing, Java 17, Maven**, sin framework de DI. Capas por feature:
`model → dao → service → view/controller`. Todo se cablea a mano en `AppContext` y `UiCoordinator`.

### Reglas duras del repo que este plan debe respetar

1. **Ningún acceso a BD en el EDT.** La entrega corre en `TareaUI`, como hoy.
2. **El estado mutable del controller sólo en el EDT** (`materialesPorDestino`, `volumenPorDestino`).
3. **Toda escritura que dependa de un dato leído lleva guarda y mira filas afectadas.**
   `0 filas` = *"la realidad ya no es la que viste"*: se aborta la transacción entera, se avisa y se
   recarga. **Sin reintento automático.**
4. **La guarda de una tabla de detalle va sobre el campo que se consume, no sobre `version`.** El
   `recalcularEstadoEquipo` **bumpea** `version` (es un camino derivado); la entrega no la **usa**
   como guarda.
5. **Movimientos y recálculo dentro de la misma transacción** que el cambio de estado.
6. **Los services no tienen JDBC.** Validan y delegan.
7. **Lógica de decisión embebida en Swing → clase plana sin Swing, con tests.** Los controllers
   orquestan y no calculan.
8. **Textos para el operador en `Constantes`.** Los mensajes de conflicto dicen **qué cambió y qué
   hacer**, no qué falló.
9. **Un conteo de guarda nunca sale de `executeBatch()`** (devuelve `SUCCESS_NO_INFO` con
   `rewriteBatchedStatements=true`): cada CAS va con `executeUpdate()`.
10. **El snapshot (`DatosOperativos`) no se muta.** El Plan 1 lo dejó así (previews sobre copias);
    el agrupador sólo lee.

### Estado del que parte este plan (verificado contra el código, 2026-09-25)

- **El bug de concurrencia.** `MaterialDAO.entregarInstitucionCompleta(nroInstitucion)`
  (`MaterialDAO:218-279`) y `EquipoOtrosDAO.entregarClienteCompleto(nroCliente)` (`:496-571`)
  reciben **sólo el destino**, releen `… WHERE estado = esterilizado FOR UPDATE` y entregan lo que
  encuentran. Un material que se esteriliza después de la lectura de la pantalla se entrega sin haber
  aparecido en la confirmación.
- **Entregas invisibles.** `AgrupadorEntregas` muestra un equipo sólo si
  `esEntregable(equipo.calcularEstado())`, es decir, si su material **más atrasado** ya está
  `ESTERILIZADO`. Pero `entregarInstitucionCompleta` entrega lo `ESTERILIZADO` de **todos** los
  equipos de la institución. Un equipo con un material `ESTERILIZADO` y otro `LAVADO` **no aparece**,
  y su material esterilizado **se entrega igual**. El usuario decidió que esas filas **se muestren**
  (marcadas), así que la entrega por id deja de ser invisible.
- **El estado se guarda como `EstadoEquipo.getNombre()`** (`"Esterilizado"`, `"Entregado"`): todos los
  escritores usan `getNombre()`, y ninguna migración siembra minúsculas. El
  `LOWER(estado) = 'esterilizado'` de `entregarInstitucionCompleta` es defensivo, no un síntoma. El
  Paso 1 lo **re-verifica** antes de escribir el CAS.
- `entregarClienteCompleto` **se traga la excepción** y devuelve `false`, mientras que
  `entregarInstitucionCompleta` lanza `DatabaseException`. El controller los trata distinto sin
  saberlo. La ruta nueva lanza en los dos casos.
- **Correcciones sólo modifica equipos en `NUEVO`** (`EquipoCorreccionService:69,108,226,301`;
  `EquipoOtrosCorreccionService:23-24,66-73`), y `remito_cantidad` sólo se corrige sin movimientos.
  Por eso `cantidad` en el CAS es defensiva, no un caso real.
- **La tabla de materiales no muestra filas entregadas**, aunque su columna sugiera lo contrario:
  `AgrupadorEntregas` construye siempre `new MaterialEntregaItem(…, false)` y saltea los grupos
  `todosEntregados()`. La columna "Entregado" dice siempre "No", y el `filter(m -> !m.isEntregado())`
  del controller es código muerto.
- La tabla de materiales está `setEnabled(false)` (`PantallaEquiposParaEntregar:75`), centra la
  **columna 1** (`:74`, que hoy es Cantidad) y se llena sólo con **exactamente una** institución
  seleccionada (`crearListenerSeleccionInstitucion`: con 0 o ≥ 2 llama
  `onInstitucionSeleccionada(null)` → `limpiarMateriales`).
- `MaterialEntregaTableModel` usa `getMaterial()`, `getCantidad()` e `isEntregado()` (cerca de la
  línea 41). Pasar el ítem a `record` lo rompe: el Paso 3 lo adapta.
- `AgrupadorEntregas` concatena los materiales de **todos** los equipos del destino, agrupados por
  código (ortopedias) o descripción (otros) **dentro de cada equipo**. Dos ingresos distintos dan dos
  filas idénticas.
- **REMITO:** sin filas reales, `AgrupadorEntregas` rinde una fila `"Elementos" × remitoCantidad`. Con
  filas son filas `"Elementos"` agrupadas por descripción. El literal `"Elementos"` aparece además en
  el SQL de `materializarRemitoSplit`, en `LoteDAO` y en `EquipoOtrosDAO`.
- `pintar` repinta y hace `limpiarMateriales()`: un refresco vacía la tabla, y con ella la selección.
- En una `JTable` con varias filas seleccionadas, **Tab recorre celdas dentro de la selección**; para
  salir con teclado es **Ctrl+Tab**.
- La jerarquía de excepciones real es `ApplicationException` → `BusinessException`
  (→ `ConflictoConcurrenciaException`), `DatabaseException`, `ValidationException` y
  `ResourceNotFoundException`. **No existe `DataAccessException`**, y la raíz no es
  `AptiumException` como dice `CLAUDE.md`. El Paso 5 lo corrige.
- Tests existentes de las rutas viejas: `MaterialDAOTest:249+`, `EquipoOtrosDAOTest:191+` y `:315`
  (`entregarClienteCompleto_remitoSinFilas_bumpeaLaVersion`), y `EquipoOtrosServiceTest:126-131`.
  **No existe `MaterialServiceTest`**: el Paso 1 lo crea. **No hay test de
  `EquiposParaEntregarController`.**

### Archivos de referencia

| Para | Leer |
|---|---|
| Controller | `features/equipos/common/controller/EquiposParaEntregarController.java` |
| Agrupación (clase plana) | `features/equipos/ortopedias/controller/helpers/AgrupadorEntregas.java` + su test |
| Pantalla | `features/equipos/ortopedias/view/PantallaEquiposParaEntregar.java`, `MaterialEntregaTableModel` |
| Escritura vieja (a borrar) | `MaterialDAO.entregarInstitucionCompleta`, `EquipoOtrosDAO.entregarClienteCompleto` |
| CAS + filas afectadas + contención | `common/dao/ControlConcurrencia.java` |
| Transacción | `TransactionalConnection` (y cómo lo usa `MaterialDAO.aplicarMovimientos`) |
| Recálculo + bump | `EquipoMaterialHelper.recalcularEstadoEquipo`, `EquipoOtrosMaterialHelper.recalcularEstadoEquipo` |
| Loop por partes a generalizar | `common/controller/helpers/AplicadorMovimientosPendientes` + su test |
| Forma de los tests de concurrencia | `infrastructure/db/ConcurrenciaOptimistaTest` (`registrarEstadoOrtopedias`, `registrarEstadoOtros`) |
| Helper de selección (NO redefinir) | `ui/common/seleccion/` — Paso 1 del Plan 1 |

### Comandos

```bash
mvn test
mvn verify
mvn test -Dtest=NombreDeClase
mvn clean package && java -jar target/aptium.jar   # smoke manual (SIN -Daptium.edt.strict=true)
```

---

## Grafo de dependencias

```
Paso 1 (escritura por id con CAS: modelos, DAOs, services, ConcurrenciaOptimistaTest)
   │         ← NO depende del Plan 1: puede correr en paralelo con él
   │
   ├──► Paso 3 (AgrupadorEntregas + MaterialEntregaItem + PlanificadorEntrega)
   │
Plan 1 Paso 5 ──► Paso 2 (AplicadorPorPartes<K,V>; RegistrarEstadoController se adapta)
   │                                                        │
   │    Plan 1 Paso 1 (helper de selección)                 │
   │                  │                                     │
   └──────────────────┴──── 1 + 2 + 3 ──────────────────────┴──► Paso 4 (pantalla + controller;
                                                                          se borran las rutas viejas)
                                                                             │
                                                                             ▼
                                                               Paso 5 (revisión, docs, cierre)
```

| Paso | Modelo sugerido | Archivos que toca |
|---|---|---|
| 1 | **Opus**, alto | `common/model/FilaAEntregar`, `RemitoAEntregar` (nuevos), `MaterialDAO`, `EquipoOtrosDAO`, `MaterialService`, `EquipoOtrosService`, `Constantes.Mensajes`, tests de DAO/service (`MaterialServiceTest` nuevo), `ConcurrenciaOptimistaTest` |
| 2 | **Opus**, medio | `AplicadorMovimientosPendientes` → `AplicadorPorPartes`, `RegistrarEstadoController` (sólo `confirmarCambios`/`finalizarConfirmacion`), su test |
| 3 | **Opus**, medio | `AgrupadorEntregas`, `MaterialEntregaItem`, `MaterialEntregaTableModel` (sólo los accesores), `PlanificadorEntrega` (nuevo), `EquiposParaEntregarController` (sólo para compilar), `Constantes`, tests |
| 4 | **Sonnet**, alto | `PantallaEquiposParaEntregar`, `MaterialEntregaTableModel`, `EquiposParaEntregarController`, `PlanificadorEntrega` (texto del resultado), `TableStyler`, `Constantes`, `Estilos`, borrado de los métodos viejos y sus tests |
| 5 | Sonnet, medio | `CLAUDE.md`, memoria, este archivo |

**Invariantes verificados después de CADA paso:**
- [ ] `mvn test` en verde
- [ ] Toda escritura nueva tiene CAS y mira filas afectadas con `executeUpdate()`
- [ ] Ninguna escritura usa `version` como guarda
- [ ] Cero JDBC en services · cero I/O en el EDT
- [ ] El helper de selección del Plan 1 **no se modifica**; si hiciera falta un cambio, va en su plan

---

## Paso 1 — La escritura por id con CAS

### Contexto (autocontenido)

Hoy la entrega recibe sólo el destino y entrega lo que haya `ESTERILIZADO` al momento de escribir.
Este paso crea la ruta que recibe **exactamente las filas que se vieron**, con la cantidad que se vio,
y aborta si cualquiera cambió. Todavía nadie la llama: el controller se cambia en el Paso 4 y las
rutas viejas se borran ahí.

### Tareas

1. **Re-verificar cómo se guarda el estado** antes de escribir el CAS:
   - `grep` de todos los `UPDATE`/`INSERT` sobre `equipo_materiales.estado`,
     `equipo_otros_materiales.estado` y `equipo_otros.estado` en `src/main`: todos tienen que escribir
     `EstadoEquipo.X.getNombre()`;
   - si hay una base MySQL de desarrollo a mano, además
     `SELECT estado, COUNT(*) FROM equipo_materiales GROUP BY estado`, y lo mismo en las otras dos.

   **Si aparece un valor en otra capitalización, PARAR y preguntar.** Normalizar con una migración
   nueva o comparar sin distinguir mayúsculas es una decisión que no corresponde a este paso.
2. **Modelos** en `common/model/` (junto a `EquipoKey` y `EntregaDestinoKey`):
   - `record FilaAEntregar(int equipoId, int materialId, int cantidadVista)`, que valida
     `cantidadVista > 0`;
   - `record RemitoAEntregar(int equipoOtrosId)`.
3. **`MaterialDAO.entregarMateriales(List<FilaAEntregar> filas)`**, con `TransactionalConnection`:
   - ordena por `(equipoId, materialId)`;
   - por fila: `UPDATE equipo_materiales SET estado = ? WHERE id = ? AND equipo_id = ? AND estado = ?
     AND cantidad = ?` con `executeUpdate()` → `ControlConcurrencia.exigirFilaAfectada(n,
     Mensajes.CONFLICTO_ENTREGA)`. Después, un `INSERT` en `material_movimientos` con
     `cantidadVista`, origen `ESTERILIZADO` y destino `ENTREGADO`;
   - por cada equipo distinto, en orden: `EquipoMaterialHelper.recalcularEstadoEquipo` (bumpea `version`);
   - `commit`;
   - `SQLException`: si `ControlConcurrencia.esContencionDeLock(e)`, se convierte en
     `ConflictoConcurrenciaException(CONFLICTO_ENTREGA)`; si no, en `DatabaseException`;
   - `ConflictoConcurrenciaException` sale tal cual, y el `close()` sin commit revierte
     (`TransactionalConnection`, verificado en la revisión). Un test lo prueba igual;
   - **no** llama a `unificarMaterialesDuplicados`: el camino viejo tampoco lo hacía, y fusionar filas
     `ENTREGADO` movería ids que ya referencia `material_movimientos`;
   - javadoc: el porqué del CAS y de la columna defensiva, por qué no hay `FOR UPDATE` previo, por qué
     se ordena y por qué la contención es conflicto.
4. **`EquipoOtrosDAO.entregar(List<FilaAEntregar> filas, List<RemitoAEntregar> remitosSinFilas)`**,
   con `TransactionalConnection`, **no** con el `rollback`/`close` manual del método viejo, que se
   traga la excepción:
   - filas: el mismo CAS sobre `equipo_otros_materiales` (`equipo_otros_id`), el movimiento en
     `otros_material_movimientos` (reusar `registrarMovimiento`) y el recálculo por equipo;
   - remitos: el `UPDATE` con `NOT EXISTS` de la tabla de decisiones → `exigirFilaAfectada`. Sin
     movimiento, porque no hay `material_id`, igual que hoy. Javadoc: se llega sólo con datos viejos;
   - orden: filas (ordenadas), después remitos (ordenados por id), después los recálculos;
   - la misma conversión de la contención de locks.
5. **Services** (sin JDBC, validan y delegan):
   - `MaterialService.entregarMateriales(List<FilaAEntregar>)`;
   - `EquipoOtrosService.entregar(List<FilaAEntregar>, List<RemitoAEntregar>)`.

   Los dos lanzan `ValidationException` (builder) si no hay nada para entregar, y devuelven `true` al
   terminar, que es lo que espera la `Operacion` del Paso 2.
6. **`Constantes.Mensajes.CONFLICTO_ENTREGA`**: qué cambió y qué hacer. Por ejemplo: `"Otro operador
   cambió alguno de estos materiales mientras preparaba la entrega. No se entregó nada de este
   destino: revise la lista actualizada y vuelva a intentar."`
7. **Tests.** Para "B cambia la fila" se usa **SQL directo** (`ejecutarSinChecked("UPDATE …")`, como
   el resto de `ConcurrenciaOptimistaTest`), salvo cuando B es una entrega.
   - `MaterialDAOTest`:
     - `entregarMateriales_filaEsterilizada_quedaEntregadaConSuMovimiento`;
     - `entregarMateriales_noTocaUnMaterialEsterilizadoQueNoEstabaEnLaSolicitud`, que es **el bug**;
     - `entregarMateriales_filaYaEntregada_conflictoYNoEscribeNada`;
     - `entregarMateriales_cantidadDistintaALaVista_conflicto`, **defensivo**, con la cantidad
       cambiada por SQL;
     - `entregarMateriales_segundaFilaEnConflicto_revierteLaPrimera`;
     - `entregarMateriales_equipoIncompleto_entregaSoloLaFilaPedidaYElEquipoQuedaEnProceso`;
     - `entregarMateriales_recalculaElEstadoYBumpeaLaVersionDelEquipo`.
   - `EquipoOtrosDAOTest`:
     - los equivalentes;
     - `entregar_remitoSinFilas_entregaElEncabezadoYBumpea`;
     - `entregar_remitoSinFilasYaEntregado_conflicto`;
     - `entregar_remitoConFilas_seEntreganSusFilas`.
   - `MaterialServiceTest` (**nuevo**) y `EquipoOtrosServiceTest`: `entregar…_sinNada_lanzaValidationException`.
   - **`ConcurrenciaOptimistaTest`**, con la forma del resto (*A lee → B modifica y commitea → A
     escribe → conflicto, y el estado final es exactamente el de B*):
     - `entregaOrtopediasYaEntregadaPorOtro`: B entrega la fila con `entregarMateriales`, A choca y
       queda **un** movimiento a `Entregado`, el de B;
     - `entregaOtrosYaEntregadaPorOtro`;
     - `entregaNoIncluyeLoEsterilizadoDespuesDeLeer`: A lee una fila; B esteriliza **otra** del mismo
       destino (SQL directo: `UPDATE equipo_materiales SET estado = 'Esterilizado' WHERE id = ?`) y
       commitea; A entrega. **No es conflicto**: se entrega sólo la de A y la de B queda
       `ESTERILIZADO`. Es el test del bug, con la forma de los demás.

### Verificación

```bash
mvn test -Dtest='MaterialDAOTest,EquipoOtrosDAOTest,ConcurrenciaOptimistaTest,MaterialServiceTest,EquipoOtrosServiceTest'
mvn test
```

### Criterio de salida

- [ ] Todos los tests nombrados en verde; `mvn test` en verde
- [ ] Cada CAS con `executeUpdate()` + `exigirFilaAfectada`; ninguno con `executeBatch()`
- [ ] Ninguna guarda nueva usa `version`
- [ ] Los métodos viejos **siguen** existiendo (se borran en el Paso 4, junto con su llamador)
- [ ] Commit: `fix: entrega por id de material con guarda de estado`

---

## Paso 2 — `AplicadorPorPartes<K, V>`: un solo loop de "escribir por partes"

### Contexto (autocontenido)

`AplicadorMovimientosPendientes` (Registrar Estado) corre una operación por equipo y separa los
choques de concurrencia de los fallos. La entrega necesita el mismo loop por destino. Se generaliza
en vez de copiarlo.

**La semántica nueva la decidió el usuario:** una `DatabaseException` en una parte **se cuenta como
error de esa parte y se sigue** con las demás. Hoy corta el loop y va a `siFalla`: las partes que ya
se escribieron no se informan ni se releen. En Registrar Estado, un fallo técnico en el equipo 2 de 3
deja hoy el buffer entero; el operador reintenta y el equipo 1 le choca contra sí mismo.

Requiere el **Paso 5 del Plan 1** commiteado (los dos tocan `RegistrarEstadoController`).

### Tareas

1. Renombrar y generalizar `AplicadorMovimientosPendientes` → **`AplicadorPorPartes`** (mismo
   paquete, `equipos/common/controller/helpers/`):
   - `@FunctionalInterface interface Operacion<K, V> { boolean aplicar(K parte, V datos); }`;
   - `record Resultado<K>(List<K> exitosas, List<K> conError, List<K> conConflicto)`, con copias
     defensivas, `todosExitosos()` y `algunaExitosa()`;
   - `static <K, V> Resultado<K> aplicarTodos(Map<K, V> partes, Operacion<K, V> operacion)`, que
     recorre en el orden del mapa (el llamador pasa un `LinkedHashMap`). Cómo clasifica cada parte:
     - `true` → exitosa;
     - `false` → error;
     - `ConflictoConcurrenciaException` → conflicto;
     - `DatabaseException` → error, con un `log.error` que nombra la parte;
     - **cualquier otra `RuntimeException` propaga**: una `ValidationException` o un NPE son bugs, no
       "una parte que falló". `catch` explícitos, en ese orden.
   - Javadoc: el porqué de cada categoría, y por qué el loop no corta.
2. **`RegistrarEstadoController`**: `confirmarCambios` pasa el mismo
   `LinkedHashMap<EquipoKey, List<MovimientoMaterial>>` de hoy. `finalizarConfirmacion` lee
   `conError` y `conConflicto` como `List<EquipoKey>` y los nombra por `getId()`, como hoy. **Nada más
   cambia**: sigue limpiando el buffer y las copias, y pidiendo el refresco en los dos casos.
3. **Test:** `AplicadorMovimientosPendientesTest` → `AplicadorPorPartesTest`, con todos sus casos
   migrados, más:
   - `databaseExceptionEnUnaParte_seCuentaComoErrorYSigueConLasDemas`;
   - `otraRuntimeException_propaga`;
   - `exitosas_enElOrdenEnQueSeIntentaron`.
4. **Smoke** de Registrar Estado: confirmar un avance sigue funcionando.

### Verificación

```bash
mvn test -Dtest=AplicadorPorPartesTest
mvn test
```

### Criterio de salida

- [ ] `AplicadorMovimientosPendientes` ya no existe; cero referencias
- [ ] `mvn test` en verde
- [ ] Commit: `refactor: aplicador por partes generico, compartido por registrar estado y entregas`

---

## Paso 3 — Qué muestra la tabla y qué se entrega: `AgrupadorEntregas` + `PlanificadorEntrega`

### Contexto (autocontenido)

Para que el controller no calcule, cada fila de la tabla tiene que llevar **qué** se entrega si se
la elige. Y la decisión "¿qué entrego con esta selección?" tiene que ser una función pura, con tests.

Requiere el Paso 1 commiteado (usa `FilaAEntregar` y `RemitoAEntregar`).

### Tareas

1. **`MaterialEntregaItem` → `record MaterialEntregaItem(String ingreso, String material, int cantidad,
   List<FilaAEntregar> filas, List<RemitoAEntregar> remitos)`**.
   - El constructor compacto valida que **exactamente una** de las dos listas no esté vacía, y hace
     copias defensivas.
   - El campo `entregado` desaparece.
   - **Para que el build no se rompa**, `MaterialEntregaTableModel` pasa en este paso a los accesores
     del record (`material()`, `cantidad()`), y su columna 2 devuelve `false` hasta que el Paso 4 la
     quite. `EquiposParaEntregarController` se toca sólo lo necesario para compilar: se borra el
     `filter(!isEntregado())` y `construirMensajeConfirmacion` usa los accesores nuevos.
2. **`AgrupadorEntregas`**:
   - **Qué equipos entran (cambia, por decisión del usuario):** ortopedias y otros DETALLES entran si
     tienen **al menos una** fila `ESTERILIZADO`, estén completos o no. Un REMITO entra **sólo** si
     todo él está `ESTERILIZADO`, como hoy.
   - **Ortopedias:** por equipo, agrupar por código **sólo las filas `ESTERILIZADO`**. Cada grupo es
     un ítem:
     - `cantidad` = la suma de las filas;
     - `filas` = una `FilaAEntregar(equipo.getId(), material.getId(), material.getCantidad())` por
       fila. Puede haber **varias** del mismo código si vienen de lotes distintos.

     Las `ENTREGADO` ya no participan: el pendiente de antes (total − entregado, sobre las
     entregables) es exactamente esa suma.
   - **Otros DETALLES:** lo mismo, agrupando por descripción.
   - **REMITO:** **un** ítem por equipo.
     - Sin filas reales: `remitos = [RemitoAEntregar(id)]`, con `cantidad = remitoCantidad`.
     - Con filas: `filas` = todas sus filas, con `cantidad` = la suma.
     - `material` = `Textos.MATERIAL_REMITO` (`"Elementos"`). Esta constante reemplaza los literales
       de Java en `AgrupadorEntregas` y en `EquipoOtros`, **no** los del SQL (`materializarRemitoSplit`,
       `LoteDAO`, `EquipoOtrosDAO`), que son datos persistidos.
   - **`ingreso`** (el texto de la columna nueva), en un método privado por tipo:
     - ortopedias: `"<paciente> · <fecha>"`, o sólo la fecha si no hay paciente;
     - otros DETALLES: `"<fecha>"`;
     - REMITO: `String.format(Textos.INGRESO_REMITO, remitoId)`, con `INGRESO_REMITO = "Remito %s"`.

     Si el equipo **no** está completo (`!esEntregable(equipo.calcularEstado())`), se agrega el sufijo
     `Textos.INGRESO_INCOMPLETO`. La fecha usa un formato de `Constantes.Formatos`; si no hay uno de
     sólo fecha, agregar `FORMATO_FECHA = "dd/MM/yyyy"`. Sin fecha va `Textos.SIN_DATO`, o el
     equivalente que ya exista.
   - No cambian: el orden de los destinos y el volumen por cliente.
   - El `SIN_CLIENTE` privado se reemplaza por una constante de `Constantes.Textos`, si no existe ya.
3. **`PlanificadorEntrega`** (nuevo, junto a `AgrupadorEntregas`, sin Swing):
   - `record SolicitudEntrega(EntregaDestinoKey destino, String nombreDestino,
     List<MaterialEntregaItem> items)` con `List<FilaAEntregar> filas()` y
     `List<RemitoAEntregar> remitos()`, que concatenan los de sus ítems.
   - `sealed interface ResultadoPlan` = `Rechazo(String mensaje)` | `Plan(LinkedHashMap<EntregaDestinoKey,
     SolicitudEntrega> solicitudes, String textoConfirmacion)`. El mapa va listo para
     `AplicadorPorPartes`, en el orden de la tabla.
   - `ResultadoPlan planificar(List<InstitucionEntregaItem> destinosSeleccionados,
     Map<EntregaDestinoKey, List<MaterialEntregaItem>> materialesPorDestino,
     List<MaterialEntregaItem> materialesSeleccionados)`:
     - sin destinos → `Rechazo(Mensajes.ENTREGA_SELECCIONE_DESTINO)`;
     - **varios** → todo lo de cada uno. `materialesSeleccionados` se ignora: con ≥ 2 destinos la
       tabla está vacía, y el javadoc lo dice;
     - **uno** con selección → sólo los seleccionados; **uno** sin selección → todo;
     - los destinos sin nada se excluyen; si no queda ninguno → `Rechazo(Mensajes.ENTREGA_SIN_PENDIENTES)`.
   - `textoConfirmacion`: por destino, su nombre y **una línea por ítem**
     (`"  • <ingreso> — <material> × <cantidad>"`), **sin fusionar por nombre**. La plantilla va en
     `Constantes.Mensajes`. Los tres literales del controller pasan a `Constantes`: `"Debe
     seleccionar al menos una institución…"`, `"Las instituciones seleccionadas no tienen…"` y
     `"¿Confirmar entrega de los siguientes materiales?"`.
4. **Tests:**
   - `AgrupadorEntregasTest` adaptado, más:
     - `ortopedias_filaTraeIdsYCantidadDeLasFilasEsterilizadas`;
     - `ortopedias_filasEntregadasNoParticipan`;
     - `ortopedias_filasDeLotesDistintos_unItemConVariasFilas`;
     - `equipoIncompleto_muestraSusFilasEsterilizadasMarcadas`;
     - `remitoIncompleto_noSeMuestra`;
     - `dosIngresosMismaInstitucion_mismoMaterial_dosItemsConIngresoDistinto`;
     - `remitoSinFilas_unItemConRemitoYSinFilas`;
     - `remitoConFilas_unSoloItemConTodasSusFilas`;
     - `ingresoOrtopediasSinPaciente_soloFecha`.
   - `PlanificadorEntregaTest`:
     - `sinDestino_rechaza`;
     - `variosDestinos_entregaTodoDeCadaUnoEIgnoraLaSeleccion`;
     - `unDestinoConSeleccion_soloLoSeleccionado`;
     - `unDestinoSinSeleccion_todo`;
     - `destinoSinPendientes_seExcluye`;
     - `ningunoConPendientes_rechaza`;
     - `remitoSeleccionado_seEntregaEntero`;
     - `solicitud_concatenaFilasYRemitosDeSusItems`;
     - `solicitudes_enElOrdenDeLosDestinos`;
     - `textoConfirmacion_unaLineaPorItemSinFusionarIngresosDistintos`.

### Verificación

```bash
mvn test -Dtest='AgrupadorEntregasTest,PlanificadorEntregaTest'
mvn test
```

### Criterio de salida

- [ ] Tests en verde; cobertura de `PlanificadorEntrega` y `AgrupadorEntregas` ≥ 90 %
- [ ] Ninguna de las dos clases importa Swing
- [ ] `mvn test` en verde (el build no se rompió: `MaterialEntregaTableModel` y el controller adaptados a lo mínimo)
- [ ] Commit: `feat: agrupador y planificador de entregas por fila de material`

---

## Paso 4 — La pantalla y el controller; las rutas viejas se borran

### Contexto (autocontenido)

Se prende la tabla de materiales con el **helper de selección del Plan 1** (se usa, **no se
redefine**), se agrega la columna Ingreso, y el controller pasa a orquestar: planifica, confirma,
entrega por partes, informa y relee. Con eso las dos rutas viejas quedan sin llamador y se borran.

Requiere los Pasos 1, 2 y 3 de este plan, y el **Paso 1 del Plan 1** (`ui/common/seleccion/`).

### Tareas

1. **`PantallaEquiposParaEntregar`**:
   - quitar `tablaMateriales.setEnabled(false)`;
   - `SeleccionAcumulativaTabla.instalar(tablaMateriales, btnEntregarInstitucion)` **después** de
     crear el botón;
   - `List<MaterialEntregaItem> getMaterialesSeleccionados()` vía `TableSelectionSupport.selectedItems`
     y un `getItemAt(int)` nuevo en `MaterialEntregaTableModel`;
   - quitar el renderer de la columna 2 (`createEntregadoRenderer`), y pasar el
     `TableStyler.centerColumns(tablaMateriales, 1)` a la **columna 2** (Cantidad, en el orden nuevo);
   - `boolean confirmarConDetalle(String detalle, String titulo)`: un `JTextArea` no editable en un
     `JScrollPane` con tamaño acotado (`Estilos.Dimensiones.CONFIRMACION_ENTREGA_ANCHO`/`_ALTO`, nuevas),
     dentro de un `showConfirmDialog` YES/NO.
2. **`MaterialEntregaTableModel`**: columnas `Ingreso | Material | Cantidad`, con
   `Textos.COLUMNA_INGRESO` (nueva). Los anchos de columna van en `Constantes`, como el resto. Si
   `TableStyler.createEntregadoRenderer` y `Textos.COLUMNA_ENTREGADO` quedan sin llamador, se borran.
3. **`EquiposParaEntregarController`** orquesta, no calcula:
   ```
   plan = planificador.planificar(panel.getInstitucionesSeleccionadas(), materialesPorDestino,
                                  panel.getMaterialesSeleccionados())
   Rechazo                         → panel.mostrarAdvertencia(mensaje); salir
   !confirmarConDetalle(texto, …)  → salir
   TareaUI: leer    → AplicadorPorPartes.aplicarTodos(plan.solicitudes(), this::entregar)
            pintar  → finalizarEntregas(resultado, plan.solicitudes())
            siFalla → error (sólo llega acá una RuntimeException no esperada) + solicitarRefresco
            antes/despues → apagar/prender el botón
   ```
   - `boolean entregar(EntregaDestinoKey destino, SolicitudEntrega s)` despacha por
     `destino.getTipo()`, como el `aplicarMovimientos` de Registrar Estado: `INSTITUCION` →
     `materialService.entregarMateriales(s.filas())`; `CLIENTE` →
     `equipoOtrosService.entregar(s.filas(), s.remitos())`.
   - `finalizarEntregas`: **siempre** `solicitarRefresco.run()`, y `onEstadosActualizados` si hubo
     alguna exitosa. Los textos del resultado los arma
     `PlanificadorEntrega.describirResultado(Resultado<EntregaDestinoKey>,
     Map<EntregaDestinoKey, SolicitudEntrega>)`, que se agrega en este paso con su test
     (`describirResultado_nombraCadaDestinoPorCategoria`): exitosas, conflicto
     (`Mensajes.CONFLICTO_ENTREGA`) y error, cada una nombrando sus destinos.
   - Se borran `construirMensajeConfirmacion`, `ejecutarEntregas` y `ResultadoEntregas`.
   - El constructor **no cambia de firma**: `PlanificadorEntrega` es un tipo del dominio sin estado,
     como `AgrupadorEntregas`.
4. **Borrar** `MaterialDAO.entregarInstitucionCompleta`, `MaterialService.entregarInstitucionCompleta`,
   `EquipoOtrosDAO.entregarClienteCompleto` y `EquipoOtrosService.entregarClienteCompleto`, junto con
   sus tests: `MaterialDAOTest:249+`, `EquipoOtrosDAOTest:191+` y `:315`, y
   `EquipoOtrosServiceTest:126-131`. Lo que cubrían ya lo cubren los tests del Paso 1. `grep`: no
   puede quedar ninguna referencia.
5. **Smoke manual** (sin `-Daptium.edt.strict=true`):
   1. Una institución con dos ingresos listos: la tabla muestra la columna Ingreso y las filas se
      distinguen. Ctrl+click en una y Ctrl+↓ suma la siguiente; la confirmación lista **exactamente**
      esas dos, con su ingreso.
   2. Una institución sin selección → la confirmación lista todo. Al entregar, la institución
      desaparece.
   3. Dos instituciones seleccionadas → la tabla queda vacía y se entrega todo de las dos. Con
      muchas filas, la confirmación tiene scroll y los botones se ven.
   4. Un cliente con un REMITO → una sola fila `Remito …`; al entregarla sale el remito entero.
   5. Un equipo con un material `ESTERILIZADO` y otro `LAVADO` → aparece la fila esterilizada,
      marcada como incompleta. Al entregarla, el equipo sigue en Registrar Estado con el otro material.
   6. Seleccionar dos materiales y hacer click en la tabla de instituciones → la selección se vacía.
      Seleccionar dos y **Ctrl+Tab** hasta Entregar → se conservan. Cancelar la confirmación y hacer
      click en la tabla de instituciones → se vacía.
   7. **Conflicto:** con dos ventanas contra la misma base, B entrega un material y A, que todavía lo
      ve, lo entrega. Sale el cartel `CONFLICTO_ENTREGA`, la pantalla se relee, y en la base hay **un**
      solo movimiento a `Entregado`.
   8. Registrar Estado → Confirmar sigue funcionando (el Paso 2 tocó su loop).

### Verificación

```bash
mvn test -Dtest='PlanificadorEntregaTest,AgrupadorEntregasTest,AplicadorPorPartesTest,ConcurrenciaOptimistaTest'
mvn test
```

### Criterio de salida

- [ ] `mvn test` en verde
- [ ] `grep -rn "entregarInstitucionCompleta\|entregarClienteCompleto" src` → nada
- [ ] `EquiposParaEntregarController` no arma textos ni filtra listas
- [ ] El helper de `ui/common/seleccion/` no se modificó (`git diff` del paso no lo lista)
- [ ] Los 8 puntos del smoke manual
- [ ] Commit: `feat: entregar algunos materiales listos de una institucion o cliente`

---

## Paso 5 — Revisión, cobertura, documentación y cierre

### Tareas

1. `/code-review high` sobre el diff de este plan (desde el commit de cierre del Plan 1). Aplicar
   CRITICAL y HIGH; anotar acá el MEDIUM que se decida no tocar, con el motivo.

   **Anotado en pasos anteriores (fuera de su alcance, resolví acá):**
   - *(Paso 1, LOW)* `ControlConcurrencia`: el javadoc de `exigirFilasAfectadas` quedó **encima de
     `esContencionDeLock`**, así que éste aparece en el IDE con la documentación del otro y
     `exigirFilasAfectadas` (al final de la clase) sin ninguna. Movido el bloque sobre su método;
     cero cambio de comportamiento. ✅

   **Resultado de `/code-review high 34b6576..2d5c7a0` (el diff de los Pasos 1-4, sin el merge de
   `main` ni los tres commits de cierre del Plan 1 que quedaron antes): 0 CRITICAL, 0 HIGH que
   requieran cambio de código.**

   Un hallazgo se verificó a fondo antes de descartarlo, porque a primera vista parecía un bug de
   pérdida de datos:
   - **`AplicadorPorPartes` + `RegistrarEstadoController.finalizarConfirmacion`: una
     `DatabaseException` en un equipo se cuenta como error de esa parte, el loop sigue con los demás,
     y al final `finalizarConfirmacion` limpia el buffer completo (`cambiosPendientes`,
     `equiposPendientes`) igual, incluido el equipo que falló.** No es una regresión: es exactamente
     lo que pide este mismo plan en el Paso 2, Tarea 2 ("**Nada más cambia**: sigue limpiando el
     buffer y las copias, y pidiendo el refresco en los dos casos" — línea 329). Antes del Paso 2 el
     buffer también se limpiaba sin condición en `finalizarConfirmacion`; lo único que cambió es que
     ahora **sí llega** a ese código en vez de cortar en `siFalla` (que es justo el bug que el Paso 2
     arregla: con el loop viejo, un fallo técnico en el equipo 2 de 3 dejaba el buffer con los tres,
     y reintentar chocaba al equipo 1 contra sí mismo porque ya estaba escrito). El operador se entera
     de qué equipo tuvo el error técnico (`ERROR_ACTUALIZAR_EQUIPO_ID`, con su id) y tiene que
     rehacer sus movimientos ahí — no hay pérdida silenciosa, es la misma mecánica de "rehacer con
     datos frescos" que ya usan los conflictos de concurrencia en todo el resto del código.

   **MEDIUM/LOW no tocados (reuse, simplificación y eficiencia — no son bugs de correctitud), con el
   motivo de por qué se dejan así en este cierre:**
   - **MEDIUM** — el `INSERT` en `material_movimientos`/`otros_material_movimientos` y la
     clasificación CAS/contención se repiten entre `aplicarMovimientos` (ya existente) y
     `entregarMateriales`/`entregar` (nuevos), 3-4 veces por archivo. Extraer un helper compartido es
     un refactor transversal a DAOs ya en producción, fuera del alcance de un paso de cierre; queda
     anotado para una sesión aparte si vuelve a aparecer al tocar cualquiera de los dos DAOs.
   - **MEDIUM** — `EquiposParaEntregarController` sostiene `materialesPorDestino` y
     `volumenPorDestino` como dos `HashMap` que se vacían y rellenan (`clear()` + `putAll()`) en cada
     `pintar()`, en vez de guardar directamente `AgrupadorEntregas.Resultado`. Cambiar el modelo del
     controller ahora es tocar `pintar()` sin un test específico de ese refactor; no es incorrecto,
     sólo más verboso de lo necesario.
   - **MEDIUM** — `AgrupadorEntregas` declara su propio `DateTimeFormatter` `dd/MM/yyyy` y un
     `fecha()` privado en vez de reusar `common/util/DateTimeDisplayUtils` (que a su vez ya está
     duplicado en `AgrupadorIngresosLote`). Consolidar toca un archivo ajeno a este plan; se anota
     para cuando se ordene el formateo de fechas en general.
   - **LOW** — `Textos.SIN_DATO` (nuevo, `"-"`) duplica el valor de `Textos.SIN_MOVIMIENTO`
     (mismo `"-"`). Cosmético: no vale una migración de callers por esto.
   - **LOW** — `"No hay materiales para entregar."` está como literal en `MaterialService` y
     `EquipoOtrosService` en vez de una constante compartida (a diferencia de `ENTREGA_SIN_PENDIENTES`,
     que sí se centralizó). Mismo texto, dos lugares para editarlo si cambia.
   - **LOW** — el `INSERT` de auditoría en `material_movimientos`/`otros_material_movimientos`
     corre con `executeUpdate()` por fila dentro del loop de entrega en vez de `addBatch()`: es una
     escritura sin guarda (no aplica el caveat de `SUCCESS_NO_INFO`), así que batchearla sería una
     optimización válida. No se toca en un cierre.
   - **LOW** — el `UPDATE` de remitos sin filas se hace uno por uno en vez de un solo
     `UPDATE … WHERE id IN (…)`. Esa rama **sólo se alcanza con datos viejos** (documentado en el
     Paso 1); no vale la complejidad de un `IN` dinámico para un caso que hoy no ocurre con los flujos
     actuales.
   - **LOW** (caller-específico, no global) — la rama `false` de `Operacion.aplicar` es código
     muerto para los llamadores de Entrega (`MaterialService.entregarMateriales`/
     `EquipoOtrosService.entregar` siempre devuelven `true` o lanzan), pero sigue viva para Registrar
     Estado vía `EquipoOtrosDAO.aplicarMovimientos`. Es un artefacto de compartir la interfaz
     genérica entre dos llamadores con formas de fallar distintas, no un bug.
2. `mvn verify` + JaCoCo:
   - `PlanificadorEntrega`, `AgrupadorEntregas` y `AplicadorPorPartes` ≥ 90 %;
   - `MaterialDAO.entregarMateriales` y `EquipoOtrosDAO.entregar` cubiertos en cada rama: fila,
     remito y conflicto.
3. **`CLAUDE.md`:**
   - **"Dónde hay guarda hoy"**: suma **Entrega** (ortopedias, otros y remito). El CAS es sobre
     `estado`, con `cantidad` como defensa: hoy ninguna ruta la cambia en una fila `ESTERILIZADO`, y
     fija "se entrega la fila que se vio". La contención de locks sale como conflicto.
   - Un párrafo: la entrega **recibe lo que se vio**, nunca "lo que haya esterilizado". El camino
     viejo, además, entregaba materiales de equipos incompletos que la pantalla no mostraba. Ahora se
     muestran, marcados.
   - `AplicadorPorPartes`: ok, conflicto y error (`false` o `DatabaseException`), y el resto de las
     `RuntimeException` propaga. Es una regla que comparten Registrar Estado y Entregas.
   - **Corregir la jerarquía de excepciones**: la raíz es `ApplicationException`, no `AptiumException`,
     y `DataAccessException` no existe.
   - La tabla de materiales de Para Entregar usa el helper de `ui/common/seleccion/`. Referenciar la
     sección que agregó el Plan 1, sin repetirla.
   - "Tests": actualizar el número.
4. **Memoria:** `project-entrega-parcial.md` (`type: project`), con:
   - el bug que se corrigió;
   - las entregas invisibles de equipos incompletos, que ahora se muestran por decisión del usuario;
   - el CAS y por qué `cantidad` es defensiva;
   - el remito como una fila, y que se muestra sólo completo;
   - "varias = todo; una = selección o todo".

   Enlazar `[[project-bloqueo-optimista]]`, `[[project-registrar-estado-avance-multiple]]` y
   `[[project-architecture]]`. Agregar una línea nueva en `MEMORY.md`.
5. Marcar este plan como **✅ CERRADO**, con los SHAs de cada paso.

### Criterio de salida

- [x] `mvn verify` en verde (1637 tests, 0 fallas/errores), cobertura según el punto 2
- [x] `CLAUDE.md` y memoria actualizados
- [x] Commit: `docs: entrega parcial en para entregar`

---

## Catálogo de anti-patrones para este plan

| Anti-patrón | Por qué está mal acá |
|---|---|
| Dejar la entrega "todo" en `entregarInstitucionCompleta` y agregar la parcial al lado | Dos rutas, y una sin guarda posible: no recibe qué vio el operador. "Todo" es la misma llamada por id, con todas las filas. |
| Releer `WHERE estado = 'Esterilizado'` en la escritura para decidir qué entregar | Es exactamente el bug: entrega lo que se esterilizó después de la lectura. |
| Esconder los equipos incompletos y entregar igual sus filas | Era el comportamiento viejo: se entregaba algo que la pantalla no mostraba. Ahora se muestran, marcados. |
| Mostrar un incompleto sin marca | El operador entrega medio equipo creyendo que era "lo listo". |
| Mostrar un REMITO incompleto | Se entrega siempre entero: sería una fila que no se puede entregar. |
| CAS sobre `version` del equipo | Falso positivo: cualquier avance de otro material del mismo equipo haría chocar la entrega. La guarda va sobre el campo que se consume (`CLAUDE.md`). |
| Justificar `cantidad` con "Correcciones la cambia" | Es falso: Correcciones sólo toca equipos en `NUEVO`. Es una defensa barata, y así se documenta. Un porqué falso en `CLAUDE.md` es peor que ninguno. |
| Contar las filas del CAS con `executeBatch()` | `SUCCESS_NO_INFO` (`-2`) con `rewriteBatchedStatements=true`: la guarda rechaza entregas válidas con un cartel falso. |
| Dejar la contención de locks como `DatabaseException` | Un 1205/1213 entre dos entregas es "otro se te adelantó"; como error técnico, el operador no sabe qué hacer. |
| Tratar las 0 filas del remito como "skip", como hoy | Ahora se entrega lo que se vio, así que 0 filas **es** que la realidad cambió. Con el skip, la entrega saldría como exitosa sin el remito. |
| Escribir sin ordenar las filas | Dos entregas con filas en común bloquean en órdenes distintos y terminan en deadlock. **H2 no lo delata.** |
| Una transacción para todos los destinos | Un conflicto en el cliente B revertiría la institución A, que no tenía nada que ver, y el resultado ya no se puede informar por destino. |
| Partir la cantidad de una fila | El pedido es por filas enteras. |
| Mostrar las filas de un REMITO y seleccionarlas en bloque | El usuario eligió una fila. Además obligaría a meter reglas de grupos en el helper genérico del Plan 1. |
| Redefinir o "ajustar" el helper de selección acá | Lo define el Plan 1 y lo comparten dos pantallas. Si hace falta un cambio, va en ese plan, con su test. |
| Fusionar por nombre en la confirmación | Dos "Tornillo" de ingresos distintos salen como uno: la confirmación deja de listar lo que se entrega. |
| La confirmación como un `String` suelto en el `JOptionPane` | Con "varios = todo" puede tener cientos de líneas y dejar los botones fuera de la pantalla. |
| Armar el texto de la confirmación o filtrar ítems en el controller | Los controllers orquestan. Es la lógica que hoy vive ahí sin tests (`construirMensajeConfirmacion`). |
| Releer sólo si hubo alguna exitosa | Un conflicto es justamente cuando la pantalla quedó vieja. |
| Atrapar `RuntimeException` en `AplicadorPorPartes` | Un NPE o una `ValidationException` son bugs: contarlos como "error de una parte" los esconde detrás de un cartel. |
| Llamar a `unificarMaterialesDuplicados` después de entregar | El camino viejo no lo hacía, y fusionar filas `ENTREGADO` movería ids que ya referencia la tabla de movimientos. |
| Reemplazar el `"Elementos"` del SQL por la constante nueva | Es un dato persistido: cambiarlo en un solo lado deja filas viejas que ya no coinciden. La constante es sólo para el Java. |

---

## Plan de sesiones

Cinco pasos, **cinco sesiones**. Cada sesión arranca en frío.

| Sesión | Pasos | Modelo | Effort | Fast mode | Por qué |
|---|---|---|---|---|---|
| 1 | Paso 1 | **Opus 5** | **alto** | ❌ no | La corrección del bug de concurrencia: el CAS, el remito con `NOT EXISTS`, el orden de escritura, la contención como conflicto y los casos de `ConcurrenciaOptimistaTest`. El orden de bloqueo es lo que **H2 no delata**. |
| 2 | Paso 2 | **Opus 5** | medio | ❌ no | Refactor chico, pero cambia la semántica de error de Registrar Estado, que ya está en producción. El orden de los `catch` y qué propaga son decisiones, no mecánica. |
| 3 | Paso 3 | **Opus 5** | medio | ❌ no | Qué es "una fila entera" (todas las `ESTERILIZADO` del grupo, varias si hay lotes distintos), los incompletos marcados, el remito sólo completo y el planificador de alcance. Lógica pura con tests: el riesgo está en entender bien el modelo. |
| 4 | Paso 4 | **Sonnet 5** | **alto** | ➖ opcional | Con los Pasos 1-3 hechos, es cablear la pantalla, el controller y el borrado de las rutas viejas. Effort alto por el borrado (verificar que no queda ningún llamador) y por el smoke de conflicto. |
| 5 | Paso 5 | **Sonnet 5** | medio | ➖ opcional | Cierre: abre con `/code-review high`, que es el que trae el juicio. |

**Paralelizar:**
- **La Sesión 1 no depende del Plan 1** y puede correr a la vez que cualquiera de sus sesiones, en un
  `git worktree`. Pero comparte archivos de test con el Paso 5 del Plan 1: `MaterialDAOTest`,
  `EquipoOtrosDAOTest` y `ConcurrenciaOptimistaTest`, más `Constantes.Mensajes`. Van a aparecer
  conflictos de merge, triviales pero seguros.
- Las **Sesiones 2 y 3** no comparten archivos. El 2 toca `common/controller/helpers` y
  `RegistrarEstadoController`; el 3, `AgrupadorEntregas`, `MaterialEntregaItem`, su `TableModel`, el
  planificador y lo mínimo del controller de entregas. Pueden correr a la vez, una vez cerrados el
  Paso 1 de acá y el Paso 5 del Plan 1.
- Orden secuencial recomendado para una sola persona: el Plan 1 completo, y después los Pasos 1 a 5
  del Plan 2.

---

### Sesión 1 — Paso 1: la escritura por id con CAS

**Opus 5 · effort alto · sin fast mode**

```
Ejecutá el Paso 1 de plans/entrega-parcial-para-entregar.md (FilaAEntregar/RemitoAEntregar,
MaterialDAO.entregarMateriales, EquipoOtrosDAO.entregar, sus services, CONFLICTO_ENTREGA y los
casos nuevos de ConcurrenciaOptimistaTest). No depende del Plan 1.

Antes de escribir leé del plan: "Decisiones de diseño tomadas por el plan", "Estado del que
parte este plan", el Paso 1 entero y el "Catálogo de anti-patrones". Después leé del CLAUDE.md
"Concurrencia — bloqueo optimista" completo, y en el código MaterialDAO.entregarInstitucionCompleta,
EquipoOtrosDAO.entregarClienteCompleto (lo que se reemplaza), MaterialDAO.aplicarMovimientos (el
molde de transacción) y ControlConcurrencia (exigirFilaAfectada y esContencionDeLock).

Lo que esta sesión tiene que dejar bien, y nada más lo va a delatar:
1. la escritura recibe LO QUE SE VIO (ids + cantidad) y nunca relee "lo que haya esterilizado":
   ése es el bug. Lo fija el test entregaNoIncluyeLoEsterilizadoDespuesDeLeer;
2. el CAS es sobre estado (+ cantidad, DEFENSIVA: Correcciones sólo toca equipos NUEVO, así que no
   escribas en ningún lado que Correcciones la cambia) en UN solo UPDATE, con executeUpdate() y
   exigirFilaAfectada. NO sobre version. Sin FOR UPDATE previo: no se compara nada en Java;
3. las filas se escriben ordenadas por (equipoId, materialId): dos entregas cruzadas en otro
   orden dan un deadlock que H2 NO reproduce. La contención (esContencionDeLock) sale como
   ConflictoConcurrenciaException, no como DatabaseException;
4. el remito sin filas: 0 filas afectadas AHORA ES CONFLICTO (hoy es "skip"). Esa rama sólo se
   alcanza con datos viejos: mantenela simple;
5. el conflicto revierte la transacción ENTERA del destino, y hay un test que lo prueba.

Tarea 1 antes que nada: re-verificá cómo se guarda `estado`. Si aparece otra capitalización,
PARÁ y preguntame. MaterialServiceTest no existe: crealo.

Los métodos viejos NO se borran en este paso. Terminá con mvn test en verde y el commit.
```

### Sesión 2 — Paso 2: `AplicadorPorPartes`

**Opus 5 · effort medio · sin fast mode**

```
Ejecutá el Paso 2 de plans/entrega-parcial-para-entregar.md (generalizar
AplicadorMovimientosPendientes a AplicadorPorPartes<K,V> y adaptar RegistrarEstadoController).
El Paso 5 del Plan 1 (plans/registrar-estado-avance-multiple.md) ya está commiteado.

Leé antes del plan: "Decisiones tomadas con el usuario" (la fila "Error técnico en un destino"),
el Paso 2 y el catálogo de anti-patrones. Después leé AplicadorMovimientosPendientes y su test,
y confirmarCambios/finalizarConfirmacion de RegistrarEstadoController.

Tres cosas:
1. una DatabaseException en una parte es error de ESA parte, y el loop SIGUE. Lo decidió el
   usuario, y cambia Registrar Estado a propósito;
2. cualquier otra RuntimeException PROPAGA: una ValidationException o un NPE son bugs, no "una
   parte que falló";
3. en RegistrarEstadoController sólo cambian los tipos del resultado: el buffer y las copias se
   siguen limpiando y el refresco se sigue pidiendo igual que hoy.

Terminá con el smoke de Registrar Estado, mvn test en verde y el commit.
```

### Sesión 3 — Paso 3: agrupador y planificador

**Opus 5 · effort medio · sin fast mode**

```
Ejecutá el Paso 3 de plans/entrega-parcial-para-entregar.md (MaterialEntregaItem como record con
filas/remitos, AgrupadorEntregas por filas ESTERILIZADO con columna Ingreso e incompletos
marcados, y PlanificadorEntrega). El Paso 1 de este plan ya está commiteado.

Leé antes del plan: "Decisiones tomadas con el usuario" (sobre todo "Equipos incompletos"),
"Estado del que parte este plan" (los puntos del REMITO, de la columna Entregado y de
MaterialEntregaTableModel), el Paso 3 y el catálogo de anti-patrones. Después leé AgrupadorEntregas
y su test, y construirMensajeConfirmacion de EquiposParaEntregarController (lo que sale de ahí).

Lo que no puede salir mal:
1. un ítem lleva TODAS las filas ESTERILIZADO de su grupo, con la cantidad de cada una, y pueden
   ser varias si vienen de lotes distintos. Eso es "fila entera", y es lo que compara la guarda;
2. un equipo incompleto SÍ aparece, con sus filas esterilizadas marcadas (INGRESO_INCOMPLETO);
   un REMITO aparece SÓLO completo, y es UN ítem;
3. varias instituciones = todo de cada una; una con selección = sólo eso; una sin selección = todo;
4. la confirmación tiene una línea por ítem, con su ingreso, SIN fusionar por nombre;
5. el build no se rompe: MaterialEntregaTableModel pasa a los accesores del record y el
   controller se toca sólo lo necesario para compilar.

Ninguna de las dos clases importa Swing. Terminá con mvn test en verde y el commit.
```

### Sesión 4 — Paso 4: pantalla, controller y borrado de las rutas viejas

**Sonnet 5 · effort alto · fast mode opcional**

```
Ejecutá el Paso 4 de plans/entrega-parcial-para-entregar.md (tabla de materiales habilitada con
el helper de selección del Plan 1, columna Ingreso, confirmación con scroll, controller que
orquesta con PlanificadorEntrega + AplicadorPorPartes, y borrado de entregarInstitucionCompleta /
entregarClienteCompleto). Los Pasos 1 a 3 de este plan y el Paso 1 del Plan 1 ya están commiteados.

Leé antes del plan: "Decisiones de diseño tomadas por el plan", el Paso 4 con su pseudocódigo y
el catálogo de anti-patrones. Después leé ui/common/seleccion/SeleccionAcumulativaTabla (lo USÁS,
no lo modificás) y el Paso 4 de plans/registrar-estado-avance-multiple.md (cómo se cableó allá).

Cuatro cosas:
1. el helper de selección NO se modifica: instalar(tablaMateriales, btnEntregarInstitucion),
   después de crear el botón;
2. después de entregar se relee SIEMPRE, también si hubo conflicto o falló;
3. con las columnas nuevas, centerColumns pasa de la columna 1 a la 2 (Cantidad);
4. antes de borrar los métodos viejos, grep: no puede quedar ninguna referencia en src/
   (incluidos EquipoOtrosDAOTest:315 y EquipoOtrosServiceTest:126-131).

Cerrá con los 8 puntos del smoke manual (SIN -Daptium.edt.strict=true; para salir de la tabla con
teclado es Ctrl+Tab); el 7 es el conflicto con dos ventanas. mvn test en verde y el commit.
```

### Sesión 5 — Paso 5: cierre

**Sonnet 5 · effort medio · fast mode opcional**

```
Ejecutá el Paso 5 de plans/entrega-parcial-para-entregar.md (revisión, cobertura, documentación
y cierre). Los Pasos 1 a 4 ya están commiteados.

Empezá por /code-review high sobre el diff del plan. Aplicá CRITICAL y HIGH; los MEDIUM que no
toques van anotados en el plan con el motivo. Después mvn verify con los umbrales del punto 2.

En CLAUDE.md: "Dónde hay guarda hoy" suma Entrega, con el CAS sobre estado y cantidad como
DEFENSA (no escribas que Correcciones la cambia: sólo toca equipos NUEVO); AplicadorPorPartes y
sus tres categorías; los incompletos que ahora se muestran; y corregí la jerarquía de excepciones
(ApplicationException es la raíz; DataAccessException no existe). La memoria lleva el bug
corregido y la decisión sobre los incompletos. Marcá el plan como CERRADO con los SHAs y commiteá.
```
