# Plan: Volúmenes por ingreso al lanzar lote (equipos "otros") — v2 TDD

**Estado:** aprobado para ejecución · **Creado:** 2026-07-03 (v2 TDD: mismo día) · **Pasos:** 4 (seriales)
**Rama base sugerida:** `main` (una rama/PR por paso: `refactor/volumen-ingreso-N-descripcion`)

## Objetivo

Eliminar la fricción del diálogo actual que pide litros **por cada material "otros"** al armarlo en un autoclave. En su lugar, al presionar **Lanzar** aparece un único diálogo que pide los litros **por ingreso** (cada `equipo_otros` con al menos un elemento en el lote). Si un cliente tiene materiales de dos ingresos distintos, se piden dos volúmenes. Ortopedias no cambian.

## Decisiones tomadas (con el usuario, 2026-07-03)

1. **Captura:** diálogo único al presionar Lanzar; tabla con una fila por (cliente, ingreso), campo litros obligatorio (min 1). Sin completar, no se lanza.
2. **Persistencia:** tabla nueva `lote_otros_volumenes (lote_id, equipo_otros_id, volumen)`. La columna `equipo_otros_materiales.volumen_lote` desaparece.
3. **Capacidad durante armado:** los materiales "otros" aportan **0** al volumen calculado hasta que se asignan litros en el diálogo (ahí se recalcula en vivo). El campo "Volumen final" manual sigue siendo la palabra final.
4. **Históricos:** migración V13 puebla la tabla nueva con `SUM(volumen_lote)` por `(lote_id, equipo_otros_id)` y luego elimina la columna. `equipo_otros.volumen_equipo` (agua acumulada por ingreso) no se toca.
5. **Metodología (v2):** TDD estricto. El paso 1 es un PR **solo de tests** que blinda el comportamiento actual antes de tocar producción; los pasos siguientes arrancan en RED.

## Metodología TDD de este plan

- **Caracterización primero:** el paso 1 congela el comportamiento observable actual en tests verdes, sin tocar `src/main`. La migración destructiva del paso 2 se hace contra esa red.
- **Regla de oro de los tests de caracterización:** assertar **solo comportamiento observable de negocio** (`equipo_otros.volumen_equipo`, salida de `obtenerOtrosPorClientePorLote`, estados de materiales) — **nunca** columnas internas como `volumen_lote`. Así los mismos tests siguen siendo válidos antes y después del cambio de esquema.
- **Patrón wrapper:** toda llamada a `lanzarLote` en los tests de caracterización pasa por un helper privado del test (p. ej. `lanzarLoteConOtros(autoclave, capTotal, capUsada, movimientos, litrosPorIngreso)`). En el paso 1 el helper adapta al API actual (reparte litros en `LoteMovimiento.volumenOtros`); en el paso 2 solo se reescribe el helper para la firma nueva. Un único punto de churn en vez de ~30 llamadas.
- **RED en Java:** un test que no compila porque la firma/clase nueva no existe cuenta como RED. Escribir el test, verlo fallar (compilación o assert), implementar lo mínimo, verde, refactor.
- Framework: **JUnit 4 + Mockito 5 + H2 en memoria** (ya configurado; hay tests existentes en `src/test`).

## Contexto para agente frío

App Swing Java 11, sin DI (todo se cablea en `AppContext`/`AppModel`). Migraciones **Flyway** en `src/main/resources/db/migration/` (MySQL en producción; tests con H2 2.2 en `MODE=MySQL`). Build: `mvn clean package`, tests: `mvn verify` (JaCoCo incluido).

**Infra de tests (leer antes de escribir tests DAO o V13):** `src/test/java/com/example/AbstractDAOTest.java` aplica las migraciones Flyway sobre H2 en 3 fases (target V3 / V4 sintético / V5+) con `ConnectionPool.setDataSourceForTesting`. Una futura V13 correrá automáticamente en la fase 3 para **todas** las clases DAO de test: un error de sintaxis en V13 tumba la suite completa. Smoke test: correr cualquier subclase de `AbstractDAOTest`.

