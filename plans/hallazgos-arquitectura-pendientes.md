# Hallazgos de arquitectura pendientes (revisión 2026-07-22)

Diagnóstico de la revisión profunda del 2026-07-22.

**Estado al 2026-08-27: los 8 hallazgos cerrados**, incluida la checklist manual de la Fase 5 de #6,
pasada contra la app real ese mismo día. Del plan de sesiones queda **sólo el `/code-review ultra`**.

| # | Estado | Commit |
|---|---|---|
| #1 fallos silenciosos ortopedias | hecho | `de1af06` |
| #2 strategies muertas (`IMaterialFilter` + `ICapacidadCalculator`) | hecho | `a370e61` |
| #5 `common` ↔ Swing | hecho | `fc15bd1` |
| #4 `Object[]` → records | hecho (2026-07-23) | `57c87d1` |
| #3 `AppModel` | hecho (2026-07-23) — **disuelto** | `728c88d` |
| #6 concurrencia / EDT | **hecho (2026-08-27)** — Fases 1-6, **4b**, el hallazgo derivado de Lavadero y la **checklist manual de la Fase 5 pasada**: 30 WARN de `EdtGuard`, los 30 de los autocompletados documentados, cero fuera de la lista. Detalle en [`refactor-concurrencia-edt.md`](refactor-concurrencia-edt.md#resultado-de-la-fase-5--pasada-2026-08-27) | Fase 4b: `14354a2` · Lavadero: `95c9e33` |
| #7 subdivisión de `Equipo*` sin persistir (agregado 2026-08-19) | hecho (2026-08-27) | Pasos 1-8 `01d18be`..`ef6b8c2` + cierre `docs: ... (#7)` |
| #8 Lavadero (Ciclos + Clasificación) fuera del modelo EDT (agregado 2026-08-27, derivado de la verificación de #6/4b) | hecho (2026-08-27) — `CiclosController` colapsó sus 4 lecturas en un `recargar()` con el record `DatosCiclos` + `ConstructorVistaCiclos`, y sus 3 escrituras van por un helper `ejecutar(...)`; `ClasificacionController` y `LavaderoController.guardar()` al patrón de 4b. 970 tests, smoke pasado | `95c9e33` |
| #9 huecos que dejó abierto el bloqueo optimista (agregado 2026-09-04) | **hecho (2026-09-04)** — las diez rutas de Correcciones, el reintento de secuencia de lotes, y las dos rutas alcanzables de ABM (eliminar/fusionar clientes). 11 pasos en [`guardas-correcciones-y-secuencia-de-lotes.md`](guardas-correcciones-y-secuencia-de-lotes.md) | `e0876db`..`6aaca8f` |
| #10 código muerto destapado por #9 (agregado 2026-09-04) | **anotado, no tocado** — ver más abajo | — |
| #11 lecturas del histórico: lo que dejó abierto el hotfix de "Ver Equipos" (agregado 2026-09-11) | **hecho (2026-09-21)** — (a), (b) y (c) cerrados por [`conexiones-y-paginacion.md`](conexiones-y-paginacion.md); la revisión de cierre dejó dos defectos (carrera en la cancelación, tabla derivada en la unificación), también cerrados. Ver "Cierre" más abajo | hotfix: `cbf3cb3` (`v1.2.0.2`) · cierre: `3bfa1fd`..`44f5b39` |

Las referencias de línea de abajo fueron **re-verificadas tras los commits de hoy**.

Contexto que se respetó en toda la revisión: la **no-atomicidad auditoría↔dato es
deliberada** (ver `plans/sacar-sql-de-equipo-otros-correccion-service.md`). No unificar
transacciones "por prolijidad".

---

## #3 — `AppModel`: fachada que ya se rompió y crece sin techo  ✅ HECHO (2026-07-23)

**Decisión del usuario:** *disolver* la fachada. `AppModel.java` fue **eliminado** (362 líneas).
`UiCoordinator` pasó a recibir `AppContext` y es ahora el único punto de la UI que lo ve
completo: le entrega a cada controller **solo los services de su alcance**, declarados en su
constructor y documentados con un javadoc de una línea.

Reparto resultante (13 archivos de `src/main` tocados, 0 tests — ninguno referenciaba `AppModel`):

| Controller | Services que recibe |
|---|---|
| `CDEViewController` | `Equipo`, `EquipoOtros` |
| `RegistrarEstadoController` | `Equipo`, `EquipoOtros`, `Material`, `IEstadoValidator` |
| `EquiposParaEntregarController` | `Equipo`, `EquipoOtros`, `Material`, `IEstadoValidator` |
| `CorreccionsController` | `EquipoCorreccion`, `EquipoOtrosCorreccion`, `CatalogoOtros` |
| `LotesController` | `Catalogo`, `Autoclave`, `Lote`, `Equipo`, `EquipoOtros` |
| `VerLotesController` | `Autoclave`, `Lote`, `LoteReporte` |
| `VerEquiposController` | `Equipo`, `EquipoOtros`, `Cliente`, `Institucion`, + los 2 reporte |
| `OrthopediaInputController` | `Cliente`(super), `Catalogo`, `Profesional`, `Institucion`, `Equipo` |
| `OtrosInputController` | `Cliente`(super), `CatalogoOtros`, `EquipoOtros` |
| `AjustesController` | `Cliente` |

**Lo que resolvió, punto por punto:**
- **(b) queda cerrado por diseño, no por disciplina:** `LotesController` ya no *puede* llamar
  `fusionarClientes()` — nunca ve `ClienteService`. El compilador reemplaza a la regla escrita.
- **Los 5 getters de servicio desaparecieron solos**, incluida la "excepción reconocida al patrón
  facade" de `getEquipoCorreccionService()`: `CorreccionsController` ya guardaba el service como
  campo, solo cambió de dónde lo recibe.
- **16 de los 47 métodos de delegación eran código muerto** (cero llamadores en `src/main`:
  `obtenerEquipoPorId`, `actualizarEquipo`, `contarEquipos`, `obtenerCatalogo`,
  `obtenerLotesFinalizados`, `obtenerMaterialesPorLote`, `obtenerVolumenesPorLote`,
  `obtenerLotesEnRango`, `obtenerClientesPorLote`, `obtenerMaterialesPorClientePorLote`,
  `obtenerOtrosPorClientePorLote`, `obtenerClientePorId`, `obtenerProfesionalPorId`,
  `obtenerInstitucionPorId`, `obtenerEquiposOtrosEntreFechas`, `obtenerEquiposEntreFechas`).
  Se fueron con la clase, sin necesidad de un refactor-clean aparte.
