# Hallazgos de arquitectura pendientes (revisión 2026-07-22)

Diagnóstico de la revisión profunda del 2026-07-22.

**Estado al 2026-07-23:** #1 a #5 **ejecutados**, suite en verde (509 tests).
Queda solo **#6**.

| # | Estado | Commit |
|---|---|---|
| #1 fallos silenciosos ortopedias | hecho | `de1af06` |
| #2 strategies muertas (`IMaterialFilter` + `ICapacidadCalculator`) | hecho | `a370e61` |
| #5 `common` ↔ Swing | hecho | `fc15bd1` |
| #4 `Object[]` → records | hecho (2026-07-23) | `57c87d1` |
| #3 `AppModel` | hecho (2026-07-23) — **disuelto** | sin commitear |
| #6 concurrencia / EDT | **pendiente** — plan escrito en [`refactor-concurrencia-edt.md`](refactor-concurrencia-edt.md) | — |

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
- [`MaterialCorreccionDTO`](../src/main/java/com/example/features/equipos/ortopedias/controller/helpers/MaterialCorreccionDTO.java)
  es **código muerto** (cero referencias) y tiene exactamente los mismos 6 campos que
  `FilaMaterial` — es el DTO que alguien creó para esto y nunca cableó. Candidato directo
  del refactor-clean.
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

**Pendientes menores que dejó, fuera del alcance de #5:**
- `Validador.esEmailValido` y `Validador.esNumeroPositivo` **no tienen ningún llamador**
  → código muerto, candidato para el refactor-clean.
- No existe `ValidadorTest`. Los 6 métodos puros que quedaron son ahora trivialmente
  testeables sin Swing.

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
3. **#6** (concurrencia/EDT) — el más grande y el único que queda. Plan escrito el 2026-07-23
   en [`refactor-concurrencia-edt.md`](refactor-concurrencia-edt.md): 5 fases, a ejecutar en
   2 chats (fases 1-3 / fases 4-5). Es donde está el riesgo real: los bugs de EDT y de
   refrescos fuera de orden no los agarra la suite de tests.
   Nota: #3 dejó el terreno mejor — `UiCoordinator.crearRefrescador()` sigue igual, pero
   ahora cada controller declara qué services toca, así que es visible cuáles hacen I/O.
4. **refactor-clean** — barre el código muerto que dejen los anteriores. Ya identificados:
   `Validador.esEmailValido` / `esNumeroPositivo` (de #5) y `MaterialCorreccionDTO` (de #4).
   Los 16 métodos muertos de `AppModel` ya se fueron con la clase en #3.
5. **security review** — superficie real acotada: queries parametrizadas, credenciales
   en `config.properties` y los defaults hardcodeados, validación de entrada. Es una app
   Swing de escritorio sin auth ni endpoints: presupuestar un chat corto, no una fase.
6. **code review de la branch** — usar `/code-review ultra`, que corre la revisión
   multi-agente en la nube y **no consume el contexto del chat**. Es user-triggered y
   facturado aparte.