**Bug preexistente conocido (NO es regresión de este refactor):** `LotesController.quitarMaterialDePendientes` (~L635-642) re-crea items otros con el constructor sin `esOtros`. Si aparece durante el E2E manual del paso 3, anotarlo aparte; no mezclarlo en estos PRs.

Flujo actual del volumen "otros" (a desmantelar):

| Punto | Archivo | Detalle |
|---|---|---|
| Diálogo por material | `features/lotes/controller/LotesController.java` → `pedirCantidadYVolumen()` (~L529) y rama `esOtros` de `agregarMaterial()` (~L487) | Pide cantidad + litros al arrastrar cada material |
| Transporte | `features/lotes/model/LoteMovimiento.java` campo `volumenOtros`; `features/lotes/view/helpers/MaterialLoteItem.java` campo `volumenOtros` | |
| Escritura | `features/lotes/dao/LoteDAO.java` → `aplicarMovimientoLoteOtros()` escribe `volumen_lote`; `features/equipos/otros/dao/EquipoOtrosMaterialHelper.java` → `materializarRemitoSplit(..., Integer volumenLote)` | Por fila de `equipo_otros_materiales` |
| Acumulación | `LoteDAO.acumularVolumenEquipoOtros()` (al finalizar lote EXITOSO) | `SUM(volumen_lote)` → `equipo_otros.volumen_equipo` |
| Lectura reportes | `LoteDAO.obtenerOtrosPorClientePorLote()` | Suma litros por cliente, agrega línea "Litros: N" |
| Lectura UI | `LoteDAO.obtenerMaterialesPorLote()` (`COALESCE(volumen_lote,1)` para otros) → `LotesController.onAutoclaveSeleccionado()` rama ocupado | Único consumidor UI del UNION de otros |
| Capacidad | `LotesController.calcularCapacidad()` usa `item.getVolumenOtros()` | |
| Validación | `features/lotes/service/LoteService.lanzarLote()` | `ValidationException.Builder` |
| Tests existentes | `LoteDAOTest` (~30 llamadas a la firma vieja, L138-415), `LoteServiceTest` (~L88-98), `EquipoOtrosMaterialHelperTest` (asserta `volumen_lote=20`, ~L140-145) | Ver paso 1 |

Conceptos clave: un **ingreso** = una fila de `equipo_otros` (tanto DETALLES como REMITO). REMITO se identifica con `remitoId` (`ddmmaaaa-{id}`); DETALLES no tiene remito → etiquetar con fecha de ingreso + id. En el armado, los items "otros" usan `materialId` negativo (`-equipoId`) para REMITO sin filas materializadas; `getEquipoId()` siempre es el id real del `equipo_otros`.

## Diseño destino

- **Tabla:** `lote_otros_volumenes(id PK, lote_id FK→lotes, equipo_otros_id FK→equipo_otros, volumen INT NOT NULL, UNIQUE(lote_id, equipo_otros_id))`.
- **Modelo:** el volumen viaja como `Map<Integer,Integer>` (`equipoOtrosId → litros`) en un parámetro nuevo de `lanzarLote` a través de `AppModel → LoteService → LoteDAO`. `LoteMovimiento` y `MaterialLoteItem` pierden `volumenOtros`.
- **Validación (LoteService):** para cada movimiento con `esOtros`, debe existir entrada en el mapa con volumen ≥ 1; no se admiten claves que no correspondan a ingresos del lote.
- **UI:** diálogo `DialogoVolumenesIngreso` (view helper) mostrado por `LotesController.lanzarLote()` cuando hay items otros; integra el resumen de confirmación actual. La lógica de agrupación de pendientes por ingreso se extrae a un helper puro y testeable.
- **DRY:** la rama "otros" de `agregarMaterial()` pasa a usar `CantidadDialogHelper.pedirCantidad` igual que ortopedia; `pedirCantidadYVolumen()` se elimina.

---

