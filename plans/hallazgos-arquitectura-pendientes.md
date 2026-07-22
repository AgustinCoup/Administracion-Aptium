# Hallazgos de arquitectura pendientes (revisión 2026-07-22)

Diagnóstico de la revisión profunda del 2026-07-22. Los hallazgos #1 y #2 tienen
tratamiento aparte (#1 ejecutado; #2 en `plans/hibrido-strategies-muertas.md`).
Este doc conserva los **pendientes #3 a #6** con el *por qué* y las referencias exactas,
para no perderlos entre sesiones. Ninguno está decidido ni ejecutado.

Contexto que se respetó en toda la revisión: la **no-atomicidad auditoría↔dato es
deliberada** (ver `plans/sacar-sql-de-equipo-otros-correccion-service.md`). No unificar
transacciones "por prolijidad".

---

## #3 — `AppModel`: fachada que ya se rompió y crece sin techo  (GRAVE)

**Qué:** `AppModel` (357 líneas, ~60 métodos de delegación de una línea). El javadoc dice
explícitamente *"No exponer servicios internos (get\*Service)"* y abajo hay **cinco** getters
de servicio ([AppModel.java:336-354](../src/main/java/com/example/app/AppModel.java)),
uno con excusa documentada y cuatro sin ella. Los reporte-services se pasan directo desde
`UiCoordinator` a los controllers, salteando la fachada.

**Por qué es problema:**
- (a) Crece linealmente con cada feature; cualquier cambio recompila y re-testea toda la app.
- (b) Todo controller ve la API completa del sistema — `LotesController` puede llamar
  `fusionarClientes()`. La regla "no exponer servicios" se cumple por disciplina, no por diseño.

**Decisión pendiente (del usuario):**
- **Disolver** `AppModel` a favor de inyección directa por controller — `AppContext` entrega a
  cada controller solo los servicios que necesita (ya lo hace con los reporte-services y con
  `VerLotesController`). Cada constructor documenta su alcance. Es el camino ya empezado.
- **o Mantener** la fachada y hacer cumplir por diseño la regla de no exponer servicios.

Cambia bastante el trabajo según cuál. Preguntar antes de ejecutar.

---

## #4 — `Object[]` posicional cruzando la frontera DAO→service  (MEDIO)

**Qué:** DAOs devuelven `Object[]` / `List<Object[]>` que el service castea por índice.
Ejemplo en [EquipoCorreccionService.java:110-114](../src/main/java/com/example/features/equipos/ortopedias/service/EquipoCorreccionService.java):
```java
Object[] materialActual = materialDAO.obtenerMaterial(materialId);
Integer codigoAnterior      = (Integer) materialActual[0];
String  descripcionAnterior = (String)  materialActual[2];
```
También `List<Object[]>` en la línea 197. Origen en `MaterialDAO.obtenerMaterial` y
`MaterialDAO.obtenerMaterialesPorCodigo`. **29 ocurrencias en 15 archivos.**

**Por qué es problema:** si alguien reordena el `SELECT` del DAO, no falla la compilación:
falla en runtime con `ClassCastException`, o peor, no falla y guarda el dato equivocado en
la auditoría. El compilador no ayuda.

**Fix:** un `record` por cada forma (ej. 5 campos para el material). Elimina la clase de bug
entera. Barato y de bajo riesgo. Buen candidato para hacer primero entre los pendientes.

---

## #5 — La capa `common` depende de Swing  (MEDIO)

**Qué:** [Validador.java:8](../src/main/java/com/example/common/util/Validador.java) importa
`javax.swing.JTextField` y `java.awt.event.KeyAdapter`. Mezcla reglas de negocio puras
(`esFormatoNombre`, `esEmailValido`, `detectarDuplicados`) con manipulación de widgets
(`aplicarSoloNumeros`, `aplicarSoloLetrasYEspacios`).

**Por qué es problema:** invierte la dirección de dependencias. `common`, que debería ser el
núcleo sin dependencias externas, depende de la capa más externa (UI). Contamina el núcleo.

**Fix:** separar en `Validador` (puro, en `common/util/`) + `RestriccionesCampo` (los helpers
de `JTextField`, movidos a `ui/common/`). ~30 min, bajo riesgo.

---

## #6 — Concurrencia ad-hoc y trabajo de BD en el EDT  (MEDIO)

**Qué:** tres modelos de concurrencia conviviendo:
- `new Thread()` crudo en 8 sitios, incluido dentro de una **view**
  ([PantallaAuditoria.java:219](../src/main/java/com/example/features/equipos/ortopedias/view/PantallaAuditoria.java)).
- `SwingWorker` (VerLotesController, VerEquiposController).
- Llamadas sincrónicas directas.

`LotesController.cargarDatos()`
([línea 161](../src/main/java/com/example/features/lotes/controller/LotesController.java))
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
`SincronizadorVolumenFinal`); `AppContext` como composition root único.

## Orden sugerido entre los pendientes
1. **#4** (`Object[]`→records) — barato, alto valor, elimina una clase de bug.
2. **#5** (`common`↔Swing) — chico, aísla el núcleo.
3. **#3** (`AppModel`) — requiere decisión de diseño primero.
4. **#6** (concurrencia/EDT) — el más grande, planificar aparte.