- **(a) el crecimiento lineal se cortó:** agregar una operación ya no toca un archivo compartido
  por toda la app; toca el controller que la usa y la línea de `UiCoordinator` que lo construye.

**Efectos colaterales que destapó:**
- `validarConexion()` era el **único** método de `AppModel` que no delegaba a un service. Se movió
  a `ConnectionPool.validarConexion()`, que es donde vive esa responsabilidad. `AppController`
  ahora la llama ahí.
- `ConstructorEquipo` declaraba un campo `AppModel model` **que nunca usaba** — `construir()` solo
  lee del panel. Se eliminó el parámetro; el constructor quedó en `(PantallaIngresoOrtopedia)`.
- `EquipoInputControllerBase` exponía `protected final AppModel model` a sus dos subclases, o sea
  que heredaban acceso a toda la API del sistema. Ahora tiene un `private final ClienteService`
  (lo único realmente común: el autocompletado de cliente) y cada subclase declara lo suyo.
- `App.main` perdió un paso entero: la secuencia de arranque bajó de 7 a 6 pasos. Actualizados
  `CLAUDE.md` y `README-DEPLOY.md` (incluida la salida de log esperada).

**Verificación:** `mvn compile` OK, **509 tests en verde**, `grep AppModel src/` sin resultados.

<details>
<summary>Diagnóstico original</summary>

**Qué:** `AppModel` (**362 líneas**, ~60 métodos de delegación de una línea). El javadoc dice
explícitamente *"No exponer servicios internos (get\*Service)"* y abajo hay **cinco** getters
de servicio (**[AppModel.java:341-359](../src/main/java/com/example/app/AppModel.java)** —
`getEquipoCorreccionService` 341, `getEquipoOtrosCorreccionService` 345, `getLoteReporteService` 349,
`getEquipoReporteService` 353, `getEquipoOtrosReporteService` 357), uno con excusa documentada
y cuatro sin ella. Los reporte-services se pasan directo desde `UiCoordinator` a los
controllers, salteando la fachada.