## Paso 1 — Red de seguridad: tests de caracterización (PR solo de tests)

**Rama:** `refactor/volumen-ingreso-1-caracterizacion` · **Riesgo:** nulo (no toca `src/main`) · **Depende de:** nada

Congela en tests el comportamiento observable actual del flujo de volúmenes. Este PR se mergea en verde y convierte la migración destructiva del paso 2 en un cambio verificable: si los tests de este paso siguen verdes después del paso 2, el negocio no se rompió.

### Tareas

1. **Nueva clase `LoteVolumenesCaracterizacionTest`** (subclase de `AbstractDAOTest`, en `src/test/java/com/example/features/lotes/dao/`), con el helper wrapper `lanzarLoteConOtros(...)` descrito en Metodología. Escenarios (asserts SOLO sobre comportamiento observable):
   - (a) DETALLES completo: lanzar lote con todos los elementos de un ingreso → materiales en `Esterilizando`; finalizar EXITOSO → `volumen_equipo` del ingreso = litros declarados; reporte `obtenerOtrosPorClientePorLote` contiene las líneas de material y `"Litros: N"` correcto.
   - (b) DETALLES parcial (split): mover cantidad menor → fila restante disponible, `volumen_equipo` acumula solo los litros del lote.
   - (c) REMITO primer split y split posterior (materialId negativo).
   - (d) Dos ingresos del mismo cliente en el mismo lote → `volumen_equipo` de **cada** `equipo_otros` correcto por separado; reporte suma litros por cliente.
   - (e) `marcarLoteFallo` → `volumen_equipo` intacto; materiales vuelven al estado anterior.
   - (f) Lote mixto ortopedia+otros → reporte de ortopedias (`obtenerMaterialesPorClientePorLote`) no se ve afectado.
   - (g) Ingreso otros sin litros declarados (hoy posible con `volumenOtros=null` vía REMITO sin volumen) → `volumen_equipo` no cambia, reporte "Litros: 0" (paridad con `COALESCE(...,0)`).
2. **Sanear asserts de implementación en tests existentes:** `EquipoOtrosMaterialHelperTest` (~L140-145) asserta `volumen_lote=20`; reescribir ese assert hacia comportamiento observable si el escenario lo permite, o marcarlo con comentario `// se elimina en paso 2 (columna desaparece)`. No tocar nada más de `LoteDAOTest`/`LoteServiceTest` todavía (su churn de firmas es del paso 2).
3. Verificar que los escenarios (a)-(g) realmente pasan contra el código actual (son caracterización, no especificación nueva).

### Verificación
```
mvn verify
```

### Criterios de salida
- `git diff --stat main -- src/main/` vacío (cero cambios de producción).
- Tests nuevos verdes y con nombres descriptivos por escenario.

### Rollback
Trivial: revert del PR de tests.

---

## Paso 2 — Persistencia por ingreso (V13 + DAO/Service/Model), UX intacta

**Rama:** `refactor/volumen-ingreso-2-persistencia` · **Riesgo:** medio (era alto; la red del paso 1 y el ciclo RED→GREEN lo bajan) · **Depende de:** Paso 1

La UI sigue pidiendo litros por material, pero el controller los agrupa por ingreso y los pasa por la firma nueva. PR verificable con la UX vieja; el paso 3 queda puramente de UI.

### Fase RED — tests nuevos que fallan

1. **`LoteServiceTest`:** tests de la firma nueva `lanzarLote(..., Map<Integer,Integer> volumenesPorIngreso)`: mapa null/faltante para un ingreso con movimientos `esOtros` → `ValidationException`; volumen 0 o negativo → `ValidationException`; clave huérfana (ingreso que no está en los movimientos) → `ValidationException`; mapa vacío con lote solo-ortopedia → OK.
2. **`LoteDAOTest` (tests nuevos):** `lanzarLote` con mapa inserta una fila por ingreso en `lote_otros_volumenes`; `obtenerVolumenesPorLote(loteId)` devuelve el mapa; `finalizarLote` acumula en `volumen_equipo` leyendo de la tabla nueva; lote fallido no acumula.
3. **Test de backfill de V13** (mejor esfuerzo): test standalone con Flyway API — migrar H2 hasta `target=V6`, sembrar `equipo_otros_materiales` con `volumen_lote` en varios (lote, ingreso) incluyendo filas NULL, migrar a V13, assertar filas agregadas en `lote_otros_volumenes` y ausencia de la columna vieja. Si el arnés de fases de `AbstractDAOTest` hace esto frágil, degradar a: verificación manual documentada del backfill sobre una copia de la BD real antes del deploy (dejar constancia en el PR).
4. Correr: los tests nuevos fallan (no compilan o assertan en rojo). Los de caracterización del paso 1 siguen verdes.

### Fase GREEN — implementación mínima

5. **`V13__lote_otros_volumenes.sql`** en `src/main/resources/db/migration/`:
   ```sql
   CREATE TABLE lote_otros_volumenes (
       id              INT AUTO_INCREMENT PRIMARY KEY,
       lote_id         INT NOT NULL,
       equipo_otros_id INT NOT NULL,
       volumen         INT NOT NULL,
       CONSTRAINT uq_lote_ingreso UNIQUE (lote_id, equipo_otros_id),
       FOREIGN KEY (lote_id)         REFERENCES lotes(id)        ON DELETE CASCADE,
       FOREIGN KEY (equipo_otros_id) REFERENCES equipo_otros(id) ON DELETE CASCADE
   );
   INSERT INTO lote_otros_volumenes (lote_id, equipo_otros_id, volumen)
   SELECT lote_id, equipo_otros_id, SUM(volumen_lote)
   FROM equipo_otros_materiales
   WHERE lote_id IS NOT NULL AND volumen_lote IS NOT NULL
   GROUP BY lote_id, equipo_otros_id;
   ALTER TABLE equipo_otros_materiales DROP COLUMN volumen_lote;
   ```
   Usar `CONSTRAINT ... UNIQUE` (portable MySQL/H2), **no** `UNIQUE KEY nombre (cols)` — ninguna migración existente usa esa forma y el parser H2 puede rechazarla.
   **Numeración V13 (decisión 2026-07-13):** la rama `Lavadero` ya ocupa V7-V12. En esta misma tarea agregar `.outOfOrder(true)` a la config Flyway de `DatabaseInitializer`: permite que las V7-V12 de Lavadero se apliquen en producción aunque V13 ya esté aplicada (escenario hotfix-antes-que-feature). Ver sección "Coordinación con rama Lavadero".