> Nota: en esta sesión se le agregó `esEntregable(EstadoEquipo)` (hallazgo #2), que es
> delegación legítima al `estadoValidator` — pero ilustra el punto (a): la fachada creció
> otra vez.

**Por qué es problema:**
- (a) Crece linealmente con cada feature; cualquier cambio recompila y re-testea toda la app.
- (b) Todo controller ve la API completa del sistema — `LotesController` puede llamar
  `fusionarClientes()`. La regla "no exponer servicios" se cumple por disciplina, no por diseño.

**Decisión que estaba pendiente (resuelta el 2026-07-23):** se eligió **disolver** — inyección
directa por controller. La alternativa descartada era mantener la fachada y hacer cumplir por
diseño la regla de no exponer servicios (p. ej. segregando en interfaces por rol), que conservaba
la capa de nombres semánticos pero dejaba vivo el problema (a): la interfaz también crece con
cada feature, con más archivos.

</details>

---

## #4 — `Object[]` posicional cruzando la frontera DAO→service  ✅ HECHO (2026-07-23)

Se creó el record [`FilaMaterial`](../src/main/java/com/example/features/equipos/ortopedias/dao/FilaMaterial.java)
(`id, equipoId, codigo, descripcion, cantidad, estado`) y `MaterialDAO.obtenerMaterial` /
`obtenerMaterialesPorCodigo` pasaron a devolverlo. `EquipoCorreccionService` ya no castea por
índice. Un único record sirve a las dos consultas: se completó cada `SELECT` con la columna que
le faltaba (`em.id` y `em.equipo_id`), que ya eran parámetros del `WHERE`, así que el record
viene siempre poblado y no hay campos "válidos según quién llamó". El mapeo quedó centralizado
en un `mapearFila(ResultSet)` privado que accede **por nombre de columna**, así que reordenar el
`SELECT` ya no rompe nada y cambiar el record falla al compilar.

**Requirió subir el proyecto de Java 11 a 17** (`maven.compiler.release`), porque `record` es
16+. Consecuencia operativa: el jar ahora exige **JRE 17+** en producción; se actualizaron
`README-DEPLOY.md` y `CLAUDE.md`. Verificado: 509 tests en verde, `mvn package` OK, bytecode
major 61.

**Pendientes menores que dejó, fuera del alcance de #4:**
- ~~`MaterialCorreccionDTO` es código muerto (cero referencias) y tiene los mismos 6 campos que
  `FilaMaterial`~~ → ✅ **borrado el 2026-08-27** en el refactor-clean.
- `MaterialDAO.obtenerMaterial` sigue devolviendo `null` cuando no encuentra; con Java 17
  disponible, `Optional` es ahora una opción. No se tocó por estar fuera del hallazgo.
- Quedan `Object[]` locales en `EquipoMaterialHelper` y `EquipoOtrosMaterialHelper` (se arman
  y consumen dentro del mismo método, no cruzan ninguna frontera) y en los table models de
  Swing (los exige la API de `DefaultTableModel`). Ambos casos se dejaron a propósito.

<details>
<summary>Diagnóstico original</summary>

**Qué:** DAOs devuelven `Object[]` / `List<Object[]>` que el service castea por índice.
Ejemplo en [EquipoCorreccionService.java:110-114](../src/main/java/com/example/features/equipos/ortopedias/service/EquipoCorreccionService.java):
```java
Object[] materialActual = materialDAO.obtenerMaterial(materialId);
Integer codigoAnterior      = (Integer) materialActual[0];
String  descripcionAnterior = (String)  materialActual[2];
```
También `List<Object[]>` en la línea 197. Origen en `MaterialDAO.obtenerMaterial` y
`MaterialDAO.obtenerMaterialesPorCodigo`. Re-medido hoy: **12 archivos en
`src/main/java`** contienen `Object[]` (el conteo original de "29 ocurrencias en 15
archivos" incluía tests). `EquipoCorreccionService` no fue tocado por los commits de
hoy, así que sus referencias de línea siguen vigentes.

**Por qué es problema:** si alguien reordena el `SELECT` del DAO, no falla la compilación:
falla en runtime con `ClassCastException`, o peor, no falla y guarda el dato equivocado en
la auditoría. El compilador no ayuda.

**Fix:** un `record` por cada forma (ej. 5 campos para el material). Elimina la clase de bug
entera. Barato y de bajo riesgo. Buen candidato para hacer primero entre los pendientes.

</details>

---

## #5 — La capa `common` depende de Swing  ✅ HECHO (`fc15bd1`)

Se separó en `Validador` (puro, en `common/util/`) + nuevo
[`RestriccionesCampo`](../src/main/java/com/example/ui/common/RestriccionesCampo.java)
en `ui/common/`, con los métodos renombrados a `soloNumeros` / `soloLetrasYEspacios`.
Actualizados los 4 call-sites (`PantallaIngresoOrtopedia` ×2, `PantallaCorrecciones`,
`AgregarMaterialDialog`, `PanelMateriales`).

Verificación: `grep -E "javax\.swing|java\.awt" src/main/java/com/example/common` → sin
resultados. El núcleo ya no depende de la UI.

**Pendientes menores que dejó — ✅ los dos cerrados el 2026-08-27:**
- ~~`Validador.esEmailValido` y `esNumeroPositivo` sin llamadores~~ → borrados en el refactor-clean.
- ~~No existe `ValidadorTest`~~ → creado, 15 tests sobre los 4 métodos que quedan
  (`noEstaVacio`, `esFormatoNombre`, `soloNumeros`, `detectarDuplicados`) + el constructor privado.

---

## #6 — Concurrencia ad-hoc y trabajo de BD en el EDT  (MEDIO)

**Qué:** tres modelos de concurrencia conviviendo:
- `new Thread()` crudo — **20 ocurrencias** en `src/main/java` (re-medido hoy; el conteo
  original decía "8 sitios"), incluido dentro de una **view**
  ([PantallaAuditoria.java:219](../src/main/java/com/example/features/equipos/ortopedias/view/PantallaAuditoria.java)).
- `SwingWorker` (VerLotesController, VerEquiposController).
- Llamadas sincrónicas directas.

`LotesController.cargarDatos()`
(**[línea 162](../src/main/java/com/example/features/lotes/controller/LotesController.java)** —
se corrió +1 por el import de `OcupacionAutoclave` en `a370e61`)
hace **cinco queries en serie sobre el EDT**, incluidas dos `obtenerTodos()` sin paginar —
y `UiCoordinator.crearRefrescador()` lo llama junto con otros cuatro `cargarDatos()` después
de cada guardado.

**Por qué es problema:** la UI se congela y el congelamiento **crece con el volumen de datos
históricos**. Sin cancelación: dos refrescos rápidos pueden aplicar resultados fuera de orden.

**Fix (dirección):** estandarizar en `SwingWorker` (o un helper propio), sacar todo acceso a BD
del EDT, y agregar cancelación/debounce al refresco global. Es el hallazgo de más trabajo y el
que conviene planificar con cuidado (toca varios controllers y el flujo de refresco).

**Estado:** se ejecuta en `plans/refactor-concurrencia-edt.md`. Fases 1-6 y 4b cerradas; el
hallazgo derivado de Lavadero (Ciclos + Clasificación) también, el 2026-08-27.

**Lo que queda del EDT, medido el 2026-08-27:** sólo los **cinco autocompletados por tecla**,
que son excepción aceptada — `AutocompleteListener` de clientes/profesionales/instituciones, el
`CatalogoLookup` de `OrthopediaInputController`, el de `catalogo_otros`, y el de clientes de
`LavaderoController`. Se disparan en cada pulsación y moverlos a fondo pide cancelación y orden
de resultados, que es un cambio aparte.

En `strict` esos cinco **lanzan**, lo que vuelve inutilizables los campos de cliente de Lavadero
y del CDE: por eso los smokes de esas pantallas se corren **sin** `strict`, verificando los WARNs
del log.

`LavaderoController.guardar()` también escribía en el EDT (se le escapó al inventario del
hallazgo de Lavadero, que sólo miró Ciclos y Clasificación). Arreglado el 2026-08-27 con el
patrón de la Fase 4b.

---

## #7 — La subdivisión de `Equipo*` no se persiste  ✅ HECHO (2026-08-27)

Blueprint de 9 pasos ([`fracciones-de-equipo-persistidas.md`](fracciones-de-equipo-persistidas.md))
ejecutado entero sobre `ConexionConCDE`, commit por paso. Resultado:

- **`V19`** — tabla `instancias_equipo_ciclo` + columna `instancia_equipo_id` en
  `elementos_ciclo_lavadero`. **`V20`** — `salidas_lavadero.elemento_ciclo_id` pasa a nullable +
  columna `instancia_equipo_id`. Ninguna migración existente se tocó.
- `SQL_DISPONIBLES` y la detección usan la fórmula
  `SUM(cantidad donde instancia IS NULL) + COUNT(DISTINCT instancia_equipo_id)`: un equipo repartido
  en N consume 1 unidad, no N.
- `CicloLavaderoDAO.crearInstanciaEquipo` (+ validación en el service); `CiclosController` crea las
  instancias antes de lanzar y **exige "Lanzar Todos"** para grupos repartidos (bloquea el lanzamiento
  individual y valida config completa del grupo).
- `AgrupadorInstanciasSalida` (clase plana, testeable) agrupa las N fracciones en 1 fila de Salidas,
  visible sólo cuando las N partes tienen ciclo finalizado. `SalidaLavaderoDAO` opera `marcarListo` /
  `volverALavado` / `derivar` sobre la instancia entera. Records `ElementoLavadoPendiente` / `SalidaLista`
  cambiaron `lavarropasNumero: int` → `lavarropas: String`.
- `CicloLavaderoDAO.detectarLineasSobregiradas()` — detección (sin reparación) de bases de desarrollo
  con datos previos.
- Defecto destapado por el test de integración y arreglado en el Paso 8: `SalidaLavaderoService.marcarListo`
  deduplicaba con `elementoCicloId` (ahora `null` en toda instancia), rompiendo el marcado masivo cuando
  dos equipos repartidos terminaban juntos — clave de duplicado nueva `claveDeDuplicado`.

**Verificación:** `mvn test` en verde (951+ tests), `mvn clean package` OK. Smoke manual de GUI
pendiente (fuera del blueprint).

<details>
<summary>Diagnóstico original (ALTO — agregado 2026-08-19)</summary>

**Qué:** cuando un `Equipo*` se reparte entre varios lavarropas, el `instanciaId` que agrupa sus
fracciones es un `AtomicInteger` en memoria de `CiclosController` (línea 46) y no viaja a la base:
`ElementoCicloMovimiento` no lo lleva y `elementos_ciclo_lavadero` no tiene dónde guardarlo.

**Por qué es problema:** un equipo de cantidad 1 repartido en 4 lavarropas escribe 4 filas de
`cantidad = 1` contra una línea de clasificación de cantidad 1 → `SQL_DISPONIBLES` calcula
`ya_procesada = 4 > 1`. El `HAVING` esconde el síntoma, así que la inconsistencia es invisible hasta
que otra feature suma esas filas. La pantalla de Salidas (rama `ConexionConCDE`) es la primera que lo
hace, y multiplica por 4 lo que manda al CDE.

**Encontrado por:** el smoke manual del Paso 8 de `salidas-lavadero-listo-y-derivacion-cde.md`.
No es deuda de ese plan: es anterior, del plan de ciclos.

**Diagnóstico completo, decisiones cerradas y blueprint paso a paso:**
[`fracciones-de-equipo-persistidas.md`](fracciones-de-equipo-persistidas.md).

</details>

---

## #9 — Huecos que dejó abiertos el bloqueo optimista  ✅ HECHO (2026-09-04)

Los tres los había dejado **explícitamente fuera de alcance** el plan
[`bloqueo-optimista-concurrencia.md`](bloqueo-optimista-concurrencia.md), que cerró los cuatro
flujos críticos (Registrar Estado × 2, Lanzar Lote, Lanzar Tanda, Salidas + derivación al CDE) más
Clasificación. Los tres se cerraron con el plan de 11 pasos
[`guardas-correcciones-y-secuencia-de-lotes.md`](guardas-correcciones-y-secuencia-de-lotes.md):

- **(a) `Correcciones` sin guarda** — las diez rutas (ortopedias y "otros") pasan a guardar por
  `version` del agregado, reemplazando lo que un snapshot del formulario mostraba. Es asimétrico a
  propósito respecto de Registrar Estado: ahí el mismo falso positivo (dos operadores tocando
  materiales distintos del mismo equipo) se **rechaza**; en Correcciones se **acepta**, por ser una
  pantalla de uso esporádico y auditado. Ver `CLAUDE.md` § *Concurrencia — bloqueo optimista*.
- **(b) `LoteDAO.obtenerSiguienteSecuencia`** — reintento sobre la violación de `UNIQUE`
  (`id_negocio`), con la clase `23` discriminada **fuera** de la transacción fallida (adentro lee
  el snapshot viejo bajo `REPEATABLE READ` y el reintento no se dispara nunca en MySQL; H2 no lo
  delata). No es un lost update, es asignación de identidad: no lleva `ConflictoConcurrenciaException`
  salvo al agotar los tres reintentos.
- **(c) Los ABM** — medidos contra el código, sólo dos operaciones eran alcanzables y pisables:
  `eliminarCliente` (CAS por nombre) y `fusionarClientes` (verificación de los dos nombres +
  `FOR UPDATE`, y de paso bumpea `equipos`/`equipo_otros` — era el único `UPDATE` de agregado sin
  bump que quedaba). El resto de lo que nombraba el hallazgo original no tenía ruta de llamada; ver
  **#10**.

**Verificación:** `mvn test` en verde (suite completa). Nueve casos nuevos en
`ConcurrenciaOptimistaTest` con la forma *A lee → B modifica y commitea → A escribe → conflicto, y
el estado final es el de B*. **Sin verificar contra MySQL real** (sólo H2): los dos puntos ciegos
de la Parte B (bloqueo del índice único en el segundo `INSERT`, y que la discriminación de la
clase `23` esté afuera de la transacción) no se pueden reproducir en H2 — ver la sección
"Verificación" del plan.

### (a) `Correcciones` sigue escribiendo a ciegas — y ya tiene la `version` esperándola  (MEDIO)

**Qué pasa.** `MaterialDAO.actualizarCantidad` / `actualizarCodigo` y sus equivalentes de "otros"
(`EquipoOtrosDAO.actualizarCantidadRemito`, `actualizarCantidadMaterial`, `insertarMaterial`,
`eliminarMaterialesPorDescripcion`) reemplazan lo que un formulario mostraba, sin ninguna guarda:
dos operadores corrigiendo el mismo equipo, gana el último en apretar Guardar.

**Por qué es distinto de lo ya cerrado.** Corrección **no consume por cantidad**: reemplaza el valor
entero desde un snapshot del formulario. Ahí la `version` del agregado es exactamente la guarda
correcta, y es el motivo por el que la V21 la creó.

**Qué falta para activarla.** La columna ya se mantiene en todas las rutas (la auditoría completa
está en el javadoc de `EquipoOtrosMaterialHelper.recalcularEstadoEquipo`). Falta: hacer viajar la
`version` leída con el formulario hasta el DAO, agregar `AND version = ?` a esos `UPDATE`, y
`ControlConcurrencia.exigirFilaAfectada` sobre el resultado. **No** es un cambio de esquema.
Antes de hacerlo, verificar que ninguna ruta de escritura quedó sin bumpear: un bump con agujeros da
**falsos negativos silenciosos**, que son peores que no tener guarda.

### (b) `LoteDAO.obtenerSiguienteSecuencia` es `SELECT MAX(secuencia) + 1`  (MEDIO)

Dos lotes lanzados en el mismo segundo pueden calcular la misma secuencia. Hoy los salva el
`UNIQUE (id_negocio)` de la V1, que los hace fallar con un error técnico feo en vez de un mensaje
claro.

**No es un lost update**, es **asignación de identidad**, y por eso no lo resuelve el bloqueo
optimista: no hay ningún dato leído por el operador que se esté pisando. Se arregla con otra
técnica — reintento sobre la violación de UNIQUE, o una tabla de secuencias — y merece su propia
decisión.

### (c) Los ABM quedaron fuera  (BAJO)

Catálogo, clientes, instituciones, profesionales y ajustes escriben sin guarda. Es lo acordado: son
pantallas de mantenimiento, con un solo operador editándolas en la práctica, y meterles guardas
tendría más costo de UX (carteles de conflicto en lugares donde nadie choca) que beneficio. Si algún
día dos personas mantienen catálogos a la vez, el mecanismo ya está armado y es agregar el `WHERE`.

---

## #10 — Código muerto destapado al medir #9 (agregado 2026-09-04)  BAJO, anotado

La medición de qué ABM eran alcanzables (#9c) encontró código sin ningún llamador de producción.
No se tocó: borrarlo es una decisión del usuario, no de este plan — el mismo criterio que dejó
viva `PantallaVerCDEv1` en el punto 4 del plan de sesiones de abajo.

- **`SimpleEntityDAO.actualizar`** (renombrar cliente / institución / profesional) — cero
  llamadores. No existe pantalla de renombrado para ninguna de las tres entidades.
- **`CatalogoDAO.guardarDescripcion`** — **no es un `INSERT`, es un upsert**
  (`ON DUPLICATE KEY UPDATE`), así que a diferencia del resto de esta lista sí tiene superficie de
  lost update. Pero es **inalcanzable, no inofensivo**: sólo lo llama `CatalogoService.guardarDescripcion`,
  que a su vez no tiene llamador de UI. Si algún día se cablea una pantalla de edición de catálogo,
  revisar esto primero — es la única entrada de esta lista que necesitaría guarda el día que deje
  de estar muerta.
- **`CatalogoDAO.eliminar`, `guardar`, `actualizar`** — cero llamadores; `guardar` y `actualizar`
  son stubs que devuelven `false`.
- **`CatalogoOtrosDAO`** — no tiene `update` ni `delete`: sólo lookup + `obtenerOCrear`
  (`INSERT IGNORE`), sin superficie de escritura que guardar.
- **`EquipoDAO.actualizar` → `EquipoService.actualizar`** — la cadena entera está muerta (ninguno
  de los dos tiene llamador, ni en `src/main` ni en `src/test`). No es el mismo caso que los de
  arriba: `EquipoDAO.actualizar` sí bumpea `version` correctamente (ver su javadoc), así que no es
  un agujero de bloqueo optimista si algún día se reconecta — es puro código sin ruta de llamada.

---

## #11 — Lecturas del histórico: lo que dejó abierto el hotfix de "Ver Equipos" (agregado 2026-09-11)

### Cierre (2026-09-21) — hecho

Lo de abajo es el diagnóstico tal como se escribió; los números de pool que cita (10 conexiones,
`connectionTimeout` 30 s, líneas de `ConnectionPool`) son los de entonces. Cómo se cerró cada punto:

| Punto | Cómo se cerró | Commits |
|---|---|---|
| **(a)** lecturas zombie (ALTO) | Se hizo la opción que acá no se recomendaba, porque resultó barata: `Statement.cancel()` de verdad, sin pasar la sentencia desde el DAO — `ConexionesSupervisadas` envuelve toda conexión y registra sus sentencias por `TokenTarea`. Además, techo de consulta de 30 s (sólo en autocommit), timeouts de red, pool 10 → 8 y un semáforo de 5 lecturas de fondo concurrentes. Verificado contra MySQL: la consulta muere en 20 ms (`CancelacionContraMySQL`). La observabilidad que faltó: duración de `leer`/`pintar` a INFO (`120763e`), y a WARN sobre un umbral de **1 s** (no los 5 s que sugería este texto: el peor caso medido es ~318 ms) | `3bfa1fd`, `120763e`, `52bd792`, `2bce7d2`, `0280ff6`, `1f8f4b3`, `44f5b39` |
| **(b)** `EquipoOtrosDAO.listar` se come los errores (MEDIO) | `listar()` propaga `DatabaseException`, incluido `obtenerPorId()` (confirmado con el usuario). `VerEquiposController.abrirDetalleOtros` salió del EDT a `TareaUI` | `9e9e48e` |
| **(c)** el histórico se lee entero (BAJO) | Último movimiento por subconsulta correlacionada en vez de tabla derivada; índices de fecha/estado (V23); Ver Equipos pagina en SQL de a 50 con todos los filtros y el orden en el `WHERE`/`ORDER BY` (187 → 10 ms a 6 000 filas). Estado de Procesos, que leía lo mismo, se borró por inalcanzable | `a604043`, `9e9e48e`, `93452f4`, `ecad88e`, `11936b7`, `02e68a6`, `3a6a0a0` |

**Dos defectos de la revisión de cierre**, los dos MEDIO y los dos cerrados:

1. **Carrera en la cancelación** — `3414204`. `cancelarDe(token)` sólo alcanzaba sentencias ya
   registradas: una tarea esperando permiso en el semáforo (justo el caso con presión) o cancelada
   entre dos sentencias corría la siguiente consulta entera. Ahora `cancelar()` prende una marca en
   el `TokenTarea` en el EDT antes de despachar el `cancelarDe`, y se revisa después de tomar el
   permiso (lo devuelve), después de registrar cada sentencia y antes de cada `execute*`. Lanza
   `TareaCanceladaException`; `FiltroTareaCancelada` saca de `error.log` los errores de tareas ya
   canceladas (ya pasaba con el `KILL QUERY`), sin tocar los DAOs. Queda una ventana de
   microsegundos entre la última revisión y que el driver marque la sentencia como en ejecución:
   sólo la cubre el techo de 30 s.
2. **Tabla derivada de movimientos en el camino de escritura** — `b37daac`. La unificación de
   materiales duplicados (Registrar Estado, dentro de la transacción y sin techo de consulta)
   agrupaba toda la tabla de movimientos una vez por grupo. Pasó a la misma subconsulta
   correlacionada de (c). No había tests de unificación: se agregaron seis (superviviente por
   fecha, desempate por id, sin movimientos pierde), que pasan igual con el SQL anterior.

**Verificado contra MySQL 8.0.46 local (2026-09-21):**

- *Connector/J 8.3.0 ignora `cancel()` antes del `execute`* — el supuesto de la revisión en
  `execute*`: sin la marca, un `SELECT SLEEP(2)` cancelado entre `prepare` y `execute` corrió
  entero (2 111 ms, devolvió 0); con la marca, rechazado en 2 ms sin llegar al servidor.
- *El `KILL` no llega a `error.log`*: con el `logback.xml` real, la
  `MySQLStatementCancelledException` de una tarea marcada, logueada como la loguean los DAOs, quedó
  en `app.log` y no en `error.log`; la contraprueba (mismo `log.error` desde una tarea sin marca)
  sí llegó. La consulta murió en 17 ms.
  Los dos: `mvn test -Dtest=CancelacionContraMySQL -Daptium.mysql=true`.
- *La unificación sobre `aptium_perf`* (sembrador + 60 000 movimientos por tabla, 6-12× producción),
  `EXPLAIN ANALYZE` de un grupo duplicado: la tabla derivada materializaba 60 000 filas en 12 000
  grupos, **110 ms** (ortopedias) y **93 ms** (otros) por grupo, dentro de la transacción; la
  correlacionada hace *index lookup* por `idx_mov_material` / `idx_otros_mov_material`,
  **0,07 ms** y **0,08 ms**. Mismo superviviente.
- *Sin verificar*: el umbral de 1 s contra la latencia real de Tailscale. Hay que mirar el
  `app.log` de producción después del deploy.

**Queda abierto, decisión del usuario (mismo criterio que #10):** `EquipoService.obtenerTodos`,
`EquipoOtrosService.obtenerTodos` y `EquipoOtrosDAO.obtenerTodos` quedaron sin llamador en
`src/main` al borrar Estado de Procesos. Los dos services sólo los usan sus tests unitarios y
`CostoDelRefrescoTest`; `EquipoOtrosDAO.obtenerTodos` es además el **oráculo** de 19 usos en tests
(`EquipoOtrosDAOTest`, `EquipoOtrosDAOPaginacionTest`, `EstadoPersistidoEsElCalculadoTest`,
`LoteDAOTest`, `ConcurrenciaOptimistaTest`). `EquipoDAO.obtenerTodos` lo exige `DAO<T,ID>`.

### El incidente que lo originó

En producción (`v1.2.0`), **Ver Equipos** y **Estado de Procesos** quedaban vacías y sin la hora de
"actualizado". El resto de las pantallas andaba. `error.log` estaba vacío; la única pista eran estos
avisos en `app.log`:

```
WARN  c.zaxxer.hikari.pool.ProxyLeakTask - Connection leak detection triggered for
      com.mysql.cj.jdbc.ConnectionImpl@554e218 on thread refresco-historial-equipos
...   (7 conexiones distintas entre 08:27 y 08:30)
INFO  ... Previously reported leaked connection ... was returned to the pool (unleaked)   (08:30:44)
```

**No eran leaks: eran lecturas lentas.** Cada lectura del histórico retenía la conexión unos 3,5 min
y el `leakDetectionThreshold` (60 s, `ConnectionPool:304`) las reportaba. Todas se devolvieron al pool.

**Causa:** `EquipoOtrosDAO.listar` hacía una sentencia por equipo para cargar sus materiales, y esa
sentencia llevaba una tabla derivada `SELECT material_id, MAX(fecha) FROM otros_material_movimientos
GROUP BY material_id`, que MySQL **materializa entera en cada ejecución**. Costo:
O(equipos × movimientos), más un viaje de red por TLS por equipo. `CostoDelRefrescoTest` exigía ese
comportamiento ("el histórico se lee entero", más sentencias con más volumen) y lo llamaba "la
contraparte del reparto".

**Hotfix `cbf3cb3` (`v1.2.0.2`):** una sola sentencia con `LEFT JOIN` a materiales, que agrupa las
filas por equipo en un `LinkedHashMap` (el patrón que `EquipoDAO.obtenerEquiposConJoin` ya usaba),
y el test invertido: ahora exige la **misma** cantidad de sentencias con o sin histórico. Verificado
en producción: la pantalla carga en el acto.

Quedan tres cosas que el hotfix no resolvió. El orden es por gravedad.

### (a) Una lectura cancelada sigue corriendo y ocupa recursos compartidos por toda la app  (ALTO)

**Qué pasa.** `RefrescadorPantallas.refrescarAhora()` cancela la lectura en vuelo antes de lanzar
otra, pero `TareaUI.Handle.cancelar()` hace `worker.cancel(false)` **a propósito** ("interrumpir una
query JDBC en curso no es confiable"): la cancelación sólo descarta el resultado. La sentencia sigue
ejecutándose en MySQL y el hilo sigue esperándola. Cada F5, y cada vez que se vuelve a entrar a la
pantalla, suma una lectura zombie. En el incidente se juntaron **7**.

**Por qué es ALTO y no un detalle.** Cada zombie retiene dos recursos que **toda la app** comparte,
y los dos tienen tope 10:

1. **El pool de conexiones** — `maximumPoolSize = 10` (`ConnectionPool:291`). Con 10 tomadas,
   cualquier pantalla del puesto espera `connectionTimeout = 30 s` y termina en error, **escrituras
   incluidas** (Registrar Estado, Lanzar Lote, Lanzar Tanda).
2. **Los hilos de `SwingWorker`** — el executor interno de `javax.swing.SwingWorker` tiene
   `MAX_WORKER_THREADS = 10`, y `TareaUI` es el único mecanismo de trabajo en fondo de la app. Con 10
   hilos colgados, las `TareaUI` nuevas **ni siquiera arrancan**: quedan encoladas sin error visible,
   que es peor que el timeout del pool porque la pantalla no dice nada.

Además, las zombies de todos los puestos caen sobre **el mismo servidor MySQL**, y pueden hacer más
lentos a puestos que no tienen nada que ver.

**Por qué el hotfix no lo cierra.** Sólo lo hace improbable: con una lectura de segundos, es difícil
apilar 10. Pero cualquier query que mañana vuelva a ponerse lenta (volumen, un índice que falta, el
servidor cargado) reproduce el cuadro completo, y ahora sabemos que no avisa por `error.log`.

**Opciones (a decidir):**
- **No lanzar mientras hay una en vuelo** — `refrescarAhora()` marca un "pendiente" y, cuando la
  lectura en curso termina, relee una sola vez más si hacía falta. Acota a **una** lectura por grupo
  y se queda con la semántica de "gana la última pedida". Es un cambio chico y local a
  `RefrescadorPantallas`, pero **no alcanza** para `ClasificacionController` ni
  `SalidasLavaderoController`, que tienen su propio `cargaEnCurso` fuera de `RefrescadorPantallas`
  (ver CLAUDE.md, "Dos asimetrías").
- **`Statement.setQueryTimeout(...)`** en las lecturas de histórico — pone un techo real al tiempo en
  MySQL (Connector/J manda un `KILL QUERY`). Sirve de red de seguridad aunque se haga lo de arriba.
  Hay que decidir qué ve el operador cuando salta: hoy caería en `mostrarErrorDeRefresco`.
- **`Statement.cancel()` desde `cancelar()`** — la cancelación "de verdad". Más invasivo: `TareaUI`
  no conoce la sentencia, habría que hacerla viajar desde el DAO. No lo recomiendo sin necesidad.

**Observabilidad que faltó.** El diagnóstico salió del `ProxyLeakTask` de Hikari **por casualidad**.
`RefrescadorPantallas` (o `TareaUI`) debería loguear la duración de cada lectura, y a WARN si pasa de
un umbral (p. ej. 5 s). Con eso, "Ver Equipos tarda" se habría visto en `app.log` en el primer
arranque de producción, antes del primer reclamo. Es barato y conviene hacerlo **primero**, antes
que cualquier otra opción de este punto.

### (b) `EquipoOtrosDAO.listar` se come los errores de SQL y devuelve una lista parcial  (MEDIO)

**Qué pasa.** `listar` hace `catch (SQLException e) { log.error(...); }` y devuelve lo que alcanzó
a leer. Su javadoc lo justifica como "el comportamiento histórico de estos listados". Es el gemelo
de "otros" del hallazgo **#1 (fallos silenciosos ortopedias, `de1af06`)**, que cerró sólo el lado de
ortopedias: `EquipoDAO.obtenerEquiposConJoin` sí lanza `DatabaseException`.

**Por qué es problema.** Toda la app sale de este método para leer "otros":

| Llamador | Qué pasa si la lectura falla a mitad |
|---|---|
| `obtenerTodos` → Ver Equipos / Estado de Procesos | Pinta una lista incompleta **y el cartel dice "actualizado HH:mm"**. El cartel miente, que es lo que el repo evita a propósito (CLAUDE.md, "Qué le pasa a lo pendiente"). |
| `obtenerActivos` → cola operativa (Registrar Estado, Para Entregar, Lotes) | Equipos que desaparecen de la cola sin aviso. |
| `obtenerEntreFechas` → reporte impreso de "otros" | **Un reporte en papel al que le faltan equipos**, sin ninguna marca. Es el caso más grave: sale del sistema y nadie lo vuelve a verificar. |
| `obtenerPorId` → detalle, Correcciones | Devuelve `null`, que el llamador trata como "no existe". |

**Lo empeoró el hotfix, un poco.** Con la sentencia por equipo, si fallaba la carga de materiales de
un equipo, ese equipo no se agregaba. Con el `JOIN`, un fallo a mitad del `ResultSet` deja el
**último equipo con parte de sus materiales**, y su `calcularEstado()` (el mínimo de sus materiales)
puede dar un estado que no es el real.

**Fix.** Igualarlo a `EquipoDAO`: `throw new DatabaseException(...)` en el `catch`, y ajustar el
javadoc. `TareaUI` ya rutea la excepción a `siFalla` → `mostrarErrorDeRefresco` ("Lo que ves puede
estar desactualizado"), que es el aviso correcto. **Antes de hacerlo:** revisar los llamadores de
`obtenerPorId`/`obtenerEquiposNuevos` fuera de `TareaUI` (Correcciones, `DetalleOtrosDialog` vía
`VerEquiposController.abrirDetalleOtros`), que hoy reciben `null`/lista vacía y pasarían a recibir
una excepción. Buscar también tests que afirmen el comportamiento parcial.

**De paso, algo que apareció al revisar esto:** `VerEquiposController.abrirDetalleOtros` (línea 189)
llama `equipoOtrosService.obtenerPorId` **en el EDT**, en el doble clic. Viola la regla dura de
CLAUDE.md ("ningún acceso a BD corre en el EDT") y no está en la lista de excepciones aceptadas (los
cinco autocompletados). `EdtGuard` debería estar avisándolo en el log de producción. Hoy la lectura
es de un solo equipo y rápida, pero lleva la tabla derivada de movimientos entera (ver (c)), así que
su costo crece con el histórico. Pasarla a `TareaUI` es chico, y conviene hacerlo **junto con** el
fix de arriba: los dos tocan cómo le llega el fallo de `obtenerPorId` a esa pantalla.

### (c) El histórico se sigue leyendo entero: el costo crece en filas con los años  (BAJO)

**Qué pasa.** `LectorHistorialEquipos` trae **todos** los equipos de ortopedias y "otros", entregados
incluidos, con todos sus materiales. Después filtra en memoria, aunque el filtro por defecto de Ver
Equipos oculte los `ENTREGADO` (`PantallaVerEquipos.estadosVisiblesPorDefecto`). Además, las dos
tablas derivadas `MAX(fecha) ... GROUP BY material_id` agregan **toda** la tabla de movimientos en
cada lectura. Tras el hotfix el costo es O(filas) en una sola ida y vuelta, no O(equipos ×
movimientos), así que hoy no se nota. Pero crece con cada día de operación, y el snapshot entero
queda en memoria en los dos controllers.

**Por qué BAJO.** Ya no es cuadrático y no hay síntoma. Anotado para que la decisión se tome con
datos, no cuando vuelva a haber un reclamo.

**Cuándo actuar.** Cuando la duración logueada de la lectura (ver la observabilidad de (a)) pase de
un par de segundos. **Opciones:**
- **Filtrar `ENTREGADO` en SQL** mientras el operador no lo pida: al marcarlo en el combo, se relee
  con el histórico completo. Es lo que más corta, porque lo entregado es la inmensa mayoría de
  las filas.
- **Ventana de fechas por defecto** (p. ej. últimos 6 meses), con los filtros Desde/Hasta que ya
  existen yendo a SQL en vez de a memoria.
- **Último movimiento sin tabla derivada**: una subconsulta correlacionada por material (usa
  `idx_otros_mov_material` / `idx_mov_material`), o una columna `ultimo_movimiento` desnormalizada
  que mantengan las escrituras. La segunda es más rápida pero agrega un lugar más que mantener
  consistente, con el mismo riesgo de agujeros que describe #9(a) para `version`.

---

## Lo que está bien (no tocar)
Jerarquía de excepciones + `ValidationException.Builder`; `TransactionalConnection`;
`SimpleEntityDAO` con detección de integridad por clase de SQLState `23`; el patrón de extraer
lógica de Swing a clases planas (`AgrupadorIngresosLote`, `ReconciliadorPendientes`,
`SincronizadorVolumenFinal`, `OcupacionAutoclave`); `AppContext` como composition root único.

## Plan de sesiones (definido al cerrar el 2026-07-22)

Cada ítem en un **chat nuevo**, no con `/compact`: los hallazgos son independientes y el
handoff son los commits + este doc, así que un chat limpio arranca más barato que un
resumen lossy.

1. ~~**#4** (`Object[]`→records)~~ — ✅ hecho el 2026-07-23.
2. ~~**#3** (`AppModel`)~~ — ✅ hecho el 2026-07-23, disuelto.
3. ~~**#6** (concurrencia/EDT)~~ — ✅ cerrado el 2026-08-27, checklist manual incluida.
   Era el más grande. Plan escrito el 2026-07-23
   en [`refactor-concurrencia-edt.md`](refactor-concurrencia-edt.md): 5 fases, a ejecutar en
   2 chats (fases 1-3 / fases 4-5). Es donde está el riesgo real: los bugs de EDT y de
   refrescos fuera de orden no los agarra la suite de tests.
   Nota: #3 dejó el terreno mejor — `UiCoordinator.crearRefrescador()` sigue igual, pero
   ahora cada controller declara qué services toca, así que es visible cuáles hacen I/O.
4. ~~**refactor-clean**~~ — ✅ hecho el 2026-08-27. Se borraron `Validador.esEmailValido` /
   `esNumeroPositivo` (de #5) y `MaterialCorreccionDTO` (de #4), los tres con cero referencias en
   `src/main` y `src/test`. Los 16 métodos muertos de `AppModel` ya se habían ido con la clase en #3.
   De paso se escribió el `ValidadorTest` que #5 anotaba como faltante (15 tests sobre los 4 métodos
   que quedan). Suite: **985 verdes** (eran 970).
   **Queda una decisión abierta:** `PantallaVerCDEv1` (69 líneas) se instancia y se registra en el
   `CardLayout` de `PantallaPrincipal:85,108`, pero `Constantes.Pantallas.VER_CDE` no se referencia
   desde ningún botón — la reemplazó `PantallaVerCDEv2`. Es inalcanzable; borrarla es una decisión
   del usuario, no del refactor-clean.
5. ~~**security review**~~ — ✅ hecha el 2026-08-27. **Sin hallazgos CRÍTICOS ni ALTOS.**
   Resultado en la sección "Revisión de seguridad" al final de este documento.
6. **code review de la branch** — usar `/code-review ultra`, que corre la revisión
   multi-agente en la nube y **no consume el contexto del chat**. Es user-triggered y
   facturado aparte. **Único ítem del plan de sesiones que sigue abierto.**

---

## Revisión de seguridad (2026-08-27)

**Sin hallazgos CRÍTICOS ni ALTOS.** La superficie es chica y bien tratada: app de escritorio,
sin endpoints, sin sesiones, sin HTML — no aplican XSS, CSRF ni SSRF.

**Lo que está bien:**

- **Inyección SQL: sin superficie.** Todo dato de usuario viaja por `PreparedStatement`. La única
  concatenación que arma SQL es el nombre de tabla en `SimpleEntityDAO` (`getTableName()`), y
  viene de un literal por subclase, nunca de entrada del usuario.
- **Cero secretos commiteados.** `config.properties` está en `.gitignore`; `README-DEPLOY.md` y
  `docs/conexion-remota-mysql-tailscale.md` usan placeholders (`TuContraseñaFuerte123!`,
  `192.168.1.100`, `<USUARIO>`); `.vscode/launch.json` no lleva credenciales.
- **Precedencia de config correcta:** variables de entorno → `config.properties` (fuera del repo,
  con instrucciones de `icacls`/permisos 600) → defaults, y el arranque **avisa por log** cuando
  cae a `localhost:root:root`. Falla ruidosamente si falta `db.ip`/`db.user`/`db.name`.
- **El runbook de Tailscale acota el firewall** a `100.64.0.0/10` (el CGNAT del tailnet) en vez de
  abrir 3306 al mundo, y ya advierte de no dejar `root@'%'`.

**Recomendaciones (MEDIO, ninguna bloqueante):**

1. **`GRANT ALL PRIVILEGES` para el usuario de la app.** Incluye `DROP`/`ALTER`, y la app además
   necesita `CREATE DATABASE` (`ConnectionPool.crearBaseDeDatosSiNoExiste`). Least privilege sería
   partirlo en dos usuarios: uno de migraciones con DDL, y uno de runtime con
   `SELECT/INSERT/UPDATE/DELETE`. Tiene costo operativo real (Flyway corre al arrancar la app), así
   que es una decisión, no un defecto.
2. **`bind-address = 0.0.0.0` protegido sólo por la regla de firewall.** El propio runbook avisa
   que la regla no persiste entre migraciones de servidor: si se pierde, MySQL queda escuchando en
   todas las interfaces. Atarlo a la IP de Tailscale es más robusto que depender del firewall.
3. **Las credenciales viven en cada puesto de trabajo.** Es consecuencia de la arquitectura
   —escritorio contra MySQL directo, sin capa de servidor—: quien tenga acceso a la PC puede leer
   `config.properties` o las variables de entorno y conectarse a la base por fuera de la app. La app
   tampoco tiene autenticación propia. Aceptado por diseño; anotado para que sea una decisión
   consciente y no una sorpresa.