6. **`LoteMovimiento`:** eliminar campo `volumenOtros` y el constructor de 5 args; dejar `(materialId, equipoId, cantidad, esOtros)`.
7. **`LoteDAO.lanzarLote(...)`:** nuevo parámetro `Map<Integer,Integer> volumenesPorIngreso`; dentro de la misma transacción, tras aplicar movimientos, insertar una fila por entrada del mapa. Quitar todo manejo de `volumen_lote` en `aplicarMovimientoLoteOtros()` (UPDATE e INSERT).
8. **`EquipoOtrosMaterialHelper.materializarRemitoSplit`:** eliminar parámetro `volumenLote` y la columna del INSERT; actualizar los dos call-sites en `LoteDAO`.
9. **`LoteDAO.acumularVolumenEquipoOtros`:** leer `SELECT volumen FROM lote_otros_volumenes WHERE lote_id=? AND equipo_otros_id=?` (ya no SUM sobre materiales).
10. **`LoteDAO.obtenerOtrosPorClientePorLote`:** implementación fija en **dos queries** (no un solo JOIN, que multiplicaría los litros por la cantidad de filas de material del ingreso): (a) la query de líneas actual sin la columna litros; (b) query agregada `SELECT c.nombre, SUM(v.volumen) FROM lote_otros_volumenes v JOIN equipo_otros eo ON v.equipo_otros_id = eo.id JOIN clientes c ON eo.nro_cliente = c.id WHERE v.lote_id = ? GROUP BY c.nombre`. Anexar la línea "Litros: N" **solo** a clientes ya presentes en el mapa de líneas (default 0, como hoy) — evita líneas fantasma de lotes fallidos relanzados. Formato de salida idéntico.
11. **`LoteDAO.obtenerMaterialesPorLote`:** en la rama UNION de otros, reemplazar `COALESCE(eom.volumen_lote,1)` por `0`. Documentar en `LoteMaterialInfo` que `volumen`/`getVolumenTotal()` no aplican a otros.
12. **`LoteDAO.obtenerVolumenesPorLote(int loteId)`** nuevo. Exponer vía `LoteService` y `AppModel` (lo consume el paso 3).
13. **`LoteService.lanzarLote`:** nueva firma; validaciones con `ValidationException.Builder` según los tests de la fase RED.
14. **`AppModel.lanzarLote`:** propagar la firma.
15. **`LotesController` (puente temporal, se elimina en paso 3):** en `lanzarLote()`, construir el mapa sumando `item.getVolumenOtros()` por `equipoId` de los pendientes otros; dejar de pasar `volumenOtros` a `LoteMovimiento`. El diálogo por material queda como está. Además, en `onAutoclaveSeleccionado` (rama ocupado, ~L321) **no llamar más `setVolumenOtros(info.getVolumen())`** — dejarlo null para que `MaterialLoteTableModel` muestre `"-"` (si quedara en 0, la columna mostraría un 0 engañoso).

### Fase REFACTOR / adaptación

16. **Actualizar el wrapper** de `LoteVolumenesCaracterizacionTest` a la firma nueva (único punto de churn); los asserts no cambian. Verde = comportamiento preservado.
17. **`LoteDAOTest` / `LoteServiceTest` existentes:** adaptar las ~30 llamadas a la firma nueva (`Map.of(equipoOtrosId, litros)` con otros, `Map.of()` solo-ortopedia); reemplazar asserts de `volumen_lote` por `lote_otros_volumenes`. Eliminar el assert marcado en `EquipoOtrosMaterialHelperTest`.
18. Limpieza final: sin duplicación entre tests de caracterización y tests DAO nuevos (consolidar si un escenario quedó cubierto dos veces).

### Verificación
```
mvn verify
```
Manual: lanzar lote con material otros (DETALLES y REMITO), finalizarlo, verificar `equipo_otros.volumen_equipo` y el reporte ("Litros: N" por cliente). Lote fallido: `volumen_equipo` no cambia.

### Criterios de salida
- Tests de caracterización del paso 1 verdes **sin haber tocado sus asserts** (solo el wrapper).
- `grep -r volumen_lote --include='*.java' src/` → 0 resultados (la columna sigue nombrada en V2/V13, que son inmutables — eso es correcto).
- Comportamiento visible idéntico al actual, con una excepción documentada: la columna Volumen de materiales otros en autoclave ocupado pasa a mostrar `"-"` (display definitivo en paso 3).

### Rollback
`git revert` del PR **antes** de aplicar V13 en producción. La migración es destructiva (DROP COLUMN): **hacer backup/dump de `equipo_otros_materiales` antes del primer arranque con V13 en la BD real**. Si ya corrió, restaurar desde backup.

---

## Paso 3 — Nueva UX: cantidad simple + diálogo de volúmenes al lanzar

**Rama:** `refactor/volumen-ingreso-3-ux` · **Riesgo:** medio · **Depende de:** Paso 2

### Fase RED — tests primero (lógica pura)

1. **`AgrupadorIngresosLoteTest`** (nuevo, unit puro sin H2 ni Swing): dado un set de `MaterialLoteItem` pendientes + `Map<Integer, EquipoOtros>`, asserta:
   - Agrupa por `equipoOtrosId`; ítems ortopedia quedan fuera.
   - Multi-cliente/multi-ingreso: dos ingresos del mismo cliente → dos filas.
   - Etiquetas: REMITO → `remitoId`; DETALLES → `"Ingreso dd/MM/yyyy (#id)"` con `fechaIngreso`.
   - `cantidadTotal` suma las cantidades del ingreso; lista vacía → lista vacía.
2. Correr: RED (la clase no existe).

### Fase GREEN — implementación

3. **Helper puro `AgrupadorIngresosLote`** (`features/lotes/controller/helpers/`): devuelve `List<IngresoPendienteInfo>` (`equipoOtrosId`, `clienteNombre`, `etiquetaIngreso`, `cantidadTotal`). Sin Swing.
4. **`LotesController`:** reemplazar `clientesPorEquipoOtros` por un `Map<Integer, EquipoOtros>` poblado en `cargarDatos()` (ya se llama `model.obtenerTodosLosEquiposOtros()`).
5. **`DialogoVolumenesIngreso`** (`features/lotes/view/helpers/`): modal con resumen de materiales (texto actual de confirmación), tabla (Cliente | Ingreso | Cantidad | Litros con spinner min 1), recálculo en vivo de "Volumen calculado" (= ortopedias + suma de litros), campo "Volumen final" editable prellenado, advertencias actuales (<80%, ajuste manual) y botones Lanzar/Cancelar. Devuelve `Optional<ResultadoLanzamiento>` (mapa + volumen final). El diálogo NO contiene lógica de negocio: consume `AgrupadorIngresosLote` y valida solo presentación.
6. **`LotesController.lanzarLote()`:** si hay pendientes otros → abrir el diálogo; si no → flujo actual sin cambios. Eliminar el puente temporal del paso 2. **Manejo explícito del estado del panel cuando hay otros** (hoy el botón Lanzar depende de `volumenManualDentroDeCapacidad` sobre `panel.getVolumenManual()`, que devuelve -1 con campo vacío, y `lanzarLote()` valida ese campo ANTES de cualquier diálogo, ~L661-672):
   - En `onAutoclaveSeleccionado`/`actualizarBotonLanzarPorVolumen`: si hay pendientes otros, deshabilitar el campo de volumen del panel (`setVolumenManualEnabled(false)`) y habilitar Lanzar solo por `hayPendientes`.
   - Saltear los checks de ~L661-672 en ese caso y moverlos **dentro del diálogo**: el "Volumen final" del diálogo valida con error duro `> capacidad` al aceptar (sin él, `LoteService` tiraría "capacidad usada no puede superar la total" como excepción en vez de mensaje UI).
   - El volumen final confirmado en el diálogo se pasa como `capacidadUsada` a `model.lanzarLote`.
7. **`agregarMaterial()`:** la rama otros usa `CantidadDialogHelper.pedirCantidad` (idéntico a ortopedia). Eliminar `pedirCantidadYVolumen()`.
8. **`MaterialLoteItem`:** eliminar `volumenOtros` y sus accessors. `calcularCapacidad()`: items otros aportan 0.
9. **Vista autoclave ocupado:** confirmar display `"-"` (ya desde paso 2); con `model.obtenerVolumenesPorLote(loteId)` disponible, opcionalmente mostrar litros por ingreso en tooltip — decisión estética menor, no bloqueante. **No tocar `VerLotesController`/`PantallaVerLotes`:** verificado en la revisión — no consumen `obtenerMaterialesPorLote` ni muestran volúmenes por material (el reporte Jasper usa `obtenerOtrosPorClientePorLote`, ya cubierto en paso 2).

### Verificación
`mvn verify` (caracterización + unit nuevos verdes) + E2E manual: (a) DETALLES parcial con "Todos" y con cantidad menor; (b) REMITO con split; (c) mismo cliente con dos ingresos → dos filas en el diálogo; (d) cancelar el diálogo no lanza ni pierde pendientes; (e) lote solo-ortopedia → flujo idéntico al actual; (f) mixto; (g) la barra no cuenta otros durante armado y el lote guarda el volumen final confirmado.

### Criterios de salida
- Al arrastrar un material otros solo se pide cantidad.
- Imposible lanzar con litros sin asignar (el diálogo no lo permite; `LoteService` lo valida igual como red de seguridad — cubierto por tests del paso 2).
- `grep -r "volumenOtros\|pedirCantidadYVolumen" src/` → 0 resultados.

### Rollback
`git revert` del PR (sin cambios de esquema en este paso).

---

## Paso 4 — Cierre: cobertura, limpieza y documentación

**Rama:** `refactor/volumen-ingreso-4-cierre` · **Riesgo:** bajo · **Depende de:** Paso 3

1. Revisar reporte JaCoCo (`mvn verify`): la lógica nueva (`LoteService.lanzarLote`, `AgrupadorIngresosLote`, caminos nuevos de `LoteDAO`) con cobertura ≥ 80%; agregar tests puntuales si hay huecos (ramas de error del DAO, mapa con ingreso duplicado, etc.).
2. Grep final de código muerto: `volumen_lote`, `volumenOtros`, `pedirCantidadYVolumen`, constructores sin uso de `MaterialLoteItem`/`LoteMovimiento`.
3. Actualizar `CLAUDE.md`: tabla Ortopedias vs. Otros (fila de volúmenes), nota sobre `lote_otros_volumenes`, y corregir la mención "src/test vacío" (ya hay tests).
4. `mvn clean package` y prueba de humo del JAR.

### Criterios de salida
Build verde, cobertura ≥ 80% en la lógica nueva, docs al día.

---

## Invariantes (verificar después de CADA paso)

- `mvn verify` verde; los tests de caracterización del paso 1 nunca cambian sus asserts (solo el wrapper en paso 2).
- El flujo de ortopedias no cambia en ningún paso.
- `equipo_otros.volumen_equipo` se acumula solo al finalizar lote EXITOSO (paridad con hoy).
- Formato de salida de reportes (`obtenerOtrosPorClientePorLote`) sin cambios para consumidores.

## Coordinación con rama Lavadero (decisión 2026-07-13)

`Lavadero` (en desarrollo) ocupa V7-V12, ya aplicadas en la BD de la PC de desarrollo. Por eso la migración de este refactor es **V13** y el paso 2 habilita `.outOfOrder(true)` en `DatabaseInitializer`.

**Procedimiento de merge main → Lavadero** (cuando el refactor esté en main):
1. `git checkout Lavadero && git merge main`. Conflictos esperables si Lavadero tocó `LotesController`/`LoteDAO`/`LoteService`: resolver y validar con la suite.
2. `mvn verify`: los 12 tests de `LoteVolumenesCaracterizacionTest` deben quedar verdes sin tocar sus asserts — esa es la prueba de que el merge no rompió el flujo de volúmenes. (H2 arranca de cero y aplica V1..V13 en orden; los tests no dependen del estado de la BD de dev.)
3. BD de desarrollo (está en V12): **backup de `equipo_otros_materiales` antes del primer arranque post-merge** (V13 es destructiva). Al arrancar la app, Flyway aplica V13 (backfill + drop) automáticamente.
4. Producción: puede recibir el refactor antes que Lavadero (queda en 1-6 + 13). Cuando Lavadero se deploye, `outOfOrder(true)` hace que V7-V12 se apliquen igual. Backup completo de la BD antes de ese deploy, como con cualquier tanda grande de migraciones.

## Anti-patrones a evitar

- No introducir flags de configuración ni doble camino de lectura (decisión: migrar y limpiar).
- El puente temporal del paso 2 (tarea 15) debe eliminarse explícitamente en el paso 3 — no dejarlo "por las dudas".
- No mover lógica de negocio al diálogo Swing: la agrupación y validación viven en helpers/service testeables.
- Tests de caracterización que assertan columnas internas (`volumen_lote`) en vez de comportamiento observable: quedan obsoletos con el esquema y pierden su valor de red de seguridad.
- No escribir implementación antes que su test en los pasos 2 y 3 (fases RED explícitas).
